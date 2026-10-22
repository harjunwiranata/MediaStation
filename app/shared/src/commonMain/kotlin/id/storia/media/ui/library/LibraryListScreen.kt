package id.storia.media.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.LocalPlatformContext
import coil3.SingletonImageLoader
import id.storia.media.data.model.Library
import id.storia.media.data.model.LibraryType

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
                    val specialLibs = libraries.filter { it.id.endsWith("_internal") }
                    val standardLibs = libraries.filter { !it.id.endsWith("_internal") }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (specialLibs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Quick Access",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(specialLibs) { library ->
                                val mediaCount = libraryMedia[library.id]?.size ?: 0
                                LibraryCard(
                                    library = library,
                                    mediaCount = mediaCount,
                                    onClick = { onLibraryClick(library.id, library.name) },
                                    onScan = { viewModel.scanLibrary(library.id) },
                                    onDelete = null // Can't delete special libraries
                                )
                            }
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }

                        item {
                            Text(
                                text = "All Libraries",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(standardLibs) { library ->
                            val mediaCount = libraryMedia[library.id]?.size ?: 0
                            LibraryCard(
                                library = library,
                                mediaCount = mediaCount,
                                onClick = { onLibraryClick(library.id, library.name) },
                                onScan = { viewModel.scanLibrary(library.id) },
                                onDelete = { libraryToDelete = library }
                            )
                        }

                        item {
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
fun LibraryCard(
    library: Library,
    mediaCount: Int,
    onClick: () -> Unit,
    onScan: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = library.type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mediaCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "$mediaCount items",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (!library.id.endsWith("_internal")) {
                    Text(
                        text = "Tap to load",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
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
        }
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
