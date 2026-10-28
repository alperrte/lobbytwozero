# Auth Bakım Turu — Commit Planı (2026-10-24)

> Bu plan 2026-10-27'de Kerem'in açık talimatıyla 7 commit olarak uygulandı (mesajlar `git log`'da; plandaki metinler taslaktır). Sıra: K1–K5 özellikler, K6 güvenlik düzeltmeleri, K7 belgeler. Mesaj biçimi Kerem'in Auth geçmişindeki standarttır:
> `LTZ-KER-AUTH-XX: Türkçe Başlık`, altında kısa maddeler, tamamlanmayanlar açıkça yazılır. AI atıf satırı yok.
> Test durumu: auth-service 327/327, ops betik testleri 5/5 (Docker açık, JDK 25). Her commit'ten önce ilgili testi çalıştırmak yeterlidir;
> asıl kapı `pre-push` tepe commit'te çalışır.

Sıra bağımlıdır (K2 ve K3, K1'in dosyalarına dayanmaz ama ortak dosyalarda hunk paylaşır). Ortak dosyalarda `git add -p` kullanın;
aşağıdaki "hunk" sütunu hangi parçanın hangi commit'e gideceğini söyler. `git diff -U0 <dosya>` çıktısındaki `@@` sırası aynıdır.

## K1 — Yasak kaldırma, 60 sn token, env üretici

| Dosya | Ne |
| --- | --- |
| `domain/enums/AccountStatus.java` | tamamı |
| `test/.../AdminAccountTest.java` | tamamı |
| `ops/generate-auth-dev-keys.mjs` (`staleDefaults` ve "güncellendi" kısmı; `AUTH_ADMIN_SERVICE_CLIENT_SECRET` satırı K5'te), `ops/generate-auth-dev-keys.test.mjs` | tamamı |
| `application.properties` | hunk 1 (`access-token-ttl` 300 → 60) |
| `docker-compose.yml` | hunk 1 (`JWT_ACCESS_TTL_SECONDS` 60) |
| `.env.example` | hunk 1 (`JWT_ACCESS_TTL_SECONDS=60`) |
| `docs/api.md` | `exp` satırı ve "BANNED → ACTIVE" durum geçişi hunk'ları |

```text
LTZ-KER-AUTH-12: Yasak Kaldırma ve Access Token Ömrünün 60 sn'ye İndirilmesi
- BANNED hesap yönetici kararıyla ACTIVE yapılabilir (gerekçe ve denetim kaydı mevcut akıştan); AdminAccountTest güncellendi
- Access token varsayılan ömrü 300 sn'den 60 sn'ye düşürüldü (application.properties, docker-compose.yml, .env.example):
  askıya alınan/yasaklanan hesap diğer servislerde en fazla 60 sn görünür (D3 gecikmesi 5 dk'dan 60 sn'ye)
- ops/generate-auth-dev-keys.mjs: değeri tam olarak eski varsayılan (300) olan JWT_ACCESS_TTL_SECONDS satırını 60'a çeker;
  özelleştirilmiş değere dokunmaz, CRLF korunur, değerleri ekrana yazmaz
- Süreli askı/otomatik açılma yok (yalnız elle); sıfır gecikmeli iptal yayılımı (D3 tam çözüm) hâlâ açık
```

## K2 — Parola politikası

| Dosya | Ne |
| --- | --- |
| `PasswordPolicy.java`, `PasswordPolicyTest.java`, `resources/password-blocklist.txt` | yeni, tamamı |
| `RegistrationService.java`, `PasswordService.java` | tamamı |
| `RegistrationControllerTest.java`, `PasswordChangeTest.java`, `PasswordResetTest.java` | tamamı |
| `AuthErrorCode.java` | hunk 1 (`WEAK_PASSWORD`) |
| `Account.java` | hunk 1 (`PASSWORD_MIN_LENGTH = 15`) |
| `docs/api.md` | kayıt (`password`, `WEAK_PASSWORD`), parola değiştir ve sıfırlama hata hunk'ları |

```text
LTZ-KER-AUTH-01/09/10: Güçlü Parola Politikası (NIST SP 800-63B-4)
- Parola en az 15 karakter (önceki 12); yerel yaygın parola kara listesi, tekrar ve ardışık desen, kullanıcı adı/e-posta yerel kısmı
  ve servis adı reddi (PasswordPolicy); yeni WEAK_PASSWORD (400) hatası
- Yalnız kayıt, parola değiştirme ve sıfırlamada uygulanır; girişe dokunmaz, eski parolalı hesaplar etkilenmez.
  Sıfırlamada zayıf parola token'ı tüketmez
- PasswordPolicyTest (17) ve kayıt/değiştirme/sıfırlama entegrasyon testleri
- Sızmış parola veritabanı (k-anonimlik sorgusu) kullanılmıyor; liste yereldir (S10 kısmen kapandı)
```

## K3 — Silme geri alma süresi, olay saklama, e-posta değiştirme (V65, V66)

Bu üç iş `AuthErrorCode`, `SecurityEventType`, `Account`, `SecurityConfig`, `AuthApiPaths` ve `api.md` içinde iç içe geçtiği için tek commit'tir.

| Dosya | Ne |
| --- | --- |
| `db/migration/V65__...sql`, `V66__...sql` | yeni |
| Silme: `AccountDeletionService/Coordinator`, `AccountDeletionRequest/Task`, `DeletionRequestStatus`, `DeletionProperties`, `AccountDeletionTaskRepository`, `AccountDeletionController`, `AccountDeletionResponse`, `CancelAccountDeletionRequest`, `LoginService` | tamamı |
| E-posta: `EmailChangeService/Controller`, `ChangeEmailRequest`, `ConfirmEmailChangeRequest`, `EmailVerificationCode`, `AccountRepository`, `VerificationPurpose`, `SessionRevokeReason` | tamamı |
| Saklama: `CleanupProperties` | tamamı |
| `Account.java` | hunk 2–6 (hunk 1 K2'de) |
| `AuthErrorCode.java` | hunk 2 |
| `SecurityEventType.java`, `AuthApiPaths.java`, `SecurityConfig.java` | tamamı |
| `application.properties` | hunk 3 ve 4 (`grace-period`, `security-event-retention`) |
| Testler | `AccountDeletionGraceTest`, `EmailChangeTest`, `AccountDeletionServiceErasureTest`, `AccountDeletionUnavailableServicesTest`, `RetentionCleanupTest`, `HttpAccountDataEraserTest`, `DeletionConfigTest`, `FlywayMigrationTest`, `IntegrationTestSupport` |
| `docs/api.md` | e-posta değiştir bölümü ve hesap silme bölümü hunk'ları |

```text
LTZ-KER-AUTH-15/16: Silme Geri Alma Süresi, Olay Saklama ve E-posta Değiştirme
- V65: account_deletion_requests.scheduled_for/cancelled_at ve CANCELLED durumu. Silme onayından sonra 14 gün bekleme;
  görevler süre bitene kadar çalışmaz. POST /account/deletion/cancel ile parola (+TOTP) doğrulamasıyla vazgeçme;
  giriş DELETION_REQUESTED hesapta ACCOUNT_DELETION_PENDING döner
- Bekleme bitince Auth PII'si (e-posta, kullanıcı adı, parola özeti, 2FA, kimlikler, oturumlar) servis onaylarını beklemeden anonimleşir (S1)
- security_events saklama süresi varsayılan 365 gün (docs/database.md), temizlik işi çalışır
- V66: e-posta değiştirme (POST /email/change ve /email/change/confirm); yeni adrese kod, onayda tüm oturumlar iptal,
  bekleyen sıfırlama bağlantıları geçersiz; başka hesabın adresinde yanıt aynı kalır
- AccountDeletionGraceTest (6), EmailChangeTest (7); mevcut silme testleri yeni davranışa uyarlandı; docs/api.md kayıtları
- Açık: parolasız (Google/Steam) hesap silmeden vazgeçemez ve e-posta değiştiremez; eski adrese bildirim ve User Service'e adres
  iletimi yok (B19); production Mail Service hâlâ PENDING
```

## K4 — JWKS ek doğrulama anahtarı, katman düzeltmesi

| Dosya | Ne |
| --- | --- |
| `JwtProperties`, `JwtSigningKey`, `JwtSigningKeyTest`, `TestServiceTokens`; `JwtConfig` (yalnız `additionalPublicKeys` satırı) | tamamı |
| `ConsumerContractTest` (yeni) | Başka servislerin (Game, Hardware, Library, Review) testlerinin yansımayla kullandığı Auth imzalarını kilitler; `JwtProperties` dört parametreli yapıcısı ve `@ConstructorBinding` bu yüzden var |
| `PendingDeletionTask.java` (yeni), `PendingDeletionQueryService`, `PendingDeletionTaskResponse`, `AdminAccountController` | tamamı |
| `application.properties` | hunk 2 (`additional-public-keys`) |
| `docker-compose.yml` | hunk 2 (`AUTH_JWT_ADDITIONAL_PUBLIC_KEYS`) |
| `.env.example` | hunk 2 (rotasyon notu) |

```text
LTZ-KER-AUTH-14: JWKS Ek Doğrulama Anahtarları ve Katman Düzeltmesi
- JWKS imza anahtarının yanında yalnız-doğrulama ek açık anahtarları yayınlayabilir (ltz.auth.jwt.additional-public-keys,
  ENV: AUTH_JWT_ADDITIONAL_PUBLIC_KEYS, varsayılan boş); özel anahtar alanı sızmaz, zayıf/bozuk anahtar açılışta reddedilir
- Rotasyon: yeni anahtar önceden yayınlanır, imza anahtarı değişince eskisi tüketici önbelleği boşalana kadar yayında kalır
- PendingDeletionQueryService artık uygulama katmanı kaydı döndürür, controller DTO'ya çevirir (S11)
- Açık: TOTP şifreleme anahtarı sürümleme ve HMAC anahtarının amaca göre ayrılması (S9 kalanı)
```

## K5 — Servis → Auth iç uçları (admin-service altyapısı, B4)

**Hunk kuralı:** K1–K4'ü yukarıdaki tablolara göre hunk hunk hazırladıktan sonra, ortak dosyalarda (`application.properties`, `.env.example`,
`docker-compose.yml`, `AuthErrorCode`, `SecurityConfig`, `AuthApiPaths`, `JwtConfig`, `IntegrationTestSupport`, `ops/generate-auth-dev-keys.mjs`,
`docs/api.md`) **geriye kalan her şey K5'tir.** K4'ün `JwtConfig` payı yalnız `additionalPublicKeys` satırıdır.

| Dosya | Ne |
| --- | --- |
| `ServiceClientProperties`, `ServiceClientConfig`, `ServiceClientTokenIssuer`, `ServiceClientTokenValidator` | yeni |
| `ServiceClientTokenService`, `ServiceAccountAdministration` | yeni |
| `ServiceTokenController`, `InternalAccountAdministrationController`, `ServiceTokenRequest/Response`, `InternalStatusChangeRequest`, `InternalRoleChangeRequest` | yeni |
| `TokenClaims`, `AuthApiPaths`, `AuthErrorCode` (`INVALID_CLIENT`, `ACTING_ADMIN_INVALID`), `JwtConfig` (`serviceTokenAuthenticationManager`), `SecurityConfig` (iç yollar, kapsam kuralları, yol bazlı doğrulayıcı) | değişiklik |
| `application.properties` (`service-clients`), `docker-compose.yml`, `.env.example` (`AUTH_ADMIN_SERVICE_CLIENT_SECRET`), `ops/generate-auth-dev-keys.mjs` | yeni anahtar |
| Testler: `InternalAccountAdministrationTest` (9), `ServiceClientTokenValidatorTest` (6), `ServiceClientTokenServiceTest` (2), `ServiceClientConfigTest` (5); `IntegrationTestSupport` | yeni |
| `docs/api.md` (iç uçlar), `docs-auth/servis-sozlesmeleri.md` §3.4 ve B4 satırı | belge |

```text
LTZ-KER-AUTH-11/12: Servis Kimlikli İç Uçlar (Admin → Auth Altyapısı)
- POST /internal/v1/service-tokens: kayıtlı servis istemcisi (client credentials) gizli anahtarıyla Auth'tan 60 sn'lik svc+jwt alır;
  aud=auth-service, sub=svc:<istemci>, kapsam istemcinin kayıtlı kapsamlarıyla sınırlı; imzalayan yalnız Auth
- GET/POST /internal/v1/accounts/{id}[/status|/roles/grant|/roles/revoke]: kapsam ile korunur (account:read, account:status:write,
  account:role:write); actorId Auth'ta yeniden doğrulanır (ACTIVE + ADMIN), kurallar kullanıcı uçlarıyla aynıdır ve gevşetilmez
- Yol bazlı doğrulama: iç yollar yalnız servis token'ı, diğerleri yalnız kullanıcı token'ı kabul eder; cookie iç uçlarda okunmaz;
  kullanıcı token'ı, Auth'un çıkış token'ları ve kapsam dışı/uzun ömürlü/eksik claim'li token'lar reddedilir
- Fail-closed: gizli anahtarı boş istemci devre dışı; zayıf anahtar veya bilinmeyen kapsam açılışta reddedilir; tek INVALID_CLIENT yanıtı
- AUTH_ADMIN_SERVICE_CLIENT_SECRET: .env.example, Compose ve ops/generate-auth-dev-keys.mjs (256 bit üretir); /internal Gateway'e açılmaz
- Açık: admin-service tüketicisi yok (uçtan uca denenmedi); gizli anahtar rotasyonu ve prod sır yöneticisi
```

## K6 — Güvenlik düzeltmeleri: tarama (Checkstyle MethodName + PMD CloseResource, test tarafı) ve log gizliliği (Alper talep 04)

Kaynak: `LTZ_SECURITY_SCANS/2026-10-26_1820/Kerem` (Checkstyle 69, PMD 9). Doğrulama `chore/security` kurallarıyla ayrı klasörde yapıldı:
**Checkstyle 0 ihlal** (yeni kodumuz dahil), PMD 9 → 7 (kalan 7 üretim kodunda ve sahibinin kararını bekliyor, aşağıda). Testler 349/349.

**Hunk kuralı:** K1–K6'da yeni eklenen test metotları zaten düzeltilmiş adlarla gelir. K7 yalnız **mevcut** metotların yeniden adlandırılmasıdır
(34 test dosyasında 86 ad: `_` kaldırıldı, sonraki harf büyütüldü; davranış aynı) ve `ErasureTargetStub` + `RequestBodySizeLimitFilterTest` düzeltmeleri.

```text
LTZ-KER-AUTH: Güvenlik Düzeltmeleri (Checkstyle, PMD CloseResource, Log Gizliliği)
- Genel DEBUG/TRACE açılsa bile hassas veri yazabilen 9 logger application.properties'te açıkça INFO'ya sabitlendi: HTTP istemci formları
  (OAuth code/state/client_secret), Hibernate varlık dökümü (e-posta, parola özeti), SQL parametreleri, PostgreSQL sürücüsü, MVC argümanları
- LOG_LEVEL=TRACE, LOG_LEVEL=DEBUG ve DEBUG=true ile Google/Steam testleri 22/22; LoggingPrivacyPinTest pinleri kilitler
- 34 test dosyasında 86 metot adından alt çizgi kaldırıldı (camelCase); davranış değişmedi, 349 test geçiyor
- ErasureTargetStub: yürütücü nesnenin kendisine ait alana taşındı (close() ile kapanır)
- RequestBodySizeLimitFilterTest: ServletInputStream try-with-resources ile kapatılıyor
- Açık (sahip kararı): PMD PreserveStackTrace x5 ve üretimdeki HttpClient CloseResource x2; kural kapatma/bastırma yapılmadı
```

## K7 — Belgeler

| Dosya | Ne |
| --- | --- |
| `docs-auth/acik-guvenlik-borclari.md`, `servis-sozlesmeleri.md` | tamamı |
| `docs-auth/ekip-uyum-denetimi-2026-10-24.md`, `surec-borclari-plani.md`, `commit-plani-2026-10-24.md` | yeni |
| `docs-auth/README.md` (dizin), `docs-auth/verilen-cevaplar/**` (19 branch klasörü, Alper'in 4 talebinin cevapları dahil) | yeni |
| `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md`, `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` | tamamı |

```text
LTZ-KER-AUTH: Bakım Turu Belgeleri ve Ekip Uyum Denetimi
- acik-guvenlik-borclari.md: B2, B3, B6, B8, B9, B17, S1, S9, S10, S11 güncellendi; B19 eklendi
- servis-sozlesmeleri.md: JWT tüketici durumu ve silme uçları (Social, Game, Library, Review hazır; 12 hedef PENDING)
- ekip-uyum-denetimi-2026-10-24.md: servis servis JWT/silme uyumu ve kişiye göre bulgular
- surec-borclari-plani.md ve commit-plani: doğrulama ve teslim sırası
- Ekip rehberleri: token ömrü 60 sn, parola politikası, env üretici davranışı
```

## Tek commit yedeği

`git add -p` zor gelirse K1–K7 tek commit olarak atılabilir; geçmiş okunurluğu düşer ama içerik aynıdır:

```text
LTZ-KER-AUTH-01/09/10/12/14/15/16: Auth Bakım Turu (yasak kaldırma, 60 sn token, parola politikası, silme geri alma, e-posta değiştirme)
- (K1–K5 maddelerinin kısa özeti; ayrıntı docs-auth/commit-plani-2026-10-24.md)
```

## Push öncesi

1. `main` ilerledi (Game/Library/Review silme uçları); PR'dan önce `origin/main` merge edilir (onay gerekir). `docs/api.md` farklı bölümlerde değiştiği için çakışma beklenmez.
2. `node ops/generate-auth-dev-keys.mjs` (yerel `.env`/`.env.dev` TTL'ini 60'a çeker).
3. `.\pre-push\pre-push.cmd service`, sonra Docker duman testi (`surec-borclari-plani.md`).
