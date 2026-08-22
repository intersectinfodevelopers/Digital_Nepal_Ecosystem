-- Align province_no with the JPA entity definition.
ALTER TABLE province
    ALTER COLUMN province_no TYPE INTEGER;