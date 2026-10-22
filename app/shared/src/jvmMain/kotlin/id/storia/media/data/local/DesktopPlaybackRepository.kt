package id.storia.media.data.local

import id.storia.media.data.model.PlaybackItem

object DesktopPlaybackRepository : PlaybackRepository {
    private var pendingMediaId: String? = null
    private var pendingItems: List<PlaybackItem> = emptyList()

    override fun setPendingItems(mediaId: String, items: List<PlaybackItem>) {
        pendingMediaId = mediaId
        pendingItems = items
    }

    override fun getPendingItems(mediaId: String): List<PlaybackItem>? {
        return if (pendingMediaId == mediaId) pendingItems else null
    }

    override fun clear() {
        pendingMediaId = null
        pendingItems = emptyList()
    }
}
