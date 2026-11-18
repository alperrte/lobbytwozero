-- REC-09 / PERF-12/13/21: yalnız yeni, açıkça bağlanmış cihaz örnekleri tahmin kanıtı olabilir.
-- Eski oturum/örnekler NULL kalır; manuel rapor veya v2 verisi geriye dönük kanıta çevrilmez.
ALTER TABLE performance_sessions
    ADD COLUMN measurement_context_id uuid,
    ADD COLUMN hardware_profile_version bigint,
    ADD COLUMN measurement_resolution varchar(11),
    ADD COLUMN measurement_graphics_preset varchar(8),
    ADD COLUMN measurement_rendering_mode varchar(16),
    ADD CONSTRAINT uq_performance_sessions_context UNIQUE (measurement_context_id),
    ADD CONSTRAINT ck_performance_sessions_context CHECK (
        (measurement_context_id IS NULL AND hardware_profile_version IS NULL
            AND measurement_resolution IS NULL AND measurement_graphics_preset IS NULL
            AND measurement_rendering_mode IS NULL)
        OR (measurement_context_id IS NOT NULL AND measurement_context_id = id
            AND hardware_profile_id IS NOT NULL AND device_link_id IS NOT NULL
            AND hardware_profile_version IS NOT NULL AND hardware_profile_version >= 0
            AND measurement_resolution IS NOT NULL
            AND measurement_resolution ~ '^[1-9][0-9]{2,4}x[1-9][0-9]{2,4}$'
            AND split_part(measurement_resolution,'x',1)::integer BETWEEN 100 AND 16384
            AND split_part(measurement_resolution,'x',2)::integer BETWEEN 100 AND 16384
            AND measurement_graphics_preset IS NOT NULL AND measurement_graphics_preset IN ('LOW','MEDIUM','HIGH','ULTRA')
            AND measurement_rendering_mode IS NOT NULL AND measurement_rendering_mode = 'NATIVE_RASTER'));

ALTER TABLE performance_samples
    ADD COLUMN measurement_context_id uuid REFERENCES performance_sessions(measurement_context_id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_performance_samples_context CHECK (measurement_context_id IS NULL OR measurement_context_id = session_id);

ALTER TABLE sync_batches DROP CONSTRAINT ck_sync_batches_session;
ALTER TABLE sync_batches ADD CONSTRAINT ck_sync_batches_session CHECK (
    (schema_version = 1 AND session_id IS NULL) OR (schema_version IN (2,3) AND session_id IS NOT NULL));

CREATE INDEX ix_performance_sessions_evidence ON performance_sessions
    (user_id,game_id,hardware_profile_id,hardware_profile_version,measurement_resolution,
     measurement_graphics_preset,measurement_rendering_mode,ended_at DESC,id)
    WHERE status = 'COMPLETED' AND measurement_context_id IS NOT NULL;
CREATE INDEX ix_performance_samples_context_fps ON performance_samples(measurement_context_id,captured_at DESC)
    WHERE measurement_context_id IS NOT NULL AND metric = 'FPS';
