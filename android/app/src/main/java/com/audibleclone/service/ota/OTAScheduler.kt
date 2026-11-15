package com.audibleclone.service.ota

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.audibleclone.data.local.datastore.SettingsDataStore
import com.audibleclone.data.local.datastore.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OTAScheduler @Inject constructor(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    
    companion object {
        const val WORK_TAG = "OTA_UPDATE_CHECK"
        const val INTERVAL_HOURS = 24L
        const val FLEX_INTERVAL_HOURS = 1L
    }
    
    /**
     * Schedule periodic OTA update checks if auto-update is enabled
     */
    fun scheduleIfEnabled(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val autoUpdate = settingsDataStore.getAutoUpdate()
            if (autoUpdate) {
                schedule()
            } else {
                cancel()
            }
        }
    }
    
    /**
     * Schedule the periodic OTA update check
     */
    fun schedule() {
        val workRequest = PeriodicWorkRequest.Builder(
            OTAUpdateWorker::class.java,
            INTERVAL_HOURS,
            TimeUnit.HOURS,
            FLEX_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .addTag(WORK_TAG)
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                OTAUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }
    
    /**
     * Cancel any scheduled OTA update checks
     */
    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(OTAUpdateWorker.WORK_NAME)
    }
    
    /**
     * Call this when auto-update setting changes
     */
    fun onAutoUpdateChanged(enabled: Boolean) {
        if (enabled) {
            schedule()
        } else {
            cancel()
        }
    }
}