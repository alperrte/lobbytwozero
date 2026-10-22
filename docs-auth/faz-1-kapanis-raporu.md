# LTZ-KER-AUTH-01 — Faz 1 Kapanış Raporu (Kayıt ve E-posta Doğrulama)

> Tarih: 2026-10-21 · Sorumlu: Kerem · Kapsam: AUTH-01, AUTH-02 · Durum: **development'ta tamam, production teslimi PENDING**
>
> Bu belge auth çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir.

## 1. Sonuç

| Görev | Development | Production |
| --- | --- | --- |
| AUTH-01 Kayıt | IMPLEMENTED | IMPLEMENTED |
| AUTH-02 E-posta doğrulama | IMPLEMENTED (Mailpit ile uçtan uca doğrulandı) | **PENDING** — gerçek Mail Service adaptörü yok; `prod` profili bilerek başlamaz |

Mailpit dışarıya e-posta göndermez. Development'taki başarılı SMTP teslimi production e-posta teslimi **değildir**.

## 2. Akış

1. `POST /api/v1/auth/register` → hesap `PENDING_VERIFICATION`, yalnız `PLAYER` rolü, parola Argon2id.
2. `SecureRandom` ile 6 haneli kod; DB'de yalnız `HMAC-SHA256(AUTH_OTP_HMAC_KEY, amaç:hesap:kod)` özeti.
3. İşlem commit edildikten sonra kod, istek iş parçacığı dışında `VerificationCodeMailPort`'a iletilir.
4. Port uygulaması `ltz.auth.mail.adapter` ile seçilir:
   - `unavailable` (varsayılan): teslim reddedilir → kod `DELIVERY_FAILED`.
   - `dev-smtp` (yalnız `dev` profili, `prod` yokken): Mailpit'e SMTP.
5. `POST /api/v1/auth/email-verification/confirm` → satır kilidiyle atomik tüketim, hesap `ACTIVE`.
6. `POST /api/v1/auth/email-verification/resend` → eski kod `SUPERSEDED`, yeni kod.

Controller, application service ve domain katmanları Mailpit için değişmedi. Gerçek Mail Service geldiğinde yalnız yeni bir `VerificationCodeMailPort` uygulaması ve `MailAdapter` değeri (`productionReady=true`) eklenir.

## 3. Production / development ayrımı

| Katman | Koruma |
| --- | --- |
| Compose | `mailpit` servisi `profiles: ["dev-mail"]`; `COMPOSE_PROFILES` tanımlı değilse başlamaz. `auth-service` Mailpit'e `depends_on` ile bağlı değildir. Portlar yalnız `127.0.0.1`. Volume yok. |
| Spring profili | `DevSmtpMailConfig`: `@Profile("dev & !prod")` + `adapter=dev-smtp`. dev ve prod birlikte seçilse bile yüklenmez. |
| Açılış denetimi | `MailDeliveryConfig`: `dev-smtp` yalnız `dev` profiliyle ve `prod` yokken kabul edilir; `prod` profili production'a hazır adaptör olmadan başlamaz (fail-fast). |
| Yapılandırma | SMTP ayarları yalnız `application-dev.properties`; kaynak kodda bağlantı bilgisi yok. |

Mailpit `COMPOSE_PROFILES=dev-mail` ile yanlışlıkla production'da başlatılsa bile `prod` profilindeki auth-service onu kullanmaz; zaten açılışta durur.

## 4. Development kullanımı

1. `.env.dev` dosyasına ekleyin (değerler sohbete/loga yazılmaz):
   - `AUTH_OTP_HMAC_KEY=` → Git Bash: `openssl rand -base64 32`
   - `COMPOSE_PROFILES=dev-mail`
2. `docker compose --env-file .env.dev up -d --build postgres mailpit auth-service`
3. Kayıt: `POST http://127.0.0.1:5081/api/v1/auth/register` (yalnız sahte e-posta, ör. `oyuncu1@example.test`).
4. Kodu Mailpit arayüzünde görün: `http://127.0.0.1:8025`.
5. `POST http://127.0.0.1:5081/api/v1/auth/email-verification/confirm`.

