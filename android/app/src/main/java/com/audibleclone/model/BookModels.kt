package com.audibleclone.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.Ignore
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

// Book model matching backend
@Serializable
@Entity(tableName = "books")
@TypeConverters(Converters::class)
data class Book(
    @PrimaryKey @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String,
    @SerialName("narrator") val narrator: String? = null,
    @SerialName("series") val series: String? = null,
    @SerialName("series_number") val seriesNumber: Double? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("genres") val genres: List<String> = emptyList(),
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("published_year") val publishedYear: Int? = null,
    @SerialName("publisher") val publisher: String? = null,
    @SerialName("isbn") val isbn: String? = null,
    @SerialName("asin") val asin: String? = null,
    @SerialName("language") val language: String = "en",
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("source") val source: BookSource = BookSource.LOCAL,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("status") val status: BookStatus = BookStatus.AVAILABLE,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_format") val fileFormat: String? = null,
    @SerialName("bitrate") val bitrate: Int? = null,
    @SerialName("sample_rate") val sampleRate: Int? = null,
    @SerialName("chapters_count") val chaptersCount: Int = 0,
    @SerialName("added_at") val addedAt: String? = null,
    @SerialName("last_played_at") val lastPlayedAt: String? = null,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("rating") val rating: Double? = null,
    @SerialName("user_rating") val userRating: Double? = null,
    @SerialName("progress_seconds") val progressSeconds: Int = 0,
    @SerialName("completed") val completed: Boolean = false,
) {
    @Ignore
    var chapters: List<Chapter> = emptyList()
    @Ignore
    var highlights: List<Highlight> = emptyList()
    
    // Computed properties
    val formattedDuration: String
        get() = durationSeconds?.let { formatDuration(it) } ?: "Unknown"
    
    val formattedProgress: String
        get() = formatDuration(progressSeconds)
    
    val progressPercent: Float
        get() = if (durationSeconds != null && durationSeconds! > 0) {
            progressSeconds.toFloat() / durationSeconds!
        } else 0f
    
    val displayAuthor: String
        get() = if (narrator.isNullOrBlank()) author else "$author (narrated by $narrator)"
    
    val coverImageUrl: String?
        get() = coverUrl?.takeIf { it.isNotBlank() } ?: coverPath
}

@Serializable
enum class BookSource {
    @SerialName("local") LOCAL,
    @SerialName("librivox") LIBRIVOX,
    @SerialName("audiobookbay") AUDIOBOOKBAY,
    @SerialName("tts_generated") TTS_GENERATED,
    @SerialName("uploaded") UPLOADED,
}

@Serializable
enum class BookStatus {
    @SerialName("available") AVAILABLE,
    @SerialName("downloading") DOWNLOADING,
    @SerialName("processing") PROCESSING,
    @SerialName("error") ERROR,
}

@Serializable
@Entity(tableName = "chapters")
@TypeConverters(Converters::class)
data class Chapter(
    @PrimaryKey @SerialName("id") val id: Int,
    @SerialName("book_id") val bookId: Int,
    @SerialName("index") val index: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("start_offset_seconds") val startOffsetSeconds: Double = 0.0,
    @SerialName("end_offset_seconds") val endOffsetSeconds: Double? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("file_path") val filePath: String? = null,
) {
    val displayTitle: String
        get() = title ?: "Chapter ${index + 1}"
    
    val formattedDuration: String
        get() = durationSeconds?.let { formatDuration(it.toLong()) } ?: "Unknown"
}

@Serializable
@Entity(tableName = "highlights")
@TypeConverters(Converters::class)
data class Highlight(
    @PrimaryKey @SerialName("id") val id: Int,
    @SerialName("book_id") val bookId: Int,
    @SerialName("chapter_id") val chapterId: Int? = null,
    @SerialName("text") val text: String,
    @SerialName("note") val note: String? = null,
    @SerialName("color") val color: String = "#FFD700",
    @SerialName("location_type") val locationType: String = "time",
    @SerialName("location_value") val locationValue: Double,
    @SerialName("location_end") val locationEnd: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

// API Response models
@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val pagination: Pagination,
)

@Serializable
data class Pagination(
    val page: Int,
    val per_page: Int,
    val total: Int,
    val total_pages: Int,
)

// Library stats
@Serializable
data class LibraryStats(
    val total_books: Int,
    val total_duration_seconds: Long,
    val total_size_bytes: Long,
    val by_source: Map<String, Int>,
    val by_status: Map<String, Int>,
)

// Download task
@Serializable
data class DownloadTask(
    val id: Int,
    val book_id: Int? = null,
    val source: BookSource,
    val source_id: String? = null,
    val source_url: String? = null,
    val title: String,
    val author: String? = null,
    val status: String,
    val progress: Float = 0f,
    val error_message: String? = null,
    val current_step: String? = null,
    val download_path: String? = null,
    val created_at: String? = null,
    val started_at: String? = null,
    val completed_at: String? = null,
)

// TTS Task
@Serializable
data class TTSTask(
    val id: Int,
    val book_id: Int? = null,
    val ebook_path: String,
    val ebook_format: String,
    val title: String? = null,
    val author: String? = null,
    val language: String = "en",
    val voice_mapping: Map<String, String>? = null,
    val engine: String = "piper",
    val status: String,
    val progress: Float = 0f,
    val current_step: String? = null,
    val error_message: String? = null,
    val output_path: String? = null,
    val created_at: String? = null,
    val started_at: String? = null,
    val completed_at: String? = null,
)

// LibriVox models
@Serializable
data class LibriVoxBook(
    val id: Int,
    val title: String,
    val author: String,
    @SerialName("url_librivox") val urlLibrivox: String,
    @SerialName("url_cover_art") val urlCoverArt: String? = null,
    @SerialName("url_rss") val urlRss: String? = null,
    @SerialName("url_zip_file") val urlZipFile: String? = null,
    @SerialName("url_other") val urlOther: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String = "en",
    val description: String = "",
    @SerialName("totaltime") val totalTime: String? = null,
    @SerialName("totaltimesecs") val totalTimeSecs: Int? = null,
    val readers: List<LibriVoxReader> = emptyList(),
    val sections: List<LibriVoxSection> = emptyList(),
)

@Serializable
data class LibriVoxReader(
    val id: Int,
    val name: String,
    val url: String,
)

@Serializable
data class LibriVoxSection(
    val id: Int,
    val title: String,
    val duration: Double = 0.0,
    val size: Long = 0,
    val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("listen_url") val listenUrl: String? = null,
)

@Serializable
data class LibriVoxResponse(
    val books: List<LibriVoxBook> = emptyList(),
    val total: Int = 0,
)

// AudioBookBay models
@Serializable
data class ABBBook(
    val title: String,
    val link: String,
    val cover: String? = null,
    val language: String = "N/A",
    val post_date: String = "N/A",
    val format: String = "N/A",
    val bitrate: String = "N/A",
    val file_size: String = "N/A",
    val magnet_link: String? = null,
    val description: String? = null,
    val details_url: String? = null,
)

// TTS Voices
@Serializable
data class TTSVoice(
    val id: String,
    val name: String,
    val path: String? = null,
    val config_path: String? = null,
    val language: String = "en",
)

// OTA Update
@Serializable
data class OTAUpdateInfo(
    val version: String,
    val release_notes: String,
    val download_url: String,
    val force_update: Boolean = false,
    val min_supported_version: String? = null,
    val released_at: String,
)

@Serializable
data class OTAUpdateResponse(
    val update_available: Boolean,
    val current_version: String,
    val latest_version: String,
    val update_info: OTAUpdateInfo? = null,
)

// Utility function
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}