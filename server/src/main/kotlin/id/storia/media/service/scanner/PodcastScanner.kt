package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import id.storia.media.util.FFmpegUtil
import java.io.File

class PodcastScanner(onStatusUpdate: (String?, Int?, Int?) -> Unit) : BaseScanner(onStatusUpdate) {
    override suspend fun scan(libraryId: String, root: File) {
        val entries = root.listFiles() ?: return
        entries.forEach { entry ->
            if (entry.isDirectory) {
                onStatusUpdate("Podcast Folder: ${entry.name}", null, null)
                val files = entry.listFiles() ?: emptyArray()
                val nfoFile = files.find { it.extension.equals("nfo", ignoreCase = true) }
                val nfoData = nfoFile?.let { parseNfo(it) }

                val mediaItemId = processMediaItem(libraryId, LibraryType.PODCAST, entry, nfoData)
                val mediaFiles = files.filter { isAudioFile(it) || isVideoFile(it) }

                mediaFiles.forEach { file ->
                    var thumb: File? = null
                    var width: Int? = null
                    var height: Int? = null
                    if (isVideoFile(file)) {
                        thumb = getOrCreateVideoThumbnail(file)
                        val dimensions = FFmpegUtil.getVideoDimensions(file)
                        width = dimensions?.first
                        height = dimensions?.second
                    }

                    addMediaFile(
                        mediaItemId = mediaItemId,
                        file = file,
                        category = "MAIN",
                        thumbnail = thumb,
                        title = file.nameWithoutExtension,
                        width = width,
                        height = height,
                    )
                }
            } else if (entry.isFile && (isAudioFile(entry) || isVideoFile(entry))) {
                onStatusUpdate("Podcast: ${entry.name}", null, null)
                val siblings = root.listFiles() ?: emptyArray()
                val nfoFile = siblings.find { it.name.equals("${entry.nameWithoutExtension}.nfo", ignoreCase = true) }
                val nfoData = nfoFile?.let { parseNfo(it) }

                var thumb: File? = null
                var width: Int? = null
                var height: Int? = null
                if (isVideoFile(entry)) {
                    thumb = getOrCreateVideoThumbnail(entry)
                    val dimensions = FFmpegUtil.getVideoDimensions(entry)
                    width = dimensions?.first
                    height = dimensions?.second
                }

                val mediaItemId = processMediaItemFromFile(libraryId, LibraryType.PODCAST, entry, nfoData)
                addMediaFile(
                    mediaItemId = mediaItemId,
                    file = entry,
                    category = "MAIN",
                    thumbnail = thumb,
                    title = nfoData?.title ?: entry.nameWithoutExtension,
                    width = width,
                    height = height,
                )
            }
        }
    }
}
