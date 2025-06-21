package com.whatis.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrightBlue80,
    secondary = BrightGreen80,
    tertiary = BrightOrange80,
    background = DarkBackground,
    surface = Color(0xFF2A2A3E),
    onPrimary = Color(0xFF0D1B2A),
    onSecondary = Color(0xFF0D1B2A),
    onTertiary = Color(0xFF0D1B2A),
    onBackground = Color(0xFFF0F0F0),
    onSurface = Color(0xFFF0F0F0),
    primaryContainer = SkyBlue80,
    secondaryContainer = SunnyYellow80,
    onPrimaryContainer = Color(0xFF0D1B2A),
    onSecondaryContainer = Color(0xFF0D1B2A)
)

private val LightColorScheme = lightColorScheme(
    primary = BrightBlue40,
    secondary = BrightGreen40,
    tertiary = BrightOrange40,
    background = LightBackground,
    surface = Color(0xFFFFFFF8),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E),
    primaryContainer = Color(0xFFE3F2FD),
    secondaryContainer = Color(0xFFE8F5E8),
    tertiaryContainer = Color(0xFFFFF3E0),
    onPrimaryContainer = Color(0xFF0D47A1),
    onSecondaryContainer = Color(0xFF1B5E20),
    onTertiaryContainer = Color(0xFFE65100),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF424242)
)

@Composable
fun WhatIsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but we disable it to ensure consistent kid-friendly colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}