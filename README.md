# Around

Private, consent-based location sharing for small trusted groups. Android-only, built with Kotlin, Jetpack Compose, Firebase, and MapLibre/OpenStreetMap.

**Status: Phase 0 — Foundations (verified on an Android 16 emulator).** Anonymous sign-in, theme, navigation, DI, Firebase wiring, emulator setup, and CI are in place. Circles, live sharing, history, places, and ring arrive in later phases.

The full product plan lives in [PROJECT_PLAN.md](PROJECT_PLAN.md). Phase 0 verification notes are in [docs/PHASE_0.md](docs/PHASE_0.md).

## What works now

- Firebase Anonymous Auth on first launch, verified end-to-end on an Android 16 emulator (including cold start and Auth persistence)
- Material 3 theme with dynamic color, light/dark mode, and static fallback
- English (`values/`) and Bahasa Indonesia (`values-in/`) strings
- Type-safe Navigation Compose skeleton
- Hilt DI with Firebase Auth, Realtime Database, and Firestore providers
- Phase 0 home screen with loading, ready, and error states
- Firebase Emulator Suite config for Auth, RTDB, Firestore, and Functions
- Cloud Functions v2 TypeScript skeleton with `maxInstances: 3` and an `asia-southeast1` health function
- CI: Android lint, unit tests, debug APK, and Functions build
- Secrets kept out of git; `app/google-services.json.example` documents the expected shape

## Requirements

- Android Studio with its bundled JDK (25), or any JDK 17+
- Android SDK platform **37.2** and build-tools 36+ (compileSdk 37.2, targetSdk 37, minSdk 26)
- Node 22+ and Firebase CLI 15+ for Functions and emulators
- `app/google-services.json` for project `around-847b3` (already present locally, gitignored)

## Build and test

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

You can also open the project root in Android Studio. `local.properties` already points at `C:\Users\axioo\AppData\Local\Android\Sdk`.

### Functions

```powershell
cd functions
npm install
npm run build
```

### Firebase Emulator Suite

```powershell
npm --prefix functions install
npm --prefix functions run build
firebase emulators:start
```

Emulator UI: http://localhost:4000. Ports: Auth 9099, Firestore 8080, RTDB 9000, Functions 5001. The `health` function should appear at `http://127.0.0.1:5001/around-847b3/asia-southeast1/health`.

The placeholder rules in `firebase/` deny all reads and writes. Phase 1 replaces them with the member-scoped rules from the plan.

## Firebase setup still to do manually

These are console/billing tasks that cannot be done from the repo:

1. **Authentication → Sign-in method → Anonymous → Enable.** Without this, the app shows the Phase 0 error state.
2. **Firestore** — confirm the database exists and note its region. Realtime Database is already in `asia-southeast1`.
3. **Blaze plan + budget alert** in Google Cloud Billing. A budget alert only notifies; it does not cap spend.
4. **Remote Config** — apply `firebase/remoteconfig.template.json` (`sharing_enabled`, `map_style_url`) with `firebase deploy --only remoteconfig`.
5. **GitHub Actions secret** `GOOGLE_SERVICES_JSON` — paste the contents of `app/google-services.json`. CI fails with a clear message until it is set.
6. **GitHub Projects board** with milestones M0–M6 (manual, per plan section 9).
7. **Firestore/Remote Config deploys** when those features start being used (Phase 1+).

Runtime verification was completed on the local AVD `Around_API_36` (Pixel 10, Android 16 / API 36, Google APIs x86_64). See `docs/phase0-coldstart.png` and `docs/PHASE_0.md`.

### Emulator

Hardware acceleration uses the Android Emulator Hypervisor Driver 2.2 (AEHD). Start the AVD with:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Around_API_36
```

For headless runs (CI-style local checks):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Around_API_36 `
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
```

Then install and launch:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.around.app/.MainActivity
```

## Secrets

- `app/google-services.json` is gitignored and must not be committed to this public repo. Use `app/google-services.json.example` as the template.
- The Firebase Admin SDK key was moved out of the project to `C:\Users\axioo\.around-secrets\around-847b3-firebase-adminsdk-fbsvc-635bcb8a68.json`. Keep it there or in a password manager; never add it to the repo.
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
| M1 / Phase 1 — Identity and Circles | Not started |
| M2 / Phase 2 — Live sharing | Not started |
| M3 / Phase 3 — History and last-known | Not started |
| M4 / Phase 4 — Places and alerts | Not started |
| M5 / Phase 5 — Ring | Not started |
| M6 / Phase 6 — Hardening and v1.0 | Not started |
