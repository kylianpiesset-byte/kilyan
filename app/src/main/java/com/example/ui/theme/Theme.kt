package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StrikeOpsColorScheme = darkColorScheme(
    primary = StrikeAmber,
    onPrimary = ObsidianVoid,
    primaryContainer = StrikeAmberDark,
    onPrimaryContainer = TextPrimary,
    secondary = CyberCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = CyberCyan,
    tertiary = SquadGreen,
    onTertiary = ObsidianVoid,
    error = AlertCrimson,
    onError = TextPrimary,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextMuted,
    outline = TacticalBorder,
)

@Composable
fun StrikeOpsTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StrikeOpsColorScheme,
        typography = Typography,
        content = content,
    )
}
