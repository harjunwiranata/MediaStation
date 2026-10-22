package id.storia.media.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import id.storia.media.data.model.Media
import id.storia.media.data.model.MediaFile
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass

@Composable
fun MusicMediaContent(media: Media, onPlayClick: (String) -> Unit) {
    val sizeClass = LocalWindowSizeClass.current
    
    if (sizeClass == WindowSizeClass.Compact) {
        Column(modifier = Modifier.fillMaxSize()) {
            MusicHeader(media)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MusicTrackList(media, onPlayClick, modifier = Modifier.weight(1f))
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.4f).fillMaxHeight().padding(top = 16.dp)) {
                MusicHeader(media)
            }
            VerticalDivider()
            MusicTrackList(media, onPlayClick, modifier = Modifier.weight(0.6f))
        }
    }
}

@Composable
private fun MusicHeader(media: Media) {
    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        val posterUrl = KtorClient.getImageUrl(media.id, "poster")
        Card(
            modifier = Modifier.size(140.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                error = rememberVectorPainter(Icons.Default.BrokenImage)
            )
        }
        
        Column(modifier = Modifier.padding(start = 16.dp).align(Alignment.CenterVertically)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            media.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            media.album?.takeIf { it.isNotBlank() }?.let { album ->
                Text(
                    text = album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = buildString {
                    media.releaseYear?.let { append("$it • ") }
                    append("${media.mainFiles.size} songs")
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MusicTrackList(media: Media, onPlayClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        val sortedFiles = media.mainFiles.sortedWith(
            compareBy<MediaFile> { it.musicInfo?.discNumber ?: 1 }
                .thenBy { it.musicInfo?.trackNumber ?: it.index ?: 0 }
        )
        
        items(sortedFiles) { file ->
            ListItem(
                headlineContent = { Text(file.displayName) },
                supportingContent = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        file.musicInfo?.artist?.takeIf { it.isNotBlank() && it != media.artist }?.let { trackArtist ->
                            Text(
                                text = trackArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(" • ", style = MaterialTheme.typography.bodySmall)
                        }
                        file.streamInfo?.durationMs?.let { ms ->
                            val minutes = ms / 60000
                            val seconds = (ms % 60000) / 1000
                            Text(
                                text = "${minutes}:${seconds.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                leadingContent = {
                    val trackText = when {
                        file.musicInfo?.trackNumber != null -> file.musicInfo?.trackNumber.toString()
                        file.index != null -> file.index.toString()
                        else -> (sortedFiles.indexOf(file) + 1).toString()
                    }
                    Text(
                        text = trackText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.End
                    )
                },
                modifier = Modifier.clickable { onPlayClick(file.id) }
            )
        }
    }
}
