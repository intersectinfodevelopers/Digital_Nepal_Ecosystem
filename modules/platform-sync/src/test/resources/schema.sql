CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sync_batch (
                                          batch_id UUID PRIMARY KEY,
                                          ward_id UUID NOT NULL,
                                          submitted_by UUID NOT NULL,
                                          device_id VARCHAR(200) NOT NULL,
    record_count INTEGER NOT NULL DEFAULT 0,
    conflict_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_message TEXT
    );

CREATE TABLE IF NOT EXISTS sync_record (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL,
    local_record_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,

    CONSTRAINT fk_test_sync_record_batch
    FOREIGN KEY (batch_id)
    REFERENCES sync_batch(batch_id)
    ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_test_sync_record_batch
    ON sync_record(batch_id);

CREATE INDEX IF NOT EXISTS idx_test_sync_record_status
    ON sync_record(status);