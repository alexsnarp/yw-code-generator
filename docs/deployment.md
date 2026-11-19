# Deployment and backend integration

## Existing live App Store backend

The App Store Worker is `yw-app-store-api`. It already exposes:

- `POST /admin/codes` — accepts `{ "count": 1, "maxUses": 1, "ttlDays": 7 }`.
- `POST /api/redeem` — redeems codes and issues device tokens.
- `ADMIN_SECRET` — administrator secret binding.
- `DB` — D1 database binding containing `authorization_codes`.

The app defaults to the current App Store API endpoint, so it can generate codes using the existing server immediately when the administrator enters the same secret configured on that Worker. The current server generates eight-character codes in `XXXX-XXXX` format and stores hashes, not plaintext codes.

## Optional separate code-generator Worker

The `server/src/index.js` Worker is a standalone admin-only implementation of the same `POST /admin/codes` endpoint. To deploy it separately:

1. Create a Worker named `yw-code-generator-api` and upload `server/src/index.js`.
2. Add a D1 binding named `DB` pointing to the existing database ID `2462ffdb-6180-4a80-ae28-be9fa2159556`.
3. Add a Worker secret named `ADMIN_SECRET`. Set it to the same administrator secret configured on `yw-app-store-api`.
4. Do not create a new D1 database. Both the App Store redemption API and code generator must use the same `authorization_codes` table.
5. Set the app's Server URL field to the new Worker's HTTPS URL.

Use a strong administrator secret (32+ characters recommended). Do not put it in source control, build configuration, app resources, or the APK. The Android app asks for it at runtime and clears the field after each request.

## Code format and security

Codes contain 8 random characters from an ambiguity-reduced 32-character alphabet, displayed as `XXXX-XXXX` (40 bits of entropy). The API hashes each code with SHA-256 before storing it. A code's use limit is 1–100 and its lifetime is 1–30 days. Plaintext codes are returned only once at creation time.

The existing App Store API applies an IP-based redemption-attempt limit and checks `uses_count < max_uses` atomically in its conditional SQL update. Keep that redeem endpoint and database table as the authority for redemption.

## Android

The app targets Android 6/API 23 or newer, uses native Java views, no WebView, and bundles ISRG Root X1. The trust manager only falls back to the bundled root when the system trust path fails due to a missing trust anchor. Normal HTTPS hostname verification remains enabled.
