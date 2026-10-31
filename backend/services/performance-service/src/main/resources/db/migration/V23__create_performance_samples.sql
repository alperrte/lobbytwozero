-- LTZ-ALP-PERF-03: cihazdan gelen ölçüm örnekleri (CPU/GPU sıcaklık, kullanım, RAM/VRAM, disk, fan, FPS).
-- Bir satır = tek metrik okuması. Ölçülmemiş değer satır olarak YAZILMAZ (0 ile doldurulmaz). Satırlar değiştirilemez
-- (append-only) olduğundan version kolonu yoktur. user_id başka servise ait mantıksal REF'tir (FK yok).
-- Gizlilik: oturum silinince örnekleri de silinir (CASCADE); cihaz bağlantısı silinmez, DISABLED kalır (RESTRICT).

CREATE TABLE performance_samples (
    id uuid CONSTRAINT pk_performance_samples PRIMARY KEY,
    session_id uuid NOT NULL CONSTRAINT fk_performance_samples_session REFERENCES performance_sessions(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    device_link_id uuid NOT NULL CONSTRAINT fk_performance_samples_device_link REFERENCES device_links(id) ON DELETE RESTRICT,
    batch_id uuid NOT NULL,
    metric varchar(24) NOT NULL,
    value double precision NOT NULL,
    captured_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    source varchar(16) NOT NULL,
    -- Aynı oturumda aynı metrik aynı anda tek kez bulunur; farklı Idempotency-Key ile tekrar gelen okuma çoğaltılmaz.
    CONSTRAINT uq_performance_samples_point UNIQUE (session_id, metric, captured_at),
    CONSTRAINT ck_performance_samples_source CHECK (source = 'DEVICE'),
    CONSTRAINT ck_performance_samples_metric CHECK (metric IN (
        'CPU_TEMP_C', 'GPU_TEMP_C', 'CPU_USAGE_PERCENT', 'GPU_USAGE_PERCENT', 'RAM_USED_MB', 'VRAM_USED_MB',
        'DISK_READ_MBPS', 'DISK_WRITE_MBPS', 'FAN_RPM', 'FPS')),
    -- Metrik başına makul aralıklar (uygulama ile aynı tablo). Sıcaklık ve FPS için 0 sensör hatası sayılır.
    CONSTRAINT ck_performance_samples_value CHECK (
        (metric IN ('CPU_TEMP_C', 'GPU_TEMP_C') AND value > 0 AND value <= 150)
        OR (metric IN ('CPU_USAGE_PERCENT', 'GPU_USAGE_PERCENT') AND value >= 0 AND value <= 100)
        OR (metric = 'RAM_USED_MB' AND value >= 0 AND value <= 1048576)
        OR (metric = 'VRAM_USED_MB' AND value >= 0 AND value <= 262144)
        OR (metric IN ('DISK_READ_MBPS', 'DISK_WRITE_MBPS') AND value >= 0 AND value <= 100000)
        OR (metric = 'FAN_RPM' AND value >= 0 AND value <= 30000)
        OR (metric = 'FPS' AND value > 0 AND value <= 1000)),
    CONSTRAINT ck_performance_samples_time CHECK (captured_at <= received_at + interval '5 minutes')
);

-- RESTRICT kontrolünün cihaz bağlantısı silinirken tabloyu taramaması için.
CREATE INDEX ix_performance_samples_device_link ON performance_samples(device_link_id);

-- Oturum başına saklanan örnek sayısı (sınır ve gösterim için). Yalnız örnek yazımı/silimi değiştirir; version artmaz.
ALTER TABLE performance_sessions
    ADD COLUMN sample_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_performance_sessions_sample_count CHECK (sample_count >= 0 AND sample_count <= 100000);

-- Toplu gönderim makbuzu: örnek taşıyan (schemaVersion 2) gönderimlerde hedef oturum ve kabul/yinelenen sayıları.
-- Makbuz 24 saat sonra silinir; örnekler silinmez (batch_id yalnız denetim izidir, FK değildir).
ALTER TABLE sync_batches
    ADD COLUMN session_id uuid,
    ADD COLUMN stored_count integer NOT NULL DEFAULT 0,
    ADD COLUMN duplicate_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_sync_batches_sample_result CHECK (
        stored_count >= 0 AND duplicate_count >= 0 AND stored_count + duplicate_count <= sample_count),
    ADD CONSTRAINT ck_sync_batches_session CHECK (
        (schema_version = 1 AND session_id IS NULL) OR (schema_version = 2 AND session_id IS NOT NULL));

-- Auth D4 hesap silme: V22 fonksiyonu örnekleri de kapsar. Örnekler oturum silinince CASCADE ile gider (hesap başına en
-- çok 500000 örnek). Makbuz sayısı sınırlı olduğundan (en çok 5 aktif bağlantı x 1500) makbuzlar tek geçişte silinir.
CREATE OR REPLACE FUNCTION erase_performance_account(account_id uuid) RETURNS boolean LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM performance_sessions WHERE id IN (
        SELECT id FROM performance_sessions WHERE user_id=account_id ORDER BY id LIMIT 1000);
    DELETE FROM sync_batches WHERE user_id=account_id;
    DELETE FROM device_links d WHERE d.user_id=account_id
        AND NOT EXISTS (SELECT 1 FROM performance_sessions s WHERE s.device_link_id=d.id)
        AND NOT EXISTS (SELECT 1 FROM performance_samples p WHERE p.device_link_id=d.id);
    DELETE FROM sync_consents WHERE user_id=account_id;
    DELETE FROM performance_write_limits WHERE bucket_key IN (
            'sync-user:'||account_id, 'link-user:'||account_id, 'session-user:'||account_id)
        OR bucket_key LIKE 'sync-device:'||account_id||':%';
    RETURN NOT (
        EXISTS(SELECT 1 FROM performance_sessions WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM performance_samples WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM sync_batches WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM device_links WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM sync_consents WHERE user_id=account_id));
END;
$$;
