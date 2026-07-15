-- V21: CPCB recycler directory refresh pipeline (feature_spec_cpcb_directory_refresh.md)
SET search_path TO eprid;

ALTER TABLE cpcb_recyclers ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMPTZ NULL;
ALTER TABLE cpcb_recyclers ADD COLUMN IF NOT EXISTS pending_review BOOLEAN NOT NULL DEFAULT FALSE;
-- Set when a previously-seen cpcb_id is absent from a refresh pull; cleared automatically if it
-- reappears. Never triggers a delete (§2 of the spec: could be deregistration, could be a
-- CPCB-side pagination hiccup — a flag is reversible, a delete isn't).
ALTER TABLE cpcb_recyclers ADD COLUMN IF NOT EXISTS no_longer_listed_at TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS cpcb_refresh_run (
    id              VARCHAR(36)  PRIMARY KEY,
    started_at      TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ  NULL,
    records_fetched INT          NOT NULL DEFAULT 0,
    records_changed INT          NOT NULL DEFAULT 0,
    records_new     INT          NOT NULL DEFAULT 0,
    records_missing INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL,   -- RUNNING | SUCCESS | PARTIAL | FAILED
    error_detail    TEXT         NULL
);

-- One row per changed tracked field per recycler per run — not per unchanged recycler
-- (feature_spec_cpcb_directory_refresh.md §2: no point logging ~550 "nothing changed" rows nightly).
CREATE TABLE IF NOT EXISTS cpcb_recycler_snapshot_diff (
    id             VARCHAR(36)  PRIMARY KEY,
    recycler_id    VARCHAR(36)  NOT NULL REFERENCES cpcb_recyclers(id),
    refresh_run_id VARCHAR(36)  NOT NULL REFERENCES cpcb_refresh_run(id),
    field_name     VARCHAR(60)  NOT NULL,
    old_value      TEXT         NULL,
    new_value      TEXT         NULL,
    detected_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cpcb_recycler_snapshot_diff_recycler_id ON cpcb_recycler_snapshot_diff(recycler_id);
CREATE INDEX IF NOT EXISTS idx_cpcb_recycler_snapshot_diff_refresh_run_id ON cpcb_recycler_snapshot_diff(refresh_run_id);
CREATE INDEX IF NOT EXISTS idx_cpcb_recyclers_pending_review ON cpcb_recyclers(pending_review) WHERE pending_review = TRUE;
