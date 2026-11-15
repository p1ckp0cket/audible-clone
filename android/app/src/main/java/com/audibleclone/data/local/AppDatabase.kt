package com.audibleclone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.audibleclone.model.BookModels.Book
import com.audibleclone.model.BookModels.Chapter
import com.audibleclone.model.BookModels.Highlight

@Database(
    entities = [Book::class, Chapter::class, Highlight::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}