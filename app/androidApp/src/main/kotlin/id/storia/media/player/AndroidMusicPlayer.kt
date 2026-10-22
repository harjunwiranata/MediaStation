package id.storia.media.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import id.storia.media.data.local.AndroidPlaybackRepository
import id.storia.media.data.model.PlaybackItem
import id.storia.media.data.model.PlaybackMetadata
import id.storia.media.data.model.RepeatMode
import id.storia.media.data.remote.KtorClient
import id.storia.media.player.toAndroidMediaItem
import id.storia.media.player.toMediaItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidMusicPlayer(context: Context) : SharedMusicPlayer {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMetadata = MutableStateFlow<PlaybackMetadata?>(null)
    override val currentMetadata: StateFlow<PlaybackMetadata?> = _currentMetadata.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    override val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    private val _currentFileId = MutableStateFlow<String?>(null)
    override val currentFileId: StateFlow<String?> = _currentFileId.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    override val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    override val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                startProgressUpdate()
            } else {
                stopProgressUpdate()
            }
        }
        override fun onMediaMetadataChanged(metadata: Media3Metadata) {
            _currentMetadata.value = metadata.toSharedMetadata()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMetadata.value = mediaItem?.mediaMetadata?.toSharedMetadata()
            _currentFileId.value = mediaItem?.mediaId
            _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            }
        }
    }

    init {
        val appContext = context.applicationContext
        val sessionToken = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                mediaController = controller
                controller?.addListener(playerListener)
                _isPlaying.value = controller?.isPlaying == true
                _currentMetadata.value = controller?.mediaMetadata?.toSharedMetadata()
                _currentFileId.value = controller?.currentMediaItem?.mediaId
                _repeatMode.value = when (controller?.repeatMode) {
                    Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                _shuffleEnabled.value = controller?.shuffleModeEnabled == true
                _duration.value = controller?.duration?.coerceAtLeast(0L) ?: 0L
                if (controller?.isPlaying == true) {
                    startProgressUpdate()
                }
            } catch (e: Exception) {
                // Handle or ignore if cancelled/released
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressJob = scope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null && controller.duration > 0) {
                    val pos = controller.currentPosition
                    val dur = controller.duration
                    _playbackPosition.value = pos
                    _duration.value = dur
                    _playbackProgress.value = pos.toFloat() / dur
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun play() {
        mediaController?.play()
    }

    override fun pause() {
        mediaController?.pause()
    }

    override fun skipNext() {
        mediaController?.seekToNext()
    }

    override fun skipPrevious() {
        mediaController?.seekToPrevious()
    }

    override fun toggleRepeatMode() {
        val current = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = next
    }

    override fun toggleShuffle() {
        mediaController?.shuffleModeEnabled = !(mediaController?.shuffleModeEnabled ?: false)
    }

    override fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    override fun playMedia(mediaId: String, fileId: String, isSingle: Boolean) {
        scope.launch {
            try {
                val controller = mediaController ?: return@launch

                _currentMediaId.value = mediaId
                _currentFileId.value = fileId

                if (controller.currentMediaItem?.mediaId == fileId) {
                    if (!controller.isPlaying) {
                        controller.play()
                    }
                    return@launch
                }
                
                if (isSingle) {
                    val media = KtorClient.service.getMediaDetails(mediaId)
                    val file = media.files.find { it.id == fileId } ?: return@launch
                    val playbackItem = PlaybackItem(
                        mediaId = file.id,
                        uri = "${KtorClient.getNormalizedBaseUrl()}/api/v1/stream/${file.id}",
                        mimeType = if (file.streamInfo?.containerFormat == "mkv") "video/x-matroska" else null,
                        metadata = PlaybackMetadata(
                            title = file.displayName,
                            artist = file.musicInfo?.artist ?: media.artist,
                            albumTitle = file.musicInfo?.album ?: media.album,
                            artworkUri = KtorClient.getImageUrl(media.id)
                        )
                    )
                    controller.setMediaItem(playbackItem.toAndroidMediaItem())
                    controller.prepare()
                    controller.play()
                    return@launch
                }

                val pendingItems = AndroidPlaybackRepository.getPendingItems(mediaId)
                if (pendingItems != null) {
                    val index = pendingItems.indexOfFirst { it.mediaId == fileId }
                    if (index != -1) {
                        val androidItems = pendingItems.map { it.toAndroidMediaItem() }
                        controller.setMediaItems(androidItems, index, 0L)
                        controller.prepare()
                        controller.play()
                        return@launch
                    }
                }

                val media = KtorClient.service.getMediaDetails(mediaId)
                val baseUrl = KtorClient.baseUrl
                val items = media.toMediaItems(baseUrl)
                val index = items.indexOfFirst { it.mediaId == fileId }
                controller.setMediaItems(items, index.coerceAtLeast(0), 0L)
                
                controller.prepare()
                controller.play()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun Media3Metadata.toSharedMetadata(): PlaybackMetadata {
        return PlaybackMetadata(
            title = title?.toString() ?: "",
            artist = artist?.toString(),
            albumTitle = albumTitle?.toString(),
            artworkUri = artworkUri?.toString(),
        )
    }

    fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
