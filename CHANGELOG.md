# Changelog

## 1.2.0 (versionCode 8) — 2026-10-11

### Added
- A rider cap that matches your plan: rides hold 5 riders on the free plan and 25 on ConvoTrack
  Pro. Trying to go past 5 now offers the upgrade instead of silently trimming the ride.
- The ride lobby shows the joining code in Ride details, with a button that copies it.
- Ride summary images are now kept on your phone after the first share, so sharing the same ride
  again is instant.

### Changed
- A completely new look across the whole app: photographs behind the main screens, a new
  headline typeface, calmer cards and a floating bottom bar for History, Home and Profile.
- The lobby shows Ride details directly under the photo, above the squad roster.
- Maps everywhere use the new darker styling, and the route is drawn as a lit line.
- The ride summary and the image you share from it were redrawn to match.

### Fixed
- Sharing a ride summary crashed the app. It now opens the share sheet, and a failure shows a
  message instead of closing the app.
- The navigation button beside a ride's destination opened Google Maps on "Choose start
  location" and waited. It now starts turn-by-turn directions straight away.
- The splash screen drew a square glow behind the logo.
- Maps flashed a light background before their tiles loaded.

## 1.1.3 (versionCode 7) — 2026-10-10

### Added
- Sign in with Google now uses the phone's own Google account picker, and falls back to the
  browser only when the picker isn't available.
- The ride lobby shows the ride's name over its hero image and wraps it onto two lines, so long
  names no longer get cut off.

### Changed
- Google is now the only sign-in option, centred on the sign-in screen. Email sign-in is
  switched off with a flag and can be turned back on.
- Signing out clears the remembered Google account, so the next sign-in doesn't pick it
  automatically.
- The top bar's icons now sit at the same right edge on every screen.

### Fixed
- In landscape, the navigation map, heading and direction arrow were a quarter turn off. They
  now follow the screen's rotation.
- The navigation route now follows the road instead of drawing straight lines across bends.
- With three-button navigation, the system navigation bar no longer hides the bottom of the
  Home, History, Profile, lobby, membership, ride-summary and sign-in screens or the bottom
  sheets.
- Closing the Google account picker no longer shows a "sign-in failed" error.
