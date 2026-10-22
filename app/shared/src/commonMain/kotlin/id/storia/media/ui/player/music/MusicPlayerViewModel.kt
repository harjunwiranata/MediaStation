package id.storia.media.ui.player.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.storia.media.data.model.PlaybackMetadata
import id.storia.media.data.model.RepeatMode
import id.storia.media.player.SharedMusicPlayerProvider
import kotlinx.coroutines.flow.StateFlow

class MusicPlayerViewModel : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { MusicPlayerViewModel() }
        }
    }

    private val player = SharedMusicPlayerProvider.get()

    val isPlaying: StateFlow<Boolean> = player.isPlaying
    val currentMetadata: StateFlow<PlaybackMetadata?> = player.currentMetadata
    val currentMediaId: StateFlow<String?> = player.currentMediaId
    val currentFileId: StateFlow<String?> = player.currentFileId
    val repeatMode: StateFlow<RepeatMode> = player.repeatMode
    val shuffleEnabled: StateFlow<Boolean> = player.shuffleEnabled
    val playbackProgress: StateFlow<Float> = player.playbackProgress
    val playbackPosition: StateFlow<Long> = player.playbackPosition
    val duration: StateFlow<Long> = player.duration

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun skipNext() {
        player.skipNext()
    }

    fun skipPrevious() {
        player.skipPrevious()
    }

    fun toggleRepeatMode() {
        player.toggleRepeatMode()
    }

    fun toggleShuffle() {
        player.toggleShuffle()
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    fun playMedia(mediaId: String, fileId: String, isSingle: Boolean = false) {
        player.playMedia(mediaId, fileId, isSingle)
    }
}
