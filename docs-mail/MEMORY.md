# Mail Service — HAFIZA (oturum temizlense bile önce bunu oku)

> `/clear` sonrası başlangıç noktası. Güncelleme: 2026-10-31. Sahip: Kerem (oturumda kullanıcı kimliği açıkça söylenmedi; Mail/Auth/User Kerem'in servisleri).
> Sıra: bu dosya → [SPEC.md](SPEC.md) (kesin teknik plan) → [decisions/kararlar.md](decisions/kararlar.md) → [integration/README.md](integration/README.md) → [progress/](progress/).
> Kaynak önceliği: repo kodu > `servis-gereksinimleri` > docx.

## 1. Durum (2026-10-31)

- **Mail Service kodu yazıldı ve testten geçti** (MAIL-01 + MAIL-02 kapsamı): V81/V82, kuyruk + işleyici + SMTP + şifreleme, 4 iç uç + silme ucu, TR/EN şablonlar. Doğrulama (2026-10-31, `mvn verify`, JDK 25.0.5): **54 test PASS** (44 + `SmtpMailSenderTest` 6 + `MailWorkersTest` 4), Checkstyle 0, PMD 0, satır coverage **%88,99** (JaCoCo; hedef ≥%80). **Resmi kapı PASS** (`pre-push mail-service`, klasör ASCII `LTZ-MAIL` yapıldıktan sonra: 54 test, %88,99; §10 çözüldü). Commit `2e51f70`.
- Tamamlanan yan işler: `ops/generate-auth-dev-keys.mjs` (+test), `docker-compose.yml` (mail-service ENV), `.env.example` (8 boş anahtar, kullanıcı ekledi), `ltz-logo.png`, `docs/api.md` `## MAIL` (5 kayıt), `docs-mail/`.
- **Auth `MAIL_SERVICE` adaptörü yazıldı** (2026-10-31): `HttpMailServiceAdapter` (4 port; `scope=mail:send`, yalnız `202` kabul, yönlendirme yok), `MailAdapter.MAIL_SERVICE` (`productionReady=true`), `TokenClaims.MAIL_SEND_SCOPE`. Adres/zaman aşımı hesap silme ayarlarındaki `mail` hedefinden. Testler: `HttpMailServiceAdapterTest` (22), `MailDeliveryConfigTest` (11), `HttpAccountDataEraserTest` (19) PASS. Compose uçtan uca (2026-10-31): Auth `ltz.auth.mail.adapter=mail-service` ile kayıt → Auth `HttpMailServiceAdapter` → Mail `202` → kuyruk → SMTP → Mailpit. `EMAIL_VERIFICATION` kaydı `SENT`, 1 deneme, gönderim sonrası şifreli içerik ve alıcı adresi temizlendi; e-posta TR+EN iki dilli geldi. 4 e-posta akışının (doğrulama kodu, parola sıfırlama bağlantısı, parola değişti bildirimi, hesap silme onayı) hepsi Compose'ta Auth → Mail → Mailpit uçtan uca doğrulandı (2026-10-31, hepsi `SENT`, 1 deneme). Silme ucu (`DELETE /internal/v1/accounts/{accountId}`) Auth silme koordinatörüyle (14 gün bekleme) uçtan uca denenmedi, yalnız `AccountErasureTest` kapsar. Auth'ta kod commit edilmedi (kullanıcı atar).
- **Sıradaki adımlar:** (1) ~~zayıf coverage~~ yapıldı: `SmtpMailSender` gerçek SMTP konuşmasıyla (yerel sahte sunucu), iki worker testli, (2) SMTP sağlayıcısı: şimdilik yok, Mailpit ile devam (karar 2026-10-31), (3) ~~kalan e-posta uçları~~ uçtan uca yapıldı; yalnız silme ucu Auth koordinatörüyle denenmedi, (4) Auth tam test paketi + `pre-push auth-service` kapısı henüz koşulmadı, (5) `WELCOME` / `SUSPICIOUS_LOGIN` (Auth tetikleyicisi yok).
- Repo: dal `feature/mail-service`, `origin/main` (af6e1cc, PR #38 user-service) ile aynı; `git fetch` yapıldı. Uzak `feature/auth-service` 4450435 = yalnız Auth Sonar düzeltmesi (`AUTH-35`), Mail'i etkilemez. `docs-mail/` henüz izlenmiyor (untracked).
- Servis durumları: **gerçek kod** auth, user, game, hardware, community, social, api-gateway; **kısmi** library, review; **iskelet** event, notification, admin, content, lfp, messaging, performance, recommendation, mail.

## 2. Kilit ilke (kullanıcı talimatı)

**Mail'e yalnız Auth istek atar; Mail Auth'un gerçek portlarına/token modeline uyar.** Başka servisten Mail'e çağrı kodda yok (event/notification/admin iskelet, Auth'ta çağıran→mail çifti tanımsız). Auth mantıksız bir şey isterse Auth'a bildirilir (şu an gerek yok). MAIL-06/07 çağıran çıkana dek ertelendi.

## 3. Kimlik kartı

| Konu | Değer |
| --- | --- |
| Servis | `mail-service`, `backend/services/mail-service`, `com.ltz.mail`, `mail_db` (`DB_MAIL_NAME`) |
| Migration | **V81–V90** (V81 şema, V82 şablon seed; başka numara yok) |
| Port / adres | host 5083, container 8080, `MAIL_SERVICE_URL`; Gateway `/api/v1/mail/**` (kullanıcı ucu yok) |
| Görevler | LTZ-KER-MAIL-01 (MAIL-10..16), -02 (MAIL-01..05, 08, 09), -03 (MAIL-06, 07 ertelendi) |
| Stack | Java 25, Spring Boot 4.1.1, JPA, Flyway, `spring-boot-starter-mail`, resource-server (pom'da hazır) |
| Mevcut | `MailServiceApplication`, 1 `contextLoads`, boş katmanlar, `application.properties`, migration yok; `docker-compose.yml`'de `mail-service` bloğu var (SMTP ENV yok) |
| Kalite | Checkstyle/PMD sıfır yeni ihlal, JaCoCo ≥ %80 (iskelet %33), Sonar A |

## 4. Auth gerçekleri (koddan doğrulandı)

- 4 port: `VerificationCodeMailPort` (+ e-posta değişikliği kodu), `PasswordResetMailPort`, `AccountDeletionMailPort`, `SecurityNotificationMailPort` (tipler: `PASSWORD_CHANGED, PASSWORD_RESET, EMAIL_CHANGED (eski adrese), ACCOUNT_SUSPENDED, ACCOUNT_BANNED, ACCOUNT_DISABLED, ACCOUNT_REACTIVATED`; veri: `idempotencyKey, accountId, recipientEmail, type, occurredAt`).
- Çağrı, commit sonrası ayrı iş parçacığında; port istisna → kod/token `DELIVERY_FAILED` + güvenlik olayı (güvenlik bildirimlerinde yalnız log, en iyi çaba).
- Portlar `locale` taşımaz → Mail: `locale` isteğe bağlı, yoksa TR+EN iki dilli.
- Token: `ServiceTokenIssuer.issue(aud, scope, accountId)` (`aud` `…-service` kalıbı, scope `^[a-z]+:[a-z]+$`, **accountId zorunlu**, ömür 30 sn, `sub=svc:auth-service`, `account_id` claim). Silme deseni: `HttpAccountDataEraser` (yönlendirme yok, 200/204 dışı hata, 5 sn zaman aşımı).
- `MailDeliveryConfig`: prod profili yalnız `productionReady` adaptörle açılır (şu an yok → B5). Dev: `ltz.auth.mail.adapter=dev-smtp` (Mailpit `mailpit:1025`, Compose profili `dev-mail`).
- Auth'un event relay'i Mail'e olay göndermez; Mail olay tüketmez.

## 5. Kesinleşen mimari (ayrıntı SPEC.md)

- 4 iç uç (Auth portlarına bire bir) + `DELETE /internal/v1/accounts/{id}`; hepsi `svc+jwt` (`aud=mail-service`, `sub=svc:auth-service`, `scope=mail:send` / `account:erase`, `account_id`=gövde/yol).
- `202` yalnız kalıcı kayıttan sonra. Idempotency `idempotency_key` UQ + `request_hash` (farklı içerik `409`).
- Kuyruk: `FOR UPDATE SKIP LOCKED` kiralama, 5 sn poll, üstel geri çekilme (1 dk→6 sa, 8 deneme), `expires_at` (kod geçerliliği/24 sa), durumlar PENDING/PROCESSING/SENT/FAILED.
- Hassas veri: payload AES-256-GCM (`MAIL_PAYLOAD_ENCRYPTION_KEY`), terminalde silinir; tam adres terminalde NULL + maskeli kopya; kod/bağlantı/adres asla loglanmaz.
- SMTP: elle kurulan `JavaMailSenderImpl` (health göstergesi yok), TLS prod'da zorunlu; şablonlar DB + V82 seed, yeni bağımlılık yok, HTML-escape + CRLF temizliği, TR/EN/iki dilli.
- Test: aynı fazda, Testcontainers PostgreSQL + yerel JWKS (User deseni).

## 6. Onaylar ve yapılanlar (2026-10-31, kullanıcı yanıtı)

| # | Konu | Durum |
| --- | --- | --- |
| 1 | SMTP ENV'leri | **YAPILDI:** `ops/generate-auth-dev-keys.mjs` içinde `blankDefaults`: `MAIL_SMTP_HOST/PORT/USERNAME/PASSWORD/SECURITY`, `MAIL_FROM_ADDRESS`, `MAIL_ALLOWED_LINK_HOSTS` satır yoksa dosya sonuna **boş** eklenir, var olana dokunmaz. Sağlayıcı seçilince elle doldurulur; boş iken dev'de Mailpit varsayılanları geçerli. Test eklendi (`generate-auth-dev-keys.test.mjs`, 6/6 PASS) |
| 2 | `MAIL_PAYLOAD_ENCRYPTION_KEY` | **YAPILDI:** aynı betikte üretici (32 bayt Base64, boş/yoksa doldurur, doluyu ezmez). Betik henüz **çalıştırılmadı**: kullanıcı `node ops/generate-auth-dev-keys.mjs` ile `.env`/`.env.dev`'i doldurur |
| 3 | `testcontainers-postgresql` | **ONAYLI** (Docker kullanımına izin verildi; pom'a kodlama fazunda eklenecek) |
| 4 | Logo | **YAPILDI:** `C:\Users\gsker\Desktop\logo.png` (512×512, 186 KB) → 256×256 RGBA, 56 KB → `backend/services/mail-service/src/main/resources/mail/ltz-logo.png`. E-postaya CID (inline) ek olarak gömülür; uzak URL/izleme yok. Görseli kontrol etmedim |
| 5 | `docker-compose.yml` `mail-service` bloğu | **YAPILDI:** 8 ENV aktarımı (`${...:-}`; hepsi boş varsayılan, fail-fast kontrolü uygulamada) |
| 6 | `.env.example` | **YAPILDI** (kullanıcı ekledi): `MAIL_PAYLOAD_ENCRYPTION_KEY`, `MAIL_SMTP_HOST/PORT/USERNAME/PASSWORD/SECURITY`, `MAIL_FROM_ADDRESS`, `MAIL_ALLOWED_LINK_HOSTS`; hepsi boş |
| 7 | Auth adaptör görevi | Ayrı iş, Mail bittikten sonra |

`.env*` dosyaları okunmaz/yazılmaz; yalnız `ops` betiği kullanıcı tarafından çalıştırılır.

## 7. Bağlayıcı kurallar (özet)

- Katmanlar `api→application→domain`, `infrastructure` yalnız port uygular; DTO = `record`; entity API'ye dönmez; hata `ProblemDetail`; Jackson `fail-on-unknown-properties`; Swagger yalnız `SWAGGER_ENABLED`.
- DB: `uuid` PK, `timestamptz`, `version`, durum `varchar`+CHECK, REF (FK yok), `ddl-auto=validate`, uygulanmış migration değişmez.
- Her endpoint aynı görevde `docs/api.md` `## MAIL`'e yazılır.
- **`git commit/push/add` yok**; yalnız `feature: ...` formatlı commit mesajı önerilir, atıf satırı yok (`git fetch` yapıldı: yalnız uzak dalları indirdi).
- Doğrulama: JDK 25 (`C:\Program Files\Java\jdk-25.0.5`), `mvn -B -f backend/pom.xml -pl :mail-service verify`, `.\pre-push\pre-push.cmd mail-service` (`service` geçerli mod değil), `lint-ratchet check --modules services/mail-service` (çalıştırılmadı). Bkz. §10.
- Windows: gerçek Python `/c/Users/gsker/AppData/Local/Python/bin/python.exe` (WindowsApps `python` Store yönlendiricisi); bash'te ileri eğik çizgi kullan; docx okuma örneği AGENTS.md §2.
- Docx (okundu): yalnız "hesap doğrulama, şifre sıfırlama, güvenlik uyarıları, sistem bilgilendirme e-postaları; şablonlar ve SMTP; `mail_db`" der.

## 8. Fazlar (ayrıntı `progress/`)

1. **MAIL-01 (altyapı):** KOD + TEST TAMAM (54 test PASS); resmi kapı PASS (%88,99). Mailpit ile uçtan uca doğrulandı (doğrulama kodu akışı).
2. **MAIL-02 (hesap e-postaları):** KOD + TEST TAMAM (10 tür × TR/EN, V82 seed, `TemplateRendererTest`); `WELCOME` ve `SUSPICIOUS_LOGIN` şablonları yok (Auth tetikleyicisi yok).
3. **Auth adaptör görevi:** KOD + TEST TAMAM (2026-10-31). `MAIL_SERVICE` adaptörü; prod artık bu adaptörle açılır (B5 kapandı, B12/B19 kısmen).
4. **MAIL-03:** ertelendi; çağıran çıkınca.

## 10. Ortam sorunu: klasör adındaki `İ` — ÇÖZÜLDÜ (2026-10-31)

Klasör `LTZ-MAIL` (ASCII) yapıldı; kapı PASS. Aşağısı tarihçe:

Repo `...\LTZ\LTZ-MAİL\...` altında. Windows'ta bu yol JaCoCo ajanına `LTZ-MAÝL` olarak (bozuk) geçiyor; `jacoco.exec` yanlış klasöre yazılıyor, `jacoco.xml` üretilmiyor ve `coverage-policy` / `pre-push` kapısı `Missing fresh report ... jacoco.xml` ile düşüyor. Testler ve Checkstyle/PMD etkilenmiyor. Kanıt: aynı kod ASCII yolda (`C:\ltz-ascii` kopyası) `jacoco.xml` üretti (satır %81,7). `subst`/junction işe yaramadı (Java yolu gerçek haline çeviriyor).
- **Çözüm (kullanıcı kararı):** repo kökünü ASCII adlı klasöre taşı/kopyala (ör. `LTZ-MAIL`) ve kapıyı orada koştur: `.\pre-push\pre-push.cmd mail-service`. Bu oturumda klasör yeniden adlandırılmadı.
- Ayrıca coverage-policy `git merge-base` ister; `.git` olmayan kopyada çalışmaz.
- Maven'i `-pl services/mail-service` yerine `-pl :mail-service` ile seçmek gerekebilir (yol eşleşmesi).
- Gerekirse `JAVA_HOME` = `C:\Program Files\Java\jdk-25.0.5` (dokümandaki 25.0.4.1 yolu bu makinede yok).

## 9. Kaynak dosyalar

`AGENTS.md`, `CLAUDE.md`, `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Kerem.md`, `docs/{SECURITY,architecture,database,api,folder-structure,engineering}.md`, `docs/decisions/migrations.md`, `docs-auth/{servis-sozlesmeleri,acik-guvenlik-borclari}.md`, `docs-auth/entegrasyon/mail-service.md`, `docs-auth/verilen-cevaplar/mail-service/README.md`, `docs-security/mail-service/*`, Auth: `application/port/*Mail*.java`, `application/service/*Dispatcher.java`, `infrastructure/client/{HttpAccountDataEraser,MailServiceUnavailableAdapter,DevSmtpVerificationMailAdapter}.java`, `infrastructure/security/ServiceTokenIssuer.java`, `infrastructure/config/MailDeliveryConfig.java`. Örnek servis: `backend/services/user-service` (`JwtConfig`, `SecurityConfig`, `AccountErasureController`, `IntegrationTestSupport`) + `docs-user/`.
