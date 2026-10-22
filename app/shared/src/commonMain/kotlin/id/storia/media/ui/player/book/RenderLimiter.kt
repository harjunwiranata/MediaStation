package id.storia.media.ui.player.book

import kotlinx.coroutines.sync.Semaphore

object RenderLimiter {
    // Limit to 1 concurrent page render across the whole viewer 
    // to prevent OOM and CPU thrashing on large manhwa files.
    val semaphore = Semaphore(1)
}
