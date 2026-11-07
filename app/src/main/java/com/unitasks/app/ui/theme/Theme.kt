package com.unitasks.app.ui.theme

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
import kotlin.math.abs

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF5F6368),
    secondaryContainer = Color(0xFFE8EAED),
    onSecondaryContainer = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF1F3F4),
    error = Color(0xFFD93025),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFF9AA0A6),
    secondaryContainer = Color(0xFF303134),
    onSecondaryContainer = Color(0xFFE8EAED),
    surface = Color(0xFF1F1F1F),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF28292A),
    error = Color(0xFFF28B82),
)

private val ListAccentPalette = listOf(
    Color(0xFF1A73E8), // Blue
    Color(0xFF188038), // Green
    Color(0xFFE37400), // Amber
    Color(0xFF9334E6), // Purple
    Color(0xFFD01884), // Pink
    Color(0xFF007B83), // Teal
    Color(0xFFC5221F), // Red
)

/** Deterministic accent color for a list badge in the unified view. */
fun listAccentColor(listId: String): Color =
    ListAccentPalette[abs(listId.hashCode()) % ListAccentPalette.size]

@Composable
fun UniTasksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
