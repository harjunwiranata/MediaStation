package id.storia.media.player

import id.storia.media.data.model.PlaybackMetadata
import id.storia.media.data.model.RepeatMode
import kotlinx.coroutines.flow.StateFlow

interface SharedMusicPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentMetadata: StateFlow<PlaybackMetadata?>
    val currentMediaId: StateFlow<String?>
    val currentFileId: StateFlow<String?>
    val repeatMode: StateFlow<RepeatMode>
    val shuffleEnabled: StateFlow<Boolean>
    val playbackProgress: StateFlow<Float>
    val playbackPosition: StateFlow<Long>
    val duration: StateFlow<Long>

    fun play()
    fun pause()
    fun skipNext()
    fun skipPrevious()
    fun toggleRepeatMode()
    fun toggleShuffle()
    fun seekTo(position: Long)
    fun playMedia(mediaId: String, fileId: String, isSingle: Boolean = false)
}