IDE'den çalıştırırken: `SPRING_PROFILES_ACTIVE=dev` ve `LTZ_AUTH_MAIL_DEVSMTP_HOST=localhost` (Mailpit host portu 1025).

## 5. Gereksinim → test → dosya kanıtı

| Madde | Test | Ana dosya |
| --- | --- | --- |
| Büyük/küçük harf duyarsız tekil e-posta ve kullanıcı adı; 20 eşzamanlı kayıt → tek hesap | `RegistrationControllerTest` | `RegistrationService`, `V61__create_account_tables.sql` |
| Hesap numaralandırma koruması (aynı yanıt), kullanıcı adında 409 | `RegistrationControllerTest` | `RegistrationService` |
| İstemci rol/durum gönderemez, yalnız PLAYER | `RegistrationControllerTest`, `FlywayMigrationTest` | `RegisterAccountRequest`, `V61` |
| Argon2id (m=19 MiB, t=2, p=1), eşzamanlı hash sınırı | `PasswordEncoderConfigTest`, `BoundedPasswordEncoderTest` | `PasswordEncoderConfig`, `BoundedPasswordEncoder` |
| 6 haneli, başında 0 olabilen kod; DB'de yalnız HMAC | `VerificationCodeGeneratorTest`, `VerificationCodeHasherTest`, `EmailVerificationControllerTest` | `VerificationCodeGenerator`, `VerificationCodeHasher`, `V62` |
| TTL 10 dk, 5 deneme, 60 sn bekleme, günde 5 kod, tek aktif kod | `EmailVerificationControllerTest`, `FlywayMigrationTest` | `EmailVerificationService`, `VerificationProperties` |
| Atomik tek kullanım; 2 eşzamanlı doğru giriş → tek başarı; eşzamanlı yanlış denemeler sınırı aşamaz | `EmailVerificationControllerTest`, `MailpitEndToEndTest` | `EmailVerificationCodeRepository` (satır kilidi) |
| Kayıt → SMTP → Mailpit → kod → ACTIVE → ikinci kullanım reddi | `MailpitEndToEndTest` | `DevSmtpVerificationMailAdapter` |
| SMTP erişilemez / Mail Service yok → gönderim iddia edilmez, kod geçersiz | `DevSmtpDeliveryFailureTest`, `VerificationDeliveryFailureTest`, `VerificationCodeDispatcherTest` | `VerificationCodeDispatcher` |
| Profil izolasyonu ve production fail-fast | `MailDeliveryConfigTest` | `MailDeliveryConfig`, `DevSmtpMailConfig` |
| Fail-closed güvenlik zinciri, 401/403 ProblemDetail, health açık | `SecurityRulesTest`, `AuthServiceApplicationTests` | `SecurityConfig`, `GlobalExceptionHandler` |
| Gövde sınırı (8 KB, chunked dahil) | `RegistrationControllerTest`, `RequestBodySizeLimitFilterTest` | `RequestBodySizeLimitFilter` |
| Loglarda parola, kod, e-posta yok | `EmailVerificationControllerTest`, `RegistrationControllerTest`, `MailpitEndToEndTest` | `application.properties` (`logServerErrorDetail=false`) |

## 6. Çalıştırılan doğrulamalar

| Komut | Sonuç |
| --- | --- |
| `mvn -B -ntp -f backend/pom.xml -pl services/auth-service clean verify` (JDK 25, Testcontainers PostgreSQL 17 + Mailpit v1.31.0) | BUILD SUCCESS, 87/87 test, satır kapsamı %95,99 |
| `sh ./pre-push/pre-push.sh auth-service` | `BACKEND TEST CHECK PASSED` |
| `sh ./pre-push/pre-push.sh full dev` | **NOT SAFE TO COMMIT.** auth-service ve api-gateway testleri geçti. Diğer 15 servisin `contextLoads` testi `localhost:5542`'de çalışan PostgreSQL olmadığı için düştü; bu servislere dokunulmadı, hata önceden de vardı. Docker Configuration adımı `.env.dev`'de `AUTH_OTP_HMAC_KEY` eksik olduğu için düştü (anahtar eklenince geçmesi beklenir). |
| `docker compose --env-file .env.example config` (profil açık/kapalı) | Mailpit yalnız `dev-mail` profiliyle; anahtar yalnız auth-service'te |

