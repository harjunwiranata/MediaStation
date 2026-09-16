package id.storia.media.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.storia.media.data.model.Media
import id.storia.media.data.model.MediaFile
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass
import id.storia.media.ui.components.MediaPoster

@Composable
fun BookMediaContent(media: Media, onPlayClick: (String) -> Unit) {
    val sizeClass = LocalWindowSizeClass.current
    val columns = when (sizeClass) {
        WindowSizeClass.Compact -> 3
        WindowSizeClass.Expanded -> 5
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                BookHeader(media)
            }

            items(media.mainFiles.sortedBy { it.index ?: 0 }) { file ->
                BookVolumeCard(file, onPlayClick)
            }
            
            if (media.extraFiles.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Extras",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(media.extraFiles) { file ->
                    BookVolumeCard(file, onPlayClick)
                }
            }
        }
    }
@Composable
private fun BookHeader(media: Media) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        val posterUrl = KtorClient.getImageUrl(media.id, "poster")
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            MediaPoster(
                posterUrl = posterUrl,
                contentDescription = media.title,
                modifier = Modifier.width(120.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${media.releaseYear ?: ""} • ${media.genres.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = media.plot ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BookVolumeCard(file: MediaFile, onPlayClick: (String) -> Unit) {
    val thumbnailUrl = file.id.let { KtorClient.getThumbnailUrl(it) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick(file.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column {
            MediaPoster(
                posterUrl = thumbnailUrl,
                contentDescription = file.displayName,
                modifier = Modifier.fillMaxWidth()
            )
            
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
