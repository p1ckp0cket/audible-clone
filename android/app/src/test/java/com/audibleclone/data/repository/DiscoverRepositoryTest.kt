package com.audibleclone.data.repository

import com.audibleclone.data.api.ApiService
import com.audibleclone.model.BookModels.ABBBook
import com.audibleclone.model.BookModels.LibriVoxBook
import com.audibleclone.model.BookModels.LibriVoxResponse
import com.audibleclone.model.BookModels.PaginatedResponse
import com.audibleclone.model.BookModels.Pagination
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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import retrofit2.HttpException
import retrofit2.Response
import java.io.File

class DiscoverRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var apiService: ApiService

    private lateinit var repository: DiscoverRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = DiscoverRepository(apiService)
    }

    @Test
    fun `init - loads mock voices`() = mainDispatcherRule.testDispatcher.runTest {
        // When - repository initializes in constructor
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertNotNull(state.availableVoices)
        assertTrue(state.availableVoices.isNotEmpty())
        // Should have both Kokoro and Piper voices
        assertTrue(state.availableVoices.any { it.engine == "kokoro" })
        assertTrue(state.availableVoices.any { it.engine == "piper" })
    }

    @Test
    fun `search - LibriVox search`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val query = "pride and prejudice"
        val books = listOf(
            TestDataFactory.createMockLibriVoxBook(id = 1, title = "Pride and Prejudice"),
            TestDataFactory.createMockLibriVoxBook(id = 2, title = "Pride and Prejudice (version 2)")
        )
        val response = LibriVoxResponse(books = books, total = 2)

        Mockito.`when`(apiService.searchLibrivox(q = query, page = 1, perPage = 20, genre = null))
            .thenReturn(response)

        // When
        repository.search(query, 0) // 0 = LibriVox
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.librivoxIsLoading)
        assertNull(state.librivoxError)
        assertEquals(2, state.librivoxBooks.size)
        assertEquals("Pride and Prejudice", state.librivoxBooks[0].title)
    }

    @Test
    fun `search - AudioBookBay search`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val query = "sci-fi"
        val books = listOf(
            TestDataFactory.createMockABBBook(title = "Dune", link = "https://abb.com/dune"),
            TestDataFactory.createMockABBBook(title = "Foundation", link = "https://abb.com/foundation")
        )
        val pagination = Pagination(page = 1, per_page = 20, total = 2, total_pages = 1)
        val response = PaginatedResponse(items = books, pagination = pagination)

        Mockito.`when`(apiService.searchAudiobookbay(q = query, page = 1, perPage = 20))
            .thenReturn(response)

        // When
        repository.search(query, 1) // 1 = AudioBookBay
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.abbIsLoading)
        assertNull(state.abbError)
        assertEquals(2, state.abbBooks.size)
        assertEquals("Dune", state.abbBooks[0].title)
    }

    @Test
    fun `search - LibriVox API error`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val error = HttpException(Response.error(429, okhttp3.ResponseBody.create("Rate Limited", okhttp3.MediaType.parse("text/plain"))))
        Mockito.`when`(apiService.searchLibrivox(anyString(), anyInt(), anyInt(), anyString()))
            .thenThrow(error)

        // When
        repository.search("test", 0)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.librivoxIsLoading)
        assertNotNull(state.librivoxError)
        assertTrue(state.librivoxError!!.contains("LibriVox error: 429"))
    }

    @Test
    fun `search - AudioBookBay API error`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val error = HttpException(Response.error(500, okhttp3.ResponseBody.create("Server Error", okhttp3.MediaType.parse("text/plain"))))
        Mockito.`when`(apiService.searchAudiobookbay(anyString(), anyInt(), anyInt()))
            .thenThrow(error)

        // When
        repository.search("test", 1)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.abbIsLoading)
        assertNotNull(state.abbError)
        assertTrue(state.abbError!!.contains("AudioBookBay error: 500"))
    }

    @Test
    fun `loadMore - LibriVox loads next page`() = mainDispatcherRule.testDispatcher.runTest {
        // Given - initial search
        val initialBooks = listOf(TestDataFactory.createMockLibriVoxBook(id = 1))
        val initialResponse = LibriVoxResponse(books = initialBooks, total = 2)
        Mockito.`when`(apiService.searchLibrivox(q = "test", page = 1, perPage = 20, genre = null))
            .thenReturn(initialResponse)

        repository.search("test", 0)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Given - next page
        val nextBooks = listOf(TestDataFactory.createMockLibriVoxBook(id = 2, title = "Page 2 Book"))
        val nextResponse = LibriVoxResponse(books = nextBooks, total = 2)
        Mockito.`when`(apiService.searchLibrivox(q = "", page = 2, perPage = 20, genre = null))
            .thenReturn(nextResponse)

        // When
        repository.loadMore(0)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(2, state.librivoxBooks.size)
        assertEquals(2, state.librivoxCurrentPage)
    }

    @Test
    fun `loadMore - AudioBookBay loads next page`() = mainDispatcherRule.testDispatcher.runTest {
        // Given - initial search
        val initialBooks = listOf(TestDataFactory.createMockABBBook(title = "Book 1"))
        val initialPagination = Pagination(page = 1, per_page = 20, total = 2, total_pages = 2)
        val initialResponse = PaginatedResponse(items = initialBooks, pagination = initialPagination)
        Mockito.`when`(apiService.searchAudiobookbay(q = "test", page = 1, perPage = 20))
            .thenReturn(initialResponse)

        repository.search("test", 1)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Given - next page
        val nextBooks = listOf(TestDataFactory.createMockABBBook(title = "Book 2"))
        val nextPagination = Pagination(page = 2, per_page = 20, total = 2, total_pages = 2)
        val nextResponse = PaginatedResponse(items = nextBooks, pagination = nextPagination)
        Mockito.`when`(apiService.searchAudiobookbay(q = "", page = 2, perPage = 20))
            .thenReturn(nextResponse)

        // When
        repository.loadMore(1)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(2, state.abbBooks.size)
        assertEquals(2, state.abbCurrentPage)
    }

    @Test
    fun `downloadLibrivoxBook - calls API`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val downloadTask = com.audibleclone.model.BookModels.DownloadTask(
            id = 1,
            book_id = 1,
            source = com.audibleclone.model.BookModels.BookSource.LIBRIVOX,
            title = "Test Book",
            status = "pending",
            progress = 0f
        )
        Mockito.`when`(apiService.downloadLibrivoxBook(1)).thenReturn(downloadTask)

        // When
        repository.downloadLibrivoxBook(1)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        Mockito.verify(apiService).downloadLibrivoxBook(1)
    }

    @Test
    fun `downloadAudiobookbayBook - calls API with details URL`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val book = TestDataFactory.createMockABBBook(link = "https://abb.com/book/1")
        val response = com.audibleclone.data.api.ABBDownloadResponse(
            message = "Success",
            magnet_link = "magnet:?xt=urn:btih:...",
            task_id = 1,
            title = book.title,
            save_path = "/downloads/book.mp3"
        )
        Mockito.`when`(apiService.downloadAudiobookbayBook(any())).thenReturn(response)

        // When
        repository.downloadAudiobookbayBook(book)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        Mockito.verify(apiService).downloadAudiobookbayBook(
            com.audibleclone.data.api.ABBDownloadRequest(details_url = book.link)
        )
    }

    @Test
    fun `uploadEbookForTTS - uploads file`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val file = File("/test/book.epub")
        val task = com.audibleclone.model.BookModels.TTSTask(
            id = 1,
            ebook_path = "/uploads/book.epub",
            ebook_format = "epub",
            title = "Test Book",
            author = "Test Author",
            language = "en",
            engine = "piper",
            status = "pending",
            progress = 0f
        )
        Mockito.`when`(apiService.uploadEbookForTTS(any(), any(), any(), any(), any())).thenReturn(task)

        // When
        repository.uploadEbookForTTS(file, "Test Book", "Test Author")
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.ttsUploading)
        assertNull(state.ttsError)
        assertEquals(1, state.ttsTaskId)
        Mockito.verify(apiService).uploadEbookForTTS(any(), any(), any(), any(), any())
    }

    @Test
    fun `uploadEbookForTTS - API error`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val error = HttpException(Response.error(500, okhttp3.ResponseBody.create("Upload Failed", okhttp3.MediaType.parse("text/plain"))))
        Mockito.`when`(apiService.uploadEbookForTTS(any(), any(), any(), any(), any())).thenThrow(error)

        // When
        repository.uploadEbookForTTS(File("/test/book.epub"), "Test", "Author")
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.ttsUploading)
        assertNotNull(state.ttsError)
        assertTrue(state.ttsError!!.contains("Upload failed"))
    }
}