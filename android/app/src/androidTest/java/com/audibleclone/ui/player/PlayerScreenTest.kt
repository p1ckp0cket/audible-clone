package com.audibleclone.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.audibleclone.data.repository.PlayerRepository
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.model.BookModels.PlaybackState
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class PlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `playerScreen_displaysLoadingState`() {
        // Given
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = MutableStateFlow(TestDataFactory.createPlayerUiState(isLoading = true))
        Mockito.`when`(mockRepository.uiState).thenReturn(uiState)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Loading player...").assertExists()
    }

    @Test
    fun `playerScreen_displaysBookInfo`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1, title = "Test Book", author = "Test Author")
        val chapter = TestDataFactory.createMockChapter(id = 1, bookId = 1, title = "Chapter 1")
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(
            book = book,
            currentChapter = chapter,
            chapters = listOf(chapter),
            playbackState = PlaybackState.PAUSED
        )
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Test Book").assertExists()
        composeRule.onNodeWithText("Test Author").assertExists()
        composeRule.onNodeWithText("Chapter 1").assertExists()
    }

    @Test
    fun `playerScreen_playPauseButtonTogglesPlayback`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1)
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(book = book, playbackState = PlaybackState.PAUSED)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        // Click play button
        composeRule.onNodeWithContentDescription("Play").performClick()

        // Then
        Mockito.verify(mockRepository).playPause()
    }

    @Test
    fun `playerScreen_skipForwardButtonCallsSkipForward`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1)
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(book = book)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        composeRule.onNodeWithContentDescription("Forward 30s").performClick()

        // Then
        Mockito.verify(mockRepository).skipForward()
    }

    @Test
    fun `playerScreen_skipBackwardButtonCallsSkipBackward`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1)
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(book = book)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        composeRule.onNodeWithContentDescription("Rewind 30s").performClick()

        // Then
        Mockito.verify(mockRepository).skipBackward()
    }

    @Test
    fun `playerScreen_chapterClickPlaysChapter`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1)
        val chapter1 = TestDataFactory.createMockChapter(id = 1, bookId = 1, index = 0, title = "Chapter 1")
        val chapter2 = TestDataFactory.createMockChapter(id = 2, bookId = 1, index = 1, title = "Chapter 2")
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(
            book = book,
            chapters = listOf(chapter1, chapter2),
            currentChapter = chapter1
        )
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        composeRule.onNodeWithText("Chapter 2").performClick()

        // Then
        Mockito.verify(mockRepository).playChapter(chapter2)
    }

    @Test
    fun `playerScreen_displaysProgressBar`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1, durationSeconds = 3600)
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(
            book = book,
            currentPosition = 1800000, // 30 minutes in ms
            duration = 3600000 // 1 hour in ms
        )
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        // Then - progress time labels should be visible
        composeRule.onNodeWithText("30:00").assertExists() // current position
        composeRule.onNodeWithText("1:00:00").assertExists() // total duration
    }

    @Test
    fun `playerScreen_speedButtonVisible`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1)
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(book = book, playbackSpeed = 1.5f)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithContentDescription("Speed: 1.5x").assertExists()
    }

    @Test
    fun `playerScreen_sleepTimerButtonVisible`() {
        // Given
        val book = TestDataFactory.createMockBook(id = 1)
        val mockRepository = mock(PlayerRepository::class.java)
        val uiState = TestDataFactory.createPlayerUiState(book = book)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            PlayerScreen(bookId = 1, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithContentDescription("Sleep timer").assertExists()
    }
}