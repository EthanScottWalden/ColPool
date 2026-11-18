package io.github.ethanscottwalden.colpool

import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorPlaceholderKeyTest {
    // Same shape check Firebase Installations applies before any request.
    @Test fun placeholderKeyHasAValidApiKeyShape() {
        assertTrue(Regex("\\AA[\\w-]{38}\\z").matches(ColPoolApp.EMULATOR_PLACEHOLDER_API_KEY))
    }
}
