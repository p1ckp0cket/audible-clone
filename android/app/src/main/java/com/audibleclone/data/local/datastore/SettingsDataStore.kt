package com.audibleclone.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Extension property to create DataStore
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

/**
 * Preferences keys for all settings.
 * Matches requirements: server_url, auto_sync, sync_frequency, sync_highlights,
 * default_speed, skip_interval, sleep_timer, remember_position, theme, cover_quality,
 * tts_engine, tts_default_voice, auto_assign_voices, auto_update, cache_size
 */
object PreferencesKeys {
    // Server
    val SERVER_URL = stringPreferencesKey("server_url")
    val SERVER_URL_SET = booleanPreferencesKey("server_url_set")

    // Sync
    val AUTO_SYNC = booleanPreferencesKey("auto_sync")
    val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
    val SYNC_HIGHLIGHTS = booleanPreferencesKey("sync_highlights")

    // Playback
    val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_speed")
    val SKIP_INTERVAL_SECONDS = intPreferencesKey("skip_interval")
    val SLEEP_TIMER_DEFAULT_MINUTES = intPreferencesKey("sleep_timer")
    val REMEMBER_POSITION = booleanPreferencesKey("remember_position")

    // Appearance
    val THEME = intPreferencesKey("theme")
    val COVER_QUALITY = stringPreferencesKey("cover_quality")

    // TTS
    val TTS_ENGINE = stringPreferencesKey("tts_engine")
    val TTS_DEFAULT_VOICE = stringPreferencesKey("tts_default_voice")
    val AUTO_ASSIGN_VOICES = booleanPreferencesKey("auto_assign_voices")

    // Storage
    val CACHE_SIZE_BYTES = longPreferencesKey("cache_size")
    val DOWNLOAD_PATH = stringPreferencesKey("download_path")

    // Updates
    val AUTO_UPDATE = booleanPreferencesKey("auto_update")

    // Search
    val RECENT_SEARCHES = stringSetPreferencesKey("recent_searches")
}

@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context
) {
    private val dataStore: DataStore<Preferences> = context.settingsDataStore

    val settingsFlow: Flow<SettingsMap> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            SettingsMap(
                serverUrl = preferences[PreferencesKeys.SERVER_URL] ?: "",
                serverUrlSet = preferences[PreferencesKeys.SERVER_URL_SET] ?: false,
                autoSync = preferences[PreferencesKeys.AUTO_SYNC] ?: true,
                syncFrequency = preferences[PreferencesKeys.SYNC_FREQUENCY] ?: "On App Start",
                syncHighlights = preferences[PreferencesKeys.SYNC_HIGHLIGHTS] ?: true,
                defaultPlaybackSpeed = preferences[PreferencesKeys.DEFAULT_PLAYBACK_SPEED] ?: 1.0f,
                skipIntervalSeconds = preferences[PreferencesKeys.SKIP_INTERVAL_SECONDS] ?: 30,
                sleepTimerDefaultMinutes = preferences[PreferencesKeys.SLEEP_TIMER_DEFAULT_MINUTES] ?: 30,
                rememberPosition = preferences[PreferencesKeys.REMEMBER_POSITION] ?: true,
                theme = preferences[PreferencesKeys.THEME] ?: 2,
                coverQuality = preferences[PreferencesKeys.COVER_QUALITY] ?: "High (Original)",
                ttsEngine = preferences[PreferencesKeys.TTS_ENGINE] ?: "auto",
                ttsDefaultVoice = preferences[PreferencesKeys.TTS_DEFAULT_VOICE] ?: "en_US-amy-medium",
                autoAssignVoices = preferences[PreferencesKeys.AUTO_ASSIGN_VOICES] ?: true,
                cacheSizeBytes = preferences[PreferencesKeys.CACHE_SIZE_BYTES] ?: 0L,
                downloadPath = preferences[PreferencesKeys.DOWNLOAD_PATH] ?: "",
                autoUpdate = preferences[PreferencesKeys.AUTO_UPDATE] ?: false,
            )
        }

    // Recent searches flow
    val recentSearchesFlow: Flow<List<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.RECENT_SEARCHES]?.toList()?.reversed() ?: emptyList()
        }

    // Individual setters using suspend functions
    suspend fun setServerUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = url
            preferences[PreferencesKeys.SERVER_URL_SET] = true
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SYNC] = enabled
        }
    }

    suspend fun setSyncFrequency(frequency: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_FREQUENCY] = frequency
        }
    }

    suspend fun setSyncHighlights(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_HIGHLIGHTS] = enabled
        }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_PLAYBACK_SPEED] = speed
        }
    }

    suspend fun setSkipIntervalSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SKIP_INTERVAL_SECONDS] = seconds
        }
    }

    suspend fun setSleepTimerDefaultMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SLEEP_TIMER_DEFAULT_MINUTES] = minutes
        }
    }

    suspend fun setRememberPosition(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMEMBER_POSITION] = enabled
        }
    }

    suspend fun setTheme(theme: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme
        }
    }

    suspend fun setCoverQuality(quality: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COVER_QUALITY] = quality
        }
    }

    suspend fun setTtsEngine(engine: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_ENGINE] = engine
        }
    }

    suspend fun setTtsDefaultVoice(voice: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_DEFAULT_VOICE] = voice
        }
    }

    suspend fun setAutoAssignVoices(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_ASSIGN_VOICES] = enabled
        }
    }

    suspend fun setCacheSizeBytes(bytes: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CACHE_SIZE_BYTES] = bytes
        }
    }

    suspend fun setDownloadPath(path: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_PATH] = path
        }
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPDATE] = enabled
        }
    }

    suspend fun getAutoUpdate(): Boolean {
        return dataStore.data.first()[PreferencesKeys.AUTO_UPDATE] ?: false
    }

    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun addRecentSearch(query: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.RECENT_SEARCHES]?.toMutableSet() ?: mutableSetOf()
            current.remove(query) // Remove if exists to move to front
            current.add(query)
            // Keep only last 10
            val trimmed = current.take(10)
            preferences[PreferencesKeys.RECENT_SEARCHES] = trimmed
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENT_SEARCHES] = emptySet()
        }
    }
}

/**
 * Data class to hold all settings as a single object
 */
data class SettingsMap(
    val serverUrl: String = "https://yourdomain.com",
    val serverUrlSet: Boolean = false,
    val autoSync: Boolean = true,
    val syncFrequency: String = "On App Start",
    val syncHighlights: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val skipIntervalSeconds: Int = 30,
    val sleepTimerDefaultMinutes: Int = 30,
    val rememberPosition: Boolean = true,
    val theme: Int = 2,
    val coverQuality: String = "High (Original)",
    val ttsEngine: String = "auto",
    val ttsDefaultVoice: String = "en_US-amy-medium",
    val autoAssignVoices: Boolean = true,
    val cacheSizeBytes: Long = 0L,
    val downloadPath: String = "/mnt/drive2/audiobooks",
    val autoUpdate: Boolean = false,
)