## 7. ENV

| Anahtar | Tür | Durum |
| --- | --- | --- |
| `AUTH_OTP_HMAC_KEY` | Secret, yalnız auth-service | `.env.example` ✓ · `.env.dev` **eksik** (kullanıcı ekleyecek) |
| `COMPOSE_PROFILES=dev-mail` | Dev ayarı, secret değil | `.env.example` ✓ · `.env.dev` kullanıcı ekleyecek · `.env.prod`'a **eklenmez** |
| `MAILPIT_SMTP_HOST_PORT`, `MAILPIT_UI_HOST_PORT` | İsteğe bağlı | Varsayılan 1025 / 8025 |

## 8. Bağımlılıklar

- `org.bouncycastle:bcprov-jdk18on:1.85.2` (runtime, Argon2)
- `spring-boot-starter-mail` (Boot yönetimli; `JavaMailSender` bean olarak yayınlanmaz, mail health göstergesi devreye girmez)
- `org.testcontainers:testcontainers-postgresql` (test, Boot yönetimli 2.0.5)
- Docker imajı: `axllent/mailpit:v1.31.0` (sabit sürüm)

## 9. Açık riskler ve PENDING maddeler

- **Production e-posta teslimi PENDING:** Mail Service (MAIL-01) ve servisler arası kimlik (D4) yok; `prod` profili bu yüzden başlamaz.
- **İstemci IP'si güvenilir değil (K8):** Gateway `trusted-proxies` ayarlı değil; IP bazlı limit yok. Toplu kayıt riski açık; Gateway önerisi onay bekliyor (ortak servis).
- **DB superuser:** Servis başına DB kullanıcısı production öncesi gerekli.
- **Gerçek Compose ortamında uçtan uca deneme yapılmadı:** `.env.dev` dosyasında `AUTH_OTP_HMAC_KEY` eksik. Aynı akış Testcontainers ile gerçek Mailpit container'ına karşı doğrulandı.

## 10. Belge ↔ uygulama farkları

- `docs/database.md`: `security_events.ip_hash` eklenmedi (güvenilir IP yok); `accounts` 2FA/kilit alanları Faz 2'ye bırakıldı; doğrulama kodlarına `purpose`, `consumed_at`, `invalidated_at`, `invalidation_reason` eklendi.
- `docs/folder-structure.md`: application servisleri Spring Data repository'lerini doğrudan kullanıyor (her repository için port gereksiz soyutlama olurdu).
- `ops/ops.md` ve `docs/SECURITY.md` §17 Compose'un yalnız PostgreSQL içerdiğini söylüyor; artık uygulama servisleri ve dev Mailpit de var (ortak belge, düzenlenmedi).
- `docs/api.md` §3 girişindeki "Henüz kayıtlı API yok" cümlesi güncel değil (ortak bölüm, düzenlenmedi).

## 11. Faz 2'ye aktarılanlar

Faz 2 (LTZ-KER-AUTH-02 — Giriş, JWT, Oturum ve 2FA) öncesi karar gereken konular:

- D1 JWT: RS256 + JWKS; `jjwt` yerine Spring `NimbusJwtEncoder` önerisi.
- D2 Cookie + CSRF modeli (Gateway, Next.js SSR, resource server).
- D3 Hesap durumu değişiminin eski token'lara etkisi ve kabul edilen azami gecikme.
- D5 2FA yöntemi (e-posta OTP / TOTP) ve admin zorunluluğu. E-posta OTP seçilirse aynı Mailpit adaptörü ve kod altyapısı (`purpose`) yeniden kullanılır.
- Login yalnız `ACTIVE` hesaplara açılmalı; `PENDING_VERIFICATION` hesap token alamaz.
