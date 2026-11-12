-- AUTH-AGE: yaş doğrulaması sonucu (access token'daki `adult_verified` iddiasının kaynağı).
--
-- İçindekiler
--   1. Yaş doğrulaması : account_age_verifications

-- =====================================================================================================================
-- 1. YAŞ DOĞRULAMASI
-- =====================================================================================================================

-- Hesabın "18 yaşından büyük" beyanının SONUCU. Doğum tarihi, yaş ve kimlik numarası saklanmaz: sunucu beyanı alır,
-- 18+ olup olmadığını hesaplar ve tarihi atar. Satır varsa ve revoked_at boşsa doğrulanmıştır; Auth her access token
-- üretiminde (en geç 60 sn'de bir) bu satıra bakar. Hesap başına en çok bir satır: geri alınıp yeniden beyan edilirse aynı
-- satır güncellenir. Hesap silinince satır CASCADE ile gider (başka servise kopyalanmaz, olay gerekmez).
CREATE TABLE account_age_verifications (
    account_id  uuid        PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    verified_at timestamptz NOT NULL,
    method      varchar(16) NOT NULL,
    revoked_at  timestamptz,
    revoked_by  varchar(8),
    version     bigint      NOT NULL DEFAULT 0,
    -- Şimdilik yalnız beyan. Üçüncü taraf yöntem eklenince bu liste DROP + ADD ile genişler (aynı dosyada, çift).
    CONSTRAINT ck_age_verifications_method CHECK (method IN ('SELF_DECLARED')),
    CONSTRAINT ck_age_verifications_revoked_by CHECK (revoked_by IS NULL OR revoked_by IN ('SELF', 'ADMIN')),
    -- Geri alma zamanı ve geri alan taraf birlikte dolu ya da birlikte boş olur.
    CONSTRAINT ck_age_verifications_revocation CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);
