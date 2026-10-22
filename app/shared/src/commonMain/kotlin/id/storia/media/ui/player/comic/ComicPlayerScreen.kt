package id.storia.media.ui.player.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import id.storia.media.ui.components.ReadingMode
import id.storia.media.ui.components.ZoomableBox
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass
import id.storia.media.ui.player.image.ViewerPage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicPlayerScreen(
    mediaId: String,
    initialFileId: String,
    onBackClick: () -> Unit,
    viewModel: ComicPlayerViewModel = viewModel(factory = ComicPlayerViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val readingMode by viewModel.readingMode.collectAsState()
    val windowSizeClass = LocalWindowSizeClass.current
    val scope = rememberCoroutineScope()
    
    var showReadingModeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId, initialFileId) {
        viewModel.loadMedia(mediaId, initialFileId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = uiState) {
            is ComicPlayerUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is ComicPlayerUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color.White)
                }
            }
            is ComicPlayerUiState.Success -> {
                val pages = state.pages
                val initialIndex = state.initialPageIndex

                if (pages.isNotEmpty()) {
                    val isDoublePageMode = windowSizeClass == WindowSizeClass.Expanded && readingMode != ReadingMode.VERTICAL
                    val spreads = remember(pages, isDoublePageMode) {
                        if (!isDoublePageMode) {
                            pages.map { listOf(it) }
                        } else {
                            val list = mutableListOf<List<ViewerPage>>()
                            list.add(listOf(pages[0]))
                            var i = 1
                            while (i < pages.size) {
                                if (i + 1 < pages.size) {
                                    list.add(listOf(pages[i], pages[i + 1]))
                                    i += 2
                                } else {
                                    list.add(listOf(pages[i]))
                                    i += 1
                                }
                            }
                            list
                        }
                    }

                    val initialSpreadIndex = remember(spreads, initialIndex) {
                        spreads.indexOfFirst { spread ->
                            val firstIdx = pages.indexOf(spread.first())
                            val lastIdx = pages.indexOf(spread.last())
                            initialIndex in firstIdx..lastIdx
                        }.coerceAtLeast(0)
                    }

                    val pagerState = rememberPagerState(
                        initialPage = initialSpreadIndex,
                        pageCount = { spreads.size }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        val spread = spreads.getOrNull(pagerState.currentPage)
                        val firstPage = spread?.firstOrNull()
                        if (firstPage != null) {
                            val pageIndex = pages.indexOf(firstPage)
                            if (pageIndex != -1) {
                                viewModel.updateProgress(pageIndex)
                            }
                        }
                    }
                    
                    val layoutDirection = if (readingMode == ReadingMode.HORIZONTAL_RTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        Scaffold(
                            containerColor = Color.Black,
                            topBar = {
                                TopAppBar(
                                    title = { 
                                        Text(
                                            text = spreads.getOrNull(pagerState.currentPage)?.firstOrNull()?.name ?: "",
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
                                    actions = {
                                        Box {
                                            IconButton(onClick = { showReadingModeMenu = true }) {
                                                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Reading Mode", tint = Color.White)
                                            }
                                            DropdownMenu(
                                                expanded = showReadingModeMenu,
                                                onDismissRequest = { showReadingModeMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Horizontal (LTR)") },
                                                    onClick = { 
                                                        viewModel.setReadingMode(ReadingMode.HORIZONTAL_LTR)
                                                        showReadingModeMenu = false 
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Manga (RTL)") },
                                                    onClick = { 
                                                        viewModel.setReadingMode(ReadingMode.HORIZONTAL_RTL)
                                                        showReadingModeMenu = false 
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Vertical (Manhwa)") },
                                                    onClick = { 
                                                        viewModel.setReadingMode(ReadingMode.VERTICAL)
                                                        showReadingModeMenu = false 
                                                    }
                                                )
                                            }
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
                                when (readingMode) {
                                    ReadingMode.VERTICAL -> {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(pages) { page ->
                                                ZoomableBox {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(LocalPlatformContext.current)
                                                            .data(page.url)
                                                            .crossfade(false)
                                                            .size(Size.ORIGINAL)
                                                            .build(),
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        contentScale = ContentScale.FillWidth,
                                                        filterQuality = FilterQuality.High
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            userScrollEnabled = true
                                        ) { spreadIndex ->
                                            val spread = spreads[spreadIndex]
                                            ZoomableBox(
                                                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onTap = { offset ->
                                                            val width = size.width
                                                            val isRtl = layoutDirection == LayoutDirection.Rtl
                                                            val leftClicked = offset.x < width / 3
                                                            val rightClicked = offset.x > width * 2 / 3
                                                            
                                                            val shouldGoNext = if (isRtl) leftClicked else rightClicked
                                                            val shouldGoPrev = if (isRtl) rightClicked else leftClicked
                                                            
                                                            if (shouldGoNext) {
                                                                scope.launch {
                                                                    if (pagerState.currentPage < spreads.size - 1) {
                                                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                                    }
                                                                }
                                                            } else if (shouldGoPrev) {
                                                                scope.launch {
                                                                    if (pagerState.currentPage > 0) {
                                                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            ) {
                                                Row(modifier = Modifier.fillMaxSize()) {
                                                    spread.forEach { page ->
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                                                .data(page.url)
                                                                .crossfade(false)
                                                                .size(Size.ORIGINAL)
                                                                .build(),
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxHeight().weight(1f),
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
                }
            }
        }
    }
}
