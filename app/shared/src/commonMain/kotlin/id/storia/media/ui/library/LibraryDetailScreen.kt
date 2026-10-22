package id.storia.media.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.LocalPlatformContext
import coil3.SingletonImageLoader
import id.storia.media.data.model.Media
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass
import id.storia.media.ui.components.MediaPoster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryDetailScreen(
    libraryId: String,
    libraryName: String,
    onMediaClick: (String, Boolean) -> Unit,
    onBackClick: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
) {
    val libraryMedia by viewModel.libraryMedia.collectAsState()
    val loadStates by viewModel.mediaLoadStates.collectAsState()
    val paginationStates by viewModel.paginationStates.collectAsState()
    
    val media = libraryMedia[libraryId] ?: emptyList()
    val loadState = loadStates[libraryId]
    val pagination = paginationStates[libraryId] ?: PaginationState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(pagination.searchQuery) }

    LaunchedEffect(libraryId) {
        viewModel.loadLibraryMedia(libraryId)
    }

    LaunchedEffect(media, loadState) {
        if (media.size == 1 && loadState is MediaLoadState.Success && searchQuery.isEmpty()) {
            onMediaClick(media.first().id, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search in $libraryName") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                viewModel.performSearch(libraryId, searchQuery)
                            }),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        searchQuery = ""
                                        viewModel.performSearch(libraryId, "")
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    } else {
                        Text(libraryName) 
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { 
                            isSearchActive = false
                            if (searchQuery != pagination.searchQuery) {
                                searchQuery = pagination.searchQuery
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        val context = LocalPlatformContext.current
                        IconButton(onClick = { 
                            SingletonImageLoader.get(context).diskCache?.clear()
                            SingletonImageLoader.get(context).memoryCache?.clear()
                            viewModel.loadLibraryMedia(libraryId, forceRefresh = true) 
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Library")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (loadState) {
                is MediaLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is MediaLoadState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Failed to load items")
                    Text(loadState.message, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadLibraryMedia(libraryId, forceRefresh = true) }) {
                        Text("Retry")
                    }
                }
                else -> {
                    if (media.isEmpty() && loadState is MediaLoadState.Success) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (pagination.searchQuery.isNotEmpty()) "No results found." else "This library is empty.")
                        }
                    } else {
                        MediaGrid(
                            media = media,
                            onMediaClick = { onMediaClick(it, false) },
                            onLoadMore = { viewModel.loadLibraryMedia(libraryId, isNextPage = true) },
                            isLoadingMore = pagination.isLoadingMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaGrid(
    media: List<Media>, 
    onMediaClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean
) {
    val sizeClass = LocalWindowSizeClass.current
    val columns = when (sizeClass) {
        WindowSizeClass.Compact -> 3
        WindowSizeClass.Expanded -> 5
    }
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItemIndex > (totalItemsNumber - 10) && totalItemsNumber > 0
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoadingMore) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(media) { item ->
            MediaCard(item, onClick = { onMediaClick(item.id) })
        }
        
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun MediaCard(item: Media, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            MediaPoster(
                posterUrl = KtorClient.getImageUrl(item.id, "poster"),
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth()
            )
            
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val subtitle = when {
                    !item.artist.isNullOrBlank() -> item.artist
                    item.releaseYear != null -> item.releaseYear.toString()
                    else -> item.type.displayName
                }
                
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
