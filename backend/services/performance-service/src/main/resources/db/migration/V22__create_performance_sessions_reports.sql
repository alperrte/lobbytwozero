-- LTZ-ALP-PERF-02: manuel performans oturumu ve oyun performans raporu.
-- user_id, game_id ve hardware_profile_id başka servislere ait mantıksal REF'tir (FK yok); doğrulama servis
-- sözleşmeleriyle yapılır. device_link_id aynı servisin device_links tablosuna FK'dir.
-- Gizlilik: oturum silinince raporu da silinir (CASCADE); cihaz bağlantısı silinmez, DISABLED kalır (RESTRICT).

CREATE TABLE performance_sessions (
    id uuid CONSTRAINT pk_performance_sessions PRIMARY KEY,
    user_id uuid NOT NULL,
    device_link_id uuid CONSTRAINT fk_performance_sessions_device_link REFERENCES device_links(id) ON DELETE RESTRICT,
    game_id uuid NOT NULL,
    game_title varchar(200) NOT NULL,
    hardware_profile_id uuid,
    source varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_performance_sessions_source CHECK (source = 'MANUAL'),
    CONSTRAINT ck_performance_sessions_status CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL)
        OR (status IN ('COMPLETED', 'ABANDONED') AND ended_at IS NOT NULL AND ended_at >= started_at)),
    CONSTRAINT ck_performance_sessions_title CHECK (length(btrim(game_title)) > 0),
    CONSTRAINT ck_performance_sessions_version CHECK (version >= 0)
);

-- Kullanıcı başına en çok bir ACTIVE oturum; eşzamanlı başlatmada ikincisi veritabanı tarafından reddedilir.
CREATE UNIQUE INDEX uq_performance_sessions_active_user ON performance_sessions(user_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_performance_sessions_user_started ON performance_sessions(user_id, started_at DESC);
CREATE INDEX ix_performance_sessions_active_started ON performance_sessions(started_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_performance_sessions_device_link ON performance_sessions(device_link_id) WHERE device_link_id IS NOT NULL;

CREATE TABLE performance_reports (
    id uuid CONSTRAINT pk_performance_reports PRIMARY KEY,
    session_id uuid NOT NULL CONSTRAINT fk_performance_reports_session REFERENCES performance_sessions(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    resolution varchar(16) NOT NULL,
    graphics_preset varchar(16) NOT NULL,
    avg_fps double precision,
    p1_low_fps double precision,
    max_cpu_temp_c double precision,
    max_gpu_temp_c double precision,
    avg_cpu_usage_percent double precision,
    avg_gpu_usage_percent double precision,
    notes varchar(500),
    source varchar(16) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_performance_reports_session UNIQUE (session_id),
    CONSTRAINT ck_performance_reports_source CHECK (source = 'MANUAL'),
    CONSTRAINT ck_performance_reports_resolution CHECK (resolution ~ '^[0-9]{3,5}x[0-9]{3,5}$'),
    CONSTRAINT ck_performance_reports_preset CHECK (graphics_preset IN ('LOW', 'MEDIUM', 'HIGH', 'ULTRA', 'CUSTOM')),
    CONSTRAINT ck_performance_reports_fps CHECK (
        (avg_fps IS NULL OR (avg_fps > 0 AND avg_fps <= 1000))
        AND (p1_low_fps IS NULL OR (p1_low_fps > 0 AND p1_low_fps <= 1000))
        AND (p1_low_fps IS NULL OR avg_fps IS NULL OR p1_low_fps <= avg_fps)),
    CONSTRAINT ck_performance_reports_temps CHECK (
        (max_cpu_temp_c IS NULL OR (max_cpu_temp_c >= 0 AND max_cpu_temp_c <= 150))
        AND (max_gpu_temp_c IS NULL OR (max_gpu_temp_c >= 0 AND max_gpu_temp_c <= 150))),
    CONSTRAINT ck_performance_reports_usage CHECK (
        (avg_cpu_usage_percent IS NULL OR (avg_cpu_usage_percent >= 0 AND avg_cpu_usage_percent <= 100))
        AND (avg_gpu_usage_percent IS NULL OR (avg_gpu_usage_percent >= 0 AND avg_gpu_usage_percent <= 100))),
    -- Ölçülmemiş değer uydurulmaz; en az bir elle girilmiş ölçüm gerekir.
    CONSTRAINT ck_performance_reports_metric_present CHECK (
        avg_fps IS NOT NULL OR p1_low_fps IS NOT NULL OR max_cpu_temp_c IS NOT NULL OR max_gpu_temp_c IS NOT NULL
        OR avg_cpu_usage_percent IS NOT NULL OR avg_gpu_usage_percent IS NOT NULL),
    CONSTRAINT ck_performance_reports_version CHECK (version >= 0)
);

CREATE INDEX ix_performance_reports_user ON performance_reports(user_id);

-- Auth D4 hesap silme: V21 fonksiyonuna oturum/rapor temizliği eklenir. Hesap başına en çok 1000 oturum
-- saklandığı için (uygulama sınırı) tek geçişte silinir; raporlar CASCADE ile gider. Önce oturumlar,
-- sonra device_links silinir (RESTRICT FK sırası).
CREATE OR REPLACE FUNCTION erase_performance_account(account_id uuid) RETURNS boolean LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM performance_sessions WHERE id IN (
        SELECT id FROM performance_sessions WHERE user_id=account_id ORDER BY id LIMIT 1000);
    DELETE FROM sync_batches WHERE id IN (
        SELECT id FROM sync_batches WHERE user_id=account_id ORDER BY id LIMIT 1000);
    DELETE FROM device_links d WHERE d.user_id=account_id
        AND NOT EXISTS (SELECT 1 FROM sync_batches b WHERE b.device_link_id=d.id)
        AND NOT EXISTS (SELECT 1 FROM performance_sessions s WHERE s.device_link_id=d.id);
    DELETE FROM sync_consents WHERE user_id=account_id;
    DELETE FROM performance_write_limits WHERE bucket_key IN (
            'sync-user:'||account_id, 'link-user:'||account_id, 'session-user:'||account_id)
        OR bucket_key LIKE 'sync-device:'||account_id||':%';
    RETURN NOT (
        EXISTS(SELECT 1 FROM performance_sessions WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM sync_batches WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM device_links WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM sync_consents WHERE user_id=account_id));
END;
$$;
