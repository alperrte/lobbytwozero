-- Auth Experience V2: sosyal girişte ilk girişte kullanıcı adı seçimi.
--
-- İçindekiler
--   1. E-postasız Steam hesabı : accounts.email / email_normalized artık boş olabilir (+ tutarlılık kısıtları)
--   2. Google kayıt token'ı    : google_registrations (hesap, kullanıcı adı seçilince açılır)

-- =====================================================================================================================
-- 1. E-POSTASIZ STEAM HESABI
-- =====================================================================================================================

-- Standart Steam OpenID e-posta vermez. SteamID64 ile açılan hesabın e-postası yoktur (sahte adres üretilmez);
-- kullanıcı sonradan e-posta ekleyip doğrulatabilir (mevcut e-posta değiştirme akışı). Benzersizlik kısıtı boş değerleri
-- birbiriyle çakıştırmaz. Mevcut hesapların hepsinin e-postası vardır; bu değişiklik onları etkilemez.
ALTER TABLE accounts ALTER COLUMN email DROP NOT NULL;
ALTER TABLE accounts ALTER COLUMN email_normalized DROP NOT NULL;

-- İkisi birlikte dolu ya da birlikte boş; doğrulanmış e-posta ve doğrulama bekleyen durum adres olmadan var olamaz.
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_email_pair CHECK ((email IS NULL) = (email_normalized IS NULL));
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_verified_needs_email CHECK (email IS NOT NULL OR email_verified_at IS NULL);
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_pending_needs_email CHECK (email IS NOT NULL OR status <> 'PENDING_VERIFICATION');

-- =====================================================================================================================
-- 2. GOOGLE KAYIT TOKEN'I
-- =====================================================================================================================

-- İlk kez Google ile gelen kişi için doğrulanmış kimlik (sub) ve Google'ın doğruladığı e-posta burada, kullanıcı adı
-- seçilene kadar bekler. Token yalnız özet olarak saklanır, tek kullanımlıktır ve kısa ömürlüdür. Hesap bu kayıt
-- tamamlanana kadar AÇILMAZ; e-posta ve sub istemciden hiçbir zaman alınmaz.
CREATE TABLE google_registrations (
    token_hash  varchar(64)  PRIMARY KEY,
    subject     varchar(64)  NOT NULL,
    email       varchar(254) NOT NULL,
    created_at  timestamptz  NOT NULL,
    expires_at  timestamptz  NOT NULL,
    consumed_at timestamptz,
    CONSTRAINT ck_google_registrations_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_google_registrations_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_google_registrations_expires ON google_registrations (expires_at);
