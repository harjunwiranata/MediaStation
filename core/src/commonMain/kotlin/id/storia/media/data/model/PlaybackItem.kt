package id.storia.media.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackItem(
    val mediaId: String,
    val uri: String,
    val mimeType: String? = null,
    val metadata: PlaybackMetadata
){
    companion object {
    }
}

@Serializable
data class PlaybackMetadata(
    val title: String,
    val artist: String? = null,
    val albumTitle: String? = null,
    val artworkUri: String? = null
)
