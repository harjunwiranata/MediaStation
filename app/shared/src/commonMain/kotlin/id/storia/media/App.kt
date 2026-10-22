package id.storia.media

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.components.MusicMiniPlayer
import id.storia.media.ui.connect.ConnectScreen
import id.storia.media.ui.connect.DiscoveredServer
import id.storia.media.ui.library.LibraryDetailScreen
import id.storia.media.ui.library.LibraryListScreen
import id.storia.media.ui.media.MediaScreen
import id.storia.media.ui.player.image.ImagePlayerScreen
import id.storia.media.ui.player.image.ImagePlayerViewModel
import id.storia.media.ui.player.music.MusicPlayerScreen
import id.storia.media.ui.player.music.MusicPlayerViewModel
import id.storia.media.ui.theme.MediaStationTheme
import id.storia.media.ui.adaptive.AdaptiveProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import id.storia.media.ui.player.book.BookPlayerScreen
import id.storia.media.ui.player.book.BookPlayerViewModel
import id.storia.media.ui.player.video.VideoPlayerScreen
import id.storia.media.ui.player.video.VideoPlayerViewModel

@Serializable
sealed interface Route : NavKey {
    @Serializable data object Connect : Route
    @Serializable data object LibraryList : Route
    @Serializable data class LibraryDetail(val libraryId: String, val libraryName: String) : Route
    @Serializable data class MediaDetail(val mediaId: String) : Route
    @Serializable data class MusicPlayer(val mediaId: String, val fileId: String, val isSingle: Boolean = false) : Route
    @Serializable data class VideoPlayer(val mediaId: String, val fileId: String, val isLandscape: Boolean = true, val isSingle: Boolean = false) : Route
    @Serializable data class ImagePlayer(val mediaId: String, val fileId: String, val isSingle: Boolean = false) : Route
    @Serializable data class ComicPlayer(val mediaId: String, val fileId: String, val isSingle: Boolean = false) : Route
    @Serializable data class BookPlayer(val mediaId: String, val fileId: String, val isSingle: Boolean = false) : Route
}

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Connect::class)
            subclass(Route.LibraryList::class)
            subclass(Route.LibraryDetail::class)
            subclass(Route.MediaDetail::class)
            subclass(Route.MusicPlayer::class)
            subclass(Route.VideoPlayer::class)
            subclass(Route.ImagePlayer::class)
            subclass(Route.ComicPlayer::class)
            subclass(Route.BookPlayer::class)
        }
    }
}

