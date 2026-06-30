-- Fix 1: per-file evidence type for document-aware date tolerance
-- Fix 2: resolved state from reverse geocoding for state-level geo check
SET search_path TO eprid;

ALTER TABLE evidence
    ADD COLUMN evidence_type    VARCHAR(30)  NOT NULL DEFAULT 'OTHER',
    ADD COLUMN resolved_state   VARCHAR(100) NULL,
    ADD COLUMN state_match      VARCHAR(20)  NULL;  -- MATCH | MISMATCH | UNVERIFIABLE
