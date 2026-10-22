package id.storia.media.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

object DesktopVlcManager {
    private var _factory: MediaPlayerFactory? = null
    val factory: MediaPlayerFactory get() {
        if (_factory == null) {
            initVlc()
            _factory = MediaPlayerFactory(
                "--no-video-title-show",
                "-vvv"
            )
        }
        return _factory!!
    }

    private var _mediaPlayer: EmbeddedMediaPlayer? = null
    val mediaPlayer: EmbeddedMediaPlayer get() {
        if (_mediaPlayer == null) {
            _mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
        }
        return _mediaPlayer!!
    }

    var currentOwner: Any? = null

    private fun initVlc() {
        // With vlcj-natives dependency, discovery will automatically find 
        // the extracted natives on the classpath.
        try {
            NativeDiscovery().discover()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            _mediaPlayer?.release()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        try {
            _factory?.release()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        _mediaPlayer = null
        _factory = null
        currentOwner = null
    }
}
