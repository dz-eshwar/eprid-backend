-- V11: Composite risk scoring + hard-disqualification (PRD §7.1a). Additive, backward-compatible —
-- existing checks simply have compositeScore=NULL until re-run through the wizard.
SET search_path TO eprid;

ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS composite_score INTEGER NULL;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS registration_sub_score INTEGER NULL;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS capacity_sub_score INTEGER NULL;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS invoice_sub_score INTEGER NULL;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS forensics_sub_score INTEGER NULL;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS regulatory_sub_score INTEGER NULL;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS hard_disqualified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS hard_disqualification_reason TEXT NULL;
