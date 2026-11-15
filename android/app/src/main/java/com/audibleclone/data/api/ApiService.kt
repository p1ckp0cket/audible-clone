package com.audibleclone.data.api

import com.audibleclone.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// Moshi for Retrofit
private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

// OkHttp client with auth interceptor
fun createOkHttpClient(baseUrl: String, authToken: String? = null): OkHttpClient {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    val authInterceptor = Interceptor { chain ->
        var request = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .build()
        
        authToken?.let { token ->
            request = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }
        
        chain.proceed(request)
    }
    
    return OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}

// Retrofit instance
fun createRetrofit(baseUrl: String, authToken: String? = null): Retrofit {
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(createOkHttpClient(baseUrl, authToken))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
}

// API Service Interface
interface ApiService {
    
    // ==================== Library ====================
    
    @GET("library")
    suspend fun getBooks(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("search") search: String? = null,
        @Query("author") author: String? = null,
        @Query("genre") genre: String? = null,
        @Query("source") source: String? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String = "added_at",
        @Query("order") order: String = "desc",
    ): PaginatedResponse<Book>
    
    @GET("library/stats")
    suspend fun getLibraryStats(): LibraryStats
    
    @GET("library/{book_id}")
    suspend fun getBook(@Path("book_id") bookId: Int): Book
    
    @GET("library/{book_id}/chapters")
    suspend fun getBookChapters(
        @Path("book_id") bookId: Int
    ): List<Chapter>

    @GET("library/{book_id}/stream")
    suspend fun streamBook(@Path("book_id") bookId: Int): okhttp3.Response
    
    @GET("library/{book_id}/chapter/{chapter_id}/stream")
    suspend fun streamChapter(
        @Path("book_id") bookId: Int,
        @Path("chapter_id") chapterId: Int
    ): okhttp3.Response
    
    @PATCH("library/{book_id}")
    suspend fun updateBook(
        @Path("book_id") bookId: Int,
        @Body updates: Map<String, Any>
    ): Book
    
    @DELETE("library/{book_id}")
    suspend fun deleteBook(
        @Path("book_id") bookId: Int,
        @Query("delete_files") deleteFiles: Boolean = false
    ): Map<String, String>
    
    @POST("library/scan")
    suspend fun scanLibrary(
        @Query("force_refresh") forceRefresh: Boolean = false
    ): Map<String, String>
    
    // ==================== LibriVox ====================
    
    @GET("librivox/search")
    suspend fun searchLibrivox(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("genre") genre: String? = null
    ): LibriVoxResponse
    
    @GET("librivox/book/{book_id}")
    suspend fun getLibrivoxBook(@Path("book_id") bookId: Int): LibriVoxBook
    
    @GET("librivox/genres")
    suspend fun getLibrivoxGenres(): Map<String, List<String>>
    
    @POST("librivox/download/{book_id}")
    suspend fun downloadLibrivoxBook(@Path("book_id") bookId: Int): DownloadTask
    
    @GET("librivox/download/status/{task_id}")
    suspend fun getLibrivoxDownloadStatus(@Path("task_id") taskId: Int): DownloadTask
    
    // ==================== AudioBookBay ====================
    
    @GET("audiobookbay/search")
    suspend fun searchAudiobookbay(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): PaginatedResponse<ABBBook>
    
    @GET("audiobookbay/book/{book_id}")
    suspend fun getAudiobookbayBook(@Path("book_id") bookId: String): ABBBook
    
    @POST("audiobookbay/download")
    suspend fun downloadAudiobookbayBook(
        @Body request: ABBDownloadRequest
    ): ABBDownloadResponse
    
    @GET("audiobookbay/download/status/{task_id}")
    suspend fun getAudiobookbayDownloadStatus(@Path("task_id") taskId: Int): DownloadTask
    
    // ==================== TTS ====================
    
    @GET("tts/voices")
    suspend fun getTTSVoices(): Map<String, List<TTSVoice>>
    
    @Multipart
    @POST("tts/upload")
    suspend fun uploadEbookForTTS(
        @Part file: okhttp3.MultipartBody.Part,
        @Part("title") title: okhttp3.RequestBody?,
        @Part("author") author: okhttp3.RequestBody?,
        @Part("language") language: okhttp3.RequestBody,
        @Part("engine") engine: okhttp3.RequestBody,
    ): TTSTask
    
    @GET("tts/task/{task_id}")
    suspend fun getTTSTask(@Path("task_id") taskId: Int): TTSTask
    
    @POST("tts/task/{task_id}/assign-voices")
    suspend fun assignCharacterVoices(
        @Path("task_id") taskId: Int,
        @Body voiceMapping: Map<String, String>
    ): Map<String, Any>
    
