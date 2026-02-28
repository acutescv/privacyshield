package com.privacyshield.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF4CAF82),   // teal-green — privacy/trust
    onPrimary        = Color(0xFF003822),
    primaryContainer = Color(0xFF00522F),
    secondary        = Color(0xFF80CBC4),
    background       = Color(0xFF0E1512),
    surface          = Color(0xFF1A2420),
    surfaceVariant   = Color(0xFF253330),
    onSurface        = Color(0xFFE0F2EE),
    error            = Color(0xFFCF6679)
)

@Composable
fun PrivacyShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography(),
        content     = content
    )
}
