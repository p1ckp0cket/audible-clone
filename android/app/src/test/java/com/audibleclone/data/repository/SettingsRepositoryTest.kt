package com.audibleclone.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesKeys
import androidx.datastore.preferences.core.mutations
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.testing.PreferenceDataStoreFactory
import androidx.datastore.preferences.testing.TestDataStoreBuilder
import com.audibleclone.data.api.ApiService
import com.audibleclone.model.BookModels.OTAUpdateResponse
import com.audibleclone.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import retrofit2.HttpException
import retrofit2.Response

class SettingsRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var apiService: ApiService

    private lateinit var repository: SettingsRepository

    // DataStore keys matching the actual implementation
    private val SERVER_URL_KEY = preferencesKey<String>("server_url")
    private val AUTO_SYNC_KEY = preferencesKey<Boolean>("auto_sync")
    private val SYNC_FREQUENCY_KEY = preferencesKey<String>("sync_frequency")
    private val SYNC_HIGHLIGHTS_KEY = preferencesKey<Boolean>("sync_highlights")
    private val DEFAULT_PLAYBACK_SPEED_KEY = preferencesKey<Float>("default_playback_speed")
    private val SKIP_INTERVAL_SECONDS_KEY = preferencesKey<Int>("skip_interval_seconds")
    private val SLEEP_TIMER_DEFAULT_MINUTES_KEY = preferencesKey<Int>("sleep_timer_default_minutes")
    private val REMEMBER_POSITION_KEY = preferencesKey<Boolean>("remember_position")
    private val THEME_KEY = preferencesKey<Int>("theme")
    private val COVER_QUALITY_KEY = preferencesKey<String>("cover_quality")
    private val TTS_ENGINE_KEY = preferencesKey<String>("tts_engine")
    private val TTS_DEFAULT_VOICE_KEY = preferencesKey<String>("tts_default_voice")
    private val AUTO_ASSIGN_VOICES_KEY = preferencesKey<Boolean>("auto_assign_voices")
    private val DOWNLOAD_PATH_KEY = preferencesKey<String>("download_path")
    private val AUTO_UPDATE_KEY = preferencesKey<Boolean>("auto_update")

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Note: In real implementation, SettingsRepository would take DataStore as dependency
        // For now, we test the API interactions
        repository = SettingsRepository(apiService)
    }

    @Test
    fun `loadSettings - initializes with default values`() = mainDispatcherRule.testDispatcher.runTest {
        // When - repository initializes in constructor
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then - verify default values
        val state = repository.uiState.first()
        assertEquals("https://yourdomain.com", state.serverUrl)
        assertTrue(state.autoSync)
        assertEquals("On App Start", state.syncFrequency)
        assertTrue(state.syncHighlights)
        assertEquals(1.0f, state.defaultPlaybackSpeed, 0.001f)
        assertEquals(30, state.skipIntervalSeconds)
        assertEquals(30, state.sleepTimerDefaultMinutes)
        assertTrue(state.rememberPosition)
        assertEquals(2, state.theme) // Dark
        assertEquals("High (Original)", state.coverQuality)
        assertEquals("auto", state.ttsEngine)
        assertEquals("en_US-amy-medium", state.ttsDefaultVoice)
        assertTrue(state.autoAssignVoices)
        assertEquals("1.0.0", state.currentVersion)
        assertEquals(1, state.buildNumber)
        assertFalse(state.autoUpdate)
    }

    @Test
    fun `checkConnection - sets connected state on success`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val healthResponse = mapOf("status" to "healthy")
        Mockito.`when`(apiService.healthCheck()).thenReturn(healthResponse)

        // When
        repository.checkConnection()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertTrue(state.isConnected)
    }

    @Test
    fun `checkConnection - sets disconnected state on failure`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val error = HttpException(Response.error(503, okhttp3.ResponseBody.create("Service Unavailable", okhttp3.MediaType.parse("text/plain"))))
        Mockito.`when`(apiService.healthCheck()).thenThrow(error)

        // When
        repository.checkConnection()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.isConnected)
    }

    @Test
    fun `testConnection - calls checkConnection`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val healthResponse = mapOf("status" to "healthy")
        Mockito.`when`(apiService.healthCheck()).thenReturn(healthResponse)

        // When
        repository.testConnection()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertTrue(state.isConnected)
        Mockito.verify(apiService).healthCheck()
    }

    @Test
    fun `syncNow - sets syncing state and calls API`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val scanResponse = mapOf("task_id" to "123")
        Mockito.`when`(apiService.scanLibrary(forceRefresh = true)).thenReturn(scanResponse)

        // When
        repository.syncNow()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.isSyncing) // Should complete quickly in test
        assertTrue(state.lastSyncTime > 0)
        Mockito.verify(apiService).scanLibrary(forceRefresh = true)
    }

    @Test
    fun `checkForUpdates - update available`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val updateInfo = com.audibleclone.model.BookModels.OTAUpdateInfo(
            version = "1.1.0",
            release_notes = "Bug fixes",
            download_url = "https://example.com/update.apk",
            force_update = false,
            released_at = "2024-01-01T00:00:00Z"
        )
        val updateResponse = OTAUpdateResponse(
            update_available = true,
            current_version = "1.0.0",
            latest_version = "1.1.0",
            update_info = updateInfo
        )
        Mockito.`when`(apiService.checkForUpdates(currentVersion = "1.0.0", platform = "android", channel = "stable"))
            .thenReturn(updateResponse)

        // When
        repository.checkForUpdates()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.isCheckingUpdates)
        assertTrue(state.updateAvailable)
        assertEquals("1.1.0", state.latestVersion)
        assertEquals("Update available: 1.1.0", state.updateStatus)
        assertNotNull(state.updateInfo)
        assertEquals("1.1.0", state.updateInfo?.version)
    }

    @Test
    fun `checkForUpdates - no update available`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val updateResponse = OTAUpdateResponse(
            update_available = false,
            current_version = "1.0.0",
            latest_version = "1.0.0"
        )
        Mockito.`when`(apiService.checkForUpdates(currentVersion = "1.0.0", platform = "android", channel = "stable"))
            .thenReturn(updateResponse)

        // When
        repository.checkForUpdates()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.isCheckingUpdates)
        assertFalse(state.updateAvailable)
        assertEquals("Up to date", state.updateStatus)
    }

    @Test
    fun `checkForUpdates - API error`() = mainDispatcherRule.testDispatcher.runTest {
        // Given
        val error = HttpException(Response.error(500, okhttp3.ResponseBody.create("Server Error", okhttp3.MediaType.parse("text/plain"))))
        Mockito.`when`(apiService.checkForUpdates(any(), any(), any())).thenThrow(error)

        // When
        repository.checkForUpdates()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.isCheckingUpdates)
        assertTrue(state.updateStatus.contains("Failed to check"))
    }

    @Test
    fun `setAutoSync - updates state`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.setAutoSync(false)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.autoSync)
    }

    @Test
    fun `setSyncHighlights - updates state`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.setSyncHighlights(false)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.syncHighlights)
    }

    @Test
    fun `setRememberPosition - updates state`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.setRememberPosition(false)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.rememberPosition)
    }

    @Test
    fun `setAutoAssignVoices - updates state`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.setAutoAssignVoices(false)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertFalse(state.autoAssignVoices)
    }

    @Test
    fun `setAutoUpdate - updates state`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.setAutoUpdate(true)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertTrue(state.autoUpdate)
    }

    @Test
    fun `clearCache - updates cache size`() = mainDispatcherRule.testDispatcher.runTest {
        // When
        repository.clearCache()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertEquals(0L, state.cacheSizeBytes)
    }

    @Test
    fun `resetToDefaults - resets to default values`() = mainDispatcherRule.testDispatcher.runTest {
        // Given - modify some settings
        repository.setAutoSync(false)
        repository.setRememberPosition(false)
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // When
        repository.resetToDefaults()
        mainDispatcherRule.testDispatcher.advanceUntilIdle()

        // Then
        val state = repository.uiState.first()
        assertTrue(state.autoSync)
        assertTrue(state.rememberPosition)
        assertEquals("https://yourdomain.com", state.serverUrl)
    }
}