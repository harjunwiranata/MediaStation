package id.storia.media.ui.adaptive

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints

enum class WindowSizeClass {
    Compact,   // Phones in portrait (< 720dp)
    Expanded   // Desktop, tablets, phones in landscape (>= 720dp)
}

val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    WindowSizeClass.Compact
}

@Composable
fun AdaptiveProvider(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val sizeClass = if (maxWidth < 720.dp) {
            WindowSizeClass.Compact
        } else {
            WindowSizeClass.Expanded
        }
        
        CompositionLocalProvider(LocalWindowSizeClass provides sizeClass) {
            content()
        }
    }
}
