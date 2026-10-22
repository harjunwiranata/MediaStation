package id.storia.media.data.model

import androidx.media3.common.MediaItem

sealed class PlaybackRequest {
    data class Music(val mediaItem: MediaItem) : PlaybackRequest()
    data class Video(val mediaItem: MediaItem) : PlaybackRequest()
}
