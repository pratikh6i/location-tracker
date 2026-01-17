package com.antigravity.locationtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material3 Theme for Antigravity Location Tracker
 * "Fresh, Faint, Silent" aesthetic with high contrast for elderly users
 */

private val LightColorScheme = lightColorScheme(
    primary = MintGreen,
    onPrimary = DeepCharcoal,
    primaryContainer = LightMint,
    onPrimaryContainer = DeepCharcoal,
    
    secondary = PaleBlue,
    onSecondary = DeepCharcoal,
    secondaryContainer = PaleBlue.copy(alpha = 0.3f),
    onSecondaryContainer = DeepCharcoal,
    
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = SuccessGreenLight,
    onTertiaryContainer = DeepCharcoal,
    
    error = MutedRed,
    onError = Color.White,
    errorContainer = MutedRedLight,
    onErrorContainer = DeepCharcoal,
    
    background = SoftWhite,
    onBackground = DeepCharcoal,
    
    surface = Color.White,
    onSurface = DeepCharcoal,
    surfaceVariant = LightMint,
    onSurfaceVariant = WarmGray,
    
    outline = LightGray,
    outlineVariant = LightGray.copy(alpha = 0.5f)
)

@Composable
fun AntigravityTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
