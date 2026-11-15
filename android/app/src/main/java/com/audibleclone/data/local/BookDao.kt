package com.audibleclone.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.model.BookModels.Highlight
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)
    
    @Query("SELECT * FROM books ORDER BY added_at DESC")
    fun getAllBooks(): Flow<List<Book>>
    
    @Query("SELECT * FROM books ORDER BY added_at DESC")
    suspend fun getAllBooksSync(): List<Book>
    
    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Int): Book?
    
    @Query("SELECT * FROM books WHERE source_id = :sourceId AND source = :source")
    suspend fun getBookBySource(sourceId: String, source: String): Book?
    
    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' ORDER BY added_at DESC")
    fun searchBooks(query: String): Flow<List<Book>>
    
    @Query("DELETE FROM books")
    suspend fun clearAll()
    
    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: Int)
    
    // Chapters
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)
    
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index")
    suspend fun getChaptersByBookId(bookId: Int): List<Chapter>
    
    // Highlights
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlights(highlights: List<Highlight>)
    
    @Query("SELECT * FROM highlights WHERE book_id = :bookId ORDER BY location_value")
    suspend fun getHighlightsByBookId(bookId: Int): List<Highlight>
    
    @Transaction
    suspend fun insertBookWithRelations(book: Book) {
        insertBook(book)
        if (book.chapters.isNotEmpty()) {
            insertChapters(book.chapters)
        }
        if (book.highlights.isNotEmpty()) {
            insertHighlights(book.highlights)
        }
    }
}