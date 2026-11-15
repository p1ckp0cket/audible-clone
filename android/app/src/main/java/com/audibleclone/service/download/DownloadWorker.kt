package com.audibleclone.service.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.audibleclone.R
import com.audibleclone.data.api.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okio.Buffer
import okio.ForwardingTimeout
import okio.Timeout
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * DownloadWorker - Background worker for downloading audiobooks for offline playback.
 * 
 * Features:
 * - Background downloads with progress notifications
 * - Resume support (checkpoint-based)
 * - Pause/resume capability
 * - Network constraint awareness
 * - Storage space validation
 * - Chapter-level downloads
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {
    
    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_IS_CHAPTER = "is_chapter_download"
        const val KEY_DESTINATION = "destination"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_IS_RESUME = "is_resume"
        const val KEY_RESUME_OFFSET = "resume_offset"
        
        const val NOTIFICATION_CHANNEL_ID = "download_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Downloaded Files"
        const val NOTIFICATION_ID_BASE = 2000
        
        private const val CHUNK_SIZE = 64 * 1024L // 64KB chunks for efficiency
        
        // Download states
        const val STATE_QUEUED = 0
        const val STATE_DOWNLOADING = 1
        const val STATE_COMPLETED = 3
        const val STATE_FAILED = 4
    }
    
    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    private val bookId = inputData.getInt(KEY_BOOK_ID, 0)
    private val chapterId = inputData.getInt(KEY_CHAPTER_ID, 0)
    private val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: ""
    private val isChapterDownload = inputData.getBoolean(KEY_IS_CHAPTER, false)
    private val isResume = inputData.getBoolean(KEY_IS_RESUME, false)
    private val resumeOffset = inputData.getLong(KEY_RESUME_OFFSET, 0L)
    private val destination = inputData.getString(KEY_DESTINATION)
    private val filePath = inputData.getString(KEY_FILE_PATH)
    
    override suspend fun doWork(): Result {
        return try {
            if (bookId <= 0 || downloadUrl.isEmpty()) {
                return Result.failure()
            }
            
            // Create notification channel
            createNotificationChannel()
            
            // Start download
            val result = downloadFile()
            
            // Cancel notification
            notificationManager.cancel(getNotificationId())
            
            return result
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download failed: ${e.message}")
            notificationManager.cancel(getNotificationId())
            return Result.failure()
        }
    }
    
    private suspend fun downloadFile(): Result {
        var fileOut: java.io.FileOutputStream? = null
        var responseBody: okhttp3.ResponseBody? = null
        var fileChannel: java.nio.channels.FileChannel? = null
        
        return try {
            // Get target file
            val targetFile = getTargetFile()
            targetFile.parentFile?.mkdirs()
            
            // Check storage space
            if (!hasEnoughSpace(targetFile)) {
                return Result.failure()
            }
            
            // Build request with resume support
            val requestBuilder = okhttp3.Request.Builder().url(downloadUrl)
            
            val existingFile = getTargetFile()
            var resumeOffset = if (isResume && existingFile.exists()) {
                existingFile.length()
            } else {
                0L
            }
            
            if (resumeOffset > 0) {
                requestBuilder.addHeader("Range", "bytes=$resumeOffset-")
            }
            
            val request = requestBuilder.build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return if (response.code == 416) { // Range not satisfiable
                    Result.success()
                } else {
                    Result.retry()
                }
            }
            
            responseBody = response.body
            
            val contentLength = responseBody?.contentLength() ?: 0L
            val totalBytes = contentLength + resumeOffset
            
            // Open file for writing (append if resuming)
            fileOut = java.io.FileOutputStream(targetFile, resumeOffset > 0)
            fileChannel = fileOut.channel
            
            val source = responseBody?.source()
            if (source == null) {
                return Result.failure()
            }
            
            var downloaded = 0L
            val startTime = System.currentTimeMillis()
            val buffer = Buffer()
            
            // Read and write in chunks
            while (true) {
                val readBytes = source.read(buffer, CHUNK_SIZE)
                if (readBytes == -1L) break
                
                buffer.read(fileChannel, readBytes)
                downloaded += readBytes
                
                // Update progress notification
                val progress = if (totalBytes > 0) {
                    (( (downloaded + resumeOffset).toFloat() / totalBytes.toFloat()) * 100).toInt()
                } else 0
                val elapsedMs = System.currentTimeMillis() - startTime
                val speed = if (elapsedMs > 0) {
                    (downloaded / (elapsedMs / 1000.0)).toLong()
                } else 0L
                updateProgressNotification(progress, formatSpeed(speed))
                
                // Check if work should stop
                if (isStopped) {
                    source.close()
                    break
                }
            }
            
            source.close()
            fileOut.flush()
            fileOut.close()
            fileChannel?.close()
            
            // Mark as completed in database
            markDownloadComplete(targetFile, totalBytes)
            
            return Result.success()
        } catch (e: IOException) {
            Log.e("DownloadWorker", "Download error: ${e.message}")
            return Result.retry()
        } finally {
            try {
                fileOut?.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                fileChannel?.close()
            } catch (e: Exception) {
                // Ignore
            }
            responseBody?.close()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
    
    private fun createProgressNotification(
        title: String,
        progress: Int,
        speedText: String
    ): Notification {
        return NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText("$title - $speedText")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, progress, progress > 0)
            .build()
    }
    
    private fun updateProgressNotification(progress: Int, speedText: String) {
        val notification = createProgressNotification(
            "Audiobook",
            progress,
            speedText
        )
        
        notificationManager.notify(getNotificationId(), notification)
    }
    
    private fun formatSpeed(speedBytes: Long): String {
        return when {
            speedBytes >= 1024 * 1024 -> String.format("%.1f MB/s", speedBytes / (1024.0 * 1024.0))
            speedBytes >= 1024 -> String.format("%.1f KB/s", speedBytes / 1024.0)
            speedBytes > 0 -> "$speedBytes B/s"
            else -> "--"
        }
    }
    
    private fun getTargetFile(): File {
        val baseDir = filePath ?: destination ?: 
            (applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) 
                ?: applicationContext.filesDir)
        
        val fileName = if (isChapterDownload) {
            "chapter_${chapterId}.mp3"
        } else {
            "book.mp3"
        }
        
        return File(File(baseDir, "audiobooks/$bookId"), fileName)
    }
    
    private fun getNotificationId(): Int {
        return NOTIFICATION_ID_BASE + bookId
    }
    
    private fun hasEnoughSpace(file: File): Boolean {
        try {
            val statFs = StatFs(file.parentFile?.absolutePath ?: "/")
            val availableBytes = statFs.availableBytes
            // Need space for file + 10MB buffer
            val required = (file.length() + 10 * 1024 * 1024L)
            return availableBytes >= required
        } catch (e: Exception) {
            return true // Assume enough space if we can't check
        }
    }
    
    private fun markDownloadComplete(file: File, totalBytes: Long) {
        // In production: update Room database with download status
        Log.d("DownloadWorker", "Download complete: ${file.absolutePath} ($totalBytes bytes)")
    }
    
    /**
     * Create a OneTimeWorkRequest for downloading a book
     */
    fun createDownloadRequest(
        bookId: Int,
        downloadUrl: String,
        destination: String? = null
    ): androidx.work.WorkRequest {
        return androidx.work.OneTimeWorkRequest.Builder(DownloadWorker::class.java)
            .setInputData(
                androidx.work.Data.Builder()
                    .putInt(KEY_BOOK_ID, bookId)
                    .putString(KEY_DOWNLOAD_URL, downloadUrl)
                    .putBoolean(KEY_IS_RESUME, false)
                    .build()
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
    }
}