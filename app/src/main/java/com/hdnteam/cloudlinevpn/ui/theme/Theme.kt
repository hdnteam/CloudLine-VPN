package com.hdnteam.cloudlinevpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// CloudLine VPN - Dark Cloud Theme
val CloudDarkBlue = Color(0xFF0A0E1A)
val CloudMidBlue = Color(0xFF111827)
val CloudSurface = Color(0xFF1A2235)
val CloudCard = Color(0xFF1E2D45)
val CloudAccent = Color(0xFF4D9FEC)
val CloudAccentBright = Color(0xFF60AFFF)
val CloudGreen = Color(0xFF00E676)
val CloudRed = Color(0xFFFF5252)
val CloudOrange = Color(0xFFFFAB40)
val CloudGray = Color(0xFF8899AA)
val CloudWhite = Color(0xFFE8EFF8)
val CloudGradientStart = Color(0xFF0D1B2A)
val CloudGradientEnd = Color(0xFF1A3A5C)

private val DarkColorScheme = darkColorScheme(
    primary = CloudAccent,
    onPrimary = Color.White,
    primaryContainer = CloudCard,
    onPrimaryContainer = CloudWhite,
    secondary = CloudAccentBright,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1B3A5E),
    onSecondaryContainer = CloudWhite,
    tertiary = CloudGreen,
    background = CloudDarkBlue,
    onBackground = CloudWhite,
    surface = CloudSurface,
    onSurface = CloudWhite,
    surfaceVariant = CloudCard,
    onSurfaceVariant = Color(0xFFB0C4D8),
    outline = Color(0xFF2A4060),
    error = CloudRed,
    onError = Color.White
)

@Composable
fun CloudLineVPNTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
