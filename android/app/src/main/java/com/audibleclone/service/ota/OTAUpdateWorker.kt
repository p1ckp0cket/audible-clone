package com.audibleclone.service.ota

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.audibleclone.BuildConfig
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.datastore.SettingsDataStore
import com.audibleclone.model.BookModels.OTAUpdateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import dagger.hilt.android.scopes.WorkerScoped
import dagger.hilt.worker.HiltWorkerFactory

@WorkerScoped
class OTAUpdateWorker @Inject constructor(
    context: Context,
    params: WorkerParameters,
    private val apiService: ApiService,
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "OTAUpdateWorker"
        const val WORK_NAME = "OTAUpdateCheck"
        const val NOTIFICATION_CHANNEL_ID = "ota_updates"
        const val NOTIFICATION_ID = 1001
        const val DOWNLOAD_NOTIFICATION_ID = 1002
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting OTA update check")

            // Check if auto-update is enabled
            val autoUpdate = settingsDataStore.getAutoUpdate()
            if (!autoUpdate) {
                Log.d(TAG, "Auto-update disabled, skipping")
                return@withContext Result.success()
            }

            // Get current version
            val currentVersion = BuildConfig.VERSION_NAME

            // Check for updates via API
            val response = checkForUpdates(currentVersion)
            if (!response.updateAvailable) {
                Log.d(TAG, "No update available")
                return@withContext Result.success()
            }

            Log.d(TAG, "Update available: ${response.latestVersion}")

            // If update info has download URL, download and install
            response.updateInfo?.let { updateInfo ->
                if (updateInfo.downloadUrl.isNotBlank()) {
                    downloadAndInstallUpdate(updateInfo)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "OTA update check failed", e)
            Result.retry()
        }
    }

    private suspend fun checkForUpdates(currentVersion: String): OTAUpdateResponse {
        return withContext(Dispatchers.IO) {
            apiService.checkForUpdates(
                currentVersion = currentVersion,
                platform = "android",
                channel = "stable"
            )
        }
    }

    private suspend fun downloadAndInstallUpdate(updateInfo: com.audibleclone.model.BookModels.OTAUpdateInfo) {
        val downloadUrl = updateInfo.downloadUrl
        val fileName = "audibleclone-${updateInfo.version}.apk"
        val destinationFile = File(applicationContext.cacheDir, fileName)

        Log.d(TAG, "Downloading update from: $downloadUrl")

        // Show download notification
        showDownloadNotification("Downloading update ${updateInfo.version}...", 0)

        val request = Request.Builder()
            .url(downloadUrl)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to download update: ${response.code()}")
            }

            val body = response.body ?: throw RuntimeException("Empty response body")
            val contentLength = body.contentLength()
            var downloaded = 0L

            FileOutputStream(destinationFile).use { fileOutput ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        fileOutput.write(buffer, 0, read)
                        downloaded += read.toLong()

                        val progress = if (contentLength > 0) {
                            (downloaded * 100 / contentLength).toInt()
                        } else {
                            -1
                        }
                        showDownloadNotification("Downloading update ${updateInfo.version}...", progress)
                    }
                }
            }
        }

        Log.d(TAG, "Download complete: ${destinationFile.absolutePath}")

        // Show install notification
        showInstallNotification(destinationFile, updateInfo.version)

        // If force_update is true, attempt auto-install
        if (updateInfo.forceUpdate) {
            attemptAutoInstall(destinationFile)
        }
    }

    private fun showDownloadNotification(message: String, progress: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AudibleClone Update")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
    }

    private fun showInstallNotification(apkFile: File, version: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create install intent
        val installIntent = Intent(Intent.ACTION_VIEW)
        val apkUri = FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.fileprovider",
            apkFile
        )
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        installIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            installIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update Ready: v$version")
            .setContentText("Tap to install")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun attemptAutoInstall(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Check if we have permission to install packages
                val packageManager = applicationContext.packageManager
                val canInstall = packageManager.canRequestPackageInstalls()

                if (canInstall) {
                    val installIntent = Intent(Intent.ACTION_VIEW)
                    val apkUri = FileProvider.getUriForFile(
                        applicationContext,
                        "${applicationContext.packageName}.fileprovider",
                        apkFile
                    )
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                    installIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    applicationContext.startActivity(installIntent)
                    Log.d(TAG, "Auto-install initiated")
                } else {
                    Log.w(TAG, "No permission to install packages, showing notification instead")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-install failed", e)
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "OTA Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for app updates"
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}