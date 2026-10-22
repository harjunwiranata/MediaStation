package id.storia.media.util

object MediaFormatUtil {
    private val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "webm", "wmv", "flv", "ogv", "3gp")
    private val audioExtensions = listOf("mp3", "flac", "wav", "ogg", "opus", "aac", "wma", "m4b", "aiff")
    private val bookExtensions = listOf("pdf", "epub", "mobi", "azw", "azw3", "djvu")
    private val comicExtensions = listOf("cbz", "cbr", "cb7", "cbt", "cba")
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "tiff", "tbn", "heic", "heif")
    private val documentExtensions = listOf("txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods", "odp")
    private val subtitleExtensions = listOf("srt", "ass", "ssa", "vtt", "sub", "idx")

    fun isVideoExtension(extension: String): Boolean = videoExtensions.contains(extension.lowercase().trim())
    fun isAudioExtension(extension: String): Boolean = audioExtensions.contains(extension.lowercase().trim())
    fun isBookExtension(extension: String): Boolean = bookExtensions.contains(extension.lowercase().trim())
    fun isComicExtension(extension: String): Boolean = comicExtensions.contains(extension.lowercase().trim())
    fun isImageExtension(extension: String): Boolean = imageExtensions.contains(extension.lowercase().trim())
    fun isDocumentExtension(extension: String): Boolean = documentExtensions.contains(extension.lowercase().trim())
    fun isSubtitleExtension(extension: String): Boolean = subtitleExtensions.contains(extension.lowercase().trim())

    fun getFormat(extension: String): String? {
        val ext = extension.lowercase().trim()
        return when {
            videoExtensions.contains(ext) -> "video"
            audioExtensions.contains(ext) -> "audio"
            imageExtensions.contains(ext) -> "image"
            comicExtensions.contains(ext) -> "comic"
            bookExtensions.contains(ext) || documentExtensions.contains(ext) -> "book"
            else -> null
        }
    }

    fun isVideoFile(fileName: String): Boolean = isVideoExtension(fileName.substringAfterLast(".", ""))
    fun isAudioFile(fileName: String): Boolean = isAudioExtension(fileName.substringAfterLast(".", ""))
    fun isBookFile(fileName: String): Boolean = isBookExtension(fileName.substringAfterLast(".", "")) || isComicExtension(fileName.substringAfterLast(".", ""))
    fun isImageFile(fileName: String): Boolean = isImageExtension(fileName.substringAfterLast(".", ""))
    fun isSubtitleFile(fileName: String): Boolean = isSubtitleExtension(fileName.substringAfterLast(".", ""))
}
