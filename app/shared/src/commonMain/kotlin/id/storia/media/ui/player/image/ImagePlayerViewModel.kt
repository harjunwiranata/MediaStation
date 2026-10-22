package id.storia.media.ui.player.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.storia.media.data.model.PlaybackProgress
import id.storia.media.data.remote.KtorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class ViewerPage(
    val url: String,
    val name: String,
    val id: String? = null
)

sealed class ImagePlayerUiState {
    data object Loading : ImagePlayerUiState()
    data class Success(val pages: List<ViewerPage>, val initialPageIndex: Int = 0) : ImagePlayerUiState()
    data class Error(val message: String) : ImagePlayerUiState()
}

class ImagePlayerViewModel : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { ImagePlayerViewModel() }
        }
    }

    private val _uiState = MutableStateFlow<ImagePlayerUiState>(ImagePlayerUiState.Loading)
    val uiState: StateFlow<ImagePlayerUiState> = _uiState.asStateFlow()

    private var currentFileId: String? = null
    private var totalPages: Int = 0

    fun loadMedia(mediaId: String, initialFileId: String, isSingle: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = ImagePlayerUiState.Loading
            currentFileId = initialFileId
            try {
                val media = KtorClient.service.getMediaDetails(mediaId)
                val files = if (isSingle) {
                    media.files.filter { it.id == initialFileId }
                } else {
                    media.mainFiles
                }
                
                val pages = files.map {
                    ViewerPage(
                        url = KtorClient.getStreamUrl(it.id),
                        name = it.title ?: it.fileName,
                        id = it.id
                    )
                }
                totalPages = pages.size
                val initialPageIndex = pages.indexOfFirst { it.id == initialFileId }.coerceAtLeast(0)
                _uiState.value = ImagePlayerUiState.Success(pages, initialPageIndex)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = ImagePlayerUiState.Error(e.message ?: "Unknown error")
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
