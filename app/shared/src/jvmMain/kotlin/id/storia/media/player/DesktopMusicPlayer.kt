package id.storia.media.player

import id.storia.media.data.local.PlaybackRepositoryProvider
import id.storia.media.data.model.PlaybackItem
import id.storia.media.data.model.PlaybackMetadata
import id.storia.media.data.model.RepeatMode
import id.storia.media.data.remote.KtorClient
import id.storia.media.data.mapper.toPlaybackItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import kotlin.time.Duration.Companion.milliseconds

object DesktopMusicPlayer : SharedMusicPlayer {
    private val scope = CoroutineScope(Dispatchers.Default)
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

    private val mediaPlayer get() = DesktopVlcManager.mediaPlayer

    private var playlist: List<PlaybackItem> = emptyList()
    private var shuffledPlaylist: List<PlaybackItem> = emptyList()
    private var currentIndex: Int = -1

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                if (DesktopVlcManager.currentOwner == DesktopMusicPlayer) {
                    _isPlaying.value = true
                    _duration.value = mediaPlayer.status().length()
                    startProgressUpdate()
                } else {
                    _isPlaying.value = false
                    stopProgressUpdate()
                }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                stopProgressUpdate()
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                if (!mediaPlayer.status().isPlaying) {
                    _isPlaying.value = false
                    stopProgressUpdate()
                }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                scope.launch {
                    delay(50.milliseconds)
                    handleFinished()
                }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                stopProgressUpdate()
            }
        })
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressJob = scope.launch {
            while (true) {
                val status = mediaPlayer.status()
                val pos = status.time()
                val dur = status.length()
                _playbackPosition.value = pos
                _duration.value = dur
                _playbackProgress.value = status.position()
                delay(1000.milliseconds)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun handleFinished() {
        val currentList = if (_shuffleEnabled.value) shuffledPlaylist else playlist
        if (currentList.isEmpty()) {
            _isPlaying.value = false
            stopProgressUpdate()
            return
        }

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                if (currentIndex != -1) {
                    playTrackAtIndex(currentIndex)
                } else {
                    playTrackAtIndex(0)
                }
            }
            RepeatMode.ALL -> {
                val nextIndex = (currentIndex + 1) % currentList.size
                playTrackAtIndex(nextIndex)
            }
            RepeatMode.OFF -> {
                if (currentIndex + 1 < currentList.size) {
                    playTrackAtIndex(currentIndex + 1)
                } else {
                    _isPlaying.value = false
                    stopProgressUpdate()
                }
            }
        }
    }

    override fun play() {
        if (DesktopVlcManager.currentOwner != DesktopMusicPlayer) {
            if (currentIndex != -1) {
                playTrackAtIndex(currentIndex)
            }
        } else {
            mediaPlayer.controls().play()
        }
    }

    override fun pause() {
        mediaPlayer.controls().pause()
    }

    override fun skipNext() {
        val currentList = if (_shuffleEnabled.value) shuffledPlaylist else playlist
        if (currentList.isNotEmpty()) {
            val nextIndex = (currentIndex + 1) % currentList.size
            playTrackAtIndex(nextIndex)
        }
    }

    override fun skipPrevious() {
        val currentList = if (_shuffleEnabled.value) shuffledPlaylist else playlist
        if (currentList.isNotEmpty()) {
            val prevIndex = if (currentIndex <= 0) currentList.size - 1 else currentIndex - 1
            playTrackAtIndex(prevIndex)
        }
    }

    override fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    override fun toggleShuffle() {
        val currentList = if (_shuffleEnabled.value) shuffledPlaylist else playlist
        val currentItem = if (currentIndex != -1 && currentIndex < currentList.size) {
            currentList[currentIndex]
        } else null

        _shuffleEnabled.value = !_shuffleEnabled.value
        
        if (_shuffleEnabled.value) {
            shuffledPlaylist = playlist.shuffled()
            if (currentItem != null) {
                currentIndex = shuffledPlaylist.indexOf(currentItem)
            }
        } else {
            if (currentItem != null) {
                currentIndex = playlist.indexOf(currentItem)
            }
        }
    }

    override fun seekTo(position: Long) {
        mediaPlayer.controls().setTime(position)
    }

    override fun playMedia(mediaId: String, fileId: String, isSingle: Boolean) {
        scope.launch {
            try {
                if (_currentMediaId.value == mediaId && _currentFileId.value == fileId && currentIndex != -1) {
                    if (!mediaPlayer.status().isPlaying) {
                        play()
                    }
                    return@launch
                }

                val pendingItems = PlaybackRepositoryProvider.get().getPendingItems(mediaId)
                if (!pendingItems.isNullOrEmpty()) {
                    playlist = pendingItems
                    _currentMediaId.value = mediaId
                    updatePlaylistAndPlay(fileId)
                    return@launch
                }

                val media = KtorClient.service.getMediaDetails(mediaId)
                val baseUrl = KtorClient.baseUrl
                val items = media.toPlaybackItems(baseUrl)

                playlist = items
                _currentMediaId.value = mediaId
                updatePlaylistAndPlay(fileId)
            } catch (e: Exception) {
                System.err.println("DesktopMusicPlayer: Error loading media: ${e.message}")
            }
        }
    }

    private fun updatePlaylistAndPlay(fileId: String) {
        if (_shuffleEnabled.value) {
            shuffledPlaylist = playlist.shuffled()
        }
        val currentList = if (_shuffleEnabled.value) shuffledPlaylist else playlist
        val index = currentList.indexOfFirst { it.mediaId == fileId }
        playTrackAtIndex(if (index != -1) index else 0)
    }

    private fun playTrackAtIndex(index: Int) {
        val currentList = if (_shuffleEnabled.value) shuffledPlaylist else playlist
        if (currentList.isEmpty()) return
        
        val safeIndex = if (index < 0 || index >= currentList.size) 0 else index
        currentIndex = safeIndex
        val item = currentList[safeIndex]
        _currentFileId.value = item.mediaId
        _currentMetadata.value = item.metadata

        DesktopVlcManager.currentOwner = DesktopMusicPlayer
        val rawUri = item.uri
        val streamUrl = if (rawUri.startsWith("http://") || rawUri.startsWith("https://")) {
            rawUri
        } else {
            val localFile = java.io.File(rawUri)
            if (localFile.exists()) localFile.absolutePath else rawUri
        }
        mediaPlayer.media().play(streamUrl)
    }

}
