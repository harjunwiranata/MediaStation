package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import id.storia.media.service.NfoData
import id.storia.media.util.FFmpegUtil
import java.io.File

class GenericScanner(
    private val type: LibraryType,
    private val libraryName: String,
    onStatusUpdate: (String?, Int?, Int?) -> Unit
) : BaseScanner(onStatusUpdate) {

    override suspend fun scan(libraryId: String, root: File) {
        // Create a single MediaItem for the entire library/folder
        val mediaItemId = processMediaItem(
            libraryId = libraryId,
            type = type,
            folder = root,
            nfoData = NfoData(title = libraryName)
        )

        root.walkTopDown().forEach { file ->
            if (file.isFile && (isVideoFile(file) || isAudioFile(file) || isImageFile(file) || isBookFile(file))) {
                onStatusUpdate(file.name, null, null)
                
                var thumbnail: File? = null
                var width: Int? = null
                var height: Int? = null

                if (isVideoFile(file)) {
                    thumbnail = getOrCreateVideoThumbnail(file)
                    val dimensions = FFmpegUtil.getVideoDimensions(file)
                    width = dimensions?.first
                    height = dimensions?.second
                } else if (isImageFile(file)) {
                    // For loose images, we could also get dimensions if we want
                    // but let's stick to video for now as per previous requirement
                }

                addMediaFile(
                    mediaItemId = mediaItemId,
                    file = file,
                    category = "MAIN",
                    thumbnail = thumbnail,
                    width = width,
                    height = height
                )
            }
        }
    }
}
