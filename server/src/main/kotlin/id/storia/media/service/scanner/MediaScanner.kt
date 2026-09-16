package id.storia.media.service.scanner

import java.io.File

interface MediaScanner {
    suspend fun scan(libraryId: String, root: File)
    suspend fun cleanup(libraryId: String)
}
