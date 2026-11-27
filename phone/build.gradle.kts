// The phone app: the list, with adding, editing and amounts, the people it is shared with, and the list it sends to the watch. It alone talks to
// the Supabase project.
import com.github.triplet.gradle.androidpublisher.ReleaseStatus

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.play.publisher)
}

// The hosted Supabase project and the Google sign-in client, from Gradle properties kept outside the repository (see README.md).
// Without them, prod builds get placeholders, so that compiling, unit tests and Lint work anywhere.
val supabaseUrl = providers.gradleProperty("cairn.supabaseUrl").getOrElse("https://placeholder.invalid")
val supabaseKey = providers.gradleProperty("cairn.supabaseKey").getOrElse("placeholder")
val googleClientId = providers.gradleProperty("cairn.googleClientId").getOrElse("")

/** [value] as a Java string literal, for a BuildConfig field. */
fun javaString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.rkeelan.cairn.phone"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // The watch app has the same application ID, which the Wearable Data Layer requires of the two ends of a link.
        applicationId = "com.rkeelan.cairn"
        targetSdk = 37
    }

    buildFeatures {
        compose = true
        // BuildConfig carries each flavour's Supabase project and how its accounts sign in.
        buildConfig = true
    }

    productFlavors {
        named("prod") {
            buildConfigField("String", "SUPABASE_URL", javaString(supabaseUrl))
            buildConfigField("String", "SUPABASE_KEY", javaString(supabaseKey))
            buildConfigField("String", "GOOGLE_CLIENT_ID", javaString(googleClientId))
            buildConfigField("String", "DEV_ACCOUNTS", javaString(""))
            buildConfigField("String", "DEV_PASSWORD", javaString(""))
        }
        named("dev") {
            applicationIdSuffix = ".dev"
            // The local Supabase stack, which the device reaches through `adb reverse tcp:54321 tcp:54321`, and its publishable key,
            // which is the same in every installation of the Supabase CLI.
            buildConfigField("String", "SUPABASE_URL", javaString("http://127.0.0.1:54321"))
            buildConfigField("String", "SUPABASE_KEY", javaString("sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"))
            buildConfigField("String", "GOOGLE_CLIENT_ID", javaString(""))
            // The sign-in screen offers these accounts, which the local stack makes on first use. supabase/seed.sql invites the
            // first to start a list; the second gets one only when the first shares it.
            buildConfigField("String", "DEV_ACCOUNTS", javaString("ada@example.com,ben@example.com"))
            buildConfigField("String", "DEV_PASSWORD", javaString("cairn-dev-password"))
        }
    }

    // Only prod is published. dev has its own application ID, which Play does not know, and it is the flavour measured.
    playConfigs {
        register("dev") {
            enabled = false
        }
    }
}

// Publishing prod to Google Play's internal testing track, which scripts/publish.ps1 runs (see README.md). Each release rolls out to the
// track's testers at once. The service account's JSON key is the file named by `-Pcairn.playCredentials=<path>`, relative to the repository's
// root, or else the contents of the ANDROID_PUBLISHER_CREDENTIALS environment variable. Only the publish tasks read it, so building and
// checking need neither.
play {
    track = "internal"
    releaseStatus = ReleaseStatus.COMPLETED
    defaultToAppBundles = true
    providers.gradleProperty("cairn.playCredentials").orNull?.let { serviceAccountCredentials.set(rootProject.file(it)) }
}

// Room writes each schema version here, to be committed: a migration is written and tested against the exported schemas.
room {
    schemaDirectory("$projectDir/schemas")
}

// The Baseline Profile that :baselineprofile:phone generates, kept in src/main so that both flavours ship it. Release builds carry it, and Android
// compiles the code it lists ahead of time.
baselineProfile {
    mergeIntoMain = true
    // Generating needs a device, so a build never does it on its own.
    automaticGenerationDuringBuild = false
    // The flavours share their code, so the dev flavour, which signs in to the local Supabase stack, generates the profile for both.
    variants {
        create("devRelease") {
            from(project(":baselineprofile:phone"))
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":link"))
    implementation(libs.kotlinx.coroutines.android)
    // Installs the profile on first launch when the app is installed without Play or a Gradle install to do it.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
