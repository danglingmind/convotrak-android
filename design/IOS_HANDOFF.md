# ConvoTrack iOS redesign: handoff

Goal: restyle the **iOS app** (`/Users/ricky/Workspace/convoy`, SwiftUI) so it matches the
redesigned **Android app** (`/Users/ricky/Workspace/convotrack-android`, Compose) in screen layout,
component set, colour, type voice and copy, while following Apple's Human Interface Guidelines for
everything platform-native (navigation, sheets, alerts, controls, haptics, Dynamic Type).

Snapshot date: 2026-10-18. Android design state: spec v2.4.0 (Oct 11), build 1.2.0 (versionCode 8).

> Paste this whole file into the new session as the first message, or tell the session to read
> `/Users/ricky/Workspace/convotrack-android/design/IOS_HANDOFF.md`.

---

## 0. Sources of truth (read in this order)

| What | Path | Status |
|---|---|---|
| Full design spec (tokens, 100+ components, every screen, copy, icon map, interaction) | `/Users/ricky/Workspace/convotrack-android/design/convotrack_design_system.json` | **Current (v2.4.0).** Authoritative. Already written iOS-first: units are pt, it has an `ios` column in `iconography.mapping` and `interaction.haptics`. Skip the `androidSubstitutions` and Android notes. |
| Working Compose implementation | `/Users/ricky/Workspace/convotrack-android/app/src/main/java/com/danglingmind/convotrack/ui/designsystem/` (`Tokens.kt`, `Buttons.kt`, `Cards.kt`, `Chrome.kt`, `Feedback.kt`, `Inputs.kt`) and `.../ui/theme/Theme.kt` | Current. Read it when the JSON is ambiguous. |
| Android screens (layout reference) | `.../ui/home/HomeScreen.kt`, `ui/history/RideHistoryScreen.kt`, `ui/profile/ProfileScreen.kt`, `ui/ride/RideLobbyScreen.kt`, `ui/ride/CreateRideScreen.kt`, `ui/ride/EditRideScreen.kt`, `ui/ride/RouteForm.kt`, `ui/ride/RideNavigationScreen.kt`, `ui/ride/NavigationHuds.kt`, `ui/ride/RegroupSheet.kt`, `ui/ride/RideSummaryScreen.kt`, `ui/membership/MembershipScreen.kt`, `ui/join/JoinRideSheet.kt`, `ui/scan/QRScannerScreen.kt`, `ui/auth/*`, `ui/splash/SplashScreen.kt`, `ui/common/RideRows.kt` | Current |
| Hero photos | `/Users/ricky/Workspace/convotrack-android/design/assets/` (`hero_riders_sunset_mirrored.jpg` for Home, `hero_road_sunset.webp` for backdrops) | Bundle into `Assets.xcassets` |
| `design/style-guide.html` and its published artifact | n/a | **Stale** (Oct 10, still ice-cyan). Do not use. |

When this file and the JSON disagree, the JSON wins. When the JSON and the running Android app
disagree, the running app wins; flag it to the user.

Do not edit anything in the Android repo during this work.

---

## 1. The look in one paragraph ("Dark Glass Ride UI")

Dark-only. A blue-black base (`#060C12`) with a photo at the top of every top-level screen that fades
into it. There is no navigation bar: round glass buttons float over the photo. One large **serif**
headline per screen, set in white. Section labels are small, wide-tracked, uppercase. All data (distances,
codes, status badges, speeds) is monospaced. Cards are quiet dark glass with hairline borders. **One lime
accent (`#CDEF1E`)** marks the single next action (one filled button per screen), live state, and the
route line. Status is never colour alone: every state also has a word.

Design principles from the spec (keep):
1. Photography first: every top-level screen opens on a photo that fades into the base.
2. Chrome floats over the photo: circular glass buttons, no app bar.
3. One serif headline per screen; everything else is clean sans.
4. Wide-tracked uppercase for labels and eyebrows; mono for data, codes and badges.
5. Accent is for the next action, live state and the route. Status colours stay solid and readable.
6. Cards are dark, low-contrast glass with hairline borders. No heavy shadows except the accent glow on primary actions.
7. Status is never colour alone.

---

## 2. What the current iOS app looks like (what changes)

