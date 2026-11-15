package com.audibleclone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audibleclone.R
import com.audibleclone.data.repository.SettingsRepository
import com.audibleclone.ui.theme.AudibleCloneTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    repository: SettingsRepository = viewModel()
) {
    val uiState by repository.uiState.collectAsStateWithLifecycle()
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            TopAppBar(
                title = { Text(text = "Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AudibleCloneTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = AudibleCloneTheme.colorScheme.onSurface
                )
            )
            
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Server Configuration
                item {
                    SettingsSection(title = "Server") {
                        SettingRow(
                            title = "Server URL",
                            subtitle = uiState.serverUrl,
                            icon = androidx.compose.material.icons.Icons.Filled.Cloud,
                            onClick = { /* TODO: Edit server URL */ }
                        )
                        SettingRow(
                            title = "Connection Status",
                            subtitle = if (uiState.isConnected) "Connected" else "Disconnected",
                            icon = androidx.compose.material.icons.Icons.Filled.Wifi,
                            trailing = {
                                androidx.compose.material3.Icon(
                                    imageVector = if (uiState.isConnected) 
                                        androidx.compose.material.icons.Icons.Filled.CheckCircle 
                                    else 
                                        androidx.compose.material.icons.Icons.Filled.CloseCircle,
                                    contentDescription = if (uiState.isConnected) "Connected" else "Disconnected",
                                    tint = if (uiState.isConnected) AudibleCloneTheme.colorScheme.primary else AudibleCloneTheme.colorScheme.error
                                )
                            }
                        )
                        SettingRow(
                            title = "Test Connection",
                            subtitle = "Verify server connectivity",
                            icon = androidx.compose.material.icons.Icons.Filled.Refresh,
                            onClick = { repository.testConnection() }
                        )
                    }
                }
                
                // Sync Settings
                item {
                    SettingsSection(title = "Sync") {
                        SettingRow(
                            title = "Auto Sync",
                            subtitle = "Automatically sync library on app start",
                            icon = androidx.compose.material.icons.Icons.Filled.Sync,
                            trailing = {
                                Switch(
                                    checked = uiState.autoSync,
                                    onCheckedChange = { repository.setAutoSync(it) },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        thumbColor = AudibleCloneTheme.colorScheme.primary,
                                        trackColor = AudibleCloneTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        )
                        SettingRow(
                            title = "Sync Frequency",
                            subtitle = uiState.syncFrequency,
                            icon = androidx.compose.material.icons.Icons.Filled.Schedule,
                            onClick = { /* TODO: Show frequency picker */ }
                        )
                        SettingRow(
                            title = "Sync Highlights",
                            subtitle = "Sync annotations across devices",
                            icon = androidx.compose.material.icons.Icons.Filled.Highlight,
                            trailing = {
                                Switch(
                                    checked = uiState.syncHighlights,
                                    onCheckedChange = { repository.setSyncHighlights(it) },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        thumbColor = AudibleCloneTheme.colorScheme.primary,
                                        trackColor = AudibleCloneTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        )
                        SettingRow(
                            title = "Sync Now",
                            subtitle = "Manually trigger library sync",
                            icon = androidx.compose.material.icons.Icons.Filled.Sync,
                            onClick = { repository.syncNow() },
                            enabled = !uiState.isSyncing
                        )
                    }
                }
                
                // Playback Settings
                item {
                    SettingsSection(title = "Playback") {
                        SettingRow(
                            title = "Default Playback Speed",
                            subtitle = "${uiState.defaultPlaybackSpeed}x",
                            icon = androidx.compose.material.icons.Icons.Filled.Speed,
                            onClick = { /* TODO: Show speed picker */ }
                        )
                        SettingRow(
                            title = "Skip Interval",
                            subtitle = "${uiState.skipIntervalSeconds}s forward / backward",
                            icon = androidx.compose.material.icons.Icons.Filled.Forward30,
                            onClick = { /* TODO: Show interval picker */ }
                        )
                        SettingRow(
                            title = "Sleep Timer Default",
                            subtitle = formatDuration(uiState.sleepTimerDefaultMinutes * 60),
                            icon = androidx.compose.material.icons.Icons.Filled.Bedtime,
                            onClick = { /* TODO: Show timer picker */ }
                        )
                        SettingRow(
                            title = "Remember Position",
                            subtitle = "Resume from where you left off",
                            icon = androidx.compose.material.icons.Icons.Filled.History,
                            trailing = {
                                Switch(
                                    checked = uiState.rememberPosition,
                                    onCheckedChange = { repository.setRememberPosition(it) },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        thumbColor = AudibleCloneTheme.colorScheme.primary,
                                        trackColor = AudibleCloneTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        )
                    }
                }
                
                // Appearance
                item {
                    SettingsSection(title = "Appearance") {
                        SettingRow(
                            title = "Theme",
                            subtitle = when (uiState.theme) {
                                0 -> "System Default"
                                1 -> "Light"
                                2 -> "Dark"
                                else -> "Dark"
                            },
                            icon = androidx.compose.material.icons.Icons.Filled.Palette,
                            onClick = { /* TODO: Show theme picker */ }
                        )
                        SettingRow(
                            title = "Cover Art Quality",
                            subtitle = uiState.coverQuality,
                            icon = androidx.compose.material.icons.Icons.Filled.Image,
                            onClick = { /* TODO: Show quality picker */ }
                        )
                    }
                }
                
                // TTS Settings
                item {
                    SettingsSection(title = "Text-to-Speech") {
                        SettingRow(
                            title = "Preferred Engine",
                            subtitle = when (uiState.ttsEngine) {
                                "auto" -> "Auto (Best Available)"
                                "piper" -> "Piper (Fast, CPU)"
                                "kokoro" -> "Kokoro (Premium Quality)"
                                else -> uiState.ttsEngine
                            },
                            icon = androidx.compose.material.icons.Icons.Filled.Mic,
                            onClick = { /* TODO: Show engine picker */ }
                        )
                        SettingRow(
                            title = "Default Voice",
                            subtitle = uiState.ttsDefaultVoice,
                            icon = androidx.compose.material.icons.Icons.Filled.VoiceOver,
                            onClick = { /* TODO: Show voice picker */ }
                        )
                        SettingRow(
                            title = "Character Voice Assignment",
                            subtitle = if (uiState.autoAssignVoices) "Automatic (by dialogue amount)" else "Manual",
                            icon = androidx.compose.material.icons.Icons.Filled.Psychology,
                            trailing = {
                                Switch(
                                    checked = uiState.autoAssignVoices,
                                    onCheckedChange = { repository.setAutoAssignVoices(it) },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        thumbColor = AudibleCloneTheme.colorScheme.primary,
                                        trackColor = AudibleCloneTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        )
                    }
                }
                
                // Storage
                item {
                    SettingsSection(title = "Storage") {
                        SettingRow(
                            title = "Cache Size",
                            subtitle = formatBytes(uiState.cacheSizeBytes),
                            icon = androidx.compose.material.icons.Icons.Filled.Storage,
                            trailing = {
                                Button(onClick = { repository.clearCache() }) {
                                    Text(text = "Clear Cache")
                                }
                            }
                        )
                        SettingRow(
                            title = "Download Location",
                            subtitle = uiState.downloadPath,
                            icon = androidx.compose.material.icons.Icons.Filled.Folder,
                            onClick = { /* TODO: Show path picker */ }
                        )
                    }
                }
                
                // OTA Updates
                item {
                    SettingsSection(title = "Updates") {
                        SettingRow(
                            title = "Current Version",
                            subtitle = uiState.currentVersion,
                            icon = androidx.compose.material.icons.Icons.Filled.Info,
                        )
                        SettingRow(
                            title = "Check for Updates",
                            subtitle = uiState.updateStatus,
                            icon = androidx.compose.material.icons.Icons.Filled.SystemUpdate,
                            onClick = { repository.checkForUpdates() },
                            enabled = !uiState.isCheckingUpdates
                        )
                        SettingRow(
                            title = "Auto Update",
                            subtitle = "Automatically download and install updates",
                            icon = androidx.compose.material.icons.Icons.Filled.AutoMode,
                            trailing = {
                                Switch(
                                    checked = uiState.autoUpdate,
                                    onCheckedChange = { repository.setAutoUpdate(it) },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        thumbColor = AudibleCloneTheme.colorScheme.primary,
                                        trackColor = AudibleCloneTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        )
                    }
                }
                
                // About
                item {
                    SettingsSection(title = "About") {
                        SettingRow(
                            title = "App Version",
                            subtitle = "${uiState.currentVersion} (${uiState.buildNumber})",
                            icon = androidx.compose.material.icons.Icons.Filled.Info,
                        )
                        SettingRow(
                            title = "Open Source Licenses",
                            subtitle = "View third-party licenses",
                            icon = androidx.compose.material.icons.Icons.Filled.Article,
                            onClick = { /* TODO: Show licenses */ }
                        )
                        SettingRow(
                            title = "Privacy Policy",
                            subtitle = "View privacy policy",
                            icon = androidx.compose.material.icons.Icons.Filled.PrivacyTip,
                            onClick = { /* TODO: Show privacy policy */ }
                        )
                        SettingRow(
                            title = "Report Bug",
                            subtitle = "Send feedback or report an issue",
                            icon = androidx.compose.material.icons.Icons.Filled.BugReport,
                            onClick = { /* TODO: Open bug report */ }
                        )
                    }
                }
                
                // Danger Zone
                item {
                    SettingsSection(title = "Danger Zone", isDestructive = true) {
                        SettingRow(
                            title = "Clear All Data",
                            subtitle = "Delete all books, highlights, and settings",
                            icon = androidx.compose.material.icons.Icons.Filled.DeleteForever,
                            iconColor = AudibleCloneTheme.colorScheme.error,
                            onClick = { repository.showClearDataConfirmation() },
                            destructive = true
                        )
                        SettingRow(
                            title = "Reset to Defaults",
                            subtitle = "Reset all settings to default values",
                            icon = androidx.compose.material.icons.Icons.Filled.Restore,
                            onClick = { repository.resetToDefaults() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    isDestructive: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDestructive) AudibleCloneTheme.colorScheme.error else AudibleCloneTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, bottom = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (isDestructive) AudibleCloneTheme.colorScheme.errorContainer else AudibleCloneTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = if (destructive) {
        androidx.compose.material3.ContentColorPair(AudibleCloneTheme.colorScheme.error, AudibleCloneTheme.colorScheme.onError)
    } else {
        androidx.compose.material3.ContentColorPair(AudibleCloneTheme.colorScheme.onSurface, AudibleCloneTheme.colorScheme.onSurfaceVariant)
    }
    
    val clickable = onClick != null && enabled
    
    androidx.compose.material3.ListItem(
        onClick = if (clickable) onClick else null,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        headContent = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (iconColor != androidx.compose.ui.graphics.Color.Unspecified) iconColor else colors.secondary.copy(alpha = 0.6f)
            )
        },
        bodyContent = {
            Column {
                Text(
                    text = title,
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = colors.secondary.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        },
        trailingContent = trailing
    )
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else if (minutes > 0) {
        "%dm %ds".format(minutes, secs)
    } else {
        "${secs}s"
    }
}