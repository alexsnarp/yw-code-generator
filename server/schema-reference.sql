-- The API shares the existing YW App Store D1 database.
-- This is the table already consumed by yw-app-store-api; do not run this
-- statement against an existing database (it is shown for documentation only).
CREATE TABLE IF NOT EXISTS authorization_codes (
  code_hash TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL,
  used_at INTEGER,
  created_at INTEGER NOT NULL,
  max_uses INTEGER NOT NULL DEFAULT 1,
  uses_count INTEGER NOT NULL DEFAULT 0
);
