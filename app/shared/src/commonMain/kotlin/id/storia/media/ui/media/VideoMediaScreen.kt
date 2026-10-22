package id.storia.media.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import id.storia.media.data.model.Media
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass

@Composable
fun VideoMediaContent(media: Media, onVideoClick: (String) -> Unit) {
    val sizeClass = LocalWindowSizeClass.current
    val columns = when (sizeClass) {
        WindowSizeClass.Compact -> 2 // Videos are usually wider, let's try 2 columns for compact
        WindowSizeClass.Expanded -> 4
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(media.mainFiles) { file ->
            val width = file.streamInfo?.width
            val height = file.streamInfo?.height
            val aspectRatio = if (width != null && height != null && (height > 0)) {
                width.toFloat() / height.toFloat()
            } else {
                16f / 9f // Standard video aspect ratio
            }
            
            val imageUrl = KtorClient.getThumbnailUrl(file.id)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onVideoClick(file.id) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                val density = LocalDensity.current
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)) {
                    val widthPx = with(density) { (maxWidth.toPx() * 2f).toInt() }
                    val heightPx = with(density) { (maxHeight.toPx() * 2f).toInt() }
                    
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .size(widthPx, heightPx)
                            .build(),
                        contentDescription = file.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillWidth,
                        filterQuality = FilterQuality.Medium
                    )
                    
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        }
    }
}
