package io.github.ethanscottwalden.colpool.ui.address

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.data.places.AddressSuggestion
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of the address search sheet states. Fake addresses only. Goldens: app/src/test/screenshots/address_search_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi", application = Application::class)
class AddressSearchScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val suggestions = listOf(
        AddressSuggestion("p1", "100 Example Street", "Exampleville, CA"),
        AddressSuggestion("p2", "200 Sample Avenue", "Exampleville, CA"),
        AddressSuggestion("p3", "300 Test Road", "Exampleville, CA"),
    )

    private fun shoot(name: String, state: AddressSearchState, notice: (@Composable () -> Unit)? = null) {
        composeRule.setContent {
            ColPoolTheme {
                Surface {
                    AddressSearchContent("Starting address", state, {}, {}, {}, {}, notice = notice)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/address_search_$name.png")
    }

    @Test fun empty() = shoot("empty", AddressSearchState())

    @Test fun suggestions() = shoot("suggestions", AddressSearchState(query = "100 Exam", suggestions = suggestions))

    @Test
    fun selected() = shoot(
        "selected",
        AddressSearchState(
            query = "100 Example Street, Exampleville, CA 90000",
            selected = SelectedAddress("100 Example Street, Exampleville, CA 90000", "p1", 12.5, -34.25),
        ),
    )

    @Test fun error() = shoot("error", AddressSearchState(query = "100 Exam", error = AddressSearchError.SearchFailed))

    @Test fun withNotice() = shoot("notice", AddressSearchState(), notice = { Text("Sample notice slot content.") })
}
