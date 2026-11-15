package com.audibleclone.data.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audibleclone.data.api.ApiService
import com.audibleclone.data.local.datastore.SettingsDataStore
import com.audibleclone.model.BookModels.OTAUpdateResponse
import com.audibleclone.service.ota.OTAScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SettingsRepository(
    private val apiService: ApiService,
    private val settingsDataStore: SettingsDataStore,
    private val otaScheduler: OTAScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadSettings()
        checkConnection()
        // Schedule OTA update worker if auto-update is enabled
        viewModelScope.launch {
            val autoUpdate = settingsDataStore.getAutoUpdate()
            if (autoUpdate) {
                otaScheduler.schedule()
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .distinctUntilChanged()
                .collect { prefs ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            serverUrl = if (prefs.serverUrlSet) prefs.serverUrl else "https://yourdomain.com",
                            autoSync = prefs.autoSync,
                            syncFrequency = if (prefs.syncFrequency.isNotBlank()) prefs.syncFrequency else "On App Start",
                            syncHighlights = prefs.syncHighlights,
                            defaultPlaybackSpeed = if (prefs.defaultPlaybackSpeed > 0) prefs.defaultPlaybackSpeed else 1.0f,
                            skipIntervalSeconds = if (prefs.skipIntervalSeconds > 0) prefs.skipIntervalSeconds else 30,
                            sleepTimerDefaultMinutes = if (prefs.sleepTimerDefaultMinutes > 0) prefs.sleepTimerDefaultMinutes else 30,
                            rememberPosition = prefs.rememberPosition,
                            theme = prefs.theme,
                            coverQuality = if (prefs.coverQuality.isNotBlank()) prefs.coverQuality else "High (Original)",
                            ttsEngine = if (prefs.ttsEngine.isNotBlank()) prefs.ttsEngine else "auto",
                            ttsDefaultVoice = if (prefs.ttsDefaultVoice.isNotBlank()) prefs.ttsDefaultVoice else "en_US-amy-medium",
                            autoAssignVoices = prefs.autoAssignVoices,
                            cacheSizeBytes = prefs.cacheSizeBytes,
                            downloadPath = if (prefs.downloadPath.isNotBlank()) prefs.downloadPath else "/mnt/drive2/audiobooks",
                            currentVersion = "1.0.0",
                            buildNumber = 1,
                            autoUpdate = prefs.autoUpdate,
                        )
                    }
                }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnected = false) }
            try {
                val response = apiService.healthCheck()
                _uiState.update { it.copy(isConnected = response["status"] == "healthy") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isConnected = false) }
            }
        }
    }

    fun testConnection() {
        checkConnection()
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val response = apiService.scanLibrary(forceRefresh = true)
                _uiState.update { it.copy(isSyncing = false, lastSyncTime = System.currentTimeMillis()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true, updateStatus = "Checking...") }
            try {
                val response: OTAUpdateResponse = apiService.checkForUpdates(
                    currentVersion = uiState.value.currentVersion,
                    platform = "android",
                    channel = "stable"
                )

                _uiState.update {
                    it.copy(
                        isCheckingUpdates = false,
                        updateStatus = if (response.updateAvailable) {
                            "Update available: ${response.latestVersion}"
                        } else {
                            "Up to date"
                        },
                        updateAvailable = response.updateAvailable,
                        latestVersion = response.latestVersion,
                        updateInfo = response.updateInfo
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingUpdates = false,
                        updateStatus = "Failed to check: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoSync(enabled) }
    }

    fun setSyncHighlights(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSyncHighlights(enabled) }
    }

    fun setRememberPosition(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setRememberPosition(enabled) }
    }

    fun setAutoAssignVoices(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoAssignVoices(enabled) }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch { 
            settingsDataStore.setAutoUpdate(enabled)
            otaScheduler.onAutoUpdateChanged(enabled)
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch { settingsDataStore.setServerUrl(url) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsDataStore.setDefaultPlaybackSpeed(speed) }
    }

    fun setSkipIntervalSeconds(seconds: Int) {
        viewModelScope.launch { settingsDataStore.setSkipIntervalSeconds(seconds) }
    }

    fun setSleepTimerDefaultMinutes(minutes: Int) {
        viewModelScope.launch { settingsDataStore.setSleepTimerDefaultMinutes(minutes) }
    }

    fun setTheme(theme: Int) {
        viewModelScope.launch { settingsDataStore.setTheme(theme) }
    }

    fun setCoverQuality(quality: String) {
        viewModelScope.launch { settingsDataStore.setCoverQuality(quality) }
    }

    fun setTtsEngine(engine: String) {
        viewModelScope.launch { settingsDataStore.setTtsEngine(engine) }
    }

    fun setTtsDefaultVoice(voice: String) {
        viewModelScope.launch { settingsDataStore.setTtsDefaultVoice(voice) }
    }

    fun setCacheSizeBytes(bytes: Long) {
        viewModelScope.launch { settingsDataStore.setCacheSizeBytes(bytes) }
    }

    fun setDownloadPath(path: String) {
        viewModelScope.launch { settingsDataStore.setDownloadPath(path) }
    }

    fun clearCache() {
        viewModelScope.launch {
            // Clear Room database cache, image cache, etc.
            settingsDataStore.setCacheSizeBytes(0)
            _uiState.update { it.copy(cacheSizeBytes = 0) }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsDataStore.resetToDefaults()
            otaScheduler.cancel()
            // The settingsFlow will re-emit defaults; also clear transient UI fields
            _uiState.update {
                it.copy(
                    serverUrl = "https://yourdomain.com",
                    autoSync = true,
                    syncFrequency = "On App Start",
                    syncHighlights = true,
                    defaultPlaybackSpeed = 1.0f,
                    skipIntervalSeconds = 30,
                    sleepTimerDefaultMinutes = 30,
                    rememberPosition = true,
                    theme = 2,
                    coverQuality = "High (Original)",
                    ttsEngine = "auto",
                    ttsDefaultVoice = "en_US-amy-medium",
                    autoAssignVoices = true,
                    cacheSizeBytes = 0,
                    downloadPath = "/mnt/drive2/audiobooks",
                    autoUpdate = false,
                )
            }
        }
    }

    fun showClearDataConfirmation() {
        // TODO: Show confirmation dialog
    }
}

data class SettingsUiState(
    // Server
    var serverUrl: String = "",
    var isConnected: Boolean = false,

    // Sync
    var autoSync: Boolean = false,
    var syncFrequency: String = "On App Start",
    var syncHighlights: Boolean = false,
    var isSyncing: Boolean = false,
    var lastSyncTime: Long = 0,

    // Playback
    var defaultPlaybackSpeed: Float = 1.0f,
    var skipIntervalSeconds: Int = 30,
    var sleepTimerDefaultMinutes: Int = 30,
    var rememberPosition: Boolean = true,

    // Appearance
    var theme: Int = 2, // 0=System, 1=Light, 2=Dark
    var coverQuality: String = "High (Original)",

    // TTS
    var ttsEngine: String = "auto",
    var ttsDefaultVoice: String = "en_US-amy-medium",
    var autoAssignVoices: Boolean = true,

    // Storage
    var cacheSizeBytes: Long = 0,
    var downloadPath: String = "",

    // Updates
    var currentVersion: String = "1.0.0",
    var buildNumber: Int = 1,
    var isCheckingUpdates: Boolean = false,
    var updateStatus: String = "Unknown",
    var updateAvailable: Boolean = false,
    var latestVersion: String = "",
    var updateInfo: com.audibleclone.model.BookModels.OTAUpdateInfo? = null,
    var autoUpdate: Boolean = false,
)