`convotrack/ConvoTrackTheme.swift` is a Material-3-style neutral-grey palette (`#131313` surfaces,
accent `caf300`), system fonts, mono labels, a `GlassCard` and `LimePrimaryButton` modifier, and a
`Norwester` custom font. The tab bar is the custom `ConvoTrackBottomNav` (FLAGGED / TRACK / PROFILE),
hidden while `AppState.isRideActive`. There is a **first-run onboarding flow** (`Onboarding/`) that
Android does not have yet.

Differences to close:
- Backgrounds: grey `#131313` family becomes blue-black `#060C12` / `#02070B`.
- Accent: `#CAF300` becomes `#CDEF1E` (and its derived tint/border/glow/soft/deep values, see `tokens.color.accent`).
- Photographic hero headers with a fade on Home, History, Lobby, Create/Edit Ride, Auth and Summary.
- Serif headline (hero titles), wide-tracked uppercase section labels, mono data.
- Floating circular glass buttons instead of a nav bar.
- Tabs renamed: **History · Home · Profile** (was FLAGGED / TRACK / PROFILE).
- Rows and cards: ride rows with destination thumbnails, active-ride card on a photo, roster rows with solid status badges.

---

## 3. iOS adaptation rules (HIG wins on platform behaviour)

The target is iOS 26.2 (project setting), so Liquid Glass is available.

| Topic | Android | iOS decision |
|---|---|---|
| Fonts | Inter / Source Serif 4 / Roboto Mono, bundled | **System fonts, not bundled.** UI = SF Pro (`.system`), display = a serif: use `.system(design: .serif)` (New York) at the spec size/weight, mono = `.system(design: .monospaced)` (SF Mono). Use Dynamic Type: map each `typography.styles.*` to a text style with `@ScaledMetric` or `.font(.system(size:…, relativeTo:))`. Keep the tracking (`letterSpacingPt`) via `.tracking()`. Use `.monospacedDigit()` for live numbers. Drop the custom `Norwester` font unless it is used for the logo. |
| Glass / blur | RenderEffect or opaque fallback | Use `.glassEffect()` / `GlassEffectContainer` for the floating circular buttons, pills and the tab bar. Use `.ultraThinMaterial` or a plain `surface.card` fill with a hairline border for cards. Do not fake blur with images. |
| Icons | Material Symbols Rounded | **SF Symbols**, via `iconography.mapping[*].ios` in the JSON. Filled variant when active/selected. Default 22pt, `.text.secondary`. |
| Haptics | HapticFeedbackConstants | `interaction.haptics.*.ios`: primary action = medium impact, selection = light/`.selection`, destructive = warning, incoming alert = `.notification(.warning)`. |
| Navigation | Single Activity + custom stack | Keep `NavigationStack` per tab. **Hide the system navigation bar** on photo screens (`.toolbar(.hidden, for: .navigationBar)`) and draw the floating round buttons yourself, but keep the interactive swipe-back gesture working (`NavigationBackSwipe.swift` exists for this). Pushed screens (Create/Edit Ride) use the "pushed" header: back button, centred uppercase title + subtitle, connection button. |
| Tab bar | Floating 60pt pill, active tab = raised accent circle | **Recommended default:** a custom floating glass bar of the same shape (inset 20, height 60, radius 28, three items History / Home / Profile, active item in an accent circle with a filled dark icon, inactive items muted with a label). It must still expose tab-bar accessibility traits. **Alternative if the user prefers more native:** the system iOS 26 `TabView` (already a floating glass bar) with `.tint(accent)`. Ask the user once before building this; do not decide silently. |
| Sheets | Custom bottom sheet | Native `.sheet` with `.presentationDetents`, `.presentationDragIndicator(.visible)`, `.presentationBackground(surface.sheet)`, top radius 24. Join Ride, Regroup, Remove Rider, QR, Plan. |
| Alerts / dialogs | Custom `ConfirmDialog` | Native `.alert` / `.confirmationDialog`. Use the **exact copy** from `screenSpecs.dialogs` (ConfirmDialog copy "stays exactly as in the app today"). Destructive actions use `role: .destructive`. |
| Buttons | 56pt PrimaryButton | Same visual (56pt, radius 18-20, flat lime, dark label, 800 weight uppercase, leading dark glyph). Respect the 44pt minimum tap target everywhere (48 in navigation HUD). |
| Segmented control | Custom | Native `Picker(.segmented)` restyled or a small custom view; selected = accent fill with `onAccent` text. |
| Stepper (max riders) | Custom | Native `Stepper` styling is acceptable; match 2-25 range and plan gating (free 5 / Pro 25, paywall at the cap). |
| Safe areas | Edge-to-edge + insets | Content inset 20pt horizontal; respect top/bottom safe areas; sticky bottom action sits above the home indicator with a 12pt clearance and a 32pt transparent-to-base fade above it. |
| Reduce motion / transparency | ANIMATOR_DURATION_SCALE | Honour `accessibilityReduceMotion` (stop pulse/splash loops) and `accessibilityReduceTransparency` (swap glass for the `fallback` opaque colour each glass token lists). |
| Dark mode | Dark only | Keep `.preferredColorScheme(.dark)` (already set). |
| Payments | Google Play Billing | Keep StoreKit 2 / `MembershipStore`. Replace "Secured by Google Play" with Apple wording ("Secured by the App Store · Cancel anytime"), keep the auto-renewal disclosure built from the store's reported price/period, keep Restore Purchases. |
| Sign-in | Google only (email hidden on Android) | Keep the iOS providers (Apple, Google, the existing Clerk flows). Apple Sign-In button must follow Apple's button guidelines (do not restyle its logo/label colours; pick the white/black style that fits dark). |

