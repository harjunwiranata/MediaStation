package id.storia.media.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.storia.media.data.model.Media
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass
import id.storia.media.ui.components.MediaPoster

@Composable
fun MovieMediaContent(media: Media, onPlayClick: (String) -> Unit) {
    val sizeClass = LocalWindowSizeClass.current
    
    if (sizeClass == WindowSizeClass.Compact) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            MovieHeader(media)
            MovieInfo(media, onPlayClick)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            val posterUrl = KtorClient.getImageUrl(media.id, "poster")
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.width(240.dp)
            ) {
                MediaPoster(
                    posterUrl = posterUrl,
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.width(32.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${media.releaseYear ?: ""} • ${media.genres.joinToString(", ")}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                MovieInfo(media, onPlayClick)
            }
        }
    }
}

@Composable
private fun MovieHeader(media: Media) {
    val posterUrl = KtorClient.getImageUrl(media.id, "poster")
    
    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
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

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${media.releaseYear ?: ""} • ${media.genres.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MovieInfo(media: Media, onPlayClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = media.plot ?: "No plot available",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (media.mainFiles.isNotEmpty()) {
            val firstFile = media.mainFiles[0]
            val res = firstFile.derivedResolution
            Button(
                onClick = { onPlayClick(firstFile.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (res != null) "Play ($res)" else "Play")
            }
            
            if (media.mainFiles.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Versions", style = MaterialTheme.typography.titleSmall)
                media.mainFiles.drop(1).forEach { file ->
                    val fileRes = file.derivedResolution
                    OutlinedButton(
                        onClick = { onPlayClick(file.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("${file.displayName}${if (fileRes != null) " - $fileRes" else ""}")
                    }
                }
            }
        } else {
            Text("No files available for playback", color = MaterialTheme.colorScheme.error)
        }
    }
}
