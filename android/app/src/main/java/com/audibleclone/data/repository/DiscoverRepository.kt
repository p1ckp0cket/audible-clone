package com.audibleclone.data.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audibleclone.data.api.ApiService
import com.audibleclone.model.BookModels.ABBBook
import com.audibleclone.model.BookModels.LibriVoxBook
import com.audibleclone.model.BookModels.LibriVoxResponse
import com.audibleclone.model.BookModels.PaginatedResponse
import com.audibleclone.model.BookModels.TTSTask
import com.audibleclone.model.BookModels.TTSVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File

class DiscoverRepository(
    private val apiService: ApiService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState
    
    // Mock voices for now - in real implementation, fetch from API
    init {
        _uiState.update { it.copy(availableVoices = getMockVoices()) }
    }
    
    fun search(query: String, source: Int) {
        viewModelScope.launch {
            when (source) {
                0 -> searchLibrivox(query)
                1 -> searchAudiobookbay(query)
            }
        }
    }
    
    private fun searchLibrivox(query: String) {
        _uiState.update { it.copy(librivoxIsLoading = true, librivoxError = null) }
        
        try {
            val response: LibriVoxResponse = apiService.searchLibrivox(q = query)
            
            _uiState.update { 
                val books = response.books
                it.copy(
                    librivoxBooks = books,
                    librivoxIsLoading = false,
                    librivoxHasMore = books.size >= 20,
                    librivoxCurrentPage = 1
                )
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    librivoxIsLoading = false,
                    librivoxError = when (e) {
                        is HttpException -> "LibriVox error: ${e.code()}"
                        else -> e.message ?: "Search failed"
                    }
                )
            }
        }
    }
    
    private fun searchAudiobookbay(query: String) {
        _uiState.update { it.copy(abbIsLoading = true, abbError = null) }
        
        try {
            val response: PaginatedResponse<ABBBook> = apiService.searchAudiobookbay(q = query)
            
            _uiState.update { 
                it.copy(
                    abbBooks = response.items,
                    abbIsLoading = false,
                    abbHasMore = response.pagination.total_pages > 1,
                    abbCurrentPage = 1
                )
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    abbIsLoading = false,
                    abbError = when (e) {
                        is HttpException -> "AudioBookBay error: ${e.code()}"
                        else -> e.message ?: "Search failed"
                    }
                )
            }
        }
    }
    
    fun loadMore(source: Int) {
        viewModelScope.launch {
            when (source) {
                0 -> {
                    _uiState.update { it.copy(librivoxIsLoading = true) }
                    try {
                        val nextPage = _uiState.value.librivoxCurrentPage + 1
                        val response: LibriVoxResponse = apiService.searchLibrivox(
                            q = "", // Would need to store last query
                            page = nextPage
                        )
                        _uiState.update { 
                            it.copy(
                                librivoxBooks = it.librivoxBooks + response.books,
                                librivoxIsLoading = false,
                                librivoxHasMore = response.books.size >= 20,
                                librivoxCurrentPage = nextPage
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(librivoxIsLoading = false) }
                    }
                }
                1 -> {
                    _uiState.update { it.copy(abbIsLoading = true) }
                    try {
                        val nextPage = _uiState.value.abbCurrentPage + 1
                        val response: PaginatedResponse<ABBBook> = apiService.searchAudiobookbay(
                            q = "", // Would need to store last query
                            page = nextPage
                        )
                        _uiState.update { 
                            it.copy(
                                abbBooks = it.abbBooks + response.items,
                                abbIsLoading = false,
                                abbHasMore = nextPage < response.pagination.total_pages,
                                abbCurrentPage = nextPage
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(abbIsLoading = false) }
                    }
                }
            }
        }
    }
    
    fun downloadLibrivoxBook(bookId: Int) {
        viewModelScope.launch {
            try {
                val task = apiService.downloadLibrivoxBook(bookId)
                // TODO: Show download progress notification
            } catch (e: Exception) {
                // TODO: Show error
            }
        }
    }
    
    fun downloadAudiobookbayBook(book: ABBBook) {
        viewModelScope.launch {
            try {
                val response = apiService.downloadAudiobookbayBook(
                    com.audibleclone.data.api.ABBDownloadRequest(details_url = book.link)
                )
                // TODO: Show magnet link or auto-add to torrent client
            } catch (e: Exception) {
                // TODO: Show error
            }
        }
    }
    
    fun uploadEbookForTTS(file: File, title: String?, author: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(ttsUploading = true, ttsError = null) }
            
            try {
                val task = apiService.uploadEbookForTTS(
                    file = file,
                    title = title,
                    author = author,
                    language = "en",
                    engine = "auto"
                )
                _uiState.update { it.copy(ttsUploading = false, ttsTaskId = task.id) }
                // TODO: Poll for task status
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        ttsUploading = false,
                        ttsError = e.message ?: "Upload failed"
                    )
                }
            }
        }
    }
    
    private fun getMockVoices(): List<VoicePreview> {
        return listOf(
            // Kokoro voices (premium quality)
            VoicePreview("af", "Adult Female (US)", "kokoro", "en-US", "Premium"),
            VoicePreview("am", "Adult Male (US)", "kokoro", "en-US", "Premium"),
            VoicePreview("bf", "British Female", "kokoro", "en-GB", "Premium"),
            VoicePreview("bm", "British Male", "kokoro", "en-GB", "Premium"),
            // Piper voices (high quality)
            VoicePreview("en_US-lessac-medium", "Lessac (US English, Medium)", "piper", "en-US", "High"),
            VoicePreview("en_US-amy-medium", "Amy (US English, Medium)", "piper", "en-US", "High"),
            VoicePreview("en_US-ryan-medium", "Ryan (US English, Medium)", "piper", "en-US", "High"),
            VoicePreview("en_US-kristin-medium", "Kristin (US English, Medium)", "piper", "en-US", "High"),
            VoicePreview("en_US-joe-medium", "Joe (US English, Medium)", "piper", "en-US", "High"),
            VoicePreview("en_GB-northern_english_male-medium", "Northern English Male (UK, Medium)", "piper", "en-GB", "High"),
            VoicePreview("en_GB-southern_english_female-medium", "Southern English Female (UK, Medium)", "piper", "en-GB", "High"),
            VoicePreview("en_GB-alan-medium", "Alan (UK English, Medium)", "piper", "en-GB", "High"),
        )
    }
}

data class DiscoverUiState(
    // LibriVox
    val librivoxBooks: List<LibriVoxBook> = emptyList(),
    val librivoxIsLoading: Boolean = false,
    val librivoxError: String? = null,
    val librivoxHasMore: Boolean = false,
    val librivoxCurrentPage: Int = 1,
    
    // AudioBookBay
    val abbBooks: List<ABBBook> = emptyList(),
    val abbIsLoading: Boolean = false,
    val abbError: String? = null,
    val abbHasMore: Boolean = false,
    val abbCurrentPage: Int = 1,
    
    // TTS
    val availableVoices: List<VoicePreview> = emptyList(),
    val ttsUploading: Boolean = false,
    val ttsError: String? = null,
    val ttsTaskId: Int? = null,
)