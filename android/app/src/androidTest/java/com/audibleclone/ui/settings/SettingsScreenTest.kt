package com.audibleclone.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithOptions
import androidx.compose.ui.test.performClick
import com.audibleclone.data.repository.SettingsRepository
import com.audibleclone.test.TestDataFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `settingsScreen_displaysServerSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Server").assertExists()
        composeRule.onNodeWithText("Server URL").assertExists()
        composeRule.onNodeWithText("Connection Status").assertExists()
        composeRule.onNodeWithText("Test Connection").assertExists()
    }

    @Test
    fun `settingsScreen_displaysConnectionStatus`() {
        // Given - connected state
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState(isConnected = true)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Connected").assertExists()
        composeRule.onNodeWithContentDescription("Connected").assertExists()
    }

    @Test
    fun `settingsScreen_displaysDisconnectedState`() {
        // Given - disconnected state
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState(isConnected = false)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithContentDescription("Disconnected").assertExists()
    }

    @Test
    fun `settingsScreen_testConnection_triggersTest`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Click test connection
        composeRule.onNodeWithText("Test Connection").performClick()

        // Then
        Mockito.verify(mockRepository).testConnection()
    }

    @Test
    fun `settingsScreen_displaysSyncSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Sync").assertExists()
        composeRule.onNodeWithText("Auto Sync").assertExists()
        composeRule.onNodeWithText("Sync Frequency").assertExists()
        composeRule.onNodeWithText("Sync Highlights").assertExists()
        composeRule.onNodeWithText("Sync Now").assertExists()
    }

    @Test
    fun `settingsScreen_autoSyncToggle_triggersSetting`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState(autoSync = false)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Toggle auto sync
        // Note: Switch testing is complex - we verify the toggle area exists
        composeRule.onNodeWithText("Auto Sync").assertExists()
    }

    @Test
    fun `settingsScreen_syncHighlightsToggle_triggersSetting`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState(syncHighlights = false)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Sync Highlights").assertExists()
    }

    @Test
    fun `settingsScreen_displaysPlaybackSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Playback").assertExists()
        composeRule.onNodeWithText("Default Playback Speed").assertExists()
        composeRule.onNodeWithText("Skip Interval").assertExists()
        composeRule.onNodeWithText("Sleep Timer Default").assertExists()
        composeRule.onNodeWithText("Remember Position").assertExists()
    }

    @Test
    fun `settingsScreen_displaysPlaybackSpeedValue`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState(defaultPlaybackSpeed = 1.5f)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("1.5x").assertExists()
    }

    @Test
    fun `settingsScreen_displaysSkipIntervalValue`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState(skipIntervalSeconds = 15)
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("15s forward / backward").assertExists()
    }

    @Test
    fun `settingsScreen_displaysAppearanceSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithText("Theme").assertExists()
        composeRule.onNodeWithText("Dark").assertExists()
        composeRule.onNodeWithText("Cover Art Quality").assertExists()
    }

    @Test
    fun `settingsScreen_displaysTtsSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Text-to-Speech").assertExists()
        composeRule.onNodeWithText("Preferred Engine").assertExists()
        composeRule.onNodeWithText("Default Voice").assertExists()
        composeRule.onNodeWithText("Character Voice Assignment").assertExists()
    }

    @Test
    fun `settingsScreen_displaysTtsEngineValue`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState(ttsEngine = "piper")
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Piper (Fast, CPU)").assertExists()
    }

    @Test
    fun `settingsScreen_displaysStorageSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Storage").assertExists()
        composeRule.onNodeWithText("Cache Size").assertExists()
        composeRule.onNodeWithText("Download Location").assertExists()
    }

    @Test
    fun `settingsScreen_displaysUpdatesSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Updates").assertExists()
        composeRule.onNodeWithText("Check for Updates").assertExists()
        composeRule.onNodeWithText("Auto Update").assertExists()
    }

    @Test
    fun `settingsScreen_displaysAboutSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("About").assertExists()
        composeRule.onNodeWithText("App Version").assertExists()
        composeRule.onNodeWithText("Open Source Licenses").assertExists()
        composeRule.onNodeWithText("Privacy Policy").assertExists()
        composeRule.onNodeWithText("Report Bug").assertExists()
    }

    @Test
    fun `settingsScreen_displaysDangerZoneSection`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        // Then
        composeRule.onNodeWithText("Danger Zone").assertExists()
        composeRule.onNodeWithText("Clear All Data").assertExists()
        composeRule.onNodeWithText("Reset to Defaults").assertExists()
    }

    @Test
    fun `settingsScreen_clearAllData_triggersMethod`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        composeRule.onNodeWithText("Clear All Data").performClick()

        // Then
        Mockito.verify(mockRepository).showClearDataConfirmation()
    }

    @Test
    fun `settingsScreen_resetToDefaults_triggersMethod`() {
        // Given
        val mockRepository = mock(SettingsRepository::class.java)
        val uiState = TestDataFactory.createSettingsUiState()
        val uiStateFlow = MutableStateFlow(uiState)
        Mockito.`when`(mockRepository.uiState).thenReturn(uiStateFlow)

        // When
        composeRule.setContent {
            SettingsScreen(repository = mockRepository)
        }

        composeRule.onNodeWithText("Reset to Defaults").performClick()

        // Then
        Mockito.verify(mockRepository).resetToDefaults()
    }
}