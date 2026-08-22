ALTER TABLE grievance
    ADD COLUMN IF NOT EXISTS municipality_id UUID REFERENCES municipality(id);

-- Index for same-municipality escalation query
CREATE INDEX IF NOT EXISTS idx_grievance_municipality
    ON grievance(municipality_id);