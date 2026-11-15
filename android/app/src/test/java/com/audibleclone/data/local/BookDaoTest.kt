package com.audibleclone.data.local

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.BookStatus
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.model.BookModels.Highlight
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = database.bookDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertAndGetAllBooks - inserts and retrieves books`() = runBlocking {
        // Given
        val book1 = TestDataFactory.createMockBook(id = 1, title = "Book 1")
        val book2 = TestDataFactory.createMockBook(id = 2, title = "Book 2")

        // When
        bookDao.insertAll(listOf(book1, book2))
        val books = bookDao.getAllBooksSync()

        // Then
        assertEquals(2, books.size)
        assertEquals("Book 1", books[0].title)
        assertEquals("Book 2", books[1].title)
    }

    @Test
    fun `insertBook - replaces existing book with same ID`() = runBlocking {
        // Given
        val book1 = TestDataFactory.createMockBook(id = 1, title = "Original Title")
        val book1Updated = TestDataFactory.createMockBook(id = 1, title = "Updated Title")

        // When
        bookDao.insertBook(book1)
        var books = bookDao.getAllBooksSync()
        assertEquals(1, books.size)
        assertEquals("Original Title", books[0].title)

        bookDao.insertBook(book1Updated)
        books = bookDao.getAllBooksSync()

        // Then
        assertEquals(1, books.size)
        assertEquals("Updated Title", books[0].title)
    }

    @Test
    fun `getBookById - returns book by ID`() = runBlocking {
        // Given
        val book = TestDataFactory.createMockBook(id = 42, title = "Specific Book")
        bookDao.insertBook(book)

        // When
        val result = bookDao.getBookById(42)

        // Then
        assertNotNull(result)
        assertEquals(42, result?.id)
        assertEquals("Specific Book", result?.title)
    }

    @Test
    fun `getBookById - returns null for non-existent ID`() = runBlocking {
        // When
        val result = bookDao.getBookById(999)

        // Then
        assertNull(result)
    }

    @Test
    fun `getBookBySource - returns book by source ID and source`() = runBlocking {
        // Given
        val book = TestDataFactory.createMockBook(
            id = 1,
            title = "LibriVox Book",
            source = BookSource.LIBRIVOX,
            sourceId = "lv_123"
        )
        bookDao.insertBook(book)

        // When
        val result = bookDao.getBookBySource("lv_123", BookSource.LIBRIVOX.name)

        // Then
        assertNotNull(result)
        assertEquals("LibriVox Book", result?.title)
        assertEquals(BookSource.LIBRIVOX, result?.source)
    }

    @Test
    fun `searchBooks - searches by title and author`() = runBlocking {
        // Given
        val book1 = TestDataFactory.createMockBook(id = 1, title = "Pride and Prejudice", author = "Jane Austen")
        val book2 = TestDataFactory.createMockBook(id = 2, title = "Sense and Sensibility", author = "Jane Austen")
        val book3 = TestDataFactory.createMockBook(id = 3, title = "Dune", author = "Frank Herbert")
        bookDao.insertAll(listOf(book1, book2, book3))

        // When - search by title
        val titleResults = bookDao.searchBooks("Pride").first()

        // Then
        assertEquals(1, titleResults.size)
        assertEquals("Pride and Prejudice", titleResults[0].title)

        // When - search by author
        val authorResults = bookDao.searchBooks("Austen").first()

        // Then
        assertEquals(2, authorResults.size)
        assertTrue(authorResults.all { it.author == "Jane Austen" })
    }

    @Test
    fun `deleteBook - removes book by ID`() = runBlocking {
        // Given
        val book1 = TestDataFactory.createMockBook(id = 1, title = "Book 1")
        val book2 = TestDataFactory.createMockBook(id = 2, title = "Book 2")
        bookDao.insertAll(listOf(book1, book2))

        // When
        bookDao.deleteBook(1)
        val books = bookDao.getAllBooksSync()

        // Then
        assertEquals(1, books.size)
        assertEquals("Book 2", books[0].title)
    }

    @Test
    fun `clearAll - removes all books`() = runBlocking {
        // Given
        val book1 = TestDataFactory.createMockBook(id = 1, title = "Book 1")
        val book2 = TestDataFactory.createMockBook(id = 2, title = "Book 2")
        bookDao.insertAll(listOf(book1, book2))

        // When
        bookDao.clearAll()
        val books = bookDao.getAllBooksSync()

        // Then
        assertTrue(books.isEmpty())
    }

    @Test
    fun `insertAndGetChapters - manages chapters`() = runBlocking {
        // Given
        val book = TestDataFactory.createMockBook(id = 1, title = "Book with Chapters")
        bookDao.insertBook(book)

        val chapters = listOf(
            TestDataFactory.createMockChapter(id = 1, bookId = 1, index = 0, title = "Chapter 1"),
            TestDataFactory.createMockChapter(id = 2, bookId = 1, index = 1, title = "Chapter 2"),
            TestDataFactory.createMockChapter(id = 3, bookId = 1, index = 2, title = "Chapter 3")
        )

        // When
        bookDao.insertChapters(chapters)
        val result = bookDao.getChaptersByBookId(1)

        // Then
        assertEquals(3, result.size)
        assertEquals("Chapter 1", result[0].title)
        assertEquals("Chapter 2", result[1].title)
        assertEquals("Chapter 3", result[2].title)
        assertEquals(0, result[0].index)
        assertEquals(1, result[1].index)
        assertEquals(2, result[2].index)
    }

    @Test
    fun `insertAndGetHighlights - manages highlights`() = runBlocking {
        // Given
        val book = TestDataFactory.createMockBook(id = 1, title = "Book with Highlights")
        bookDao.insertBook(book)

        val highlights = listOf(
            TestDataFactory.createMockHighlight(id = 1, bookId = 1, text = "Highlight 1", locationValue = 100.0),
            TestDataFactory.createMockHighlight(id = 2, bookId = 1, text = "Highlight 2", locationValue = 200.0)
        )

        // When
        bookDao.insertHighlights(highlights)
        val result = bookDao.getHighlightsByBookId(1)

        // Then
        assertEquals(2, result.size)
        assertEquals("Highlight 1", result[0].text)
        assertEquals("Highlight 2", result[1].text)
        assertEquals(100.0, result[0].locationValue, 0.001)
        assertEquals(200.0, result[1].locationValue, 0.001)
    }

    @Test
    fun `insertBookWithRelations - inserts book with chapters and highlights`() = runBlocking {
        // Given
        val book = TestDataFactory.createMockBook(id = 1, title = "Complete Book")
        val chapters = listOf(TestDataFactory.createMockChapter(id = 1, bookId = 1, index = 0))
        val highlights = listOf(TestDataFactory.createMockHighlight(id = 1, bookId = 1))
        
        // Create a book with relations
        val bookWithRelations = com.audibleclone.model.BookModels.Book(
            id = book.id,
            title = book.title,
            author = book.author,
            narrator = book.narrator,
            durationSeconds = book.durationSeconds,
            coverUrl = book.coverUrl,
            addedAt = book.addedAt,
            source = book.source
        ).also {
            it.chapters = chapters
            it.highlights = highlights
        }

        // When
        bookDao.insertBookWithRelations(bookWithRelations)
        
        val retrievedBook = bookDao.getBookById(1)
        val retrievedChapters = bookDao.getChaptersByBookId(1)
        val retrievedHighlights = bookDao.getHighlightsByBookId(1)

        // Then
        assertNotNull(retrievedBook)
        assertEquals(1, retrievedChapters.size)
        assertEquals(1, retrievedHighlights.size)
    }

    @Test
    fun `getAllBooks - returns Flow for observation`() = runBlocking {
        // Given
        val book = TestDataFactory.createMockBook(id = 1, title = "Observable Book")
        bookDao.insertBook(book)

        // When
        val flow = bookDao.getAllBooks()
        val books = flow.first()

        // Then
        assertEquals(1, books.size)
        assertEquals("Observable Book", books[0].title)
    }

    @Test
    fun `bookStatusAndSourceEnums - stored correctly`() = runBlocking {
        // Given
        val book = TestDataFactory.createMockBook(
            id = 1,
            title = "Status Test Book",
            source = BookSource.TTS_GENERATED,
            sourceId = "tts_123"
        )
        // Note: Book model doesn't have status field in constructor, it's in the entity
        
        // When
        bookDao.insertBook(book)
        val result = bookDao.getBookById(1)

        // Then
        assertNotNull(result)
        assertEquals(BookSource.TTS_GENERATED, result?.source)
        assertEquals("tts_123", result?.sourceId)
    }
}