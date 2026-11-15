package com.audibleclone.data.local

import com.audibleclone.model.BookModels.BookSource
import com.audibleclone.model.BookModels.BookStatus
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()
    private val gson = Gson()

    @Test
    fun `stringListToJson - converts list to JSON`() {
        // Given
        val list = listOf("genre1", "genre2", "genre3")

        // When
        val json = converters.stringListToJson(list)

        // Then
        assertEquals("""["genre1","genre2","genre3"]""", json)
    }

    @Test
    fun `stringListToJson - returns null for null input`() {
        // When
        val json = converters.stringListToJson(null)

        // Then
        assertNull(json)
    }

    @Test
    fun `jsonToStringList - converts JSON to list`() {
        // Given
        val json = """["genre1","genre2","genre3"]"""

        // When
        val list = converters.jsonToStringList(json)

        // Then
        assertEquals(listOf("genre1", "genre2", "genre3"), list)
    }

    @Test
    fun `jsonToStringList - returns null for null input`() {
        // When
        val list = converters.jsonToStringList(null)

        // Then
        assertNull(list)
    }

    @Test
    fun `bookSourceToString - converts enum to string`() {
        // When/Then
        assertEquals("LOCAL", converters.bookSourceToString(BookSource.LOCAL))
        assertEquals("LIBRIVOX", converters.bookSourceToString(BookSource.LIBRIVOX))
        assertEquals("AUDIOBOOKBAY", converters.bookSourceToString(BookSource.AUDIOBOOKBAY))
        assertEquals("TTS_GENERATED", converters.bookSourceToString(BookSource.TTS_GENERATED))
        assertEquals("UPLOADED", converters.bookSourceToString(BookSource.UPLOADED))
        assertNull(converters.bookSourceToString(null))
    }

    @Test
    fun `stringToBookSource - converts string to enum`() {
        // When/Then
        assertEquals(BookSource.LOCAL, converters.stringToBookSource("LOCAL"))
        assertEquals(BookSource.LIBRIVOX, converters.stringToBookSource("LIBRIVOX"))
        assertEquals(BookSource.AUDIOBOOKBAY, converters.stringToBookSource("AUDIOBOOKBAY"))
        assertEquals(BookSource.TTS_GENERATED, converters.stringToBookSource("TTS_GENERATED"))
        assertEquals(BookSource.UPLOADED, converters.stringToBookSource("UPLOADED"))
        assertNull(converters.stringToBookSource(null))
        assertNull(converters.stringToBookSource("INVALID"))
    }

    @Test
    fun `bookStatusToString - converts enum to string`() {
        // When/Then
        assertEquals("AVAILABLE", converters.bookStatusToString(BookStatus.AVAILABLE))
        assertEquals("DOWNLOADING", converters.bookStatusToString(BookStatus.DOWNLOADING))
        assertEquals("PROCESSING", converters.bookStatusToString(BookStatus.PROCESSING))
        assertEquals("ERROR", converters.bookStatusToString(BookStatus.ERROR))
        assertNull(converters.bookStatusToString(null))
    }

    @Test
    fun `stringToBookStatus - converts string to enum`() {
        // When/Then
        assertEquals(BookStatus.AVAILABLE, converters.stringToBookStatus("AVAILABLE"))
        assertEquals(BookStatus.DOWNLOADING, converters.stringToBookStatus("DOWNLOADING"))
        assertEquals(BookStatus.PROCESSING, converters.stringToBookStatus("PROCESSING"))
        assertEquals(BookStatus.ERROR, converters.stringToBookStatus("ERROR"))
        assertNull(converters.stringToBookStatus(null))
        assertNull(converters.stringToBookStatus("INVALID"))
    }

    @Test
    fun `roundTrip - string list`() {
        val original = listOf("a", "b", "c")
        val json = converters.stringListToJson(original)
        val result = converters.jsonToStringList(json)
        assertEquals(original, result)
    }

    @Test
    fun `roundTrip - book source`() {
        val sources = BookSource.values()
        for (source in sources) {
            val str = converters.bookSourceToString(source)
            val result = converters.stringToBookSource(str)
            assertEquals(source, result)
        }
    }

    @Test
    fun `roundTrip - book status`() {
        val statuses = BookStatus.values()
        for (status in statuses) {
            val str = converters.bookStatusToString(status)
            val result = converters.stringToBookStatus(str)
            assertEquals(status, result)
        }
    }
}