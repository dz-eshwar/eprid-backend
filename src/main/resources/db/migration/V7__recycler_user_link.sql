-- V7: Link Recycler entity to a registered User account (Module C1 — RECYCLER role)
SET search_path TO eprid;

ALTER TABLE recyclers ADD COLUMN IF NOT EXISTS user_id VARCHAR(36) NULL REFERENCES users(id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_recyclers_user_id ON recyclers(user_id) WHERE user_id IS NOT NULL;
