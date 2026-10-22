package id.storia.media

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import id.storia.media.data.local.AndroidPlaybackRepository
import id.storia.media.data.local.PlaybackRepositoryProvider
import id.storia.media.data.remote.DiscoveryManager
import id.storia.media.player.AndroidMusicPlayer
import id.storia.media.player.SharedMusicPlayerProvider
import id.storia.media.ui.connect.DiscoveredServer
import id.storia.media.ui.player.video.VlcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var musicPlayer: AndroidMusicPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pre-initialize LibVLC in background to avoid lag on first video playback
        lifecycleScope.launch(Dispatchers.Default) {
            VlcManager.getInstance(applicationContext)
        }

        PlaybackRepositoryProvider.set(AndroidPlaybackRepository)
        musicPlayer = AndroidMusicPlayer(applicationContext)
        SharedMusicPlayerProvider.set(musicPlayer)
        enableEdgeToEdge()
        setContent {
            val discoveryManager = remember { DiscoveryManager(applicationContext) }
            val servers by discoveryManager.discoveredServers.collectAsState()

            LaunchedEffect(Unit) {
                discoveryManager.startDiscovery()
            }

            DisposableEffect(Unit) {
                onDispose {
                    discoveryManager.stopDiscovery()
                }
            }

            val sharedServers = servers.map { DiscoveredServer(it.name, it.host, it.port) }
            App(discoveredServers = sharedServers)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        musicPlayer.release()
        VlcManager.release()
    }
}