---

## 4. Tokens (starting point, copy into one Swift file)

Create `convotrack/DesignSystem/` (suggested) with `Tokens.swift` and one file per component family,
mirroring Android (`Buttons`, `Inputs`, `Cards`, `Chrome`, `Feedback`). Replace or wrap the existing
`ConvoTrackTheme.swift`. Screens must not use raw hex, raw font sizes or raw corner radii.

Full lists are in `tokens.*` in the JSON. The essentials:

**Backgrounds:** base `#060C12`, deep `#02070B`, elevated `#0B131B`, pureBlack `#000000`.
App background = vertical gradient base → base (60%) → deep.

**Surfaces:** card `rgba(20,30,41,0.78)` (fallback `#111A24`), cardQuiet `rgba(14,22,31,0.80)`
(fallback `#0B131C`; roster rows, recent rows, details list), field `rgba(27,38,49,0.92)`, glassOnPhoto
`rgba(22,26,30,0.55)` blur 16, nav `rgba(12,17,21,0.92)` blur 22, sheet `#0B131B`, dialog `#111B26`,
raised `#16212D`, neutralBadge `#2A2F37`, iconDisc `#1A231B`, scrim `rgba(2,7,12,0.56)`,
dangerContainer `#3A1518`.

**Text:** primary `#F5F7FA`, label `#D5D9DF`, secondary `#B4B9C1`, muted `#868C95`, subtle `#5C636D`
(not for body), disabled `#5D6670` (not for body), onAccent `#0C0E02`, onSuccess `#0B2416`, onPhoto `#FFFFFF`.

**Accent (lime):** base `#CDEF1E`, bright `#D4F140`, soft `#AECB1A`, deep `#718310`, tint 12%,
tonal 17%, border 45%, glow 28%. Focus border `#B8D71B`.

**Status:** success `#5EF7A6`, warning `#FFCF70`, danger `#FBB0A9`, dangerStrong `#E5484D`, info `#9DBEFF`
(each has tint/border variants in the JSON). Regroup categories: fuel `#FFB547`, food `#FF8A4C`,
scenic `#5AB8FF`, other `#B58CFF`, emergency = danger.

**Borders:** hairline 10%, subtle 12%, default 18%, strong 28% of `rgba(160,180,200,·)`.

**Radius:** xs 8, sm 12, md 16, lg 20, xl 24, nav 28, pill 999.
**Spacing:** 4-pt grid (4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 48, 56, 64). Screen inset 20, section gap 24, list gap 12, field gap 8.
**Border widths:** hairline 0.5, default 1, emphasis 1.5, focus 2.
**Shadows:** accentGlow `rgba(205,239,30,0.28)` y6 blur 28 on primary actions only; floating `rgba(0,0,0,0.30)` y12 blur 32; textOnPhoto for text over photos.
**Motion:** quick 120ms, standard 220ms, emphasized 320ms, spring damping 0.82. Live values update in place without animating.

