# Phase 1 — Identity and Circles

**Goal:** profile, Circle creation/joining by invite code, member list, and access revocation. **DoD:** phone A creates a Circle, phone B joins by code, both see the member list; rule tests pass; leaving revokes access.

## Delivered — backend

The backend runs on a **free Vercel Hobby** relay (`backend/`). Firebase stays on Spark; no Blaze or Cloud Functions.

### HTTP API

All endpoints are `POST` with JSON plus `Authorization: Bearer <Firebase ID token>`.

| Endpoint | Behavior |
|---|---|
| `/api/createCircle` | Validates name/profile, caps at 3 Circles per user, creates Circle + owner member, mirrors membership to RTDB, issues the first invite code |
| `/api/joinCircle` | Normalizes/hashes the code, checks expiry/revocation/uses, enforces the 20-member and 3-Circle caps, rate-limits failed guesses, joins transactionally, mirrors to RTDB |
| `/api/rotateInviteCode` | Owner-only; revokes existing codes for the Circle and issues a new 8-character code |
| `/api/leaveCircle` | Non-owner only; deletes RTDB data first, then removes membership + `memberUids` |
| `/api/removeMember` | Owner-only; same revocation path for another member |
| `/api/health` | `GET` liveness check |

Other backend pieces:

- Firebase Admin SDK verifies every ID token before business logic.
- Service account lives only in Vercel environment variables, never in the app or git.
- 8-character invite codes from an unambiguous alphabet, 24 h expiry, max 20 uses, **stored hashed** (`inviteCodes/{sha256(code)}`).
- `joinAttempts/{uid}` rate limit: 10 failed attempts per 10 minutes.
- RTDB mirror `circleMembers/{circleId}/{uid}` written by the relay only.
- One catch-all Vercel function, Node 24, region `sin1`.
- Local `dev-server.ts` runs the same router so API tests do not need a Vercel login.

Architecture details: [ADR 0002](decisions/0002-vercel-relay.md). Deployment: [VERCEL.md](VERCEL.md).

### Security rules

- Firestore: own-profile read/write, member-scoped Circle/member reads, self-only member profile updates, server-only invite codes/secrets/attempts, and the placeholder places/events rules from the plan.
- RTDB: member-only reads, self-only location writes, member-gated history writes.
- `tests/` runs Firestore rules, RTDB rules, and HTTP API integration tests against the emulator suite.

## Delivered — Android

- Session gate: anonymous auth → profile check → Circles.
- Profile setup/edit with display name, emoji or color avatar, and a live avatar preview.
- Circles home with empty state, owner badge, member counts, and profile entry point.
- Create Circle screen + invite code card with copy and share sheet.
- Join by code screen with normalization and localized errors.
- Circle detail: member list with roles/You badge, owner invite-code generation/rotation, member removal, leave confirmation, and error retry.
- Localized English and Bahasa Indonesia strings for all Phase 1 UI.
- `-PuseFirebaseEmulators=true` connects Auth, Firestore, and RTDB to the emulators and the API to `http://10.0.2.2:3000`.
- `-ParoundApiBaseUrl=https://...` points production builds at the deployed Vercel API.
- Network security config permits cleartext only to emulator hosts.

## Verified

| Check | Result |
|---|---|
| Android unit tests | 13 tests, 0 failures |
| Android lint | 0 issues |
| Backend emulator tests | 25 tests, 0 failures (9 Firestore rules, 6 RTDB rules, 10 HTTP API) |
| `:app:assembleDebug` | Passed |
| A creates Circle | Passed; invite code shown once |
| B joins by code | Passed; B sees Alice (owner) + Bob (member) |
| A sees B join | Passed; live member list shows 2 |
| B leaves | Covered by the HTTP API integration test and an earlier UI run |
| Access revocation | API test confirms `permission-denied` after leaving |
| Owner removal | Covered by HTTP API integration tests |
| Invite rotation/expiry/revocation | Covered by HTTP API integration tests |
| Rate limiting | Covered by HTTP API integration test |
| Unauthenticated API call | Rejected with `401 unauthenticated` |

Screenshots:

- `docs/phase1-profile.png` — profile setup
- `docs/phase1-circles-empty.png` — empty Circles state
- `docs/phase1-created.png` — Circle created with invite code
- `docs/phase1-circle-detail-a.png` — owner view with 1 member
- `docs/phase1-joined-b.png` — member B sees both members
- `docs/phase1-a-return.png` — owner A sees both members

Captured through the local Vercel-style API with the Firebase emulators.

## How Phase 1 was tested locally

Two identities were verified with Android **multi-user** on one AVD (only ~0.7 GB free RAM prevented a second emulator):

1. Started Auth, Firestore, and RTDB emulators.
2. Started the local Vercel-style API server (`backend/dev-server.ts`) against those emulators.
3. User 0 (Alice) completed a profile and created “Family” through the app.
4. User 10 (Bob) completed a profile and joined with the invite code.
5. Both member lists showed Alice (owner) and Bob (member).
6. Leave, removal, and revocation are covered by the HTTP API integration tests. An earlier UI run also showed B returning to “No Circles yet” after leaving and Alice’s count returning to 1.

The Firestore watch stream dropped repeatedly during Android user switching, so Bob’s back-stack Circles list briefly showed stale cached data until relaunch. This is an emulator/multi-user artifact; the server-side removal and fresh reads were correct. The app always re-reads from Firestore on a fresh launch.

## Not done / pending

- **Production Firestore is enabled** in `asia-southeast2` (Jakarta) and Firestore/RTDB rules are deployed. The service account `firebase-adminsdk-fbsvc@around-847b3.iam.gserviceaccount.com` has no project IAM role yet, so Admin SDK Firestore calls fail with `PERMISSION_DENIED`. Grant it `roles/datastore.user` (or `roles/editor` for future RTDB/FCM/Auth admin operations). Production create/join is blocked until then.
- **Vercel deployment is live:** `https://around-api-topaz.vercel.app`, project `akha1/around-api`. `/api/health` returns OK. The app defaults to this URL for production builds.
- Remote Config template has not been deployed.
- Budget alerts are no longer required; there is no Blaze plan.
- Owners cannot leave or delete a Circle in Phase 1. A `deleteCircle`/ownership-transfer flow should be added before broader release.
- Invite codes are only visible when created/rotated because only the hash is stored. Rotating invalidates the previous code.
- App Check, Crashlytics, and production analytics remain for later phases.
- Vercel Hobby is for personal, non-commercial use. If the project is ever monetized, it needs a different free host or a paid plan.

## Deploy checklist

1. Grant the backend service account Firestore data access in Google Cloud IAM (`roles/datastore.user`, or `roles/editor` for future features). Production create/join is blocked until this is granted.
2. Vercel CLI:
   - Project `akha1/around-api`
   - Production URL: `https://around-api-topaz.vercel.app`
   - Environment: `FIREBASE_PROJECT_ID`, `FIREBASE_DATABASE_URL`, `FIREBASE_SERVICE_ACCOUNT_BASE64` (secret)
   - Health: `https://around-api-topaz.vercel.app/api/health` returns OK
   - RTDB rules are deployed
3. Firestore is enabled in `asia-southeast2`; rules are deployed.
4. Build the app with `-ParoundApiBaseUrl=https://around-api-topaz.vercel.app` (the default now) and run the two-user flow against production after IAM is granted.

See [VERCEL.md](VERCEL.md) for exact commands and [ADR 0002](decisions/0002-vercel-relay.md) for the architecture.
