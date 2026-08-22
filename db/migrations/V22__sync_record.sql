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
    CONSTRAINT fk_sync_record_batch
        FOREIGN KEY (batch_id) REFERENCES sync_batch(batch_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_record_batch ON sync_record(batch_id);
CREATE INDEX IF NOT EXISTS idx_sync_record_status ON sync_record(status);
CREATE INDEX IF NOT EXISTS idx_sync_record_retry ON sync_record(retry_count);
CREATE INDEX IF NOT EXISTS idx_sync_record_version ON sync_record(version_number);