**Type scale (pt, from `typography.styles`):**
heroTitle serif 40/700, tracking -1 · heroTitleCompact serif 30/700 · eyebrow 11/600, tracking 4.4, caps ·
sectionLabel 14/600, tracking 4.6, caps, `text.label` · sectionAction 12/600, tracking 2.6, caps ·
fieldLabel 12/600, tracking 3, caps · headerTitle 16/600, tracking 3.6, caps · titleLarge 24/700 ·
cardTitle 22/700 · rowTitle 16/600 · body 15/400 · bodySmall 13/400 muted · button 18/800, tracking 1.4, caps ·
buttonWide 14/700, tracking 3, caps · buttonPill 13/700, tracking 1.2, caps · statLabel 11/500 caps ·
statValue 18/600 · metricValue 24/600 · metricDisplay 44/700 · dataMono mono 13/500, tracking 0.6, caps ·
codeValue mono 18/700 · codeDisplay mono 30/700, tracking 6 · otpDisplay mono 26/700, tracking 10 ·
badge mono 12/700, tracking 2, caps · badgeSmall mono 10/700, tracking 1.4 · navLabel 12/500 ·
HUD: hudTurnDistance mono 26/900, hudTurnInstruction 17/600, hudStatValue mono 24/900, hudEta mono 22/900.

Fixed-height components (SummaryMetricCard, NavBottomBar, PlanCard price) must shrink text, never clip digits.

---

## 5. Shared structure of every screen

- **Background:** app-background gradient. Top-level screens add a hero photo + `photoFade` gradient
  (transparent → 25% → 80% → base). Heights: Home 300pt, Lobby 340pt, Create/Edit Ride and History 150pt
  (road photo at 45% opacity).
- **Header:** no bar. Floating 44pt circular glass buttons at status-bar + 8pt. Back top-leading;
  actions top-trailing. `ConnectionStatusButton` (antenna icon, accent when live) appears on Home,
  History, Create/Edit Ride.
- **Section heading:** `SectionHeading` = uppercase tracked label (`text.label`) with an optional
  trailing: action (`SEE ALL >`), live (`● RIDING`, accent), outlined pill (`VIEW ON MAP ↗`), or square icon buttons.
- **Sticky bottom action:** one `PrimaryButton` (56pt), with a fade above it. Content clearance = tab bar (60) + 8 + 24.
- **Max content width:** 560pt (iPad / large phones).

---

## 6. Screens (map to existing iOS files)

Legend: Android screen → iOS file to restyle. Exact copy, sizes and sections are in
`screenSpecs.<id>` in the JSON.

