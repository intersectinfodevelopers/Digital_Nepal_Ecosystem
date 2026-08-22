CREATE INDEX IF NOT EXISTS idx_sync_conflict_citizen_id
    ON sync_conflict_registry (citizen_id);

CREATE INDEX IF NOT EXISTS idx_sync_conflict_resolution_status
    ON sync_conflict_registry (resolution_status);