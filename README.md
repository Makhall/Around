# Around

Private, consent-based location sharing for small trusted groups. Android-only, built with Kotlin, Jetpack Compose, Firebase, and MapLibre/OpenStreetMap.

**Status: Phase 1 — Identity and Circles (verified on an Android 16 emulator).** Anonymous sign-in, profiles, Circles, invite codes, member lists, and access revocation are in place. Live sharing, history, places, and ring arrive in later phases.

The full product plan lives in [PROJECT_PLAN.md](PROJECT_PLAN.md). Verification notes: [Phase 0](docs/PHASE_0.md) and [Phase 1](docs/PHASE_1.md).

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
- Functions and security-rule test suites run against the Firebase Emulator Suite
- CI: Android lint, unit tests, debug APK, Functions build, rules and callable tests

## Requirements

- Android Studio with its bundled JDK (25), or any JDK 17+
- Android SDK platform **37.2** and build-tools 36+ (compileSdk 37.2, targetSdk 37, minSdk 26)
- Node 22+ and Firebase CLI 15+ for Functions and emulators
- `app/google-services.json` for project `around-847b3` (present locally, gitignored)
- A Blaze plan to deploy Cloud Functions; local development runs entirely on emulators

## Build and test

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Run the app against the Firebase Emulator Suite

```powershell
# Terminal 1
npm --prefix functions install
npm --prefix functions run build
firebase emulators:start --only auth,firestore,database,functions

# Terminal 2
.\gradlew.bat :app:assembleDebug -PuseFirebaseEmulators=true
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.around.app/.MainActivity
```

The flag makes the app connect Auth (9099), Firestore (8080), RTDB (9000), and Functions (5001) through `10.0.2.2`. Emulator UI: http://localhost:4000.

### Backend tests

```powershell
npm --prefix functions run build
firebase emulators:exec --only auth,firestore,database,functions --project around-847b3 "npm --prefix tests test"
```

This runs Firestore rules, RTDB rules, and callable-function integration tests (24 tests).

## Deploying to production

**Production Phase 1 is not deployed yet.** `around-847b3` is still on the Spark plan, so Cloud Functions cannot be enabled. Once Blaze is active and a budget alert exists:

```powershell
firebase deploy --only functions
firebase deploy --only firestore:rules
firebase deploy --only database
firebase deploy --only remoteconfig
```

Then build the APK **without** `-PuseFirebaseEmulators=true`. See [docs/PHASE_1.md](docs/PHASE_1.md) for details and the deploy checklist.

### Remaining manual setup

1. **Blaze plan + budget alert** in Google Cloud Billing (required for Functions). A budget alert notifies but does not cap spend.
2. **Firestore database** — confirm it exists and record its region. Realtime Database is in `asia-southeast1`.
3. **Anonymous Auth** — enabled and verified in Phase 0.
4. **Remote Config** — deploy `firebase/remoteconfig.template.json`.
5. **GitHub Actions** — CI compiles without the private config using `app/google-services.json.example`; set the `GOOGLE_SERVICES_JSON` secret for a real build.
6. **GitHub Projects board** with milestones M0–M6 (manual, per plan section 9).

## Verification screenshots

Phase 1 end-to-end evidence is in `docs/`:

- `phase1-profile.png` — profile setup
- `phase1-created.png` — invite code after creating a Circle
- `phase1-circle-detail-a.png` — owner with 1 member
- `phase1-joined-b.png` — member B sees both members
- `phase1-a-return.png` — owner A sees both members
- `phase1-b-relaunch.png` — B after leaving, no Circles
- `phase1-a-after-leave.png` — A after B leaves, 1 member

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
| compileSdk / targetSdk / minSdk | 37.2 / 37 / 26 |
| Cloud Functions | Node 22, TypeScript, `asia-southeast1` |

## Phase status

| Phase | Status |
|---|---|
| M0 / Phase 0 — Foundations | **Done and verified** |
| M1 / Phase 1 — Identity and Circles | **Done on emulator; production deploy pending Blaze** |
| M2 / Phase 2 — Live sharing | Not started |
| M3 / Phase 3 — History and last-known | Not started |
| M4 / Phase 4 — Places and alerts | Not started |
| M5 / Phase 5 — Ring | Not started |
| M6 / Phase 6 — Hardening and v1.0 | Not started |
