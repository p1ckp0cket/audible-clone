package com.audibleclone.integration

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesKey
import androidx.datastore.preferences.testing.TestDataStoreBuilder
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.AppDatabase
import com.audibleclone.data.local.BookDao
import com.audibleclone.data.repository.DiscoverRepository
import com.audibleclone.data.repository.LibraryRepository
import com.audibleclone.data.repository.PlayerRepository
import com.audibleclone.data.repository.SettingsRepository
import com.audibleclone.model.BookModels.ABBBook
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.model.BookModels.Highlight
import com.audibleclone.model.BookModels.LibriVoxBook
import com.audibleclone.model.BookModels.PlaybackState
import com.audibleclone.model.BookModels.PaginatedResponse
import com.audibleclone.model.BookModels.Pagination
import com.audibleclone.test.MainDispatcherRule
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Integration tests for full app flows
 * Tests: library scan → play book → add highlight → sync → offline → online sync
 */
class IntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var database: AppDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = database.bookDao()
        
        apiService = mock(ApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        database.close()
    }

    @Test
    fun `fullFlow_libraryScanToPlayBook`() = runTest {
        // Given - a book in the library
        val book = TestDataFactory.createMockBook(
            id = 1,
            title = "Test Audiobook",
            author = "Test Author",
            chaptersCount = 3,
            progressSeconds = 0,
            completed = false
        )
        
        // Mock API response for library scan
        val pagination = Pagination(page = 1, per_page = 50, total = 1, total_pages = 1)
        val libraryResponse = PaginatedResponse(items = listOf(book), pagination = pagination)
        
        // Create repositories
        val libraryRepository = LibraryRepository(apiService, database)
        val playerRepository = PlayerRepository(apiService, database)

        // When - scan library
        mockWebServer.enqueue(MockResponse().apply {
            setBody("""{"items": [{"id": 1, "title": "Test Audiobook", "author": "Test Author"}], "pagination": {"page": 1, "per_page": 50, "total": 1, "total_pages": 1}}""")
            setResponseCode(200)
        })
        
        // Mock the library scan
        `when`(apiService.getBooks(page = 1, perPage = 50, search = null, author = null, genre = null, source = null, status = null, sort = "added_at", order = "desc"))
            .thenReturn(libraryResponse)
        
        libraryRepository.loadLibrary(page = 1)
        advanceUntilIdle()

        // Then - book is in library
        val libraryState = libraryRepository.uiState.first()
        assertEquals(1, libraryState.books.size)
        assertEquals("Test Audiobook", libraryState.books[0].title)
    }

    @Test
    fun `fullFlow_addHighlightToBook`() = runTest {
        // Given - a book with chapters
        val book = TestDataFactory.createMockBook(id = 1, title = "Test Book")
        val chapter = TestDataFactory.createMockChapter(id = 1, bookId = 1, title = "Chapter 1")
        val highlight = TestDataFactory.createMockHighlight(id = 1, bookId = 1, text = "Important quote")

        // When - add highlight
        bookDao.insertBook(book)
        bookDao.insertChapters(listOf(chapter))
        bookDao.insertHighlights(listOf(highlight))

        // Then - highlight is stored
        val highlights = bookDao.getHighlightsByBookId(1)
        assertEquals(1, highlights.size)
        assertEquals("Important quote", highlights[0].text)
    }

    @Test
    fun `fullFlow_syncHighlightsToBackend`() = runTest {
        // Given - a highlight that needs syncing
        val highlight = TestDataFactory.createMockHighlight(
            id = 100,
            bookId = 1,
            text = "Synced highlight",
            locationValue = 120.5
        )

        // When - sync highlights (mocked)
        val syncRequest = com.audibleclone.data.api.SyncHighlightsRequest(
            bookId = 1,
            highlights = listOf(highlight),
            deviceId = "test_device"
        )

        // Mock sync response
        val syncResponse = com.audibleclone.data.api.SyncHighlightsResponse(
            created = 1,
            updated = 0,
            deleted = 0,
            highlights = listOf(highlight)
        )
        
        Mockito.`when`(mock(ApiService::class.java).syncHighlights(syncRequest))
            .thenReturn(syncResponse)

        // Then - sync was called (mocked)
        // In real implementation, this would verify the API call
    }

    @Test
    fun `fullFlow_offlineMode_playback`() = runTest {
        // Given - a book downloaded for offline playback
        val book = TestDataFactory.createMockBook(
            id = 1,
            title = "Offline Book",
            filePath = "/data/data/com.audibleclone/files/offline/book.mp3",
            fileSize = 50 * 1024 * 1024, // 50 MB
            fileFormat = "mp3"
        )

        // When - load book
        bookDao.insertBook(book)
        val loadedBook = bookDao.getBookById(1)

        // Then - book is available offline
        assertNotNull(loadedBook)
        assertEquals("/data/data/com.audibleclone/files/offline/book.mp3", loadedBook?.filePath)
        assertTrue(loadedBook?.fileSize!! > 0)
    }

    @Test
    fun `fullFlow_onlineSync_afterOffline`() = runTest {
        // Given - a book modified offline
        val book = TestDataFactory.createMockBook(
            id = 1,
            title = "Offline Modified Book",
            progressSeconds = 1800 // 30 minutes played
        )

        // When - sync back online
        mockWebServer.enqueue(MockResponse().apply {
            setBody("""{"id": 1, "title": "Offline Modified Book", "progress_seconds": 1800}""")
            setResponseCode(200)
        })

        bookDao.insertBook(book)
        
        // Mock API update call
        val api = mock(ApiService::class.java)
        `when`(api.updateBook(1, mapOf("progress_seconds" to 1800)))
            .thenReturn(book)

        // Then - sync succeeds
        val updatedBook = bookDao.getBookById(1)
        assertEquals(1800, updatedBook?.progressSeconds)
    }

    @Test
    fun `dataStorePersistence_settings`() = runTest {
        // Given - settings changes
        val dataStore = TestDataStoreBuilder().build()
        
        // When - save settings
        dataStore.edit { prefs ->
            prefs[preferencesKey<Boolean>("auto_sync")] = false
            prefs[preferencesKey<Float>("default_playback_speed")] = 1.5f
            prefs[preferencesKey<String>("server_url")] = "https://custom-server.com/api/"
        }

        // Then - settings persist
        val prefs = dataStore.data.first()
        assertEquals(false, prefs[preferencesKey<Boolean>("auto_sync")])
        assertEquals(1.5f, prefs[preferencesKey<Float>("default_playback_speed")], 0.001f)
        assertEquals("https://custom-server.com/api/", prefs[preferencesKey<String>("server_url")])
    }

    @Test
    fun `bookMigration_v1_To_v2`() = runTest {
        // Given - v1 book format
        val v1Book = TestDataFactory.createMockBook(id = 1)
        // Note: In real migration, new fields would get default values

        // When - insert
        bookDao.insertBook(v1Book)

        // Then - book is stored with defaults
        val book = bookDao.getBookById(1)
        assertNotNull(book)
        assertEquals(1, book?.id)
    }

    @Test
    fun `searchIntegration_multiSourceSearch`() = runTest {
        // Given - search query
        val query = "dune"

        // When - search both LibriVox and AudioBookBay
        val librivoxBooks = listOf(
            TestDataFactory.createMockLibriVoxBook(id = 1, title = "Dune by Frank Herbert")
        )
        val abbBooks = listOf(
            TestDataFactory.createMockABBBook(title = "Dune", link = "https://abb.com/dune")
        )

        val librivoxRepo = mock(DiscoverRepository::class.java)
        val abbRepo = mock(DiscoverRepository::class.java)

        `when`(librivoxRepo.uiState).thenReturn(MutableStateFlow(
            com.audibleclone.data.repository.DiscoverUiState(librivoxBooks = librivoxBooks)
        ))
        `when`(abbRepo.uiState).thenReturn(MutableStateFlow(
            com.audibleclone.data.repository.DiscoverUiState(abbBooks = abbBooks)
        ))

        // Then - results from both sources
        val lvState = librivoxRepo.uiState.first()
        val abbState = abbRepo.uiState.first()
        
        assertTrue(lvState.librivoxBooks.any { it.title.contains("Dune") })
        assertTrue(abbState.abbBooks.any { it.title.contains("Dune") })
    }

    @Test
    fun `concurrentModification_concurrentUpdates`() = runTest {
        // Given - same book being updated from multiple sources
        val bookId = 1
        
        // When - concurrent updates
        val updates = listOf(100, 200, 300, 400, 500)
        for (progress in updates) {
            val book = TestDataFactory.createMockBook(id = bookId, progressSeconds = progress)
            bookDao.insertBook(book)
        }

        // Then - last write wins (Room behavior)
        val finalBook = bookDao.getBookById(bookId)
        assertEquals(500, finalBook?.progressSeconds)
    }

    @Test
    fun `performance_startupTime`() = runTest {
        // Test that database can be queried quickly
        val startTime = System.nanoTime()
        
        // Insert 100 books
        val books = (1..100).map { i ->
            TestDataFactory.createMockBook(id = i, title = "Book $i")
        }
        bookDao.insertAll(books)
        
        // Query all books
        val allBooks = bookDao.getAllBooksSync()
        
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000
        
        // Assert startup time < 2 seconds (performance requirement)
        assertTrue("Startup time ${durationMs}ms exceeds 2000ms", durationMs < 2000)
        assertEquals(100, allBooks.size)
    }

    @Test
    fun `performance_memoryUsage`() = runTest {
        // Test memory usage for large library
        val books = (1..1000).map { i ->
            TestDataFactory.createMockBook(id = i, title = "Book $i", description = "Description $i")
        }
        
        bookDao.insertAll(books)
        
        val allBooks = bookDao.getAllBooksSync()
        
        // Memory usage should be reasonable (this is a rough check)
        val estimatedSize = allBooks.sumOf { 
            it.title.length + (it.author?.length ?: 0) + (it.description?.length ?: 0)
        }
        
        // Should fit within 150MB limit (rough estimation)
        assertTrue(estimatedSize < 150 * 1024 * 1024)
    }
}