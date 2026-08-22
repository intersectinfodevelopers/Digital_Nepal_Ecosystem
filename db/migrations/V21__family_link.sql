-- Family tree linking table

CREATE TABLE IF NOT EXISTS family_link (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id              UUID        NOT NULL REFERENCES citizen(id),
    relation_type           VARCHAR(30) NOT NULL,
    related_citizen_id      UUID        REFERENCES citizen(id),
    related_name_text       VARCHAR(300),
    related_citizenship_no  VARCHAR(100),
    link_status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_family_link_citizen ON family_link(citizen_id);
CREATE INDEX IF NOT EXISTS idx_family_link_related ON family_link(related_citizen_id);
CREATE INDEX IF NOT EXISTS idx_family_link_cit_norm ON family_link(related_citizenship_no)
    WHERE link_status = 'PENDING';