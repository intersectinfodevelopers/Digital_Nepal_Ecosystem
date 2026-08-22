CREATE TABLE IF NOT EXISTS household (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           ward_id UUID NOT NULL REFERENCES ward(id),

                           head_citizen_id UUID REFERENCES citizen(id),

                           house_type VARCHAR(30),
                           construction_type VARCHAR(30),
                           room_count SMALLINT,

                           land_owned BOOLEAN DEFAULT FALSE,
                           land_area_ropani DECIMAL(8,2),
                           land_location VARCHAR(300),

                           electricity VARCHAR(30),
                           water_source VARCHAR(30),
                           sanitation VARCHAR(30),
                           internet_access VARCHAR(30),

                           has_bank_account BOOLEAN DEFAULT FALSE,
                           bank_name VARCHAR(200),

                           monthly_income_band VARCHAR(30),
                           annual_income_band VARCHAR(30),

                           dependent_count SMALLINT,
                           poverty_class VARCHAR(30),

                           created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS disability_profile (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                    citizen_id UUID NOT NULL UNIQUE REFERENCES citizen(id),

                                    disability_type VARCHAR(50) NOT NULL,

                                    severity_body SMALLINT NOT NULL CHECK (severity_body BETWEEN 0 AND 4),
                                    severity_activity SMALLINT NOT NULL CHECK (severity_activity BETWEEN 0 AND 4),
                                    severity_participation SMALLINT NOT NULL CHECK (severity_participation BETWEEN 0 AND 4),

                                    certificate_no VARCHAR(100),
                                    issuing_hospital VARCHAR(300),

                                    certificate_date DATE,
                                    certificate_expiry DATE,

                                    caregiver_id UUID REFERENCES citizen(id),

                                    assistive_device VARCHAR(100),
                                    device_provided BOOLEAN DEFAULT FALSE,
                                    device_provided_date DATE,

                                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE disability_profile
    ADD COLUMN IF NOT EXISTS caregiver_id UUID REFERENCES citizen(id),
    ADD COLUMN IF NOT EXISTS device_provided_date DATE;

CREATE TABLE IF NOT EXISTS sync_conflict_registry (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                        citizen_id UUID NOT NULL REFERENCES citizen(id),
                                        submitting_user_id UUID NOT NULL REFERENCES users(id),

                                        device_id VARCHAR(200) NOT NULL,

                                        server_version INTEGER NOT NULL,
                                        device_version INTEGER NOT NULL,

                                        conflicting_data JSONB NOT NULL,

                                        resolution_status VARCHAR(30) DEFAULT 'PENDING_REVIEW',

                                        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS employment_profile (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                    citizen_id UUID NOT NULL UNIQUE REFERENCES citizen(id),

                                    category VARCHAR(30) NOT NULL,

                                    sub_fields JSONB NOT NULL DEFAULT '{}',

                                    income_band VARCHAR(30),

                                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    updated_by UUID NOT NULL REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS foreign_employment (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                    citizen_id UUID NOT NULL REFERENCES citizen(id),

                                    country_code VARCHAR(5) NOT NULL,
                                    country_name VARCHAR(100) NOT NULL,

                                    visa_type VARCHAR(30) NOT NULL,

                                    employer_name VARCHAR(300),
                                    job_category VARCHAR(50),

                                    departure_date DATE NOT NULL,
                                    expected_return DATE,

                                    remittance_band VARCHAR(20),
                                    remittance_channel VARCHAR(30),

                                    foreign_phone_enc TEXT,

                                    manager_citizen_id UUID REFERENCES citizen(id),

                                    doe_registered BOOLEAN DEFAULT FALSE,
                                    insured BOOLEAN DEFAULT FALSE,

                                    is_active BOOLEAN DEFAULT TRUE,

                                    return_date_actual DATE,
                                    return_reason TEXT,

                                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);