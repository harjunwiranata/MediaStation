package id.storia.media.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastTapTime by remember { mutableStateOf(0L) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val currentTime = System.currentTimeMillis()
                    
                    // Double tap detection
                    if (currentTime - lastTapTime < 300) {
                        scale = when {
                            scale < 2f -> 2.5f
                            scale < 4f -> 5f
                            else -> 1f
                        }
                        if (scale == 1f) offset = Offset.Zero
                        lastTapTime = 0L // Reset after double tap
                        // Consume the down to avoid extra single tap processing
                        down.consume()
                    } else {
                        lastTapTime = currentTime
                    }

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (scale > 1f || zoom != 1f) {
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            
                            if (scale > 1f) {
                                offset += pan
                                // Consume all changes to lock the Pager/Scroll
                                event.changes.forEach { it.consume() }
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()

        val boundX = (maxWidth * (scale - 1f) / 2f).coerceAtLeast(0f)
        val boundY = (maxHeight * (scale - 1f) / 2f).coerceAtLeast(0f)

        val clampedOffset = Offset(
            x = offset.x.coerceIn(-boundX, boundX),
            y = offset.y.coerceIn(-boundY, boundY)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = clampedOffset.x
                    translationY = clampedOffset.y
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
