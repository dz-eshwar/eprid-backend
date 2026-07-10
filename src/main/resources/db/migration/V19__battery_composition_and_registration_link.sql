-- V19: battery composition-table check + hard-disqualification rules 1-3
-- (feature_spec_close_scoring_gaps.md §1, §2)
SET search_path TO eprid;

ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS declared_battery_chemistry VARCHAR(20) NULL;
-- LEAD_ACID | LITHIUM_ION | ZINC_BASED | NICKEL_CADMIUM; NULL for tyre checks or when not declared

ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS certificate_date DATE NULL;
-- Distinct from created_at: the certificate's own effective date, used for point-in-time
-- registration-validity hard-disqualification (rule 1). Falls back to processing_date when null.

CREATE TABLE IF NOT EXISTS claimed_metal_recoveries (
    id                VARCHAR(36)   PRIMARY KEY,
    check_id          VARCHAR(36)   NOT NULL REFERENCES verification_checks(id),
    metal             VARCHAR(5)    NOT NULL,   -- BatteryMetal enum name, e.g. PB, LI, MN
    claimed_weight_kg NUMERIC(12,3) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_claimed_metal_recoveries_check_id ON claimed_metal_recoveries(check_id);

CREATE TABLE IF NOT EXISTS metal_composition_checks (
    id            VARCHAR(36)   PRIMARY KEY,
    check_id      VARCHAR(36)   NOT NULL REFERENCES verification_checks(id),
    metal         VARCHAR(5)    NOT NULL,
    claimed_pct   NUMERIC(6,3)  NULL,       -- null when no claimed weight was submitted for this metal
    expected_min  NUMERIC(6,3)  NOT NULL,
    expected_max  NUMERIC(6,3)  NOT NULL,
    result        VARCHAR(20)   NOT NULL,   -- PASS | FAIL | ZERO_CELL_VIOLATION | COULD_NOT_VERIFY
    detail        TEXT          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_metal_composition_checks_check_id ON metal_composition_checks(check_id);

-- Recycler <-> CPCB directory link (rules 1 & 2's shared prerequisite). Nullable, manually confirmed
-- via CpcbRecyclerLinkService — GST format/normalization between the two tables isn't confirmed
-- clean (open item), so this is never auto-populated by a bare join.
ALTER TABLE recyclers ADD COLUMN IF NOT EXISTS gst_number VARCHAR(20) NULL;
ALTER TABLE recyclers ADD COLUMN IF NOT EXISTS cpcb_recycler_id VARCHAR(36) NULL REFERENCES cpcb_recyclers(id);

CREATE INDEX IF NOT EXISTS idx_recyclers_cpcb_recycler_id ON recyclers(cpcb_recycler_id);
