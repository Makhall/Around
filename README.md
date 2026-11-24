# Around

Private, consent-based location sharing for small trusted groups. Android-only, built with Kotlin, Jetpack Compose, Firebase Spark, a free Vercel Hobby relay, and MapLibre/OpenStreetMap.

**Status: Phase 2 — Live location sharing (implemented, emulator-verified; two-phone field test pending).** Phase 1 identity/Circles is production-verified. Phase 2 adds the permission flow, a location foreground service, per-Circle sharing controls, pause/stop, boot restore, and a MapLibre/OpenStreetMap screen with avatar markers and stale state.

The full product plan lives in [PROJECT_PLAN.md](PROJECT_PLAN.md). Architecture decision for the free backend: [ADR 0002](docs/decisions/0002-vercel-relay.md). Verification notes: [Phase 0](docs/PHASE_0.md), [Phase 1](docs/PHASE_1.md), and [Phase 2](docs/PHASE_2.md). Deployment: [Vercel API](docs/VERCEL.md).

## What works now

- Firebase Anonymous Auth on first launch, including cold start and Auth persistence
- Profile setup/edit with display name and emoji or color avatar
- Create a Circle (max 3 per user), with an 8-character invite code valid for 24 hours
- Join a Circle by code, with localized errors for expired, revoked, full, rate-limited, and unknown codes
- Member list with owner/member roles and a real-time update when someone joins
- Owner can rotate the invite code and remove members
- Members can leave; leaving or removal revokes Firestore and RTDB access
- Location + notification + background-location permission onboarding with OEM battery guidance
- Location foreground service with adaptive intervals, battery/charging payload, multi-Circle writes, and a persistent notification
- Pause 1 h/8 h/indefinitely, resume, and stop controls; stop clears live locations and per-Circle sharing flags
- Resume after reboot or process kill
- Per-Circle sharing toggle and a MapLibre/OpenStreetMap live map with avatar markers, member sheet, stale state, and share controls
- Material 3 theme with dynamic color, light/dark, and English + Bahasa Indonesia
- Hilt DI, type-safe Navigation Compose, Firestore/RTDB-backed repositories
- Privileged operations run on a free Vercel Hobby Node relay with Firebase ID-token verification
- Backend API and Firebase rules test suites run against the Firebase Emulator Suite
- CI: Android lint, unit tests, debug APK, API typecheck/build, rules and API integration tests

## Cost model

This project is designed to stay **100% free**:

| Service | Plan | Use |
|---|---|---|
| Firebase Auth | Spark | Anonymous users |
| Cloud Firestore | Spark | Profiles, Circles, places, events |
| Realtime Database | Spark | Live locations, history, membership mirror |
| MapLibre + OpenFreeMap | Free | Map rendering; no API key |
| Cloud Messaging | Free | Receiving push (sending relayed later) |
| Remote Config / Crashlytics | Free | Kill switch, map style, crash reports |
| Vercel Functions | Hobby | Trusted API, future FCM fan-out, ring, recovery, pruning |

No Blaze plan or payment method is required. Vercel Hobby pauses on overage; it does not bill. See [ADR 0002](docs/decisions/0002-vercel-relay.md) for the tradeoffs.

## Requirements

- Android Studio with its bundled JDK (25), or any JDK 17+
- Android SDK platform **37.2** and build-tools 36+ (compileSdk 37.2, targetSdk 37, minSdk 26)
- Node 24+ and Firebase CLI 15+ for the emulator suite and backend
- `app/google-services.json` for project `around-847b3` (present locally, gitignored)
- A free Vercel account and the Vercel CLI for deployment

## Build and test

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Run the full stack locally

```powershell
# Terminal 1 - emulators (no Functions emulator anymore)
firebase emulators:start --only auth,firestore,database

# Terminal 2 - API
Copy-Item backend\.env.example backend\.env
npm --prefix backend install
npm --prefix backend run dev

# Terminal 3 - app
.\gradlew.bat :app:assembleDebug -PuseFirebaseEmulators=true
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.around.app/.MainActivity
```

With `-PuseFirebaseEmulators=true`, the app uses the local emulators and `http://10.0.2.2:3000` for the API. Production builds default to the deployed API at `https://around-api-topaz.vercel.app`; override with `-ParoundApiBaseUrl=...` if needed.

If `10.0.2.2` forwarding is unreliable on your machine, forward the emulator ports and use `127.0.0.1` instead:

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

### Backend tests

