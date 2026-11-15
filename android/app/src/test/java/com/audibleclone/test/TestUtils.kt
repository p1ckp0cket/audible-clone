package com.audibleclone.test

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutations
import androidx.datastore.preferences.core.preferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.audibleclone.data.repository.DiscoverUiState
import com.audibleclone.data.repository.LibraryUiState
import com.audibleclone.data.repository.PlayerUiState
import com.audibleclone.data.repository.SettingsUiState
import com.audibleclone.model.BookModels.ABBBook
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.model.BookModels.Highlight
import com.audibleclone.model.BookModels.LibriVoxBook
import com.audibleclone.model.BookModels.PlaybackState
import com.audibleclone.model.BookModels.TTSTask
import com.audibleclone.model.BookModels.VoicePreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TestCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Test utilities and common rules for unit and UI tests
 */

// Rule to run coroutines synchronously in tests
@ExperimentalCoroutinesApi
class MainDispatcherRule(val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            @Override
            @Throws(Throwable::class)
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                    testDispatcher.cleanupTestCoroutines()
                }
            }
        }
    }
}

// Rule for instant task executor (LiveData/Flow)
val instantTaskExecutorRule = InstantTaskExecutorRule()

// Test LifecycleOwner for ViewModel testing
class TestLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this).apply {
        markState(Lifecycle.State.CREATED)
    }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    fun setState(state: Lifecycle.State) {
        lifecycleRegistry.markState(state)
    }
}

// Test CoroutineScope extension
@ExperimentalCoroutinesApi
val mainTestScope: TestCoroutineScope get() = TestCoroutineScope(TestCoroutineDispatcher())

// Helper to wait for flow emissions
suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrThrow(timeoutMs: Long = 5000): T {
    return this.first { true }
}

// Mock data factory
object TestDataFactory {
    fun createMockBook(
        id: Int = 1,
        title: String = "Test Book",
        author: String = "Test Author",
        narrator: String = "Test Narrator",
        durationSeconds: Int = 3600,
        progressSeconds: Int = 0,
        completed: Boolean = false,
        source: BookSource = BookSource.LOCAL,
        sourceId: String? = null
    ): Book = Book(
        id = id,
        title = title,
        author = author,
        narrator = narrator,
        durationSeconds = durationSeconds,
        progressSeconds = progressSeconds,
        completed = completed,
        coverUrl = "https://example.com/cover.jpg",
        addedAt = "2024-01-01T00:00:00Z",
        source = source,
        sourceId = sourceId
    )

    fun createMockChapter(
        id: Int = 1,
        bookId: Int = 1,
        index: Int = 0,
        title: String = "Chapter 1",
        durationSeconds: Double = 1800.0
    ): Chapter = Chapter(
        id = id,
        bookId = bookId,
        index = index,
        title = title,
        durationSeconds = durationSeconds
    )

    fun createMockHighlight(
        id: Int = 1,
        bookId: Int = 1,
        text: String = "Test highlight",
        locationValue: Double = 100.0,
        color: String = "#FFD700"
    ): Highlight = Highlight(
        id = id,
        bookId = bookId,
        text = text,
        color = color,
        locationValue = locationValue
    )

    fun createMockLibriVoxBook(
        id: Int = 1,
        title: String = "Test LibriVox Book",
        author: String = "Test Author",
        totalTimeSecs: Int = 3600
    ): LibriVoxBook = LibriVoxBook(
        id = id,
        title = title,
        author = author,
        urlLibrivox = "https://librivox.org/book/$id",
        urlCoverArt = "https://example.com/cover.jpg",
        totalTimeSecs = totalTimeSecs
    )

    fun createMockABBBook(
        title: String = "Test ABB Book",
        link: String = "https://audiobookbay.com/book/1",
        format: String = "MP3",
        bitrate: String = "128 kbps",
        fileSize: String = "500 MB"
    ): ABBBook = ABBBook(
        title = title,
        link = link,
        format = format,
        bitrate = bitrate,
        file_size = fileSize
    )

    fun createMockVoicePreview(
        id: String = "en_US-amy-medium",
        name: String = "Amy (US English, Medium)",
        engine: String = "piper",
        language: String = "en-US",
        quality: String = "High"
    ): VoicePreview = VoicePreview(
        id = id,
        name = name,
        engine = engine,
        language = language,
        quality = quality
    )

    fun createMockTTSTask(
        id: Int = 1,
        status: String = "completed",
        progress: Float = 1.0f,
        ebookPath: String = "/uploads/book.epub"
    ): TTSTask = TTSTask(
        id = id,
        ebookPath = ebookPath,
        ebookFormat = "epub",
        title = "Test Book",
        author = "Test Author",
        language = "en",
        engine = "piper",
        status = status,
        progress = progress
    )

