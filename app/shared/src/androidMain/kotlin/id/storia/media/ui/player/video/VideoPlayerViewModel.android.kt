package id.storia.media.ui.player.video

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.storia.media.data.local.SettingsManager
import id.storia.media.data.model.PlaybackProgress
import id.storia.media.data.remote.KtorClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

actual class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private var progressJob: Job? = null

    val libVlc = VlcManager.getInstance(application)
    val mediaPlayer = MediaPlayer(libVlc)

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

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private var isSeeking = false
    private var isInitialized = false

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    _isPlaying.value = true
                    _isBuffering.value = false
                    extractMediaInfo()
                }
                MediaPlayer.Event.Paused -> {
                    _isPlaying.value = false
                    _isBuffering.value = false
                }
                MediaPlayer.Event.Stopped -> {
                    _isPlaying.value = false
                    _isBuffering.value = false
                }
                MediaPlayer.Event.EndReached -> {
                    _isPlaying.value = false
                    _isBuffering.value = false
                }
                MediaPlayer.Event.Buffering -> {
                    val progress = event.buffering
                    _bufferProgress.value = progress / 100f
                    _isBuffering.value = progress < 100f
                }
                MediaPlayer.Event.EncounteredError -> {
                    _isBuffering.value = false
                    _errorMessage.value = "An error occurred during playback"
                }
                MediaPlayer.Event.TimeChanged -> {
                    if (!isSeeking) {
                        _currentTime.value = event.timeChanged
                    }
                }
                MediaPlayer.Event.LengthChanged -> _duration.value = event.lengthChanged
                MediaPlayer.Event.MediaChanged -> extractMediaInfo()
            }
        }
    }

    private fun extractMediaInfo() {
        val infoMap = LinkedHashMap<String, String>()
        try {
            val media = mediaPlayer.media
            if (media != null) {
                val trackCount = media.trackCount
                for (i in 0 until trackCount) {
                    val track = media.getTrack(i) ?: continue
                    when (track) {
                        is IMedia.VideoTrack -> {
                            infoMap["Video Codec"] = track.codec ?: "Unknown"
                            infoMap["Resolution"] = "${track.width}x${track.height}"
                            val fps = if (track.frameRateDen != 0) track.frameRateNum.toFloat() / track.frameRateDen else 0f
                            if (fps > 0) infoMap["FPS"] = "%.2f".format(fps)
                            if (track.bitrate > 0) infoMap["Bitrate"] = "${track.bitrate / 1000} kbps"
                        }
                        is IMedia.AudioTrack -> {
                            infoMap["Audio Codec"] = track.codec ?: "Unknown"
                            infoMap["Audio Channels"] = "${track.channels}"
                            infoMap["Audio Rate"] = "${track.rate} Hz"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            infoMap["Info Error"] = e.localizedMessage ?: "Unknown"
        }

        val currentInfo = _mediaInfo.value
        val merged = LinkedHashMap<String, String>(currentInfo)
        merged.putAll(infoMap)
        _mediaInfo.value = merged
    }

    actual fun loadMedia(mediaId: String, fileId: String, initialLandscape: Boolean, isSingle: Boolean) {
        if (isInitialized) return
        isInitialized = true
        _isLandscape.value = initialLandscape

        viewModelScope.launch {
            try {
                val mediaDetails = KtorClient.service.getMediaDetails(mediaId)
                val file = mediaDetails.files.find { it.id == fileId }
                    ?: mediaDetails.mainFiles.firstOrNull()
                    ?: mediaDetails.files.first()

                val streamUrl = KtorClient.getStreamUrl(file.id)

                _mediaInfo.value = linkedMapOf(
                    "Filename" to file.fileName,
                    "Container Size" to formatFileSize(file.fileSize ?: 0L),
                    "Duration" to formatTimeMs(file.streamInfo?.durationMs ?: 0L)
                )

                val width = file.streamInfo?.width
                val height = file.streamInfo?.height
                if (width != null && height != null) {
                    _isLandscape.value = width > height
                }

                val vlcMedia = Media(libVlc, streamUrl.toUri())
                vlcMedia.setHWDecoderEnabled(true, false)
                vlcMedia.addOption(":network-caching=300")
                vlcMedia.addOption(":clock-jitter=0")

                mediaPlayer.media = vlcMedia
                vlcMedia.release()

                file.progress?.positionSeconds?.let {
                    mediaPlayer.time = it.toLong() * 1000
                }

                mediaPlayer.play()

                val durationSec = file.streamInfo?.durationMs?.toInt()?.div(1000) ?: 0
                startProgressTracking(fileId, { (_currentTime.value / 1000).toInt() }, durationSec)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load media"
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.2f GB".format(gb)
            mb >= 1.0 -> "%.2f MB".format(mb)
            kb >= 1.0 -> "%.2f KB".format(kb)
            else -> "$bytes bytes"
        }
    }

    private fun formatTimeMs(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%d min %d sec".format(mins, secs)
    }

    fun setLandscape(context: Context, landscape: Boolean) {
        val activity = context.findActivity() ?: return
        _isLandscape.value = landscape
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    fun toggleOrientation(context: Context) {
        setLandscape(context, !_isLandscape.value)
    }

    actual fun playPause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        } else {
            mediaPlayer.play()
        }
    }

    actual fun seekTo(timeMs: Long) {
        isSeeking = true
        _currentTime.value = timeMs
    }

    actual fun seekFinished(timeMs: Long) {
        mediaPlayer.time = timeMs
        isSeeking = false
    }

    private fun startProgressTracking(fileId: String, currentPositionSeconds: () -> Int, durationSeconds: Int) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
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
                delay(10000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        mediaPlayer.stop()
        mediaPlayer.release()
    }

    actual companion object {
        actual val Factory: ViewModelProvider.Factory
            get() = TODO("Not yet implemented")
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
