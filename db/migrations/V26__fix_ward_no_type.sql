-- Align ward_no with the JPA entity definition.
ALTER TABLE ward
    ALTER COLUMN ward_no TYPE INTEGER;