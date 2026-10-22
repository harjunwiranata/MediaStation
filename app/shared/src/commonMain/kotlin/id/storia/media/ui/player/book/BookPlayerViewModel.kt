package id.storia.media.ui.player.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.storia.media.data.model.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BookPlayerViewModel : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { BookPlayerViewModel() }
        }
    }

    private val _mediaList = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaList: StateFlow<List<MediaFile>> = _mediaList

    fun loadMedia(mediaFiles: List<MediaFile>) {
        _mediaList.value = mediaFiles
    }
}
