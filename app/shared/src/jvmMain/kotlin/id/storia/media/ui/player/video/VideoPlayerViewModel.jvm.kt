package id.storia.media.ui.player.video

import id.storia.media.data.local.SettingsManager
import id.storia.media.data.model.PlaybackProgress
import id.storia.media.data.remote.KtorClient
import id.storia.media.player.DesktopVlcManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.fullscreen.adaptive.AdaptiveFullScreenStrategy
import java.awt.Canvas
import java.awt.Color

actual class VideoPlayerViewModel : ViewModel() {
    actual companion object {
        actual val Factory: androidx.lifecycle.ViewModelProvider.Factory = viewModelFactory {
            initializer { VideoPlayerViewModel() }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val settingsManager = SettingsManager()
    private var progressJob: Job? = null

    val canvas = Canvas().apply { background = Color.BLACK }
    val mediaPlayer: EmbeddedMediaPlayer get() = DesktopVlcManager.mediaPlayer

    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTime = MutableStateFlow(0L)
    actual val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    actual val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferProgress = MutableStateFlow(0f)
    actual val bufferProgress: StateFlow<Float> = _bufferProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    actual val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mediaInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    actual val mediaInfo: StateFlow<Map<String, String>> = _mediaInfo.asStateFlow()

    private val _isLandscape = MutableStateFlow(true)
    actual val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    private var isSeeking = false

    private var pendingStartPositionMs: Long = 0L
    private var initialSeekApplied = false

    private val eventListener = object : MediaPlayerEventAdapter() {
        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            if (!isSeeking) {
                _currentTime.value = newTime
            }
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            _duration.value = newLength
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            if (DesktopVlcManager.currentOwner == this@VideoPlayerViewModel) {
                _isPlaying.value = true
                if (!initialSeekApplied && pendingStartPositionMs > 0) {
                    initialSeekApplied = true
                    val currentPos = mediaPlayer.status().time()
                    if (kotlin.math.abs(currentPos - pendingStartPositionMs) > 2000) {
                        try {
                            mediaPlayer.controls().setTime(pendingStartPositionMs)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                }
            } else {
                _isPlaying.value = false
            }
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            _isPlaying.value = false
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            _isPlaying.value = false
        }

        override fun error(mediaPlayer: MediaPlayer) {
            println("VLC Error event received")
            _errorMessage.value = "VLC Error: Failed to play media format or stream"
        }
    }

    init {
        try {
            mediaPlayer.videoSurface().set(DesktopVlcManager.factory.videoSurfaces().newVideoSurface(canvas))
            mediaPlayer.events().addMediaPlayerEventListener(eventListener)
        } catch (t: Throwable) {
            t.printStackTrace()
            _errorMessage.value = "Failed to initialize video player: ${t.message}"
        }
    }

    actual fun loadMedia(mediaId: String, fileId: String, initialLandscape: Boolean, isSingle: Boolean) {
        scope.launch {
            try {
                val mediaDetails = KtorClient.service.getMediaDetails(mediaId)
                val file = mediaDetails.files.find { it.id == fileId } 
                    ?: mediaDetails.mainFiles.firstOrNull() 
                    ?: mediaDetails.files.first()

                val rawStreamUrl = KtorClient.getStreamUrl(file.id)
                val streamUrl = if (rawStreamUrl.startsWith("http://") || rawStreamUrl.startsWith("https://")) {
                    rawStreamUrl
                } else {
                    val localFile = java.io.File(rawStreamUrl)
                    if (localFile.exists()) localFile.absolutePath else rawStreamUrl
                }

                _mediaInfo.value = linkedMapOf(
                    "Filename" to file.fileName,
                    "Container Size" to "${(file.fileSize ?: 0L) / (1024 * 1024)} MB",
                    "Duration" to "${(file.streamInfo?.durationMs ?: 0L) / 60000} min"
                )

                val startPosition = file.progress?.positionSeconds?.toLong()?.times(1000) ?: 0L
                pendingStartPositionMs = startPosition
                initialSeekApplied = false

                DesktopVlcManager.currentOwner = this@VideoPlayerViewModel
                
                try {
                    val options = mutableListOf(
                        ":network-caching=1000",
                        ":file-caching=1000",
                        ":clock-jitter=0"
                    )
                    if (startPosition > 0) {
                        options.add(":start-time=${startPosition / 1000.0}")
                    }

                    mediaPlayer.videoSurface().set(DesktopVlcManager.factory.videoSurfaces().newVideoSurface(canvas))
                    mediaPlayer.media().play(streamUrl, *options.toTypedArray())
                } catch (t: Throwable) {
                    println("VLC load error: ${t.message}")
                    t.printStackTrace()
                    _errorMessage.value = "VLC Error: ${t.message ?: "Invalid media or memory access failure"}"
                    DesktopVlcManager.release()
                    return@launch
                }

                startProgressTracking(fileId, { 
                    try {
                        (mediaPlayer.status().time() / 1000).toInt()
                    } catch (t: Throwable) {
                        0
                    }
                }, (file.streamInfo?.durationMs ?: 0L).toInt() / 1000)
            } catch (t: Throwable) {
                println("Failed to load media: ${t.message}")
                t.printStackTrace()
                _errorMessage.value = t.message ?: "Failed to load media"
            }
        }
    }

    override fun onCleared() {
        release()
    }

    actual fun playPause() {
        try {
            if (DesktopVlcManager.currentOwner != this) {
                DesktopVlcManager.currentOwner = this
            }
            
            if (mediaPlayer.status().isPlaying) {
                mediaPlayer.controls().pause()
            } else {
                mediaPlayer.controls().play()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun toggleFullScreen() {
        try {
            mediaPlayer.fullScreen().toggle()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setFullScreen(fullscreen: Boolean) {
        try {
            mediaPlayer.fullScreen().set(fullscreen)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private var hasConfiguredStrategy = false
    fun setFullScreenStrategy(window: java.awt.Window) {
        if (hasConfiguredStrategy) return
        try {
            mediaPlayer.fullScreen().strategy(AdaptiveFullScreenStrategy(window))
            hasConfiguredStrategy = true
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun seekRelative(offsetMs: Long) {
        try {
            val newTime = (mediaPlayer.status().time() + offsetMs).coerceIn(0, _duration.value)
            mediaPlayer.controls().setTime(newTime)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun adjustVolume(delta: Int) {
        try {
            val currentVolume = mediaPlayer.audio().volume()
            val newVolume = (currentVolume + delta).coerceIn(0, 100)
            mediaPlayer.audio().setVolume(newVolume)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    actual fun seekTo(timeMs: Long) {
        isSeeking = true
        _currentTime.value = timeMs
    }

    actual fun seekFinished(timeMs: Long) {
        try {
            mediaPlayer.controls().setTime(timeMs)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        isSeeking = false
    }

    private fun startProgressTracking(fileId: String, currentPositionSeconds: () -> Int, durationSeconds: Int) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                try {
                    val position = currentPositionSeconds()
                    KtorClient.service.updatePlaybackProgress(
                        PlaybackProgress(
                            mediaFileId = fileId,
                            clientId = settingsManager.getClientId(),
                            positionSeconds = position,
                            durationSeconds = durationSeconds
                        )
                    )
                } catch (e: Exception) {
                    // Ignore network errors
                }
                delay(10000L)
            }
        }
    }

    private var isReleased = false
    fun release() {
        if (isReleased) return
        isReleased = true
        
        if (DesktopVlcManager.currentOwner == this) {
            DesktopVlcManager.currentOwner = null
        }
        
        progressJob?.cancel()
        
        try {
            mediaPlayer.videoSurface().set(null)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        
        try {
            mediaPlayer.events().removeMediaPlayerEventListener(eventListener)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        
        try {
            if (mediaPlayer.fullScreen().isFullScreen) {
                mediaPlayer.fullScreen().set(false)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            if (mediaPlayer.status().isPlaying) {
                mediaPlayer.controls().stop()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
