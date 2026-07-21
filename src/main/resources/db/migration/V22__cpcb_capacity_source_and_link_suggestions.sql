-- V22: wire real CPCB capacity data into the capacity-ceiling plausibility check
-- (prompt: "wire real CPCB capacity data into Check 1")
SET search_path TO eprid;

-- Step 4/5: track whether the capacity-ceiling check used the CPCB-registered figure or the
-- recycler's own self-reported number, so CompositeScoringService's >3x hard-disqualification
-- rule can require CPCB_VERIFIED before it fires (a self-reported number is gameable).
ALTER TABLE plausibility_checks
    ADD COLUMN IF NOT EXISTS capacity_source VARCHAR(20) NOT NULL DEFAULT 'SELF_REPORTED';

-- Step 2/3: pending Recycler <-> CPCB-directory match suggestions. Deliberately NOT auto-linked
-- on any tier (including exact-GST) in this pass: GST-format normalization between
-- recyclers.gst_number and cpcb_recyclers.recycler_gst_no has never been confirmed against real
-- self-reported data (the `recyclers` table had 0 rows at the time this was built, so there was
-- nothing to test the join against) — see feature_spec_close_scoring_gaps.md §6. Revisit
-- auto-linking once real GST data exists and the format is confirmed to normalize cleanly.
CREATE TABLE IF NOT EXISTS recycler_link_suggestions (
    id               VARCHAR(36)  PRIMARY KEY,
    recycler_id      VARCHAR(36)  NOT NULL REFERENCES recyclers(id),
    cpcb_recycler_id VARCHAR(36)  NOT NULL REFERENCES cpcb_recyclers(id),
    match_tier       VARCHAR(20)  NOT NULL,              -- GST_EXACT | NAME_FALLBACK
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | ACCEPTED | REJECTED
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ  NULL
);

-- Only one PENDING suggestion per (recycler, cpcb_recycler) pair — prevents duplicate suggestions
-- from repeated check-creation triggers or backfill re-runs. Accepted/rejected rows are exempt so
-- history is preserved rather than overwritten.
CREATE UNIQUE INDEX IF NOT EXISTS uq_recycler_link_suggestions_pending
    ON recycler_link_suggestions(recycler_id, cpcb_recycler_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_recycler_link_suggestions_status ON recycler_link_suggestions(status);
