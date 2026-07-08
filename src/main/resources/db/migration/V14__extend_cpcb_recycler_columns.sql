-- V14: extend cpcb_recyclers with the remaining columns from CPCB's full 41-column export
-- (recycler_web_address/phone, authorized_phone, installed/operating dates, ISO 9001/14001 and
-- APCM/WPCM upload flags, ApplicationStatus/PaymentStatus, certificate_no/certificate_date,
-- source's own updated_at, and the MRAI/SOP/ESG/website fields). V12 only modeled the subset
-- needed for Entity Health Score; this brings the schema up to the full CPCB contract so a
-- future re-pull that actually populates these fields doesn't need another migration.
--
-- As of the 2026-07-08 full pull (553 rows, cpcb_battery_recyclers_COMPLETE_2026-07-08.csv):
-- every column added here except application_status/payment_status/certificate_no/certificate_date
-- is 100% blank at source (verified against all 553 rows) — stored raw for when CPCB starts
-- populating them, not scored on until then. See CpcbRecyclerScoring's isoBothOnFile comment.
SET search_path TO eprid;

ALTER TABLE cpcb_recyclers
    ADD COLUMN recycler_web_address TEXT        NULL,
    ADD COLUMN recycler_phone_no    TEXT        NULL,
    ADD COLUMN authorized_phone     TEXT        NULL,
    ADD COLUMN installed_date       DATE        NULL,
    ADD COLUMN operating_date       DATE        NULL,
    ADD COLUMN iso_9001_upload      BOOLEAN     NULL,  -- NULL = not captured, distinct from confirmed-false
    ADD COLUMN iso_14001_upload     BOOLEAN     NULL,
    ADD COLUMN apcm_upload          BOOLEAN     NULL,
    ADD COLUMN wpcm_upload          BOOLEAN     NULL,
    ADD COLUMN application_status   INTEGER     NULL,  -- raw CPCB code, meaning not decoded — same treatment as inspection_status
    ADD COLUMN payment_status       INTEGER     NULL,
    ADD COLUMN certificate_no       TEXT        NULL,
    ADD COLUMN certificate_date     DATE        NULL,
    ADD COLUMN source_updated_at    TIMESTAMPTZ NULL,
    ADD COLUMN mrai_memb            BOOLEAN     NULL,
    ADD COLUMN sop_recycling        BOOLEAN     NULL,
    ADD COLUMN esg_policy           BOOLEAN     NULL,
    ADD COLUMN website_link         TEXT        NULL;
