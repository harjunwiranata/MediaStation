package id.storia.media.data.mapper

import id.storia.media.data.model.Media
import id.storia.media.data.model.MediaFile
import id.storia.media.data.model.PlaybackItem
import id.storia.media.data.model.PlaybackMetadata
import id.storia.media.data.remote.KtorClient

fun Media.toPlaybackItems(baseUrl: String): List<PlaybackItem> {
    val posterUrl = KtorClient.getImageUrl(id)

    return mainFiles.sortedWith(
        compareBy<MediaFile> { it.musicInfo?.discNumber ?: 0 }
            .thenBy { it.musicInfo?.trackNumber ?: it.index ?: 0 }
    ).map { file ->
        val streamUrl = KtorClient.getStreamUrl(file.id)
        val metadata = PlaybackMetadata(
            title = file.displayName,
            artist = file.musicInfo?.artist ?: artist,
            albumTitle = file.musicInfo?.album ?: album,
            artworkUri = posterUrl
        )

        PlaybackItem(
            mediaId = file.id,
            uri = streamUrl,
            mimeType = if (file.streamInfo?.containerFormat == "mkv") "video/x-matroska" else null,
            metadata = metadata
        )
    }
}
