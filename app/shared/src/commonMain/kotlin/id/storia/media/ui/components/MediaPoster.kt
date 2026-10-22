package id.storia.media.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A reusable component to display media posters with a "Fit + Blur" strategy.
 * This ensures that non-portrait images (like square album covers) are fully visible
 * while maintaining a consistent aspect ratio in the UI.
 */
@Composable
fun MediaPoster(
    posterUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.7f,
    oversample: Float = 2f
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { (maxWidth.toPx() * oversample).toInt() }
        val heightPx = with(density) { (maxHeight.toPx() * oversample).toInt() }
        
        val context = LocalPlatformContext.current
        val imageRequest = ImageRequest.Builder(context)
            .data(posterUrl)
            .crossfade(true)
            .size(widthPx, heightPx)
            .build()

        val bgImageRequest = ImageRequest.Builder(context)
            .data(posterUrl)
            .crossfade(true)
            .size(32, 32)
            .build()

        if (!posterUrl.isNullOrBlank()) {
            Box(Modifier.fillMaxSize()) {
                // Background layer: Zoomed-in, low-res blob
                AsyncImage(
                    model = bgImageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(4f) // Heavy zoom to remove recognizable features
                        .blur(80.dp),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    alpha = 0.4f
                )
                
                // Softening overlay to hide pixelation/blockiness on older APIs
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }
            
            // Foreground fit layer: Shows the full image without cropping
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                error = rememberVectorPainter(Icons.Default.BrokenImage)
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
