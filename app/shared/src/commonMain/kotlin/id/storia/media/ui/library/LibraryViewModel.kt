package id.storia.media.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.storia.media.data.model.Library
import id.storia.media.data.model.LibraryType
import id.storia.media.data.model.Media
import id.storia.media.data.remote.KtorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class LibraryViewModel : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel() }
        }
    }

    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries: StateFlow<List<Library>> = _libraries

    private val _libraryMedia = MutableStateFlow<Map<String, List<Media>>>(emptyMap())
    val libraryMedia: StateFlow<Map<String, List<Media>>> = _libraryMedia

    // Tracks loading state for each library ID
    private val _mediaLoadStates = MutableStateFlow<Map<String, MediaLoadState>>(emptyMap())
    val mediaLoadStates: StateFlow<Map<String, MediaLoadState>> = _mediaLoadStates

    private val _paginationStates = MutableStateFlow<Map<String, PaginationState>>(emptyMap())
    val paginationStates: StateFlow<Map<String, PaginationState>> = _paginationStates

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                // Ensure service is ready
                val libs = KtorClient.service.getLibraries()
                // Add a "Recent" synthetic library at the start
                val homeLib = Library(id = "recent_internal", name = "Recently Played", type = LibraryType.MISC)
                val allLibs = listOf(homeLib) + libs
                _libraries.value = allLibs
                
                _uiState.value = LibraryUiState.Success
                // Force reload the home media
                loadLibraryMedia(homeLib.id, forceRefresh = true)
            } catch (e: Exception) {
                _uiState.value = LibraryUiState.Error(e.message ?: "Failed to connect to server")
            }
        }
    }

    fun loadLibraryMedia(libraryId: String, forceRefresh: Boolean = false, isNextPage: Boolean = false) {
        val currentState = _mediaLoadStates.value[libraryId]
        val pagination = _paginationStates.value[libraryId] ?: PaginationState()
        
        if (!forceRefresh && !isNextPage && (currentState is MediaLoadState.Success || currentState is MediaLoadState.Loading)) {
            return
        }
        
        if (isNextPage && (pagination.isLastPage || pagination.isLoadingMore)) {
            return
        }

        viewModelScope.launch {
            if (isNextPage) {
                _paginationStates.value = _paginationStates.value + (libraryId to pagination.copy(isLoadingMore = true))
            } else {
                _mediaLoadStates.value = _mediaLoadStates.value + (libraryId to MediaLoadState.Loading)
                // Reset pagination on refresh or search
                _paginationStates.value = _paginationStates.value + (libraryId to pagination.copy(offset = 0, isLastPage = false))
            }

            try {
                val currentPagination = _paginationStates.value[libraryId] ?: PaginationState()
                val limit = 40
                val offset = if (isNextPage) currentPagination.offset else 0
                val search = currentPagination.searchQuery.takeIf { it.isNotBlank() }

                val newItems = if (libraryId == "recent_internal") {
                    KtorClient.service.getRecentMedia(limit = limit, offset = offset)
                } else {
                    KtorClient.service.getLibraryMedia(libraryId, search = search, offset = offset, limit = limit)
                }

                val currentItems = if (isNextPage) _libraryMedia.value[libraryId] ?: emptyList() else emptyList()
                _libraryMedia.value = _libraryMedia.value + (libraryId to (currentItems + newItems))
                
                _mediaLoadStates.value = _mediaLoadStates.value + (libraryId to MediaLoadState.Success)
                _paginationStates.value = _paginationStates.value + (libraryId to currentPagination.copy(
                    offset = offset + newItems.size,
                    isLastPage = newItems.size < limit,
                    isLoadingMore = false
                ))
            } catch (e: Exception) {
                if (isNextPage) {
                    _paginationStates.value = _paginationStates.value + (libraryId to pagination.copy(isLoadingMore = false))
                } else {
                    _mediaLoadStates.value = _mediaLoadStates.value + (libraryId to MediaLoadState.Error(e.message ?: "Network error"))
                }
            }
        }
    }

    fun performSearch(libraryId: String, query: String) {
        val currentPagination = _paginationStates.value[libraryId] ?: PaginationState()
        if (currentPagination.searchQuery == query) return
        
        _paginationStates.value = _paginationStates.value + (libraryId to PaginationState(searchQuery = query))
        loadLibraryMedia(libraryId, forceRefresh = true)
    }

    fun createLibrary(name: String, type: LibraryType, path: String) {
        viewModelScope.launch {
            try {
                KtorClient.service.createLibrary(name, type, listOf(path))
                loadInitialData()
            } catch (e: Exception) {
                // Should probably handle error state for creation
            }
        }
    }

    fun deleteLibrary(id: String) {
        viewModelScope.launch {
            try {
                KtorClient.service.deleteLibrary(id)
                loadInitialData()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun scanLibrary(id: String) {
        viewModelScope.launch {
            try {
                KtorClient.service.scanLibrary(id)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Success : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

sealed class MediaLoadState {
    object Loading : MediaLoadState()
    object Success : MediaLoadState()
    data class Error(val message: String) : MediaLoadState()
}

data class PaginationState(
    val offset: Int = 0,
    val isLastPage: Boolean = false,
    val searchQuery: String = "",
    val isLoadingMore: Boolean = false
)
