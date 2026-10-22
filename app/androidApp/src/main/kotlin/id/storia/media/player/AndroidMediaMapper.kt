package id.storia.media.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import id.storia.media.data.model.Media
import id.storia.media.data.model.PlaybackItem
import androidx.core.net.toUri
import id.storia.media.data.mapper.toPlaybackItems
import id.storia.media.util.MediaFormatUtil

fun PlaybackItem.toAndroidMediaItem(): MediaItem {
    val metadata = metadata
    
    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(metadata.title)
        .setArtist(metadata.artist)
        .setAlbumTitle(metadata.albumTitle)
        .setArtworkUri(metadata.artworkUri?.toUri())
        .setMediaType(
            if (mimeType?.startsWith("video") == true || mimeType == "video/x-matroska") {
                MediaMetadata.MEDIA_TYPE_MOVIE
            } else {
                MediaMetadata.MEDIA_TYPE_MUSIC
            }
        )

    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri.toUri())
        .setMimeType(mimeType)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

fun Media.toMediaItems(baseUrl: String): List<MediaItem> {
    return toPlaybackItems(baseUrl).map { it.toAndroidMediaItem() }
}
