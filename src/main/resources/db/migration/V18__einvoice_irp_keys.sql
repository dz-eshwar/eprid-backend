-- V18: e-invoice QR originality check (PRD §7.1, Tier 1) — trusted IRP public keys + Evidence result field
SET search_path TO eprid;

CREATE TABLE IF NOT EXISTS irp_public_keys (
    id           VARCHAR(36)  PRIMARY KEY,
    irp          VARCHAR(20)  NOT NULL,   -- EInvoiceIrp enum name, e.g. IRP1_NIC
    key_id       VARCHAR(64)  NULL,       -- JWS "kid" this cert corresponds to, when known
    cert_pem     TEXT         NOT NULL,
    not_before   TIMESTAMPTZ  NULL,
    not_after    TIMESTAMPTZ  NULL,       -- read from the certificate itself, never from page text
    source_url   VARCHAR(500) NOT NULL,
    fetch_status VARCHAR(20)  NOT NULL,   -- FETCHED | MANUAL_SEED | FETCH_FAILED
    active       BOOLEAN      NOT NULL DEFAULT true,
    fetched_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_irp_public_keys_irp        ON irp_public_keys(irp) WHERE active;
CREATE INDEX IF NOT EXISTS idx_irp_public_keys_key_id     ON irp_public_keys(key_id);

ALTER TABLE evidence ADD COLUMN IF NOT EXISTS invoice_qr_status VARCHAR(20) NULL;
-- VALID | INVALID | COULD_NOT_VERIFY | NOT_APPLICABLE; NULL means this evidence isn't an INVOICE
