-- V16: ScoreConfidence.ENTITY_HEALTH renamed to ENTITY_RISK — "health score" reads as
-- higher-is-better, which is backwards for a field where higher composite_score means riskier,
-- not healthier. CERTIFICATE_RISK (still reserved, not yet built) already used the correct
-- "_RISK" convention; this just brings the entity-level score's naming in line with it.
-- Hibernate stores enum names in uppercase (EnumType.STRING) — existing rows have literal
-- 'ENTITY_HEALTH', not V12's unused lowercase 'entity_health' default.
SET search_path TO eprid;

UPDATE cpcb_recycler_scores
SET score_confidence = 'ENTITY_RISK'
WHERE score_confidence = 'ENTITY_HEALTH';

ALTER TABLE cpcb_recycler_scores
    ALTER COLUMN score_confidence SET DEFAULT 'entity_risk';
