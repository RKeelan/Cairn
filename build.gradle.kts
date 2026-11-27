import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.util.Properties

plugins {
    // Gives the root project a check task, which runs ktlint over the build scripts in this directory.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    // AGP compiles Android modules' Kotlin itself; declaring the Kotlin plugin here also pins the Kotlin version AGP uses.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.play.publisher) apply false
}

// Robolectric reaches into JDK internals for file descriptors, which JDK 17 and later close to it by default.
val robolectricJvmArgs = listOf("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")

// Cairn's key, from a git-ignored keystore.properties at the repository's root, or else from ~/.android/cairn-keystore.properties, which
// every checkout and worktree on the PC shares (see README.md). Without either, builds are signed with the debug key, so that CI and
// `./gradlew check` build without it.
val keystoreProperties =
    listOf(
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".android/cairn-keystore.properties"),
    ).firstOrNull { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }

// Cairn's version, Major.Minor.Revision, from cairn.version in version.properties (see README.md). The phone app's version code is
// major * 1,000,000 + minor * 1,000 + revision, so a newer version always has the greater code, as Play requires of each upload. Play also
// requires each upload's code to differ from every other's, the other app's included, so the watch app's code adds 1,000,000,000.
val cairnVersion =
    Properties()
        .apply { rootProject.file("version.properties").inputStream().use(::load) }
        .getProperty("cairn.version")
        ?: error("version.properties has no cairn.version")
val phoneVersionCode =
    cairnVersion.split(".").map(String::toIntOrNull).let { parts ->
        val (major, minor, revision) =
            parts.filterNotNull().takeIf { it.size == 3 && parts.size == 3 }
                ?: error("cairn.version must be Major.Minor.Revision, not $cairnVersion")
        require(major in 0..999 && minor in 0..999 && revision in 0..999) {
            "cairn.version $cairnVersion is out of range for a version code: each part must be at most 999"
        }
        major * 1_000_000 + minor * 1_000 + revision
    }
// WATCH_VERSION_CODE_OFFSET in :link, by which the phone app tells whether the watch app is older, must match.
val watchVersionCodeOffset = 1_000_000_000

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

    // Unit tests must cover at least 75% of the lines Kover measures, in the modules that apply it. Left out of the measurement are the
    // screens, in each app's `ui` package, which unit tests don't run; the classes that only connect the app to Android or to the
    // watch; and the Supabase client and Room's generated code, which need a database. Keep logic out of all of them.
    pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
        extensions.configure<KoverProjectExtension> {
            reports {
                filters {
                    excludes {
                        classes(
                            "*.ui.*",
                            "*Activity",
                            "*Activity$*",
                            "*Application",
                            "*Application$*",
                            "*Service",
                            "*Service$*",
                            "*Worker",
                            "*Worker$*",
                            "*.Link",
                            "*.Link$*",
                            "*.SupabaseServer",
                            "*.SupabaseServer$*",
                            "*_Impl",
                            "*_Impl$*",
                            "*.GoogleSignInKt",
                            "*.BuildConfig",
                        )
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

    // Both apps take their version from version.properties. Both sign every build, debug and release in both flavours, with Cairn's one key: the Data Layer links only apps signed with
    // the same key, and Android installs a build over another only when they share a key, so switching builds never needs an uninstall.
    pluginManager.withPlugin("com.android.application") {
        val android = extensions.getByType<ApplicationExtension>()
        android.defaultConfig.versionName = cairnVersion
        android.defaultConfig.versionCode = if (path == ":wear") watchVersionCodeOffset + phoneVersionCode else phoneVersionCode

        val key =
            keystoreProperties?.let { properties ->
                android.signingConfigs.create("cairn") {
                    storeFile = rootProject.file(properties.getProperty("storeFile"))
                    storePassword = properties.getProperty("storePassword")
                    keyAlias = properties.getProperty("keyAlias")
                    keyPassword = properties.getProperty("keyPassword")
                }
            } ?: android.signingConfigs.getByName("debug")
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                variant.signingConfig.from(key)
            }
        }
    }

    listOf("com.android.application", "com.android.library", "com.android.test").forEach { android ->
        pluginManager.withPlugin(android) {
            extensions.configure<CommonExtension> {
                defaultConfig.minSdk = 34

                // prod is the app in daily use, against the hosted Supabase project. dev is for development: it installs alongside prod
                // and uses the local Supabase stack (see README.md).
                flavorDimensions += "environment"
                productFlavors {
                    create("prod") {
                        dimension = "environment"
                    }
                    create("dev") {
                        dimension = "environment"
                    }
                }

                compileOptions.apply {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                testOptions.apply {
                    // Robolectric reads the merged manifest and resources.
                    unitTests.isIncludeAndroidResources = true
                    unitTests.all {
                        it.jvmArgs(robolectricJvmArgs)
                    }
                }

                lint.apply {
                    warningsAsErrors = true
                    abortOnError = true
                    // These fire whenever a newer version is published, which would fail unrelated changes. Dependabot owns upgrades.
                    disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi", "NewerVersionAvailable")
                }
            }

            (extensions.getByName("androidComponents") as AndroidComponentsExtension<*, *, *>).apply {
                beforeVariants { variant ->
                    // Unit tests run in devDebug only, so check runs each suite once.
                    if (variant.name != "devDebug") {
                        (variant as? HasHostTestsBuilder)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)?.enable = false
                    }
                }
            }

            pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
                tasks.named("check") {
                    dependsOn("koverVerifyDevDebug")
                }
            }
        }
    }
}
