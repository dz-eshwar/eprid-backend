-- V12: CPCB battery-recycler public directory (Entity Health Score) — a separate concept from
-- `recyclers` (which holds producer-created/user-upserted recyclers tied to a VerificationCheck).
-- This is CPCB's own public registry, ingested from a CSV snapshot of the unauthenticated
-- eprbattery.cpcb.gov.in DataTables endpoint. See product_document_built_state.md for scope notes:
-- this table only supports an "Entity Health Score" (registration/authorization/geography) — it
-- deliberately has no columns for certificate volume, Form 4 input weight, or invoice data, since
-- that data isn't available from this source. Add those later without needing to touch this table.
SET search_path TO eprid;

CREATE TABLE cpcb_recyclers (
    id                      VARCHAR(36)   PRIMARY KEY,
    -- CPCB's own row id/uuid — nullable because some captured rows (e.g. a partial-capture
    -- artifact during research) have no source id at all. NULL != 0 rows sharing an id: Postgres
    -- unique constraints allow multiple NULLs, so this is safe as a soft dedupe key.
    cpcb_id                 TEXT          NULL,
    cpcb_uuid               TEXT          NULL,

    recycler_name           TEXT          NOT NULL,
    recycler_address        TEXT          NULL,
    state_id                TEXT          NULL,   -- raw CPCB numeric state code, no name mapping table yet

    recycler_gst_no         TEXT          NULL,   -- number only, not verified active/filing (needs GST Sandbox API, not built)

    consent_air_expiry      DATE          NULL,   -- SPCB Consent-to-Operate (air)
    consent_water_expiry    DATE          NULL,   -- SPCB Consent-to-Operate (water)
    hwmd_valid_expiry       DATE          NULL,   -- Hazardous Waste Management authorization
    dic_valid_expiry        DATE          NULL,   -- District Industries Centre validity ("varies" coverage even at source)

    recycler_type_raw       TEXT          NULL,   -- verbatim CPCB string, '#,'-joined multi-category
    -- recycling_capacity units are NOT confirmed against CPCB documentation (likely MT/year given
    -- observed value ranges, e.g. Attero 7,380 vs Gravita facilities 40,000-84,000) — treat any
    -- absolute threshold applied to this column as a raw-number comparison, not unit-aware.
    recycling_capacity      NUMERIC(14,2) NULL,

    latitude                NUMERIC(10,6) NULL,
    longitude               NUMERIC(10,6) NULL,

    -- PII of an individual, not the company. Never surface in customer-facing search/score
    -- responses by default — see CpcbRecyclerSearchService.
    authorized_name         TEXT          NULL,
    authorized_email        TEXT          NULL,
    authorized_mobile       TEXT          NULL,

    source_created_at       TIMESTAMPTZ   NULL,   -- CPCB's own created_at for this row, if captured
    certificate_flag        TEXT          NULL,   -- raw 'certificate' column value as captured (meaning not decoded)
    staff_no                INTEGER       NULL,   -- NULL = not captured; 0 = confirmed zero. Do not conflate.
    worker_no               INTEGER       NULL,
    inspection_status       INTEGER       NULL,   -- CPCB's own internal status code — meaning not decoded, stored raw only
    internal_app_status     INTEGER       NULL,   -- same

    -- True when this row is missing enough of its optional fields that a blank shouldn't be read
    -- as "confirmed absent" (e.g. a large legitimate player captured without registration dates in
    -- this particular query pass). See CpcbRecyclerIngestionService for the exact heuristic.
    data_quality_partial_capture BOOLEAN  NOT NULL DEFAULT false,
    data_quality_notes      TEXT          NULL,

    ingested_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    source                  TEXT          NOT NULL DEFAULT 'cpcb_battery_recyclerview',

    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_cpcb_recyclers_cpcb_id ON cpcb_recyclers(cpcb_id) WHERE cpcb_id IS NOT NULL;
CREATE INDEX idx_cpcb_recyclers_name ON cpcb_recyclers(recycler_name);
CREATE INDEX idx_cpcb_recyclers_gst ON cpcb_recyclers(recycler_gst_no);
CREATE INDEX idx_cpcb_recyclers_state ON cpcb_recyclers(state_id);

