# LTZ-KER-MAIL-01 — SMTP, Kalıcı Gönderim Kuyruğu ve Güvenilir Teslim

Sorumlu: Kerem. Görevler: MAIL-10, 11, 12, 13, 14, 15, 16. Durum: **KOD + TEST TAMAM** (2026-10-31); resmi kapı PASS (54 test, satır coverage %88,99).
Ön koşul onayları (`decisions/kararlar.md`) verildi.

## Gerçekleşen durum (2026-10-31)

| Madde | Durum | Kanıt |
| --- | --- | --- |
| V81 şema (`mail_templates`, `mail_messages`, `mail_delivery_attempts`) | TAMAM | `V81__create_mail_tables.sql`, `MailSchemaAndRetentionTest` (7) |
| Domain + enqueue + idempotency (`request_hash`, 409) | TAMAM | `MailDeliveryControllerTest` (15) |
| Kuyruk işleyici (SKIP LOCKED, kiralama, retry, FAILED, expires_at) | TAMAM | `MailDispatchServiceTest` (12) |
| SMTP gönderici (`SmtpMailSender`, TLS, timeout) | TAMAM: `SmtpMailSenderTest` (6, gerçek SMTP konuşması: başarı, 4xx/5xx, geçersiz alıcı, erişilemez sunucu, kimlik hatası); Mailpit'e gerçek gönderim uçtan uca doğrulandı (2026-10-31) | — |
| AES-256-GCM payload şifreleme, terminalde silme/maskeleme | TAMAM | `MailDispatchServiceTest`, `MailSchemaAndRetentionTest` |
| Servis token doğrulama (typ/aud/sub/scope/account_id) | TAMAM | `MailDeliveryControllerTest` (401/403 negatifleri) |
| Hesap silme ucu (idempotent) | TAMAM | `AccountErasureTest` (3) |
| Retention işleyici | TAMAM; servis ve zamanlayıcı sınıfları testli | `MailSchemaAndRetentionTest`, `MailWorkersTest` (4) |
| `docs/api.md` `## MAIL` | TAMAM (5 kayıt) | — |
| `docs-security/mail-service/NOTLAR.md` | TAMAM | — |

**Doğrulama (güncel, 2026-10-31):** `mvn verify` (JDK 25.0.5): 54 test PASS (44 + `SmtpMailSenderTest` 6 + `MailWorkersTest` 4), Checkstyle 0, PMD 0, JaCoCo satır %88,99. `pre-push mail-service` PASS (klasör ASCII `LTZ-MAIL` yapılınca; eski `İ` sorunu için [MEMORY §10](../MEMORY.md)). `lint-ratchet` temiz.
**Kod düzeltmesi:** `MailEnqueueService` içindeki `'\u0000'` → `'\0'` (coverage kapısı Unicode kaçışlarını reddediyor).

## Kabul kriterleri (görev dosyasından)

| Görev | Kriter |
| --- | --- |
| MAIL-10 | Seçilecek sağlayıcı üzerinden güvenli SMTP; yapılandırma yalnız Mail Service'te |
| MAIL-11 | İstek kalıcı kaydedilir, işleyici işler |
| MAIL-12 | Geçici hatada kontrollü tekrar |
| MAIL-13 | Bekliyor / işleniyor / gönderildi / başarısız |
| MAIL-14 | Tekrarlanan istek mükerrer e-posta üretmez |
| MAIL-15 | Tür, alıcı, zaman, deneme sayısı, sağlayıcı sonucu güvenli kaydedilir |
| MAIL-16 | Token üretmez/parola değiştirmez; ana akışı bloke etmez; SMTP hatasında kayıp yok; token loglanmaz; auth/notification/admin/event ile sözleşme; in-app bildirim notification'da |

## İş kırılımı (sıra)

1. **V81** `create_mail_tables.sql`: `mail_templates`, `mail_messages` (+ `request_hash`, `lease_until`, `payload_key_version`, CHECK status/type), `mail_delivery_attempts`, `inbox_events` yok (Mail olay tüketmez). Kısıt adları `uq_mail_messages_idempotency_key` vb.
2. Domain: `MailMessage` (durum geçiş kuralları, `@Version`), `MailStatus`, `MailType`, `MailLocale`, `UserErrorCode` benzeri `MailErrorCode`/`MailException`.
3. Application: `MailEnqueueService` (idempotent kuyruğa alma, tek transaction), `MailDispatchService` (kirala → render → SMTP → sonuç), `MailRetryPolicy`, `MailRetentionService` (temizlik), `AccountErasureService`; portlar: `MailSenderPort`, `MailTemplatePort`, `PayloadCipherPort`.
4. Infrastructure: `SmtpMailSender` (JavaMailSender, TLS, timeout, hata → kod eşleme, adres/mesaj loglamaz), `AesGcmPayloadCipher`, `MailDispatchWorker` (zamanlayıcı, SKIP LOCKED, takılı PROCESSING kurtarma), `SecurityConfig` + `JwtConfig` + servis token doğrulayıcı (User/Social örneği), `RequestBodySizeLimitFilter`.
5. API (yalnız iç): 4 ayrı `POST /internal/v1/mail/{verification-codes|password-resets|account-deletions|security-notifications}` (202), `DELETE /internal/v1/accounts/{accountId}` (204). `GlobalExceptionHandler` (ProblemDetail).
6. `application.properties` (SMTP, worker, retry — ENV'siz sabitler `ltz.mail.*`; yeni ENV yalnız onayla), `.env.example` önerisi (onay beklenir), compose aktarımı.
7. `docs/api.md` `## MAIL` kayıtları, `docs/database.md` §3.3 güncelleme notu, `docs-security/mail-service/NOTLAR.md`, `docs-auth` karşılığı için Auth'a not.
8. Testler (aynı fazda): enqueue idempotency (aynı/farklı içerik/eşzamanlı), durum makinesi, retry zamanlaması + limit, geçici/kalıcı SMTP hatası, kiralama yarışı, çökme sonrası kurtarma, sır/e-posta log sızıntısı yok, token doğrulama negatifleri (typ/aud/sub/scope/ömür), silme idempotent, Flyway şema, 401/403.

## Doğrulama (bitişte)
`mvn -B -f backend/pom.xml -pl :mail-service verify` (JDK 25), `lint-ratchet check --modules services/mail-service`, `.\pre-push\pre-push.cmd mail-service`, coverage ≥ %80.
