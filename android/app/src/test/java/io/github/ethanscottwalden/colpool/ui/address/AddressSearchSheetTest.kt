package io.github.ethanscottwalden.colpool.ui.address

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** onConfirm behaviour of the sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AddressSearchSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val confirmed = mutableListOf<SelectedAddress>()
    private val title = mutableStateOf("Starting address")

    private fun show(vm: AddressSearchViewModel) {
        rule.setContent {
            ColPoolTheme { AddressSearchSheet(title.value, vm, { confirmed += it }, {}) }
        }
    }

    @Test
    fun onConfirmFiresOnceAfterResolveAndNotOnRecomposition() {
        val vm = AddressSearchViewModel(FakePlaces(), debounceMs = 0)
        show(vm)
        rule.onNodeWithTag(AddressSearchTags.Field).performTextInput("100 Example")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("100 Example Street")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("100 Example Street").performClick()
        rule.waitUntil(5_000) { confirmed.isNotEmpty() }
        assertEquals(1, confirmed.size)
        assertEquals("place-1", confirmed.single().placeId)
        title.value = "Pickup spot" // forces recomposition
        rule.waitForIdle()
        assertEquals(1, confirmed.size)
    }

    @Test
    fun onConfirmNeverFiresWhenAnEditCancelsTheResolve() {
        val gated = GatedPlaces(ignoreCancellation = true)
        val vm = AddressSearchViewModel(gated, debounceMs = 0)
        show(vm)
        rule.onNodeWithTag(AddressSearchTags.Field).performTextInput("100 Example")
        rule.waitUntil(5_000) { vm.state.value.searching }
        gated.complete("100 Example")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("100 Example")).fetchSemanticsNodes().size > 1 }
        rule.onAllNodes(hasText("100 Example"))[1].performClick()
        rule.waitUntil(5_000) { vm.state.value.resolving }
        rule.onNodeWithTag(AddressSearchTags.Field).performTextInput("x")
        gated.completeResolve("id-100 Example")
        rule.waitForIdle()
        assertEquals(0, confirmed.size)
    }
}
