-- Development schema baseline: initialize an empty hardware_db.
-- External user, actor and device UUIDs are logical references, never cross-service foreign keys.

CREATE TABLE hardware_profiles (
    id uuid CONSTRAINT pk_hardware_profiles PRIMARY KEY,
    user_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    device_type varchar(32) NOT NULL,
    source varchar(32) NOT NULL DEFAULT 'MANUAL',
    visibility varchar(16) NOT NULL DEFAULT 'PRIVATE',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_hardware_profiles_name CHECK (length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_hardware_profiles_device_type CHECK (device_type IN ('DESKTOP', 'LAPTOP', 'OTHER')),
    CONSTRAINT ck_hardware_profiles_source CHECK (source = 'MANUAL'),
    CONSTRAINT ck_hardware_profiles_version CHECK (version >= 0),
    CONSTRAINT ck_hardware_profiles_visibility CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC'))
);

CREATE INDEX ix_hardware_profiles_user_created ON hardware_profiles (user_id, created_at, id);

CREATE TABLE hardware_profile_field_visibility (
    profile_id uuid NOT NULL CONSTRAINT fk_hardware_profile_field_visibility_profile REFERENCES hardware_profiles(id) ON DELETE CASCADE,
    field_name varchar(32) NOT NULL,
    visibility varchar(16) NOT NULL,
    CONSTRAINT pk_hardware_profile_field_visibility PRIMARY KEY (profile_id, field_name),
    CONSTRAINT ck_hardware_profile_field_visibility_field_name CHECK (field_name IN ('PROFILE_NAME', 'DEVICE_TYPE', 'COMPONENT_MODEL',
        'COMPONENT_BRAND', 'COMPONENT_QUANTITY', 'TECHNICAL_SPECIFICATIONS')),
    CONSTRAINT ck_hardware_profile_field_visibility_visibility CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC'))
);

CREATE TABLE hardware_profile_category_visibility (
    profile_id uuid NOT NULL CONSTRAINT fk_hardware_profile_category_visibility_profile REFERENCES hardware_profiles(id) ON DELETE CASCADE,
    category varchar(32) NOT NULL,
    visibility varchar(16) NOT NULL,
    CONSTRAINT pk_hardware_profile_category_visibility PRIMARY KEY (profile_id, category),
    CONSTRAINT ck_hardware_profile_category_visibility_category CHECK (category IN ('CPU', 'GPU', 'RAM', 'MOTHERBOARD', 'STORAGE', 'COOLING',
        'PSU', 'MONITOR', 'KEYBOARD', 'MOUSE', 'HEADSET', 'OTHER')),
    CONSTRAINT ck_hardware_profile_category_visibility_visibility CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC'))
);
