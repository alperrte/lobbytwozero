# mail-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Kerem.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- **Yerel doğrulama engeli (2026-10-31):** Repo klasör adı `LTZ-MAİL` (Türkçe `İ`) olduğu için JaCoCo ajanı `jacoco.exec` dosyasını bozuk yola (`LTZ-MAÝL`) yazıyor; `jacoco.xml` üretilmiyor ve `coverage-policy` / `pre-push mail-service` kapısı `Missing fresh report` ile düşüyor. Kod kaynaklı değildir (ASCII yolda aynı kod rapor üretir). Bu makinede kapı PASS sayılmaz; ASCII adlı klasörde yeniden koşturulmalı. Ayrıntı: `docs-mail/MEMORY.md` §10.
- **Test borcu:** `SmtpMailSender` satır coverage'ı %36 ve `MailDispatchWorker` / `MailRetentionWorker` %0; servis katmanları testli. Toplam satır coverage'ı %81,7.
- **Güvenlik tasarımı (kodda):** servis token'ı yalnız `sub=svc:auth-service`, `aud=mail-service`, `scope=mail:send|account:erase`, `account_id` eşleşmesiyle; kod/bağlantı AES-256-GCM ile şifreli, terminalde silinir; e-posta adresi terminalde maskelenir; hata gövdesi istek değerini yansıtmaz; gövde üst sınırı 16 KB.
