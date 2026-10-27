# LTZ-HAM-GAME-04 — Game Service / Auth Service entegrasyonu

Tarih: 2026-10-22 · Sorumlu: Hamza · Branch: feature/game-service

## 1. Amaç ve kapsam

Game Service, mevcut Auth Service'in RS256 / `at+jwt` / JWKS sözleşmesine uyarlandı.
Auth, Gateway, diğer mikroservisler ve Game migration dosyaları değiştirilmedi.
Auth sabit ve salt okunur referanstır; Auth token üretimi Game'e uydurulmadı.

Doğrulama iki ayrı katmanda yürütüldü:

- **A — Java entegrasyonu:** `RealAuthIntegrationTest`, gerçek Auth üreticisi ve
  JWKS controller'ını çalıştırır; Gateway'in gerçek filtrelerini Game güvenlik
  zinciri önüne koyar. Game iş akışları geçici Testcontainers PostgreSQL'e yazar.
- **B — Gerçek HTTP:** Ayrı JVM'lerde çalışan Auth, Gateway ve Game; geçici
  PostgreSQL ve Mailpit ile kayıt → e-posta doğrulama → login → Game akışı.
  Bu çalışma mevcut DEV dağıtımının sağlıklı olduğunu kanıtlamaz.

İlk kalite kapanışı §10'dadır. Açık ENV yazma onayı sonrasındaki en güncel DEV
doğrulaması §12'de kayıtlıdır. Eski `BLOCKED`/`NOT VERIFIED` durumları tarihsel
kayıttır; güncel sonuç olarak kullanılmaz.

## 2. Referanslar ve giderilen Game eksikleri

AGENTS.md, Hamza görevleri (GAME-07/12/13/14/15 ve güvenlik testleri), genel
gereksinim DOCX'i, SECURITY.md, architecture.md, database.md, er-diagram.md,
folder-structure.md, web-rules.md, api.md, AUTH_SERVICE_ENTEGRASYON_REHBERI.md,
EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md, docs-auth/servis-sozlesmeleri.md ve
acik-guvenlik-borclari.md incelendi.

Auth referansları: JwtConfig, JwtProperties, AccessTokenIssuer, JwtSigningKey,
TokenClaims, JwksController, SessionStateValidator, SecurityConfig, AuthApiPaths,
Account ve AuthSession. Gateway referansları: CookieBearerFilter,
ClientRequestSanitizerFilter, GatewaySecurityConfig, GatewaySecurityProperties
ve route yapılandırması. Game güvenliği, Actor çözümlemesi, erişim politikaları,
grant kilitleri, 14 controller ve ilişkili testleri incelendi.

Giderilen eksikler:

- RS384/RS512 kabulü kaldırıldı; açık `at+jwt` doğrulaması eklendi.
- Zorunlu `iat`/`nbf`, gelecekteki `iat`, başlıkta `kid` ve UUID `sub` denetlendi.
- Metin, `ROLE_`, boşluk ve küçük harf rol esnekliği kaldırıldı.
- Üretim properties'i mevcut Auth ENV alanlarına bağlandı.
- Eski Game test token'ları Auth biçimine taşındı; `USER` yerine `PLAYER` kullanıldı.

Ortak belgelerdeki eski “servislerde güvenlik yok” ifadeleri gerçek kodla
çelişmektedir. Kaynak kod esas alındı; başka sahiplerin belgeleri değiştirilmedi.

## 3. JWT ve JWKS yapılandırması

```properties
ltz.security.jwt.issuer=${JWT_ISSUER:ltz-auth}
ltz.security.jwt.audience=${JWT_AUDIENCE:ltz-api}
ltz.security.jwt.jwk-set-uri=${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json
```

Bu ENV alanları zaten vardır; yeni kalıcı anahtar eklenmedi. Auth private key'i
Game'e verilmez. `AUTH_SERVICE_URL` yoksa üretim başlangıcı güvenli biçimde durur.

- Yalnız RS256. Nimbus `validateType(false)` ile type kontrolünü açık
  `JwtTypeValidator(at+jwt)` doğrulayıcısına devreder.
- `iss`, `aud` ve kanonik UUID `sub` zorunludur; kimlik başka claim'den alınmaz.
- `exp`, `nbf`, `iat` zorunludur. `JwtTimestampValidator` süre/nbf kontrolünü yapar;
  `iat` toleransın ötesinde gelecekte olamaz. Tolerans 0–60 saniye, varsayılan 60'tır.
- `kid` boş olmayan metindir; JWKS anahtarı `kid` ve imzayla seçilir.
- Nimbus süreç içi JWKS önbelleği kullanılır (varsayılan yaklaşık 5 dakika).
  Bilinen anahtarın tekrar indirilmemesi ve yeni `kid` ile yenileme testlidir.
- Alınamayan/bozuk JWKS ile doğrulanamayan token reddedilir. Önbellekteki geçerli
  anahtarla doğrulanabilen token için her istekte Auth erişimi gerekmez.
- İmza kaynağı yokluğunda `FailClosedJwtDecoder` korunur. İki kaynak, hatalı
  algoritma, eksik issuer/audience, yanlış claim veya aşırı tolerans başlangıcı durdurur.
- Sabit public key alternatifi izole testlerde kullanılır; test JWKS ayarı açıkça
  boşaltılır. Fail-closed testleri her iki imza kaynağını da boşaltır.
- JWKS adresi HTTP(S), host içeren, user-info/query/fragment taşımayan sunucu ayarıdır.

