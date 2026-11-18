-- Yeni güvenlik bildirimi türleri: hesap silme talebinin geri alınması (ACCOUNT_DELETION_CANCELLED) ve tanınmayan cihazdan giriş
-- uyarısı (SUSPICIOUS_LOGIN). Auth SecurityNotificationType ile aynı ad sözleşmesi; bildirim yalnız occurredAt taşır.
-- Şablonlar dosyadır (mail/templates/<TÜR>.<dil>.mail, V86), bu yüzden yalnız tür kısıtı yeniden oluşturulur.
-- V81..V87 değiştirilmez. ck_mail_messages_sensitive_data (V87) bu sürümde dokunulmadan geçerli kalır.

ALTER TABLE mail_messages DROP CONSTRAINT ck_mail_messages_type;
ALTER TABLE mail_messages ADD CONSTRAINT ck_mail_messages_type CHECK (type IN (
    'EMAIL_VERIFICATION', 'PASSWORD_RESET_LINK', 'ACCOUNT_DELETION_CONFIRMATION',
    'PASSWORD_CHANGED', 'PASSWORD_RESET_COMPLETED', 'EMAIL_CHANGED',
    'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED', 'ACCOUNT_DISABLED', 'ACCOUNT_REACTIVATED',
    'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'RECOVERY_CODE_USED', 'EXTERNAL_IDENTITY_LINKED',
    'WELCOME', 'NOTICE', 'ACCOUNT_DELETION_CANCELLED', 'SUSPICIOUS_LOGIN'));
