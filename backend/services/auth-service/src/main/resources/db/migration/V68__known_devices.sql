-- Tanınan tarayıcılar ("yeni cihazdan giriş" bildirimi için).
-- Tarayıcıdaki rastgele cihaz çerezinin yalnız SHA-256 özeti (hex) saklanır. IP, User-Agent ve konum SAKLANMAZ.
-- Hesap silinince kayıtlar da silinir. Hesap başına en çok 20 kayıt tutulur (uygulama en eskiyi budar).
CREATE TABLE known_devices (
    id            uuid        PRIMARY KEY,
    account_id    uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    device_hash   varchar(64) NOT NULL,
    first_seen_at timestamptz NOT NULL,
    last_seen_at  timestamptz NOT NULL,
    -- Bu cihaz için "yeni cihaz" maili gittiyse zamanı; hesap başına 24 saatte en çok 1 mail için kullanılır.
    notified_at   timestamptz,
    CONSTRAINT uq_known_devices_account_hash UNIQUE (account_id, device_hash),
    CONSTRAINT ck_known_devices_hash CHECK (device_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_known_devices_seen CHECK (last_seen_at >= first_seen_at)
);

CREATE INDEX ix_known_devices_account_last_seen ON known_devices (account_id, last_seen_at);
