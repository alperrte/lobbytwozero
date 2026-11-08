-- V193 explicitly allocated to Hardware by the user (2026-11-08); V1-V10, V191 and V192 remain unchanged.
-- LTZ Atölyesi: each computer profile stores the chosen case colour. Existing profiles default to BLACK.
ALTER TABLE hardware_profiles ADD COLUMN case_color varchar(16) NOT NULL DEFAULT 'BLACK';
ALTER TABLE hardware_profiles ADD CONSTRAINT ck_hardware_profiles_case_color
    CHECK (case_color IN ('BLACK', 'WHITE', 'GRAPHITE', 'RED'));
