plugins {
    // Gives the root project a check task, which runs ktlint over the build scripts in this directory.
    base
    alias(libs.plugins.android.application) apply false
    // AGP compiles Android modules' Kotlin itself; declaring the Kotlin plugin here also pins the Kotlin version AGP uses.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ktlint)
}

ktlint {
    version = libs.versions.ktlint
}

// Every dependency is pinned to an exact version, so a range or a snapshot fails the build.
allprojects {
    configurations.configureEach {
        resolutionStrategy {
            failOnDynamicVersions()
            failOnChangingVersions()
        }
    }
}
