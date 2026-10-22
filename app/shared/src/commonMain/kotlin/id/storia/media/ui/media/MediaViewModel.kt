package id.storia.media.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.storia.media.data.local.PlaybackRepositoryProvider
import id.storia.media.data.model.LibraryType
import id.storia.media.data.model.Media
import id.storia.media.data.remote.KtorClient
import id.storia.media.data.mapper.toPlaybackItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MediaViewModel : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { MediaViewModel() }
        }
    }

    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Loading)
    val uiState: StateFlow<MediaUiState> = _uiState

    fun loadDetails(mediaId: String) {
        viewModelScope.launch {
            _uiState.value = MediaUiState.Loading
            try {
                val details = KtorClient.service.getMediaDetails(mediaId)
                if (details.type in listOf(LibraryType.MUSIC_ALBUM, LibraryType.AUDIO, LibraryType.PODCAST)) {
                    val items = details.toPlaybackItems(KtorClient.baseUrl)
                    PlaybackRepositoryProvider.get().setPendingItems(mediaId, items)
                }
                _uiState.value = MediaUiState.Success(details)
            } catch (e: Exception) {
                _uiState.value = MediaUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            PlaybackRepositoryProvider.get().clear()
        } catch (e: Exception) {
            // Might not be initialized yet
        }
    }
}

sealed class MediaUiState {
    data object Loading : MediaUiState()
    data class Success(val media: Media) : MediaUiState()
    data class Error(val message: String) : MediaUiState()
}
