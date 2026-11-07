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

    private fun isMatchingFile(file: File): Boolean {
        return file.isFile && when (type) {
            // Loose Types only
            LibraryType.VIDEO -> isVideoFile(file)
            LibraryType.AUDIO -> isAudioFile(file)
            LibraryType.IMAGE -> isImageFile(file)
            LibraryType.DOCUMENT -> isBookFile(file)
            LibraryType.MISC -> isMediaFile(file)
            else -> false
        }
    }

    override suspend fun scan(libraryId: String, root: File) {
        // Create a single MediaItem for the entire library/folder
        val mediaItemId = processMediaItem(
            libraryId = libraryId,
            type = type,
            folder = root,
            nfoData = NfoData(title = libraryName)
        )

        root.walkTopDown().forEach { file ->
            if (isMatchingFile(file)) {
                onStatusUpdate(file.name, null, null)
                
                var thumbnail: File? = null
                var width: Int? = null
                var height: Int? = null

                when (type) {
                    LibraryType.VIDEO -> {
                        if (isVideoFile(file)) {
                            thumbnail = getOrCreateVideoThumbnail(file)
                            val dimensions = FFmpegUtil.getVideoDimensions(file)
                            width = dimensions?.first
                            height = dimensions?.second
                        }
                    }
                    LibraryType.AUDIO -> {
                        // Loose Audio handling
                    }
                    LibraryType.IMAGE -> {
                        // Loose Image handling
                    }
                    LibraryType.DOCUMENT -> {
                        // Loose Document handling
                    }
                    LibraryType.MISC -> {
                        if (isVideoFile(file)) {
                            thumbnail = getOrCreateVideoThumbnail(file)
                            val dimensions = FFmpegUtil.getVideoDimensions(file)
                            width = dimensions?.first
                            height = dimensions?.second
                        }
                    }
                    else -> {}
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
