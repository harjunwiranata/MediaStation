package id.storia.media.ui.player.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import id.storia.media.ui.components.ReadingMode

@Composable
actual fun PdfViewer(
    itemId: String,
    filePath: String,
    modifier: Modifier,
    readingMode: ReadingMode
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("PDF Viewer not supported on Desktop yet", color = Color.White)
    }
}
