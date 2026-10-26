-- E-posta değiştirme: yeni adrese giden doğrulama kodu mevcut email_verification_codes tablosunu kullanır.
-- Yeni adres yalnız bekleyen kod satırında tutulur; hesap satırı kod doğrulanınca güncellenir. Süresi dolan kodlar
-- (ve içindeki adres) RetentionCleanupService ile silinir, hesap silinirken AuthDataEraser ile silinir.
ALTER TABLE email_verification_codes ADD COLUMN new_email varchar(254);
ALTER TABLE email_verification_codes ADD COLUMN new_email_normalized varchar(254);

ALTER TABLE email_verification_codes DROP CONSTRAINT ck_email_verification_codes_purpose;
ALTER TABLE email_verification_codes ADD CONSTRAINT ck_email_verification_codes_purpose
    CHECK (purpose IN ('EMAIL_VERIFICATION', 'EMAIL_CHANGE'));
ALTER TABLE email_verification_codes ADD CONSTRAINT ck_email_verification_codes_new_email
    CHECK ((purpose = 'EMAIL_CHANGE') = (new_email IS NOT NULL AND new_email_normalized IS NOT NULL));

-- E-posta değişince hesabın oturumları iptal edilir.
ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_reason;
ALTER TABLE auth_sessions ADD CONSTRAINT ck_auth_sessions_reason CHECK (
    revoke_reason IS NULL OR revoke_reason IN (
        'LOGOUT', 'LOGOUT_ALL', 'REFRESH_REUSE', 'ACCOUNT_INACTIVE', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'ROLE_CHANGED', 'ACCOUNT_DELETION', 'EMAIL_CHANGED'
    )
);
