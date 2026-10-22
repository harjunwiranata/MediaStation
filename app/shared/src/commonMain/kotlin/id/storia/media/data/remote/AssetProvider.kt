package id.storia.media.data.remote

interface AssetProvider {
    fun getImageUrl(mediaId: String, imageType: String = "poster"): String
    fun getThumbnailUrl(fileId: String): String
}
