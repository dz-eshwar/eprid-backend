-- V10: Tyre EPR credit reconciliation fields (Module D) — supports QEPR = QP × CF × WP,
-- replacing the earlier unverified TPO yield-ratio benchmark. Additive, backward-compatible.
SET search_path TO eprid;

ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS tyre_end_product VARCHAR(30) NULL;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS tyre_imported BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS claimed_epr_credit_kg NUMERIC(12,3) NULL;
