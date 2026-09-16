package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import id.storia.media.data.model.ScanStatus
import id.storia.media.db.*
import id.storia.media.db.currentLocalDateTime
import id.storia.media.service.NfoData
import id.storia.media.service.ScannerService
import id.storia.media.service.SubtitleInfo
import id.storia.media.util.FFmpegUtil
import id.storia.media.util.PathProvider
import id.storia.media.util.MediaFormatUtil
import org.jaudiotagger.tag.images.Artwork
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.io.File
import java.security.MessageDigest
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

abstract class BaseScanner(protected val onStatusUpdate: (currentFile: String?, filesProcessed: Int?, filesFound: Int?) -> Unit) : MediaScanner {
    protected val processedFiles = mutableSetOf<String>()

    override suspend fun cleanup(libraryId: String) = transaction {
        // 1. Get all files in DB for this library
        val dbFiles = (MediaFiles innerJoin MediaItems)
            .selectAll()
            .where { MediaItems.libraryId eq libraryId }
            .map { it[MediaFiles.id] to it[MediaFiles.filePath] }
        
        // 2. Delete files that were not processed during this scan
        dbFiles.forEach { (id, path) ->
            if (path !in processedFiles) {
                // If it's not in processedFiles, it's either missing from disk
                // or the scanner no longer considers it a valid media file for this library.
                // In both cases, "files is source of truth" means it should be removed.
                if (!File(path).exists()) {
                    MediaFiles.deleteWhere { MediaFiles.id eq id }
                }
            }
        }
        
        // 3. Cleanup items with no files
        cleanupEmptyMediaItems(libraryId)
    }

