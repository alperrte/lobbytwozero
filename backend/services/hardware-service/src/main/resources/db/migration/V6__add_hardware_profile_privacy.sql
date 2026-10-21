ALTER TABLE hardware_profiles ADD COLUMN visibility varchar(16) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE hardware_profiles ADD CONSTRAINT ck_hardware_profiles_visibility
    CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC'));

-- Aggregate version and pessimistic parent lock protect both rule collections.
CREATE TABLE hardware_profile_field_visibility (
    profile_id uuid NOT NULL REFERENCES hardware_profiles(id) ON DELETE CASCADE,
    field_name varchar(32) NOT NULL,
    visibility varchar(16) NOT NULL,
    PRIMARY KEY (profile_id, field_name),
    CHECK (field_name IN ('PROFILE_NAME', 'DEVICE_TYPE', 'COMPONENT_MODEL',
        'COMPONENT_BRAND', 'COMPONENT_QUANTITY', 'TECHNICAL_SPECIFICATIONS')),
    CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC'))
);
CREATE TABLE hardware_profile_category_visibility (
    profile_id uuid NOT NULL REFERENCES hardware_profiles(id) ON DELETE CASCADE,
    category varchar(32) NOT NULL,
    visibility varchar(16) NOT NULL,
    PRIMARY KEY (profile_id, category),
    CHECK (category IN ('CPU', 'GPU', 'RAM', 'MOTHERBOARD', 'STORAGE', 'COOLING',
        'PSU', 'MONITOR', 'KEYBOARD', 'MOUSE', 'HEADSET', 'OTHER')),
    CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC'))
);
CREATE INDEX ix_hardware_components_profile_category
    ON hardware_profile_components(profile_id, category, created_at, id);
