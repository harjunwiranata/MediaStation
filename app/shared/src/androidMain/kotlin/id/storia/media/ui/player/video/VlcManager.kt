package id.storia.media.ui.player.video

import android.content.Context
import org.videolan.libvlc.LibVLC

object VlcManager {
    @Volatile
    private var instance: LibVLC? = null

    fun getInstance(context: Context): LibVLC {
        return instance ?: synchronized(this) {
            instance ?: LibVLC(context.applicationContext, arrayListOf(
                "--no-lua",
                "--no-stats",
                "--avcodec-fast",
                "--no-osd",
                "--drop-late-frames",
                "--skip-frames"
            )).also { instance = it }
        }
    }

    fun release() {
        synchronized(this) {
            instance?.release()
            instance = null
        }
    }
}
