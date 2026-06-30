-- E-PRid initial schema
-- Creates the eprid schema and compliance_estimates table (Module B — EPR Calculator)

CREATE SCHEMA IF NOT EXISTS eprid;

SET search_path TO eprid;

CREATE TABLE compliance_estimates (
    id                               VARCHAR(36)    PRIMARY KEY,
    battery_category                 VARCHAR(30)    NOT NULL,
    financial_year                   VARCHAR(10)    NOT NULL,
    quantity_placed_tonnes           NUMERIC(12,3)  NOT NULL,
    quantity_already_fulfilled_tonnes NUMERIC(12,3) NOT NULL DEFAULT 0,
    recovery_target_percent          INTEGER        NOT NULL,
    target_tonnes                    NUMERIC(12,3)  NOT NULL,
    shortfall_tonnes                 NUMERIC(12,3)  NOT NULL,
    shortfall_kg                     NUMERIC(15,3)  NOT NULL,
    user_id                          VARCHAR(36)    NULL,
    created_at                       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_compliance_estimates_user_id ON compliance_estimates(user_id);
CREATE INDEX idx_compliance_estimates_created_at ON compliance_estimates(created_at DESC);
