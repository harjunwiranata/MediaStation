package id.storia.media.data.api

interface AssetProvider {
    fun getImageUrl(mediaId: String, imageType: String = "poster"): String
    fun getThumbnailUrl(fileId: String): String
}
