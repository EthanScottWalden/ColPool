package io.github.ethanscottwalden.colpool.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.github.ethanscottwalden.colpool.config.EmulatorConfig

/**
 * Manual DI: holds the Firebase singletons. Created once in [io.github.ethanscottwalden.colpool.ColPoolApp]
 * after FirebaseApp is initialised and emulators are configured.
 * ViewModels are built with viewModelFactory { initializer { } } from this container.
 */
class AppContainer {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(EmulatorConfig.FUNCTIONS_REGION) }
}
