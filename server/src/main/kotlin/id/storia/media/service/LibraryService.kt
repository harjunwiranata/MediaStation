package id.storia.media.service

import id.storia.media.data.model.*
import id.storia.media.db.*
import id.storia.media.util.PathProvider
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Clock
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object LibraryService {

    fun getAllLibraries(): List<Library> = transaction {
        Libraries.selectAll().orderBy(Libraries.name).map { row ->
            val libId = row[Libraries.id]
            val folderPaths = LibraryFolders.selectAll().where { LibraryFolders.libraryId eq libId }
                .map { it[LibraryFolders.path] }
            
            Library(
                id = libId,
                name = row[Libraries.name],
                type = LibraryType.fromString(row[Libraries.type]),
                path = row[Libraries.path],
                folders = folderPaths,
                createdAt = row[Libraries.createdAt].toString(),
                lastScannedAt = row.getOrNull(Libraries.lastScannedAt)?.toString()
            )
        }
    }

    fun getLibraryById(id: String): Library? = transaction {
        Libraries.selectAll().where { Libraries.id eq id }.singleOrNull()?.let { row ->
            val folderPaths = LibraryFolders.selectAll().where { LibraryFolders.libraryId eq id }
                .map { it[LibraryFolders.path] }
            
            Library(
                id = id,
                name = row[Libraries.name],
                type = LibraryType.fromString(row.get(Libraries.type)),
                path = row.get(Libraries.path),
                folders = folderPaths,
                createdAt = row.get(Libraries.createdAt).toString(),
                lastScannedAt = row.getOrNull(Libraries.lastScannedAt)?.toString()
            )
        }
    }

    fun createLibrary(name: String, type: LibraryType, paths: List<String>): Library = transaction {
        val id = UUID.randomUUID().toString()
        Libraries.insert {
            it[Libraries.id] = id
            it[Libraries.name] = name
            it[Libraries.type] = type.name.lowercase()
            it[Libraries.path] = paths.firstOrNull()
        }

        paths.forEach { p ->
            LibraryFolders.insert {
                it[LibraryFolders.id] = UUID.randomUUID().toString()
                it[LibraryFolders.libraryId] = id
                it[LibraryFolders.path] = p
            }
        }

        getLibraryById(id)!!
    }

    fun deleteLibrary(id: String) = transaction {
        Libraries.deleteWhere { Libraries.id eq id }
    }

    fun getMediaItemsByLibrary(
        libraryId: String,
        search: String?,
        includeHidden: Boolean,
        offset: Long? = null,
        limit: Int? = null
    ): List<Media> = transaction {
        var query = MediaItems.selectAll().where { MediaItems.libraryId eq libraryId }
        if (!includeHidden) {
            query = query.andWhere { MediaItems.isHidden eq false }
        }
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase()}%"
            query = query.andWhere {
                (MediaItems.title.lowerCase() like pattern) or
                (MediaItems.originalTitle.lowerCase() like pattern) or
                (MediaItems.artist.lowerCase() like pattern) or
                (MediaItems.artistSortOrder.lowerCase() like pattern) or
                (MediaItems.album.lowerCase() like pattern) or
                (MediaItems.albumArtist.lowerCase() like pattern) or
                (MediaItems.albumArtistSortOrder.lowerCase() like pattern)
            }
        }
        
        query = query.orderBy(
            MediaItems.albumArtistSortOrder to SortOrder.ASC_NULLS_LAST,
            MediaItems.artistSortOrder to SortOrder.ASC_NULLS_LAST,
            MediaItems.albumArtist to SortOrder.ASC_NULLS_LAST,
            MediaItems.artist to SortOrder.ASC_NULLS_LAST,
            MediaItems.sortTitle to SortOrder.ASC_NULLS_LAST,
            MediaItems.title to SortOrder.ASC_NULLS_LAST
        )
        
        if (limit != null) {
            query = query.limit(limit)
        }
        if (offset != null) {
            query = query.offset(offset)
        }

        query.map { row ->
            mapMediaRow(row, includeFiles = false)
        }
    }

    fun getRecentMediaItems(limit: Int, offset: Long = 0L, includeHidden: Boolean): List<Media> = transaction {
        var query = MediaItems.selectAll().where { MediaItems.lastPlayedAt.isNotNull() }
        if (!includeHidden) {
            query = query.andWhere { MediaItems.isHidden eq false }
        }
        query.orderBy(MediaItems.lastPlayedAt, SortOrder.DESC).limit(limit).offset(offset).map { row ->
            mapMediaRow(row, includeFiles = false)
        }
    }

    fun getMediaItemDetails(itemId: String, includeHidden: Boolean): Media? = transaction {
        var query = MediaItems.selectAll().where { MediaItems.id eq itemId }
        if (!includeHidden) {
            query = query.andWhere { MediaItems.isHidden eq false }
        }
        
        query.singleOrNull()?.let { row ->
            var filesQuery = MediaFiles.selectAll().where { MediaFiles.mediaItemId eq itemId }
            if (!includeHidden) {
                filesQuery = filesQuery.andWhere { MediaFiles.isHidden eq false }
            }
            
            val files = filesQuery
                .orderBy(MediaFiles.seasonNumber to SortOrder.ASC)
                .orderBy(MediaFiles.episodeNumber to SortOrder.ASC)
                .orderBy(MediaFiles.trackNumber to SortOrder.ASC)
                .orderBy(MediaFiles.fileName to SortOrder.ASC)
                .map { fileRow -> mapMediaFileRow(fileRow) }
            
            mapMediaRow(row, includeFiles = true, files = files)
        }
    }

    fun getMediaFileById(fileId: String): MediaFile? = transaction {
        MediaFiles.selectAll().where { MediaFiles.id eq fileId }.singleOrNull()?.let {
            mapMediaFileRow(it)
        }
    }

    fun getSystemInfo(): SystemInfo = SystemInfo(
        name = "MediaStation Server",
        version = "1.0.0",
        directPlayOnly = true,
        videoTranscodingAllowed = false
    )

    fun getFilePath(fileId: String): String? = transaction {
        MediaFiles.selectAll().where { MediaFiles.id eq fileId }.singleOrNull()?.get(MediaFiles.filePath)
    }

    fun getPosterPath(mediaId: String): String? = transaction {
        MediaItems.selectAll().where { MediaItems.id eq mediaId.trim() }.singleOrNull()?.get(MediaItems.posterPath)
    }

    fun getFanartPath(mediaId: String): String? = transaction {
        MediaItems.selectAll().where { MediaItems.id eq mediaId.trim() }.singleOrNull()?.get(MediaItems.fanartPath)
    }

    fun getThumbnailPath(fileId: String): String? = transaction {
        MediaFiles.selectAll().where { MediaFiles.id eq fileId.trim() }.singleOrNull()?.get(MediaFiles.thumbnailPath)
    }

    fun getFirstPageFile(fileId: String): File? {
        val pages = getPages(fileId)
        val firstPage = pages.firstOrNull() ?: return null
        return getPageFile(fileId, firstPage.name)
    }

    fun getPages(fileId: String): List<PageInfo> = transaction {
        val path = getFilePath(fileId) ?: return@transaction emptyList()
        val file = File(path)
        if (!file.exists()) return@transaction emptyList()

        if (file.isDirectory) {
            file.listFiles()?.filter { it.isFile && isImageFile(it.name) }?.sortedBy { it.name }?.map { 
                PageInfo(name = it.name, size = it.length())
            } ?: emptyList()
        } else if (file.extension.equals("cbz", ignoreCase = true)) {
            try {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && isImageFile(it.name) }
                        .sortedBy { it.name }
                        .map { PageInfo(name = it.name, size = it.size) }
                        .toList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun getPageFile(fileId: String, pageName: String): File? {
        val path = getFilePath(fileId) ?: return null
        val sourceFile = File(path)
        if (!sourceFile.exists()) return null

        if (sourceFile.isDirectory) {
            if (pageName.isEmpty()) return sourceFile
            val pageFile = File(sourceFile, pageName)
            return if (pageFile.exists() && pageFile.isFile) pageFile else null
        }

        if (sourceFile.extension.equals("cbz", ignoreCase = true)) {
            if (pageName.isEmpty()) return sourceFile
            
            val cacheDir = File(PathProvider.getCacheDir(), "cbz/$fileId")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val cachedFile = File(cacheDir, pageName)
            if (cachedFile.exists()) {
                cachedFile.setLastModified(System.currentTimeMillis())
                return cachedFile
            }

            try {
                ZipFile(sourceFile).use { zip ->
                    val entry = zip.getEntry(pageName) ?: return null
                    zip.getInputStream(entry).use { input ->
                        cachedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                // Periodic cleanup check (randomized or simplified for now)
                if (System.currentTimeMillis() % 20 == 0L) {
                    cleanupCache()
                }
                return cachedFile
            } catch (e: Exception) {
                return null
            }
        }
        
        return if (pageName.isEmpty()) sourceFile else null
    }

    fun cleanupCache(maxSizeBytes: Long = 100 * 1024 * 1024) { // 100MB
        val cacheRoot = PathProvider.getCacheDir()
        if (!cacheRoot.exists()) return

        val files = cacheRoot.walkTopDown().filter { it.isFile }.toList()
            .sortedBy { it.lastModified() }
        
        var currentSize = files.sumOf { it.length() }
        
        if (currentSize > maxSizeBytes) {
            for (file in files) {
                val size = file.length()
                if (file.delete()) {
                    currentSize -= size
                    if (currentSize <= maxSizeBytes * 0.7) break
                }
            }
            // Clean up empty directories
            cacheRoot.walkBottomUp().filter { it.isDirectory && it != cacheRoot }.forEach { 
                if (it.listFiles()?.isEmpty() == true) it.delete()
            }
        }
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast(".", "").lowercase()
        return listOf("jpg", "jpeg", "png", "webp", "tbn").contains(ext)
    }

    fun updatePlaybackProgress(
        mediaFileId: String,
        clientId: String,
        positionSeconds: Int,
        durationSeconds: Int,
        pageNumber: Int? = null,
        totalPages: Int? = null
    ) = transaction {
        val isFinished = if (totalPages != null && pageNumber != null) {
            totalPages > 0 && pageNumber >= totalPages - 1
        } else {
            durationSeconds > 0 && positionSeconds >= durationSeconds * 0.92
        }

        val existing = PlaybackProgressTable.selectAll().where {
            (PlaybackProgressTable.mediaFileId eq mediaFileId) and (PlaybackProgressTable.clientId eq clientId)
        }.singleOrNull()

        if (existing != null) {
            PlaybackProgressTable.update({ PlaybackProgressTable.id eq existing.get(PlaybackProgressTable.id) }) {
                it[PlaybackProgressTable.positionSeconds] = positionSeconds
                it[PlaybackProgressTable.durationSeconds] = durationSeconds
                it[PlaybackProgressTable.pageNumber] = pageNumber
                it[PlaybackProgressTable.totalPages] = totalPages
                it[PlaybackProgressTable.isFinished] = isFinished
                it[PlaybackProgressTable.updatedAt] = currentLocalDateTime()
            }
        } else {
            PlaybackProgressTable.insert {
                it[PlaybackProgressTable.id] = UUID.randomUUID().toString()
                it[PlaybackProgressTable.mediaFileId] = mediaFileId
                it[PlaybackProgressTable.clientId] = clientId
                it[PlaybackProgressTable.positionSeconds] = positionSeconds
                it[PlaybackProgressTable.durationSeconds] = durationSeconds
                it[PlaybackProgressTable.pageNumber] = pageNumber
                it[PlaybackProgressTable.totalPages] = totalPages
                it[PlaybackProgressTable.isFinished] = isFinished
            }
        }

        // Update last played at on parent media item
        MediaFiles.selectAll().where { MediaFiles.id eq mediaFileId }.singleOrNull()?.let { fileRow ->
            val itemId = fileRow[MediaFiles.mediaItemId]
            MediaItems.update({ MediaItems.id eq itemId }) {
                it[MediaItems.lastPlayedAt] = currentLocalDateTime()
            }
        }
    }

    private fun mapMediaFileRow(fileRow: ResultRow): MediaFile {
        val fileId = fileRow.get(MediaFiles.id)
        val progress = PlaybackProgressTable.selectAll().where { PlaybackProgressTable.mediaFileId eq fileId }
            .singleOrNull()?.let { pRow ->
                PlaybackProgress(
                    mediaFileId = fileId,
                    clientId = pRow.get(PlaybackProgressTable.clientId),
                    positionSeconds = pRow.get(PlaybackProgressTable.positionSeconds),
                    durationSeconds = pRow.get(PlaybackProgressTable.durationSeconds),
                    pageNumber = pRow.getOrNull(PlaybackProgressTable.pageNumber),
                    totalPages = pRow.getOrNull(PlaybackProgressTable.totalPages)
                )
            }

        val streamInfo = MediaStreamInfo(
            durationMs = fileRow.getOrNull(MediaFiles.durationMs),
            resolution = fileRow.getOrNull(MediaFiles.resolution),
            containerFormat = fileRow.getOrNull(MediaFiles.containerFormat),
            videoCodec = fileRow.getOrNull(MediaFiles.videoCodec),
            audioCodec = fileRow.getOrNull(MediaFiles.audioCodec),
            width = fileRow.getOrNull(MediaFiles.width),
            height = fileRow.getOrNull(MediaFiles.height),
            bitrate = fileRow.getOrNull(MediaFiles.bitrate),
            sampleRate = fileRow.getOrNull(MediaFiles.sampleRate)
        )

        val seriesInfo = SeriesInfo(
            seasonNumber = fileRow.getOrNull(MediaFiles.seasonNumber),
            episodeNumber = fileRow.getOrNull(MediaFiles.episodeNumber),
            episodeTitle = fileRow.getOrNull(MediaFiles.episodeTitle),
            plot = fileRow.getOrNull(MediaFiles.plot),
            airDate = fileRow.getOrNull(MediaFiles.airDate)
        )

        val musicInfo = MusicInfo(
            album = fileRow.getOrNull(MediaFiles.album),
            albumArtist = fileRow.getOrNull(MediaFiles.albumArtist),
            albumArtistSortOrder = fileRow.getOrNull(MediaFiles.albumArtistSortOrder),
            artist = fileRow.getOrNull(MediaFiles.artist),
            artistSortOrder = fileRow.getOrNull(MediaFiles.artistSortOrder),
            artists = fileRow.getOrNull(MediaFiles.artists)?.let { a ->
                try {
                    a.trim('[', ']').split(',').map { it.trim(' ', '"') }.filter { it.isNotEmpty() }
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList(),
            trackNumber = fileRow.getOrNull(MediaFiles.trackNumber),
            discNumber = fileRow.getOrNull(MediaFiles.discNumber),
            totalTracks = fileRow.getOrNull(MediaFiles.totalTracks),
            totalDiscs = fileRow.getOrNull(MediaFiles.totalDiscs)
        )

        return MediaFile(
            id = fileId,
            mediaItemId = fileRow.get(MediaFiles.mediaItemId),
            fileName = fileRow.get(MediaFiles.fileName),
            fileSize = fileRow.get(MediaFiles.fileSize),
            title = fileRow.getOrNull(MediaFiles.title) ?: fileRow.getOrNull(MediaFiles.episodeTitle) ?: fileRow.get(MediaFiles.fileName),
            category = fileRow.get(MediaFiles.category),
            index = fileRow.getOrNull(MediaFiles.episodeNumber) ?: fileRow.getOrNull(MediaFiles.trackNumber),
            filePath = fileRow.get(MediaFiles.filePath),
            isHidden = fileRow.get(MediaFiles.isHidden),
            hiddenAt = fileRow.getOrNull(MediaFiles.hiddenAt)?.toString(),
            createdAt = fileRow.get(MediaFiles.createdAt).toString(),
            progress = progress,
            streamInfo = streamInfo,
            seriesInfo = seriesInfo,
            musicInfo = musicInfo
        )
    }

    private fun mapMediaRow(row: ResultRow, includeFiles: Boolean = false, files: List<MediaFile> = emptyList()): Media {
        return Media(
            id = row.get(MediaItems.id),
            libraryId = row.get(MediaItems.libraryId),
            type = LibraryType.fromString(row.get(MediaItems.type)),
            title = row.get(MediaItems.title),
            originalTitle = row.getOrNull(MediaItems.originalTitle),
            sortTitle = row.getOrNull(MediaItems.sortTitle),
            releaseYear = row.getOrNull(MediaItems.releaseYear),
            plot = row.getOrNull(MediaItems.plot),
            rating = row.getOrNull(MediaItems.rating),
            genres = row.getOrNull(MediaItems.genres)?.let { g ->
                try {
                    g.trim('[', ']').split(',').map { it.trim(' ', '"') }.filter { it.isNotEmpty() }
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList(),
            studio = row.getOrNull(MediaItems.studio),
            director = row.getOrNull(MediaItems.director),
            artist = row.getOrNull(MediaItems.artist),
            album = row.getOrNull(MediaItems.album),
            albumArtist = row.getOrNull(MediaItems.albumArtist),
            albumArtistSortOrder = row.getOrNull(MediaItems.albumArtistSortOrder),
            artistSortOrder = row.getOrNull(MediaItems.artistSortOrder),
            posterPath = row.getOrNull(MediaItems.posterPath),
            fanartPath = row.getOrNull(MediaItems.fanartPath),
            totalDurationMs = row.getOrNull(MediaItems.totalDurationMs),
            isHidden = row.get(MediaItems.isHidden),
            hiddenAt = row.getOrNull(MediaItems.hiddenAt)?.toString(),
            createdAt = row.get(MediaItems.createdAt).toString(),
            updatedAt = row.get(MediaItems.updatedAt).toString(),
            lastPlayedAt = row.getOrNull(MediaItems.lastPlayedAt)?.toString(),
            files = files
        )
    }
}
