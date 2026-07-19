# ConvoTrack — Android

Native Android port of the ConvoTrack iOS app (SwiftUI). Built with **Kotlin + Jetpack Compose + Material 3**.

This repository currently contains the **UI shell**: all core screens ported for
look/feel parity, driven by hardcoded data. Backend integration (Clerk auth,
Socket.IO, Google Maps, REST) is intentionally deferred — see *Roadmap* below.

## Requirements

- **Android Studio** (Ladybug or newer). It ships its own JDK — use that to build,
  not a system JDK (this project targets JDK 17; a very new system JDK like 25 can
  break the Gradle daemon).
- Android SDK Platform **35**, minSdk **26**.

## Build & Run

Open the project folder in Android Studio and press Run, or from the CLI (with a
configured Android SDK + a compatible JDK):

```
./gradlew :app:assembleDebug     # build the APK
./gradlew installDebug           # install on a connected device/emulator
```

> Set the SDK location via `local.properties` (`sdk.dir=/Users/<you>/Library/Android/sdk`)
> or the `ANDROID_HOME` env var — Android Studio writes this automatically on first open.

## Architecture

Single-activity Compose app. Navigation via `androidx.navigation:navigation-compose`.

```
MainActivity → ConvoTrackTheme → AppRoot (NavHost)
  splash            SplashScreen        (animated route line, 3.5s → auth)
  auth              AuthScreen          (Google / Apple buttons, 4-color Google G)
  main              MainScreen          (bottom nav host: FLAGGED / TRACK / PROFILE)
      FLAGGED         RideHistoryScreen
      TRACK           HomeScreen        (create/join, active-ride card, recent rides)
      PROFILE         ProfileScreen
      + JoinRideSheet (ModalBottomSheet)
  create_ride       CreateRideScreen
  ride_lobby        RideLobbyScreen     (invite code, roster, ready states)
  ride_navigation   RideNavigationScreen(map HUD stat pills, rider strip)
  ride_summary      RideSummaryScreen   (metrics + awards)
```

### Theme system (`ui/theme/`)
1:1 port of the iOS `ConvoTrackTheme.swift`. Dark-only, Material You-inspired.
- `Color.kt` — the full palette as `object CT` (lime `#CAF300` accent, surface scale, etc.)
- `Type.kt` — the typography scale as `object CTType` (displayMetrics, headlineLg/Md, bodyLg/Md, labelCaps mono, dataMono mono).
- `Theme.kt` — `ConvoTrackTheme` composable wiring the palette into a Material 3 `darkColorScheme` + edge-to-edge system bars.
- `Components.kt` — reusable pieces: `LimePrimaryButton`, `Modifier.glassCard()`, `ConvoTrackTopBar`, `FloatingStatPill`, `ConvoTrackBottomNav`.

### iOS → Android dependency map (for the backend phase)
| iOS | Android |
|---|---|
| SwiftUI | Jetpack Compose |
| ClerkKit | Clerk Android SDK (`clerk-android`) — same Clerk project/keys |
| SocketIO | `socket.io-client-java` — same events, `auth.token` handshake |
| GoogleMaps | Maps SDK for Android + `maps-compose` |
| CoreLocation | `FusedLocationProviderClient` + foreground service |
| StoreKit | Google Play Billing |
| Vision/VisionKit (QR) | ML Kit / CameraX barcode |

The **backend contract is reusable as-is** — same REST endpoints and Socket.IO events.

## Roadmap (post-shell)
1. Data layer — port `Models.swift`, `APIClient` (Retrofit/Ktor), `SocketClient`, Clerk auth.
2. Wire live data into Home / Lobby / Navigation / Summary.
3. Google Maps: replace map placeholders with `maps-compose` + route polylines.
4. Location foreground service for background tracking.
5. Google Play Billing for membership.
6. QR scan (CameraX + ML Kit) for JoinRide.
