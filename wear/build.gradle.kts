plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.rkeelan.cairn"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
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

// Composables are left out of the measurement, since unit tests don't run them.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable")
                classes("*ComposableSingletons*")
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)

    testImplementation(libs.junit)
}
