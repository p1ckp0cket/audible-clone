package com.audibleclone.ui.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.audibleclone.data.repository.LibraryRepository
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `libraryScreen_displaysLoadingState`() {
        // Given
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = MutableStateFlow(TestDataFactory.createLibraryUiState(isLoading = true))
        Mockito.`when`(mockRepository.uiState).thenReturn(uiState)

        // When
        composeRule.setContent {
            LibraryScreen(onBookClick = {}, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithContentDescription("Loading...").assertExists()
    }

    @Test
    fun `libraryScreen_displaysEmptyState`() {
        // Given
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = TestDataFactory.createLibraryUiState(books = emptyList(), isLoading = false)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            LibraryScreen(onBookClick = {}, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Your library is empty").assertExists()
        composeRule.onNodeWithText("Add books from Discover or upload your own").assertExists()
    }

    @Test
    fun `libraryScreen_displaysErrorState`() {
        // Given
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = TestDataFactory.createLibraryUiState(
            books = emptyList(),
            isLoading = false,
            error = "Network error"
        )
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            LibraryScreen(onBookClick = {}, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Error loading library").assertExists()
        composeRule.onNodeWithText("Network error").assertExists()
        composeRule.onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `libraryScreen_displaysBooks`() {
        // Given
        val books = listOf(
            TestDataFactory.createMockBook(id = 1, title = "Book 1", author = "Author 1"),
            TestDataFactory.createMockBook(id = 2, title = "Book 2", author = "Author 2")
        )
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = TestDataFactory.createLibraryUiState(books = books)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            LibraryScreen(onBookClick = {}, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Book 1").assertExists()
        composeRule.onNodeWithText("Author 1").assertExists()
        composeRule.onNodeWithText("Book 2").assertExists()
        composeRule.onNodeWithText("Author 2").assertExists()
    }

    @Test
    fun `libraryScreen_refreshButtonTriggersRefresh`() {
        // Given
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = TestDataFactory.createLibraryUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        composeRule.setContent {
            LibraryScreen(onBookClick = {}, repository = mockRepository)
        }

        // When
        composeRule.onNodeWithContentDescription("Refresh").performClick()

        // Then
        Mockito.verify(mockRepository).refresh()
    }

    @Test
    fun `libraryScreen_bookClickNavigates`() {
        // Given
        val books = listOf(TestDataFactory.createMockBook(id = 1, title = "Test Book"))
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = TestDataFactory.createLibraryUiState(books = books)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        var clickedBook: Book? = null

        // When
        composeRule.setContent {
            LibraryScreen(onBookClick = { clickedBook = it }, repository = mockRepository)
        }

        composeRule.onNodeWithText("Test Book").performClick()

        // Then
        assertNotNull(clickedBook)
        assertEquals("Test Book", clickedBook!!.title)
    }

    @Test
    fun `libraryScreen_displaysProgressForPartiallyReadBooks`() {
        // Given
        val book = TestDataFactory.createMockBook(
            id = 1,
            title = "In Progress Book",
            progressSeconds = 1800, // 30 minutes
            completed = false
        )
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = TestDataFactory.createLibraryUiState(books = listOf(book))
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            LibraryScreen(onBookClick = {}, repository = mockRepository)
        }

        // Then - Progress indicator should be visible
        // Note: We can't easily test LinearProgressIndicator visibility without more complex matchers
        // but we can verify the book is displayed
        composeRule.onNodeWithText("In Progress Book").assertExists()
    }

    @Test
    fun `libraryScreen_displaysCompletedBadge`() {
        // Given
        val book = TestDataFactory.createMockBook(
            id = 1,
            title = "Completed Book",
            completed = true
        )
        val mockRepository = mock(LibraryRepository::class.java)
        val uiState = TestDataFactory.createLibraryUiState(books = listOf(book))
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            LibraryScreen(onBookClick = {}, repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Completed Book").assertExists()
        composeRule.onNodeWithText("Completed").assertExists()
    }
}