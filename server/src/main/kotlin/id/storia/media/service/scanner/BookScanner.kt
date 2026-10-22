package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import id.storia.media.db.*
import id.storia.media.service.NfoData
import id.storia.media.util.PathProvider
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class BookScanner(
    private val libraryType: LibraryType = LibraryType.BOOK,
    onStatusUpdate: (String?, Int?, Int?) -> Unit
) : BaseScanner(onStatusUpdate) {
    override suspend fun scan(libraryId: String, root: File) {
        root.walkTopDown().forEach { folder ->
            if (folder.isDirectory) {
                val files = folder.listFiles() ?: emptyArray()
                
                // Only scan files as books (.cbz, .pdf)
                val bookFiles = files.filter { isBookFile(it) }.sortedBy { it.name }
                
                if (bookFiles.isNotEmpty()) {
                    val flattenedName = if (folder == root) {
                        folder.name
                    } else {
                        folder.relativeTo(root).path.replace(File.separator, " - ")
                    }
                    
                    onStatusUpdate("Scanning Series: $flattenedName", null, null)
                    
                    // Use flattened name as the primary title/identity for the media item
                    val nfoData = NfoData(title = flattenedName)

                    val mediaItemId = processMediaItem(libraryId, libraryType, folder, nfoData)

                    val imagesAtThisLevel = files.filter { isImageFile(it) }

                    bookFiles.forEach { book ->
                        processBookEntry(mediaItemId, book, imagesAtThisLevel)
                    }

                    // Find a candidate for the series poster: Vol 1 or Chapter 1
                    // We avoid Vol 0 as it is usually misc/extra
                    val posterCandidate = bookFiles.find { book ->
                        val name = book.nameWithoutExtension.lowercase()
                        val volPattern = Regex("\\b(vol|volume|v)\\s*[.]?\\s*0*1\\b")
                        val chPattern = Regex("\\b(ch|chapter|c)\\s*[.]?\\s*0*1\\b")
                        volPattern.containsMatchIn(name) || chPattern.containsMatchIn(name)
                    } ?: bookFiles.find { book ->
                        val name = book.nameWithoutExtension.lowercase()
                        val zeroPattern = Regex("\\b(vol|volume|v|ch|chapter|c)\\s*[.]?\\s*0+\\b")
                        !zeroPattern.containsMatchIn(name)
                    } ?: bookFiles.first()

                    // Fallback: If no series poster, use first page of first volume
                    generateSeriesPosterIfMissing(mediaItemId, posterCandidate)
                }
            }
        }
    }

    private fun generateSeriesPosterIfMissing(mediaItemId: String, firstVolume: File) = transaction {
        val existingPoster = MediaItems.selectAll().where { MediaItems.id eq mediaItemId }.singleOrNull()?.get(MediaItems.posterPath)
        if (existingPoster != null && File(existingPoster).exists()) return@transaction

        val posterFile = File(PathProvider.getPosterDir(), "$mediaItemId.jpg")
        
        if (firstVolume.extension.equals("cbz", ignoreCase = true)) {
            try {
                ZipFile(firstVolume).use { zip ->
                    val firstImageEntry = zip.entries().asSequence()
                        .filter { !it.isDirectory && isImageFile(it.name) }
                        .sortedBy { it.name }
                        .firstOrNull() ?: return@transaction
                    
                    zip.getInputStream(firstImageEntry).use { input ->
                        posterFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    updateMediaPoster(mediaItemId, posterFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processBookEntry(mediaItemId: String, file: File, imagesAtParentLevel: List<File>) {
        onStatusUpdate("Book: ${file.name}", null, null)

        val comicInfo = if (file.extension.equals("cbz", ignoreCase = true)) {
            parseComicInfoFromZip(file)
        } else null

        val trackTitle = comicInfo?.title ?: file.nameWithoutExtension
        
        val thumb = imagesAtParentLevel.find { it.nameWithoutExtension.equals(file.nameWithoutExtension, ignoreCase = true) }

        val plot = comicInfo?.summary
        val airDate = comicInfo?.getDateString()

        addMediaFile(
            mediaItemId = mediaItemId,
            file = file,
            category = "MAIN",
            thumbnail = thumb,
            title = trackTitle,
            plot = plot,
            airDate = airDate,
            episodeNumber = comicInfo?.number?.replace(Regex("[^0-9]"), "")?.toIntOrNull(),
            seasonNumber = comicInfo?.volume
        )
    }

    private data class ComicInfo(
        val title: String? = null,
        val series: String? = null,
        val number: String? = null,
        val volume: Int? = null,
        val summary: String? = null,
        val year: Int? = null,
        val month: Int? = null,
        val day: Int? = null,
    ) {
        fun getDateString(): String? {
            if (year == null) return null
            return buildString {
                append(year)
                if (month != null) {
                    append("-")
                    append(month.toString().padStart(2, '0'))
                    if (day != null) {
                        append("-")
                        append(day.toString().padStart(2, '0'))
                    }
                }
            }
        }
    }

    private fun parseComicInfoFromZip(file: File): ComicInfo? {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("ComicInfo.xml") ?: return null
                return zip.getInputStream(entry).use { parseComicInfoInputStream(it) }
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseComicInfoInputStream(inputStream: InputStream): ComicInfo? {
        return try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(inputStream)
            val root = doc.documentElement
            root.normalize()

            ComicInfo(
                title = getElementText(root, "Title"),
                series = getElementText(root, "Series"),
                number = getElementText(root, "Number"),
                volume = getElementText(root, "Volume")?.toIntOrNull(),
                summary = getElementText(root, "Summary"),
                year = getElementText(root, "Year")?.toIntOrNull(),
                month = getElementText(root, "Month")?.toIntOrNull(),
                day = getElementText(root, "Day")?.toIntOrNull(),
            )
        } catch (_: Exception) {
            null
        }
    }
}
