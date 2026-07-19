package com.zookie.simpleschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF35675C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8DCCF),
    onPrimaryContainer = Color(0xFF08201B),
    secondary = Color(0xFF53645E),
    surface = Color(0xFFF8FAF8),
    surfaceVariant = Color(0xFFE1E8E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCDBE),
    onPrimary = Color(0xFF07372F),
    primaryContainer = Color(0xFF1C4F45),
    onPrimaryContainer = Color(0xFFB8DCCF),
    secondary = Color(0xFFB9CAC3),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF3F4945),
)

@Composable
fun JianChengTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