    protected fun getElementText(parent: Element, tagName: String): String? {
        val nodes = parent.getElementsByTagName(tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.parentNode == parent) {
                return node.textContent
            }
        }
        return null
    }

    protected fun parseNfo(file: File): NfoData? {
        if (!file.exists()) return null
        return try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(file)
            val root = doc.documentElement
            root.normalize()
            
            val title = getElementText(root, "title")
            val originalTitle = getElementText(root, "originaltitle")
            val year = getElementText(root, "year")?.toIntOrNull()
            val plot = getElementText(root, "plot")
            
            var rating = getElementText(root, "rating")?.toDoubleOrNull()
            if (rating == null) {
                val ratings = root.getElementsByTagName("ratings").item(0) as? Element
                val firstRating = ratings?.getElementsByTagName("rating")?.item(0) as? Element
                rating = getElementText(firstRating ?: root, "value")?.toDoubleOrNull()
            }
            
            val studio = getElementText(root, "studio")
            val director = getElementText(root, "director")
            val season = getElementText(root, "season")?.toIntOrNull()
            val episode = getElementText(root, "episode")?.toIntOrNull()
            val aired = getElementText(root, "aired") ?: getElementText(root, "premiered")
            
            val genreNodes = root.getElementsByTagName("genre")
            val genres = mutableListOf<String>()
            for (i in 0 until genreNodes.length) {
                if (genreNodes.item(i).parentNode == root) {
                    genres.add(genreNodes.item(i).textContent)
                }
            }

            NfoData(title, originalTitle, year, plot, rating, genres, studio, director, season, episode, aired)
        } catch (_: Exception) {
            null
        }
    }

    protected fun processMediaItem(libraryId: String, type: LibraryType, folder: File, nfoData: NfoData?): String = transaction {
        val folderName = folder.name
        val nameTitle = folderName.replace(Regex("\\s*\\(\\d{4}\\)$"), "")
        val nameYear = Regex("\\((\\d{4})\\)$").find(folderName)?.groupValues?.get(1)?.toIntOrNull()
        
        val title = nfoData?.title ?: nameTitle
        val year = nfoData?.year ?: nameYear
        
        val existing = MediaItems.selectAll().where { 
            (MediaItems.libraryId eq libraryId) and (MediaItems.title eq title) 
        }.singleOrNull()

        val allFiles = folder.listFiles() ?: emptyArray()
        val mainMediaFile = allFiles.find { isVideoFile(it) }
        val poster = allFiles.find { isPosterFile(it, mainMediaFile?.nameWithoutExtension) }
            ?: if (type == LibraryType.PHOTOBOOK) allFiles.find { isImageFile(it) } else null
        val fanart = allFiles.find { isFanartFile(it) }

        if (existing != null) {
            val id = existing[MediaItems.id]
            val oldPoster = existing[MediaItems.posterPath]
            val oldFanart = existing[MediaItems.fanartPath]

            MediaItems.update({ MediaItems.id eq id }) {
                it[MediaItems.plot] = nfoData?.plot
                it[MediaItems.rating] = nfoData?.rating
                it[MediaItems.genres] = nfoData?.genres?.joinToString(", ")
                
                if (poster != null) {
                    it[MediaItems.posterPath] = poster.absolutePath
                } else if (oldPoster != null && !File(oldPoster).exists()) {
                    it[MediaItems.posterPath] = null
                }

                if (fanart != null) {
                    it[MediaItems.fanartPath] = fanart.absolutePath
                } else if (oldFanart != null && !File(oldFanart).exists()) {
                    it[MediaItems.fanartPath] = null
                }

                it[MediaItems.updatedAt] = currentLocalDateTime()
            }
            id
        } else {
            val id = UUID.randomUUID().toString()
            MediaItems.insert {
                it[MediaItems.id] = id
                it[MediaItems.libraryId] = libraryId
                it[MediaItems.type] = type.name.lowercase()
                it[MediaItems.title] = title
                it[MediaItems.releaseYear] = year
                it[MediaItems.plot] = nfoData?.plot
                it[MediaItems.rating] = nfoData?.rating
                it[MediaItems.genres] = nfoData?.genres?.joinToString(", ")
                it[MediaItems.posterPath] = poster?.absolutePath
                it[MediaItems.fanartPath] = fanart?.absolutePath
            }
            id
        }
    }

    protected fun processMediaItemFromFile(libraryId: String, type: LibraryType, file: File, nfoData: NfoData?, posterPath: String? = null): String = transaction {
        val nameTitle = file.nameWithoutExtension.replace(Regex("\\s*\\(\\d{4}\\)$"), "")
        val nameYear = Regex("\\((\\d{4})\\)$").find(file.nameWithoutExtension)?.groupValues?.get(1)?.toIntOrNull()

        val title = nfoData?.title ?: nameTitle
        val year = nfoData?.year ?: nameYear

        val existing = MediaItems.selectAll().where { 
            (MediaItems.libraryId eq libraryId) and (MediaItems.title eq title) 
        }.singleOrNull()

        if (existing != null) {
            val id = existing[MediaItems.id]
            val oldPoster = existing[MediaItems.posterPath]
            
            MediaItems.update({ MediaItems.id eq id }) {
                if (nfoData != null) {
                    it[MediaItems.plot] = nfoData.plot
                    it[MediaItems.rating] = nfoData.rating
                    it[MediaItems.genres] = nfoData.genres.joinToString(", ")
                }
                
                if (posterPath != null) {
                    it[MediaItems.posterPath] = posterPath
                } else if (oldPoster != null && !File(oldPoster).exists()) {
                    it[MediaItems.posterPath] = null
                }
                
                it[MediaItems.updatedAt] = currentLocalDateTime()
            }
            id
        } else {
            val id = UUID.randomUUID().toString()
            MediaItems.insert {
                it[MediaItems.id] = id
                it[MediaItems.libraryId] = libraryId
                it[MediaItems.type] = type.name.lowercase()
                it[MediaItems.title] = title
                it[MediaItems.releaseYear] = year
                it[MediaItems.plot] = nfoData?.plot
                it[MediaItems.rating] = nfoData?.rating
                it[MediaItems.genres] = nfoData?.genres?.joinToString(", ")
                it[MediaItems.posterPath] = posterPath
            }
            id
        }
    }

    protected fun addMediaFile(
        mediaItemId: String,
        file: File,
        category: String,
        thumbnail: File? = null,
        title: String? = null,
        episodeTitle: String? = null,
        trackArtist: String? = null,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
        trackNum: Int? = null,
        plot: String? = null,
        airDate: String? = null,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null,
        bitrate: Long? = null,
        sampleRate: Int? = null,
        audioCodec: String? = null,
        videoCodec: String? = null,
        overrideExtension: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        albumArtistSort: String? = null,
        artist: String? = null,
        artistSort: String? = null,
        artists: List<String>? = null,
        totalTracks: Int? = null,
        totalDiscs: Int? = null,
        discNumber: Int? = null
    ) = transaction {
        val filePath = file.absolutePath
        processedFiles.add(filePath)
        
        val existing = MediaFiles.selectAll().where { MediaFiles.filePath eq filePath }.singleOrNull()
        
        if (existing == null) {
            MediaFiles.insert {
                it[MediaFiles.id] = UUID.randomUUID().toString()
                it[MediaFiles.mediaItemId] = mediaItemId
                it[MediaFiles.filePath] = filePath
                it[MediaFiles.fileName] = file.name
                it[MediaFiles.title] = title
                it[MediaFiles.fileSize] = file.length()
                it[MediaFiles.containerFormat] = overrideExtension ?: file.extension
                it[MediaFiles.category] = category
                it[MediaFiles.thumbnailPath] = thumbnail?.absolutePath
                it[MediaFiles.trackNumber] = trackNum
                it[MediaFiles.episodeTitle] = episodeTitle
                it[MediaFiles.trackArtist] = trackArtist
                it[MediaFiles.episodeNumber] = episodeNumber
                it[MediaFiles.seasonNumber] = seasonNumber
                it[MediaFiles.plot] = plot
                it[MediaFiles.airDate] = airDate
                it[MediaFiles.width] = width
                it[MediaFiles.height] = height
                it[MediaFiles.durationMs] = durationMs
                it[MediaFiles.bitrate] = bitrate
                it[MediaFiles.sampleRate] = sampleRate
                it[MediaFiles.audioCodec] = audioCodec
                it[MediaFiles.videoCodec] = videoCodec
                it[MediaFiles.album] = album
                it[MediaFiles.albumArtist] = albumArtist
                it[MediaFiles.albumArtistSortOrder] = albumArtistSort
                it[MediaFiles.artist] = artist
                it[MediaFiles.artistSortOrder] = artistSort
                it[MediaFiles.artists] = artists?.joinToString(", ")
                it[MediaFiles.totalTracks] = totalTracks
                it[MediaFiles.totalDiscs] = totalDiscs
                it[MediaFiles.discNumber] = discNumber
            }
            onStatusUpdate(null, 1, null)
        } else {
            MediaFiles.update({ MediaFiles.id eq existing[MediaFiles.id] }) {
                it[MediaFiles.mediaItemId] = mediaItemId
                it[MediaFiles.category] = category
                it[MediaFiles.thumbnailPath] = thumbnail?.absolutePath
                it[MediaFiles.title] = title ?: existing[MediaFiles.title]
                it[MediaFiles.trackNumber] = trackNum ?: existing[MediaFiles.trackNumber]
                it[MediaFiles.episodeTitle] = episodeTitle ?: existing[MediaFiles.episodeTitle]
                it[MediaFiles.trackArtist] = trackArtist ?: existing[MediaFiles.trackArtist]
                it[MediaFiles.episodeNumber] = episodeNumber ?: existing[MediaFiles.episodeNumber]
                it[MediaFiles.seasonNumber] = seasonNumber ?: existing[MediaFiles.seasonNumber]
                it[MediaFiles.plot] = plot ?: existing[MediaFiles.plot]
                it[MediaFiles.airDate] = airDate ?: existing[MediaFiles.airDate]
                it[MediaFiles.width] = width ?: existing[MediaFiles.width]
                it[MediaFiles.height] = height ?: existing[MediaFiles.height]
                it[MediaFiles.durationMs] = durationMs ?: existing[MediaFiles.durationMs]
                it[MediaFiles.bitrate] = bitrate ?: existing[MediaFiles.bitrate]
                it[MediaFiles.sampleRate] = sampleRate ?: existing[MediaFiles.sampleRate]
                it[MediaFiles.audioCodec] = audioCodec ?: existing[MediaFiles.audioCodec]
                it[MediaFiles.videoCodec] = videoCodec ?: existing[MediaFiles.videoCodec]
                it[MediaFiles.containerFormat] = overrideExtension ?: file.extension
                it[MediaFiles.album] = album ?: existing[MediaFiles.album]
                it[MediaFiles.albumArtist] = albumArtist ?: existing[MediaFiles.albumArtist]
                it[MediaFiles.albumArtistSortOrder] = albumArtistSort ?: existing[MediaFiles.albumArtistSortOrder]
                it[MediaFiles.artist] = artist ?: existing[MediaFiles.artist]
                it[MediaFiles.artistSortOrder] = artistSort ?: existing[MediaFiles.artistSortOrder]
                it[MediaFiles.artists] = artists?.joinToString(", ") ?: existing[MediaFiles.artists]
                it[MediaFiles.totalTracks] = totalTracks ?: existing[MediaFiles.totalTracks]
                it[MediaFiles.totalDiscs] = totalDiscs ?: existing[MediaFiles.totalDiscs]
                it[MediaFiles.discNumber] = discNumber ?: existing[MediaFiles.discNumber]
            }
        }
    }

    protected fun processSubtitles(mediaFilePath: String, subFiles: List<File>) = transaction {
        val mediaFile = MediaFiles.selectAll().where { MediaFiles.filePath eq mediaFilePath }.singleOrNull() ?: return@transaction
        val mediaFileId = mediaFile[MediaFiles.id]
        val mediaBaseName = File(mediaFilePath).nameWithoutExtension

        Subtitles.deleteWhere { (Subtitles.mediaFileId eq mediaFileId) and (Subtitles.isEmbedded eq false) }
        
        subFiles.forEach { subFile ->
            val info = extractSubtitleInfo(subFile, mediaBaseName)
            Subtitles.insert {
                it[Subtitles.id] = UUID.randomUUID().toString()
                it[Subtitles.mediaFileId] = mediaFileId
                it[Subtitles.language] = info.language
                it[Subtitles.title] = info.title
                it[Subtitles.format] = info.format
                it[Subtitles.filePath] = subFile.absolutePath
                it[Subtitles.isEmbedded] = false
            }
        }
    }

    protected fun extractSubtitleInfo(file: File, mediaBaseName: String): SubtitleInfo {
        val ext = file.extension.lowercase()
        val nameWithoutExt = file.nameWithoutExtension
        var lang = "und"
        var title = "Default"

        if (nameWithoutExt.lowercase().startsWith(mediaBaseName.lowercase())) {
            val remainder = nameWithoutExt.substring(mediaBaseName.length).trimStart('.', '_', '-', ' ')
            if (remainder.isNotEmpty()) {
                val parts = remainder.split(Regex("[._ -]+"))
                if (parts[0].length <= 4) {
                    lang = parts[0].lowercase()
                    title = parts.joinToString(" ").uppercase()
                } else {
                    title = remainder
                }
            }
        }
        return SubtitleInfo(file, lang, title, ext)
    }

    protected fun updateMediaPoster(mediaItemId: String, poster: File) = transaction {
        MediaItems.update({ MediaItems.id eq mediaItemId }) {
            it[MediaItems.posterPath] = poster.absolutePath
            it[MediaItems.updatedAt] = currentLocalDateTime()
        }
    }

    protected fun cleanupEmptyMediaItems(libraryId: String) = transaction {
        val items = MediaItems.selectAll().where { MediaItems.libraryId eq libraryId }.map { it[MediaItems.id] }
        items.forEach { itemId ->
            val hasFiles = MediaFiles.selectAll().where { MediaFiles.mediaItemId eq itemId }.any()
            if (!hasFiles) {
                MediaItems.deleteWhere { MediaItems.id eq itemId }
            }
        }
    }

    protected fun isVideoFile(file: File) = !file.isDirectory && MediaFormatUtil.isVideoFile(file.name)
    protected fun isVideoFile(name: String) = MediaFormatUtil.isVideoFile(name)
    
    protected fun isAudioFile(file: File) = !file.isDirectory && MediaFormatUtil.isAudioFile(file.name)
    protected fun isAudioFile(name: String) = MediaFormatUtil.isAudioFile(name)
    
    protected fun isBookFile(file: File) = !file.isDirectory && isBookFile(file.name)
    protected fun isBookFile(name: String) = listOf("pdf", "cbz").contains(name.substringAfterLast(".").lowercase())
    
    protected fun isImageFile(file: File) = !file.isDirectory && isImageFile(file.name)
    protected fun isImageFile(name: String) = listOf("jpg", "jpeg", "png", "webp", "tbn").contains(name.substringAfterLast(".").lowercase())
    protected fun isSubtitleFile(file: File) = !file.isDirectory && listOf("srt", "ass", "ssa", "vtt", "sub", "idx").contains(file.extension.lowercase())
    protected fun isNfoFile(file: File) = !file.isDirectory && file.extension.lowercase() == "nfo"
    protected fun isThumbnailFile(file: File) = !file.isDirectory && file.name.lowercase().endsWith(".thumb.jpg")

    protected fun isMediaFile(file: File) = (isVideoFile(file) || isAudioFile(file) || isImageFile(file)) && !isThumbnailFile(file)
    
    protected fun isPosterFile(file: File, mediaBaseName: String? = null): Boolean {
        if (isThumbnailFile(file)) return false
        val name = file.nameWithoutExtension.lowercase()
        val isCommon = name == "poster" || name == "folder" || name == "cover" || name == "album"
        val isMatch = mediaBaseName != null && name == mediaBaseName.lowercase()
        return isImageFile(file) && (isCommon || isMatch)
    }

    protected fun isFanartFile(file: File): Boolean {
        if (isThumbnailFile(file)) return false
        val name = file.nameWithoutExtension.lowercase()
        return isImageFile(file) && (name == "fanart" || name == "backdrop" || name == "background")
    }

    protected fun isMetadataFile(file: File) = isPosterFile(file) || isFanartFile(file) || isNfoFile(file) || isThumbnailFile(file)

    protected fun getOrCreateVideoThumbnail(videoFile: File): File? {
        if (!isVideoFile(videoFile)) return null

        val parent = videoFile.parentFile
        val baseName = videoFile.nameWithoutExtension

        // 1. Look for existing sidecar (priority: .thumb.jpg -> .jpg -> .png -> .webp -> .tbn)
        val specificSidecar = File(parent, "${videoFile.name}.thumb.jpg")
        if (specificSidecar.exists()) return specificSidecar

        val extensions = listOf("jpg", "jpeg", "png", "webp", "tbn")
        for (ext in extensions) {
            val sidecar = File(parent, "$baseName.$ext")
            if (sidecar.exists()) return sidecar
        }

        // 2. Try to generate sidecar
        if (parent.canWrite()) {
            val targetSidecar = File(parent, "${videoFile.name}.thumb.jpg")
            if (FFmpegUtil.generateThumbnail(videoFile, targetSidecar)) {
                return targetSidecar
            }
        }

        // 3. Fallback to app data cache
        val hash = MessageDigest.getInstance("MD5")
            .digest(videoFile.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cachedThumb = File(PathProvider.getThumbnailDir(), "$hash.jpg")
        
        if (cachedThumb.exists()) return cachedThumb
        
        if (FFmpegUtil.generateThumbnail(videoFile, cachedThumb)) {
            return cachedThumb
        }

        return null
    }

    protected fun saveArtwork(artwork: Artwork): File? {
        val data = artwork.binaryData ?: return null
        if (data.isEmpty()) return null
        
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(data).joinToString("") { "%02x".format(it) }
        
        val ext = when (artwork.mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        
        val coverDir = PathProvider.getCoverDir()
        
        val coverFile = File(coverDir, "$hash.$ext")
        if (!coverFile.exists()) {
            coverFile.writeBytes(data)
        }
        return coverFile
    }
}