| # | Screen | iOS file | Layout summary |
|---|---|---|---|
| 1 | **Splash** | `SplashView.swift` | Base + accent aura. Logo 160pt at 15% from top on a breathing glow; route-line trace in the middle; `CONVOTRACK` wordmark (accent) 20% from bottom; `MULTIPLAYER NAVIGATION` eyebrow; cycling stage text with live dot (INITIALIZING GPS… / SYNCING SQUAD… / CACHING MAPS… / PREPARING HUD…); footer text. Stop loops under Reduce Motion. |
| 2 | **Auth** | `AuthView.swift` | Road-sunset photo + fade. Top half: logo 100pt, wordmark, `RIDE TOGETHER IN REAL TIME`. Bottom half: sign-in buttons (keep Apple + Google), error line, `TERMS · PRIVACY` links, status pills (SERVER ONLINE with pulsing dot, SSL ENCRYPTED). |
| 3 | **Verify email** | part of `AuthView`/Clerk flow | Pushed header "VERIFY EMAIL". 88pt accent-tint disc + email icon, "Check your email" (compact serif), 6-digit mono code input, VERIFY button. |
| 4 | **Home** | `HomeView.swift` (+ `MainTabView.swift`) | Riders-sunset hero 300pt. Top: logo badge (leading), connection button (trailing). Hero text left: eyebrow `RIDE TOGETHER`, serif "Discover new roads", subtitle. `CREATE RIDE` (accent) / `JOIN RIDE` (glass) pair, 46pt, 10pt gap. `ACTIVE RIDE` heading + live status (`● IN LOBBY` / `● RIDING`) and `ActiveRideCard` (190pt, destination photo, scrim, destination pill, avatar stack, title, distance/time meta chips, RESUME pill) when a ride is in progress. `RECENT RIDES` heading + `SEE ALL >` (switches to History) and up to 5 `RideRowCard`s. Empty state when no rides. |
| 5 | **History** | `RideHistoryView.swift` | Road photo header 150pt with serif "Ride history"; connection button. Totals strip of 3 stat tiles (DISTANCE · LAST n / RIDES SHOWN / AVG SPEED); list of `RideRowCard`. |
| 6 | **Profile** | `ProfileView.swift` | Centered avatar (xxl, accent ring), name (titleLarge), subtitle `CONVOTRACK PRO · N RIDES` / `FREE PLAN · N RIDES`. 3 stat tiles (RIDES / TOTAL KM / RIDES LED). Pro promo banner. Settings group (Notifications, Membership & Billing, Privacy Policy, Terms of Service). Account group (Sign Out, Delete Account, destructive). Delete-account dialog. |
| 7 | **Membership / paywall** | `MembershipView.swift` | Gradient + accent aura. Close top-right. 72pt accent disc + premium icon, "ConvoTrack Pro", "Your rides. No limits." 2x2 `BenefitTile` grid (25 RIDERS / UNLIMITED / ANALYTICS / 365 DAYS). `PlanCard` radio list (selected = accent border), never show placeholder prices. CTA `GET PRO · {price}/{period}`. Trust line, auto-renewal disclosure, Restore · Terms · Privacy. Success state: 88pt success disc, "You're in", `LET'S RIDE`. **Keep the 3-day free trial lead from the latest iOS paywall change**; the spec predates it. |
| 8 | **Join Ride sheet** | `JoinRideView.swift` | Sheet. 150pt hero with dot-grid: standby ("SYSTEM: STANDBY", "Ready to Connect") → verified (accent border, "CODE VERIFIED", ride title, leader + `n/max`). `JOIN RIDE` button under hero when verified. `ENTER INVITE CODE` with 6-char mono code input, `OR` divider, `SCAN QR CODE` target. |
| 9 | **QR scanner** | `QRScannerView.swift` | Black camera, accent corner brackets on a 260pt square, 56% scrim outside, glass pill hint "Point at a ConvoTrack QR code". Camera-permission request / denied states per spec. |
| 10 | **Create Ride** | `CreateRideView.swift` | Same as Edit Ride with title `NEW RIDE`, no delete, primary `CREATE RIDE` with loading state. |
| 11 | **Edit Ride** | `EditRideView.swift` | Pushed header (back, `EDIT RIDE` + "Plan your route", connection). `RIDE TITLE` field card. `ROUTE` timeline (rail with green start marker, accent-deep stop markers, accent destination marker): start `LocationCard`, stop search fields with remove, `ADD STOP`, destination `LocationCard`. `ROUTE PREVIEW` map card (+ `VIEW ON MAP`), alternative-route chips, 2 metric cards (EST. TIME / DISTANCE), max-riders stepper, sticky `SAVE RIDE`, leader-only destructive `DELETE RIDE`. No tab bar. |
| 12 | **Ride Lobby** | `RideLobbyView.swift` | Destination photo hero 340pt (else route map), floating back / edit / more buttons, serif ride title + status line at bottom of photo, photo attribution pill. `RIDE DETAILS` list (incl. Joining code row with copy). `SQUAD ROSTER` heading with `● LIVE`, ready tally (SOLO / ALL READY / n/m READY), link + QR square buttons. `RosterRow`s (leader first, accent bar, larger avatar). Sticky primary: leader = `START RIDE` / `WAITING FOR RIDERS`; rider = `MARK READY` → `YOU'RE READY` (secondary); active = `RESUME NAVIGATION`. Tab bar stays visible under the sticky action (user decision). |
| 13 | **Remove rider sheet** | `RiderDetailDrawer.swift` | Centered avatar, name, role, destructive `REMOVE FROM RIDE`, `CANCEL`. |
| 14 | **Ride Navigation** | `RideNavigationView.swift` (+ `Map/`) | Full-bleed dark map. Preview state: back + destination pill, bottom preview bar. Active portrait: turn instruction card (accent maneuver tile), leaderboard strip, banners (emergency / split / regroup / reconnecting), off-route chip, off-screen rider edge chips, bottom bar with mono stats (speed / ETA / distance) + regroup + end buttons. Landscape: 1/3-width glass panel on the trailing side. Tab bar hidden here. Keep-screen-on. |
| 15 | **Regroup sheet** | in `RideNavigationView`/`CoordinationOverlayView` | Sheet: "Regroup", `MEET POINT` segmented (Current / 100 m / 500 m), 2x2 reason tiles (fuel / food / scenic / other; emergency = danger) that broadcast on tap with no confirm, `CANCEL REQUEST` ghost button. |
| 16 | **Ride Summary** | `RideSummaryView.swift` | Pushed header "RIDE SUMMARY", no back. Destination photo hero (else road photo): `RIDE COMPLETE` accent eyebrow + serif title. 2x2 metrics (TOTAL DISTANCE [accent, primary], DURATION, AVG SPEED, MAX GROUP SPLIT [alert if > 2 km]). `AWARDS` card. `SHARE SUMMARY` secondary; sticky `DONE`. |
| 17 | **Share card** | `RideSummaryShareCard.swift` | 1080×1920 poster: map hero 820px with fade, wordmark, 190px accent distance, title, meta line, 3 metrics, up to 3 awards, QR bottom-right. Fixed pixel layout, independent of Dynamic Type. |
| 18 | **Onboarding** | `Onboarding/*` (iOS-only) | Not in Android. Restyle to the same tokens (base background, serif headlines, tracked labels, lime primary button, glass cards). Keep its behaviour, videos and paywall. Check consistency with Splash and Auth. |

