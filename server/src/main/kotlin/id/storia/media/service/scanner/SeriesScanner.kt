package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import java.io.File

class SeriesScanner(onStatusUpdate: (String?, Int?, Int?) -> Unit) : BaseScanner(onStatusUpdate) {
    override suspend fun scan(libraryId: String, root: File) {
        val showDirs = root.listFiles { it.isDirectory } ?: return
        
        showDirs.forEach { showDir ->
            val showName = showDir.name
            onStatusUpdate("Show: $showName", null, null)
            
            val allFiles = showDir.listFiles() ?: emptyArray()
            val nfoFile = allFiles.find { it.name.equals("tvshow.nfo", ignoreCase = true) }
            val nfoData = nfoFile?.let { parseNfo(it) }
            
            val mediaItemId = processMediaItem(libraryId, LibraryType.SERIES, showDir, nfoData)

            // 2. Process Files in showDir (Extras)
            allFiles.forEach { file ->
                if (file.isFile) {
                    if (isMetadataFile(file) || isSubtitleFile(file)) return@forEach
                    if (!isMediaFile(file)) return@forEach
                    
                    // Look for matching Nfo for extras too
                    val extraNfo = allFiles.find { it.name.equals("${file.nameWithoutExtension}.nfo", ignoreCase = true) }
                    val extraNfoData = extraNfo?.let { parseNfo(it) }
                    
                    addMediaFile(
                        mediaItemId = mediaItemId,
                        file = file,
                        category = "EXTRA",
                        episodeNumber = extraNfoData?.episode,
                        seasonNumber = extraNfoData?.season,
                        episodeTitle = extraNfoData?.title,
                        plot = extraNfoData?.plot,
                        airDate = extraNfoData?.aired
                    )
                }
            }

            // 3. Process Subfolders (Seasons) for MAIN files
            val subDirs = allFiles.filter { it.isDirectory }
            subDirs.forEach { subDir ->
                onStatusUpdate("Folder: ${showName}/${subDir.name}", null, null)
                val subFiles = subDir.listFiles() ?: emptyArray()
                
                val videos = subFiles.filter { isVideoFile(it) }
                val images = subFiles.filter { isImageFile(it) }
                
                videos.forEach { video ->
                    val thumb = images.find { it.nameWithoutExtension.equals(video.nameWithoutExtension, ignoreCase = true) }
                        ?: getOrCreateVideoThumbnail(video)
                    val epNfoFile = subFiles.find { it.name.equals("${video.nameWithoutExtension}.nfo", ignoreCase = true) }
                    val epNfoData = epNfoFile?.let { parseNfo(it) }

                    addMediaFile(
                        mediaItemId = mediaItemId,
                        file = video,
                        category = "MAIN",
                        thumbnail = thumb,
                        episodeNumber = epNfoData?.episode,
                        seasonNumber = epNfoData?.season,
                        episodeTitle = epNfoData?.title,
                        plot = epNfoData?.plot,
                        airDate = epNfoData?.aired
                    )
                    
                    val mediaBase = video.nameWithoutExtension
                    val matchedSubs = subFiles.filter { isSubtitleFile(it) && it.nameWithoutExtension.lowercase().startsWith(mediaBase.lowercase()) }
                    processSubtitles(video.absolutePath, matchedSubs)
                }
            }
        }
    }
}
