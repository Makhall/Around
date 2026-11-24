# Deploying the free Vercel API

The API lives in `backend/` and runs as one catch-all Vercel function at `/api/*`. It uses the Firebase Admin SDK against the `around-847b3` project.

## Current deployment

| Item | Value |
|---|---|
| Vercel project | `akha1/around-api` |
| Production URL | `https://around-api-topaz.vercel.app` |
| Health | `https://around-api-topaz.vercel.app/api/health` |
| Runtime | Node 24 (Vercel), Firebase Admin SDK 13.6 |
| Region | `sin1` |
| Firestore database | `asia-southeast2` (Jakarta) |
| Service account | App Engine default (`around-847b3@appspot.gserviceaccount.com`), stored as `FIREBASE_SERVICE_ACCOUNT_BASE64` |
| Firestore/RTDB rules | Deployed |

Node 24 plus `firebase-admin` 13.6 is used because `firebase-admin` 14 pulls an ESM-only `jose` that Vercel's CommonJS function loader cannot require.

## 1. Install and link

```powershell
cd backend
npm install
vercel login
vercel link
```

When prompted:

- Link to your existing Vercel account/project, or create a project (for example `around-api`).
- Set the project **Root Directory** to `backend` if you create it from the repository root.
- Framework preset: **Other**.
- Build command can stay empty; Vercel compiles the TypeScript API routes.

## 2. Set environment variables in Vercel

Use the Vercel dashboard → Project → Settings → Environment Variables, or the CLI. Set these for **Production**:

| Name | Value |
|---|---|
| `FIREBASE_PROJECT_ID` | `around-847b3` |
| `FIREBASE_DATABASE_URL` | `https://around-847b3-default-rtdb.asia-southeast1.firebasedatabase.app` |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | Base64 of the App Engine default service-account key |
| `CRON_SECRET` | A long random value; Vercel sends it to the daily history-pruning cron |

Generate the base64 value locally without putting the key in shell history if possible:

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("C:\Users\axioo\.around-secrets\around-847b3-appspot-key.json")
) | Set-Clipboard
```

Then:

```powershell
vercel env add FIREBASE_PROJECT_ID production
vercel env add FIREBASE_DATABASE_URL production
vercel env add FIREBASE_SERVICE_ACCOUNT_BASE64 production
vercel env add CRON_SECRET production
```

Paste the clipboard value when prompted. Never commit the JSON key or the env values.

Do **not** set `FIRESTORE_EMULATOR_HOST`, `FIREBASE_AUTH_EMULATOR_HOST`, or `FIREBASE_DATABASE_EMULATOR_HOST` in Vercel.

`backend/vercel.json` schedules `GET /api/cron/pruneHistory` daily at 03:00 UTC. Vercel Hobby permits daily cron jobs; the endpoint refuses to run without the `CRON_SECRET` bearer token.

## 3. Deploy

```powershell
cd backend
vercel deploy --prod
```

The output contains the deployment URL. Verify:

```powershell
Invoke-RestMethod https://YOUR-PROJECT.vercel.app/api/health
# { ok = True; service = around-api }
```

## 4. Point the app at the deployment

The default production API URL in Gradle is a placeholder. Build with the real URL:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug `
  -ParoundApiBaseUrl=https://YOUR-PROJECT.vercel.app
```

For local emulator development the flag defaults to `http://10.0.2.2:3000` when `-PuseFirebaseEmulators=true` is used.

To avoid repeating the property, add this to `%USERPROFILE%\.gradle\gradle.properties`:

```properties
aroundApiBaseUrl=https://YOUR-PROJECT.vercel.app
```

## 5. Vercel Hobby limits

Current free limits (personal, non-commercial use):

- 1,000,000 function invocations/month
- 4 CPU-hours/month
- 360 GB-hours provisioned memory/month
- 100 GB fast data transfer/month
- 300 s max function duration

The API is far below these limits for fewer than 20 users. Exceeding a limit pauses the service until the cycle resets; Vercel does not bill a Hobby account for overage.

## 6. Troubleshooting

| Symptom | Likely cause |
|---|---|
| `401 unauthenticated` | Missing/expired Firebase ID token, or the app is pointed at the wrong API |
| `500 internal` with missing service account | `FIREBASE_SERVICE_ACCOUNT_BASE64` is not set for the environment |
| `500` Firestore errors | Service account lacks Firestore permissions, wrong project ID, or the Firestore API/database is not enabled |
| Firestore 403 during setup | Enable the Cloud Firestore API and create a database in the Firebase console |
| RTDB mirror failures | Wrong `FIREBASE_DATABASE_URL` or the service account lacks RTDB access |
| Cold start of 1–3 s | Normal on a serverless free tier; retry or warm the function before ring tests |
| `DEPLOYMENT_PAUSED` | Hobby usage limit reached; wait for the reset |

## Why not Cloud Functions?

Cloud Functions requires Blaze. This relay keeps the project 100% free on Spark. See [ADR 0002](decisions/0002-vercel-relay.md).
