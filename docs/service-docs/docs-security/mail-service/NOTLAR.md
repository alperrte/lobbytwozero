# mail-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Kerem.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- **Yerel doğrulama engeli (2026-10-31) — ÇÖZÜLDÜ: klasör ASCII `LTZ-MAIL` yapıldı, `pre-push mail-service` PASS (%81,65). Tarihçe:** Repo klasör adı `LTZ-MAİL` (Türkçe `İ`) olduğu için JaCoCo ajanı `jacoco.exec` dosyasını bozuk yola (`LTZ-MAÝL`) yazıyor; `jacoco.xml` üretilmiyor ve `coverage-policy` / `pre-push mail-service` kapısı `Missing fresh report` ile düşüyor. Kod kaynaklı değildir (ASCII yolda aynı kod rapor üretir). Bu makinede kapı PASS sayılmaz; ASCII adlı klasörde yeniden koşturulmalı. Ayrıntı: `docs-mail/MEMORY.md` §10.
- **Test borcu kapandı (2026-10-31):** `SmtpMailSenderTest` (6) ve `MailWorkersTest` (4) eklendi; toplam satır coverage %88,99 (54 test).
- **Güvenlik tasarımı (kodda):** servis token'ı yalnız `sub=svc:auth-service`, `aud=mail-service`, `scope=mail:send|account:erase`, `account_id` eşleşmesiyle; kod/bağlantı AES-256-GCM ile şifreli, terminalde silinir; e-posta adresi terminalde maskelenir; hata gövdesi istek değerini yansıtmaz; gövde üst sınırı 16 KB.
