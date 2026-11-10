-- Platform kapısı (diğer servislerin bilgilendirme maili attırması): NOTICE türü, çağıran izleme sütunu ve "alıcı adresi gönderim
-- anında Auth'tan çözülür" kaydı. V81..V86 değiştirilmez.
--
-- caller: kaydı yaratan servisin adı (svc:<caller> token'ından). NULL = Auth kapısı (adres istekte gelir). Dolu = platform kapısı:
-- alıcı adresi KALICI YAZILMAZ; işleyici gönderim anında Auth'tan çözer, bellekte kullanır ve saklamaz.
ALTER TABLE mail_messages ADD COLUMN caller varchar(40);

ALTER TABLE mail_messages DROP CONSTRAINT ck_mail_messages_type;
ALTER TABLE mail_messages ADD CONSTRAINT ck_mail_messages_type CHECK (type IN (
    'EMAIL_VERIFICATION', 'PASSWORD_RESET_LINK', 'ACCOUNT_DELETION_CONFIRMATION',
    'PASSWORD_CHANGED', 'PASSWORD_RESET_COMPLETED', 'EMAIL_CHANGED',
    'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED', 'ACCOUNT_DISABLED', 'ACCOUNT_REACTIVATED',
    'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'RECOVERY_CODE_USED', 'EXTERNAL_IDENTITY_LINKED',
    'WELCOME', 'NOTICE'));

-- Bekleyen kayıtta şifreli yük her zaman vardır; adres ya istekte gelmiştir (Auth kapısı) ya da çağıran doludur (platform
-- kapısı, adres gönderim anında çözülür). Biten kayıt (SENT/FAILED) hiçbir hassas veri taşımaz (değişmedi).
ALTER TABLE mail_messages DROP CONSTRAINT ck_mail_messages_sensitive_data;
ALTER TABLE mail_messages ADD CONSTRAINT ck_mail_messages_sensitive_data CHECK (
    (status IN ('PENDING', 'PROCESSING') AND payload_encrypted IS NOT NULL
        AND (recipient_email IS NOT NULL OR caller IS NOT NULL))
    OR (status IN ('SENT', 'FAILED') AND recipient_email IS NULL AND payload_encrypted IS NULL));

-- Hız sınırı sayımları (hesap başına ve çağıran başına, son saat) bu indeksten okunur.
CREATE INDEX ix_mail_messages_caller_rate ON mail_messages (caller, recipient_user_id, created_at)
    WHERE caller IS NOT NULL;
