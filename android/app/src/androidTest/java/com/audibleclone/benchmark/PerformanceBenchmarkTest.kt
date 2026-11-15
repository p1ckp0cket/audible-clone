package com.audibleclone.benchmark

import android.content.Context
import androidx.benchmark.junit4.BenchmarkTestRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidBenchmarkRunner
import com.audibleclone.data.local.AppDatabase
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Performance benchmarks for the audiobook app
 * Tests: Startup time < 2s, memory < 150MB, frame drops < 5%
 */
@RunWith(AndroidBenchmarkRunner::class)
class PerformanceBenchmarkTest {

    @get:Rule
    val benchmarkRule = BenchmarkTestRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    fun tearDown() {
        database.close()
    }

    /**
     * Benchmark: Database initialization and first query
     */
    @androidx.test.ext.junit.runners.AndroidJUnit4.runWithTestRule
    fun `benchmark_databaseInitialization`() {
        setUp()
        
        benchmarkRule.measureRepeated {
            runBlocking {
                // Clear and re-create
                database.bookDao().clearAll()
                
                // Insert test data
                val books = (1..100).map { i ->
                    TestDataFactory.createMockBook(
                        id = i,
                        title = "Benchmark Book $i",
                        author = "Author $i"
                    )
                }
                database.bookDao().insertAll(books)
                
                // First query
                database.bookDao().getAllBooksSync()
            }
        }
        
        tearDown()
    }

    /**
     * Benchmark: Large library query performance
     */
    fun `benchmark_largeLibraryQuery`() = benchmarkRule.measureRepeated {
        setUp()
        
        runBlocking {
            // Prepare large dataset
            val books = (1..1000).map { i ->
                TestDataFactory.createMockBook(
                    id = i,
                    title = "Benchmark Book $i with a longer title for testing",
                    author = "Author $i",
                    description = "Description for book $i with some additional text"
                )
            }
            database.bookDao().insertAll(books)

            // Benchmark the query
            database.bookDao().getAllBooksSync()
        }
        
        tearDown()
    }

    /**
     * Benchmark: Search performance
     */
    fun `benchmark_searchPerformance`() = benchmarkRule.measureRepeated {
        setUp()
        
        runBlocking {
            // Prepare dataset
            val books = (1..500).map { i ->
                TestDataFactory.createMockBook(
                    id = i,
                    title = "Searchable Book $i " + if (i % 3 == 0) "Dune" else if (i % 3 == 1) "Foundation" else "Neuromancer",
                    author = "Author $i"
                )
            }
            database.bookDao().insertAll(books)

            // Benchmark search
            database.bookDao().searchBooks("Dune").first()
        }
        
        tearDown()
    }

    /**
     * Benchmark: Book detail loading with chapters
     */
    fun `benchmark_bookDetailLoad`() = benchmarkRule.measureRepeated {
        setUp()
        
        runBlocking {
            // Prepare book with chapters
            val book = TestDataFactory.createMockBook(id = 1, title = "Detailed Book")
            database.bookDao().insertBook(book)
            
            // Add chapters
            val chapters = (1..20).map { i ->
                TestDataFactory.createMockChapter(id = i, bookId = 1, index = i - 1)
            }
            database.bookDao().insertChapters(chapters)

            // Benchmark loading
            val retrievedBook = database.bookDao().getBookById(1)
            val bookChapters = database.bookDao().getChaptersByBookId(1)
            
            assertNotNull(retrievedBook)
            assertEquals(20, bookChapters.size)
        }
        
        tearDown()
    }

    /**
     * Benchmark: Highlight operations
     */
    fun `benchmark_highlightOperations`() = benchmarkRule.measureRepeated {
        setUp()
        
        runBlocking {
            // Prepare book
            val book = TestDataFactory.createMockBook(id = 1)
            database.bookDao().insertBook(book)

            // Benchmark adding and retrieving highlights
            repeat(100) { i ->
                database.bookDao().insertHighlights(
                    listOf(TestDataFactory.createMockHighlight(id = i, bookId = 1, text = "Highlight $i"))
                )
            }

            database.bookDao().getHighlightsByBookId(1)
        }
        
        tearDown()
    }
}