# Phase 4 — Places and alerts

**Goal:** let a Circle share named places and get a push when someone arrives or leaves. **DoD:** leaving and entering a place triggers a push on the other phone, with no duplicate alerts.

## Delivered

### Places

- Places live in Firestore at `circles/{circleId}/places/{placeId}` with `name`, `lat`, `lng`, `radiusM`, `createdBy`, and `createdAt`.
- Each Circle accepts up to **20 places**; the radius is limited to **100–1000 m** and names to 40 characters in the security rules.
- A **Places** card on the Circle detail screen opens the list. Any member can add a place; the creator or the Circle owner can edit or delete it.
- The place editor uses a MapLibre/OpenStreetMap picker centred on the member's last known location and a **50 m-step radius slider**. The radius circle is drawn on the picker.
- Places, their radius circles, and name labels are drawn on the live Circle map. The member sheet adds **At <place>** when a member is inside a saved place.
- Per-member **Place alerts** switch on the Circle detail screen; it defaults to on and is stored as `placeAlerts` on the member document.

### Geofencing

- Each phone registers the Circle's places with the Google Play Services **Geofencing API** (`ACCESS_FINE_LOCATION` + background location), up to 100 geofences.
- Registration refreshes on sign-in/app start, after a place is saved or deleted, and after boot or an app update.
- Geofence IDs are `circleId|placeId`. The broadcast receiver accepts **enter** and **exit** transitions.
- The receiver reports the transition to the relay with an authenticated `POST /api/placeEvent`; it does not write Firestore directly.

### Alerts

- The Vercel relay validates that the caller is a member and the place exists, writes a server-only `events` document, and dedupes by `uid + placeId + type` in **5-minute buckets**.
- Receiving members (`placeAlerts` not false) get an FCM **data message** through the Admin SDK. Tokens live at `users/{uid}/devices/{deviceId}` and are registered after sign-in and on token refresh.
- Invalid or unregistered FCM tokens are deleted from Firestore.
- The Android **Place alerts** notification channel is created at app start. Alerts show "<name> arrived at <place>" or "<name> left <place>" and open that Circle's map when tapped.
- Emulator tests skip the actual FCM send (`FIREBASE_AUTH_EMULATOR_HOST`/`FIRESTORE_EMULATOR_HOST` detected) while still exercising validation and dedupe.

## Design deviation

The project plan originally said the geofence receiver writes the event document and a Firestore trigger sends FCM. Vercel cannot react to Firestore writes on the Spark plan, so the receiver calls the relay instead. The event document is still created server-side, with the same dedupe behaviour, but clients have no write access to `events`.

## Verified

| Check | Result |
|---|---|
| Android build (`assembleDebug`) | Passed |
| Android unit tests | 36 tests, 0 failures |
| Android lint | 0 issues |
| Backend rules + API suite | 30 tests, 0 failures |
| Firestore rules deployment | `places`/`events`/`placeAlerts` rules released to production |
| Production API | `/api/placeEvent` deployed to `https://around-api-topaz.vercel.app` |
| Device token registration | Both production phones registered an FCM token under `users/{uid}/devices` |
| Place create on device | Redmi created **Rumah** (150 m) through the map picker; Firestore doc matched |
| Place edit on device | Radius changed to **350 m**; Firestore and the Places list updated |
| Place delete on device | Confirmation dialog, empty state, Firestore `places` empty |
| Geofence registration | Both phones logged `Registered 1 geofences`; Redmi re-registered after the edit |
| Place labels | POCO map showed the **Rumah** label, radius circle, and member inside it |
| Production FCM fan-out | `POST /api/placeEvent` returned `sent: 1`, then `deduped: true` on repeat |
| Notification on other phone | Redmi displayed "Place alert — FieldA arrived at Rumah · FieldTest" |
| Notification tap | Opened the FieldTest live map from the background |
| Alert mute | FieldB switched **Place alerts** off; the next event returned `sent: 0`; switching on restored `placeAlerts: true` |
| Real geofence crossing | **Pending** — requires walking out of and back into the radius |

## Real-device notes

- POCO `2511FPC34G` (Android 16): FieldA / owner. Redmi `22120RN86G` (Android 14): FieldB / member.
- Both run the production API and production Firestore/RTDB.
- The first geofence attempt exposed an Android 12+ requirement: geofence `PendingIntent`s must be `FLAG_MUTABLE`. It is fixed and re-verified with `Registered 1 geofences`.
- The FCM fan-out and dedupe were exercised with a real FieldA ID token while both phones were signed in.

Screenshots:

- `docs/phase4-places.png` — Places list with **Rumah** and its 150 m radius on the Redmi.
- `docs/phase4-alert.png` — "FieldA left Rumah · FieldTest" notification on the Redmi.

## Not done / pending

- Walk out of and back into a place to see the OS geofence trigger the receiver and alert the other phone; the DoD expects a delay of a couple of minutes.
- Two-phone Phase 2 field walk and 30-minute screen-off survival remain pending.
- Production `FieldTest` data (`WfdZ591cw6G3DCQKs05A`) is test data and must be removed after the field tests.
