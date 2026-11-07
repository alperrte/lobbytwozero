-- Local development dataset import; V1-V9 and all existing product UUIDs are preserved.
ALTER TABLE hardware_product_sources DROP CONSTRAINT hardware_product_sources_source_provider_check;
ALTER TABLE hardware_product_sources ADD CONSTRAINT ck_hardware_product_sources_provider
    CHECK (source_provider IN ('OPEN_ICECAT','PC_PART_DATASET'));
ALTER TABLE hardware_product_sources ADD COLUMN source_revision varchar(40);
ALTER TABLE hardware_product_sources ADD CONSTRAINT ck_hardware_product_sources_revision
    CHECK (source_revision IS NULL OR source_revision ~ '^[0-9a-f]{40}$');

CREATE TABLE hardware_dataset_import_runs (
    id uuid CONSTRAINT pk_hardware_dataset_import_runs PRIMARY KEY,
    source_revision varchar(40) NOT NULL,
    files jsonb NOT NULL,
    summary jsonb NOT NULL,
    status varchar(32) NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_hardware_dataset_import_runs_revision CHECK (source_revision ~ '^[0-9a-f]{40}$'),
    CONSTRAINT ck_hardware_dataset_import_runs_status CHECK (status IN ('SUCCEEDED','PARTIAL')),
    CONSTRAINT ck_hardware_dataset_import_runs_json CHECK (
        jsonb_typeof(files)='object' AND jsonb_typeof(summary)='object' AND
        octet_length(files::text)<=8192 AND octet_length(summary::text)<=65536),
    CONSTRAINT ck_hardware_dataset_import_runs_times CHECK (finished_at>=started_at),
    CONSTRAINT ck_hardware_dataset_import_runs_version CHECK (version>=0)
);
