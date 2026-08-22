-- Digital Nepal Ecosystem - Initial Database Schema
-- PostgreSQL 16 + PostGIS
-- Created: 2026-05-29
-- Version: 1.0

-- ============================================================================
-- EXTENSIONS
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- SCHEMAS
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS citizen_registry;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS employment;
CREATE SCHEMA IF NOT EXISTS health;
CREATE SCHEMA IF NOT EXISTS education;
CREATE SCHEMA IF NOT EXISTS id_management;
CREATE SCHEMA IF NOT EXISTS reporting;

-- ============================================================================
-- CITIZEN REGISTRY SCHEMA
-- ============================================================================

-- Citizens table with geographic data (PostGIS)
CREATE TABLE IF NOT EXISTS citizen_registry.citizens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender CHAR(1) NOT NULL CHECK (gender IN ('M', 'F', 'O')),
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    current_location GEOGRAPHY(POINT, 4326),
    permanent_address TEXT,
    temporary_address TEXT,
    citizenship_number VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(50) DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'suspended', 'deceased')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);

-- Create index on geography column for efficient location queries
CREATE INDEX idx_citizens_location ON citizen_registry.citizens USING GIST(current_location);
CREATE INDEX idx_citizens_citizenship_number ON citizen_registry.citizens(citizenship_number);
CREATE INDEX idx_citizens_email ON citizen_registry.citizens(email);
CREATE INDEX idx_citizens_status ON citizen_registry.citizens(status);

-- ============================================================================
-- AUTHENTICATION SCHEMA
-- ============================================================================

-- Users table
CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    citizen_id UUID NOT NULL REFERENCES citizen_registry.citizens(id) ON DELETE CASCADE,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    account_status VARCHAR(50) DEFAULT 'active' CHECK (account_status IN ('active', 'inactive', 'locked', 'suspended')),
    login_attempts INT DEFAULT 0,
    last_login TIMESTAMP,
    password_changed_at TIMESTAMP,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON auth.users(username);
CREATE INDEX idx_users_email ON auth.users(email);
CREATE INDEX idx_users_account_status ON auth.users(account_status);

-- Roles table
CREATE TABLE IF NOT EXISTS auth.roles (
    id SERIAL PRIMARY KEY,
    role_name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User roles junction table
CREATE TABLE IF NOT EXISTS auth.user_roles (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES auth.roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, role_id)
);

-- ============================================================================
-- EMPLOYMENT SCHEMA
-- ============================================================================

CREATE TABLE IF NOT EXISTS employment.employment_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    citizen_id UUID NOT NULL REFERENCES citizen_registry.citizens(id) ON DELETE CASCADE,
    employer_name VARCHAR(255) NOT NULL,
    job_title VARCHAR(150) NOT NULL,
    employment_type VARCHAR(50) NOT NULL CHECK (employment_type IN ('full-time', 'part-time', 'contract', 'self-employed')),
    start_date DATE NOT NULL,
    end_date DATE,
    is_current BOOLEAN DEFAULT TRUE,
    location GEOGRAPHY(POINT, 4326),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employment_citizen ON employment.employment_records(citizen_id);
CREATE INDEX idx_employment_is_current ON employment.employment_records(is_current);

-- ============================================================================
-- HEALTH SCHEMA
-- ============================================================================

CREATE TABLE IF NOT EXISTS health.health_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    citizen_id UUID NOT NULL REFERENCES citizen_registry.citizens(id) ON DELETE CASCADE,
    blood_type VARCHAR(10),
    allergies TEXT,
    medical_conditions TEXT,
    medications TEXT,
    emergency_contact_name VARCHAR(150),
    emergency_contact_phone VARCHAR(20),
    last_checkup_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_health_citizen ON health.health_records(citizen_id);

