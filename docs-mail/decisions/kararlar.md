# Mail Service — kararlar (Auth'a uyum esaslı)

Tarih: 2026-10-31. İlke: **Mail'e bugün yalnız Auth istek atar.** Event, Notification, Admin ve User kodunda Mail'e çağrı yoktur
(iskelet; Auth'ta onlar için token çifti de tanımlı değil). Bu yüzden Mail, Auth'un gerçek kodu ve `docs/api.md` kaydıyla birebir uyumlu yapılır.
Auth'un yaptığı mantıklı; Mail'den gelen **tek** değişiklik isteği aşağıda "Auth'a bildirilecekler"dedir.

## Alınan varsayılanlar (onay gerekmez, Auth'a uyum gereği)

| # | Konu | Karar | Dayanak |
| --- | --- | --- | --- |
| D1 | Uç biçimi | Auth'un 4 portuna **bire bir** 4 iç uç: `POST /internal/v1/mail/verification-codes`, `.../password-resets`, `.../account-deletions`, `.../security-notifications`. İçeride tek kuyruk | `api.md` Auth→Mail kaydı (verification-codes) + `*MailPort` arayüzleri |
| D2 | Kimlik | `svc+jwt`: `iss=ltz-auth`, `aud=mail-service`, `sub=svc:auth-service`, `scope=mail:send`, ömür ≤60 sn, `jti`. Auth `ServiceTokenIssuer.issue(aud, scope, accountId)` ile üretir; scope kalıbı `^[a-z]+:[a-z]+$` ile uyumlu. Mail yalnız doğrular (JWKS), kimseyi çağırmaz | `servis-sozlesmeleri.md` §3, `HttpAccountDataEraser` deseni |
| D3 | Yanıt | Kalıcı kayıttan **sonra** `202`. Auth `202` dışını "teslim edilemedi" sayıp kodu `DELIVERY_FAILED` yapar, bu yüzden `202` yalnız DB'ye yazıldıysa döner. Yönlendirme yok, gövde yok | Auth `*Dispatcher` sınıfları |
| D4 | Alıcı e-postası | Auth gövdede verir (`recipientEmail`). Mail hiçbir servisten e-posta çözmez | Auth portları |
| D5 | Dil | `locale` isteğe bağlı (`tr`/`en`). Gelmezse **iki dilli** (TR + EN) gönderilir; bugünkü Auth/Mailpit davranışıyla aynı. Auth'ta değişiklik gerekmez | `DevSmtpVerificationMailAdapter` metinleri |
| D6 | Idempotency | `idempotencyKey` (Auth'un kod/token kaydı kimliği) tekil. Aynı anahtar + aynı içerik → `202` (önceki kayıt); farklı içerik → `409`. Bunun için `mail_messages.request_hash` | `database.md` §2.4 |
| D7 | Hassas içerik | Kod/bağlantı kuyrukta **şifreli** tutulur (SMTP kesintisinde retry için gerekli), gönderim sonrası silinir. Loga/geçmişe düz yazılmaz | `SECURITY.md` §15 |
| D8 | Çağıran izin matrisi | `type`↔`sub` tablosu kodda: şimdilik yalnız `svc:auth-service`. İleride başka servis isterse tabloya satır eklenir | `servis-sozlesmeleri.md` §3.5 deseni |
| D9 | Retry/kuyruk | DB kuyruğu, `FOR UPDATE SKIP LOCKED` kiralama (Auth silme işçisi gibi), üstel geri çekilme (1 dk → en çok 6 sa), en çok 8 deneme, sonra `FAILED`. SMTP 4xx geçici, 5xx kalıcı. Değerler kodda `ltz.mail.*` | `architecture.md` §8 |
| D10 | Silme | `DELETE /internal/v1/accounts/{accountId}` (`scope=account:erase`, `sub=svc:auth-service`, `account_id`=yol), `204`, idempotent. **İlk iş** | `servis-sozlesmeleri.md` §3.1 |
| D11 | Şablon | Yeni bağımlılık yok: DB'de `mail_templates` + Flyway seed, kendi küçük yer tutucu çözücüsü, HTML-escape zorunlu, başlıkta satır sonu temizliği | `SECURITY.md` §15 |
| D12 | Test | Her fazda test; gerçek PostgreSQL (Auth'un yaklaşımı), SMTP için sahte gönderici (`spring-boot-starter-mail-test` zaten pom'da). Yeni bağımlılık yok | `AGENTS.md` §6 |
| D13 | Geçmiş uçları | Kullanıcıya/yöneticiye uç yok. Gönderim geçmişi yalnız DB'dedir | Kapsam küçültme |
| D14 | Event/Notification/Admin | **Ertelendi.** İstek atan olmadıkça MAIL-06/07 için uç açılmaz; olursa D2/D8 modeliyle (Auth'ta çift + tablo satırı) eklenir | Auth-merkezli ilke |

**Güncelleme (2026-10-31):** tüm teknik ayrıntı [../SPEC.md](../SPEC.md)'de kesinleştirildi. Kod okumasından ek kesin kararlar: token `account_id` ile gövde `accountId` eşleşmeli; `expires_at` ile süresi dolan kod gönderilmez; terminal durumda tam adres NULL + maskeli kopya, payload silinir; SMTP gönderici elle kurulur (health göstergesi yok); `inbox_events`/outbox bu serviste yok.

## Onay durumu (2026-10-31): hepsi verildi

SMTP ENV'leri boş olarak `ops` betiğine ve compose'a eklendi; şifreleme anahtarı üreticisi betiğe eklendi; testcontainers onaylı; logo `mail-service/src/main/resources/mail/ltz-logo.png`. Kalan: `.env.example` satırları (kullanıcı ekler) ve Auth adaptör görevi. Ayrıntı: `MEMORY.md` §6. Aşağıdaki liste tarihsel kayıttır.

## (Tarihsel) onay isteyenler

1. **SMTP sağlayıcısı ve yeni ENV'ler.** Sağlayıcı seçilmedi. Önerilen ENV: `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD`, `MAIL_SMTP_SECURITY` (`STARTTLS` varsayılan), `MAIL_FROM_ADDRESS`, `MAIL_ALLOWED_LINK_HOSTS` (bağlantı host allowlist'i; prod'da boşsa açılmaz). Dev: mevcut Mailpit (`dev-mail`).
2. **Şifreleme anahtarı ENV'i** (D7): `MAIL_PAYLOAD_ENCRYPTION_KEY` (Base64, 256 bit). Boş/zayıfsa servis açılmaz (fail-fast). `.env.example` + compose için sana öneri sunarım, `.env`'e dokunmam.
2b. **Test bağımlılığı:** `testcontainers-postgresql` (test scope; Auth ve User'da zaten kullanılıyor).
2c. **LTZ logosu** repoda yok (MAIL-08): dosya/URL gerekli.
3. **Auth'a bildirilecek değişiklikler** (Auth de senin; ayrı küçük görev):
   - `MailAdapter`'a `MAIL_SERVICE` değeri (`productionReady=true`) + RestClient adaptörü (`HttpAccountDataEraser` deseni, `mail:send` token, 202 dışını hata say) + `MAIL_SERVICE_URL`.
   - `api.md`'deki doğrulama kodu kaydına diğer 3 ucu ekleme; `locale` Auth'tan gelmeyecek (D5).
   - **Sonra, Mail bittiğinde:** MAIL-05 için `WELCOME` (yeni `SecurityNotificationType`), MAIL-03 için "şüpheli giriş" Auth'ta henüz yok.

## Sorulmayacaklar (kaldırıldı)
Eski K2 (alıcı çözümü), K6 (notification tercihi), K7 (başka servis çiftleri), K12 (geçmiş uçları): çağıran olmadığı için gereksiz. Nisa/notification sahibine mesaj gerekmez; ihtiyaç doğarsa açılır.
