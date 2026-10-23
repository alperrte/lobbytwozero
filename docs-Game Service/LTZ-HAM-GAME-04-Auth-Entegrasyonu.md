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

D4 servis kimliği uygulanmadı. Kimliksiz iç silme ucu veya yeni servis token
sistemi eklenmedi. Game, Auth silme hedef listesine eklenmedi.

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