Not wired / leave unless asked: `CoordinationOverlayView` standalone overlay (only reuse its regroup bits).

---

## 7. Components to build first (SwiftUI names suggested)

Mirror Android names so the two codebases stay greppable. Details for each are in `components.<Name>`.

**Chrome:** `FloatingButton` (44pt circle, glass, 22pt icon, variants accentIcon / onDark), `ConnectionStatusButton`,
`LogoBadge`, `ScreenHeader` (pushed variant), `BottomNavFloating`, `SectionHeading`, `HeroTitleBlock`,
`GlassPill`, `GlassCard`, `Divider` / `OrDivider`.

**Buttons:** `PrimaryButton` (56pt, flat lime, glow, leading glyph, loading/disabled), `SecondaryButton`,
`DestructiveButton`, `GhostButton`, `PillButton` / `TonalPill` (RESUME, UPGRADE…), `TextButton`, `SquareIconButton`,
`ActionButtonPair`.

**Inputs:** `TextFieldCard`, `PlaceSearchField`, `CodeInput` (invite / email OTP), `LocationCard`,
`AddStopButton`, `RouteTimeline`, `Stepper`, `SegmentedControl`, `RouteOptionChip`.

**Cards / rows:** `RideRowCard`, `ActiveRideCard`, `RosterRow`, `AwardRow`, `StatTile`, `StatPill`, `MetricCard`,
`SummaryMetricCard`, `PlanCard`, `BenefitTile`, `PromoBanner`, `SettingsGroup`, `ReasonTile`, `RouteMapCard`,
`DetailsList`, `HeroMedia`.

**Atoms / feedback:** `Avatar` + `AvatarStack` (initials on a colour from `tokens.color.avatar.palette`
picked by rider id, presence dot), `StatusBadge` (READY = lime / WAITING = neutral / RIDING = green, solid fills),
`LeaderBadge`, `Chip`, `MetaChip`, `LiveIndicator`, `StatusPill`, `EmptyState`, `ErrorState`, `LoadingState`, `InlineError`.

**Navigation HUD:** `TurnInstructionCard`, `LeaderboardStrip`, `NavBottomBar`, `BarStat`, `Banner`, `OffRouteChip`,
`RegroupArrivalControl`, `ResumePill`, `EdgeChip`, `DestinationPill`, `NavigationPreviewBar`.

