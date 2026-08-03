# PlanRoute (Android)

Native Android port of `packages/client`, sharing the same backend
(`packages/server`) and third-party services (Nominatim, OSRM, Digitraffic).

## Opening the project

Open this `android/` folder directly in Android Studio (not the repo
root) — it's an independent Gradle project living alongside the pnpm
workspace, not part of it.

## Current state

This is a structural scaffold, not a feature-complete app:

- `MainActivity.kt` lays out the intended screen shell — a full-screen
  map area behind a draggable bottom sheet holding the route planner
  form — using local Compose state only. No network calls, location
  updates, or voice output are wired up yet.
- The map itself is a placeholder `Surface`; swap in MapLibre GL Android
  (or osmdroid) pointed at the same OSM tile source the web client uses
  (`OSM_TILE_LAYER_URL` in `packages/client/src/constants/map.constants.ts`).

## Follow-up work

See the requirements list already worked out for this port:

1. Wire a Retrofit/OkHttp client to `packages/server`'s REST API plus
   Nominatim/OSRM/Digitraffic directly, mirroring `packages/shared`'s
   DTOs as Kotlin data classes.
2. Replace `MapPlaceholder` with a real map (markers, polyline, draggable
   via-stop markers, POI/construction-zone popups).
3. `FusedLocationProviderClient` for GPS, `SensorManager` (rotation
   vector) for the compass.
4. `TextToSpeech` for turn-by-turn voice, restricted to en/fi/sv voices
   the same way `speech.service.ts` does.
5. A foreground service to keep location + voice alive during
   navigation with the screen locked (needs `ACCESS_BACKGROUND_LOCATION`,
   `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and
   `POST_NOTIFICATIONS` — intentionally left out of the manifest until
   this is built, see the comment there).
6. An Android App Link handling the same "share route" JSON payload the
   web client encodes into a URL.
