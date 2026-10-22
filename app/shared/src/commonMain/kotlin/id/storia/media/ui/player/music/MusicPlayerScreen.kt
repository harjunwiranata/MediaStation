package id.storia.media.ui.player.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import id.storia.media.data.model.PlaybackMetadata
import id.storia.media.data.model.RepeatMode
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel = viewModel(factory = MusicPlayerViewModel.Factory),
    onBackClick: (() -> Unit)? = null
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentMetadata by viewModel.currentMetadata.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()
    val position by viewModel.playbackPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    Scaffold(
        topBar = {
            if (onBackClick != null) {
                TopAppBar(
                    title = { Text(currentMetadata?.albumTitle ?: "Now Playing") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            val sizeClass = LocalWindowSizeClass.current
            
            if (sizeClass == WindowSizeClass.Compact) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PlayerContent(isPlaying, currentMetadata, repeatMode, shuffleEnabled, progress, position, duration, viewModel)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Artwork(currentMetadata)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Info(currentMetadata)
                        Spacer(modifier = Modifier.height(32.dp))
                        PlaybackProgress(progress, position, duration)
                        Spacer(modifier = Modifier.height(32.dp))
                        Controls(isPlaying, repeatMode, shuffleEnabled, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerContent(
    isPlaying: Boolean,
    currentMetadata: PlaybackMetadata?,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    progress: Float,
    position: Long,
    duration: Long,
    viewModel: MusicPlayerViewModel
) {
    Artwork(currentMetadata)
    Spacer(modifier = Modifier.height(32.dp))
    Info(currentMetadata)
    Spacer(modifier = Modifier.height(32.dp))
    PlaybackProgress(progress, position, duration)
    Spacer(modifier = Modifier.height(32.dp))
    Controls(isPlaying, repeatMode, shuffleEnabled, viewModel)
}

@Composable
private fun PlaybackProgress(progress: Float, position: Long, duration: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.small),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0) return "0:00"
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun Artwork(currentMetadata: PlaybackMetadata?) {
    Card(
        modifier = Modifier.size(360.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(currentMetadata?.artworkUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High
        )
    }
}

@Composable
private fun Info(currentMetadata: PlaybackMetadata?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Invisible spacer to balance the favorite button on the right
        Spacer(modifier = Modifier.size(48.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentMetadata?.title?.takeIf { it.isNotBlank() } ?: "No Track Selected",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = currentMetadata?.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }

        IconButton(onClick = { /* TODO: Toggle Favorite */ }, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun Controls(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    viewModel: MusicPlayerViewModel
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        IconButton(onClick = { viewModel.toggleRepeatMode() }) {
            val icon = when (repeatMode) {
                RepeatMode.OFF -> Icons.Default.Repeat
                RepeatMode.ONE -> Icons.Default.RepeatOne
                RepeatMode.ALL -> Icons.Default.Repeat
            }
            val tint = if (repeatMode == RepeatMode.OFF) {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primary
            }
            Icon(
                icon,
                contentDescription = "Repeat",
                modifier = Modifier.size(28.dp),
                tint = tint
            )
        }

        IconButton(onClick = { viewModel.skipPrevious() }) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        FilledIconButton(
            onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(48.dp)
            )
        }

        IconButton(onClick = { viewModel.skipNext() }) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        IconButton(onClick = { viewModel.toggleShuffle() }) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier.size(28.dp),
                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}
