# Cairn

[![CI](https://github.com/RKeelan/Cairn/actions/workflows/ci.yml/badge.svg)](https://github.com/RKeelan/Cairn/actions/workflows/ci.yml)

Cairn is a shopping list for Android phones and Wear OS watches, to which AI agents can add items.

## Modules

* `:wear` is the Wear OS app, written in Kotlin with Compose for Wear OS.

## Commands

The build needs JDK 17 or later and the Android SDK, found through `ANDROID_HOME` or a `local.properties` file with `sdk.dir`.

- `./gradlew check` lints with ktlint and Android Lint, runs the unit tests, and fails if they cover less than 75% of the measured code
- `./gradlew ktlintFormat` fixes formatting
- `./gradlew :wear:testDebugUnitTest` runs the unit tests
- `./gradlew :wear:koverHtmlReportDebug` writes a coverage report to `wear/build/reports/kover/htmlDebug`
- `./gradlew :wear:installDebug` installs the watch app on the connected device or emulator
