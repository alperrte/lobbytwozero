-- Parolasız (Google/Steam) hesabın ilk parolasını belirlemesi: e-posta kodu amacı.
--
-- İçindekiler
--   1. Kod amacı : email_verification_codes.purpose listesine PASSWORD_SET eklenir

-- =====================================================================================================================
-- 1. KOD AMACI
-- =====================================================================================================================

-- PASSWORD_SET : oturumlu, parolasız hesabın ilk parolasını belirlemek için hesabın doğrulanmış adresine gönderilen
--                tek kullanımlık 6 haneli kod. new_email kullanmaz (ck_email_verification_codes_new_email yalnız
--                EMAIL_CHANGE satırlarında adres ister).
ALTER TABLE email_verification_codes DROP CONSTRAINT ck_email_verification_codes_purpose;
ALTER TABLE email_verification_codes ADD CONSTRAINT ck_email_verification_codes_purpose CHECK (
    purpose IN ('EMAIL_VERIFICATION', 'EMAIL_CHANGE', 'PASSWORD_SET')
);
