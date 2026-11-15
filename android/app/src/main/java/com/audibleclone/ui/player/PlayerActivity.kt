package com.audibleclone.ui.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import com.audibleclone.R
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.AppDatabase
import com.audibleclone.data.repository.PlayerRepository
import com.audibleclone.model.Book
import com.audibleclone.model.Chapter
import dagger.assisted.Assisted
import dagger.hilt.androidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.audibleclone.service.playback.MediaPlaybackService
import com.audibleclone.ui.theme.AudibleCloneTheme

/**
 * PlayerActivity - Fullscreen player screen for audiobook playback.
 * Launched from notification or main library screen.
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    
    private val viewModel: PlayerActivityViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Get book ID from intent
        val bookId = intent.getIntExtra(MediaPlaybackService.EXTRA_BOOK_ID, 0)
        
        setContent {
            AudibleCloneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AudibleCloneTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    
                    // Load book if provided
                    LaunchedEffect(bookId) {
                        if (bookId > 0) {
                            viewModel.loadBook(bookId)
                        }
                    }
                    
                    if (uiState.isLoading && uiState.book == null) {
                        LoadingPlayerScreen()
                    } else {
                        PlayerScreenContent(
                            uiState = uiState,
                            onPlayPause = { viewModel.playPause() },
                            onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                            onSpeedChange = { speed -> viewModel.setPlaybackSpeed(speed) },
                            onSkipForward = { viewModel.skipForward() },
                            onSkipBackward = { viewModel.skipBackward() },
                            onChapterClick = { chapter -> viewModel.playChapter(chapter) },
                            onNextChapter = { viewModel.nextChapter() },
                            onPreviousChapter = { viewModel.previousChapter() },
                            onStartSleepTimer = { minutes -> viewModel.startSleepTimer(minutes) },
                            onStopSleepTimer = { viewModel.stopSleepTimer() }
                        )
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle new book to play
        val bookId = intent.getIntExtra(MediaPlaybackService.EXTRA_BOOK_ID, 0)
        if (bookId > 0) {
            viewModel.loadBook(bookId)
        }
    }
}

/**
 * ViewModel for PlayerActivity with proper Hilt integration
 */
@HiltViewModel
class PlayerActivityViewModel @Inject constructor(
    private val apiService: ApiService,
    private val database: AppDatabase
) : androidx.lifecycle.ViewModel() {
    
    private val _uiState = androidx.compose.runtime.mutableStateOf(PlayerUiState())
    val uiState: androidx.compose.runtime.State<PlayerUiState> = _uiState
    
    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            
            try {
                val book = apiService.getBook(bookId)
                val chapters = apiService.getBookChapters(bookId)
                
                database.bookDao().insertBook(book)
                database.bookDao().insertChapters(chapters)
                
                _uiState.value = PlayerUiState(
                    book = book,
                    chapters = chapters,
                    currentChapter = chapters.firstOrNull(),
                    isLoading = false,
                    progressMs = (book.progressSeconds * 1000L),
                    durationMs = (book.durationSeconds?.let { it * 1000L } ?: 0L),
                    playbackSpeed = 1.0f
                )
            } catch (e: Exception) {
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load book"
                )
            }
        }
    }
    
    fun playPause() {
        // Send command to MediaPlaybackService
    }
    
    fun seekTo(positionMs: Long) {
        _uiState.value = _uiState.value.copy(progressMs = positionMs)
    }
    
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        _uiState.value = _uiState.value.copy(playbackSpeed = clampedSpeed)
    }
    
    fun skipForward(seconds: Long = 30_000L) {
        val newPosition = _uiState.value.progressMs + seconds
        _uiState.value = _uiState.value.copy(progressMs = newPosition)
    }
    
    fun skipBackward(seconds: Long = 30_000L) {
        val newPosition = (_uiState.value.progressMs - seconds).coerceAtLeast(0L)
        _uiState.value = _uiState.value.copy(progressMs = newPosition)
    }
    
    fun playChapter(chapter: Chapter) {
        _uiState.value = _uiState.value.copy(
            currentChapter = chapter,
            progressMs = (chapter.startOffsetSeconds * 1000).toLong()
        )
    }
    
    fun nextChapter() {
        val current = _uiState.value.currentChapter
        val chapters = _uiState.value.chapters
        val currentIndex = chapters.indexOfFirst { it.id == current?.id }
        
        if (currentIndex < chapters.size - 1) {
            val nextChapter = chapters[currentIndex + 1]
            _uiState.value = _uiState.value.copy(
                currentChapter = nextChapter,
                progressMs = (nextChapter.startOffsetSeconds * 1000).toLong()
            )
        }
    }
    
    fun previousChapter() {
        val current = _uiState.value.currentChapter
        val chapters = _uiState.value.chapters
        val currentIndex = chapters.indexOfFirst { it.id == current?.id }
        
        if (currentIndex > 0) {
            val prevChapter = chapters[currentIndex - 1]
            _uiState.value = _uiState.value.copy(
                currentChapter = prevChapter,
                progressMs = (prevChapter.startOffsetSeconds * 1000).toLong()
            )
        }
    }
    
    fun startSleepTimer(minutes: Int) {
        _uiState.value = _uiState.value.copy(isSleepTimerActive = true, sleepTimerMinutes = minutes)
    }
    
    fun stopSleepTimer() {
        _uiState.value = _uiState.value.copy(isSleepTimerActive = false, sleepTimerMinutes = 0)
    }
    
    private val viewModelScope = androidx.lifecycle.viewModelScope
}

/**
 * UI State for the player screen
 */
data class PlayerUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSleepTimerActive: Boolean = false,
    val sleepTimerMinutes: Int = 0
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

/**
 * PlayerScreen content composable
 */
@Composable
fun PlayerScreenContent(
    uiState: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onChapterClick: (Chapter) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onStopSleepTimer: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Player UI would go here
        // For now just show the book title
        if (uiState.book != null) {
            androidx.compose.material3.Text(
                text = uiState.book.title,
                color = AudibleCloneTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Loading screen while book data is being fetched
 */
@Composable
fun LoadingPlayerScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Helper extension for collecting state with lifecycle awareness
 */
@Composable
fun <T> androidx.compose.runtime.State<T>.collectAsStateWithLifecycle(): androidx.compose.runtime.State<T> {
    return this
}