**Map:** dark Google style (park `#17271F`, roads `#252E33` / `#38434A`, water `#0B1418`, labels `#8D969C`),
route core `#CDEF1E` with glow, widths 5/9pt in preview and 8/13pt in navigation, start marker green `#5EF7A6`,
destination marker lime, rider markers ringed lime. Full style in `map.*`. The iOS app already uses Google Maps
(`Map/GoogleMapView.swift`), so apply the style JSON there.

---

## 8. Copy and vocabulary (do not invent labels)

Use only `appVocabulary` from the JSON:
- Ride status: `IN LOBBY`, `RIDING`.
- Roster badge: `READY`, `WAITING`, `RIDING` (leader never shows WAITING). Roles: `RIDE LEADER`, `FORMATION RIDER`.
- Ready tally: `SOLO`, `ALL READY`, `n/m READY`. Live indicator: `LIVE`.
- Ride-row tags: `LEADER` badge and `RESUME` pill only.
- Not used: INVITED, "Last seen N min ago", MEMBER, LIVE NOW, ACTIVE as a tag, LEADER chip in the roster.
- Dialog copy: exactly as `screenSpecs.dialogs`. Keep existing iOS strings where the spec says "as in the app today", and keep `Localizable.xcstrings` entries in sync.

---

## 9. Accessibility (required, not optional)

- Min tap target 44pt (48pt in navigation HUD). Every icon-only button needs an accessibility label.
- Contrast: `text.subtle` and `text.disabled` fail AA for body text; use them only for separators, disabled controls or large text.
- Dynamic Type everywhere; fixed-height components shrink digits (min 26pt for metricDisplay) rather than clip.
- Status never colour-only. HUD banners are announced (emergency assertive, others polite).
- Provide a text summary of the route for the map (start, stops, destination, distance, ETA).
- Respect Reduce Motion and Reduce Transparency.

---

## 10. Suggested plan for the new session

1. **Read** this file, the JSON (`tokens`, `layout`, `components`, `screenSpecs`, `iconography`, `interaction`, `appVocabulary`), the current iOS `ConvoTrackTheme.swift`, `MainTabView.swift`, `HomeView.swift`.
2. **Ask the user once**, before coding: (a) custom floating tab bar vs native iOS 26 `TabView`; (b) serif face, New York (system) or a bundled one; (c) whether to restyle onboarding in this pass.
3. **Foundation:** `DesignSystem/Tokens.swift` (colours, gradients, type, spacing, radius, shadows, motion), add hero images to `Assets.xcassets`, then atoms and buttons.
4. **Components** by family (section 7), each with a `#Preview`.
5. **Screens** in this order: Home → History → Profile → Edit/Create Ride → Lobby → Join sheet → Summary → Membership → Auth/Splash/Onboarding → Navigation HUD + map style → Share card.
6. After each screen: build with
   `xcodebuild -project convotrack.xcodeproj -scheme convotrack -destination 'platform=iOS Simulator,name=iPhone 17' build`,
   run in the simulator, screenshot, and compare side by side with the Android screen. Check Dynamic Type at the largest size, Reduce Motion, and small phone (SE-size) layout.
7. Keep behaviour untouched. This is a visual and structural restyle: do not change networking, socket, ride-lifecycle or StoreKit logic (`Networking/`, `Services/`).
8. Do not commit or push without asking the user.

Out of scope: backend, Android changes, new features (nearby rides / join requests / notifications plan lives in the Android repo `docs/plans` and is a separate effort).

---

## 11. Known gaps and judgement calls to flag to the user

- The Android spec was "inferred from reference screens"; some values (exact radii, glass opacities) should be validated against real iOS screenshots.
- The raised-accent-circle tab treatment is not a standard iOS pattern; see the tab-bar decision in section 3.
- Android hides the email sign-in path; iOS keeps its providers.
- The serif font choice is open (spec names Source Serif 4 on Android, Georgia as the nominal).
- Placeholder: the Android "destination photo per ride" uses Google Places photos with attribution; iOS already has `GooglePlacePhotoService.swift`, so reuse it for thumbnails, hero and the active-ride card, with the attribution pill.
- iOS has onboarding videos and an onboarding paywall that Android lacks; the spec does not cover them.
