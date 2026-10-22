package id.storia.media.data.local

object PlaybackRepositoryProvider {
    private var instance: PlaybackRepository? = null

    fun get(): PlaybackRepository {
        return instance ?: throw IllegalStateException("PlaybackRepository not initialized")
    }

    fun set(repository: PlaybackRepository) {
        instance = repository
    }
}
