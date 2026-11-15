package com.audibleclone.data.repository

import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.AppDatabase
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.model.BookModels.PlaybackState
import com.audibleclone.test.MainDispatcherRule
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import retrofit2.HttpException
import retrofit2.Response

class PlayerRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var apiService: ApiService

    @Mock
    private lateinit var database: AppDatabase

    @Mock
    private lateinit var bookDao: com.audibleclone.data.local.BookDao

    private lateinit var repository: PlayerRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Mockito.`when`(database.bookDao()).thenReturn(bookDao)
        repository = PlayerRepository(apiService, database)
    }

    @Test
    fun `loadBook - loads from cache then fetches from API`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val cachedBook = TestDataFactory.createMockBook(id = 1, title = "Cached Book")
        val cachedChapters = listOf(
            TestDataFactory.createMockChapter(id = 1, bookId = 1, index = 0, title = "Chapter 1"),
            TestDataFactory.createMockChapter(id = 2, bookId = 1, index = 1, title = "Chapter 2")
        )
        val apiBook = TestDataFactory.createMockBook(id = 1, title = "API Book", author = "API Author")

        Mockito.`when`(bookDao.getBookById(1)).thenReturn(cachedBook)
        Mockito.`when`(bookDao.getChaptersByBookId(1)).thenReturn(cachedChapters)
        Mockito.`when`(apiService.getBook(1)).thenReturn(apiBook)
        Mockito.`when`(bookDao.insertBook(apiBook)).thenReturn(Unit)

        // When
        repository.loadBook(1)

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then - verify cache was returned first
        val initialState = repository.uiState.first()
        assertEquals(cachedBook.title, initialState.book?.title)
        assertEquals(cachedChapters.size, initialState.chapters.size)
        assertEquals(cachedChapters.first(), initialState.currentChapter)
        assertFalse(initialState.isLoading)

        // Then - verify API was called and state updated
        val finalState = repository.uiState.first()
        assertEquals(apiBook.title, finalState.book?.title)
        assertFalse(finalState.isLoading)
        assertNull(finalState.error)
    }

    @Test
    fun `loadBook - API error sets error state`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val error = HttpException(Response.error(404, okhttp3.ResponseBody.create("Not Found", okhttp3.MediaType.parse("text/plain"))))
        Mockito.`when`(bookDao.getBookById(1)).thenReturn(null)
        Mockito.`when`(bookDao.getChaptersByBookId(1)).thenReturn(emptyList())
        Mockito.`when`(apiService.getBook(1)).thenThrow(error)

        // When
        repository.loadBook(1)

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Server error: 404"))
    }

    @Test
    fun `playPause - toggles playback state`() = mainDispatcherRule.testDispatcher.runTest {
        // Given - initial state is PAUSED
        val initialState = repository.uiState.first()
        assertEquals(PlaybackState.PAUSED, initialState.playbackState)

        // When - play
        repository.playPause()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then - should be PLAYING
        var state = repository.uiState.first()
        assertEquals(PlaybackState.PLAYING, state.playbackState)

        // When - pause
        repository.playPause()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then - should be PAUSED
        state = repository.uiState.first()
        assertEquals(PlaybackState.PAUSED, state.playbackState)
    }

    @Test
    fun `seekTo - updates current position`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.seekTo(5000) // 5 seconds

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(5000L, state.currentPosition)
    }

    @Test
    fun `setPlaybackSpeed - updates playback speed`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.setPlaybackSpeed(1.5f)

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(1.5f, state.playbackSpeed, 0.001f)
    }

    @Test
    fun `playChapter - sets current chapter`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val chapter = TestDataFactory.createMockChapter(id = 2, bookId = 1, index = 1, title = "Chapter 2")

        // When
        repository.playChapter(chapter)

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(chapter.id, state.currentChapter?.id)
        assertEquals(chapter.title, state.currentChapter?.title)
    }

    @Test
    fun `skipForward - placeholder test (ExoPlayer integration needed)`() = mainDispatcherRule.testDispatcher.runTest {
        // This is a placeholder - actual implementation needs ExoPlayer integration
        repository.skipForward()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()
        // Verify no crash
        assertNotNull(repository.uiState.first())
    }

    @Test
    fun `skipBackward - placeholder test (ExoPlayer integration needed)`() = mainDispatcherRule.testDispatcher.runTest {
        // This is a placeholder - actual implementation needs ExoPlayer integration
        repository.skipBackward()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()
        // Verify no crash
        assertNotNull(repository.uiState.first())
    }
}