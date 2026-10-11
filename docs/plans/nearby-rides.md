# Nearby rides, join requests and notifications — plan

Status: **planned, not started** (2026-10-11). Waiting on the decisions at the end before implementation.

## Goal

- Riders can see live rides near them, open a ride's details (description, destination, distance,
  estimated time, riders/max, leader) and **request to join** rides they weren't invited to.
- The ride's leader gets a **notification** and can **accept or deny**.
- A **notifications page** behind the top-right icon lists every notification with its actions.
- Rides get an optional **description** written by the creator.

## What exists today

- Backend (`convoy-backend`, Fastify + Postgres + Socket.IO on Render):
  - `GET /rides/nearby?lat&lng&radiusKm` (`src/routes/rides.ts:226`, query in `src/db/discoveryRepo.ts`):
    up to 20 rides in LOBBY/ACTIVE/PAUSED whose START waypoint is within radius (default 20, max 50 km).
    Returns id, title, status, destination, distance, **exact start lat/lng**, distance from user, rider
    count, max, leader name/id, **invite code**, viewer role.
  - `users.notify_nearby_rides` is stored but unused. No push infrastructure.
- iOS: a full Nearby Rides section exists in `convoy/convotrack/HomeView.swift`, hidden behind
  `showNearbyRides = false` (old design).
- Android: only `ApiService.getNearbyRides` and the `NearbyRide` model (`data/net/Models.kt`). No UI.

### Security gap found while planning (fix first — Phase 0)

- `POST /rides/:rideId/join` does **not** check the invite code — only ended / already joined / full /
  quota. Anyone who knows a ride id can join it.
- `GET /rides/nearby` hands every signed-in user the ride ids, invite codes and exact start points of all
  live rides nearby. Together: a stranger can join any nearby ride from outside the app today.

## Build

### Backend

1. Schema (new migrations after `013_*`):
   - `rides.description TEXT NULL` (≤ 500 chars), `rides.visibility TEXT NOT NULL DEFAULT 'PRIVATE'`
     (`PRIVATE` | `DISCOVERABLE`).
   - `join_requests`: id, ride_id, requester_id, status (`PENDING` | `ACCEPTED` | `DENIED` | `CANCELLED` |
     `EXPIRED`), created_at, decided_at, decided_by. Unique pending per (ride, requester).
   - `notifications`: id, user_id, type (`JOIN_REQUEST`, `JOIN_ACCEPTED`, `JOIN_DENIED`), ride_id,
     request_id, actor_id, read_at, created_at.
   - `device_tokens`: user_id, platform, token, updated_at.
2. Endpoints:
   - `POST /rides/:id/join` requires the invite code **or** an accepted request (Phase 0).
   - `/rides/nearby`: discoverable rides only; drop invite code; rough start area instead of exact point;
     add description, leader avatar, viewer's request status.
   - `GET /rides/:id/preview` for non-members: title, description, destination, distance, est. time,
     riders/max, leader, status.
   - `POST /rides/:id/join-requests` (send), `DELETE …/join-requests/mine` (cancel),
     `GET /rides/:id/join-requests` (leader), `POST /join-requests/:id/accept` and `/deny` (leader).
     Accept runs the same checks as join (not full, requester's monthly quota).
   - `GET /notifications` (paged), `GET /notifications/unread-count`, `POST /notifications/read`.
   - `POST /devices` / `DELETE /devices/:token` (push token register/unregister).
   - Create / PATCH ride accept `description` and `visibility`.
3. Push: Firebase Cloud Messaging (Android + iOS via APNs). Leader: "X wants to join <ride>".
   Requester: "You're in" / "Request declined". Pending requests expire when the ride ends.

### Android

- Home: "Nearby rides" section. Needs location on Home (reuse `LocationDisclosureDialog`, handle denied).
  Cards: title, destination, distance from you, riders/max, leader, IN LOBBY / RIDING.
- Ride preview screen: destination photo, description, details, leader + rider count. Action:
  **Request to join** / **Requested** (tap to cancel) / **Ride full** / **Open lobby** (already a member).
- Create / Edit Ride: description field + "Show to nearby riders" switch (`RouteForm.kt`).
- Lobby: description in Ride details; leader sees a **Join requests** section with Accept / Deny.
- Notifications: the top-right icon on the History / Home / Profile tabs becomes a **bell** with an unread
  badge (today it is the antenna connection indicator — that stays only inside rides). Opens a
  Notifications page: grouped Today / Earlier, inline actions (Accept / Deny for leaders, View ride for
  requesters), pull to refresh, mark read, empty state.
- Push: Firebase setup, token registration after sign-in / removal on sign-out, a "Ride requests"
  channel, tapping a notification opens the right screen.

### iOS

Same features; the existing Nearby section is old-design. Push needs APNs + Firebase.

## Estimate

| Phase | What | Effort |
|---|---|---|
| 0 | Close the join-without-code gap; stop nearby exposing codes and exact start points | 2–3 hours |
| 1 | Backend: description, visibility, nearby/preview, join requests, notifications | 3–4 days |
| 2 | Android: nearby, preview, form fields, lobby requests, bell + Notifications page (fetched on open, no push) | 4–5 days |
| 3 | Push: Android + backend sending (needs a Firebase project) | 1.5–2 days |
| 4 | iOS: same features in the old design + APNs | 5–6 days |

Android + backend with push ≈ 2 weeks. Everything incl. iOS ≈ 3 weeks. Porting the Dark Glass redesign to
iOS first is a separate ~1–1.5 weeks. Phases 1–2 can ship without push.

## Decisions needed

1. Discoverability: every ride, or only rides with "Show to nearby riders" on? (Recommended: the switch,
   on by default for new rides.)
2. What strangers see: rough start area only, rider count + avatars but no names, leader name.
   (Recommended.)
3. Which rides accept requests: lobby only, or also rides already riding? (Recommended: lobby only.)
4. Add an optional "Starts at" time to rides? (+~1 day; today browsers can't tell when a ride leaves.)
5. Plan limits: accepting counts toward the leader's rider cap (5 on Free) and the requester's monthly
   limit. Show the leader the paywall if accepting would exceed 5?
6. Accounts: Phase 3 needs a Firebase project; Phase 4 needs an Apple push key.