    // ==================== Upload ====================
    
    @Multipart
    @POST("upload/audiobook")
    suspend fun uploadAudiobook(
        @Part() files: List<okhttp3.MultipartBody.Part>,
        @Part("cover") cover: okhttp3.MultipartBody.Part?,
        @Part("title") title: okhttp3.RequestBody?,
        @Part("author") author: okhttp3.RequestBody?,
        @Part("narrator") narrator: okhttp3.RequestBody?,
        @Part("series") series: okhttp3.RequestBody?,
        @Part("series_number") seriesNumber: okhttp3.RequestBody?,
        @Part("description") description: okhttp3.RequestBody?,
        @Part("genres") genres: okhttp3.RequestBody?,
        @Part("tags") tags: okhttp3.RequestBody?,
        @Part("published_year") publishedYear: okhttp3.RequestBody?,
        @Part("publisher") publisher: okhttp3.RequestBody?,
        @Part("isbn") isbn: okhttp3.RequestBody?,
        @Part("asin") asin: okhttp3.RequestBody?,
        @Part("language") language: okhttp3.RequestBody,
    ): Map<String, Any>
    
    @Multipart
    @POST("upload/cover/{book_id}")
    suspend fun uploadCover(
        @Path("book_id") bookId: Int,
        @Part cover: okhttp3.MultipartBody.Part
    ): Map<String, String>
    
    // ==================== Highlights ====================
    
    @GET("highlights/book/{book_id}")
    suspend fun getBookHighlights(
        @Path("book_id") bookId: Int,
        @Query("chapter_id") chapterId: Int? = null
    ): Map<String, List<Highlight>>
    
    @POST("highlights")
    suspend fun createHighlight(
        @Body request: CreateHighlightRequest
    ): Highlight
    
    @PATCH("highlights/{highlight_id}")
    suspend fun updateHighlight(
        @Path("highlight_id") highlightId: Int,
        @Body updates: Map<String, Any>
    ): Highlight
    
    @DELETE("highlights/{highlight_id}")
    suspend fun deleteHighlight(@Path("highlight_id") highlightId: Int): Map<String, String>
    
    @POST("highlights/sync")
    suspend fun syncHighlights(
        @Body request: SyncHighlightsRequest
    ): SyncHighlightsResponse
    
    // ==================== OTA ====================
    
    @GET("ota/check")
    suspend fun checkForUpdates(
        @Query("current_version") currentVersion: String,
        @Query("platform") platform: String = "android",
        @Query("channel") channel: String = "stable"
    ): OTAUpdateResponse
    
    @GET("ota/versions")
    suspend fun getVersions(): Map<String, List<Map<String, Any>>>
    
    // ==================== Health ====================
    
    @GET("health")
    suspend fun healthCheck(): Map<String, String>
    
    @GET("version")
    suspend fun getVersion(): Map<String, String>
}

// Request/Response bodies
data class ABBDownloadRequest(
    val details_url: String,
)

data class ABBDownloadResponse(
    val message: String,
    val magnet_link: String,
    val task_id: Int,
    val title: String,
    val save_path: String,
)

data class CreateHighlightRequest(
    val book_id: Int,
    val text: String,
    val location_value: Double,
    val location_type: String = "time",
    val chapter_id: Int? = null,
    val note: String? = null,
    val color: String = "#FFD700",
    val location_end: Double? = null,
    val device_id: String? = null,
)

data class SyncHighlightsRequest(
    val book_id: Int,
    val highlights: List<Highlight>,
    val device_id: String,
)

data class SyncHighlightsResponse(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val highlights: List<Highlight>,
)

// Helper to create multipart parts
fun createPart(value: String?): okhttp3.RequestBody? {
    return value?.let {
        okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("text/plain"),
            it
        )
    }
}

fun createPart(value: Int?): okhttp3.RequestBody? {
    return value?.let {
        okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("text/plain"),
            it.toString()
        )
    }
}

fun createPart(value: List<String>?): okhttp3.RequestBody? {
    return value?.let {
        okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("application/json"),
            Json { prettyPrint = true }.encodeToString(it)
        )
    }
}

fun createFilePart(file: java.io.File, fieldName: String): okhttp3.MultipartBody.Part {
    val mediaType = when (file.extension.lowercase()) {
        "mp3", "m4b", "m4a" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
    
    val requestBody = okhttp3.RequestBody.create(
        okhttp3.MediaType.parse(mediaType),
        file
    )
    
    return okhttp3.MultipartBody.Part.createFormData(fieldName, file.name, requestBody)
}