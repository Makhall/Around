# Phase 2 — Live location sharing

**Goal:** two phones see each other moving on a map in real time, with explicit consent, pause/stop controls, and reliable background sharing. **DoD:** two phones, one walking outdoors, the other sees movement within seconds; sharing survives screen-off for 30+ minutes on at least two phone brands; pause/stop work and update the other phone.

## Delivered — Android

### Permissions and onboarding

- Permission screen with separate status rows for precise/approximate location, notifications, and background location.
- Foreground and notification requests use the Android runtime permission APIs.
- Android 10 requests background location directly; Android 11+ opens app settings with the exact path in the copy.
- Battery-optimization guidance card and a map-screen guidance dialog open the system battery settings.
- A setup card on the Circles screen appears whenever location or notification permission is missing.
- Sharing cannot be turned on from the Circle or map screens without location **and** notification permission, so the persistent sharing notification is always available.
- English and Bahasa Indonesia copy for the full flow.

### Location foreground service

- `LocationForegroundService` is a location-typed foreground service with an ongoing notification.
- Adaptive request profile:
  - moving: high accuracy, 10 s interval, 15 m minimum distance
  - stationary after 2 minutes without movement: balanced accuracy, 5 minute interval, 50 m minimum distance
- Each fix writes `lat`, `lng`, accuracy, speed, battery %, charging state, fix time, and a server timestamp.
- One multi-path RTDB write updates every Circle where this member has sharing enabled.
- Notification actions: **Pause 1 h**, **Resume**, **Stop**. The map adds pause presets for 1 hour, 8 hours, and until manually resumed.
- Pause stores an absolute resume time and schedules an in-process timer; a process restart restores the pause state.
- Stop immediately removes the ongoing notification, disables the per-Circle sharing flags, and deletes this member's live location from every Circle.
- `START_STICKY`, `BootReceiver` (`BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`), and DataStore preferences restore sharing after a process kill or reboot.
- Remote Config `sharing_enabled=false` stops a newly starting service and disables the sharing controls.

### Map

- MapLibre Native map in Compose with the OpenFreeMap Liberty style (`map_style_url` in Remote Config).
- Avatar markers drawn as Compose overlays and projected with the MapLibre camera; marker positions update while panning and zooming.
- Current member gets a primary-color ring; stale members are dimmed.
- Tapping a marker opens a Material 3 bottom sheet with avatar, role, battery, charging state, accuracy, last updated, and stale state.
- Sharing card on the map with the per-Circle toggle, live/paused/stopped status, pause menu, and stop action.
- Floating action button recenters on the current member's last fix.
- Attribution and MapLibre logo are enabled.
- Firestore and RTDB listeners are attached in `LifecycleStartEffect` and cancelled on stop, so no location listeners remain while the map is in the background.

### Privacy and data

- `sharing` is a per-Circle member flag; default is off (opt-in).
- Turning a Circle off clears that Circle's RTDB location immediately.
- Global stop clears all of this member's locations and disables every Circle flag.
- Leaving or being removed deletes the member's RTDB location (`deleteMemberRealtimeData`).
- The service never writes when there is no sharing-enabled Circle.
- No backend endpoint is called on each location write; live locations stay on the RTDB Spark tier.

### Backend fix found during Phase 2

The RTDB emulator client used the database instance namespace (`around-847b3-default-rtdb`), while the backend relay used the project ID (`around-847b3`). Membership mirrors therefore landed in a different emulator namespace and client location writes were denied. `backend/src/firebase.ts` now derives the namespace from `FIREBASE_DATABASE_URL`, with `FIREBASE_DATABASE_INSTANCE` as an override. The API integration test now reads the RTDB mirror through the Firebase client to cover this path.

## Verified

| Check | Result |
|---|---|
| Android build (`assembleDebug`) | Passed |
| Android unit tests | 23 tests, 0 failures |
| Android lint | 0 issues |
| Backend rules + API suite | 25 tests, 0 failures (including the new RTDB mirror assertion) |
| Android share toggle | Wrote `sharing=true`, started the foreground service |
| Service location write | Alice's fix appeared in RTDB with lat/lng/accuracy/battery/charging/timestamps |
| Two-member map | Both avatar markers rendered; member sheet data was available |
| Pause 1 hour | Map showed `Paused until …`; notification switched to resume |
| Resume | Returned to `Sharing location` |
| Stop | Sharing flags disabled and Alice's live location deleted from RTDB |
| Health endpoint | Local API and production API return OK |
| Two-phone movement | **Pending field test** on two real phones |
| 30+ minute screen-off survival | **Pending field test** on at least two OEM brands |
| Map tile raster in headless emulator | MapLibre style, markers, logo, and attribution rendered; tile raster was blank after an emulator GPU restart (`MESA: Failed to open rendernode`). Tile requests returned HTTP 200. Verify on a real device. |

Screenshot:

- `docs/phase2-map.png` — map screen with the sharing card and two avatar markers.

All Phase 2 runtime testing used the Firebase Emulator Suite and the local API. No production test data was created.

## How Phase 2 was tested locally

1. Started Auth, Firestore, and RTDB emulators and the local API.
2. Because the headless emulator's `10.0.2.2` forwarding became unreliable, forwarded ports and built with `127.0.0.1`:

   ```powershell
   adb -s emulator-5554 reverse tcp:3000 tcp:3000
   adb -s emulator-5554 reverse tcp:8080 tcp:8080
   adb -s emulator-5554 reverse tcp:9000 tcp:9000
   adb -s emulator-5554 reverse tcp:9099 tcp:9099

   .\gradlew.bat :app:assembleDebug `
     -PuseFirebaseEmulators=true `
     -ParoundEmulatorHost=127.0.0.1 `
     -ParoundApiBaseUrl=http://127.0.0.1:3000
   ```

3. Granted location, background location, and notification permissions with `pm grant`.
4. Created Alice's profile and Circle in the app.
5. Seeded Bob through the local API and wrote a nearby RTDB fix; both avatars appeared on the map.
6. Enabled sharing, confirmed Alice's fix in RTDB, exercised pause/resume/stop, and confirmed stop deleted the location and flags.
7. Re-ran the backend API integration suite; it now asserts the RTDB membership mirror through the Firebase client.

## Not done / pending

- Two-phone field test with real movement and at least two OEM brands.
- 30+ minute screen-off background survival test.
- Tile raster verification on a real device (the headless emulator's GPU was unreliable after restarting).
- Remote Config values are still not deployed; the app falls back to `sharing_enabled=true` and the Liberty style.
- App Check, Crashlytics, and production analytics remain for later phases.
- The map currently shows only members with a live location; the Phase 3 history/last-known screen will add offline last-known details.
