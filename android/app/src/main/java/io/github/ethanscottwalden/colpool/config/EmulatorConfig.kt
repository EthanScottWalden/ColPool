package io.github.ethanscottwalden.colpool.config

import io.github.ethanscottwalden.colpool.BuildConfig

/**
 * Emulator Suite wiring. Ports are the Emulator Suite defaults; #12's firebase.json
 * must match them (docs/adr/0001-android-setup.md). Release builds never use emulators.
 */
object EmulatorConfig {
    const val FUNCTIONS_REGION = "us-west2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val FUNCTIONS_PORT = 5001

    val enabled: Boolean get() = BuildConfig.USE_EMULATORS
    val host: String get() = BuildConfig.EMULATOR_HOST
    val projectId: String get() = BuildConfig.EMULATOR_PROJECT_ID
}
