# Cairn

[![CI](https://github.com/RKeelan/Cairn/actions/workflows/ci.yml/badge.svg)](https://github.com/RKeelan/Cairn/actions/workflows/ci.yml)

Cairn is a shopping list for Android phones and Wear OS watches, to which AI agents can add items.

A household shares one list, which a [Supabase](https://supabase.com) project holds. On the phone, people add items, give them amounts, tick them off, and share the list. The watch shows the list and ticks items off, and gets everything through the phone, so it needs no connection of its own.

## How it works

The phone app keeps a copy of the list in a Room database, changes it at once, and exchanges changes with the Supabase project when it can reach it: after each change, when Realtime reports a change made elsewhere, and once a minute while the list is on screen. It keeps working without a connection and catches up when one returns.

The phone sends the watch the list over the Wearable Data Layer whenever it changes. A tick on the watch shows at once, and goes back to the phone as a data item of its own, which waits while the watch is out of the phone's range.

* An item is identified by its name, without regard to case or spacing, so "Milk" added on two phones is one item.
* Where two changes to an item meet, the one made later wins.
* Ticking an item marks it bought. Bought items stay on show after the needed ones, the most recently ticked first, so that a mistaken tick is easy to undo, and they are suggested when a name is typed.
* An item may have a quantity, a unit, both or neither, as in "2 loaves", "500 g", "3" and "a box". Common units, such as grams and cups, are written in one standard form.
* People sign in with Google. A list is shared by adding a member's email address, and an account can start a list of its own only if the project's owner has invited it.

The apps never write to a table. They call four functions, which `supabase/migrations` defines along with the tables: `my_lists`, `sync`, `add_member` and `remove_member`. `docs/data-model.md` describes the tables and their fields.

## Layout

* `:core` is pure Kotlin on the JVM and never depends on Android. It holds the list and its operations, the exchange of changes with the server, and the Supabase client.
* `:link` is an Android library that both apps use: the list as the watch shows it, the ticks the watch sends back, the Data Layer that carries both, and the timer that times taps.
* `:phone` is the phone app, written with Compose and Material 3. Its `ui` package holds the screens, and the rest holds storage, sync, sign-in and the view model.
* `:wear` is the Wear OS app, written with Compose for Wear OS: one screen, a list whose rows follow the round edge of the screen, where a tap ticks an item off or puts it back. Its `ui` package holds the screen, and the rest the state behind it.
* `:baselineprofile:phone` and `:baselineprofile:wear` generate each app's Baseline Profile on a device (see "Baseline Profiles"). Neither ships in an app.
* `supabase/` is the Supabase project: its configuration, its migrations, and the seed data of the local stack.

Each app has two flavours. `prod` is the app in daily use, against a hosted Supabase project. `dev`, `com.rkeelan.cairn.dev`, installs alongside it and uses a Supabase stack running on the PC. The phone app and the watch app share an application ID, which the Data Layer requires.

## Commands

The build needs JDK 17 or later and the Android SDK, found through `ANDROID_HOME` or a `local.properties` file with `sdk.dir`. The Supabase CLI needs [bun](https://bun.sh) and Docker.

- `./gradlew check` lints with ktlint and Android Lint, runs the unit tests, and fails if they cover less than 75% of the measured code in any module
- `./gradlew ktlintFormat` fixes formatting
- `./gradlew :core:integrationTest` runs the Supabase client against the local stack, which must be running
- `./gradlew :core:koverHtmlReport` and, for an Android module, `./gradlew :phone:koverHtmlReportDevDebug` write coverage reports under each module's `build/reports/kover`
- `./gradlew :phone:installDevDebug` and `./gradlew :wear:installDevDebug` install the dev flavour on the device `ANDROID_SERIAL` names
- `./gradlew :phone:installDevRelease` and `./gradlew :wear:installDevRelease` install the dev flavour's release builds

## Signing

Every build of both apps, debug and release in both flavours, is signed with Cairn's one key. The Data Layer links the phone app and the watch app only when the same key signs both, and Android installs a build over another only when they share a key, so a device moves between debug and release builds without an uninstall. The key lives in a keystore. Gradle reads the keystore's location and passwords from the first of these that exists:

* `keystore.properties` at the repository's root, which Git ignores. It serves that one checkout.
* `~/.android/cairn-keystore.properties`, which every checkout and worktree on the PC shares, so a worktree signs with Cairn's key without anything copied into it.

Without either, builds are signed with the SDK's debug key, which is how CI builds them.

The keystore and its properties file are kept in `~/.android`, outside any checkout. To create the keystore once, with the JDK's `keytool`:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $HOME\.android\cairn.jks -alias cairn -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=<name>"
```

Then write `~/.android/cairn-keystore.properties`. A PKCS12 keystore, keytool's default, uses its password for the key too. `storeFile` is absolute, with forward slashes, since a backslash is an escape in a properties file:

```properties
storeFile=C:/Users/<user>/.android/cairn.jks
storePassword=<the keystore's password>
keyAlias=cairn
keyPassword=<the keystore's password>
```

A relative `storeFile` is relative to the repository's root. On another PC, copy the keystore into `~/.android` and write the properties file again.

Android refuses to install a build over one signed with a different key, so a device holding builds signed with the debug key, from before the PC had Cairn's key, needs them uninstalled once: `adb uninstall com.rkeelan.cairn.dev` (or `com.rkeelan.cairn`) on the phone and the watch. Uninstalling deletes the phone app's copy of the list and its sign-in; the list itself is kept in Supabase.

## Measure speed

A tap should show its result within 100 ms, and scrolling should keep up with the display's frame rate, on a Pixel 10 Pro XL and a Pixel Watch 4 (AGENTS.md, "Speed"). A debug build runs slower than a release build, so measure a release build against those figures. The commands below measure the dev flavour on the device `ANDROID_SERIAL` names; the phone app and the watch app share its package name, `com.rkeelan.cairn.dev`.

Measure only compiled code. Each app ships a Baseline Profile (see "Baseline Profiles" below), and `installDevRelease` installs it beside the APK, so Android compiles the code it lists at install. An APK installed with `adb install` runs on the JIT until the device's background compilation gets to it, which makes it slower: the watch's scrolling once measured 8% janky uncompiled and under 1% compiled. To compile such an install at once:

```sh
adb shell cmd package compile -m speed-profile -f com.rkeelan.cairn.dev
adb shell dumpsys package dexopt | grep -A2 com.rkeelan.cairn.dev   # Shows status=speed-profile once compiled
```

### Taps

Each app times a tap from the finger lifting to the commit of the frame that shows its result, and logs it under the tag `Cairn`:

```
I Cairn   : interaction tick 12.3 ms
```

The phone app times ticking an item off or back on (`tick`), adding an item with the add button or a suggestion (`add`), and going back to the list from the members or settings screen (`back`). The watch app times ticking an item off or back on (`tick`). The timer, `InteractionTimer` in `:link`, sees the touches the activity's window dispatches, so a tap in a dialog or a menu, which are windows of their own, a long press, which acts while the finger is down, and a key such as the keyboard's Done, log `untimed` in place of a time.

Watch the log while tapping, by hand or with `adb shell input tap <x> <y>`, 20 times or more, and take the median and the 90th percentile of the times:

```sh
adb logcat -s Cairn
```

### Scrolling

Android counts the frames each app draws, and how long each took. Reset the counts, scroll, then read them:

```sh
adb shell dumpsys gfxinfo com.rkeelan.cairn.dev reset
# Scroll the list: by hand, with the crown on the watch, or with adb shell input swipe <x> <y1> <x> <y2> <milliseconds>
adb shell dumpsys gfxinfo com.rkeelan.cairn.dev
```

The report gives the number of frames rendered, the janky frames among them, which missed the display's refresh, and the 50th, 90th, 95th and 99th percentile times to draw a frame. Scrolling keeps up when there are no janky frames, and the percentiles stay under the display's refresh interval: 8.3 ms at 120 Hz, 16.7 ms at 60 Hz. Reset the counts before each measurement, since they cover everything drawn since the last reset, and leave the screen still apart from the scroll, since a tap or an animation adds its own frames. After starting the app, wait a few seconds before the reset, so that the frames drawn while it starts are left out.

## Baseline Profiles

A Baseline Profile lists the classes and methods an app runs as it starts and in its common paths, so that Android compiles them ahead of time rather than leaving them to the JIT. Each app's profile is in its `src/main/generated/baselineProfiles`, and release builds of both flavours carry it. `:baselineprofile:phone` and `:baselineprofile:wear` generate them, by running each app's dev flavour on a device: starting it, scrolling the list, and ticking an item off and back on.

Generate them again after a change to what the apps run, such as a new screen or an upgrade of Compose. Each needs a device running Android 13 or later, and an app with a list of more items than fill the screen:

* For the phone app, start the local Supabase stack and forward its port, install the build the generator uses with `./gradlew :phone:installDevNonMinifiedRelease`, and sign in.
* For the watch app, pair the watch with that phone, so that it holds the list.

Then, with `ANDROID_SERIAL` naming the phone or the watch:

```sh
./gradlew :phone:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
./gradlew :wear:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

Without the property, the run uninstalls the app when it ends, which signs the phone app out and deletes its copy of the list.

## Publishing

The phone and the watch get the prod flavour from Google Play's internal testing tracks, which offer each release to the track's testers as soon as it is rolled out, without review. Play keeps the watch app's releases apart from the phone app's, on the tracks of its Wear OS form factor, but both apps are one Play app, `com.rkeelan.cairn`. Play re-signs what it delivers with Cairn's key through Play App Signing, and the same key is the upload key, so a copy installed with adb and one installed from Play are the same app and update each other without losing data.

Cairn's version is Major.Minor.Revision, set as `cairn.version` in `version.properties` at the repository's root, and both apps take it. It is kept out of `gradle.properties`, which keys CI's Gradle cache, so that a new version neither invalidates the cache nor runs CI on `main` to save a new one. The phone app's version code is major × 1,000,000 + minor × 1,000 + revision, so a newer version always has the greater code; the build refuses a part above 999. Play requires every upload of a Play app to have a version code of its own, whichever form factor it is for, so the watch app's code is the phone app's plus 1,000,000,000.

To publish, run `./scripts/publish.ps1` on the publishing PC, from a clean working tree where only Markdown files at the root (such as `TODO.md`) and `.claude/` may be untracked. It builds the prod flavour's release App Bundle of each app, signed with Cairn's key, and uploads them through the [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) plugin's `:phone:publishProdReleaseBundle` and `:wear:publishProdReleaseBundle` tasks, as completed releases on the `internal` and `wear:internal` tracks. The dev flavour is never published: the build disables publishing for it, and Play knows only `com.rkeelan.cairn`. Measurements use the dev flavour, so no build that is measured reaches Play.

Play accepts each version code only once, so each upload needs a greater `cairn.version` than the last. The script tags each version it publishes `play/<version>` and pushes the tag to origin. Before building anything, it fetches origin's tags and refuses a `cairn.version` that is not above every such tag. It also refuses uncommitted changes and any other untracked file, so each tag names the commit uploaded; missing properties for Cairn's key (see Signing), without which the bundles would be signed with the debug key; and missing `cairn.*` properties for the hosted Supabase project (see "A hosted Supabase project"), without which the phone app could not sign in. The two apps are uploaded one after the other, so if the second fails, the first is on Play at that version, and the next attempt needs a greater version. Should a version reach Play without its tag, Play refuses the upload, and the plugin fails with "Version code is too low or has already been used".

The script uploads as a Google Cloud service account, whose JSON key is attached to the "Google Play Publisher" item in 1Password's Private vault, in its `play-service-account` section. One service account can publish all of a developer account's apps. The script hands the key to Gradle in the `ANDROID_PUBLISHER_CREDENTIALS` environment variable, taking it from the first of these that is present:

* `-Credentials <file>`, a key file, which Gradle reads through `-Pcairn.playCredentials=<file>` instead of the variable.
* `ANDROID_PUBLISHER_CREDENTIALS`, if it is already set.
* `%LOCALAPPDATA%\CairnPublish\play-service-account.dpapi`, a copy of the key encrypted with Windows DPAPI. The script decrypts it into the variable for the Gradle run alone and clears the variable afterwards.
* 1Password: the script runs Gradle under `op run` with the variable set to `op://Private/Google Play Publisher/play-service-account/json`, so 1Password asks for approval and hands the key to Gradle alone.

The DPAPI copy exists so that the script can publish with nobody at the publishing PC to approve a 1Password prompt. DPAPI ties the encryption to the Windows account that saved the copy, on the PC where it was saved, so only processes running as that account on that PC can decrypt the file. To create the file, or refresh it after the key changes in 1Password, run `./scripts/publish.ps1 -SaveCredentials`. It fetches the key through `op run` once, checks that its `client_email` is a service account's address, saves the encrypted copy, and publishes nothing.

The script never writes the decrypted key to disk or prints it, and Gradle runs without a daemon, so the key does not outlive the build. Building and `./gradlew check` need neither the key nor the keystore.

### Setting up Play

This is done once, in Play Console, in Google Cloud, and on the publishing PC.

* In [Play Console](https://play.google.com/console), choose Create app. Name it Cairn, make it an app rather than a game and free, and accept the declarations. Once a bundle has been uploaded, the package name, `com.rkeelan.cairn`, cannot change.
* Under Test and release > Advanced settings, on the Form factors tab, add Wear OS. Play then shows Wear OS's tracks beside the phone's.
* Under Test and release > Testing > Internal testing, for the phone and again for Wear OS, on the Testers tab, select the email list holding the testers' Google accounts, creating it if it does not exist, and save.
* Before uploading any bundle, make Cairn's key the app signing key. Play Console generates a key of its own when the app is created; a release signed with it would not install over a copy installed with adb, and the watch app installed one way would not link to a phone app installed the other. Under Test and release > App integrity, on the App signing tab, choose Change key, then "Export and upload a key from Java keystore". Download `pepk.jar` and the encryption public key it offers into `~\Downloads`, then run PEPK with JDK 17, since a newer JDK fails with "NoSuchAlgorithmException: RSA/NONE/OAEPWithSHA1AndMGF1Padding". Gradle's toolchain JDK 17 is under `~/.gradle/jdks`. The keystore's password is `storePassword` in `~/.android/cairn-keystore.properties`, and the key's password is the same:

  ```powershell
  $java = (Get-ChildItem $HOME\.gradle\jdks -Directory -Filter 'eclipse_adoptium-17-*' | Select-Object -First 1).FullName + '\bin\java.exe'
  $password = ((Get-Content $HOME\.android\cairn-keystore.properties) -match '^storePassword=')[0] -replace '^storePassword=', ''
  & $java -jar $HOME\Downloads\pepk.jar --keystore=$HOME\.android\cairn.jks --alias=cairn --keystore-pass=$password --output=$HOME\Downloads\cairn-signing-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=$HOME\Downloads\encryption_public_key.pem
  ```

  Upload `cairn-signing-key.zip`, then delete it, since it holds the private key, though encrypted. Register no separate upload key, so Cairn's key stays the upload key. Play Console shows the PEPK command itself; if its flags differ from these, use its.
* Under Policy and programs > App content, answer No to Advertising ID: Cairn does not declare the `AD_ID` permission. Play warns about the release until this is answered.
* Make the first release of each app by hand. Build the bundles with `./gradlew :phone:bundleProdRelease :wear:bundleProdRelease` from a clean working tree at the version in `version.properties`. Under Test and release > Testing > Internal testing, create a new release, upload `phone/build/outputs/bundle/prodRelease/phone-prod-release.aab`, and roll it out to internal testing; then do the same on Wear OS's internal testing track with `wear/build/outputs/bundle/prodRelease/wear-prod-release.aab`. The Play Developer API cannot create an app, and creates only draft releases for an app that has never had a release rolled out. Then tag the commit that sets that version with `git tag play/<version>` and push the tag with `git push origin play/<version>`, so that the script asks for a greater version.
* On the Testers tab of each internal testing track, copy the opt-in link, open it on the phone, and accept. Play then installs the watch app on a paired watch and updates both apps whenever a greater version code is rolled out.
* The service account needs a Google Cloud project with the [Google Play Android Developer API](https://console.cloud.google.com/apis/api/androidpublisher.googleapis.com/) enabled, and its key in 1Password. These serve every app of the developer account, so if another app's setup has made them, skip this step. Otherwise, in [Google Cloud](https://console.cloud.google.com/projectcreate), create a project and enable the API in it; Play no longer needs the project linked to the developer account. Under IAM & Admin > Service accounts, create a service account, which needs no roles in the project. On its Keys tab, choose Add key > Create new key > JSON. Attach the file it downloads to a "Google Play Publisher" item in 1Password's Private vault, labelled `play-service-account.json` (which 1Password files as field `json` in section `play-service-account`), then delete the download.
* In Play Console, under Users and permissions, give the service account "Release apps to testing tracks" for Cairn. If the account is already a user there, open it, and under App permissions choose Add app, select Cairn, and save. Otherwise, choose Invite new users, enter its email address, add Cairn under App permissions with that permission, and choose Invite user.
* On the publishing PC, run `./scripts/publish.ps1 -SaveCredentials` to save the DPAPI-encrypted copy of the key.

## The local Supabase stack

```sh
bun install            # Installs the Supabase CLI, at the version package.json pins
bunx supabase start    # Starts the stack in Docker and applies the migrations and the seed data
bunx supabase db reset # Rebuilds the database from the migrations and the seed data, losing what was in it
bunx supabase stop
```

The stack listens on port 54321. A phone or an emulator reaches it at its own loopback address, once adb forwards the port:

```sh
adb reverse tcp:54321 tcp:54321
```

The dev flavour's sign-in screen offers two accounts, which the stack makes on first use. `supabase/seed.sql` invites the first, `ada@example.com`, to start a list. The second, `ben@example.com`, gets a list only when the first shares one with it.

## A hosted Supabase project

The prod flavour needs a Supabase project of your own.

* Create a project, then apply the migrations to it with `bunx supabase link` and `bunx supabase db push`.
* In Google Cloud, create an OAuth client of the web application type, and an Android client for the phone app's package name and signing certificate. In the Supabase dashboard, enable the Google provider under Authentication and give it the web client's ID.
* Invite the accounts that may start a list, in the dashboard's SQL editor: `insert into public.invited (email) values ('someone@example.com');`
* Give the build the project's URL, its publishable key and the web client's ID, as Gradle properties kept outside the repository, such as in `~/.gradle/gradle.properties`:

```properties
cairn.supabaseUrl=https://<project>.supabase.co
cairn.supabaseKey=<the publishable key>
cairn.googleClientId=<the web client's ID>
```

Without those properties the prod flavour still builds, against a placeholder address, and offers no way to sign in.

Supabase pauses a project on its free plan after a week without activity. A request a day from anywhere keeps one awake.
