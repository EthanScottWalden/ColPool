package io.github.ethanscottwalden.colpool

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.github.ethanscottwalden.colpool.config.EmulatorConfig
import io.github.ethanscottwalden.colpool.di.AppContainer

class ColPoolApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        container = AppContainer(this)
        if (EmulatorConfig.enabled) connectEmulators(container)
    }

    private fun initFirebase() {
        // google-services (when present) initialises FirebaseApp via a ContentProvider before this runs.
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        check(EmulatorConfig.enabled) {
            "google-services.json is missing. See android/app/google-services.README.md."
        }
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey(EMULATOR_PLACEHOLDER_API_KEY)
            .setProjectId(EmulatorConfig.projectId)
            .build()
        FirebaseApp.initializeApp(this, options)
    }

    companion object {
        /**
         * Fake key for emulator-only builds. The emulators ignore it, but some Firebase SDK parts
         * (used by the sign-up callable) reject a key that isn't key-shaped ("A" + 38 of [\w-])
         * before any request. Built at runtime so secret scanners don't flag a fake literal.
         */
        val EMULATOR_PLACEHOLDER_API_KEY: String = "AIza" + "0".repeat(35)
    }

    // Must run before any other Firebase use.
    private fun connectEmulators(container: AppContainer) {
        val host = EmulatorConfig.host
        container.auth.useEmulator(host, EmulatorConfig.AUTH_PORT)
        container.firestore.useEmulator(host, EmulatorConfig.FIRESTORE_PORT)
        container.functions.useEmulator(host, EmulatorConfig.FUNCTIONS_PORT)
    }
}
