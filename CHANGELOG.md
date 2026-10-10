# Changelog

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
