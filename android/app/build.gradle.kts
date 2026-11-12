plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin: kotlin-android is intentionally not applied.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.secrets)
    alias(libs.plugins.roborazzi)
}

// Apply google-services only when the owner has downloaded the file (never committed).
// Without it, debug + emulators initialise FirebaseApp manually (ColPoolApp).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

fun gradleProp(name: String, default: String): String =
    (project.findProperty(name) as String?) ?: default

val useEmulators = gradleProp("colpool.useEmulators", "true").toBoolean()
val emulatorHost = gradleProp("colpool.emulatorHost", "10.0.2.2")
val emulatorProjectId = gradleProp("colpool.emulatorProjectId", "demo-colpool")

// The android-all jar Robolectric 4.17 needs for SDK 36 (part of Robolectric, not a new library).
val robolectricAndroidAllJar = configurations.create("robolectricAndroidAllJar")
val robolectricDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricAndroidAll = tasks.register<Copy>("robolectricAndroidAll") {
    from(robolectricAndroidAllJar)
    into(robolectricDir)
}

android {
    namespace = "io.github.ethanscottwalden.colpool"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ethanscottwalden.colpool"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_EMULATORS", useEmulators.toString())
            buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
            buildConfigField("String", "EMULATOR_PROJECT_ID", "\"$emulatorProjectId\"")
        }
        release {
            // Release can never point at emulators.
            buildConfigField("boolean", "USE_EMULATORS", "false")
            buildConfigField("String", "EMULATOR_HOST", "\"\"")
            buildConfigField("String", "EMULATOR_PROJECT_ID", "\"\"")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Honour TMPDIR (the agent sandbox's /tmp is read-only; harmless elsewhere).
            System.getenv("TMPDIR")?.let { dir -> it.jvmArgs("-Djava.io.tmpdir=$dir") }
            // Robolectric runs offline from jars Gradle resolved (its own downloader ignores proxy auth).
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            it.dependsOn(robolectricAndroidAll)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricDir.get().asFile.absolutePath)
        }
    }
}

secrets {
    // Real key: android/local.properties (uncommitted). CI/fresh clone: placeholder.
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    robolectricAndroidAllJar("org.robolectric:android-all-instrumented:16-robolectric-13921718-i7")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)

    // Added but not wired: Places is initialised by #16.
    implementation(libs.places)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
