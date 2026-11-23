# Around

Private, consent-based location sharing for small trusted groups. Android-only, built with Kotlin, Jetpack Compose, Firebase Spark, a free Vercel Hobby relay, and MapLibre/OpenStreetMap.

**Status: Phase 1 — Identity and Circles (verified on an Android 16 emulator).** Anonymous sign-in, profiles, Circles, invite codes, member lists, and access revocation are in place. Live sharing, history, places, and ring arrive in later phases.

The full product plan lives in [PROJECT_PLAN.md](PROJECT_PLAN.md). Architecture decision for the free backend: [ADR 0002](docs/decisions/0002-vercel-relay.md). Verification notes: [Phase 0](docs/PHASE_0.md) and [Phase 1](docs/PHASE_1.md). Deployment: [Vercel API](docs/VERCEL.md).

## What works now

- Firebase Anonymous Auth on first launch, including cold start and Auth persistence
- Profile setup/edit with display name and emoji or color avatar
- Create a Circle (max 3 per user), with an 8-character invite code valid for 24 hours
- Join a Circle by code, with localized errors for expired, revoked, full, rate-limited, and unknown codes
- Member list with owner/member roles and a real-time update when someone joins
- Owner can rotate the invite code and remove members
- Members can leave; leaving or removal revokes Firestore and RTDB access
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
| Cloud Messaging | Free | Receiving push (sending relayed later) |
| Remote Config / Crashlytics | Free | Kill switch, map style, crash reports |
| Vercel Functions | Hobby | Trusted API, future FCM fan-out, ring, recovery, pruning |

No Blaze plan or payment method is required. Vercel Hobby pauses on overage; it does not bill. See [ADR 0002](docs/decisions/0002-vercel-relay.md) for the tradeoffs.

## Requirements

- Android Studio with its bundled JDK (25), or any JDK 17+
- Android SDK platform **37.2** and build-tools 36+ (compileSdk 37.2, targetSdk 37, minSdk 26)
- Node 22+ and Firebase CLI 15+ for the emulator suite and backend
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

With `-PuseFirebaseEmulators=true`, the app uses the local emulators and `http://10.0.2.2:3000` for the API. Production builds default to the `https://around-api.vercel.app` placeholder until the real Vercel URL is supplied.

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

1. **Deploy the Vercel API** and set the three production environment variables.
2. **Firestore database** — the Cloud Firestore API is not enabled on the project yet. Create a Spark-plan database (choose the region once; `asia-southeast1` matches the RTDB) and deploy rules. Realtime Database is already in `asia-southeast1`.
3. **Anonymous Auth** — enabled and verified in Phase 0.
4. **Remote Config** — deploy `firebase/remoteconfig.template.json` (`sharing_enabled`, `map_style_url`).
5. **GitHub Actions** — CI compiles without the private config using `app/google-services.json.example`; set the `GOOGLE_SERVICES_JSON` secret for a real build.
6. **GitHub Projects board** with milestones M0–M6 (manual, per plan section 9).

## Verification screenshots

Phase 1 end-to-end evidence is in `docs/`:

- `phase1-profile.png` — profile setup
- `phase1-created.png` — invite code after creating a Circle
- `phase1-circle-detail-a.png` — owner with 1 member
- `phase1-joined-b.png` — member B sees both members
- `phase1-a-return.png` — owner A sees both members

The screenshots were captured through the local Vercel-style API with the Firebase emulators.

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
- The Firebase Admin SDK key is outside the repository at `C:\Users\axioo\.around-secrets\around-847b3-firebase-adminsdk-fbsvc-635bcb8a68.json`.
- In production, the key exists only as the base64 `FIREBASE_SERVICE_ACCOUNT_BASE64` Vercel environment variable.
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
| API runtime | Node 22, TypeScript, Vercel Hobby `sin1` |

## Phase status

| Phase | Status |
|---|---|
| M0 / Phase 0 — Foundations | **Done and verified** |
| M1 / Phase 1 — Identity and Circles | **Done on emulator; Vercel deploy pending** |
| M2 / Phase 2 — Live sharing | Not started |
| M3 / Phase 3 — History and last-known | Not started |
| M4 / Phase 4 — Places and alerts | Not started |
| M5 / Phase 5 — Ring | Not started |
| M6 / Phase 6 — Hardening and v1.0 | Not started |
