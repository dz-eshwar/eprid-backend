-- V8: Waste-stream discriminator (Module D — tyre/TPO). Additive, backward-compatible with battery.
SET search_path TO eprid;

ALTER TABLE producers ADD COLUMN IF NOT EXISTS waste_stream VARCHAR(20) NOT NULL DEFAULT 'BATTERY';
ALTER TABLE recyclers ADD COLUMN IF NOT EXISTS waste_stream VARCHAR(20) NOT NULL DEFAULT 'BATTERY';
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS waste_stream VARCHAR(20) NOT NULL DEFAULT 'BATTERY';

-- Tyre-specific: claimed TPO (Tyre Pyrolysis Oil) output in litres. Null for battery checks.
ALTER TABLE verification_checks ADD COLUMN IF NOT EXISTS claimed_output_quantity NUMERIC(12,3) NULL;

CREATE INDEX IF NOT EXISTS idx_producers_waste_stream ON producers(waste_stream);
CREATE INDEX IF NOT EXISTS idx_recyclers_waste_stream ON recyclers(waste_stream);
CREATE INDEX IF NOT EXISTS idx_verification_checks_waste_stream ON verification_checks(waste_stream);
