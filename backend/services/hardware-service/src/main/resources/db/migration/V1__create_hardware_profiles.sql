CREATE TABLE hardware_profiles (
    id uuid CONSTRAINT pk_hardware_profiles PRIMARY KEY,
    user_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    device_type varchar(32) NOT NULL,
    source varchar(32) NOT NULL DEFAULT 'MANUAL',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_hardware_profiles_name CHECK (length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_hardware_profiles_device_type CHECK (device_type IN ('DESKTOP', 'LAPTOP', 'OTHER')),
    CONSTRAINT ck_hardware_profiles_source CHECK (source = 'MANUAL'),
    CONSTRAINT ck_hardware_profiles_version CHECK (version >= 0)
);

-- user_id başka servise ait mantıksal REF'tir; fiziksel FK değildir.
CREATE INDEX ix_hardware_profiles_user_created ON hardware_profiles (user_id, created_at, id);
