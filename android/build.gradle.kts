// Plugins are declared here and applied in :app. AGP 9 has built-in Kotlin,
// so the kotlin-android plugin is not applied (see docs/adr/0001-android-setup.md).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.roborazzi) apply false
}
