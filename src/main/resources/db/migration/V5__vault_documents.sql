-- V5: Module C1 — recycler document vault (standalone, no evidence-linkage at MVP)
SET search_path TO eprid;

CREATE TABLE vault_documents (
    id                  VARCHAR(36)   PRIMARY KEY,
    recycler_id         VARCHAR(36)   NOT NULL REFERENCES recyclers(id),
    user_id             VARCHAR(36)   NOT NULL REFERENCES users(id),

    doc_type            VARCHAR(50)   NOT NULL,   -- REGISTRATION_CERT | GST_CERT | PROCESSING_RECEIPT | OTHER
    display_name        VARCHAR(500)  NOT NULL,
    file_name           VARCHAR(500)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    file_size_bytes     BIGINT        NOT NULL,
    storage_path        VARCHAR(1000) NOT NULL,

    -- Consent — must be accepted before first upload (DPDP compliance)
    consent_accepted_at TIMESTAMPTZ   NOT NULL,

    notes               TEXT          NULL,
    uploaded_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ   NULL      -- soft-delete
);

CREATE INDEX idx_vault_docs_recycler ON vault_documents(recycler_id);
CREATE INDEX idx_vault_docs_user     ON vault_documents(user_id);
CREATE INDEX idx_vault_docs_active   ON vault_documents(recycler_id) WHERE deleted_at IS NULL;
