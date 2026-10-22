package id.storia.media.service

import id.storia.media.data.model.LibraryType
import id.storia.media.data.model.ScanStatus
import id.storia.media.db.*
import id.storia.media.service.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

data class NfoData(
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val plot: String? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val studio: String? = null,
    val director: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val aired: String? = null
)

data class SubtitleInfo(
    val file: File,
    val language: String,
    val title: String,
    val format: String
)

object ScannerService {
    private var currentStatus = ScanStatus(isScanning = false)

    init {
        // Silence jaudiotagger logs
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun getStatus() = currentStatus

    private fun updateStatus(currentFile: String? = null, filesProcessed: Int? = null, filesFound: Int? = null) {
        currentStatus = currentStatus.copy(
            currentFile = currentFile ?: currentStatus.currentFile,
            filesProcessed = filesProcessed ?: currentStatus.filesProcessed,
            filesFound = filesFound ?: currentStatus.filesFound
        )
    }

    suspend fun scanLibrary(libraryId: String) = withContext(Dispatchers.IO) {
        val (libName, libType, folders) = transaction {
            val lib = Libraries.selectAll().where { Libraries.id eq libraryId }.singleOrNull()
                ?: throw IllegalArgumentException("Library not found")
            
            val paths = LibraryFolders.selectAll().where { LibraryFolders.libraryId eq libraryId }
                .map { it[LibraryFolders.path] }
            
            Triple(lib[Libraries.name], LibraryType.fromString(lib[Libraries.type]), paths)
        }

        if (currentStatus.isScanning) return@withContext

        currentStatus = ScanStatus(
            isScanning = true,
            currentLibraryId = libraryId,
            libraryName = libName,
            filesFound = 0,
            filesProcessed = 0
        )

        try {
            val scanner: MediaScanner = when (libType) {
                LibraryType.SERIES -> SeriesScanner(::updateStatus)
                LibraryType.FILM -> FilmScanner(::updateStatus)
                LibraryType.MUSIC_ALBUM -> MusicScanner(::updateStatus)
                LibraryType.BOOK -> BookScanner(LibraryType.BOOK, ::updateStatus)
                LibraryType.COMIC -> BookScanner(LibraryType.COMIC, ::updateStatus)
                LibraryType.PHOTOBOOK -> PhotobookScanner(::updateStatus)
                else -> GenericScanner(libType, libName, ::updateStatus)
            }

            folders.forEach { folderPath ->
                val root = File(folderPath)
                if (root.exists() && root.isDirectory) {
                    scanner.scan(libraryId, root)
                }
            }

            scanner.cleanup(libraryId)

            transaction {
                Libraries.update({ Libraries.id eq libraryId }) {
                    it[Libraries.lastScannedAt] = currentLocalDateTime()
                }
            }
        } catch (e: Exception) {
            currentStatus = currentStatus.copy(error = e.message)
            e.printStackTrace()
        } finally {
            currentStatus = currentStatus.copy(isScanning = false)
        }
    }
}
