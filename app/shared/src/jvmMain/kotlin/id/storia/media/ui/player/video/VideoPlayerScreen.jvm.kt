package id.storia.media.ui.player.video

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun VideoPlayerScreen(
    mediaId: String,
    fileId: String,
    initialLandscape: Boolean,
    onBackClick: () -> Unit,
    viewModel: VideoPlayerViewModel
) {
    val errorMessage by viewModel.errorMessage.collectAsState()
    var isFullScreenConfigured by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId, fileId) {
        viewModel.loadMedia(mediaId, fileId, initialLandscape)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SwingPanel(
            factory = {
                viewModel.canvas.apply {
                    isFocusable = true
                    
                    // Add AWT KeyListener to ensure keys work when video surface has focus
                    addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            when (e.keyCode) {
                                KeyEvent.VK_ESCAPE -> {
                                    // Navigate back on EDT immediately
                                    SwingUtilities.invokeLater {
                                        onBackClick()
                                    }
                                    try {
                                        viewModel.release()
                                    } catch (t: Throwable) {
                                        t.printStackTrace()
                                    }
                                }
                                KeyEvent.VK_SPACE -> {
                                    viewModel.playPause()
                                }
                                KeyEvent.VK_ENTER -> {
                                    viewModel.toggleFullScreen()
                                }
                                KeyEvent.VK_LEFT -> {
                                    viewModel.seekRelative(-10000L)
                                }
                                KeyEvent.VK_RIGHT -> {
                                    viewModel.seekRelative(10000L)
                                }
                                KeyEvent.VK_UP -> {
                                    viewModel.adjustVolume(5)
                                }
                                KeyEvent.VK_DOWN -> {
                                    viewModel.adjustVolume(-5)
                                }
                            }
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { component ->
                val window = SwingUtilities.getWindowAncestor(component)
                if (window != null && !isFullScreenConfigured) {
                    isFullScreenConfigured = true
                    viewModel.setFullScreenStrategy(window)
                }
                // Ensure the video surface gets focus so key events are captured
                viewModel.canvas.requestFocusInWindow()
            }
        )
        
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                try {
                                    viewModel.release()
                                } catch (t: Throwable) {
                                    t.printStackTrace()
                                }
                                onBackClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Back to Library")
                        }
                    }
                }
            }
        }
    }
}
