package id.storia.media.data.remote

import id.storia.media.data.model.*
import id.storia.media.service.LibraryService
import id.storia.media.service.ScannerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectApiService : ApiService {

    override suspend fun getLibraries(): List<Library> = withContext(Dispatchers.IO) {
        LibraryService.getAllLibraries()
    }

    override suspend fun getLibrary(id: String): Library = withContext(Dispatchers.IO) {
        LibraryService.getLibraryById(id) ?: throw IllegalArgumentException("Library $id not found")
    }

    override suspend fun createLibrary(name: String, type: LibraryType, paths: List<String>): Library = withContext(Dispatchers.IO) {
        LibraryService.createLibrary(name, type, paths)
    }

    override suspend fun deleteLibrary(id: String): GenericResponse = withContext(Dispatchers.IO) {
        LibraryService.deleteLibrary(id)
        GenericResponse(success = true)
    }

    override suspend fun scanLibrary(id: String): GenericResponse = withContext(Dispatchers.IO) {
        ScannerService.scanLibrary(id)
        GenericResponse(success = true)
    }

    override suspend fun getLibraryMedia(
        id: String,
        search: String?,
        offset: Int?,
        limit: Int?
    ): List<Media> = withContext(Dispatchers.IO) {
        LibraryService.getMediaItemsByLibrary(
            libraryId = id,
            search = search,
            includeHidden = false,
            offset = offset?.toLong() ?: 0L,
            limit = limit ?: 40
        )
    }

    override suspend fun getRecentMedia(limit: Int, offset: Int?): List<Media> = withContext(Dispatchers.IO) {
        LibraryService.getRecentMediaItems(
            limit = limit,
            offset = offset?.toLong() ?: 0L,
            includeHidden = false
        )
    }

    override suspend fun getMediaDetails(id: String): Media = withContext(Dispatchers.IO) {
        LibraryService.getMediaItemDetails(
            itemId = id,
            includeHidden = false
        ) ?: throw IllegalArgumentException("Media $id not found")
    }

    override suspend fun getFileDetails(fileId: String): MediaFile = withContext(Dispatchers.IO) {
        LibraryService.getMediaFileById(fileId) ?: throw IllegalArgumentException("File $fileId not found")
    }

    override suspend fun getFilePages(fileId: String): List<PageInfo> = withContext(Dispatchers.IO) {
        LibraryService.getPages(fileId)
    }

    override suspend fun updatePlaybackProgress(progress: PlaybackProgress): GenericResponse = withContext(Dispatchers.IO) {
        LibraryService.updatePlaybackProgress(
            mediaFileId = progress.mediaFileId,
            clientId = progress.clientId ?: "local_desktop",
            positionSeconds = progress.positionSeconds,
            durationSeconds = progress.durationSeconds ?: 0,
            pageNumber = progress.pageNumber,
            totalPages = progress.totalPages
        )
        GenericResponse(success = true)
    }

    override suspend fun getSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
        LibraryService.getSystemInfo()
    }
}
