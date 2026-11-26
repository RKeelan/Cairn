import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // Gives the root project a check task, which runs ktlint over the build scripts in this directory.
    base
    alias(libs.plugins.android.application) apply false
    // AGP compiles Android modules' Kotlin itself; declaring the Kotlin plugin here also pins the Kotlin version AGP uses.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ktlint)
}

// Every dependency is pinned to an exact version, so a range or a snapshot fails the build.
allprojects {
    configurations.configureEach {
        resolutionStrategy {
            failOnDynamicVersions()
            failOnChangingVersions()
        }
    }

    pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            version.set(rootProject.libs.versions.ktlint)
        }
    }
}

// What every module holds to. A module's own build script says only what the module is and what it depends on.
subprojects {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }

    tasks.withType<Test>().configureEach {
        testLogging.exceptionFormat = TestExceptionFormat.FULL
    }

    // Unit tests must cover at least 75% of the lines Kover measures, in the modules that apply it. Left out of the measurement is the
    // Supabase client, which needs a database and has integration tests of its own.
    pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
        extensions.configure<KoverProjectExtension> {
            reports {
                filters {
                    excludes {
                        classes("*.SupabaseServer", "*.SupabaseServer$*")
                    }
                }
                verify {
                    rule {
                        minBound(75)
                    }
                }
            }
        }
    }
}
