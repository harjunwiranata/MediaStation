package id.storia.media.data.remote

import id.storia.media.service.LibraryService
import java.io.File

class DirectAssetProvider : AssetProvider {
    override fun getImageUrl(mediaId: String, imageType: String): String {
        val path = when (imageType) {
            "poster" -> LibraryService.getPosterPath(mediaId)
            "fanart" -> LibraryService.getFanartPath(mediaId)
            "thumbnail" -> LibraryService.getThumbnailPath(mediaId)
            else -> LibraryService.getPosterPath(mediaId)
        }
        return if (path != null && File(path).exists()) path else ""
    }

    override fun getThumbnailUrl(fileId: String): String {
        val thumbPath = LibraryService.getThumbnailPath(fileId)
        if (thumbPath != null && File(thumbPath).exists()) return thumbPath
        val firstPage = LibraryService.getFirstPageFile(fileId)
        if (firstPage != null && firstPage.exists()) return firstPage.absolutePath
        return ""
    }
}
