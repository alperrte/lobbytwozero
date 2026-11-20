-- Hesap durumunun son değiştiği an. Diğer servislere yeniden gönderilen ACCOUNT_STATUS_CHANGED olayı gerçek değişim
-- zamanını taşır; alıcı (User) kopyasındaki daha yeni durumu eski bir olayla ezmez. updated_at bunun yerine kullanılamaz:
-- parola, e-posta ve token sürümü değişince de ilerler.
--
-- Nullable ve geriye uyumludur: eski sürüm örnekleri kolonu yazmaz (NULL kalır, onarım işi bu satırları atlar).
-- Mevcut satırlar updated_at ile doldurulur; gerçek değişim zamanı bilinmediğinden bu bir üst sınırdır (durum o andan
-- önce değişmiş olabilir, sonra değişmiş olamaz).

ALTER TABLE accounts ADD COLUMN status_changed_at timestamptz;

UPDATE accounts SET status_changed_at = updated_at WHERE status_changed_at IS NULL;

CREATE INDEX ix_accounts_status_changed ON accounts (status_changed_at) WHERE status_changed_at IS NOT NULL;
