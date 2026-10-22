package id.storia.media.ui.player.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.storia.media.data.model.MediaFile
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.components.ReadingMode

@Composable
expect fun PdfViewer(
    itemId: String,
    filePath: String,
    modifier: Modifier = Modifier,
    readingMode: ReadingMode = ReadingMode.VERTICAL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPlayerScreen(
    mediaId: String,
    initialFileId: String,
    viewModel: BookPlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaList by viewModel.mediaList.collectAsStateWithLifecycle()
    
    val initialIndex = remember(mediaList) {
        val index = mediaList.indexOfFirst { it.id == initialFileId }
        if (index == -1) null else index
    }
    
    if (initialIndex == null || mediaList.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        MediaViewerContent(
            initialIndex = initialIndex,
            mediaList = mediaList,
            onBack = onBack,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaViewerContent(
    initialIndex: Int,
    mediaList: List<MediaFile>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaList.size }
    )

    val currentItem = mediaList.getOrNull(pagerState.currentPage)
    
    val isPagingEnabled = true // Books always allowed to page

    var readingMode by remember(currentItem?.id) { 
        mutableStateOf(ReadingMode.HORIZONTAL_RTL) 
    }
    var showReadingModeMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.background(Color.Black),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(currentItem?.displayName ?: "", color = Color.White, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showReadingModeMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = "Reading Mode",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showReadingModeMenu,
                            onDismissRequest = { showReadingModeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manga (RTL)") },
                                onClick = {
                                    readingMode = ReadingMode.HORIZONTAL_RTL
                                    showReadingModeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manga (LTR)") },
                                onClick = {
                                    readingMode = ReadingMode.HORIZONTAL_LTR
                                    showReadingModeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manhwa (Vertical)") },
                                onClick = {
                                    readingMode = ReadingMode.VERTICAL
                                    showReadingModeMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    titleContentColor = Color.White
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1,
            userScrollEnabled = isPagingEnabled
        ) { page ->
            val item = mediaList[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val fileUrl = KtorClient.getFileUrl(item.id, item.fileName)
                val fileName = item.fileName.lowercase()
                
                when {
                    fileName.endsWith(".pdf") -> PdfViewer(
                        itemId = item.id,
                        filePath = fileUrl,
                        readingMode = readingMode
                    )
                    else -> Text("Unsupported Media Type: $fileName", color = Color.White)
                }
            }
        }
    }
}
