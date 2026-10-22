package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import java.io.File

class FilmScanner(onStatusUpdate: (String?, Int?, Int?) -> Unit) : BaseScanner(onStatusUpdate) {
    override suspend fun scan(libraryId: String, root: File) {
        val entries = root.listFiles() ?: return
        entries.forEach { entry ->
            if (entry.isDirectory) {
                onStatusUpdate("Movie Folder: ${entry.name}", null, null)
                
                val files = entry.listFiles() ?: emptyArray()
                val nfoFile = files.find { it.name.equals("movie.nfo", ignoreCase = true) || it.extension.equals("nfo", ignoreCase = true) }
                val nfoData = nfoFile?.let { parseNfo(it) }
                
                val mediaItemId = processMediaItem(libraryId, LibraryType.FILM, entry, nfoData)
                
                val videos = files.filter { isVideoFile(it) }
                val images = files.filter { isImageFile(it) }
                
                videos.forEach { video ->
                    val category = if (video == videos.first()) "MAIN" else "EXTRA"
                    val thumb = images.find { it.nameWithoutExtension.equals(video.nameWithoutExtension, ignoreCase = true) }
                        ?: getOrCreateVideoThumbnail(video)
                    val epNfoFile = files.find { it.name.equals("${video.nameWithoutExtension}.nfo", ignoreCase = true) }
                    val epNfoData = epNfoFile?.let { parseNfo(it) }

                    addMediaFile(
                        mediaItemId = mediaItemId,
                        file = video,
                        category = category,
                        thumbnail = thumb,
                        episodeTitle = epNfoData?.title ?: nfoData?.title,
                        plot = epNfoData?.plot ?: nfoData?.plot,
                        airDate = epNfoData?.aired ?: nfoData?.aired
                    )
                    
                    val matchedSubs = files.filter { isSubtitleFile(it) && it.nameWithoutExtension.lowercase().startsWith(video.nameWithoutExtension.lowercase()) }
                    processSubtitles(video.absolutePath, matchedSubs)
                }
                
                files.filter { it.isFile && isMediaFile(it) && !isVideoFile(it) && !isMetadataFile(it) && !isSubtitleFile(it) }.forEach { file ->
                    addMediaFile(mediaItemId, file, category = "EXTRA")
                }
            } else if (entry.isFile && isVideoFile(entry)) {
                onStatusUpdate("Movie: ${entry.name}", null, null)
                
                val siblings = root.listFiles() ?: emptyArray()
                val nfoFile = siblings.find { it.name.equals("${entry.nameWithoutExtension}.nfo", ignoreCase = true) }
                val nfoData = nfoFile?.let { parseNfo(it) }
                
                val posterFile = siblings.find { isPosterFile(it, entry.nameWithoutExtension) }
                val thumbFile = getOrCreateVideoThumbnail(entry)
                
                val mediaItemId = processMediaItemFromFile(libraryId, LibraryType.FILM, entry, nfoData, posterFile?.absolutePath)
                addMediaFile(mediaItemId, entry, category = "MAIN", thumbnail = thumbFile, episodeTitle = nfoData?.title, plot = nfoData?.plot, airDate = nfoData?.aired)
                
                val matchedSubs = siblings.filter { isSubtitleFile(it) && it.nameWithoutExtension.lowercase().startsWith(entry.nameWithoutExtension.lowercase()) }
                processSubtitles(entry.absolutePath, matchedSubs)
            }
        }
    }
}
