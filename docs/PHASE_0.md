# Phase 0 — Foundations

**Goal:** cold start → anonymous sign-in → home placeholder, with CI green.

## Delivered

- Gradle Kotlin DSL project with version catalog and Gradle wrapper 9.9.2
- AGP 9.5.2 with built-in Kotlin, Compose compiler 2.4.21, KSP, Hilt, and Google Services plugins
- Material 3 theme: dynamic color on Android 12+, static teal palette fallback, light/dark
- English and Indonesian resources for every Phase 0 string
- Type-safe Navigation Compose skeleton (`HomeRoute`)
- `AroundApp` (`@HiltAndroidApp`) and Hilt-injected `MainActivity`
- `AuthRepository` + `FirebaseAuthRepository`, anonymous sign-in through `kotlinx-coroutines-play-services`
- `HomeViewModel` with `Loading` / `Ready(uid)` / `Error` states and retry
- Firebase providers for Auth, Realtime Database, and Firestore
- Firebase Emulator Suite configuration for Auth, RTDB, Firestore, and Functions
- Cloud Functions v2 TypeScript skeleton: global `maxInstances: 3`, region `asia-southeast1`, `health` HTTP function
- Remote Config template with `sharing_enabled` and `map_style_url`
- Deny-all placeholder security rules (replaced in Phase 1)
- GitHub Actions CI for lint, unit tests, debug APK, and Functions build
- `.gitignore` covering `google-services.json`, Admin SDK keys, keystores, and local files

## Verified locally

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| `:app:testDebugUnitTest` | 3 tests, 0 failures |
| `:app:lintDebug` | 0 issues |
| `npm run build` (functions) | Passed |
| `firebase emulators:exec` (Auth, RTDB, Firestore, Functions) | All started; `health` loaded |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |

## Not verified / pending

- **Runtime cold start and real anonymous sign-in.** No AVD or connected device is available on this machine. Android Studio can create an AVD (API 36/37 x86_64) or a physical phone can be connected.
- **Anonymous Auth provider.** Must be enabled in the Firebase console once; this is not a repo setting.
- **CI execution.** The workflow needs the `GOOGLE_SERVICES_JSON` GitHub Actions secret. It has not been pushed/run yet.
- **Budget alert and GitHub Projects board.** Manual console/GitHub tasks.

## Notes and deviations

- **compileSdk is 37.2**, because Compose BOM 2026.11.01 / AndroidX libraries require API 37.1+. Platform `android-37.2` was installed. Target SDK is 37; min SDK is 26.
- The installed global JDK is 27, but the build was verified with Android Studio's JBR 25. Use `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` for command-line builds.
- `local.properties` is gitignored and points at the local SDK; its Windows drive colon is escaped (`C\:`) to satisfy lint.
- The Firebase Admin SDK key was moved outside the repository to `C:\Users\axioo\.around-secrets\`.
- The Functions emulator warns that local Node 24 differs from the declared Node 22 runtime. CI uses Node 22; deploy targets Node 22.

## Definition of done

Cold start → anonymous sign-in → home placeholder: **implemented**, runtime check pending a device/emulator. CI passes locally (lint, unit tests, assemble); GitHub Actions pending the repository secret.
