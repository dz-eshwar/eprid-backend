-- V20: widen metal_composition_checks.claimed_pct precision — the percentage-calculation bug fix
-- in BatteryCompositionCheckService (dividing after multiplying by 100, not before) needs a column
-- that can hold 4 decimal places instead of 3 (claimed weight is kg against a tonnes-scale batch,
-- so realistic percentages are frequently sub-0.01%).
SET search_path TO eprid;

ALTER TABLE metal_composition_checks ALTER COLUMN claimed_pct TYPE NUMERIC(9,4);
