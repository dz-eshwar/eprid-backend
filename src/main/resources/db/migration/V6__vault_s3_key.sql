-- V6: Module C1 — migrate vault document storage from local disk to S3
SET search_path TO eprid;

ALTER TABLE vault_documents RENAME COLUMN storage_path TO s3_key;