```powershell
npm --prefix backend run build
firebase emulators:exec --only auth,firestore,database --project around-847b3 "npm --prefix tests test"
```

This runs Firestore rules, RTDB rules, and HTTP API integration tests (including ID-token auth, create/join/rotate/leave/remove, rate limiting, and revocation).

## Deploying the free API

See [docs/VERCEL.md](docs/VERCEL.md). The short version:

```powershell
cd backend
npm install
vercel login
vercel link
vercel env add FIREBASE_SERVICE_ACCOUNT_BASE64 production
vercel env add FIREBASE_PROJECT_ID production
vercel env add FIREBASE_DATABASE_URL production
vercel deploy --prod
```

Then build the app with the deployment URL:

```powershell
.\gradlew.bat :app:assembleDebug `
  -ParoundApiBaseUrl=https://YOUR-PROJECT.vercel.app
```

The service-account key is stored only in Vercel environment variables. Never commit it.

### Remaining manual setup

1. **Vercel API** — live at `https://around-api-topaz.vercel.app`; `/api/health` returns OK. Production environment variables are set in project `akha1/around-api`.
2. **Firestore** — database exists in **`asia-southeast2`** (Jakarta) with Firestore and RTDB rules deployed. The production create/cleanup smoke test passed.
3. **Remote Config** — deploy `firebase/remoteconfig.template.json` (`sharing_enabled`, `map_style_url`) before release; the app has safe local defaults.
4. **GitHub Actions** — CI compiles without the private config using `app/google-services.json.example`; set the `GOOGLE_SERVICES_JSON` secret for a real build.
5. **GitHub Projects board** with milestones M0–M6 (manual, per plan section 9).

## Verification screenshots

Phase 1 end-to-end evidence is in `docs/`:

- `phase1-profile.png` — profile setup
- `phase1-created.png` — invite code after creating a Circle
- `phase1-circle-detail-a.png` — owner with 1 member
- `phase1-joined-b.png` — member B sees both members
- `phase1-a-return.png` — owner A sees both members

Phase 2 evidence is in `docs/phase2-map.png` — the live map with the sharing card and both avatar markers. It was captured through the local API with the Firebase emulators. Tile raster rendering was intermittent in the headless emulator after a GPU restart; verify tiles on a real device.

The Phase 1 screenshots were captured through the local Vercel-style API with the Firebase emulators.

## Emulator

Hardware acceleration uses the Android Emulator Hypervisor Driver 2.2 (AEHD). AVD `Around_API_36` is a Pixel 10 / Android 16 / Google APIs x86_64 image.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Around_API_36
```

Headless:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Around_API_36 `
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
```

## Secrets

- `app/google-services.json` is gitignored and must not be committed to this public repo. Use `app/google-services.json.example` as the template.
- The Vercel backend uses the project's App Engine default service-account key at `C:\Users\axioo\.around-secrets\around-847b3-appspot-key.json`. It is stored in Vercel as the `FIREBASE_SERVICE_ACCOUNT_BASE64` secret.
- The original `firebase-adminsdk-fbsvc` key remains at `C:\Users\axioo\.around-secrets\around-847b3-firebase-adminsdk-fbsvc-635bcb8a68.json`; it has no project IAM roles and is not used by the backend.
- Keystores, `.env` files, and service-account JSON files are gitignored.

## Version baseline

| Tool | Version |
|---|---|
| Gradle | 9.9.2 |
| Android Gradle Plugin | 9.5.2 (built-in Kotlin) |
| Kotlin / Compose compiler | 2.4.21 |
| Compose BOM | 2026.11.01 |
| Firebase BoM | 35.2.0 |
| Hilt / KSP | 2.60.1 / 2.3.14 |
| OkHttp | 5.5.1 |
| compileSdk / targetSdk / minSdk | 37.2 / 37 / 26 |
| API runtime | Node 24, TypeScript, Vercel Hobby `sin1` |

## Phase status

| Phase | Status |
|---|---|
| M0 / Phase 0 — Foundations | **Done and verified** |
| M1 / Phase 1 — Identity and Circles | **Done and verified, including production create/cleanup** |
| M2 / Phase 2 — Live sharing | **Implemented and emulator-verified; two-phone field test pending** |
| M3 / Phase 3 — History and last-known | Not started |
| M4 / Phase 4 — Places and alerts | Not started |
| M5 / Phase 5 — Ring | Not started |
| M6 / Phase 6 — Hardening and v1.0 | Not started |
