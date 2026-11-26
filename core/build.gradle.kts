import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: the list, its exchanges with the server, and the Supabase client, tested on the JVM. It must never depend on Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    // The fake server and the in-memory store, which :core's tests and the Android modules' run against.
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Compile against JDK 17's class library, whichever JDK runs Gradle, so that :core cannot call a newer JDK method that Android lacks.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

/** The tests that run against a local Supabase stack, in integrationTest only (see README.md). */
val integrationTests = "**/*IntegrationTest.class"

tasks.test {
    exclude(integrationTests)
}

tasks.register<Test>("integrationTest") {
    description = "Runs the tests that need the local Supabase stack, which `supabase start` brings up."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include(integrationTests)
    // The stack keeps what a run leaves in it, so a second run is not a repeat of the first.
    outputs.upToDateWhen { false }
}

kover {
    currentProject {
        instrumentation {
            // Coverage counts the unit tests alone. Without this, Kover would also run the integration tests whenever it reports.
            disabledForTestTasks.add("integrationTest")
        }
    }
}

tasks.named("check") {
    dependsOn("koverVerify")
}

dependencies {
    // ListRepository publishes the list as a Flow, so consumers of :core see the coroutines API.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
