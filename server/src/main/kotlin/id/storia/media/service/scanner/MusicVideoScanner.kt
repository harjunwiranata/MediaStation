package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import id.storia.media.util.FFmpegUtil
import java.io.File

class MusicVideoScanner(onStatusUpdate: (String?, Int?, Int?) -> Unit) : BaseScanner(onStatusUpdate) {
    override suspend fun scan(libraryId: String, root: File) {
        val entries = root.listFiles() ?: return
        entries.forEach { entry ->
            if (entry.isDirectory) {
                onStatusUpdate("Music Video Folder: ${entry.name}", null, null)
                val files = entry.listFiles() ?: emptyArray()
                val nfoFile = files.find { it.extension.equals("nfo", ignoreCase = true) }
                val nfoData = nfoFile?.let { parseNfo(it) }

                val mediaItemId = processMediaItem(libraryId, LibraryType.MUSIC_VIDEO, entry, nfoData)
                val videos = files.filter { isVideoFile(it) }

                videos.forEach { video ->
                    val thumb = getOrCreateVideoThumbnail(video)
                    val dimensions = FFmpegUtil.getVideoDimensions(video)
                    addMediaFile(
                        mediaItemId = mediaItemId,
                        file = video,
                        category = "MAIN",
                        thumbnail = thumb,
                        title = video.nameWithoutExtension,
                        width = dimensions?.first,
                        height = dimensions?.second,
                    )
                }
            } else if (entry.isFile && isVideoFile(entry)) {
                onStatusUpdate("Music Video: ${entry.name}", null, null)
                val siblings = root.listFiles() ?: emptyArray()
                val nfoFile = siblings.find { it.name.equals("${entry.nameWithoutExtension}.nfo", ignoreCase = true) }
                val nfoData = nfoFile?.let { parseNfo(it) }
                val thumbFile = getOrCreateVideoThumbnail(entry)
                val dimensions = FFmpegUtil.getVideoDimensions(entry)

                val mediaItemId = processMediaItemFromFile(libraryId, LibraryType.MUSIC_VIDEO, entry, nfoData)
                addMediaFile(
                    mediaItemId = mediaItemId,
                    file = entry,
                    category = "MAIN",
                    thumbnail = thumbFile,
                    title = nfoData?.title ?: entry.nameWithoutExtension,
                    width = dimensions?.first,
                    height = dimensions?.second,
                )
            }
        }
    }
}
