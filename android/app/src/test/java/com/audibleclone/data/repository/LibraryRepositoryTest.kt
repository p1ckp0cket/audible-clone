package com.audibleclone.data.repository

import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.AppDatabase
import com.audibleclone.model.BookModels.Book
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

class LibraryRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var apiService: ApiService

    @Mock
    private lateinit var database: AppDatabase

    @Mock
    private lateinit var bookDao: com.audibleclone.data.local.BookDao

    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Mockito.`when`(database.bookDao()).thenReturn(bookDao)
        repository = LibraryRepository(apiService, database)
    }

    @Test
    fun `loadLibrary - first page loads from cache then fetches from API`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val cachedBooks = listOf(
            TestDataFactory.createMockBook(id = 1, title = "Cached Book 1"),
            TestDataFactory.createMockBook(id = 2, title = "Cached Book 2")
        )
        val apiBooks = listOf(
            TestDataFactory.createMockBook(id = 3, title = "API Book 1"),
            TestDataFactory.createMockBook(id = 4, title = "API Book 2")
        )
        val pagination = Pagination(page = 1, per_page = 50, total = 4, total_pages = 1)
        val apiResponse = PaginatedResponse(items = apiBooks, pagination = pagination)

        Mockito.`when`(bookDao.getAllBooksSync()).thenReturn(cachedBooks)
        Mockito.`when`(apiService.getBooks(page = 1, perPage = 50, search = null, author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(apiResponse)
        Mockito.`when`(bookDao.insertAll(apiBooks)).thenReturn(Unit)

        // When
        repository.loadLibrary(page = 1, forceRefresh = false)

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then - verify cache was returned first
        val initialState = repository.uiState.first()
        assertEquals(cachedBooks.size, initialState.books.size)
        assertFalse(initialState.isLoading)

        // Then - verify API was called and cache updated
        val finalState = repository.uiState.first()
        assertEquals(apiBooks.size, finalState.books.size)
        assertFalse(finalState.isLoading)
        assertNull(finalState.error)
        assertEquals(1, finalState.currentPage)
        assertEquals(1, finalState.totalPages)
        assertFalse(finalState.hasMore)
    }

    @Test
    fun `loadLibrary - force refresh skips cache and fetches from API`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val cachedBooks = listOf(TestDataFactory.createMockBook(id = 1, title = "Cached Book"))
        val apiBooks = listOf(TestDataFactory.createMockBook(id = 2, title = "API Book"))
        val pagination = Pagination(page = 1, per_page = 50, total = 1, total_pages = 1)
        val apiResponse = PaginatedResponse(items = apiBooks, pagination = pagination)

        Mockito.`when`(bookDao.getAllBooksSync()).thenReturn(cachedBooks)
        Mockito.`when`(apiService.getBooks(page = 1, perPage = 50, search = null, author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(apiResponse)
        Mockito.`when`(bookDao.insertAll(apiBooks)).thenReturn(Unit)

        // When
        repository.loadLibrary(page = 1, forceRefresh = true)

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then - verify cache was NOT returned first (loading state shown)
        val states = repository.uiState.take(2).toList()
        assertTrue(states[0].isLoading) // Should show loading first
        assertEquals(apiBooks.size, states[1].books.size)
        assertFalse(states[1].isLoading)
    }

    @Test
    fun `loadLibrary - API error sets error state`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val error = HttpException(Response.error(500, okhttp3.ResponseBody.create("Server Error", okhttp3.MediaType.parse("text/plain"))))
        Mockito.`when`(bookDao.getAllBooksSync()).thenReturn(emptyList())
        Mockito.`when`(apiService.getBooks(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenThrow(error)

        // When
        repository.loadLibrary(page = 1, forceRefresh = true)

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Server error: 500"))
    }

    @Test
    fun `loadMore - loads next page when has more`() = mainDispatcherRule.testDispatcher.runTest {
        // Given - setup initial state with hasMore = true
        val initialBooks = listOf(TestDataFactory.createMockBook(id = 1))
        val page2Books = listOf(TestDataFactory.createMockBook(id = 2))
        val pagination = Pagination(page = 2, per_page = 50, total = 2, total_pages = 2)
        val apiResponse = PaginatedResponse(items = page2Books, pagination = pagination)

        // First load page 1
        Mockito.`when`(bookDao.getAllBooksSync()).thenReturn(initialBooks)
        Mockito.`when`(apiService.getBooks(page = 1, perPage = 50, search = null, author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(PaginatedResponse(items = initialBooks, Pagination(page = 1, per_page = 50, total = 2, total_pages = 2)))
        Mockito.`when`(bookDao.insertAll(initialBooks)).thenReturn(Unit)

        repository.loadLibrary(page = 1, forceRefresh = true)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Setup for page 2
        Mockito.`when`(apiService.getBooks(page = 2, perPage = 50, search = null, author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(apiResponse)
        Mockito.`when`(bookDao.insertAll(page2Books)).thenReturn(Unit)

        // When
        repository.loadMore()

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(2, state.books.size) // Both pages combined
        assertEquals(2, state.currentPage)
        assertFalse(state.hasMore)
    }

    @Test
    fun `searchLibrary - searches with query`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val searchResults = listOf(TestDataFactory.createMockBook(id = 1, title = "Search Result"))
        val pagination = Pagination(page = 1, per_page = 50, total = 1, total_pages = 1)
        val apiResponse = PaginatedResponse(items = searchResults, pagination = pagination)

        Mockito.`when`(apiService.getBooks(page = 1, perPage = 50, search = "test", author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(apiResponse)

        // When
        repository.searchLibrary("test")

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals("test", state.searchQuery)
        assertEquals(searchResults.size, state.books.size)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `clearSearch - resets to normal library load`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val books = listOf(TestDataFactory.createMockBook(id = 1))
        val pagination = Pagination(page = 1, per_page = 50, total = 1, total_pages = 1)
        val apiResponse = PaginatedResponse(items = books, pagination = pagination)

        Mockito.`when`(bookDao.getAllBooksSync()).thenReturn(emptyList())
        Mockito.`when`(apiService.getBooks(page = 1, perPage = 50, search = null, author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(apiResponse)
        Mockito.`when`(bookDao.insertAll(books)).thenReturn(Unit)

        // When
        repository.clearSearch()

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals("", state.searchQuery)
        assertEquals(books.size, state.books.size)
    }

    @Test
    fun `refresh - forces reload from API`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val books = listOf(TestDataFactory.createMockBook(id = 1))
        val pagination = Pagination(page = 1, per_page = 50, total = 1, total_pages = 1)
        val apiResponse = PaginatedResponse(items = books, pagination = pagination)

        Mockito.`when`(bookDao.getAllBooksSync()).thenReturn(emptyList())
        Mockito.`when`(apiService.getBooks(page = 1, perPage = 50, search = null, author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(apiResponse)
        Mockito.`when`(bookDao.insertAll(books)).thenReturn(Unit)

        // When
        repository.refresh()

        // Wait for coroutine to complete
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(books.size, state.books.size)
        assertNull(state.error)
    }
}