CREATE TABLE IF NOT EXISTS health.medical_visits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    health_record_id UUID NOT NULL REFERENCES health.health_records(id) ON DELETE CASCADE,
    visit_date DATE NOT NULL,
    healthcare_facility VARCHAR(255),
    reason_for_visit TEXT,
    diagnosis TEXT,
    treatment TEXT,
    doctor_name VARCHAR(150),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- EDUCATION SCHEMA
-- ============================================================================

CREATE TABLE IF NOT EXISTS education.education_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    citizen_id UUID NOT NULL REFERENCES citizen_registry.citizens(id) ON DELETE CASCADE,
    institution_name VARCHAR(255) NOT NULL,
    level VARCHAR(100) NOT NULL CHECK (level IN ('primary', 'secondary', 'bachelor', 'master', 'phd')),
    field_of_study VARCHAR(150),
    start_date DATE NOT NULL,
    end_date DATE,
    grade_obtained VARCHAR(10),
    is_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_education_citizen ON education.education_records(citizen_id);

-- ============================================================================
-- ID MANAGEMENT SCHEMA
-- ============================================================================

CREATE TABLE IF NOT EXISTS id_management.national_ids (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    citizen_id UUID NOT NULL UNIQUE REFERENCES citizen_registry.citizens(id) ON DELETE CASCADE,
    id_number VARCHAR(50) UNIQUE NOT NULL,
    issue_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    issuing_authority VARCHAR(255),
    status VARCHAR(50) DEFAULT 'active' CHECK (status IN ('active', 'expired', 'suspended', 'revoked')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_national_ids_id_number ON id_management.national_ids(id_number);
CREATE INDEX idx_national_ids_status ON id_management.national_ids(status);

-- ============================================================================
-- REPORTING SCHEMA
-- ============================================================================

CREATE TABLE IF NOT EXISTS reporting.population_statistics (
    id SERIAL PRIMARY KEY,
    region_location GEOGRAPHY(POLYGON, 4326),
    region_name VARCHAR(100),
    total_citizens INT DEFAULT 0,
    males INT DEFAULT 0,
    females INT DEFAULT 0,
    others INT DEFAULT 0,
    avg_age DECIMAL(5, 2),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_statistics_location ON reporting.population_statistics USING GIST(region_location);

CREATE TABLE IF NOT EXISTS reporting.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id),
    action VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100),
    entity_id UUID,
    changes JSONB,
    ip_address INET,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user ON reporting.audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON reporting.audit_logs(created_at);
CREATE INDEX idx_audit_logs_entity ON reporting.audit_logs(entity_type, entity_id);

-- ============================================================================
-- AUDIT TRIGGERS
-- ============================================================================

-- Update timestamp on citizen update
CREATE OR REPLACE FUNCTION citizen_registry.update_citizens_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER citizens_update_timestamp
BEFORE UPDATE ON citizen_registry.citizens
FOR EACH ROW
EXECUTE FUNCTION citizen_registry.update_citizens_timestamp();

-- Similar trigger for auth.users
CREATE OR REPLACE FUNCTION auth.update_users_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_update_timestamp
BEFORE UPDATE ON auth.users
FOR EACH ROW
EXECUTE FUNCTION auth.update_users_timestamp();

-- ============================================================================
-- INSERT DEFAULT DATA
-- ============================================================================

-- Insert default roles
INSERT INTO auth.roles (role_name, description) VALUES
('ADMIN', 'Administrator with full access'),
('OFFICER', 'Government officer'),
('CITIZEN', 'Regular citizen user'),
('AUDIT', 'Audit and compliance officer')
ON CONFLICT (role_name) DO NOTHING;

-- ============================================================================
-- GRANT PERMISSIONS
-- ============================================================================

-- Grant usage on all schemas
GRANT USAGE ON SCHEMA citizen_registry, auth, employment, health, education, id_management, reporting TO public;

-- Grant select on all tables
GRANT SELECT ON ALL TABLES IN SCHEMA citizen_registry, auth, employment, health, education, id_management, reporting TO public;

COMMIT;
