package id.storia.media.service.scanner

import id.storia.media.data.model.LibraryType
import id.storia.media.service.NfoData
import java.io.File

class GalleryScanner(onStatusUpdate: (String?, Int?, Int?) -> Unit) : BaseScanner(onStatusUpdate) {
    override suspend fun scan(libraryId: String, root: File) {
        scanDirectory(libraryId, root, root, "")
    }

    private fun scanDirectory(libraryId: String, root: File, currentDir: File, parentPrefix: String) {
        val files = currentDir.listFiles() ?: return
        val galleryFiles = files.filter { (isImageFile(it) || isVideoFile(it)) && !isThumbnailFile(it) }

        // Determine gallery name based on relative path from root
        val currentFolderName = currentDir.name
        val isRoot = currentDir == root
        
        val galleryName = if (isRoot) {
            "" 
        } else {
            if (parentPrefix.isEmpty()) currentFolderName else "$parentPrefix - $currentFolderName"
        }

        if (galleryFiles.isNotEmpty()) {
            val finalTitle = if (galleryName.isEmpty()) currentFolderName else galleryName
            onStatusUpdate("Gallery: $finalTitle", null, null)
            
            // We use processMediaItem which handles existing items and posters
            // We pass NfoData just to set the title we computed
            val mediaItemId = processMediaItem(libraryId, LibraryType.PHOTOBOOK, currentDir, NfoData(title = finalTitle))

            galleryFiles.forEach { file ->
                var width: Int? = null
                var height: Int? = null
                var thumbnail: File? = null

                if (isImageFile(file)) {
                    val dimensions = getImageDimensions(file)
                    width = dimensions?.first
                    height = dimensions?.second
                } else if (isVideoFile(file)) {
                    val dimensions = id.storia.media.util.FFmpegUtil.getVideoDimensions(file)
                    width = dimensions?.first
                    height = dimensions?.second
                    thumbnail = getOrCreateVideoThumbnail(file)
                }
                
                addMediaFile(
                    mediaItemId = mediaItemId,
                    file = file,
                    category = "MAIN",
                    width = width,
                    height = height,
                    thumbnail = thumbnail
                )
            }
        }

        // Recursively scan subdirectories
        files.filter { it.isDirectory }.forEach { subDir ->
            scanDirectory(libraryId, root, subDir, galleryName)
        }
    }

    private fun getImageDimensions(file: File): Pair<Int, Int>? {
        return try {
            // Method 1: Use ImageReader (efficient, doesn't load full image)
            val input = javax.imageio.ImageIO.createImageInputStream(file)
            if (input != null) {
                input.use { iis ->
                    val readers = javax.imageio.ImageIO.getImageReaders(iis)
                    if (readers.hasNext()) {
                        val reader = readers.next()
                        try {
                            reader.input = iis
                            val w = reader.getWidth(0)
                            val h = reader.getHeight(0)
                            if (w > 0 && h > 0) return Pair(w, h)
                        } finally {
                            reader.dispose()
                        }
                    }
                }
            }
            
            // Method 2: Fallback to reading the full image (more robust but heavier)
            val img = javax.imageio.ImageIO.read(file)
            if (img != null) {
                Pair(img.width, img.height)
            } else null
        } catch (e: Exception) {
            println("Error reading dimensions for ${file.name}: ${e.message}")
            null
        }
    }
}
