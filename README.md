# YW Code Generator

A separate, native Android 6+ administrator app for generating short YW App Store authorization codes, backed by a Cloudflare Worker.

## What it does

- Generates human-readable 8-character codes in `XXXX-XXXX` form.
- Lets the administrator choose the maximum redemptions per code (1–100), number of codes (1–50), and expiry (1–720 hours).
- Shows each generated code only in the creation response; the server stores SHA-256 hashes, never plaintext codes.
- Uses the same D1 authorization-code table as the YW App Store API, so codes generated here are redeemed by the existing App Store `/api/redeem` endpoint.
- The administrator secret is entered at runtime and is never embedded in the APK or persisted by the app.
- Bundles ISRG Root X1 for Android 6 TLS compatibility while preserving normal hostname verification.

## Repository layout

- `android/`: Java Android application (minSdk 23, no WebView).
- `server/`: standalone Cloudflare Worker admin API source.
- `docs/`: deployment and security notes.

## Existing backend integration

The current `yw-app-store-api` Worker already has `POST /admin/codes`, `ADMIN_SECRET`, and the D1 `authorization_codes` table with `max_uses` and `uses_count`. Its code generator already uses the 8-character `XXXX-XXXX` format. This repository provides a separate Android admin app and a separable Worker implementation; both use the same existing D1 database schema. Do not create a second authorization-code database.

The Android app defaults to the existing App Store API URL. You can change the endpoint in the app if the standalone admin Worker is deployed.

## Build

Open `android/` in Android Studio or run a Gradle 8.9 build with JDK 17:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

The GitHub Actions workflow builds and uploads the debug APK and source archive.

## Cloudflare deployment

See [docs/deployment.md](docs/deployment.md). The Worker needs a D1 binding named `DB` pointing to the existing YW App Store database and an `ADMIN_SECRET` Worker secret set to the same administrator secret used by the App Store API. Never commit that secret.
