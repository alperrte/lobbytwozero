-- AUTH-TIER: hesap türleri (A/B/C) ve yönetici onaylı silme talebi.
--
-- İçindekiler
--   1. Kayıt yöntemi       : accounts.registration_method (PASSWORD | GOOGLE | STEAM)
--   2. Silme destek talebi : account_deletion_support_requests

-- =====================================================================================================================
-- 1. KAYIT YÖNTEMİ
-- =====================================================================================================================

-- Hesabın NASIL açıldığı: PASSWORD (e-posta + parola), GOOGLE veya STEAM (doğrudan sağlayıcıyla kayıt). Sonradan
-- değişmez; parola belirlemek ya da sağlayıcı bağlamak kayıt yöntemini değiştirmez. Hesap türü (A/B/C) bu sütundan,
-- 2FA durumundan ve bağlı sağlayıcılardan her seferinde hesaplanır, saklanmaz. Üretimde hesap yoktur; yerel
-- hesapların hepsi parolayla açıldığı için DEFAULT 'PASSWORD' geriye dönük sınıflama gerektirmez.
ALTER TABLE accounts ADD COLUMN registration_method varchar(16) NOT NULL DEFAULT 'PASSWORD';
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_registration_method CHECK (
    registration_method IN ('PASSWORD', 'GOOGLE', 'STEAM')
);

-- =====================================================================================================================
-- 2. SİLME DESTEK TALEBİ
-- =====================================================================================================================

-- B ve C türü hesap kendi başına silme başlatamaz; yöneticiye talep açar. Onaylanırsa standart silme süreci başlar
-- (bekleme süresi, servis görevleri), reddedilirse hesap olduğu gibi kalır. Hesap başına aynı anda tek bekleyen talep.
-- Hesap silinince satırlar CASCADE ile gider (talep metni kişisel veri sayılabilir; ayrıca tutulmaz).
CREATE TABLE account_deletion_support_requests (
    id            uuid        PRIMARY KEY,
    account_id    uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    status        varchar(16) NOT NULL,
    message       varchar(500),
    created_at    timestamptz NOT NULL,
    decided_at    timestamptz,
    decided_by    uuid,
    decision_note varchar(500),
    CONSTRAINT ck_deletion_support_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_deletion_support_decision CHECK (
        (status = 'PENDING') = (decided_at IS NULL AND decided_by IS NULL)
    )
);

CREATE UNIQUE INDEX uq_deletion_support_pending ON account_deletion_support_requests (account_id) WHERE status = 'PENDING';
CREATE INDEX ix_deletion_support_status_created ON account_deletion_support_requests (status, created_at);
CREATE INDEX ix_deletion_support_account ON account_deletion_support_requests (account_id, created_at);