-- Parsed R1-R4 chemistry/process authorization categories, normalized out of recycler_type_raw so
-- "is this recycler authorized for lead-acid" is a real query, not a string-contains on a blob.
CREATE TABLE cpcb_recycler_authorizations (
    id              VARCHAR(36)  PRIMARY KEY,
    recycler_id     VARCHAR(36)  NOT NULL REFERENCES cpcb_recyclers(id) ON DELETE CASCADE,
    category_code   TEXT         NOT NULL,   -- e.g. 'R1', 'R2' — 'UNKNOWN' if the source string didn't match the expected 'Rn: ...' shape
    category_label  TEXT         NOT NULL
);

CREATE INDEX idx_cpcb_recycler_auth_recycler ON cpcb_recycler_authorizations(recycler_id);
CREATE INDEX idx_cpcb_recycler_auth_code ON cpcb_recycler_authorizations(category_code);

-- History preserved (one row per scoring run), same convention as recycler_credential_checks —
-- query the latest by recycler_id ORDER BY scored_at DESC rather than overwriting in place.
CREATE TABLE cpcb_recycler_scores (
    id                  VARCHAR(36)  PRIMARY KEY,
    recycler_id         VARCHAR(36)  NOT NULL REFERENCES cpcb_recyclers(id) ON DELETE CASCADE,
    composite_score     INTEGER      NOT NULL,
    risk_band           VARCHAR(10)  NOT NULL,   -- LOW | MEDIUM | HIGH | CRITICAL
    flags               TEXT         NOT NULL,   -- JSON array of human-readable confirmed-issue strings
    unassessed           TEXT        NOT NULL,   -- JSON array of signals that couldn't be checked (missing data, not a clean pass)
    layer_breakdown     TEXT         NOT NULL,   -- JSON object: per-layer points + reasoning — JSON (not columns) so
                                                  -- Layer 2 (yield) / Layer 4 (invoice) can be added later without a migration
    -- 'entity_health' today (registration/authorization/geography only); reserved 'certificate_risk'
    -- for when Layer 2/4 data (certificate volume, invoice traceability) actually exists.
    score_confidence    VARCHAR(20)  NOT NULL DEFAULT 'entity_health',
    scored_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_cpcb_recycler_scores_recycler ON cpcb_recycler_scores(recycler_id, scored_at DESC);

-- Static, editable reference list — battery-specific geographic risk hotspots from E-PRid's
-- existing risk-methodology research (not derived from the CPCB feed itself). Point + radius is a
-- coarse proxy for a district, not real boundary data; update lat/lon/radius as better sourcing
-- turns up (the PRD separately downgraded the Alwar/Haryana rows' evidentiary specifics to a more
-- general 2019 NGT closure order — kept here as-is per this task's explicit spec).
CREATE TABLE cpcb_geo_risk_hotspots (
    id              VARCHAR(36)   PRIMARY KEY,
    location_name   TEXT          NOT NULL,
    risk_level      TEXT          NOT NULL,
    points          INTEGER       NOT NULL,
    latitude        NUMERIC(10,6) NOT NULL,
    longitude       NUMERIC(10,6) NOT NULL,
    radius_km       NUMERIC(6,2)  NOT NULL
);

-- Literal UUIDs (not gen_random_uuid()) — avoids depending on pgcrypto being available/enabled.
INSERT INTO cpcb_geo_risk_hotspots (id, location_name, risk_level, points, latitude, longitude, radius_km) VALUES
    ('a1e1c1a0-0001-4a00-9a00-000000000001', 'Morena / Lohgarh, Madhya Pradesh',       'Critical',     40, 26.5000, 78.0000, 40),
    ('a1e1c1a0-0001-4a00-9a00-000000000002', 'Kalapipal / Shahpur, Madhya Pradesh',    'Critical',     40, 23.4000, 76.3000, 40),
    ('a1e1c1a0-0001-4a00-9a00-000000000003', 'Alwar, Rajasthan',                       'High',         25, 27.5665, 76.6250, 40),
    ('a1e1c1a0-0001-4a00-9a00-000000000004', 'Haryana industrial belt',                'High',         25, 28.4100, 77.3100, 80),
    ('a1e1c1a0-0001-4a00-9a00-000000000005', 'Shamli / Muzaffarnagar, Uttar Pradesh',  'Medium-High',  15, 29.4727, 77.7085, 40);
