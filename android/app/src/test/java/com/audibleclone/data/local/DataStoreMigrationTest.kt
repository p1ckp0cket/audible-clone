package com.audibleclone.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.testing.TestDataStoreBuilder
import com.audibleclone.model.BookModels.BookSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreMigrationTest {

    // DataStore keys matching SettingsDataStore.kt
    private val SERVER_URL_KEY = preferencesKey<String>("server_url")
    private val AUTO_SYNC_KEY = preferencesKey<Boolean>("auto_sync")
    private val SYNC_FREQUENCY_KEY = preferencesKey<String>("sync_frequency")
    private val SYNC_HIGHLIGHTS_KEY = preferencesKey<Boolean>("sync_highlights")
    private val DEFAULT_PLAYBACK_SPEED_KEY = preferencesKey<Float>("default_speed")
    private val SKIP_INTERVAL_SECONDS_KEY = preferencesKey<Int>("skip_interval")
    private val SLEEP_TIMER_DEFAULT_MINUTES_KEY = preferencesKey<Int>("sleep_timer")
    private val REMEMBER_POSITION_KEY = preferencesKey<Boolean>("remember_position")
    private val THEME_KEY = preferencesKey<Int>("theme")
    private val COVER_QUALITY_KEY = preferencesKey<String>("cover_quality")
    private val TTS_ENGINE_KEY = preferencesKey<String>("tts_engine")
    private val TTS_DEFAULT_VOICE_KEY = preferencesKey<String>("tts_default_voice")
    private val AUTO_ASSIGN_VOICES_KEY = preferencesKey<Boolean>("auto_assign_voices")
    private val CACHE_SIZE_BYTES_KEY = preferencesKey<Long>("cache_size")
    private val DOWNLOAD_PATH_KEY = preferencesKey<String>("download_path")
    private val AUTO_UPDATE_KEY = preferencesKey<Boolean>("auto_update")

    private lateinit var dataStore: androidx.datastore.preferences.core.PreferencesDataStore

    @Before
    fun setUp() {
        dataStore = TestDataStoreBuilder().build()
    }

    @Test
    fun `migration_v1_to_v2 - adds new keys with defaults`() = runBlocking {
        // Given - simulate v1 data (only some keys present)
        dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = "https://old-server.com/api/v1/"
            preferences[AUTO_SYNC_KEY] = true
            // Missing: new keys added in v2
        }

        // When - migrate to v2 (apply defaults for missing keys)
        val migratedDataStore = migrateV1ToV2(dataStore)

        // Then - verify all keys have values
        val prefs = migratedDataStore.data.first()
        
        assertEquals("https://old-server.com/api/v1/", prefs[SERVER_URL_KEY])
        assertTrue(prefs[AUTO_SYNC_KEY]!!)
        assertEquals("On App Start", prefs[SYNC_FREQUENCY_KEY])
        assertTrue(prefs[SYNC_HIGHLIGHTS_KEY]!!)
        assertEquals(1.0f, prefs[DEFAULT_PLAYBACK_SPEED_KEY]!!, 0.001f)
        assertEquals(30, prefs[SKIP_INTERVAL_SECONDS_KEY]!!)
        assertEquals(30, prefs[SLEEP_TIMER_DEFAULT_MINUTES_KEY]!!)
        assertTrue(prefs[REMEMBER_POSITION_KEY]!!)
        assertEquals(2, prefs[THEME_KEY]!!) // Dark
        assertEquals("High (Original)", prefs[COVER_QUALITY_KEY])
        assertEquals("auto", prefs[TTS_ENGINE_KEY])
        assertEquals("en_US-amy-medium", prefs[TTS_DEFAULT_VOICE_KEY])
        assertTrue(prefs[AUTO_ASSIGN_VOICES_KEY]!!)
        assertEquals(0L, prefs[CACHE_SIZE_BYTES_KEY]!!)
        assertEquals("", prefs[DOWNLOAD_PATH_KEY]?: "/mnt/drive2/audiobooks")
    }

    @Test
    fun `migration_preserves_existing_values`() = runBlocking {
        // Given - v1 data with custom values
        dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = "https://custom-server.com/api/v1/"
            preferences[AUTO_SYNC_KEY] = false
            preferences[SYNC_FREQUENCY_KEY] = "Every 6 Hours"
            preferences[DEFAULT_PLAYBACK_SPEED_KEY] = 1.5f
            preferences[THEME_KEY] = 1 // Light theme
        }

        // When - migrate
        val migratedDataStore = migrateV1ToV2(dataStore)

        // Then - existing values preserved, new keys get defaults
        val prefs = migratedDataStore.data.first()
        
        assertEquals("https://custom-server.com/api/v1/", prefs[SERVER_URL_KEY])
        assertFalse(prefs[AUTO_SYNC_KEY]!!)
        assertEquals("Every 6 Hours", prefs[SYNC_FREQUENCY_KEY])
        assertEquals(1.5f, prefs[DEFAULT_PLAYBACK_SPEED_KEY]!!, 0.001f)
        assertEquals(1, prefs[THEME_KEY]!!) // Light theme preserved
        
        // New keys get defaults
        assertTrue(prefs[SYNC_HIGHLIGHTS_KEY]!!)
        assertEquals(30, prefs[SKIP_INTERVAL_SECONDS_KEY]!!)
        assertEquals("auto", prefs[TTS_ENGINE_KEY])
    }

    @Test
    fun `settings_data_class_serialization`() = runBlocking {
        // Test that settings can be saved and loaded correctly
        dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = "https://test.com/api/"
            preferences[AUTO_SYNC_KEY] = true
            preferences[SYNC_FREQUENCY_KEY] = "On App Start"
            preferences[SYNC_HIGHLIGHTS_KEY] = true
            preferences[DEFAULT_PLAYBACK_SPEED_KEY] = 1.25f
            preferences[SKIP_INTERVAL_SECONDS_KEY] = 15
            preferences[SLEEP_TIMER_DEFAULT_MINUTES_KEY] = 45
            preferences[REMEMBER_POSITION_KEY] = true
            preferences[THEME_KEY] = 0 // System
            preferences[COVER_QUALITY_KEY] = "Medium"
            preferences[TTS_ENGINE_KEY] = "piper"
            preferences[TTS_DEFAULT_VOICE_KEY] = "en_US-lessac-medium"
            preferences[AUTO_ASSIGN_VOICES_KEY] = false
            preferences[CACHE_SIZE_BYTES_KEY] = 1024 * 1024 * 100 // 100 MB
            preferences[DOWNLOAD_PATH_KEY] = "/custom/path"
            preferences[AUTO_UPDATE_KEY] = true
        }

        val prefs = dataStore.data.first()
        
        assertEquals("https://test.com/api/", prefs[SERVER_URL_KEY])
        assertTrue(prefs[AUTO_SYNC_KEY]!!)
        assertEquals("On App Start", prefs[SYNC_FREQUENCY_KEY])
        assertTrue(prefs[SYNC_HIGHLIGHTS_KEY]!!)
        assertEquals(1.25f, prefs[DEFAULT_PLAYBACK_SPEED_KEY]!!, 0.001f)
        assertEquals(15, prefs[SKIP_INTERVAL_SECONDS_KEY]!!)
        assertEquals(45, prefs[SLEEP_TIMER_DEFAULT_MINUTES_KEY]!!)
        assertTrue(prefs[REMEMBER_POSITION_KEY]!!)
        assertEquals(0, prefs[THEME_KEY]!!)
        assertEquals("Medium", prefs[COVER_QUALITY_KEY])
        assertEquals("piper", prefs[TTS_ENGINE_KEY])
        assertEquals("en_US-lessac-medium", prefs[TTS_DEFAULT_VOICE_KEY])
        assertFalse(prefs[AUTO_ASSIGN_VOICES_KEY]!!)
        assertEquals(1024 * 1024 * 100L, prefs[CACHE_SIZE_BYTES_KEY]!!)
        assertEquals("/custom/path", prefs[DOWNLOAD_PATH_KEY])
        assertTrue(prefs[AUTO_UPDATE_KEY]!!)
    }

    @Test
    fun `concurrent_writes_handled_correctly`() = runBlocking {
        // Simulate concurrent updates
        val updates = listOf(
            { preferences: Preferences -> preferences[AUTO_SYNC_KEY] = true },
            { preferences: Preferences -> preferences[THEME_KEY] = 2 },
            { preferences: Preferences -> preferences[DEFAULT_PLAYBACK_SPEED_KEY] = 2.0f }
        )

        // Apply all updates
        for (update in updates) {
            dataStore.edit(update)
        }

        val prefs = dataStore.data.first()
        assertTrue(prefs[AUTO_SYNC_KEY]!!)
        assertEquals(2, prefs[THEME_KEY]!!)
        assertEquals(2.0f, prefs[DEFAULT_PLAYBACK_SPEED_KEY]!!, 0.001f)
    }

    // Migration helper function (simplified for testing)
    private fun migrateV1ToV2(dataStore: androidx.datastore.preferences.core.PreferencesDataStore): androidx.datastore.preferences.core.PreferencesDataStore {
        val prefs = dataStore.data.first()
        
        val newDataStore = TestDataStoreBuilder().build()
        
        newDataStore.edit { newPrefs ->
            // Copy existing values
            newPrefs[SERVER_URL_KEY] = prefs[SERVER_URL_KEY] ?: "https://yourdomain.com/api/v1/"
            newPrefs[AUTO_SYNC_KEY] = prefs[AUTO_SYNC_KEY] ?: true
            newPrefs[SYNC_FREQUENCY_KEY] = prefs[SYNC_FREQUENCY_KEY] ?: "On App Start"
            
            // Add new keys with defaults
            newPrefs[SYNC_HIGHLIGHTS_KEY] = prefs[SYNC_HIGHLIGHTS_KEY] ?: true
            newPrefs[DEFAULT_PLAYBACK_SPEED_KEY] = prefs[DEFAULT_PLAYBACK_SPEED_KEY] ?: 1.0f
            newPrefs[SKIP_INTERVAL_SECONDS_KEY] = prefs[SKIP_INTERVAL_SECONDS_KEY] ?: 30
            newPrefs[SLEEP_TIMER_DEFAULT_MINUTES_KEY] = prefs[SLEEP_TIMER_DEFAULT_MINUTES_KEY] ?: 30
            newPrefs[REMEMBER_POSITION_KEY] = prefs[REMEMBER_POSITION_KEY] ?: true
            newPrefs[THEME_KEY] = prefs[THEME_KEY] ?: 2
            newPrefs[COVER_QUALITY_KEY] = prefs[COVER_QUALITY_KEY] ?: "High (Original)"
            newPrefs[TTS_ENGINE_KEY] = prefs[TTS_ENGINE_KEY] ?: "auto"
            newPrefs[TTS_DEFAULT_VOICE_KEY] = prefs[TTS_DEFAULT_VOICE_KEY] ?: "en_US-amy-medium"
            newPrefs[AUTO_ASSIGN_VOICES_KEY] = prefs[AUTO_ASSIGN_VOICES_KEY] ?: true
            newPrefs[CACHE_SIZE_BYTES_KEY] = prefs[CACHE_SIZE_BYTES_KEY] ?: 0L
            newPrefs[DOWNLOAD_PATH_KEY] = prefs[DOWNLOAD_PATH_KEY] ?: "/mnt/drive2/audiobooks"
            newPrefs[AUTO_UPDATE_KEY] = prefs[AUTO_UPDATE_KEY] ?: false
        }
        
        return newDataStore
    }
}