    // UI State factory methods
    fun createLibraryUiState(
        books: List<Book> = emptyList(),
        isLoading: Boolean = false,
        error: String? = null,
        currentPage: Int = 1,
        totalPages: Int = 1,
        hasMore: Boolean = false,
        searchQuery: String = ""
    ): LibraryUiState = LibraryUiState(
        books = books,
        isLoading = isLoading,
        error = error,
        currentPage = currentPage,
        totalPages = totalPages,
        hasMore = hasMore,
        searchQuery = searchQuery
    )

    fun createPlayerUiState(
        book: Book? = null,
        chapters: List<Chapter> = emptyList(),
        currentChapter: Chapter? = null,
        playbackState: PlaybackState = PlaybackState.PAUSED,
        currentPosition: Long = 0,
        duration: Long = 0,
        playbackSpeed: Float = 1.0f,
        isLoading: Boolean = false,
        error: String? = null
    ): PlayerUiState = PlayerUiState(
        book = book,
        chapters = chapters,
        currentChapter = currentChapter,
        playbackState = playbackState,
        currentPosition = currentPosition,
        duration = duration,
        playbackSpeed = playbackSpeed,
        isLoading = isLoading,
        error = error
    )

    fun createSettingsUiState(
        serverUrl: String = "https://yourdomain.com",
        isConnected: Boolean = false,
        autoSync: Boolean = true,
        syncFrequency: String = "On App Start",
        syncHighlights: Boolean = true,
        isSyncing: Boolean = false,
        lastSyncTime: Long = 0,
        defaultPlaybackSpeed: Float = 1.0f,
        skipIntervalSeconds: Int = 30,
        sleepTimerDefaultMinutes: Int = 30,
        rememberPosition: Boolean = true,
        theme: Int = 2,
        coverQuality: String = "High (Original)",
        ttsEngine: String = "auto",
        ttsDefaultVoice: String = "en_US-amy-medium",
        autoAssignVoices: Boolean = true,
        cacheSizeBytes: Long = 0,
        downloadPath: String = "/mnt/drive2/audiobooks",
        currentVersion: String = "1.0.0",
        buildNumber: Int = 1,
        isCheckingUpdates: Boolean = false,
        updateStatus: String = "Unknown",
        updateAvailable: Boolean = false,
        latestVersion: String = "",
        autoUpdate: Boolean = false
    ): SettingsUiState = SettingsUiState(
        serverUrl = serverUrl,
        isConnected = isConnected,
        autoSync = autoSync,
        syncFrequency = syncFrequency,
        syncHighlights = syncHighlights,
        isSyncing = isSyncing,
        lastSyncTime = lastSyncTime,
        defaultPlaybackSpeed = defaultPlaybackSpeed,
        skipIntervalSeconds = skipIntervalSeconds,
        sleepTimerDefaultMinutes = sleepTimerDefaultMinutes,
        rememberPosition = rememberPosition,
        theme = theme,
        coverQuality = coverQuality,
        ttsEngine = ttsEngine,
        ttsDefaultVoice = ttsDefaultVoice,
        autoAssignVoices = autoAssignVoices,
        cacheSizeBytes = cacheSizeBytes,
        downloadPath = downloadPath,
        currentVersion = currentVersion,
        buildNumber = buildNumber,
        isCheckingUpdates = isCheckingUpdates,
        updateStatus = updateStatus,
        updateAvailable = updateAvailable,
        latestVersion = latestVersion,
        autoUpdate = autoUpdate
    )

    fun createDiscoverUiState(
        librivoxBooks: List<LibriVoxBook> = emptyList(),
        librivoxIsLoading: Boolean = false,
        librivoxError: String? = null,
        librivoxHasMore: Boolean = false,
        librivoxCurrentPage: Int = 1,
        abbBooks: List<ABBBook> = emptyList(),
        abbIsLoading: Boolean = false,
        abbError: String? = null,
        abbHasMore: Boolean = false,
        abbCurrentPage: Int = 1,
        availableVoices: List<VoicePreview> = emptyList(),
        ttsUploading: Boolean = false,
        ttsError: String? = null,
        ttsTaskId: Int? = null
    ): DiscoverUiState = DiscoverUiState(
        librivoxBooks = librivoxBooks,
        librivoxIsLoading = librivoxIsLoading,
        librivoxError = librivoxError,
        librivoxHasMore = librivoxHasMore,
        librivoxCurrentPage = librivoxCurrentPage,
        abbBooks = abbBooks,
        abbIsLoading = abbIsLoading,
        abbError = abbError,
        abbHasMore = abbHasMore,
        abbCurrentPage = abbCurrentPage,
        availableVoices = availableVoices,
        ttsUploading = ttsUploading,
        ttsError = ttsError,
        ttsTaskId = ttsTaskId
    )
}

// Helper for running tests with coroutines
@ExperimentalCoroutinesApi
suspend fun runTestWithDispatcher(
    dispatcher: TestCoroutineDispatcher,
    block: suspend TestCoroutineScope.() -> Unit
) {
    val scope = TestCoroutineScope(dispatcher)
    scope.block()
    dispatcher.advanceUntilIdle()
}