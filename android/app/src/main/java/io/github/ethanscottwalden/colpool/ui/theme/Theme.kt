package io.github.ethanscottwalden.colpool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF2A211A)
private val Amber = Color(0xFFFFC73D)
private val Cream = Color(0xFFFFF8E4)

private val ColPoolColors = lightColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Ink,
    onSecondary = Cream,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1E6C8),
    onSurfaceVariant = Color(0xFF5A4F43),
    outline = Ink,
    secondaryContainer = Amber,
    onSecondaryContainer = Ink,
)

/** Light-only theme loosely following the prototype's palette. Fuller brand theming comes with later screens. */
@Composable
fun ColPoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColPoolColors, content = content)
}
