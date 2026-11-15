package com.audibleclone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2E4A47),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A9BB5),
    onPrimaryContainer = Color(0xFF1A2B2C),
    secondary = Color(0xFF1B5E7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE6F0),
    onSecondaryContainer = Color(0xFF1A2B2C),
    tertiary = Color(0xFF5A9BB5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDE6F0),
    onTertiaryContainer = Color(0xFF1A2B2C),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFF1A2B2C),
    onBackground = Color(0xFFCDE6F0),
    surface = Color(0xFF2E4A47),
    onSurface = Color(0xFFCDE6F0),
    surfaceVariant = Color(0xFF3D5A56),
    onSurfaceVariant = Color(0xFFBFCFCB),
    outline = Color(0xFF6E8380),
    outlineVariant = Color(0xFF3D5A56),
    shadow = Color.Black,
    scrim = Color.Black,
    inverseSurface = Color(0xFFCDE6F0),
    inverseOnSurface = Color(0xFF1A2B2C),
    inversePrimary = Color(0xFF2E4A47),
    surfaceDim = Color(0xFF1A2B2C),
    surfaceBright = Color(0xFF3D5A56),
    surfaceContainerLowest = Color(0xFF1A2B2C),
    surfaceContainerLow = Color(0xFF253D3A),
    surfaceContainer = Color(0xFF2E4A47),
    surfaceContainerHigh = Color(0xFF385552),
    surfaceContainerHighest = Color(0xFF415E5A),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E4A47),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A9BB5),
    onPrimaryContainer = Color(0xFF1A2B2C),
    secondary = Color(0xFF1B5E7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE6F0),
    onSecondaryContainer = Color(0xFF1A2B2C),
    tertiary = Color(0xFF5A9BB5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDE6F0),
    onTertiaryContainer = Color(0xFF1A2B2C),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFCDE6F0),
    onBackground = Color(0xFF1A2B2C),
    surface = Color.White,
    onSurface = Color(0xFF1A2B2C),
    surfaceVariant = Color(0xFFD6E8F0),
    onSurfaceVariant = Color(0xFF2E4A47),
    outline = Color(0xFF6E8380),
    outlineVariant = Color(0xFFBFCFCB),
    shadow = Color.Black,
    scrim = Color.Black,
    inverseSurface = Color(0xFF1A2B2C),
    inverseOnSurface = Color(0xFFCDE6F0),
    inversePrimary = Color(0xFF5A9BB5),
    surfaceDim = Color(0xFFBFCFCB),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1FAFF),
    surfaceContainer = Color(0xFFE8F0F5),
    surfaceContainerHigh = Color(0xFFDCE3E8),
    surfaceContainerHighest = Color(0xFFD0D8DC),
)

@Composable
fun AudibleCloneTheme(
    darkTheme: Boolean = true, // Default to dark for audiobook app
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}