import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

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

// Windows only: start the Firebase emulators in WSL before a debug build (Android Studio Run).
// Does nothing on Linux, in CI (env CI set), with colpool.useEmulators=false or
// -Pcolpool.autoStartEmulators=false. Docs: DEVELOPMENT.md "Run the app from Android Studio (Windows)".
abstract class EnsureFirebaseEmulators : DefaultTask() {
    @get:Input abstract val wslCheckout: Property<String>
    @get:Internal abstract val launcherDir: DirectoryProperty

    private fun accepts(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 500); true }
    } catch (_: IOException) {
        false
    }

    /** True once the last seeded account (dev.staff) can sign in: the seed has finished. */
    private fun seeded(): Boolean = try {
        val url = URI("http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo").toURL()
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 1500
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use {
                it.write("""{"email":"dev.staff@calbaptist.edu","password":"demo-pass-123","returnSecureToken":true}""".toByteArray())
            }
            conn.responseCode == 200
        } finally {
            conn.disconnect()
        }
    } catch (_: IOException) {
        false
    }

    @TaskAction
    fun run() {
        if (accepts("localhost", 9099)) {
            logger.lifecycle("Firebase emulators already running (localhost:9099).")
            return
        }
        val checkout = wslCheckout.get()
        if (!Regex("^~?[A-Za-z0-9_./-]+$").matches(checkout)) {
            throw GradleException("colpool.wslCheckout='$checkout' is not allowed: use letters, digits, _ . / - only (no spaces), e.g. ~/ColPool.")
        }
        val wsl = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/wsl.exe")
        if (!wsl.exists()) {
            throw GradleException("wsl.exe was not found at $wsl. Install WSL2, or start the emulators yourself (cd $checkout/functions && npm run emulators:dev in WSL) and rebuild with -Pcolpool.autoStartEmulators=false.")
        }
        val bashPath = if (checkout.startsWith("~")) "\$HOME" + checkout.substring(1) else checkout
        val inWsl = "source ~/.nvm/nvm.sh >/dev/null 2>&1; cd \\\"$bashPath/functions\\\" && npm run emulators:dev; " +
            "echo; echo Emulators stopped. Press Enter to close this window.; read"
        val dir = launcherDir.get().asFile.also { it.mkdirs() }
        val launcher = File(dir, "start-emulators.cmd")
        launcher.writeText("@echo off\r\ntitle ColPool emulators\r\n\"${wsl.absolutePath}\" -e bash -lc \"$inWsl\"\r\n")
        logger.lifecycle("Starting the Firebase emulators in WSL (a minimized console window opens; close it to stop them)...")
        ProcessBuilder("cmd.exe", "/c", "start", "ColPool emulators", "/min", launcher.absolutePath)
            .redirectErrorStream(true)
            .start()
        val deadline = System.nanoTime() + 240_000_000_000L
        while (System.nanoTime() < deadline) {
            if (seeded()) {
                logger.lifecycle("Firebase emulators are ready and seeded.")
                return
            }
            Thread.sleep(2000)
        }
        throw GradleException(
            "The Firebase emulators did not become ready within 240 seconds. In WSL run: " +
                "cd $checkout/functions && npm run emulators:dev and read the output (or the minimized 'ColPool emulators' window). " +
                "To skip auto-start: -Pcolpool.autoStartEmulators=false."
        )
    }
}

val autoStartEmulators = gradleProp("colpool.autoStartEmulators", "true").toBoolean()
val wslCheckoutPath = gradleProp("colpool.wslCheckout", "~/ColPool")
val ensureFirebaseEmulators = tasks.register<EnsureFirebaseEmulators>("ensureFirebaseEmulators") {
    description = "Windows only: starts the Firebase emulators in WSL (and waits for the seed) before debug builds."
    wslCheckout.set(wslCheckoutPath)
    launcherDir.set(layout.buildDirectory.dir("emulators"))
    // Evaluated at configuration time; the values are plain booleans, so this is configuration-cache safe.
    val active = System.getProperty("os.name").startsWith("Windows", ignoreCase = true) &&
        useEmulators && autoStartEmulators && System.getenv("CI") == null
    onlyIf { active }
}
// Run (assembleDebug + deploy) and CLI installDebug only; test tasks (testDebugUnitTest, verifyRoborazziDebug) never start emulators.
tasks.matching { it.name == "assembleDebug" || it.name == "installDebug" }.configureEach { dependsOn(ensureFirebaseEmulators) }

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
    // Real key: <repo root>/local.properties (uncommitted). CI/fresh clone: placeholder.
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    robolectricAndroidAllJar("org.robolectric:android-all-instrumented:16-robolectric-13921718-i7")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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
