# CLAUDE.md

Native Android port of `packages/client`. Independent Gradle project — open
`android/` directly in Android Studio, not the repo root; it isn't part of
the pnpm workspace and isn't built by anything at the repo root.

## Commands
- `./gradlew assembleDebug` — Build a debug APK
- `./gradlew test` — Run unit tests
- `./gradlew lint` — Run Android Lint
- No system-wide `java`/`gradle` on this machine — if building from the
  CLI rather than Android Studio, point `JAVA_HOME` at Android Studio's
  bundled JDK first:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`

## Architecture
- `app/src/main/java/com/planroute/app/MainActivity.kt` — the Compose UI
  shell (map placeholder behind a `BottomSheetScaffold` route planner) —
  a structural skeleton, not a feature-complete screen; see `README.md`
  for what's still follow-up work (networking, location, voice, a real
  map).
- `app/src/main/java/com/planroute/app/ui/theme/` — Material 3 theme.
  `Color.kt`'s tokens are carried over from the web client's own palette
  on purpose — same identity, new surface.
- Talks to the same backend as `packages/client`: `packages/server`'s
  REST API, plus Nominatim/OSRM/Digitraffic directly. `packages/shared`'s
  DTOs should be mirrored as Kotlin data classes with identical JSON
  field names when that networking layer is built, so the server doesn't
  need to change for this client to exist.

## Coding Standards
- Kotlin + Jetpack Compose + Material 3 — no XML layouts, no View system
- Package name is `com.planroute.app`; keep new files under it
- No `!!` — handle nullability explicitly rather than asserting past it
- AGP 9+ applies Kotlin automatically — do not add the
  `org.jetbrains.kotlin.android` plugin back (see Gotchas)

## PR Review Rules
1. **Skeleton stays honest** — nothing here talks to the network,
   location, or TTS yet; new code should either genuinely wire up a
   follow-up item from `README.md` or clearly stay a placeholder, not
   pretend to be functional
2. **Palette from `ui/theme/Color.kt`** — reuse those tokens rather than
   introducing new hex values that drift from the web client
3. **Permissions follow features** — don't add a manifest permission
   before the feature that needs it actually lands (see the comment
   block in `AndroidManifest.xml` listing what's deferred and why)

## Critical Gotchas
- `compileSdk`/`targetSdk` must stay at 37+ — the AndroidX versions
  pinned in `gradle/libs.versions.toml` (`core-ktx` 1.19.0,
  `activity-compose` 1.13.0) require it; bumping either further may
  force `compileSdk` up again
- Gradle 9.6.1 + AGP 9.3.1: AGP's built-in Kotlin support means the
  `org.jetbrains.kotlin.android` plugin must **not** be applied — doing
  so fails the build with "plugin is no longer required"
- `local.properties` (the `sdk.dir` path) and everything under `build/`
  are machine-specific and gitignored — don't commit them
