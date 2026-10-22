package id.storia.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import id.storia.media.data.local.DesktopPlaybackRepository
import id.storia.media.data.local.PlaybackRepositoryProvider
import id.storia.media.data.remote.DirectApiService
import id.storia.media.data.remote.DirectAssetProvider
import id.storia.media.data.remote.DirectStreamProvider
import id.storia.media.data.remote.KtorClient
import id.storia.media.gui.ServerManager
import id.storia.media.player.DesktopMusicPlayer
import id.storia.media.player.DesktopVlcManager
import id.storia.media.player.SharedMusicPlayerProvider
import id.storia.media.ui.ServerDashboardScreen
import java.io.File

fun main() {
    try {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(KtorNetworkFetcherFactory())
                }
                .build()
        }

        PlaybackRepositoryProvider.set(DesktopPlaybackRepository)
        SharedMusicPlayerProvider.set(DesktopMusicPlayer)

        // Configure Direct Access for Data, Assets, and Streaming
        KtorClient.service = DirectApiService()
        KtorClient.assetProvider = DirectAssetProvider()
        KtorClient.streamProvider = DirectStreamProvider()

        // Initialize Database (server engine can be started on demand in dashboard)
        ServerManager.initializeDatabase()

        application {
            Window(
                onCloseRequest = {
                    ServerManager.stopServer()
                    DesktopVlcManager.release()
                    exitApplication()
                },
                title = "MediaStation Local Server & GUI"
            ) {
                var isViewingLibrary by remember { mutableStateOf(false) }

                if (!isViewingLibrary) {
                    ServerDashboardScreen(
                        onOpenLibrary = {
                            isViewingLibrary = true
                        }
                    )
                } else {
                    App(
                        isLocal = true,
                        onServerDashboardClick = {
                            isViewingLibrary = false
                        }
                    )
                }
            }
        }
    } catch (e: Throwable) {
        val crashFile = File(System.getProperty("user.home"), ".mediastation/crash.log")
        crashFile.parentFile?.mkdirs()
        crashFile.writeText(e.stackTraceToString())
        throw e
    }
}
