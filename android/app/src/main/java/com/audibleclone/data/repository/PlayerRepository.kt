package com.audibleclone.data.repository

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.AppDatabase
import com.audibleclone.model.Book
import com.audibleclone.model.BookModels
import com.audibleclone.service.playback.MediaPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/**
 * PlayerRepository - ViewModel for managing playback state and controlling MediaPlaybackService.
 * 
 * This repository bridges the UI with the MediaPlaybackService, providing:
 * - Reactive playback state
 * - Book and chapter loading
 * - Playback control commands
 * - Sync with MediaSession
 */
@HiltViewModel
class PlayerRepository @Inject constructor(
    private val apiService: ApiService,
    private val database: AppDatabase
) : ViewModel() {
    
    companion object {
        const val SKIP_INTERVAL_DEFAULT = 30_000L // 30 seconds in milliseconds
    }
    
    // Playback state
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState
    
    // Media playback service connection
    private var playbackService: MediaPlaybackService? = null
    
    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Load from local cache first
                val cachedBook = database.bookDao().getBookById(bookId)
                
                if (cachedBook != null) {
                    _uiState.update { 
                        it.copy(
                            book = cachedBook,
                            chapters = cachedBook.chapters,
                            currentChapter = cachedBook.chapters.firstOrNull(),
                            isLoading = false,
                            progressSeconds = cachedBook.progressSeconds,
                            completed = cachedBook.completed
                        )
                    }
                }
                
                // Fetch fresh data from API
                val book = apiService.getBook(bookId)
                val chapters = apiService.getBookChapters(bookId)
                
                // Update local cache
                database.bookDao().insertBook(book)
                database.bookDao().insertChapters(chapters)
                
                _uiState.update {
                    it.copy(
                        book = book,
                        chapters = chapters,
                        currentChapter = chapters.firstOrNull(),
                        isLoading = false
                    )
                }
                
                // Notify playback service
                loadBookIntoPlayer(bookId)
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = when (e) {
                            is HttpException -> "Server error: ${e.code()}"
                            else -> e.message ?: "Failed to load book"
                        }
                    )
                }
            }
        }
    }
    
    private fun loadBookIntoPlayer(bookId: Int) {
        // In production: use MediaController to send commands to MediaPlaybackService
        // This would involve binding to the service and using MediaController
    }
    
    /**
     * Play or pause playback
     */
    fun playPause() {
        viewModelScope.launch {
            // Send command to playback service via MediaController
            // mediaController.play() or mediaController.pause()
        }
    }
    
    /**
     * Seek to a specific position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            // Send seek command to playback service
            _uiState.update { it.copy(progressMs = positionMs) }
        }
    }
    
    /**
     * Set playback speed (0.5x to 3.0x)
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        viewModelScope.launch {
            // Send speed command to playback service
            _uiState.update { it.copy(playbackSpeed = clampedSpeed) }
        }
    }
    
    /**
     * Skip forward by 30 seconds
     */
    fun skipForward(seconds: Long = SKIP_INTERVAL_DEFAULT) {
        viewModelScope.launch {
            val newPosition = _uiState.value.progressMs + seconds
            _uiState.update { it.copy(progressMs = newPosition) }
        }
    }
    
    /**
     * Skip backward by 30 seconds
     */
    fun skipBackward(seconds: Long = SKIP_INTERVAL_DEFAULT) {
        viewModelScope.launch {
            val newPosition = (_uiState.value.progressMs - seconds).coerceAtLeast(0L)
            _uiState.update { it.copy(progressMs = newPosition) }
        }
    }
    
    /**
     * Play a specific chapter
     */
    fun playChapter(chapter: BookModels.Chapter) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentChapter = chapter,
                    progressMs = (chapter.startOffsetSeconds * 1000).toLong()
                )
            }
        }
    }
    
    /**
     * Navigate to next chapter
     */
    fun nextChapter() {
        viewModelScope.launch {
            val current = _uiState.value.currentChapter
            val chapters = _uiState.value.chapters
            val currentIndex = chapters.indexOfFirst { it.id == current?.id }
            
            if (currentIndex < chapters.size - 1) {
                val nextChapter = chapters[currentIndex + 1]
                _uiState.update {
                    it.copy(
                        currentChapter = nextChapter,
                        progressMs = (nextChapter.startOffsetSeconds * 1000).toLong()
                    )
                }
            }
        }
    }
    
    /**
     * Navigate to previous chapter
     */
    fun previousChapter() {
        viewModelScope.launch {
            val current = _uiState.value.currentChapter
            val chapters = _uiState.value.chapters
            val currentIndex = chapters.indexOfFirst { it.id == current?.id }
            
            if (currentIndex > 0) {
                val prevChapter = chapters[currentIndex - 1]
                _uiState.update {
                    it.copy(
                        currentChapter = prevChapter,
                        progressMs = (prevChapter.startOffsetSeconds * 1000).toLong()
                    )
                }
            }
        }
    }
    
    /**
     * Start sleep timer
     */
    fun startSleepTimer(minutes: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSleepTimerActive = true, sleepTimerMinutes = minutes) }
            // In production: send command to MediaPlaybackService
        }
    }
    
    /**
     * Stop sleep timer
     */
    fun stopSleepTimer() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSleepTimerActive = false,
                    sleepTimerMinutes = 0
                )
            }
        }
    }
    
    /**
     * Refresh playback position
     */
    fun refreshPosition() {
        val book = _uiState.value.book
        if (book != null) {
            val progressMs = book.progressSeconds * 1000L
            _uiState.update { it.copy(progressMs = progressMs) }
        }
    }
}

/**
 * UI State for the player screen
 */
data class PlayerUiState(
    val book: Book? = null,
    val chapters: List<BookModels.Chapter> = emptyList(),
    val currentChapter: BookModels.Chapter? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val playbackState: Int = 1, // Player.STATE_IDLE = 1, STATE_READY = 3, etc.
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSleepTimerActive: Boolean = false,
    val sleepTimerMinutes: Int = 0,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val bufferedPosition: Long = 0,
    val completed: Boolean = false
) {
    val progressSeconds: Int
        get() = (progressMs / 1000).toInt()
    
    val progressPercent: Float
        get() = if (durationMs > 0) {
            progressMs.toFloat() / durationMs
        } else 0f
    
    val formattedCurrentTime: String
        get() = formatTime(progressMs)
    
    val formattedDuration: String
        get() = formatTime(durationMs)
    
    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }
}