Eski esnek token biçimleri artık kabul edilmez. Auth'un kanonik token biçimi
değişmemiştir. [Spring Resource Server JWT belgesi](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
çerçeve davranışı için referanstır.

## 4. Actor, roller ve endpoint sınırları

Actor, ActorAuthentication, CurrentActorArgumentResolver, ActorRole,
GameAccessPolicy ve repository kilitleri korundu. Controller'lar Actor kullanır;
doğrulanmış `sub` audit/feedback aktörüne dönüşür. İş mantığı claim okumaz.

`ADMIN`, `MODERATOR`, `GAME_DEVELOPER` mevcut domain yetkileridir. `PLAYER` ve
`CONTENT_CREATOR` kimlikli kullanıcıdır; ayrıcalıklı ActorRole eklenmedi.
`roles` yalnız metin listesidir ve adlar birebir eşleşir. Metin/`ROLE_`/küçük harf
biçimleri ayrıcalık vermez. Eksik/yanlış türde rol claim'i boş yetki üretir;
kimlikli feedback erişimi ile ayrıcalıklı endpoint erişimi ayrı denetlenir.

Tüm yolların öneki `/api/v1/game`'dir. `SecurityBoundaryTest`, gerçek route
kaydından 29 korumalı method/path çıkarır: anonim 401, feedback dışındaki 26 uçta
PLAYER 403 ve `application/problem+json` kontrol edilir.

### ADMIN — 14 method/path

- POST /admin/games
- GET ve PATCH /admin/games/{gameId}
- POST /admin/games/{gameId}/archive
- POST /admin/games/{gameId}/restore
- POST /admin/games/{gameId}/manual-overrides/{field}/release
- PUT /admin/popular
- GET ve POST /admin/games/{gameId}/developers
- POST /admin/games/{gameId}/developers/{developerUserId}/revoke
- GET /admin/games/{gameId}/achievements
- PUT /admin/games/{gameId}/achievements/{code}
- POST /admin/identity/reassign
- POST /admin/identity/split

### GAME_DEVELOPER — 11 method/path

- GET /developer/games
- GET /developer/games/{gameId}/content
- PUT /developer/games/{gameId}/description
- POST /developer/games/{gameId}/media
- DELETE /developer/games/{gameId}/media/{mediaId}
- POST /developer/games/{gameId}/update-notes
- PUT ve DELETE /developer/games/{gameId}/update-notes/{noteId}
- GET /developer/games/{gameId}/feedback
- GET /developer/games/{gameId}/feedback/{id}
- PATCH /developer/games/{gameId}/feedback/{id}/status

Oyun bazlı işlemler ayrıca aktif grant ister. Yanlış oyun, eksik/iptal grant ve
grant olup rol olmaması reddedilir. İçerik/feedback IDOR, eşzamanlı grant iptali
ve gerçek Auth token'ıyla grant/iptal/çapraz oyun testleri korunur.

### Diğer sınırlar

- ADMIN veya MODERATOR: POST /moderation/games/{gameId}/developer-content/actions.
- Authenticated/Owner: GET ve POST /feedback; GET /feedback/{id}.
  Başka yazarın kaydı 404; ADMIN/MODERATOR özel yazara erişim vermez.
- Mevcut 11 public katalog/oyun okuma method/path anonim kalır. Batch yalnız
  JSON ile anonim ve sınırlıdır. Public istekte geçersiz Bearer yine 401'dir.
- Tanımlanmayan yollar `denyAll`; fiziksel oyun silme ucu eklenmedi.

## 5. Gateway, CSRF ve hata davranışı

Game güvenlik zinciri/CSRF politikası değişmedi. Game yalnız Authorization Bearer
kabul eder; access cookie doğrudan Game'de kimlik sağlamaz. Gateway cookie'yi
Bearer'a çevirmeden önce mutasyonda CSRF cookie/başlık ve Origin kontrolü yapar.

Java entegrasyonunda gerçek Gateway sanitizer/cookie filtreleri, HTTP çalışmasında
gerçek Gateway routing kullanıldı. Geçerli cookie ve CSRF/Origin mutasyonu
Game'e ulaşır; eksik/yanlış CSRF ve yasak Origin 403 olur. Sahte X-User-Id/X-Role
ayrıcalık vermez. Tokensız korumalı uç 401'dir. Bearer ile doğrudan Game POST
çerez tabanlı CSRF gerektirmez. 401/403 `ProblemDetail` biçimindedir.

## 6. Test katmanları ve kanıt sınırları

`ProductionAuthSupport`, seçilmiş 13 Auth ve 6 Gateway üretim Java kaynağını
geçici dizine JavaCompiler ve mevcut Lombok ile derler. Kaynaklar değiştirilmez;
yeni Maven veya çalışma zamanı servis bağımlılığı eklenmez. 3072-bit test özel
anahtarı bellektedir; Game gerçek JWKS controller yanıtından açık anahtarı alır.
Bu Java katmanındaki hesap/oturum nesneleri test içindir; HTTP kayıt/login kanıtı değildir.

- `RealAuthIntegrationTest`: 3 test; gerçek issuer/JWKS, PLAYER feedback/IDOR,
  ADMIN audit, geliştirici grant/rol/iptal ve Gateway filtre/CSRF akışları.
- `AuthJwtContractTest`: 30 test; bozuk imza, eksik/zamanı yanlış claim,
  yanlış iss/aud/typ, eksik typ/kid, geçersiz UUID, none/HS256/RS384/RS512,
  bilinmeyen kid, yenileme, önbellek ve JWKS arızaları.
- `SecurityBoundaryTest`: 29 korumalı route matrisi, rol/kimlik başlığı sınırları.
- Diğer regresyonlar: özel feedback, kullanıcı/IP kotası, audit rollback,
  oyun kilidi, grant iptal yarışı, migration geçmişi ve içerik yönetimi.

Hedefli güvenlik çalışması 48 test PASS; tüm Game çalışması 277 test PASS'tır.
Gerçek HTTP katmanı Java test sayısına eklenmez; ayrı 44 kontrol olarak raporlanır.

HTTP çalışması mevcut DEV DB'lerini kullanmadı. Normal kayıt/e-posta doğrulama/login
uygulandı; Auth DB'sine etkinleştirme veya rol yükseltme SQL'i yazılmadı.
Loopback HTTP için mevcut DEV cookie ayarı kullanıldı; MFA kuralları gevşetilmedi.
ADMIN provisioning/2FA ve GAME_DEVELOPER mutasyonları gerçek HTTP'de **NOT VERIFIED**;
Java katmanının kapsamı bunların canlı doğrulandığı anlamına gelmez.

## 7. D3, D4 ve hesap silme

Game `sid`/`ver` ile bağımsız oturum doğrulamaz, Auth DB'sini sorgulamaz.
D3 iptal yayılımı uygulanmadı. Auth varsayılan access ömrü 5 dakika; Game'in
60 saniye toleransı dikkate alınmalıdır. Anlık rol/durum/oturum iptali garanti
edilmez; Auth sahibi ve ortak olay/inbox tasarımıyla koordinasyon gerekir.

D4 Auth→servis yönü Auth tarafında uygulandı; Game de 16 silme hedefi arasındadır.
2026-10-24'te Game'e ayrı servis JWT doğrulaması ve güvenli iç silme ucu eklendi.
İlk politika bekleme raporu §13'tedir. Kullanıcının daha sonraki Kerem'le ortak
karar teyidiyle onaylanan politika V171 ile uygulandı (§14): yalnız temizleme ve
son REF denetimi tamamlanınca `204`, geçici hata/eksik işlemde `503` döner.
Admin→Auth servis kimlikli iç uç ayrı, bekleyen bir bağımlılıktır.

Silme/anonimleştirme kararı gereken Game kullanıcı REF envanteri:

- games.editor_actor_id
- game_field_overrides.actor_id
- game_identity_audits.actor_id
- game_admin_audits.actor_id (append-only audit)
- game_developer_grants.developer_user_id, granted_by, revoked_by
- game_developer_contents.author_id, game_developer_media.author_id
- game_update_notes.author_id
- game_developer_feedback.author_id

Saklama, anonimleştirme, audit istisnası ve aktif grant kapatma Hamza/Kerem
koordinasyonu gerektirir. Çalışan JWT doğrulaması bu bağımlılıkları kapatmaz.

## 8. İzolasyon ve çalışma alanı

Testler, görev etiketli PostgreSQL 17 konteynerinde 17 geçici DB ve gerçek HTTP
için ayrı DB'lerde yürütüldü. Rastgele loopback portu mevcut DEV portundan farklıdır;
DEV/Sonar veri volume'leri paylaşılmadı. 1153 kaynak/araç dosyasının test kopyasıyla
SHA-256 eşitliği doğrulandı. Auth/Gateway kopyalarında kaynak uyarlaması yapılmadı.

Bu ek çalışmada açık kullanıcı izniyle mevcut ENV/Sonar token'ı güvenli okundu.
Token yalnız işlem ortamında kullanıldı; değeri rapora/loga/komut argümanına yazılmadı.
İlk kalite çalışmasında kalıcı ENV değiştirilmedi. §12'deki ayrı, açık kullanıcı
onayıyla yalnız üç yerel Auth anahtarı daha sonra dolduruldu. Geçici test profili
yalnız test kimlikleri içerir.

§10'a kadarki çalışmada DEV'e SQL veya Compose deploy/rebuild uygulanmadı.
§12'de onaylı DEV başlatması yapıldı. Hiçbir aşamada volume reset/prune veya
Flyway clean/repair/baseline uygulanmadı. V31–V40 değişmedi. Önceki çalışmada Docker Desktop açıldığında mevcut
konteynerlerin restart politikaları devreye girdiği için tarihsel süreç durumlarının
aynı kaldığı iddia edilmez. DEV verisinin bit düzeyinde eşitliği **NOT VERIFIED**.

Başlangıç dalı origin/feature/game-service'e göre 28 commit ilerideydi.
Add/commit/push/merge/rebase/reset veya branch değişimi yapılmadı.

## 9. Değişiklik envanteri

Üretim: JwtDecoderConfiguration.java, JwtSecurityProperties.java,
LtzJwtAuthenticationConverter.java ve application.properties (yalnız Game).

Mevcut Game testleri: AuthenticatedApiTest, PostgresTestSupport,
AdminGameIdentityControllerTest, GameFeedbackTest, GameModuleTest,
MigrationTransferApplicationTest, FailClosedSecurityTest,
LtzJwtAuthenticationConverterTest, SecurityBoundaryTest.

Yeni test/destek: AuthJwtContractTest, ProductionAuthSupport, RealAuthIntegrationTest.
Belgeler: bu dosya ve docs/api.md'nin yalnız GAME bölümü.

Toplam 18 dosya: 15 mevcut değişiklik ve 3 yeni test/destek dosyası.
Controller/DTO/entity/migration veya Auth/Gateway kodu değiştirilmedi.
Ortak pre-push/Sonar araçları değiştirilmedi. Göreve özel denetim betikleri ve
kanıtlar Git dışında `test-results/game-auth-final/` altında tutulur.

## 10. Nihai kalite kapatma kaydı

### A. ENV ve Sonar erişimi

Mevcut DEV profilinin token'ı mevcut/geçerli; sunucu `UP`, sürüm 26.9.0.129388.
Kabukta SONAR_TOKEN yoktu; mevcut `.env.dev` değeri yalnız bellekte kullanıldı.
Gerçek ENV dosyaları değişmedi. Son raporlarda gerçek secret eşleşmesi bulunmadı.

### B. Pre-push

- Game: **PASS — BACKEND TEST CHECK PASSED**, 277 test, failure/error/skipped 0/0/0.
  JaCoCo LINE 1501/1520 = **%98,75**.
- Backend: **FAIL — BACKEND TEST CHECK FAILED**. Maven verify ve 562 test PASS;
  failure/error/skipped 0/0/0. Auth 252 test / %93,07; Gateway 18 / %94,19.
  Toplam JaCoCo LINE 3867/4089 = %94,57; kapı her servisi ayrı değerlendirdiğinden
  bu toplam düşük kapsamlı modüllerin başarısızlığını kaldırmaz. Sonar düzeltmesi
  yalnız Game testindeki regex'tir; sonrasında 277 Game testi yeniden geçirilmiştir.
- Eşik her modül için %80'dir. Aşağıdaki 15 modülün her biri 1 test PASS fakat
  **%33,33 line coverage** sonucuyla ortak pre-push kapısını düşürür:
  user, mail, library, review, social, messaging, lfp, community, content,
  event, hardware, recommendation, performance, admin, notification.
  Bunlar kapsam dışı **BLOCKED / SERVICE OWNER ACTION REQUIRED** bulgularıdır.
- Full DEV: Compose doğrulaması **FAIL**, tam `full dev` koşusu **BLOCKED**.
  Gerçek DEV profilinde AUTH_JWT_PRIVATE_KEY, AUTH_OTP_HMAC_KEY,
  AUTH_TOTP_ENCRYPTION_KEY alanları boş; Compose interpolation durur.
  DEV PostgreSQL başlangıçta ve son denetimde kapalıdır. Gateway health 200,
  diğer uygulamaların çoğu erişilemez/yeniden başlar; Game'in yayınlı HTTP portu yoktur.
  Mevcut DEV profilini düzeltme bu görevin kapsamı dışındadır.

İlk backend denemesinde ajanın devraldığı DEBUG ortam değişkeni Spring/JDBC
debug loglamasını etkinleştirdi; Auth'un iki log-gizlilik testi başarısız oldu.
Auth kodu/testi değiştirilmeden yalnız test yürütücüsünün DEBUG/TRACE ortamı
temizlendi. Aynı 270 Auth kaynak dosyasıyla hedefli 22 test, ardından tam 252
Auth testi PASS oldu. Son backend sonucu temiz işlem ortamına aittir.
[Spring Boot loglama belgesi](https://docs.spring.io/spring-boot/reference/features/logging.html)
debug modunda etkinleşen çekirdek loglayıcıları açıklar.

### C. SonarQube

**Gerçek analiz SUCCESS; Quality Gate FAIL.** Son analiz kimliği
`068e0475-7b11-41d2-8f5f-84b24961f467`, işleme zamanı 2026-10-22 18:36:01 UTC.
[Yerel Sonar dashboard](http://localhost:5199/dashboard?id=ltz-backend).

- Güncel asıl kaynaklarda 950 dosya indekslendi; SCM ignore nedeniyle dışlanan 0.
  İzole kopyanın taze bytecode, test ve JaCoCo raporları kullanıldı; ilave kaynak
  dışlaması, gate değişikliği veya issue bastırma yapılmadı.
- Overall security/reliability/maintainability: **A/A/A**. Yeni kod notları da A/A/A.
- Backend Sonar coverage **%90,2**, yeni kod **%90,7**.
  Bu satır+koşul ölçüsüdür; Game JaCoCo LINE %98,75 ile aynı kapsam/ölçü değildir.
- Duplication **%0,6**, yeni kod **%0,41082**.
- Açık issue **49**: **46 Auth + 3 Gateway**, tamamı code smell. Game **0**.
  Bug **0**, vulnerability **0**, security hotspot **0**.
- Başarısız koşul: `new_violations = 49`, izin verilen **0**.
  Yeni coverage ≥%80 ve duplication ≤%3 koşulları PASS. Overall hedefler PASS
  olması Quality Gate'in geçtiği anlamına gelmez.
- Yeni kod dönemi sunucunun mevcut `PREVIOUS_VERSION`, 2026-10-20 21:51:27 UTC
  tabanıdır; dönem/kalite politikası değiştirilmedi. İlk gerçek analiz 50 bulgu
  buldu; Game S9142 düzeltmesi ve tekrar test/tarama sonrası 49'a indi.
- Commit edilmemiş/yeni Game dosyalarında scanner missing-blame uyarısı vardır.
  Kaynaklar analiz edildi; SCM satır atıflarının tamamlığı iddia edilmez. Kullanıcı
  talimatı gereği bu uyarıyı gidermek için commit veya Git değişikliği yapılmadı.

Auth/Gateway dosya, satır, kural ve öneri listesi §11'dedir. **AUTH OWNER ACTION
REQUIRED — Kerem** / **GATEWAY OWNER ACTION REQUIRED**. Kodlarına dokunulmadı.

### D. Auth entegrasyonu

- A katmanı: 48 hedefli güvenlik testi ve 277 Game testinde gerçek üretici/JWKS,
  rol/grant/iptal, audit, endpoint ve IDOR kontrolleri PASS.
- B katmanı: üç gerçek HTTP sunucusunda **44 kontrol PASS, 0 başarısız**.
  İki PLAYER normal kayıt, gerçek SMTP doğrulama kodu ve login ile oluşturuldu.
  HttpOnly access cookie, Auth JWKS ile kriptografik imza ve header/claim sözleşmesi doğrulandı.
- Gateway cookie→Bearer ve doğrudan Game Bearer PASS; doğrudan Game cookie 401.
  PLAYER→ADMIN ve sahte rol başlığı 403; tokensız/bozuk Bearer 401/ProblemDetail.
- Geçerli cookie+CSRF+Origin ile feedback 201; sahibi 200, diğer gerçek PLAYER 404.
  Eksik/yanlış CSRF ve yasak Origin 403; doğrudan Bearer POST 201.
- HTTP ADMIN/2FA ve GAME_DEVELOPER grant mutasyonu **NOT VERIFIED**. Mevcut
  DEV dağıtımına hesap eklenmedi, Auth DB rol/etkinleştirme SQL'i kullanılmadı.

### E. Düzeltmeler

Game sözleşme düzeltmeleri §2/§9'dadır. Bu ek kalite çalışmasında yeni üretim
kodu değişikliği gerektiren test hatası bulunmadı. Sonar `java:S9142`,
SecurityBoundaryTest:35'te route döngüsü içinde `String.replaceAll` ile her kez
derlenen regex'i buldu. Aynı ifade `PATH_VARIABLE` sabit Pattern'ine taşındı;
29 endpoint/401/403 assertion'ı korunarak tam Game pre-push tekrar çalıştırıldı.
İşlem ortamı debug sızıntısı yalnız geçici yürütücüde giderildi. Windows cmd.exe
uzunluk sınırına takılan ilk tarama aktarımı, aynı Maven launcher'ını Java ile
doğrudan başlatan geçici yürütücüyle aşıldı; ortak araç değiştirilmedi.
Belgedeki Türkçe karakterler düzeltildi; GAME API notu gerçek HTTP kanıtıyla
güncellendi. Başka servis testi atlanmadı.

### F. Koruma denetimi

1153 kaynak/araç dosyası test kopyasıyla aynı; yalnız izin verilen 18 dosya
Git değişiklik listesinde. Auth/Gateway/diğer servis/migration diff'i boş.
API kataloğunda GAME dışı içerik HEAD ile aynı. Index boş; diff --check PASS.
Auth dosyaları yerel `origin/main` referansı `eb40ca04` ile de aynıdır;
bu kontrol uzak sunucudan yeniden fetch yapıldığı anlamına gelmez.
DEV ve Sonar konteyner ID/image/mount eşitliği PASS; Sonar süreç başlangıcı aynı.
DEV servislerinin kendi restart döngüleri sürdüğünden bütün süreç başlangıçları
aynı değildir. DEV verisinin bit düzeyinde eşitliği ölçülmedi.
Gerçek ENV dosya özeti eşitliği PASS; gerçek secret rapor/log eşleşmesi yok.
HTTP JVM'leri kapatıldı ve üç geçici HTTP portunun kapalı olduğu doğrulandı.
Son temizlikte yalnız görev etiketi/adı/volume sahipliği doğrulanan PostgreSQL
ve Mailpit konteynerleri, bunların görev ağı ve sentetik geçici ENV profili kaldırıldı.
DEV/Sonar konteynerlerine veya veri volume'lerine temizlik uygulanmadı.

### G. Açık bağımlılıklar

D3/D4 ve hesap silme koordinasyonu §7'deki gibi açıktır. Canlı ADMIN/2FA ve
geliştirici mutasyonu ayrıca doğrulanmalıdır. Eksik DEV anahtarları için
**AUTH OWNER ACTION REQUIRED — Kerem**; diğer servislerin kapsam eksiği ilgili
sahibinin işidir. Bunlar Game'de güvensiz geçici çözümle gizlenmedi.

### H. Manuel commit değerlendirmesi

**NOT READY.** Game pre-push ve gerçek HTTP doğrulaması PASS'tır; Game kaynaklı
açık Sonar bulgusu kalmamıştır. Ancak ortak Sonar Quality Gate 49 kapsam dışı
bulgudan ve backend pre-push 15 modülün kapsam eksiğinden dolayı FAIL'dir.
Full DEV yapılandırması da BLOCKED'dır. Bu zorunlu kapılar geçmeden genel
`SAFE TO COMMIT` / `READY TO COMMIT` sonucu verilmez. Sahip düzeltmelerinden
sonra ortak kontroller yeniden çalıştırılmalıdır; başka servise müdahale edilmedi.

Kanıtlar: `test-results/game-auth-final/game-prepush.log`, `backend-prepush.log`,
`backend-result.json`, `sonar-scan.log`, `sonar-retry.log`, `sonar-retry-result.json`,
`sonar-final.json`, `http-result.json`, `preservation.json`.

## 11. Kapsam dışı Sonar bulguları — salt okunur devir kaydı

**2026-10-24 durum güncellemesi:** Kerem'in `docs-auth/d4-ekip-notlari/03_HAMZA_game_library_review.md`
bildirimine göre aşağıdaki 46 Auth + 3 Gateway bulgusu kapandı; `ltz-auth-gateway`
projesinde açık bulgu/hotspot 0/0 ve Quality Gate PASSED. Bu çalışma Sonar sunucusunu
yeniden sorgulamadı; kapanış Auth sahibinin bildirimi olarak kaydedildi.
Aşağıdaki devir listesi ve eski OWNER ACTION REQUIRED başlıkları tarihsel kayıttır.

Bu liste gerçek son analizden alınmıştır. Kaynak dosyalar değiştirilmedi; bulgular
sunucuda kapatılmadı, bastırılmadı veya analizden dışlanmadı. Sonar yeni kod dönemi
2026-10-20 tarihli PREVIOUS_VERSION tabanıdır; bu bulguların bu Game göreviyle
Auth/Gateway kaynaklarına eklendiği anlamına gelmez. Öneriler sahip incelemesi gerektirir.

### AUTH OWNER ACTION REQUIRED — Kerem — 46 bulgu

- `src/test/java/com/ltz/auth/api/controller/SessionCookiesProfileTest.java:33` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/api/controller/SessionCookiesProfileTest.java:35` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/api/controller/SessionCookiesProfileTest.java:37` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/api/controller/SessionCookiesProfileTest.java:39` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/main/java/com/ltz/auth/application/service/SteamLoginService.java:128` — `java:S8491` (MAJOR). Ardışık iki Javadoc bloğunu içerik kaybetmeden tek metot açıklamasında birleştir.
- `src/test/java/com/ltz/auth/infrastructure/config/CleanupConfigTest.java:31` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/config/CleanupConfigTest.java:37` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/main/java/com/ltz/auth/application/service/AccountDeletionDispatcher.java:51` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/application/service/AccountDeletionService.java:75` — `java:S2589` (MAJOR). Null dönebilen callback/Optional yollarını ve JSpecify nullability sözleşmesini incele; mevcut null güvenlik kontrolünü körlemesine kaldırma. Doğru nullable bildirim veya gerekçeli bulgu incelemesi Auth sahibince yapılmalı.
- `src/main/java/com/ltz/auth/domain/entity/AccountDeletionTask.java:95` — `java:S6885` (MAJOR). Aynı alt/üst sınır ve taşma davranışını koruyarak Math.clamp kullan; backoff sınır testlerini koru.
- `src/main/java/com/ltz/auth/infrastructure/security/SessionStateValidator.java:50` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/application/service/PasswordResetDispatcher.java:52` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/application/service/PasswordService.java:60` — `java:S2589` (MAJOR). Null dönebilen callback/Optional yollarını ve JSpecify nullability sözleşmesini incele; mevcut null güvenlik kontrolünü körlemesine kaldırma. Doğru nullable bildirim veya gerekçeli bulgu incelemesi Auth sahibince yapılmalı.
- `src/main/java/com/ltz/auth/application/service/PasswordService.java:94` — `java:S2589` (MAJOR). Null dönebilen callback/Optional yollarını ve JSpecify nullability sözleşmesini incele; mevcut null güvenlik kontrolünü körlemesine kaldırma. Doğru nullable bildirim veya gerekçeli bulgu incelemesi Auth sahibince yapılmalı.
- `src/main/java/com/ltz/auth/application/service/GoogleLoginService.java:92` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/application/service/GoogleLoginService.java:116` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/application/service/SteamAssertionValidator.java:80` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/application/service/SteamLoginService.java:103` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/test/java/com/ltz/auth/application/service/SteamAssertionValidatorTest.java:77` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/main/java/com/ltz/auth/application/service/EmailVerificationService.java:67` — `java:S2589` (MAJOR). Null dönebilen callback/Optional yollarını ve JSpecify nullability sözleşmesini incele; mevcut null güvenlik kontrolünü körlemesine kaldırma. Doğru nullable bildirim veya gerekçeli bulgu incelemesi Auth sahibince yapılmalı.
- `src/main/java/com/ltz/auth/application/service/SecurityEventRecorder.java:36` — `java:S6213` (MAJOR). Metodu örneğin recordEvent olarak adlandırıp Auth içindeki çağrılarını birlikte güncelle; audit davranışını koru.
- `src/main/java/com/ltz/auth/application/service/SessionService.java:69` — `java:S6878` (MAJOR). Rotated kaydını record pattern ile aç; dönen session ve diğer outcome davranışlarını koru.
- `src/main/java/com/ltz/auth/application/service/SessionService.java:70` — `java:S1481` (MINOR). Kullanılmayan outcome bağlayıcısını unnamed pattern yap; aynı hata kodunu üretmeye devam et.
- `src/main/java/com/ltz/auth/application/service/SessionService.java:71` — `java:S1481` (MINOR). Kullanılmayan outcome bağlayıcısını unnamed pattern yap; aynı hata kodunu üretmeye devam et.
- `src/main/java/com/ltz/auth/application/service/VerificationCodeDispatcher.java:53` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/infrastructure/security/BoundedPasswordEncoder.java:44` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/infrastructure/security/JwtSigningKey.java:46` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/infrastructure/security/JwtSigningKey.java:59` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/infrastructure/security/JwtSigningKey.java:75` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/infrastructure/security/SecretCipher.java:62` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/infrastructure/security/SecretCipher.java:78` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/main/java/com/ltz/auth/infrastructure/security/VerificationCodeHasher.java:61` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/test/java/com/ltz/auth/infrastructure/client/MailServiceUnavailableAdapterTest.java:27` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/client/MailpitEndToEndTest.java:69` — `java:S5838` (MINOR). AssertJ Map assertion için containsEntry(key, value) kullan; assertion anlamını koru.
- `src/test/java/com/ltz/auth/infrastructure/persistence/FlywayMigrationTest.java:33` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/persistence/FlywayMigrationTest.java:39` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/persistence/FlywayMigrationTest.java:46` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/BoundedPasswordEncoderTest.java:66` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/RequestBodySizeLimitFilterTest.java:52` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/SecretCipherTest.java:41` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/SecretCipherTest.java:42` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/SecretCipherTest.java:44` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/SecretCipherTest.java:52` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/SecretCipherTest.java:54` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/VerificationCodeHasherTest.java:45` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/auth/infrastructure/security/VerificationCodeHasherTest.java:55` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.

Game etkisi: mevcut JWT/JWKS ve HTTP PLAYER akışı geçer; ortak Sonar Quality Gate,
yeni kod dönemindeki açık bulgular nedeniyle kapanmaz. Bu durum Game güvenliği
gevşetilerek veya ilgili servisin testleri atlanarak giderilmedi.

### GATEWAY OWNER ACTION REQUIRED — 3 bulgu

- `src/main/java/com/ltz/gateway/security/GatewaySecurityProperties.java:51` — `java:S7467` (MINOR). Kullanılmayan catch/pattern değişkenini Java 25 unnamed `_` ile değiştir; hata davranışını koru.
- `src/test/java/com/ltz/gateway/security/GatewaySecurityFiltersTest.java:307` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.
- `src/test/java/com/ltz/gateway/security/GatewaySecurityFiltersTest.java:311` — `java:S5778` (MAJOR). Beklenen istisnayı üreten tek çağrıyı assertion lambda içinde bırak; diğer hazırlık çağrılarını dışarı al ve mevcut assertion kapsamını koru.

Game etkisi: mevcut JWT/JWKS ve HTTP PLAYER akışı geçer; ortak Sonar Quality Gate,
yeni kod dönemindeki açık bulgular nedeniyle kapanmaz. Bu durum Game güvenliği
gevşetilerek veya ilgili servisin testleri atlanarak giderilmedi.

S2589 için somut null yolları: AccountDeletionService.issueToken:133,
PasswordService.issueToken:144/152 ve EmailVerificationService.issueForResend:95/101
null döndürebilir. PasswordService:59, Optional.orElse(null) kullanır. Bu nedenle
bu bulgular doğrudan güvenlik kontrolü kaldırma talimatı olarak yorumlanmamalıdır.

## 12. Onaylı yerel Auth anahtar kurulumu ve güncel DEV doğrulaması

Tarih: 2026-10-22. Bu bölüm §10'daki eski Full DEV BLOCKED durumunu günceller.
Kullanıcı, resmî script ile yalnız üç yerel Auth anahtarının oluşturulmasına
ve mevcut DEV entegrasyonunun doğrulanmasına açık onay verdi.

### 12.1 ENV koruması — PASS

Kerem'in iki rehberi ve özellikle EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI §0.5
repository'den okundu. `ops/generate-auth-dev-keys.mjs` kaynak incelemesi,
syntax kontrolü ve geçici dosyalardaki 3/3 güvenlik testi PASS oldu.

Resmî `node ops/generate-auth-dev-keys.mjs` çalıştırıldı. `.env` ve `.env.dev`
içindeki yalnız eksik AUTH_JWT_PRIVATE_KEY, AUTH_OTP_HMAC_KEY ve
AUTH_TOTP_ENCRYPTION_KEY dolduruldu. İki dosyada değer eşitliği, dolu değerlerin
korunması ve diğer ENV içeriği/Sonar token'ının değişmemesi doğrulandı.
JWT anahtarı PKCS#8 DER Base64 / 3072-bit RSA; HMAC ve TOTP anahtarları 32 bayttır.
Değerler çıktıya/rapora yazılmadı. Kurulum sonrası ENV özetleri son denetimde aynı;
rapor/loglarda gerçek secret eşleşmesi bulunmadı. Kalıcı başka ayar değiştirilmedi.

### 12.2 Gerçek DEV — PASS

- İki ENV dosyasıyla Compose config doğrulaması PASS.
- Yalnız postgres, auth-service, api-gateway ve game-service için güncel imajlarla
  `up -d --build` uygulandı. Auth/Gateway/diğer servis kaynakları değiştirilmedi.
- PostgreSQL, Auth ve Gateway konteynerleri healthy. Game Dockerfile'ında healthcheck
  tanımı yoktur; Game için gerçek HTTP `/actuator/health` 200 / UP doğrulandı.
- Gerçek DEV'de üç servis health kontrolüne ek 13 salt okunur bağlantı/güvenlik
  kontrolü PASS: doğrudan/Gateway JWKS 200, yalnız açık RSA anahtarı, iki JWKS'in
  eşitliği, çalışan Auth anahtarının onaylı ENV anahtarıyla eşleşmesi, doğrudan ve
  Gateway Game katalog 200, tokensız/bozuk Bearer 401 ve ProblemDetail.
- Game issuer/audience/Auth URL ayarları Auth ile eşleşir; Auth özel anahtarları
  Game ortamında yoktur. Gerçek Game konteynerinden Auth JWKS HTTP 200 alınır.
- DEV'de register/login, test hesabı, feedback fixture veya test SQL'i çalıştırılmadı.

**Veri koruması:** Mevcut `ltz-dev_postgres_data` volume'ü aynı ad, kaynak ve
hedefte korunmuştur. PostgreSQL konteyneri yeniden oluşturuldu. Değişen bind mount,
init SQL dosyasının eski `Masaüstü/lobbytwozero` yolundan mevcut
`Masaüstü/projects/lobbytwozero` yoluna bağlanmasıdır; veri volume'ü değişmedi.
Game başlangıcı 10 migration doğruladı, şema V40 ve up-to-date; yeni migration
uygulamadı. Auth ilk açılışta boş şemasına mevcut V61–V64 zincirini kendi Flyway
süreciyle uyguladı. Dosyalar değiştirilmedi; elle SQL/repair/clean/baseline yoktur.
Bu normal uygulama başlangıcıdır; DEV'in bit düzeyinde değişmediği iddia edilmez.
Sonar konteynerleri ve veri volume'leri korunmuştur.

### 12.3 Taze izole Full DEV — PASS / SAFE TO COMMIT

Resmî `runPrePush` full/dev akışı, kaynakları aynı geçici kopyada çalıştırıldı.
1153 kaynak/araç dosyası SHA-256 ile eşleştirildi. Maven DB hedefi her çağrıda
göreve ait PostgreSQL 17 / rastgele loopback portuna sabitlendi; gerçek DEV ile
volume/port paylaşılmaz. Git/hijyen denetimleri asıl repository'de, backend HTTP
health denetimleri gerçek `ltz-dev` projesindeki 18 serviste yapıldı.

- 562 test PASS, failure/error/skipped **0/0/0**; Maven clean verify PASS.
- Game **277 / %98,75 LINE**, Auth **252 / %93,07**, Gateway **18 / %94,19**.
- Toplam JaCoCo LINE **3867/4089 = %94,57**.
- Git/Secret, Backend Tests, Build, Coverage, Docker Configuration ve gerçek
  DEV'deki 18 servisin HTTP Health satırlarının tamamı PASS; çıktı **SAFE TO COMMIT**.
- Opsiyonel gitleaks kurulu olmadığından geçmiş taraması SKIPPED; diğer secret,
  Git ignore, port ve anahtar üretici güvenlik kontrolleri PASS.

Bu full kapı **toplam kapsamı** %80 ile karşılaştırır. Ayrı servis bazlı kapıda
§10.B'deki 15 iskelet servis hâlâ birer test / %33,33 kapsamla %80'in altındadır.
Bu fark kapı değiştirilerek oluşturulmadı; ortak araçlar olduğu gibi kullanıldı.
15 servis kapsam eksiği **SERVICE OWNER ACTION REQUIRED** olarak devam eder.

### 12.4 Taze gerçek HTTP ve Sonar

Yeni full derlemesinden alınan Auth/Gateway/Game JAR'ları ayrı JVM'lerde, yalnız
geçici PostgreSQL ve Mailpit ile tekrar çalıştırıldı: **44 kontrol PASS, 0 FAIL**.
İki PLAYER gerçek register → SMTP doğrulama → login yaptı. JWT/JWKS, HttpOnly
cookie, Gateway cookie/Bearer, feedback oluşturma/okuma/çapraz kullanıcı IDOR,
CSRF/Origin ve 401/403 kontrolleri geçti. Gerçek DEV DB kullanılmadı.
ADMIN/2FA ve geliştirici grant mutasyonu gerçek HTTP'de yine NOT VERIFIED;
Game'in Java entegrasyon testleri bu senaryoları kapsar.

Taze raporlarla Sonar yeniden çalıştırıldı. Gerçek analiz SUCCESS, kimlik
`e7afe465-6c8d-4b95-b483-ebb6c32ea682`, işleme zamanı **22:02:47 Türkiye saati**.
Quality Gate **FAIL**: `new_violations=49 > 0`, dağılım **46 Auth + 3 Gateway**;
Game **0**, bug/vulnerability/hotspot **0**. Security/reliability/maintainability
**A/A/A**; coverage **%90,2** (yeni %90,7), duplication **%0,6** (yeni %0,41082).
§11'deki dosya/satır/kural/öneri listesi geçerlidir. Bulgu bastırma, kaynak
dışlama veya kalite politikası değişikliği yapılmadı. Auth/Gateway sahip aksiyonu gerekir.

### 12.5 Son durum ve kanıtlar

DEV kurulum engeli kapandı; Game–Auth DEV bağlantısı ve izole güvenlik testleri
PASS. Yeni Game üretim/test kodu düzeltmesi gerekmedi. Bu aşamada yalnız onaylı
üç ENV anahtarı ve Game belgeleri güncellendi; eski Game kod değişiklikleri korundu.
Game migration'ları ve Auth/Gateway/diğer servis kaynakları değişmedi. Git index
boş; add/commit/push/merge/rebase/reset veya branch değişimi yoktur.

Geçici HTTP JVM'leri/portları kapatıldı. Yalnız görev etiketli PostgreSQL/Mailpit,
görev ağı ve sentetik test ENV profili temizlendi. Gerçek DEV servisleri açık ve
sağlıklı bırakıldı. D3/D4, hesap silme ve canlı ayrıcalıklı kullanıcı akışları §7
ve §12.4'teki sınırlarla açıktır.

**Genel karar: NOT READY.** Full DEV'nin SAFE TO COMMIT çıktısı kendi kontrol
kapsamı için geçerlidir. Sonar kapısı 49 dış servis bulgusu ve servis bazlı kapsam
eksikleri nedeniyle genel kalite kapanışı verilemez; bu sorunlar Game'de gizlenmedi.

Git dışı kanıtlar: `test-results/game-auth-dev/keys.json`, `compose.json`,
`runtime.json`, `full-prepush.log`, `full-result.json`, `http-result.json`,
`sonar-retry-result.json`, `sonar-final.json` ve `final.json`.

## 13. Hesap silme veri envanteri ve karar raporu — 2026-10-24

**Tarihsel, onay öncesi değerlendirme:** Aşağıdaki karar bekleme bilgileri ilk
rapor anını gösterir. Kullanıcı daha sonra Kerem'le ortak politika onayını iletti;
güncel karar ve uygulama §14'tedir. Tablo envanteri/kısıt analizi geçerlidir.

### 13.1 Güncel kapsam ve onay sınırı

Kullanıcı yalnız güvenlik/iç uç/test altyapısına devam edilmesini, veri saklama
politikasının önce raporlanmasını istedi. Kerem'in nil UUID yaklaşımı **öneridir**;
ortak saklama kararı alınmadı. Bu çalışmada kullanıcı verisi silen veya anonimleştiren
bir production SQL eklenmedi; mevcut migration ve audit trigger'ları değiştirilmedi.
V171–V180 tahsisinin onayı yalnız yeni migration numara alanını kapsar.

Game iç ucu `DELETE /internal/v1/accounts/{accountId}`:

- Ayrı D4 decoder: RS256/JWKS, tam `typ=svc+jwt`, issuer, tek `aud=game-service`,
  `kid`, `jti`, ham `iat`, `nbf`, `exp`, en çok 60 saniye ömür ve 30 saniye saat toleransı.
- Yalnız `SCOPE_account:erase`, tam `sub=svc:auth-service` ve token `account_id` = yol UUID'si.
  Kullanıcı token'ı (ADMIN dahil) iç uçta; servis token'ı kullanıcı uçlarında reddedilir.
- Mevcut REF'ler veya audit metninde hesap UUID'si bulunursa `503 ACCOUNT_ERASURE_PENDING`.
  Hiçbir satır değiştirilmez. Auth görevi onaylanmaz, yeniden denenir.
- Bilinen hesap verisi bulunmazsa gövdesiz `204`; tekrar çağrı aynı sonucu verir.
  Bu, tam silme/anonimleştirme implementasyonu değildir. UUID/REF taraması, kimlikten
  bağımsız yazılmış serbest metinlerdeki bütün kişisel veriyi saptama garantisi vermez.
- Gateway route'u, Auth kaynağı, ENV veya gerçek DEV veritabanı değiştirilmedi.

### 13.2 Gerçek tablo ilişkileri ve kısıtlar

Kaynak: aktif V31–V40 migration'ları ve mevcut entity/service kodu. Kullanıcı
kimlikleri Auth DB'sine FK değildir; Game içindeki UUID REF'leridir. `games.id`
katalog kimliğidir; hesap silme, oyunun fiziksel silinmesi anlamına gelmemelidir.

- `games.editor_actor_id` nullable kullanıcı REF'i; `editor_evidence` serbest metindir.
  Oyun silinirse katalog çocukları etkilenir; audit/reference/feedback RESTRICT
  ilişkileri de vardır. Hesap silerken `games` satırını silme önerilmiyor.
- `game_identity_audits.actor_id` NOT NULL; `evidence` NOT NULL ve en az 8 karakterdir.
  `reference_id → game_external_refs`, `from_game_id/to_game_id → games` ilişkileri
  RESTRICT'tir. V34 trigger'ı her UPDATE/DELETE'i reddeder.
- `game_admin_audits.actor_id` NOT NULL; `game_id → games` RESTRICT. `reason` nullable,
  doluysa en az 8 karakter; `details` nullable serbest metindir. V35 trigger'ı her
  UPDATE/DELETE'i reddeder. `DeveloperGrantService` başka aktörün audit satırına
  `developerUserId=<hesap UUID>` yazar; yalnız actor_id taraması bu veriyi kaçırır.
- `game_field_overrides.actor_id` NOT NULL; `game_id → games` CASCADE ve `(game_id,field)`
  UNIQUE. Satırın silinmesi sağlayıcı alan kilidini kaldırır, katalog senkronizasyonu
  manuel düzeltmeyi ezebilir; aktör kimliğiyle alan kilidi ayrı ele alınmalıdır.
- `game_developer_grants.developer_user_id/granted_by` NOT NULL; `revoked_by` nullable.
  `game_id → games` CASCADE; aktif `(game_id,developer_user_id)` kısmi UNIQUE.
  **CHECK: `(revoked_at is null) = (revoked_by is null)`**. Revoke edilmiş kayıtta
  yalnız revoked_by=NULL yapmak geçersizdir; ikisini NULL yapmak yetkiyi yeniden
  aktifleştirir. Kerem'in genel nullable→NULL önerisi burada doğrudan uygulanamaz.
- `game_developer_contents`: game_id hem PK hem games'e CASCADE FK; description ve
  author_id NOT NULL. `moderated_by` nullable REF, `moderation_reason` serbest metindir.
  İçeriğin silinmesi katalog/sağlayıcı açıklamasını silmez.
- `game_developer_media`: game_id → games CASCADE; author_id NOT NULL; moderated_by
  nullable; URL, alt_text ve moderation_reason metin taşır. Mevcut sistem dış medya
  URL'sini saklar; bağlantı satırını silmek harici sağlayıcıdaki dosyayı silmez.
- `game_update_notes`: game_id → games CASCADE; author_id NOT NULL; moderated_by
  nullable; title/body/moderation_reason içerik taşır. İçerik saklanacaksa yalnız
  yazar UUID'sini değiştirmek metni anonimleştirmiş sayılmaz.
- `game_developer_feedback`: game_id → games RESTRICT; author_id NOT NULL; title/body
  özel oyuncu geri bildirimidir. Bu tablo Review içeriği değildir.
- `game_developers`/`game_publishers` katalogdaki adlardır, Auth kullanıcı REF'i değildir.
  Hesap kimliğine göre otomatik silinemez; bir gerçek kişi adı için ayrı veri talebi
  gerekiyorsa eşleme ve değerlendirme süreci ayrıca belirlenmelidir.
- Game'de mevcut outbox/inbox kişisel veri tablosu yoktur. Gelecekte eklenirse olay
  payload'ları, bekleyen teslimler ve silme testi aynı görevde kapsama alınmalıdır.

### 13.3 Önerilen işlemler — uygulanmadı, ortak karar bekliyor

- Kullanıcıya ait `game_developer_grants` ve özel `game_developer_feedback` satırlarını
  kalıcı silme; başka kullanıcıların yetkilerini ve verisini koruma.
- `games.editor_actor_id` alanını NULL yapma; editör sıralamasını koruma. editor_evidence
  metnini temizleme veya kişisel veriden arındırılmış kanıtla değiştirme kararı gerekli.
- `game_field_overrides` satırını koruyup actor_id'yi anonimleştirme; alan kilidini koruma.
- Başkasına ait grant satırlarında granted_by'ı anonimleştirme; revoked_by için
  revoked_at ile tutarlı anonim aktör kullanma. **Revoke durumu korunmalı.**
- İki audit tablosunda olay/oyun/referans/zaman bilgisini tutup aktör REF'ini nil UUID'ye
  çevirme, kişisel reason/details/evidence verisini temizleme seçeneği. Özellikle
  başka aktörün `details` alanındaki hedef hesap da kapsanmalı. Bu bir saklama istisnasıdır;
  gerekçe, süre ve yetki modeli onaylanmadan uygulanmayacak.
- Geliştirici açıklaması/medyası/güncelleme notları için iki seçenek: hesabın
  içeriklerini kalıcı silme veya oyunla ilgili içeriği inceleyip kişisel metin/URL
  verisini kaldırarak yazarını anonimleştirme. Görünür/gizli/silinmiş durumların
  hangisinin nasıl işleneceği de seçilmeli; bu çalışma hiçbirini seçmedi.
- Silinen hesap moderatörse başkasının içeriğini silmeden moderated_by bağlantısını
  kaldırma ve moderation_reason metnini değerlendirme. Yazar ve moderatör işlemleri ayrılmalı.

### 13.4 Kerem ve Hamza'nın kararlaştırması gerekenler

1. Audit'in hangi gerekçeyle, hangi süreyle, hangi alanlarla saklanacağı; saklama
   gerekmiyorsa kalıcı silmenin kabul edilip edilmediği.
2. Nil UUID'nin ortak anonim aktör olarak kullanımı ve bu kimliğin hiçbir yetki/gerçek
   hesaba dönüşmemesi; frontend/API'de nasıl gösterileceği.
3. Geliştirici içeriğinin kalıcı silinmesi mi, incelenip anonim saklanması mı;
   metinler, medya URL'leri ve gizli/silinmiş içerik kapsamı.
4. Serbest metindeki hedef kimlikleri ve kişisel veriler: audit.details, reason,
   evidence, editor_evidence, moderation_reason için temizleme yöntemi ve kanıtı.
5. Audit değişmezliğinin yalnız izinli alanlarda kontrollü anonimleştirme istisnası:
   dar kapsamlı trigger/fonksiyon, DB yetkileri, normal UPDATE/DELETE'in engellenmeye
   devam etmesi, normal uygulama aktörünün istisnayı kötüye kullanamaması.
6. D3 henüz yokken eski access token'ıyla silme sonrası tekrar veri yazılması yarışı:
   yerel silinmiş-hesap kaydı/yazma kontrolü veya durum yayılımı tasarımı. Boş hesap
   için verilen onay sonrasında yeni REF oluşmasını da önlemek gerekir.
7. Büyük veri için 5 saniye çağrı bütçesi, indeksler, transaction/lock ve yeniden
   deneme stratejisi; `204` ancak kararlaştırılan bütün temizlik tamamlanınca.

Bu kararlar sonrasında V171–V180 içindeki ilk boş numarayla yeni migration yazılır.
V34/V35 dosyaları yeniden yazılmaz; trigger disable, session_replication_role veya
runtime DDL ile audit koruması aşılmaz. Migration/yetki sınırı ve gerçek DB silme,
idempotency, çapraz hesap izolasyonu, rollback ve yazma yarışı testleri ayrıca eklenir.

### 13.5 Library ve Review

İki servis de henüz entity/migration/kişisel veri tablosu taşımıyor. Güvenli iç uç
aynı sözleşmeyle veri yok yanıtı `204` verir; mevcut veri silme/puan saklama kararı
uygulanmış sayılmaz. İleride Library tabloları veya Review inceleme/puan/beğeni/rapor
tabloları eklenirken silme servisi ve DB testleri aynı görevde genişletilmelidir.
Review metni ve puanını birlikte silme ya da puanı anonim saklama kararı ayrıca bekler.

### 13.6 Doğrulama

Güvenlik ve Auth üreticisi uyumluluk testleri üç serviste eklendi. Tam servis testleri,
JaCoCo ve yerel kontrol sonuçları aşağıda nihai doğrulamadan sonra kaydedilir.
Hesap silme politikasının veya çalışan DEV Auth koordinasyonunun tamamlandığı iddia edilmez.

## 14. Onaylanan hesap silme politikası ve uygulama — 2026-10-24

### 14.1 Ortak karar ve saklama istisnasının gerekçesi

Kullanıcı, Auth sahibi Kerem'le görüştüğünü açıkça bildirerek Game silme politikasını
onayladı. Bu bölüm §13'teki onay bekleme durumunun yerine geçer. İstisnanın amacı
oyun kimliği eşleme ve yönetim değişikliklerinin olay/zaman/oyun izini ve sağlayıcı
alan kilitlerini korurken silinen hesapla kişisel bağlantıyı kaldırmaktır.
Audit metinleri bu amaç için tutulmaz; actor_id nil UUID, ilgili nullable metinler
NULL, NOT NULL evidence `[silinen hesap: metin kaldırıldı]` olur. Genel saklama
süreleri için yeni bir süre uydurulmadı; bu görev anonim olay kaydının korunmasını kapsar.

- Hesabın geliştirici açıklamaları, medya bağlantıları, güncelleme notları, özel
  feedback kayıtları ve kendi geliştirici grant satırları kalıcı silinir.
- `games` ve başka hesapların kayıtları korunur. editor_actor_id/editor_evidence
  NULL olur; oyun ve editör sıralaması silinmez. Alan kilidi satırı kalır, actor_id nil olur.
- Başkasının grant'ında ilgili granted_by nil olur. Revoke edilmiş grant'ta
  revoked_by nil olur, revoked_at korunur. Bu, kullanıcının "uygun nullable alan"
  şartını mevcut CHECK'e uydurur; revoked_by=NULL ile grant yeniden açılamaz.
- Silinen hesap yalnız moderatörse başkasının içeriği korunur; moderated_by ve
  moderation_reason NULL olur. Moderasyon zamanı/durumu korunur.
- Admin audit'inde silinen hesap aktörse veya metinde açık UUID hedefiyse ilgili
  reason/details tamamen NULL olur. Başka aktörün kimliği korunur. Özellikle
  `developerUserId=<UUID>` grant/revoke hedefleri, silinen feedback/media/not
  kayıtlarının yapısal hedef kimlikleri de değerlendirilir.
- Identity audit'inde ilgili aktör nil olur, evidence sabit metne çevrilir;
  başka aktörün evidence alanında açık hesap UUID'si varsa bütün evidence kaldırılır.
- Regex ile kişisel veri temizliği yapılmaz. Metindeki UUID araması yalnız ilgili
  satırın seçimi içindir; metin parçaları yeniden yazılmaz. Kimlikle ilişkilendirilemeyen
  başka kişilerin serbest metinlerindeki isimleri otomatik tanıma iddiası yoktur.
- Nil UUID bir gerçek hesap veya silme hedefi olamaz; iç uçta nil hedef 400'dür.

### 14.2 V171 ve veritabanı güvenlik sınırı

Önceki açık kullanıcı talimatıyla alınan V171–V180 aralığında ilk boş V171 kullanıldı:
`V171__enable_controlled_account_erasure.sql`. V31–V40 dosyaları değiştirilmedi.
Migration mevcut veriyi değiştirmez; endpoint çağrısında kullanılacak yapıyı tanımlar.

`erase_game_account_data(uuid)` tek transaction'da tablo yazmalarını kilitler,
temizliği yapar ve uygulama son REF kontrolünden sonra transaction'ı commit eder.
Controller ancak servis transaction'ı tamamlanınca gövdesiz 204 döner. Veri yoksa ve
tekrar çağrıda aynı yanıt verilir. Hata/eksik temizlikte commit yapılmaz; geçici
kilit/süre hataları 503 ACCOUNT_ERASURE_PENDING, diğer beklenmeyen hatalar 500'dür.
202 kullanılmaz. Fonksiyon lock_timeout=1s, uygulama transaction bütçesi 3s'dir;
servis decoder'ının JWKS bağlantı/okuma bütçesi 750'şer ms ve redirect kapalıdır;
büyük veri çağrısı tamamlanmazsa rollback ve Auth yeniden denemesi vardır.

V34/V35 trigger adları ve DELETE yasağı korunur. Değiştirilen trigger fonksiyonları
yalnız özel transaction bağlamı varken ilgili satırın izinli alanlarını, beklenen
nil/NULL/sabit evidence değerlerine güncelleyebilir. Oyun, olay, zaman, reference_id
ve diğer alanlar değiştirilemez. Bağlam tablosuna PUBLIC yetkisi verilmez; bir session
GUC/başlık ayarlamak korumayı açmaz. Bağlam satırı aynı transaction'da kaldırılır;
hata olursa bütün değişikliklerle birlikte geri alınır. Kalıcı silinmiş-hesap listesi yoktur.

Fonksiyonlar SECURITY DEFINER ve sabit `search_path=pg_catalog` kullanır; uygulama
tabloları açık `public.` adlarıyla çağrılır. PUBLIC'a fonksiyon EXECUTE verilmez.
Ayrı runtime DB rolü kullanılıyorsa yalnız `erase_game_account_data(uuid)` EXECUTE
tahsis edilmelidir; bağlam tablosuna DML veya audit sahipliği verilmez. DB sahibi
rolünün kendi trigger/tablolarını değiştirebilmesi bu mekanizmayla engellenemez;
production'da migration sahibi/runtime rol ayrımı mevcut least-privilege şartıdır.

Silme sırasındaki eşzamanlı yazmalar kilitle engellenir. Transaction bittikten sonra
eski access token'ıyla yeniden veri yazılmasını küresel olarak engelleyen D3 veya
yerel tombstone bu göreve eklenmedi; §7'deki access-token iptal gecikmesi sürer.
Kalıcı hesap blok kaydı ayrıca veri saklama/mimari kararı gerektirir.

### 14.3 Ayrı servis sınırları ve doğrulama

Library/Review iskeletleri kendi servis audience'ı, imza/süre/scope/hesap eşleşmesi
denetimleriyle gövdesiz 204 verir. Game politikası bu servislere taşınmadı; yeni
kişisel veri tabloları eklendiğinde kendi silme işlemleri/testleri de eklenmelidir.
Auth/Gateway/diğer ekip servisleri ve ENV dosyaları değiştirilmedi.

Gerçek DEV dağıtımına migration uygulanmadı, çalışan Auth silme görevinin CONFIRMED
olması bu çalışma içinde denenmedi. Nihai yerel test/kapsam sonuçları aşağıya eklenir.

### 14.4 Nihai test kaydı

2026-10-24, son kaynaklarla `mvn -B -ntp -f backend/pom.xml -pl
services/game-service,services/library-service,services/review-service --fail-at-end
clean verify` → **BUILD SUCCESS**. Gerçek ENV bulunmayan izole kaynak kopyası ve
geçici PostgreSQL 17 kullanıldı; DEV verisi kullanılmadı.

- Game: **303 test**, hata/başarısız/atlanmış 0/0/0, JaCoCo satır **%98,76**.
- Library: **18 test**, hata/başarısız/atlanmış 0/0/0, satır **%96,97**.
- Review: **18 test**, hata/başarısız/atlanmış 0/0/0, satır **%96,97**.
- Gerçek Auth ServiceTokenIssuer ile kabul, kullanıcı/servis token ayrımı,
  scope/hesap/sub sınırı, scp/roles fallback reddi, imza/süre/claim hataları doğrulandı.
- PostgreSQL: V40→V171 veri korumalı yükseltme; gerçek silme/anonimleştirme,
  diğer hesapların korunması, revoke CHECK, audit normal UPDATE/DELETE yasağı,
  sınırlı runtime rolünün bağlamı taklit edememesi, rollback ve kilit sonrası tekrar.
- 362 kaynak/test dosyası izole kopyayla SHA-256 eşit. V31–V40 HEAD ile aynı.
  API kataloğunda GAME/LIBRARY/REVIEW dışı içerik aynı; Auth/Gateway/pre-push/lib/AGENTS diff'i boş.
- Zorunlu `pre-push.cmd game-service`, `library-service`, `review-service`
  denemeleri mevcut `pre-push/quality.mjs:60` sözdizimi hatasıyla başlamadan durdu:
  `SyntaxError: Identifier 'result' has already been declared`. Ortak araç değiştirilmedi.
  **SAFE TO COMMIT / BACKEND TEST CHECK PASSED alınmadı.** Maven/test/kapsam
  sonuçları ayrı kanıttır; pre-push başarısı olarak sunulmaz.

Git dışı kanıt: `test-results/hamza-erasure-check-20261024/maven-final.log`,
aynı dizindeki üç `*-service-check.log`, servis `target/surefire-reports` ve
`target/site/jacoco` raporları. Gerçek DEV Auth koordinasyonu ve Sonar yeniden taraması yapılmadı.

## 15. Pre-push kök neden analizi ve güncel kalite kaydı — 2026-10-24

Bu bölüm §14.4'teki önceki engel durumunu günceller. Hesap silme politikası ve V171
korunmuştur. Kalite eşikleri, analiz kapsamı, güvenlik kontrolleri veya test atlama
kuralları değiştirilmemiştir. DEV verisi ve migration geçmişi korunmuştur.

### 15.1 Kesin kök neden ve izin verilen ortak araç düzeltmeleri

`pre-push/quality.mjs:60` hatası aynı callback içinde iki backend çalıştırma bloğunun
birleşmesiyle `const result` değişkeninin iki kez tanımlanmasından kaynaklandı.
Git geçmişinde hata `fcec004` merge commit'inde (Alper Temiz, 2026-10-24 01:40:09 +03,
`Merge branch 'main' into feature/hardware-service`) ortaya çıkıyor. İki parent
`6b60134` ve `6f33cc2` sözdizimi kontrolünden geçiyor; merge ve düzeltme öncesi HEAD
`node --input-type=module --check` ile aynı hatayı veriyor. Bu, hesap silme
geliştirmelerinden bağımsız ortak araç birleşim hatasıdır.

Kullanıcının dosya bazında açık izinleriyle şu minimal düzeltmeler yapıldı:

- `pre-push/quality.mjs`: eski 44–47 arasındaki yinelenen dört satır kaldırıldı.
  Değişken adı değiştirmek yeterli olmazdı: aynı Maven işi iki kez çalışacak ve eski
  `backendTestEnvironment` çağrısı Compose modelini bekleyen işlevle uyuşmayacaktı.
- `pre-push/quality.test.mjs`: iki eski beklenti gerçek Compose çözümlemesine göre
  düzeltildi. Parola terminal profilinden değil çözümlenmiş Compose DB ayarından
  alınır; profil çözümlenemiyorsa Maven başlamadan FAIL olur. Önce 9/11 geçiş ve bu
  iki başarısızlık doğrulandı; sonra 11/11 geçti. Hata/secret/coverage kontrolleri korundu.
- `pre-push/backend-check.mjs`: kullanılmayan `backendTestEnv` import'u kaldırıldı;
  Maven çağrısı hazırlanmış `run(mvn, args)` sarmalayıcısına geçirildi. Önceki çağrı
  profili `dev` olarak sabitliyor ve hazırlanmış bağlantı/maskeleme ortamını atlıyordu.
  Seçilen dev/prod profili, loopback DB bağlantısı, environment aktarımı, parola
  maskelemesi ve Maven hata kodu 7'nin FAIL olarak kalması ayrı sözleşme testleriyle
  doğrulandı. `menu.test.mjs` değiştirilmedi.

Bu üç dosya ortak araç bakım kapsamındadır; düzeltmeler kullanıcı izniyle burada
yapıldı. Başka ortak araç veya Auth/Gateway/diğer servis kaynağı değiştirilmedi.
Game'e ait `AccountErasureMigrationTest.java` içindeki Sonar `java:S8714` bulgusu
`try/catch + fail` yerine `assertDoesNotThrow` kullanılarak giderildi; bütün iç
assertion'lar ve güvenlik kontrolleri korundu.

### 15.2 Gerçek repository kontrolleri

- Ortak JavaScript testleri: **60/60 PASS**, atlanan 0.
- Ek geçici dev/prod/hata-kodu sözleşme testleri: **3/3 PASS**, atlanan 0.
- `pre-push.cmd game-service`: **BACKEND TEST CHECK PASSED**, 303 test,
  JaCoCo satır **%98,76**. Son Game test düzenlemesi ardından tekrar çalıştırıldı.
- `pre-push.cmd library-service`: **BACKEND TEST CHECK PASSED**, 18 test,
  satır **%96,97**.
- `pre-push.cmd review-service`: **BACKEND TEST CHECK PASSED**, 18 test,
  satır **%96,97**.
- `pre-push.cmd full dev`: **1.137 test**, 0 failure/error/skipped;
  genel JaCoCo satır **%95,91**. Git/Secret, Backend Tests, Backend Build,
  Backend Coverage ve Docker Configuration PASS. **Backend HTTP Health FAIL**:
  seçilen DEV Compose projesindeki 18 uygulama konteyneri çalışmıyor.
  Sonuç **NOT SAFE TO COMMIT**, exit 1. Bu, iskelet servislerin kapsamının
  Game/Library/Review'a yüklenmesi değildir; ayrı runtime sağlık kontrolü engelidir.
- İlk izole tüm-backend denemesinde terminalden miras kalan `DEBUG` nedeniyle iki
  Auth log-gizliliği testi başarısızdı. Yalnız test alt süreci için DEBUG/TRACE=false
  yapılarak tekrarlandı ve yukarıdaki 1.137 testin tamamı geçti; Auth kodu veya
  kalıcı ENV değiştirilmedi. Mevcut opsiyonel gitleaks aracı kurulu olmadığı için
  araç kendi SKIPPED uyarısını verdi; kontrol ayarı değiştirilmedi.
- Full kontrol sonrasında yalnız Game testinin Sonar yazım düzenlemesi yapıldı;
  Game'in 303 testi yeniden geçti. Diğer servis kaynakları değişmedi.

Testler ayrı, geçici PostgreSQL 17 ile çalıştırıldı. Spring datasource ve host
PostgreSQL ayarları bu geçici DB'ye yönlendirildi; Game Testcontainers testleri de
kendi geçici DB'lerini kullandı. DEV servislerini başlatmak migration çalıştırabileceği
için sağlık kontrolünü yeşile çevirmek amacıyla başlatılmadılar. İşe ait geçici
PostgreSQL konteyneri adı/etiketi doğrulandıktan sonra temizlendi.

### 15.3 Güncel SonarQube sonucu ve kapsam sınırı

Proje: **ltz-backend**, [yerel panel](http://localhost:5199/dashboard?id=ltz-backend).
CE task `bc9ed252-e5af-4539-9bd4-c1d0e78e9303`, analysis `077ce8ab-22e2-47d7-a014-0f056ca913be`;
işleme **SUCCESS**, tamamlanma `2026-10-24T00:15:29+0000`.
Scanner exit **1**, Quality Gate **ERROR**.
Bu, son Game test düzenlemesini içeren yeni analizdir; eski proje sonucu kullanılmadı.

- `new_coverage`: 92.4; eşik LT 80; **OK**.
- `new_duplicated_lines_density`: 1.24659; eşik GT 3; **OK**.
- `new_violations`: 17; eşik GT 0; **ERROR**.

- Game açık bulgu: **0**.
- Library açık bulgu: **0**.
- Review açık bulgu: **0**.
- Diğer servisler: **19** açık bulgu; tamamı Umut'un
  Social Service kapsamındadır. Yeni kod dönemindeki toplam bulgu **17**.
  Bu bulgular değiştirilmedi, bastırılmadı veya kapatılmış işaretlenmedi.
- Aynı güncel `ltz-backend` analizinde Auth/Gateway açık bulgusu yoktur. Ayrı
  `ltz-auth-gateway` projesindeki geçmiş 49 bulgunun durumu buraya taşınmadı.
- Mevcut Maven analiz kapsamı Java kaynak/testlerini içerir. API dosya envanteri
  sorgusunda V171 SQL ve kökteki `quality.mjs` indekslenmiyor; bunlar için doğrudan
  Sonar statik analiz başarısı iddia edilmez. SQL gerçek PostgreSQL testleriyle,
  ortak JS dosyaları 60+3 test ve sözdizimi denetimleriyle doğrulandı. Sonar kapsamı
  izinsiz genişletilmedi. Commit edilmemiş yeni dosyalarda blame bilgisi eksikliği
  uyarısı vardır; analiz işleme başarısını engellemedi.

Umut'a iletilmesi gereken Social bulguları (dosya/satır ve Sonar kuralı):

- `services/social-service/src/main/java/com/ltz/social/infrastructure/config/JwtConfig.java:46` — `java:S6831`, MAJOR: Remove this redundant "@Qualifier" annotation and rely on the @Bean method.
- `services/social-service/src/main/java/com/ltz/social/infrastructure/config/JwtConfig.java:52` — `java:S6831`, MAJOR: Remove this redundant "@Qualifier" annotation and rely on the @Bean method.
- `services/social-service/src/main/java/com/ltz/social/application/service/FriendsListAccessPolicy.java:56` — `java:S7467`, MINOR: Replace "exception" with an unnamed pattern.
- `services/social-service/src/main/java/com/ltz/social/application/service/FriendshipService.java:59` — `java:S6809`, CRITICAL: Call transactional methods via an injected dependency instead of directly via 'this'.
- `services/social-service/src/test/java/com/ltz/social/ServiceBoundaryTest.java:72` — `java:S9142`, MAJOR: Move this "compile" call outside the loop.
- `services/social-service/src/main/java/com/ltz/social/application/service/OutboxEventRecorder.java:31` — `java:S6213`, MAJOR: Rename this method to not match a restricted identifier.
- `services/social-service/src/test/java/com/ltz/social/application/service/OutboxEventsTest.java:40` — `java:S5838`, MINOR: Use assertThat(actual).containsEntry(key, value) instead.
- `services/social-service/src/test/java/com/ltz/social/application/service/OutboxEventsTest.java:41` — `java:S5838`, MINOR: Use assertThat(actual).containsEntry(key, value) instead.
- `services/social-service/src/test/java/com/ltz/social/application/service/OutboxEventsTest.java:42` — `java:S5838`, MINOR: Use assertThat(actual).containsEntry(key, value) instead.
- `services/social-service/src/test/java/com/ltz/social/application/service/OutboxEventsTest.java:43` — `java:S5838`, MINOR: Use assertThat(actual).containsEntry(key, value) instead.
- `services/social-service/src/test/java/com/ltz/social/application/service/OutboxEventsTest.java:44` — `java:S5838`, MINOR: Use assertThat(actual).containsEntry(key, value) instead.
- `services/social-service/src/test/java/com/ltz/social/application/service/OutboxEventsTest.java:97` — `java:S5778`, MAJOR: Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
- `services/social-service/src/main/java/com/ltz/social/api/controller/AuthenticatedUser.java:17` — `java:S7467`, MINOR: Replace "exception" with an unnamed pattern.
- `services/social-service/src/main/java/com/ltz/social/application/service/FriendRequestService.java:83` — `java:S7467`, MINOR: Replace "exception" with an unnamed pattern.
- `services/social-service/src/main/java/com/ltz/social/application/service/KeysetCursor.java:39` — `java:S7467`, MINOR: Replace "exception" with an unnamed pattern.
- `services/social-service/src/test/java/com/ltz/social/domain/entity/FriendshipIdTest.java:21` — `java:S5838`, MINOR: Use assertThat(actual).hasSameHashCodeAs(expected) instead.
- `services/social-service/src/test/java/com/ltz/social/domain/entity/FriendshipIdTest.java:32` — `java:S5838`, MINOR: Use assertThat(actual).isLessThan(expected) instead.
- `services/social-service/src/test/java/com/ltz/social/SocialServiceApplicationTests.java:8` — `java:S1186`, CRITICAL: Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation.
- `services/social-service/src/test/java/com/ltz/social/SocialServiceApplicationTests.java:8` — `java:S2699`, BLOCKER: Add at least one assertion to this test case.

### 15.4 V171 ve hesap silme doğrulaması

V171 bu kalite çalışmasında değiştirilmedi; başlangıçtaki kaynak kopyasıyla SHA-256
eşitliği doğrulandı. V31–V40 HEAD ile aynı. Yeni geçici PostgreSQL üzerinde migration,
V40'tan veri korumalı yükseltme, gerçek silme/anonimleştirme, hedef kişi audit'leri,
başka hesapların korunması, idempotency, rollback, lock timeout ve audit normal
UPDATE/DELETE yasağı testlerle doğrulandı. Sınırlı runtime rolünün özel bağlamı
taklit edememesi ve kontrollü anonimleştirme istisnası da test kapsamındadır.

Gerçek Auth ServiceTokenIssuer ile sözleşme uyumu, svc+jwt/at+jwt ayrımı, exact
subject/account_id/audience/scope ve diğer imza/süre sınırları üç serviste testlidir.
Library/Review kendi veri içermeyen iskelet sözleşmeleriyle doğrulandı; Game'in
saklama politikası bu servislere taşınmadı. DEV migration uygulanmadı, çalışan
Auth koordinatörüyle uçtan uca hesap silme/CONFIRMED akışı denenmedi. §14.2'deki
eski access token ile sonradan yazma sınırı geçerlidir; yeni saklama kararı alınmadı.

### 15.5 Tam değişen dosya listesi ve açık engeller

Aşağıdaki 45 dosya hesap silme geliştirmesi dahil çalışma ağacının tamamıdır.
Bu kalite incelemesinde ayrıca dokunulanlar yalnız yukarıdaki üç izinli ortak araç,
Game'in migration testi ve bu rapordur. Git add/commit/push/merge/rebase/reset yapılmadı.

- `backend/services/game-service/src/main/java/com/ltz/game/api/exception/GameExceptionHandler.java` (değişti).
- `backend/services/game-service/src/main/java/com/ltz/game/infrastructure/security/GameSecurityConfiguration.java` (değişti).
- `backend/services/game-service/src/main/java/com/ltz/game/infrastructure/security/JwtDecoderConfiguration.java` (değişti).
- `backend/services/game-service/src/main/resources/application.properties` (değişti).
- `backend/services/game-service/src/test/java/com/ltz/game/infrastructure/persistence/MigrationArchitectureTest.java` (değişti).
- `backend/services/game-service/src/test/java/com/ltz/game/infrastructure/persistence/MigrationTransferApplicationTest.java` (değişti).
- `backend/services/game-service/src/test/java/com/ltz/game/infrastructure/persistence/MigrationTransferSupport.java` (değişti).
- `backend/services/game-service/src/test/java/com/ltz/game/infrastructure/security/ProductionAuthSupport.java` (değişti).
- `backend/services/game-service/src/test/java/com/ltz/game/infrastructure/security/RealAuthIntegrationTest.java` (değişti).
- `backend/services/library-service/src/main/resources/application.properties` (değişti).
- `backend/services/review-service/src/main/resources/application.properties` (değişti).
- `docs-Game Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md` (değişti).
- `docs/api.md` (değişti).
- `docs/database.md` (değişti).
- `docs/decisions/migrations.md` (değişti).
- `docs/er-diagram.md` (değişti).
- `pre-push/backend-check.mjs` (değişti).
- `pre-push/quality.mjs` (değişti).
- `pre-push/quality.test.mjs` (değişti).
- `backend/services/game-service/src/main/java/com/ltz/game/api/controller/AccountErasureController.java` (yeni).
- `backend/services/game-service/src/main/java/com/ltz/game/application/service/AccountErasureService.java` (yeni).
- `backend/services/game-service/src/main/java/com/ltz/game/domain/exception/AccountErasurePendingException.java` (yeni).
- `backend/services/game-service/src/main/java/com/ltz/game/infrastructure/persistence/repository/AccountErasureRepository.java` (yeni).
- `backend/services/game-service/src/main/java/com/ltz/game/infrastructure/security/ServiceTokenDecoderConfiguration.java` (yeni).
- `backend/services/game-service/src/main/resources/db/migration/V171__enable_controlled_account_erasure.sql` (yeni).
- `backend/services/game-service/src/test/java/com/ltz/game/api/controller/AccountErasureTest.java` (yeni).
- `backend/services/game-service/src/test/java/com/ltz/game/application/service/AccountErasureServiceTest.java` (yeni).
- `backend/services/game-service/src/test/java/com/ltz/game/infrastructure/persistence/AccountErasureMigrationTest.java` (yeni).
- `backend/services/game-service/src/test/java/com/ltz/game/infrastructure/security/ServiceTokenDecoderConfigurationTest.java` (yeni).
- `backend/services/library-service/src/main/java/com/ltz/library/api/controller/AccountErasureController.java` (yeni).
- `backend/services/library-service/src/main/java/com/ltz/library/api/exception/AccountErasureExceptionHandler.java` (yeni).
- `backend/services/library-service/src/main/java/com/ltz/library/application/service/AccountErasureService.java` (yeni).
- `backend/services/library-service/src/main/java/com/ltz/library/infrastructure/security/AccountErasureSecurityConfiguration.java` (yeni).
- `backend/services/library-service/src/main/java/com/ltz/library/infrastructure/security/ServiceTokenDecoderConfiguration.java` (yeni).
- `backend/services/library-service/src/test/java/com/ltz/library/api/controller/AccountErasureTest.java` (yeni).
- `backend/services/library-service/src/test/java/com/ltz/library/infrastructure/security/ProductionServiceTokenSupport.java` (yeni).
- `backend/services/library-service/src/test/java/com/ltz/library/infrastructure/security/ServiceTokenDecoderConfigurationTest.java` (yeni).
- `backend/services/review-service/src/main/java/com/ltz/review/api/controller/AccountErasureController.java` (yeni).
- `backend/services/review-service/src/main/java/com/ltz/review/api/exception/AccountErasureExceptionHandler.java` (yeni).
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/AccountErasureService.java` (yeni).
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/security/AccountErasureSecurityConfiguration.java` (yeni).
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/security/ServiceTokenDecoderConfiguration.java` (yeni).
- `backend/services/review-service/src/test/java/com/ltz/review/api/controller/AccountErasureTest.java` (yeni).
- `backend/services/review-service/src/test/java/com/ltz/review/infrastructure/security/ProductionServiceTokenSupport.java` (yeni).
- `backend/services/review-service/src/test/java/com/ltz/review/infrastructure/security/ServiceTokenDecoderConfigurationTest.java` (yeni).

Açık engeller: Social bulgularının servis sahibi Umut tarafından giderilmesi ve
güvenli çalışma ortamında 18 uygulama servisinin HTTP sağlık kontrolünün geçmesi.
Gerçek DEV DB'ye dokunmama sınırı nedeniyle burada servisler başlatılmadı.
**SAFE TO COMMIT alınmadı; bütün kontroller başarılı veya dağıtıma hazır denmiyor.**

Git dışı kanıtlar `test-results/erasure-quality-20261024/` altında:
`javascript-tests-final.log`, `backend-wrapper-contract.log`,
`game-service-prepush-sonar-fix.log`, `library-service-prepush-final.log`,
`review-service-prepush-final.log`, `full-dev-final.log`, `backend-metrics.json`,
`sonar-first-result.json`, `sonar-current-result.json`, `sonar-scanner.log`,
`sonar-file-scope.json` ve `changed-files.json`. Bu dosyalar çalışma kanıtıdır,
Git'e eklenmemiştir. Testler ve kalite kayıtları gizli değer içermez.
