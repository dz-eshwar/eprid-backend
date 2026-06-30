-- Agent 2: Auth + core EPR entities
SET search_path TO eprid;

-- ─── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            VARCHAR(36)   PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    full_name     VARCHAR(255)  NOT NULL,
    role          VARCHAR(30)   NOT NULL DEFAULT 'CONSULTANT',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);

-- ─── Producers ────────────────────────────────────────────────────────────────
CREATE TABLE producers (
    id                  VARCHAR(36)   PRIMARY KEY,
    name                VARCHAR(255)  NOT NULL,
    cpcb_reg_number     VARCHAR(100)  NULL UNIQUE,
    battery_categories  TEXT          NOT NULL DEFAULT '',
    created_by_user_id  VARCHAR(36)   NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_producers_created_by ON producers(created_by_user_id);

-- ─── Recyclers ────────────────────────────────────────────────────────────────
CREATE TABLE recyclers (
    id                       VARCHAR(36)     PRIMARY KEY,
    name                     VARCHAR(255)    NOT NULL,
    bwmr_reg_number          VARCHAR(100)    NULL UNIQUE,
    self_reported_capacity_t NUMERIC(12,3)   NULL,
    state                    VARCHAR(100)    NULL,
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_recyclers_bwmr_reg ON recyclers(bwmr_reg_number);

-- ─── Verification Checks ──────────────────────────────────────────────────────
CREATE TABLE verification_checks (
    id                    VARCHAR(36)   PRIMARY KEY,
    producer_id           VARCHAR(36)   NOT NULL REFERENCES producers(id),
    recycler_id           VARCHAR(36)   NOT NULL REFERENCES recyclers(id),
    requested_by_user_id  VARCHAR(36)   NOT NULL REFERENCES users(id),
    compliance_estimate_id VARCHAR(36)  NULL REFERENCES compliance_estimates(id),

    -- Batch details claimed by recycler
    batch_weight_tonnes   NUMERIC(12,3) NOT NULL,
    claimed_recovery_pct  NUMERIC(5,2)  NOT NULL,
    processing_date       DATE          NOT NULL,

    -- Outcome
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   -- PENDING | RUNNING | COMPLETE | FAILED
    risk_rating           VARCHAR(10)   NULL,                         -- LOW | MEDIUM | HIGH
    risk_summary          TEXT          NULL,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ   NULL
);

CREATE INDEX idx_checks_producer    ON verification_checks(producer_id);
CREATE INDEX idx_checks_recycler    ON verification_checks(recycler_id);
CREATE INDEX idx_checks_requested_by ON verification_checks(requested_by_user_id);
CREATE INDEX idx_checks_status      ON verification_checks(status);

-- ─── Evidence ─────────────────────────────────────────────────────────────────
CREATE TABLE evidence (
    id               VARCHAR(36)   PRIMARY KEY,
    check_id         VARCHAR(36)   NOT NULL REFERENCES verification_checks(id),
    file_name        VARCHAR(500)  NOT NULL,
    content_type     VARCHAR(100)  NOT NULL,
    file_size_bytes  BIGINT        NOT NULL,
    storage_path     VARCHAR(1000) NOT NULL,

    -- Extracted metadata
    exif_latitude    DOUBLE PRECISION NULL,
    exif_longitude   DOUBLE PRECISION NULL,
    exif_datetime    TIMESTAMPTZ   NULL,
    exif_device      VARCHAR(255)  NULL,
    pdf_author       VARCHAR(255)  NULL,
    pdf_creator      VARCHAR(255)  NULL,
    pdf_created_at   TIMESTAMPTZ   NULL,
    pdf_modified_at  TIMESTAMPTZ   NULL,
    image_phash      VARCHAR(64)   NULL,   -- perceptual hash for duplicate detection

    -- Forensics result per file
    forensics_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | PASS | FAIL | UNVERIFIABLE
    forensics_notes  TEXT          NULL,

    uploaded_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_evidence_check_id   ON evidence(check_id);
CREATE INDEX idx_evidence_phash      ON evidence(image_phash);
