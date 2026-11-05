-- Süreli hesap askısı (B8): SUSPENDED hesabın otomatik açılma zamanı.
--
-- İçindekiler
--   1. Askı bitişi : accounts.suspended_until (+ kısıt, kısmi indeks)

-- =====================================================================================================================
-- 1. ASKI BİTİŞİ
-- =====================================================================================================================

-- NULL: süresiz askı (yalnız elle açılır) ya da askıda olmayan hesap. Dolu: süre dolunca arka plan işi hesabı ACTIVE yapar.
-- Yalnız SUSPENDED hesapta dolu olabilir; durum değişince (elle açma, yasaklama vb.) uygulama alanı temizler.
ALTER TABLE accounts ADD COLUMN suspended_until timestamptz;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_suspended_until CHECK (
    suspended_until IS NULL OR status = 'SUSPENDED'
);

-- Süresi gelen askıları bulan tarama yalnız dolu satırlara bakar.
CREATE INDEX ix_accounts_suspended_until ON accounts (suspended_until) WHERE suspended_until IS NOT NULL;