@Composable
fun App(discoveredServers: List<DiscoveredServer> = emptyList(), isLocal: Boolean = false, onServerDashboardClick: (() -> Unit)? = null) {
    MediaStationTheme {
        AdaptiveProvider {
            val startKey = if (isLocal) Route.LibraryList else Route.Connect
            val backStack = rememberNavBackStack(navConfig, startKey as NavKey)
            val currentRoute = backStack.lastOrNull() ?: startKey

            val musicPlayerViewModel = viewModel<MusicPlayerViewModel>(
                factory = MusicPlayerViewModel.Factory
            )
            val currentMetadata by musicPlayerViewModel.currentMetadata.collectAsState()
            val currentMediaId by musicPlayerViewModel.currentMediaId.collectAsState()
            val currentFileId by musicPlayerViewModel.currentFileId.collectAsState()

            Scaffold(
                bottomBar = {
                    val showMiniPlayer = when (currentRoute) {
                        is Route.Connect -> false
                        is Route.MusicPlayer -> false
                        is Route.VideoPlayer -> false
                        is Route.ImagePlayer -> false
                        is Route.ComicPlayer -> false
                        is Route.BookPlayer -> false
                        else -> currentMetadata != null
                    }

                    if (showMiniPlayer) {
                        MusicMiniPlayer(
                            viewModel = musicPlayerViewModel,
                            onClick = {
                                val mediaId = currentMediaId ?: ""
                                val fileId = currentFileId ?: ""
                                backStack.add(Route.MusicPlayer(mediaId, fileId))
                            },
                            onServerDashboardClick = onServerDashboardClick
                        )
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Surface(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val provider = entryProvider<NavKey> {
                        entry<Route.Connect> {
                            ConnectScreen(
                                discoveredServers = discoveredServers,
                                onConnect = { url ->
                                    KtorClient.baseUrl = url
                                    backStack.add(Route.LibraryList)
                                }
                            )
                        }
                        entry<Route.LibraryList> {
                            LibraryListScreen(onLibraryClick = { libraryId, libraryName ->
                                backStack.add(Route.LibraryDetail(libraryId, libraryName))
                            })
                        }
                        entry<Route.LibraryDetail> { route ->
                            LibraryDetailScreen(
                                libraryId = route.libraryId,
                                libraryName = route.libraryName,
                                onMediaClick = { mediaId, isAuto ->
                                    if (isAuto) {
                                        // Replace LibraryDetail with MediaDetail in the backstack
                                        // to avoid the backstack loop and skip the intermediate page
                                        if (backStack.isNotEmpty()) {
                                            backStack.removeAt(backStack.size - 1)
                                        }
                                    }
                                    backStack.add(Route.MediaDetail(mediaId))
                                },
                                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                            )
                        }
                        entry<Route.MediaDetail> { route ->
                            MediaScreen(
                                mediaId = route.mediaId,
                                onPlayClick = { fileId, format, isLandscape, isSingle ->
                                    when (format) {
                                        "audio" -> backStack.add(Route.MusicPlayer(route.mediaId, fileId, isSingle))
                                        "image" -> backStack.add(Route.ImagePlayer(route.mediaId, fileId, isSingle))
                                        "comic" -> backStack.add(Route.ComicPlayer(route.mediaId, fileId, isSingle))
                                        "book" -> backStack.add(Route.BookPlayer(route.mediaId, fileId, isSingle))
                                        "video" -> backStack.add(Route.VideoPlayer(route.mediaId, fileId, isLandscape, isSingle))
                                        else -> { /* Handled by MediaScreen modal */ }
                                    }
                                },
                                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                            )
                        }
                        entry<Route.MusicPlayer> { route ->
                            val viewModel = viewModel<MusicPlayerViewModel>(factory = MusicPlayerViewModel.Factory)
                            LaunchedEffect(route.mediaId, route.fileId) {
                                if (route.mediaId.isNotBlank() && route.fileId.isNotBlank()) {
                                    viewModel.playMedia(route.mediaId, route.fileId, route.isSingle)
                                }
                            }
                            MusicPlayerScreen(
                                viewModel = viewModel,
                                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                            )
                        }
                        entry<Route.VideoPlayer> { route ->
                            val viewModel = if (getPlatform().name.startsWith("Java")) {
                                viewModel<VideoPlayerViewModel>(factory = VideoPlayerViewModel.Factory)
                            } else {
                                viewModel<VideoPlayerViewModel>()
                            }
                            LaunchedEffect(route.mediaId, route.fileId) {
                                viewModel.loadMedia(route.mediaId, route.fileId, route.isLandscape, route.isSingle)
                            }
                            VideoPlayerScreen(
                                mediaId = route.mediaId,
                                fileId = route.fileId,
                                initialLandscape = route.isLandscape,
                                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                                viewModel = viewModel
                            )
                        }
                        entry<Route.ImagePlayer> { route ->
                            val viewModel = viewModel<ImagePlayerViewModel>(factory = ImagePlayerViewModel.Factory)
                            LaunchedEffect(route.mediaId, route.fileId) {
                                viewModel.loadMedia(route.mediaId, route.fileId, route.isSingle)
                            }
                            ImagePlayerScreen(
                                mediaId = route.mediaId,
                                initialFileId = route.fileId,
                                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                                viewModel = viewModel
                            )
                        }
                        entry<Route.ComicPlayer> { route ->
                            val viewModel = viewModel<id.storia.media.ui.player.comic.ComicPlayerViewModel>(factory = id.storia.media.ui.player.comic.ComicPlayerViewModel.Factory)
                            LaunchedEffect(route.mediaId, route.fileId) {
                                viewModel.loadMedia(route.mediaId, route.fileId, route.isSingle)
                            }
                            id.storia.media.ui.player.comic.ComicPlayerScreen(
                                mediaId = route.mediaId,
                                initialFileId = route.fileId,
                                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                                viewModel = viewModel
                            )
                        }
                        entry<Route.BookPlayer> { route ->
                            val viewModel = viewModel<BookPlayerViewModel>(factory = BookPlayerViewModel.Factory)
                            LaunchedEffect(route.mediaId, route.fileId) {
                                val details = KtorClient.service.getMediaDetails(route.mediaId)
                                if (route.isSingle) {
                                    viewModel.loadMedia(details.files.filter { it.id == route.fileId })
                                } else {
                                    viewModel.loadMedia(details.files)
                                }
                            }
                            BookPlayerScreen(
                                mediaId = route.mediaId,
                                initialFileId = route.fileId,
                                viewModel = viewModel,
                                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                            )
                        }
                    }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                        entryProvider = provider,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator()
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun MockScreen(title: String, subtitle: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Text("This screen is currently mocked in shared module.")
            }
        }
    }
}
