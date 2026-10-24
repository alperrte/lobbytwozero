-- Isolated PostgreSQL smoke check; run after V1 with psql -v ON_ERROR_STOP=1.
-- All fixture data is synthetic and rolled back.
BEGIN;

INSERT INTO hardware_profiles (id, user_id, name, device_type, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002', 'Test PC', 'DESKTOP', now(), now());

DO $$
DECLARE
    test_profile hardware_profiles%ROWTYPE;
BEGIN
    SELECT * INTO STRICT test_profile FROM hardware_profiles
        WHERE id = '00000000-0000-0000-0000-000000000001';
    ASSERT test_profile.source = 'MANUAL', 'manual default missing';
    ASSERT test_profile.version = 0, 'version default missing';
    ASSERT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ix_hardware_profiles_user_created'),
        'owner pagination index missing';
    ASSERT NOT EXISTS (SELECT 1 FROM pg_constraint
        WHERE conrelid = 'hardware_profiles'::regclass AND contype = 'f'),
        'external owner must not have a foreign key';
    ASSERT (SELECT data_type = 'timestamp with time zone' FROM information_schema.columns
        WHERE table_name = 'hardware_profiles' AND column_name = 'created_at'), 'UTC type missing';

    BEGIN
        UPDATE hardware_profiles SET name = '   ' WHERE id = test_profile.id;
        RAISE EXCEPTION 'blank name accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE hardware_profiles SET device_type = 'INVALID' WHERE id = test_profile.id;
        RAISE EXCEPTION 'invalid device type accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE hardware_profiles SET source = 'DESKTOP_SYNC' WHERE id = test_profile.id;
        RAISE EXCEPTION 'unapproved sync source accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE hardware_profiles SET version = -1 WHERE id = test_profile.id;
        RAISE EXCEPTION 'negative version accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE hardware_profiles SET user_id = NULL WHERE id = test_profile.id;
        RAISE EXCEPTION 'null owner accepted';
    EXCEPTION WHEN not_null_violation THEN NULL;
    END;
    BEGIN
        UPDATE hardware_profiles SET name = repeat('x', 101) WHERE id = test_profile.id;
        RAISE EXCEPTION 'oversized name accepted';
    EXCEPTION WHEN string_data_right_truncation THEN NULL;
    END;
END $$;

ROLLBACK;
