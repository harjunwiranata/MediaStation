package id.storia.media.ui.player.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import id.storia.media.ui.components.ZoomableBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePlayerScreen(
    mediaId: String,
    initialFileId: String,
    onBackClick: () -> Unit,
    viewModel: ImagePlayerViewModel = viewModel(factory = ImagePlayerViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(mediaId, initialFileId) {
        viewModel.loadMedia(mediaId, initialFileId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = uiState) {
            is ImagePlayerUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is ImagePlayerUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color.White)
                }
            }
            is ImagePlayerUiState.Success -> {
                val pages = state.pages
                val initialIndex = state.initialPageIndex

                if (pages.isNotEmpty()) {
                    val pagerState = rememberPagerState(
                        initialPage = initialIndex,
                        pageCount = { pages.size }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        viewModel.updateProgress(pagerState.currentPage)
                    }

                    Scaffold(
                        containerColor = Color.Black,
                        topBar = {
                            TopAppBar(
                                title = { 
                                        Text(
                                            text = pages.getOrNull(pagerState.currentPage)?.name ?: "",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1
                                        ) 
                                },
                                navigationIcon = {
                                    IconButton(onClick = onBackClick) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Black.copy(alpha = 0.4f)
                                )
                            )
                        },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = true
                            ) { pageIndex ->
                                val page = pages[pageIndex]
                                ZoomableBox(
                                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { offset ->
                                                val width = size.width
                                                if (offset.x < width / 3) {
                                                    scope.launch {
                                                        if (pagerState.currentPage > 0) {
                                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                        }
                                                    }
                                                } else if (offset.x > width * 2 / 3) {
                                                    scope.launch {
                                                        if (pagerState.currentPage < pages.size - 1) {
                                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalPlatformContext.current)
                                            .data(page.url)
                                            .crossfade(false)
                                            .size(Size.ORIGINAL)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                        filterQuality = FilterQuality.High
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
