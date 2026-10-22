package id.storia.media

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import id.storia.media.data.local.DesktopPlaybackRepository
import id.storia.media.data.local.PlaybackRepositoryProvider
import id.storia.media.player.SharedMusicPlayerProvider
import id.storia.media.player.DesktopMusicPlayer
import id.storia.media.player.DesktopVlcManager
import id.storia.media.ui.connect.DiscoveredServer
import kotlinx.coroutines.flow.MutableStateFlow
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.PlatformContext

fun main() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .build()
    }
    
    PlaybackRepositoryProvider.set(DesktopPlaybackRepository)
    SharedMusicPlayerProvider.set(DesktopMusicPlayer)

    val discoveredServersFlow = MutableStateFlow<List<DiscoveredServer>>(emptyList())

    val jmdns = try {
        JmDNS.create().apply {
            addServiceListener("_mediastation._tcp.local.", object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    requestServiceInfo(event.type, event.name)
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    discoveredServersFlow.value = discoveredServersFlow.value.filterNot { it.name == event.name }
                }

                override fun serviceResolved(event: ServiceEvent) {
                    val name = event.name
                    val info = event.info
                    val host = info.inetAddresses.firstOrNull()?.hostAddress ?: return
                    val port = info.port

                    val current = discoveredServersFlow.value
                    if (current.none { it.host == host && it.port == port }) {
                        discoveredServersFlow.value = current + DiscoveredServer(name, host, port)
                    }
                }
            })
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    application {
        val servers by discoveredServersFlow.collectAsState()
        val windowState = WindowState(placement = WindowPlacement.Maximized)
        Window(
            onCloseRequest = {
                jmdns?.close()
                DesktopVlcManager.release()
                exitApplication()
            },
            title = "MediaStation",
            state = windowState
        ) {
            App(discoveredServers = servers)
        }
    }
}
