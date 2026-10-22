package id.storia.media.ui.player.video

import androidx.compose.runtime.*

@Composable
expect fun VideoPlayerScreen(
    mediaId: String,
    fileId: String,
    initialLandscape: Boolean = true,
    onBackClick: () -> Unit,
    viewModel: VideoPlayerViewModel
)
