# ADR 0002 — Vercel Hobby relay instead of Firebase Cloud Functions

**Date:** 2026-11-23
**Status:** Accepted

## Context

The project must stay **100% free**. The original plan assumed Firebase Cloud Functions on the Blaze plan for trusted server-side logic, invite validation, push fan-out, ring, recovery tokens, and pruning.

Firebase Spark does not include Cloud Functions, and Cloud Functions cannot be deployed without a billing account. Enabling `cloudbuild.googleapis.com` fails until the project is upgraded to Blaze. Spark still includes Auth, Firestore, Realtime Database, FCM receiving, Remote Config, and Crashlytics.

We need a trusted execution environment for operations that cannot safely live on the client.

## Decision

Keep Firebase on the **Spark plan** for data and identity, and move privileged operations to a **Vercel Hobby** backend.

```
Android app
  ├─ Firebase Auth (anonymous)
  ├─ Firestore / RTDB reads and rule-governed writes
  └─ HTTPS + Firebase ID token
        └─ Vercel Hobby API (Node 22, region sin1)
              ├─ firebase-admin: verify ID token
              ├─ Firestore / RTDB admin access
              └─ FCM HTTP v1 (later phases)
```

Vercel Hobby was chosen because:

- Free; no payment method required.
- 1,000,000 function invocations, 4 CPU-hours, and 360 GB-hours per month.
- Node.js functions run the Firebase Admin SDK without a custom runtime.
- No bills on overage; the service pauses until the limit resets.
- Free tier is for personal, non-commercial use, which matches this project.
- Supports a Singapore (`sin1`) region close to the RTDB/Firestore location.

## API contract

All endpoints are `POST` with JSON and `Authorization: Bearer <Firebase ID token>`:

| Endpoint | Body | Result |
|---|---|---|
| `/api/createCircle` | `{ "name": string }` | `{ circleId, circleName, inviteCode, expiresAt }` |
| `/api/joinCircle` | `{ "code": string }` | `{ circleId, circleName }` |
| `/api/rotateInviteCode` | `{ "circleId": string }` | `{ inviteCode, expiresAt }` |
| `/api/leaveCircle` | `{ "circleId": string }` | `{ ok: true }` |
| `/api/removeMember` | `{ "circleId": string, "targetUid": string }` | `{ ok: true }` |
| `/api/health` | `GET` | `{ ok: true, service: "around-api" }` |

Errors are `{ "error": { "code": string, "message": string } }`. The Android client maps `code` to localized UI messages.

## Security

- The Firebase service-account key is stored only in Vercel environment variables, never in git or the APK.
- Every endpoint verifies a Firebase ID token with the Admin SDK.
- Firestore rules continue to gate direct client reads/writes; Admin SDK operations bypass rules and are validated in TypeScript.
- Invite codes remain SHA-256 hashed at rest.
- Join failures are rate-limited in Firestore (`joinAttempts`).
- CORS is irrelevant for the Android client; the API does not implement browser CORS.

## Consequences

### Positive

- Project remains 100% free with no Blaze/billing account.
- Keeps the original MVP features, including server-side invite validation and future FCM/ring support.
- The existing TypeScript business logic ports with minimal changes.
- Rules and integration tests run locally against emulators.

### Negative

- A second platform/account and deployment secret to manage.
- Vercel Hobby terms restrict usage to personal, non-commercial projects.
- Free-tier capacity is finite; exceeding it pauses the API rather than billing.
- Serverless cold starts may add roughly 1–3 seconds to the first request. Ring delivery will need a warm-up/latency spike in Phase 5.
- Firestore `onCreate` triggers are unavailable. Place-alert events will call an authenticated API endpoint from the geofence receiver instead of relying on a database trigger.
- Scheduled pruning will use Vercel Cron (or a GitHub Actions schedule) rather than Cloud Functions scheduler.

## Supersedes

This ADR supersedes the Cloud Functions/Blaze assumptions in `PROJECT_PLAN.md` sections 5.x, 6, 7.5, and the Phase 4–6 function references. Firebase Spark quotas still apply.
