package com.audibleclone.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.audibleclone.data.repository.DiscoverRepository
import com.audibleclone.model.BookModels.ABBBook
import com.audibleclone.model.BookModels.LibriVoxBook
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class DiscoverScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `discoverScreen_displaysTabButtons`() {
        // Given
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("LibriVox").assertExists()
        composeRule.onNodeWithText("AudioBookBay").assertExists()
        composeRule.onNodeWithText("TTS Upload").assertExists()
    }

    @Test
    fun `discoverScreen_librivoxTab_displaysSearchBar`() {
        // Given
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Then - LibriVox tab is selected by default
        composeRule.onNodeWithText("Search LibriVox...").assertExists()
    }

    @Test
    fun `discoverScreen_audiobookbayTab_displaysSearchBar`() {
        // Given
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // When - click AudioBookBay tab
        composeRule.onNodeWithText("AudioBookBay").performClick()

        // Then
        composeRule.onNodeWithText("Search AudioBookBay...").assertExists()
    }

    @Test
    fun `discoverScreen_searchLibrivox_triggersSearch`() {
        // Given
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Type in search and press enter
        composeRule.onNodeWithText("Search LibriVox...").performTextInput("pride and prejudice")
        // Note: In real test, we'd trigger the keyboard action

        // Then
        Mockito.verify(mockRepository).search("pride and prejudice", 0)
    }

    @Test
    fun `discoverScreen_searchAudiobookbay_triggersSearch`() {
        // Given
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Switch to AudioBookBay tab
        composeRule.onNodeWithText("AudioBookBay").performClick()

        // Type in search
        composeRule.onNodeWithText("Search AudioBookBay...").performTextInput("dune")

        // Then
        Mockito.verify(mockRepository).search("dune", 1)
    }

    @Test
    fun `discoverScreen_librivoxResults_displaysBooks`() {
        // Given
        val books = listOf(
            TestDataFactory.createMockLibriVoxBook(id = 1, title = "Pride and Prejudice", author = "Jane Austen"),
            TestDataFactory.createMockLibriVoxBook(id = 2, title = "Emma", author = "Jane Austen")
        )
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState(librivoxBooks = books)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Pride and Prejudice").assertExists()
        composeRule.onNodeWithText("Jane Austen").assertExists()
        composeRule.onNodeWithText("Emma").assertExists()
    }

    @Test
    fun `discoverScreen_abbResults_displaysBooks`() {
        // Given
        val books = listOf(
            TestDataFactory.createMockABBBook(title = "Dune", link = "https://abb.com/dune"),
            TestDataFactory.createMockABBBook(title = "Foundation", link = "https://abb.com/foundation")
        )
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState(abbBooks = books)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Switch to AudioBookBay tab
        composeRule.onNodeWithText("AudioBookBay").performClick()

        // Then
        composeRule.onNodeWithText("Dune").assertExists()
        composeRule.onNodeWithText("Foundation").assertExists()
    }

    @Test
    fun `discoverScreen_librivoxDownloadButton_triggersDownload`() {
        // Given
        val books = listOf(TestDataFactory.createMockLibriVoxBook(id = 1, title = "Test Book"))
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState(librivoxBooks = books)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Click download button
        composeRule.onNodeWithText("Download").performClick()

        // Then
        Mockito.verify(mockRepository).downloadLibrivoxBook(1)
    }

    @Test
    fun `discoverScreen_abbGetMagnetButton_triggersDownload`() {
        // Given
        val books = listOf(TestDataFactory.createMockABBBook(title = "Test Book", link = "https://abb.com/book"))
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState(abbBooks = books)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Switch to AudioBookBay tab
        composeRule.onNodeWithText("AudioBookBay").performClick()

        // Click get magnet button
        composeRule.onNodeWithText("Get Magnet").performClick()

        // Then
        Mockito.verify(mockRepository).downloadAudiobookbayBook(books[0])
    }

    @Test
    fun `discoverScreen_ttsTab_displaysVoicePreviews`() {
        // Given
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Switch to TTS tab
        composeRule.onNodeWithText("TTS Upload").performClick()

        // Then
        composeRule.onNodeWithText("Upload eBook for TTS Conversion").assertExists()
        composeRule.onNodeWithText("Available Voices").assertExists()
        // Should show mock voices
        composeRule.onNodeWithText("Adult Female (US)").assertExists() // Kokoro voice
        composeRule.onNodeWithText("Amy (US English, Medium)").assertExists() // Piper voice
    }

    @Test
    fun `discoverScreen_loadMoreLibrivox_triggersLoadMore`() {
        // Given
        val books = listOf(TestDataFactory.createMockLibriVoxBook(id = 1))
        val mockRepository = mock(DiscoverRepository::class.java)
        val uiState = TestDataFactory.createDiscoverUiState(
            librivoxBooks = books,
            librivoxHasMore = true
        )
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            DiscoverScreen(repository = mockRepository)
        }

        // Click load more
        composeRule.onNodeWithText("Load More").performClick()

        // Then
        Mockito.verify(mockRepository).loadMore(0)
    }
}