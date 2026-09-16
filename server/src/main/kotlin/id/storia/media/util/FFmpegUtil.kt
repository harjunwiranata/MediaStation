package id.storia.media.util

import java.io.File
import java.util.concurrent.TimeUnit

object FFmpegUtil {

    private val isFFmpegAvailable by lazy { checkCommand("ffmpeg -version") }
    private val isFFprobeAvailable by lazy { checkCommand("ffprobe -version") }

    fun isAvailable(): Boolean = isFFmpegAvailable && isFFprobeAvailable

    private fun checkCommand(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor(2, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets video dimensions using ffprobe.
     * Returns Pair(width, height) or null if failed.
     */
    fun getVideoDimensions(file: File): Pair<Int, Int>? {
        if (!isFFprobeAvailable) return null
        
        return try {
            val pb = ProcessBuilder(
                "ffprobe", 
                "-v", "error", 
                "-select_streams", "v:0", 
                "-show_entries", "stream=width,height", 
                "-of", "csv=p=0", 
                file.absolutePath
            )
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(5, TimeUnit.SECONDS)
            
            if (output.isNotEmpty()) {
                val parts = output.split(",")
                if (parts.size >= 2) {
                    val width = parts[0].toIntOrNull()
                    val height = parts[1].toIntOrNull()
                    if (width != null && height != null) {
                        return Pair(width, height)
                    }
                }
            }
            null
        } catch (e: Exception) {
            println("Error getting dimensions for ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Generates a thumbnail from a video file using ffmpeg.
     * Takes a frame at 1 second mark (or 0 if very short).
     */
    fun generateThumbnail(videoFile: File, outputFile: File): Boolean {
        if (!isFFmpegAvailable) return false
        
        return try {
            // ffmpeg -i input -ss 00:00:01 -vframes 1 output
            val pb = ProcessBuilder(
                "ffmpeg",
                "-y", // Overwrite output
                "-i", videoFile.absolutePath,
                "-ss", "00:00:01",
                "-vframes", "1",
                "-q:v", "2", // High quality
                outputFile.absolutePath
            )
            pb.redirectErrorStream(true)
            val process = pb.start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            
            process.exitValue() == 0 && outputFile.exists()
        } catch (e: Exception) {
            println("Error generating thumbnail for ${videoFile.name}: ${e.message}")
            false
        }
    }
}
