package id.storia.media.data.remote

import id.storia.media.service.LibraryService
import java.io.File

class DirectStreamProvider : StreamProvider {
    override fun getStreamUrl(fileId: String): String {
        val path = LibraryService.getFilePath(fileId)
        return if (path != null && File(path).exists()) path else ""
    }

    override fun getFileUrl(fileId: String, fileName: String?): String {
        val path = if (fileName != null) {
            LibraryService.getPageFile(fileId, fileName)?.absolutePath
        } else {
            LibraryService.getFilePath(fileId)
        }
        return if (path != null && File(path).exists()) path else ""
    }
}
