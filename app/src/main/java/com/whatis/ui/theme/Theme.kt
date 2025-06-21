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
    primary = BrightBlue,
    onPrimary = OnPrimaryColors,
    primaryContainer = BrightBlueDark,
    onPrimaryContainer = OnPrimaryColors,
    secondary = SunnyOrange,
    onSecondary = OnPrimaryColors,
    secondaryContainer = SunnyOrangeDark,
    onSecondaryContainer = OnPrimaryColors,
    tertiary = VibrantGreen,
    onTertiary = OnPrimaryColors,
    tertiaryContainer = VibrantGreenDark,
    onTertiaryContainer = OnPrimaryColors,
    error = CoralPink,
    onError = OnPrimaryColors,
    background = Color(0xFF1C1B1F),
    onBackground = OnDarkBackground,
    surface = Color(0xFF1C1B1F),
    onSurface = OnDarkBackground,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    inverseOnSurface = OnLightBackground,
    inverseSurface = LightCream,
    inversePrimary = BrightBlueDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrightBlue,
    onPrimary = OnPrimaryColors,
    primaryContainer = SoftSkyBlue,
    onPrimaryContainer = BrightBlueDark,
    secondary = SunnyOrange,
    onSecondary = OnPrimaryColors,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = SunnyOrangeDark,
    tertiary = VibrantGreen,
    onTertiary = OnPrimaryColors,
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = VibrantGreenDark,
    error = CoralPink,
    onError = OnPrimaryColors,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFFBA1A1A),
    background = LightCream,
    onBackground = OnLightBackground,
    surface = PureWhite,
    onSurface = OnLightBackground,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    inverseOnSurface = OnDarkBackground,
    inverseSurface = Color(0xFF313033),
    inversePrimary = BrightBlue
)

@Composable
fun WhatIsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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