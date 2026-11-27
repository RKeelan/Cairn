// The link between the phone app and the watch app: the list as the watch shows it, the ticks the watch sends back, and the Wearable Data Layer
// that carries both. It also holds the timer that times taps in both apps. Both apps depend on it; the watch app depends on nothing else of
// Cairn's.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.rkeelan.cairn.link"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.core)
    implementation(libs.androidx.wear.remote.interactions)

    testImplementation(libs.junit)
}
