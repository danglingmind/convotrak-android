# Changelog

## 2.0.6 (versionCode 15) — 2026-10-26

### Changed
- When planning a ride, the map now opens centred on you.
- ConvoTrack now measures which of its ads bring new riders to the app, using Meta's app events. This sends Meta your device's advertising ID and a few events (app installs and opens, signing up, creating or joining a ride, and starting a trial or subscription). Your name, email, location, rides and invite codes are never sent. See convotrack.in/privacy.

## 2.0.5 (versionCode 14) — 2026-10-23

### Changed
- Creating and editing a ride now happens on a full-screen map. Search for your destination and the route is drawn straight away, with the other routes shown on the map with how much longer they take. Tap one to switch.
- Add stops along the way, swap start and destination, and drag stops to reorder them.
- Long-press the map to drop a pin and use it as a start, stop or destination, or choose a spot by moving the map.
- Search for fuel, food and viewpoints near the map, or along your route with how many minutes each adds.
- New route options: avoid highways and avoid tolls.
- The route starts from your location by default, and your recent destinations are kept on your phone.
- When editing a ride, Save changes only turns on once something has changed, and going back asks before discarding your edits.
- Delete ride moved into the ⋯ menu.

## 2.0.4 (versionCode 13) — 2026-10-23

### Changed
- If you leave the route and reroute, ConvoTrack now guides you back onto the route your leader chose instead of switching to Google's default route.
- Anonymous usage analytics help us see which parts of the app get used and fix what doesn't work. No locations, names, emails or invite codes are included.

## 2.0.3 (versionCode 12) — 2026-10-22

### Fixed
- Riders no longer show up as "Unknown": your name and photo are now sent to ConvoTrack when you sign in, so other riders see them in lobbies, rosters and live rides.

## 2.0.2 (versionCode 11) — 2026-10-22

### Added
- Several open regroups and emergencies can now show at once, and the caller or leader can dismiss an open regroup.

### Changed
- The online indicator in the top-right corner is gone from every screen.
- The Google photo credit is now a small, quiet line in the corner of the destination photo.
- Spoken prompts pick the best installed English voice.

## 2.0.1 (versionCode 10) — 2026-10-20

### Added
- A gold GO PRO chip with a soft glow, and gold Pro cards and plans screens.

### Changed
- One type family throughout: titles, labels and numbers now use Inter, with clearer durations
  and distances ("16 min", "1h 05m", "5.6 km").
- "Off route. Rerouting." is no longer read aloud; the turn card still shows it.

## 2.0.0 (versionCode 9) — 2026-10-19

### Added
- A 3-day free trial: new accounts get 3 days from sign-in, and everyone gets 3 days once it is
  switched on. A badge on Home and Profile shows how many days are left (TRIAL · 2D, amber on the
  last day). During the trial you can ride up to 3 times with up to 5 riders.
- After the trial, ConvoTrack Pro is needed to create or join rides. Create and Join show a lock
  and open the plans; a ride already in progress can always be finished. Invite links and QR codes
  open the plans first and carry on into the ride once you go Pro.
- A plans screen after your first sign-in, a quiet Pro card on Home, and an occasional reminder
  when you open the app — none of them during a ride.
- Your rider limit is always shown while you plan a ride, the lobby shows how full the pack is,
  and the ride summary offers Pro after a good ride.
- Turn-by-turn voice guidance with "then" hints for quick double turns, a mute button that still
  lets emergencies through, and spoken off-route, regroup and group-split alerts.
- A live ride notification on the lock screen (Android 16 Live Updates), with arrival time.
- The arrival stat shows the clock time you'll arrive, with time left beneath.

### Changed
- The plans screen leads with your free days and states that going Pro bills right away.
- Prices and purchases always use the plan's standard price, never an introductory offer.
- The Pro rider limit and plan terms come from the server, so they always match your plan.
- One alert at a time during navigation, by priority, with a "+N more" chip; regroup no longer
  moves the map.
- The connection indicator is a colour-coded antenna that expands to show its state.
- Photos and avatars load from one shared cache and stay available offline.
- Reduce-motion is respected on the map and in animations; HUD labels have more contrast.

### Fixed
- A back swipe during live guidance no longer drops you out of navigation.
- The convoy gap only shows once both riders have a position.
- Group-split prompts name the rider who's behind and are spoken once per split.
- Ride history thumbnails keep their destination photo offline.

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
