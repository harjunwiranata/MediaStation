package id.storia.media.data.remote

import id.storia.media.data.model.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class KtorApiService : ApiService {
    private val client = KtorClient.client

    private fun HttpRequestBuilder.apiUrl(path: String) {
        val normalizedBaseUrl = KtorClient.getNormalizedBaseUrl()
        if (normalizedBaseUrl.isNotEmpty()) {
            url("$normalizedBaseUrl/api/v1/${path.removePrefix("/")}")
        } else {
            url(path)
        }
    }

    override suspend fun getLibraries(): List<Library> = client.get { apiUrl("libraries") }.body()

    override suspend fun getLibrary(id: String): Library = client.get { apiUrl("libraries/$id") }.body()

    override suspend fun createLibrary(name: String, type: LibraryType, paths: List<String>): Library = client.post {
        apiUrl("libraries")
        contentType(ContentType.Application.Json)
        setBody(CreateLibraryRequest(name, type, paths))
    }.body()

    override suspend fun deleteLibrary(id: String): GenericResponse = client.delete {
        apiUrl("libraries/$id")
    }.body()

    override suspend fun scanLibrary(id: String): GenericResponse = client.post {
        apiUrl("libraries/$id/scan")
    }.body()

    override suspend fun getLibraryMedia(
        id: String,
        search: String?,
        offset: Int?,
        limit: Int?
    ): List<Media> = client.get {
        apiUrl("libraries/$id/media")
        parameter("search", search)
        parameter("offset", offset)
        parameter("limit", limit)
    }.body()

    override suspend fun getRecentMedia(limit: Int, offset: Int?): List<Media> = client.get {
        apiUrl("media/recent")
        parameter("limit", limit)
        parameter("offset", offset)
    }.body()

    override suspend fun getMediaDetails(id: String): Media = client.get { apiUrl("media/$id") }.body()

    override suspend fun getFileDetails(fileId: String): MediaFile = client.get { apiUrl("files/$fileId") }.body()

    override suspend fun getFilePages(fileId: String): List<PageInfo> = client.get { apiUrl("files/$fileId/pages") }.body()

    override suspend fun updatePlaybackProgress(progress: PlaybackProgress): GenericResponse = client.post {
        apiUrl("playback/progress")
        contentType(ContentType.Application.Json)
        setBody(progress)
    }.body()

    override suspend fun getSystemInfo(): SystemInfo = client.get { apiUrl("system/info") }.body()
}
