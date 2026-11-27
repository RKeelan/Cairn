// Generates the watch app's Baseline Profile on a connected watch (see README.md, "Baseline Profiles"). It ships nothing of its own.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.rkeelan.cairn.baselineprofile.wear"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":wear"

    // The module runs in a process of its own, so the generator is told which app to start.
    productFlavors {
        named("dev") {
            testInstrumentationRunnerArguments["targetAppId"] = "com.rkeelan.cairn.dev"
        }
    }
}

baselineProfile {
    useConnectedDevices = true
}

// The flavours differ only in their package name and server, not in their code, so the dev flavour, which signs in to the local Supabase stack,
// generates the profile for both.
androidComponents {
    beforeVariants(selector().withFlavor("environment" to "prod")) { it.enable = false }
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
