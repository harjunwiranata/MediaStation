package id.storia.media.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.SingletonImageLoader
import id.storia.media.data.model.Library
import id.storia.media.data.model.LibraryType
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.adaptive.LocalWindowSizeClass
import id.storia.media.ui.adaptive.WindowSizeClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListScreen(
    onLibraryClick: (String, String) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val libraries by viewModel.libraries.collectAsState()
    val libraryMedia by viewModel.libraryMedia.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var libraryToDelete by remember { mutableStateOf<Library?>(null) }

    val windowSizeClass = LocalWindowSizeClass.current
    val gridColumns = when (windowSizeClass) {
        WindowSizeClass.Compact -> 2
        WindowSizeClass.Expanded -> 4
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Libraries") },
                actions = {
                    val context = LocalPlatformContext.current
                    IconButton(onClick = { 
                        SingletonImageLoader.get(context).diskCache?.clear()
                        SingletonImageLoader.get(context).memoryCache?.clear()
                        viewModel.loadInitialData() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh & Clear Cache")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = uiState) {
                is LibraryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is LibraryUiState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Connection Error", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadInitialData() }) {
                        Text("Retry Connection")
                    }
                }
                is LibraryUiState.Success -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(libraries, key = { it.id }) { library ->
                            val mediaList = libraryMedia[library.id]
                            val mediaCount = mediaList?.size ?: 0

                            LaunchedEffect(library.id) {
                                viewModel.loadLibraryMedia(library.id)
                            }

                            LibraryGridCard(
                                library = library,
                                mediaCount = mediaCount,
                                posterMediaId = remember(mediaList) { mediaList?.firstOrNull()?.id },
                                onClick = { onLibraryClick(library.id, library.name) },
                                onScan = { viewModel.scanLibrary(library.id) },
                                onDelete = if (!library.id.endsWith("_internal")) { { libraryToDelete = library } } else null
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }, key = "add_library_card") {
                            AddLibraryCard(onClick = { showAddDialog = true })
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddLibraryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, path ->
                viewModel.createLibrary(name, type, path)
                showAddDialog = false
            }
        )
    }

    libraryToDelete?.let { library ->
        AlertDialog(
            onDismissRequest = { libraryToDelete = null },
            title = { Text("Delete Library") },
            text = { Text("Are you sure you want to delete '${library.name}'? This will only remove the library from MediaStation, not your files.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLibrary(library.id)
                        libraryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { libraryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LibraryGridCard(
    library: Library,
    mediaCount: Int,
    posterMediaId: String?,
    onClick: () -> Unit,
    onScan: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val icon = getLibraryIcon(library)
    val posterUrl = remember(posterMediaId) {
        posterMediaId?.let { KtorClient.getImageUrl(it, "poster") }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Layer: Media Poster or Fallback Gradient
            if (!posterUrl.isNullOrBlank()) {
                val context = LocalPlatformContext.current
                val imageRequest = remember(posterUrl) {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(true)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dark Gradient Scrim to ensure high text legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                )
            }

            // Foreground Overlay Content
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Icon Badge & Options Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (posterUrl != null) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = if (posterUrl != null) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = if (posterUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Scan Library") },
                                onClick = {
                                    showMenu = false
                                    onScan()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete Library", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = { Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }

                // Bottom Section: Library Title & Count
                Column {
                    Text(
                        text = library.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (posterUrl != null) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = library.type.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (posterUrl != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (mediaCount > 0) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (posterUrl != null) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "$mediaCount items",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (posterUrl != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getLibraryIcon(library: Library): ImageVector {
    if (library.id.endsWith("_internal")) return Icons.Default.History
    return when (library.type) {
        LibraryType.FILM,
        LibraryType.SERIES,
        LibraryType.MUSIC_VIDEO,
        LibraryType.WEB_VOD,
        LibraryType.BROADCAST,
        LibraryType.VIDEO -> Icons.Default.VideoLibrary

        LibraryType.MUSIC_ALBUM,
        LibraryType.PODCAST,
        LibraryType.AUDIO -> Icons.Default.Headphones

        LibraryType.BOOK,
        LibraryType.COMIC,
        LibraryType.DOCUMENT -> Icons.AutoMirrored.Filled.MenuBook

        LibraryType.PHOTOBOOK,
        LibraryType.IMAGE -> Icons.Default.PhotoLibrary

        LibraryType.MISC -> Icons.Default.Folder
    }
}

@Composable
fun AddLibraryCard(onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Add New Library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AddLibraryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LibraryType, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(LibraryType.FILM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Library Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Folder Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("/path/to/media") }
                )
                
                Text("Library Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LibraryType.entries.filter { !it.name.contains("MISC") && it.name != "MISC" }.forEach { libraryType ->
                        FilterChip(
                            selected = type == libraryType,
                            onClick = { type = libraryType },
                            label = { Text(libraryType.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, type, path) },
                enabled = name.isNotBlank() && path.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
