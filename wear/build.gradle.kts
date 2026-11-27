// The watch app: the list, and a tap to tick an item off or back on. It gets the list from the phone app, through :link, and talks to nothing
// else.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.rkeelan.cairn.wear"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // The phone app has the same application ID, which the Wearable Data Layer requires of the two ends of a link.
        applicationId = "com.rkeelan.cairn"
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    productFlavors {
        named("dev") {
            applicationIdSuffix = ".dev"
        }
    }

    buildFeatures {
        compose = true
    }
}

// The Baseline Profile that :baselineprofile:wear generates, kept in src/main so that both flavours ship it. Release builds carry it, and Android
// compiles the code it lists ahead of time.
baselineProfile {
    mergeIntoMain = true
    // Generating needs a device, so a build never does it on its own.
    automaticGenerationDuringBuild = false
    // The flavours share their code, so the dev flavour, which signs in to the local Supabase stack, generates the profile for both.
    variants {
        create("devRelease") {
            from(project(":baselineprofile:wear"))
        }
    }
}

dependencies {
    implementation(project(":link"))
    implementation(libs.kotlinx.coroutines.android)
    // Installs the profile on first launch when the app is installed without Play or a Gradle install to do it.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
