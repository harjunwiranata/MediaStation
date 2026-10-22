package id.storia.media.data.remote

import id.storia.media.data.model.*

interface ApiService {
    suspend fun getLibraries(): List<Library>
    suspend fun getLibrary(id: String): Library
    suspend fun createLibrary(name: String, type: LibraryType, paths: List<String>): Library
    suspend fun deleteLibrary(id: String): GenericResponse
    suspend fun scanLibrary(id: String): GenericResponse
    suspend fun getLibraryMedia(
        id: String,
        search: String? = null,
        offset: Int? = null,
        limit: Int? = null
    ): List<Media>
    suspend fun getRecentMedia(limit: Int = 12, offset: Int? = null): List<Media>
    suspend fun getMediaDetails(id: String): Media
    suspend fun getFileDetails(fileId: String): MediaFile
    suspend fun getFilePages(fileId: String): List<PageInfo>
    suspend fun updatePlaybackProgress(progress: PlaybackProgress): GenericResponse
    suspend fun getSystemInfo(): SystemInfo
}
