# Phase 1 — Identity and Circles

**Goal:** profile, Circle creation/joining by invite code, member list, and access revocation. **DoD:** phone A creates a Circle, phone B joins by code, both see the member list; rule tests pass; leaving revokes access.

## Delivered — backend

### Callable functions (`functions/src/circles.ts`)

| Function | Behavior |
|---|---|
| `createCircle` | Validates name and profile, caps at 3 Circles per user, creates the Circle + owner member doc, mirrors membership to RTDB, issues the first invite code |
| `joinCircle` | Normalizes/hashes the code, checks expiry/revocation/uses, enforces 20-member cap and 3-Circle cap, rate-limits failed guesses, joins transactionally, mirrors to RTDB |
| `rotateInviteCode` | Owner-only; revokes all existing codes for the Circle and issues a new 8-character code |
| `leaveCircle` | Non-owner only; deletes RTDB data first, then removes membership + `memberUids` |
| `removeMember` | Owner-only; same revocation path for another member |

Other backend pieces:

- 8-character invite codes from an unambiguous alphabet, 24 h expiry, max 20 uses, **stored hashed** (`inviteCodes/{sha256(code)}`).
- `joinAttempts/{uid}` rate limit: 10 failed attempts per 10 minutes.
- RTDB mirror `circleMembers/{circleId}/{uid}` written by Functions only.
- `maxInstances: 3`, region `asia-southeast1`, Node 22.
- Explicit Admin SDK initialization (`functions/src/admin.ts`) so the Functions emulator works with `firebase-admin` v14.

### Security rules

- Firestore: own-profile read/write, member-scoped Circle/member reads, self-only member profile updates, server-only invite codes/secrets/attempts, and the placeholder places/events rules from the plan.
- RTDB: member-only reads, self-only location writes, member-gated history writes.
- `tests/` runs Firestore rules, RTDB rules, and callable integration tests against the emulator suite.

## Delivered — Android

- Session gate: anonymous auth → profile check → Circles.
- Profile setup/edit with display name, emoji or color avatar, and a live avatar preview.
- Circles home with empty state, owner badge, member counts, and profile entry point.
- Create Circle screen + invite code card with copy and share sheet.
- Join by code screen with normalization and localized errors.
- Circle detail: member list with roles/You badge, owner invite-code generation/rotation, member removal, leave confirmation, and error retry.
- Localized English and Bahasa Indonesia strings for all Phase 1 UI.
- `-PuseFirebaseEmulators=true` build flag connects Auth, Firestore, RTDB, and Functions to `10.0.2.2`.
- Network security config permits cleartext only to emulator hosts.

## Verified

| Check | Result |
|---|---|
| Android unit tests | 13 tests, 0 failures |
| Android lint | 0 issues |
| Backend emulator tests | 24 tests, 0 failures (9 Firestore rules, 6 RTDB rules, 9 callable integration) |
| `:app:assembleDebug` | Passed |
| A creates Circle | Passed; invite code shown once |
| B joins by code | Passed; B sees Alice (owner) + Bob (member) |
| A sees B join | Passed; live member list shows 2 |
| B leaves | Passed; B returns to Circles |
| Access revocation | B relaunch shows “No Circles yet”; A shows 1 member |
| Owner removal | Covered by callable integration tests |
| Invite rotation/expiry/revocation | Covered by callable integration tests |
| Rate limiting | Covered by callable integration test |

Screenshots:

- `docs/phase1-profile.png` — profile setup
- `docs/phase1-circles-empty.png` — empty Circles state
- `docs/phase1-created.png` — Circle created with invite code
- `docs/phase1-circle-detail-a.png` — owner view with 1 member
- `docs/phase1-joined-b.png` — member B sees both members
- `docs/phase1-a-return.png` — owner A sees both members
- `docs/phase1-b-relaunch.png` — B after leaving, no Circles
- `docs/phase1-a-after-leave.png` — A after B leaves, 1 member

## How Phase 1 was tested locally

Two identities were verified with Android **multi-user** on one AVD (only ~0.7 GB free RAM prevented a second emulator):

1. User 0 (Alice) completed a profile and created “Family”.
2. User 10 (Bob) completed a profile and joined with the invite code.
3. Both member lists showed Alice (owner) and Bob (member).
4. Bob left; after a force-stop/relaunch his Circles list was empty.
5. Alice’s member count returned to 1.

The Firestore watch stream dropped repeatedly during Android user switching, so Bob’s back-stack Circles list briefly showed stale cached data until relaunch. This is an emulator/multi-user artifact; the server-side removal and fresh reads were correct. The app always re-reads from Firestore on a fresh launch.

## Not done / pending

- **Production Functions deploy is blocked by billing.** `around-847b3` is still on the Spark plan; enabling `cloudbuild.googleapis.com` fails until Blaze is active. Phase 1 features work against the emulator only.
- Firestore production database/region has not been confirmed; deploy rules/functions after Blaze is enabled.
- Budget alert and GitHub Projects board are still manual tasks.
- Owners cannot leave or delete a Circle in Phase 1. A `deleteCircle`/ownership-transfer flow should be added before broader release.
- Invite codes are only visible when created/rotated because only the hash is stored. Rotating invalidates the previous code.
- App Check, Crashlytics, and production analytics remain for later phases.

## Deploy checklist once Blaze is enabled

```powershell
# 1. Upgrade the Firebase project to Blaze and create a budget alert first.
# 2. Make sure Firestore exists (asia-southeast1 recommended).
firebase deploy --only functions
firebase deploy --only firestore:rules
firebase deploy --only database
firebase deploy --only remoteconfig
```

Then rebuild the debug APK **without** `-PuseFirebaseEmulators=true` and run the two-user flow against production.
