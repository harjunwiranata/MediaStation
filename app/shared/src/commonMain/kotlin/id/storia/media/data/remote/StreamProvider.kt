package id.storia.media.data.remote

interface StreamProvider {
    fun getStreamUrl(fileId: String): String
    fun getFileUrl(fileId: String, fileName: String? = null): String
}
