package id.storia.media.data.api

interface StreamProvider {
    fun getStreamUrl(fileId: String): String
    fun getFileUrl(fileId: String, fileName: String? = null): String
}
