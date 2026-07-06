-- V9: Recycler credential/KYC check history (Module A0). Additive, does not affect existing tables.
SET search_path TO eprid;

CREATE TABLE IF NOT EXISTS recycler_credential_checks (
    id           VARCHAR(36)  PRIMARY KEY,
    recycler_id  VARCHAR(36)  NOT NULL REFERENCES recyclers(id),
    check_type   VARCHAR(30)  NOT NULL,
    result       VARCHAR(20)  NOT NULL,
    provider     VARCHAR(50)  NOT NULL,
    reason       TEXT         NULL,
    checked_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recycler_credential_checks_recycler ON recycler_credential_checks(recycler_id);
