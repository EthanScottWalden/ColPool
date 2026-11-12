package io.github.ethanscottwalden.colpool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.ethanscottwalden.colpool.ui.placeholder.PlaceholderScreen
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme

/** Single Activity. Navigation (navigation-compose) is reserved and not wired yet. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColPoolTheme {
                PlaceholderScreen()
            }
        }
    }
}
