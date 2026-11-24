package io.github.ethanscottwalden.colpool.di

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import io.github.ethanscottwalden.colpool.BuildConfig
import io.github.ethanscottwalden.colpool.config.EmulatorConfig
import io.github.ethanscottwalden.colpool.data.places.PlacesAutocomplete
import io.github.ethanscottwalden.colpool.data.places.PlacesSdkAutocomplete
import io.github.ethanscottwalden.colpool.data.search.FirebaseSearchBackend
import io.github.ethanscottwalden.colpool.data.search.SearchBackend
import io.github.ethanscottwalden.colpool.data.series.FirebaseSeriesBackend
import io.github.ethanscottwalden.colpool.data.series.SeriesBackend
import io.github.ethanscottwalden.colpool.data.session.AuthBackend
import io.github.ethanscottwalden.colpool.data.session.FirebaseAuthBackend
import io.github.ethanscottwalden.colpool.data.session.FirebaseOnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.OnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.FirebaseProfileBackend
import io.github.ethanscottwalden.colpool.data.session.ProfileBackend

/**
 * Manual DI: holds the Firebase singletons. Created once in [io.github.ethanscottwalden.colpool.ColPoolApp]
 * after FirebaseApp is initialised and emulators are configured.
 * ViewModels are built with viewModelFactory { initializer { } } from this container.
 */
class AppContainer(private val appContext: Context) {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    // Memory-only cache: nothing is persisted to disk (ADR 0004). Set before first use and before useEmulator.
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().also {
            it.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
    val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(EmulatorConfig.FUNCTIONS_REGION) }

    // Places is initialised once, on first use, with the key from the Secrets plugin (placeholder in CI).
    // A bad or placeholder key makes calls fail; the address sheet shows its error state.
    private val placesClient: PlacesClient by lazy {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(appContext, BuildConfig.PLACES_API_KEY)
        Places.createClient(appContext)
    }
    val places: PlacesAutocomplete by lazy { PlacesSdkAutocomplete { placesClient } }

    val authBackend: AuthBackend by lazy { FirebaseAuthBackend(auth, firestore, functions) }
    val onboardingBackend: OnboardingBackend by lazy { FirebaseOnboardingBackend(auth, firestore) }
    val profileBackend: ProfileBackend by lazy { FirebaseProfileBackend(auth, firestore) }
    val searchBackend: SearchBackend by lazy { FirebaseSearchBackend(firestore) { auth.currentUser?.uid } }
    val seriesBackend: SeriesBackend by lazy { FirebaseSeriesBackend(firestore, functions) { auth.currentUser?.uid } }
}
