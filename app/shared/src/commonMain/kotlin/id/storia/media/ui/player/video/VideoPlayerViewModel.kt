package id.storia.media.ui.player.video

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

expect class VideoPlayerViewModel : ViewModel {
    companion object {
        val Factory: androidx.lifecycle.ViewModelProvider.Factory
    }

    val isPlaying: StateFlow<Boolean>
    val currentTime: StateFlow<Long>
    val duration: StateFlow<Long>
    val bufferProgress: StateFlow<Float>
    val errorMessage: StateFlow<String?>
    val mediaInfo: StateFlow<Map<String, String>>
    val isLandscape: StateFlow<Boolean>

    fun loadMedia(mediaId: String, fileId: String, initialLandscape: Boolean = true, isSingle: Boolean = false)
    fun playPause()
    fun seekTo(timeMs: Long)
    fun seekFinished(timeMs: Long)
}
