package io.github.ethanscottwalden.colpool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Default Material 3 theme for now. Brand theming comes with the screens. */
@Composable
fun ColPoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
