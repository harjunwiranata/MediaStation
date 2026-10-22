package id.storia.media.ui.player.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.storia.media.data.model.PlaybackProgress
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.components.ReadingMode
import id.storia.media.ui.player.image.ViewerPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ComicPlayerUiState {
    data object Loading : ComicPlayerUiState()
    data class Success(val pages: List<ViewerPage>, val initialPageIndex: Int = 0) : ComicPlayerUiState()
    data class Error(val message: String) : ComicPlayerUiState()
}

class ComicPlayerViewModel : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { ComicPlayerViewModel() }
        }
    }

    private val _uiState = MutableStateFlow<ComicPlayerUiState>(ComicPlayerUiState.Loading)
    val uiState: StateFlow<ComicPlayerUiState> = _uiState.asStateFlow()

    private val _readingMode = MutableStateFlow(ReadingMode.HORIZONTAL_LTR)
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    private var currentFileId: String? = null
    private var totalPages: Int = 0

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
    }

    fun loadMedia(mediaId: String, initialFileId: String, isSingle: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = ComicPlayerUiState.Loading
            currentFileId = initialFileId
            try {
                val media = KtorClient.service.getMediaDetails(mediaId)
                val initialFile = media.files.find { it.id == initialFileId }

                _readingMode.value = ReadingMode.HORIZONTAL_RTL
                val remotePages = KtorClient.service.getFilePages(initialFileId)
                totalPages = remotePages.size
                val pages = remotePages.map {
                    ViewerPage(
                        url = KtorClient.getFileUrl(initialFileId, it.name),
                        name = it.name
                    )
                }
                if (media.genres.any { it.contains("manga", ignoreCase = true) }) {
                    _readingMode.value = ReadingMode.HORIZONTAL_RTL
                }
                
                val savedPage = initialFile?.progress?.pageNumber ?: 0
                _uiState.value = ComicPlayerUiState.Success(pages, savedPage)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = ComicPlayerUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateProgress(pageIndex: Int) {
        val fileId = currentFileId ?: return
        if (totalPages <= 0) return

        viewModelScope.launch {
            try {
                KtorClient.service.updatePlaybackProgress(
                    PlaybackProgress(
                        mediaFileId = fileId,
                        positionSeconds = 0,
                        pageNumber = pageIndex,
                        totalPages = totalPages
                    )
                )
            } catch (e: Exception) {
                // Ignore silent progress failures
            }
        }
    }
}
