# Cairn

[![CI](https://github.com/RKeelan/Cairn/actions/workflows/ci.yml/badge.svg)](https://github.com/RKeelan/Cairn/actions/workflows/ci.yml)

Cairn is a shopping list for Android phones and Wear OS watches, to which AI agents can add items.

A household shares one list, which a [Supabase](https://supabase.com) project holds. People sign in with Google. A list is shared by adding a member's email address, and an account can start a list of its own only if the project's owner has invited it.

The apps never write to a table. They call four functions, which `supabase/migrations` defines along with the tables: `my_lists`, `sync`, `add_member` and `remove_member`. `docs/data-model.md` describes the tables and their fields.

## Layout

* `:wear` is the Wear OS app, written in Kotlin with Compose for Wear OS.
* `supabase/` is the Supabase project: its configuration, its migrations, and the seed data of the local stack.

## Commands

The build needs JDK 17 or later and the Android SDK, found through `ANDROID_HOME` or a `local.properties` file with `sdk.dir`. The Supabase CLI needs [bun](https://bun.sh) and Docker.

- `./gradlew check` lints with ktlint and Android Lint, runs the unit tests, and fails if they cover less than 75% of the measured code
- `./gradlew ktlintFormat` fixes formatting
- `./gradlew :wear:testDebugUnitTest` runs the unit tests
- `./gradlew :wear:koverHtmlReportDebug` writes a coverage report to `wear/build/reports/kover/htmlDebug`
- `./gradlew :wear:installDebug` installs the watch app on the connected device or emulator

## The local Supabase stack

```sh
bun install            # Installs the Supabase CLI, at the version package.json pins
bunx supabase start    # Starts the stack in Docker and applies the migrations and the seed data
bunx supabase db reset # Rebuilds the database from the migrations and the seed data, losing what was in it
bunx supabase stop
```

The stack listens on port 54321. `supabase/seed.sql` invites two made-up accounts to start a list.

## A hosted Supabase project

* Create a project, then apply the migrations to it with `bunx supabase link` and `bunx supabase db push`.
* In Google Cloud, create an OAuth client of the web application type. In the Supabase dashboard, enable the Google provider under Authentication and give it the web client's ID.
* Invite the accounts that may start a list, in the dashboard's SQL editor: `insert into public.invited (email) values ('someone@example.com');`

Supabase pauses a project on its free plan after a week without activity. A request a day from anywhere keeps one awake.
