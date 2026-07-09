-- V17: cpcb_state_codes — maps cpcb_recyclers.state_id's raw CPCB numeric codes to real state
-- names, so the directory can be searched by state name instead of an opaque number. V12's own
-- comment already flagged this gap ("raw CPCB numeric state code, no name mapping table yet").
--
-- Not CPCB-published. These values don't match standard GST state codes either (checked:
-- state_id 7 below is Himachal Pradesh, but GST code 07 is Delhi — different scheme entirely).
-- Inferred by cross-referencing each state_id against district/industrial-area names in that
-- code's cpcb_recyclers.recycler_address values across the 553-row 2026-07-08 pull. Only covers
-- state_ids actually observed in that pull (21 of them) — a future re-pull with new state_ids
-- won't have a name for them until this table is extended.
SET search_path TO eprid;

CREATE TABLE cpcb_state_codes (
    state_id    TEXT PRIMARY KEY,
    state_name  TEXT NOT NULL
);

INSERT INTO cpcb_state_codes (state_id, state_name) VALUES
    ('1',  'Andhra Pradesh'),
    ('2',  'Assam'),
    ('4',  'Bihar'),
    ('5',  'Gujarat'),
    ('6',  'Haryana'),
    ('7',  'Himachal Pradesh'),
    ('8',  'Jammu & Kashmir'),
    ('9',  'Karnataka'),
    ('10', 'Kerala'),
    ('11', 'Madhya Pradesh'),
    ('12', 'Maharashtra'),
    ('14', 'Meghalaya'),
    ('18', 'Punjab'),
    ('19', 'Rajasthan'),
    ('21', 'Tamil Nadu'),
    ('23', 'Uttar Pradesh'),
    ('24', 'West Bengal'),
    ('33', 'Uttarakhand'),
    ('35', 'Chhattisgarh'),
    ('36', 'Telangana');
    -- state_id 25 deliberately excluded: its only occupant in the 2026-07-08 pull is the known
    -- CPCB test/placeholder row (cpcb_id 1003, "RELIANCE INDUSTRIES LIMITED", address "ABC") —
    -- not enough real signal to infer a state from.
