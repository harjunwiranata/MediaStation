package id.storia.media.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import id.storia.media.data.model.Media
import id.storia.media.data.model.MediaFile
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass
import kotlinx.coroutines.launch

@Composable
fun SeriesMediaContent(media: Media, onPlayClick: (String) -> Unit) {
    val seasons = media.mainFiles.groupBy { it.seriesInfo?.seasonNumber ?: 1 }.toSortedMap()
    val sizeClass = LocalWindowSizeClass.current

    BoxWithConstraints {
        val visibleItems = when (sizeClass) {
            WindowSizeClass.Compact -> 3
            WindowSizeClass.Expanded -> 5
        }
        val horizontalPadding = 16.dp
        val spacing = 12.dp
        val cardWidth = (maxWidth - (horizontalPadding * 2) - (spacing * (visibleItems - 1))) / visibleItems

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                val fanartUrl = KtorClient.getImageUrl(media.id, "fanart")
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(fanartUrl)
                        .crossfade(true)
                        .size(Size.ORIGINAL)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    error = rememberVectorPainter(Icons.Default.BrokenImage)
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = media.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${media.releaseYear ?: ""} • ${media.genres.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = media.plot ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            seasons.forEach { (seasonNum, episodes) ->
                item {
                    ScrollableMediaRow(
                        title = if (seasonNum > 0) "Season $seasonNum" else "Other",
                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                        spacing = spacing
                    ) {
                        items(episodes.sortedBy { it.seriesInfo?.episodeNumber ?: 0 }) { episode ->
                            EpisodeCard(episode, cardWidth, onPlayClick)
                        }
                    }
                }
            }

            if (media.extraFiles.isNotEmpty()) {
                item {
                    ScrollableMediaRow(
                        title = "Extras",
                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                        spacing = spacing
                    ) {
                        items(media.extraFiles) { file ->
                            EpisodeCard(file, cardWidth, onPlayClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableMediaRow(
    title: String,
    contentPadding: PaddingValues,
    spacing: Dp,
    content: LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val canScrollBackward by remember {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    val canScrollForward by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            if (totalItemsCount == 0) return@derivedStateOf false
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index < totalItemsCount - 1 || 
                    lastVisibleItem.offset + lastVisibleItem.size > layoutInfo.viewportEndOffset
        }
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = state,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxWidth(),
                content = content
            )

            if (canScrollBackward) {
                Surface(
                    onClick = {
                        scope.launch {
                            val viewportWidth = state.layoutInfo.viewportSize.width
                            state.animateScrollBy(-viewportWidth.toFloat() * 0.8f)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left")
                    }
                }
            }

            if (canScrollForward) {
                Surface(
                    onClick = {
                        scope.launch {
                            val viewportWidth = state.layoutInfo.viewportSize.width
                            state.animateScrollBy(viewportWidth.toFloat() * 0.8f)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right")
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(file: MediaFile, width: Dp, onPlayClick: (String) -> Unit) {
    val thumbnailUrl = KtorClient.getThumbnailUrl(file.id)
    
    Card(
        modifier = Modifier
            .width(width)
            .clickable { onPlayClick(file.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.77f)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Default.BrokenImage),
                    filterQuality = FilterQuality.Medium
                )
                
                if (file.seriesInfo?.episodeNumber != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    ) {
                        Text(
                            text = "E${file.seriesInfo?.episodeNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                file.derivedResolution?.let { 
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
