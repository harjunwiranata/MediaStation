package id.storia.media.player

object SharedMusicPlayerProvider {
    private var instance: SharedMusicPlayer? = null

    fun get(): SharedMusicPlayer {
        return instance ?: throw IllegalStateException("SharedMusicPlayer not initialized")
    }

    fun set(player: SharedMusicPlayer) {
        instance = player
    }
}
