package io.github.ethanscottwalden.colpool.di

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.github.ethanscottwalden.colpool.BuildConfig
import io.github.ethanscottwalden.colpool.config.EmulatorConfig
import io.github.ethanscottwalden.colpool.data.places.PlacesAutocomplete
import io.github.ethanscottwalden.colpool.data.places.PlacesSdkAutocomplete

/**
 * Manual DI: holds the Firebase singletons. Created once in [io.github.ethanscottwalden.colpool.ColPoolApp]
 * after FirebaseApp is initialised and emulators are configured.
 * ViewModels are built with viewModelFactory { initializer { } } from this container.
 */
class AppContainer(private val appContext: Context) {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(EmulatorConfig.FUNCTIONS_REGION) }

    // Places is initialised once, on first use, with the key from the Secrets plugin (placeholder in CI).
    // A bad or placeholder key makes calls fail; the address sheet shows its error state.
    private val placesClient: PlacesClient by lazy {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(appContext, BuildConfig.PLACES_API_KEY)
        Places.createClient(appContext)
    }
    val places: PlacesAutocomplete by lazy { PlacesSdkAutocomplete { placesClient } }
}
