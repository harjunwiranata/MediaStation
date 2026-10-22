package id.storia.media.data.local

import id.storia.media.data.model.PlaybackItem

interface PlaybackRepository {
    fun setPendingItems(mediaId: String, items: List<PlaybackItem>)
    fun getPendingItems(mediaId: String): List<PlaybackItem>?
    fun clear()
}
