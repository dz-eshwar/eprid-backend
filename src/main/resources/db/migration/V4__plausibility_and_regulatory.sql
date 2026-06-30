SET search_path TO eprid;

-- ─── Plausibility check results (one per verification check) ─────────────────
CREATE TABLE plausibility_checks (
    id                          VARCHAR(36)    PRIMARY KEY,
    check_id                    VARCHAR(36)    NOT NULL UNIQUE REFERENCES verification_checks(id),

    -- Recovery rate sub-check
    claimed_recovery_pct        NUMERIC(5,2)   NOT NULL,
    recovery_status             VARCHAR(20)    NOT NULL,
    recovery_detail             TEXT           NOT NULL,

    -- Capacity ceiling sub-check
    recycler_annual_capacity_t  NUMERIC(12,3)  NULL,
    batch_to_capacity_ratio     NUMERIC(7,4)   NULL,
    capacity_status             VARCHAR(20)    NOT NULL,
    capacity_detail             TEXT           NOT NULL,

    -- Absolute batch size sub-check
    batch_weight_t              NUMERIC(12,3)  NOT NULL,
    batch_size_status           VARCHAR(20)    NOT NULL,
    batch_size_detail           TEXT           NOT NULL,

    overall_status              VARCHAR(20)    NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ─── Regulatory findings (one or more per recycler per check) ────────────────
CREATE TABLE regulatory_findings (
    id            VARCHAR(36)   PRIMARY KEY,
    recycler_id   VARCHAR(36)   NOT NULL REFERENCES recyclers(id),
    check_id      VARCHAR(36)   NULL     REFERENCES verification_checks(id),
    source        VARCHAR(50)   NOT NULL,   -- CPCB | NGT | SPCB | NEWS | CLAUDE_ANALYSIS
    finding_type  VARCHAR(50)   NOT NULL,   -- ENFORCEMENT_NOTICE | COURT_ORDER | SUSPENSION | NEWS_MENTION | NO_RECORD
    severity      VARCHAR(10)   NOT NULL,   -- HIGH | MEDIUM | LOW | INFO
    title         TEXT          NOT NULL,
    summary       TEXT          NOT NULL,
    url           TEXT          NULL,
    finding_date  DATE          NULL,
    confidence    VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',  -- HIGH | MEDIUM | LOW
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Track the overall regulatory research status per check
ALTER TABLE verification_checks
    ADD COLUMN regulatory_status  VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN regulatory_risk    VARCHAR(10) NULL,
    ADD COLUMN regulatory_summary TEXT        NULL;

CREATE INDEX idx_regulatory_findings_recycler ON regulatory_findings(recycler_id);
CREATE INDEX idx_regulatory_findings_check    ON regulatory_findings(check_id);
