package id.storia.media.data.remote

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class KtorAssetProvider : AssetProvider {
    override fun getImageUrl(mediaId: String, imageType: String): String {
        return "${KtorClient.getNormalizedBaseUrl()}/api/v1/images/${mediaId.trim()}/${imageType.trim()}"
    }

    override fun getThumbnailUrl(fileId: String): String {
        return "${KtorClient.getNormalizedBaseUrl()}/api/v1/images/${fileId.trim()}/thumbnail"
    }
}

class KtorStreamProvider : StreamProvider {
    override fun getStreamUrl(fileId: String): String {
        return "${KtorClient.getNormalizedBaseUrl()}/api/v1/stream/${fileId.trim()}"
    }

    override fun getFileUrl(fileId: String, fileName: String?): String {
        val baseUrl = "${KtorClient.getNormalizedBaseUrl()}/api/v1/files/${fileId.trim()}/raw"
        return if (fileName != null) "$baseUrl/${fileName.trim()}" else baseUrl
    }
}

object KtorClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    var baseUrl: String = ""

    fun getNormalizedBaseUrl(): String {
        return baseUrl.trim().removeSuffix("/").removeSuffix("/api/v1")
    }

    var assetProvider: AssetProvider = KtorAssetProvider()
    var streamProvider: StreamProvider = KtorStreamProvider()

    fun getImageUrl(mediaId: String, imageType: String = "poster"): String = assetProvider.getImageUrl(mediaId, imageType)
    fun getThumbnailUrl(fileId: String): String = assetProvider.getThumbnailUrl(fileId)
    fun getStreamUrl(fileId: String): String = streamProvider.getStreamUrl(fileId)
    fun getFileUrl(fileId: String, fileName: String? = null): String = streamProvider.getFileUrl(fileId, fileName)

    val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.BODY
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 60000
        }
    }

    // Shared service instance (mutable to allow DirectApiService in local server app)
    var service: ApiService = KtorApiService()
}
