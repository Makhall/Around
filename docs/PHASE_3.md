# Phase 3 — History and last-known location

**Goal:** short route history with a member/time picker and a route on the map, plus a clear last-known location when someone is offline. **DoD:** history shows a recorded walk; points older than the retention window are removed.

## Delivered

### History recording

- The foreground service now records history points alongside live locations.
- A point is sampled when **60 seconds** have passed or the member moved at least **50 m**; the first fix is always recorded.
- History is written only for Circles where the member has **sharing** on **and** **Save history** on.
- `history/{circleId}/{uid}/{timestamp}` stores `lat`, `lng`, `t`, and optional `acc`.
- Per-Circle setting `historyEnabled` (Firestore member document) defaults to **on**.
- Turning **Save history** off immediately deletes that member's saved history for the Circle.
- Leaving or being removed already deletes the member's RTDB history through `deleteMemberRealtimeData`.
- Service continues to record a time-based heartbeat when stationary; the Phase 2 request no longer uses a minimum-distance filter that could suppress all fixes while stationary.

### Retention and pruning

- Retention is **24 hours** (`HISTORY_RETENTION_MS`).
- A hard cap of **2,000 points per member per Circle** prevents unbounded growth inside the window.
- Vercel Hobby runs the free daily cron `0 3 * * *` at `GET /api/cron/pruneHistory`.
- The endpoint requires `Authorization: Bearer ${CRON_SECRET}`; Vercel supplies this automatically when `CRON_SECRET` is configured.
- Pruning scans Firestore Circles/members, deletes expired RTDB points in batches, then trims the oldest points above the cap.
- No Cloud Functions or Blaze plan is required.

### History screen

- New History screen reachable from the Circle detail History card and the map top bar.
- Member picker with avatars and a **1 h / 6 h / 24 h** range picker.
- MapLibre map with a Compose-drawn route polyline, start dot, latest-position marker, and camera fit to the selected route.
- Timeline with newest-first times, accuracy, and **Latest** / **Start** labels.
- Empty state explains that history appears while sharing and **Save history** are on.
- History listeners are attached only while the screen is visible and are cancelled on stop.

### Last-known and directions

- The member sheet now says **Last seen …** and keeps the stale state and stale chip.
- Marker accessibility copy uses **last seen**.
- **Directions** opens the device's maps app using a `geo:` intent, with a Google Maps web fallback.

## Verified

| Check | Result |
|---|---|
| Android build (`assembleDebug`) | Passed |
| Android unit tests | 31 tests, 0 failures |
| Android lint | 0 issues |
| Backend rules + API suite | 27 tests, 0 failures |
| History sampling | Unit tests cover first point, time interval, distance threshold, and stationary skip |
| Real phone install | Redmi 22120RN86G, Android 14 (API 34), installed over USB with `adb` |
| Real location + live write | Fused location fix written to RTDB with battery/charging/accuracy |
| Real history write | First service sampled point appeared in RTDB history automatically |
| History screen on device | Rendered 10 points, route polyline, member/range pickers, and timeline |
| Save history off | Firestore flag became `false`; RTDB history for the Circle became `null` |
| Directions | Opened Google Maps on the device |
| Retention pruning | API test confirms a 25-hour-old point is deleted and a fresh point remains |
| Unauthenticated pruning | Missing/wrong `CRON_SECRET` returns 401 |
| Recorded real walk | **Pending field test**; the polyline verification used the service's real points plus a seeded route |

Screenshot:

- `docs/phase3-history.png` — History screen on the Redmi with the route, member picker, range picker, and timeline.

All testing used the local Firebase Emulator Suite and local API through `adb reverse`. No production data was created.

## Real-device test notes

- Device: Redmi `22120RN86G`, Android 14, serial `x4xsfquwayrc6xjv`.
- App build: emulator configuration with `-ParoundEmulatorHost=127.0.0.1` and API `http://127.0.0.1:3000`.
- `adb reverse` forwarded 3000/8080/9000/9099 to the host emulators.
- Permissions were granted with `pm grant` for location, background location, and notifications.
- The service wrote a live fix and history point from real GPS; the history screen rendered map tiles and the timeline.
- Seven additional points were seeded into RTDB to verify route polyline rendering; they are test data only.
- The test APK was removed from the phone after verification.

## Not done / pending

- A real walking route captured entirely by the service (not seeded).
- Two-phone live/history test and background survival on multiple OEM brands.
- Production deployment of `CRON_SECRET` and the Vercel cron; the local endpoint and schedule file are ready.
- History retention is fixed at 24 h; the planned configurable 1–7 day setting remains for a later phase.
- App Check, Crashlytics, and production analytics remain for later phases.
