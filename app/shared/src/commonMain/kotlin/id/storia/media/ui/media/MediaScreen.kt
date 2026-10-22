package id.storia.media.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import id.storia.media.data.model.LibraryType
import id.storia.media.util.MediaFormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    mediaId: String,
    onPlayClick: (String, String, Boolean, Boolean) -> Unit,
    onBackClick: () -> Unit,
    viewModel: MediaViewModel = viewModel(factory = MediaViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    var unsupportedFile by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mediaId) {
        viewModel.loadDetails(mediaId)
    }

    if (unsupportedFile != null) {
        AlertDialog(
            onDismissRequest = { unsupportedFile = null },
            title = { Text("File not recognized") },
            text = { Text("The file '$unsupportedFile' format is not supported.") },
            confirmButton = {
                TextButton(onClick = { unsupportedFile = null }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = uiState) {
                is MediaUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is MediaUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
                is MediaUiState.Success -> {
                    val media = state.media
                    val onPlayFile = { fileId: String ->
                        val file = media.files.find { it.id == fileId }
                        val extension = file?.streamInfo?.containerFormat?.lowercase() ?: file?.fileName?.substringAfterLast(".", "")?.lowercase() ?: ""
                        
                        val detectedFormat = MediaFormatUtil.getFormat(extension)
                        
                        if (detectedFormat == null) {
                            unsupportedFile = file?.fileName ?: "Unknown file"
                        } else {
                            val width = file?.streamInfo?.width
                            val height = file?.streamInfo?.height
                            val isLandscapeVideo = if (width != null && height != null) width > height else true
                            val isSingle = file?.category == "EXTRA"
                            onPlayClick(fileId, detectedFormat, isLandscapeVideo, isSingle)
                        }
                    }
                    when (media.type) {
                        LibraryType.MUSIC_ALBUM, LibraryType.PODCAST -> MusicMediaContent(media, onPlayFile)
                        LibraryType.AUDIO -> AudioMediaContent(media, onPlayFile)
                        LibraryType.SERIES -> SeriesMediaContent(media, onPlayFile)
                        LibraryType.PHOTOBOOK -> PhotobookMediaContent(media, onPlayFile)
                        LibraryType.IMAGE -> ImageMediaContent(media, onPlayFile)
                        LibraryType.VIDEO -> VideoMediaContent(media, onPlayFile)
                        LibraryType.BOOK, LibraryType.COMIC -> BookMediaContent(media, onPlayFile)
                        LibraryType.DOCUMENT -> DocumentMediaContent(media, onPlayFile)
                        else -> MovieMediaContent(media, onPlayFile)
                    }
                }
            }
        }
    }
}
