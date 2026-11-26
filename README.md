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
* `:link` is an Android library that both apps use: the list as the watch shows it, the ticks the watch sends back, and the Data Layer that carries both.
* `:phone` is the phone app, written with Compose and Material 3. Its `ui` package holds the screens, and the rest holds storage, sync, sign-in and the view model.
* `:wear` is the Wear OS app, written with Compose for Wear OS: one screen, a list whose rows follow the round edge of the screen, where a tap ticks an item off or puts it back. Its `ui` package holds the screen, and the rest the state behind it.
* `supabase/` is the Supabase project: its configuration, its migrations, and the seed data of the local stack.

Each app has two flavours. `prod` is the app in daily use, against a hosted Supabase project. `dev`, `com.rkeelan.cairn.dev`, installs alongside it and uses a Supabase stack running on the PC. The phone app and the watch app share an application ID, which the Data Layer requires.

## Commands

The build needs JDK 17 or later and the Android SDK, found through `ANDROID_HOME` or a `local.properties` file with `sdk.dir`. The Supabase CLI needs [bun](https://bun.sh) and Docker.

- `./gradlew check` lints with ktlint and Android Lint, runs the unit tests, and fails if they cover less than 75% of the measured code in any module
- `./gradlew ktlintFormat` fixes formatting
- `./gradlew :core:integrationTest` runs the Supabase client against the local stack, which must be running
- `./gradlew :core:koverHtmlReport` and, for an Android module, `./gradlew :phone:koverHtmlReportDevDebug` write coverage reports under each module's `build/reports/kover`
- `./gradlew :phone:installDevDebug` and `./gradlew :wear:installDevDebug` install the dev flavour on the device `ANDROID_SERIAL` names

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
