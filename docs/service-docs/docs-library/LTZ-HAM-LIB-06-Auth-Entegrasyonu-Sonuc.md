# LTZ-HAM-LIB-06 — Library ↔ Auth Service entegrasyonu sonucu

Tarih: 30 Ekim 2026. Sorumlu: Hamza. Kapsam: Library'nin Auth Service ile gerçek backend entegrasyonu.
Genel ilerleme: **15/18** (değişmedi). LIB-10, LIB-11, LIB-14 açık; bu çalışma onları tamamlamaz.

> **Güncelleme (2026-10-31):** Bu belge Kerem'in kararlarından (Auth PR #36) önceki durumu anlatır. İç okuma/servis token'ı bölümleri `LTZ-HAM-LIB-07-Auth-Karar-Uyarlamasi-Sonuc.md` ile geçersizleşmiştir (sinyal ucu `signals:read`+`account_id`, Library→User `access:decide`, profil iç uçları DEPRECATED).

Sınır: `backend/services/auth-service/` ve diğer servisler yalnız okundu, değiştirilmedi. `docs-library/request/` belgeleri değiştirilmedi.
Pre-push ve SonarQube çalıştırılmadı (kullanıcı manuel yapacak). Commit/push yok.

## 1. Auth'un gerçek sözleşmesi (kaynak kod esas alındı)

- **Kullanıcı token'ı:** RS256, başlık `typ=at+jwt` + `kid`; claim'ler `iss=ltz-auth`, `sub`=hesap UUID, `aud`=`ltz-api` (tek değer, JSON'da string),
  `iat`, `nbf`, `exp`, `jti`, `sid`, `ver`, `roles`, `amr`. Roller `PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN`.
  Token yalnız HttpOnly çerezdedir; Gateway çerezi Bearer'a çevirir. JWKS: `GET /api/v1/auth/.well-known/jwks.json` (`kid` thumbprint tabanlı; rotasyon için ek açık anahtarlar yayınlanabilir).
- **İptal:** Auth kendi uçlarında oturumu/hesabı/`ver`'i her istekte DB'den denetler (`SessionStateValidator`). Diğer servislere introspection veya iptal listesi **sunmaz**;
  iptal token ömrüyle yayılır (`docs/api.md` politikası: prod 60 sn + 30 sn tolerans).
- **Servis token'ı (mevcut):** `POST /internal/v1/service-tokens` (client credentials, tek `aud`, `scope`, 60 sn, **başka claim yok**). Kayıtlı istemciler admin, social, community, messaging, lfp;
  hedef kapsamları yalnız `events:ingest`, `blocks:check`. Kullanıcıya/hedefe bağlı token üretimi **yoktur**.
- **D4 hesap silme:** `HttpAccountDataEraser` → `DELETE <SERVİS>_SERVICE_URL/internal/v1/accounts/{id}`, `ServiceTokenIssuer` ile 30 sn `svc+jwt`
  (`sub=svc:auth-service`, `aud=<servis>-service`, `scope=account:erase`, `account_id`); yalnız 200/204 onaydır; silme 14 günlük bekleme sonrası başlar.

## 2. Uyumluluk matrisi

| # | Mekanizma | Sınıf | Kanıt |
| --- | --- | --- | --- |
| 1 | Kullanıcı JWT: RS256 + JWKS imza doğrulaması | MEVCUT VE UYUMLU | Canlı Auth token'ı Library'de 200 (iki ayrı tur); `UserTokenDecoderConfigurationTest` |
| 2 | `typ`, `kid`, `iss`, `aud`, `iat/nbf/exp`, `sub` UUID | MEVCUT VE UYUMLU | Canlı + birim; Auth'un tek-string `aud` biçimi kabul ediliyor |
| 3 | `roles` allowlist'i | MEVCUT VE UYUMLU | Auth `RoleCode` ile Library allowlist'i birebir aynı; bilinmeyen/biçimsiz rol yetki vermez |
| 4 | Sahiplik (`sub`'dan kimlik), gövde/query/header kimliği | MEVCUT VE UYUMLU | Canlı: gövdede `userId` 400; query/`X-User-Id`/`X-Role` etkisiz |
| 5 | Başka kullanıcının koleksiyonu (gizlilik) | MEVCUT VE UYUMLU (fail-closed) | Canlı: 503; `UnavailableLibraryVisibility` korundu |
| 6 | Gateway çerez→Bearer, CSRF/Origin | MEVCUT VE UYUMLU | Canlı: çerez GET 200, çerezle POST CSRF'siz 403, CSRF+Origin ile geçer |
| 7 | İç uçlar kullanıcı token'ını reddeder | MEVCUT VE UYUMLU | Canlı: iç profil/öneri/D4 uçları 401; Gateway iç yolu 404 |
| 8 | D4 `account:erase` token'ı ↔ Library doğrulayıcısı | MEVCUT VE UYUMLU (kaynak + HTTP testi) | `AccountErasureTest`; yeni `AuthErasureAdapterIntegrationTest` (Auth'un gerçek HTTP adaptörü) |
| 9 | D4 canlı Auth silme akışı | GERÇEK SERVİSLE DOĞRULANMADI | 14 günlük bekleme süresi; dev ortamı değiştirilemez |
| 10 | Oturum/hesap iptalinin Library'ye etkisi | AUTH POLİTİKASI (introspection yok) — Library'de yapılacak şey yok | Canlı gözlem §4 |
| 11 | İç okuma uçları (User T1 / Recommendation T2): `library:*` kapsam + `target_user_id`/`viewer_id` | AUTH TARAFINDA DESTEKLENMİYOR | Auth bu claim/kapsamı üretmiyor; istemciler kayıtlı değil |
| 12 | Library→User gizlilik kararı için servis token'ı (T3) | AUTH TARAFINDA DESTEKLENMİYOR + KAPSAM DIŞI | `library-service` istemcisi ve `privacy:library:check` kapsamı yok; User ucu da yok |
| 13 | User gizlilik kararı, FRIENDS/engel, Recommendation izni | KAPSAM DIŞI / SONRAKİ ENTEGRASYONA BAĞLI | User, Social, Recommendation henüz hazır değil |

Library kodunda Auth ile **çelişen** veya uyarlanması gereken bir kural bulunmadı; üretim kodu değiştirilmedi. #11 ve #12 için Library'yi gevşetmek güvenlik garantisini
zayıflatacağından (kullanıcı kimliği/viewer'ı doğrulanamaz alanlardan almak, kapsamsız servis erişimi) uygulanmadı.

## 3. Yapılan değişiklikler

- `AuthErasureAdapterIntegrationTest` (yeni): Auth'un gerçek `HttpAccountDataEraser` + `ServiceTokenIssuer` kaynaklarını geçici dizine derler (kopyalamaz, Auth'a dokunmaz),
  Library'nin gerçek HTTP sunucusuna (RANDOM_PORT, gerçek güvenlik zinciri/JWKS/PostgreSQL) karşı çalıştırır. Kanıtlar: 204 onayı, tekrar/veri olmayan hesapta idempotentlik,
  yalnız bağlı hesabın silinmesi (diğer kullanıcı korunur), başka `aud` token'ının 401 ile onaylanmaması, hata iletisinde token yok.
- `UserTokenDecoderConfigurationTest` (yeni, 8 test): kullanıcı JWT doğrulayıcısı gerçek RS256/JWKS ile — Auth şeklinde token, rotasyon örtüşmesi (geçerli+önceki anahtar),
  yabancı anahtar, bozuk imza/payload, `alg=none`, HS256 algoritma karışıklığı, yanlış `typ`, eksik/bilinmeyen `kid`, süre dolmuş/henüz geçerli değil, yanlış issuer/audience,
  kanonik olmayan `sub`, eksik `nbf`/`iat`, bilinmeyen/biçimsiz roller.
- `docs/api.md` LIBRARY: iki kayıtta test durumu güncellendi. `docs-security/library-service/NOTLAR.md`: Auth entegrasyon notları.
- Üretim kodu, migration, ENV, Compose, Gateway: **değişmedi**.

## 4. Canlı Auth ↔ Library doğrulaması (ltz-dev)

Yöntem: yalnız yerel dev yığını; her turda iki yeni sentetik hesap (gerçek kayıt → Mailpit kodu → e-posta doğrulama → giriş). Token/parola çıktıya yazılmadı. Library ilk turda
mevcut imajla, ikinci turda **güncel kaynaktan yeniden derlenmiş imajla** (`ltz/library-service:dev`, yalnız Library konteyneri yenilendi) denendi.

- Tur 1 (eski imaj): kurulum 10/10, kontroller 33/33 PASS. Tur 2 (güncel imaj): kurulum 10/10, kontroller 33/33 PASS.
- Gözlenen Auth token'ı: `typ=at+jwt`, `alg=RS256`, `kid`, `iss=ltz-auth`, `aud=ltz-api`, rol `PLAYER`, **ömür 900 sn** (dev yapılandırması; `docs/api.md` prod değeri 60 sn der).
- Pozitif: kendi koleksiyon/favori/bitirilen/istatistik/profil okumaları 200 (boş koleksiyon, `no-store`); olmayan oyunla ekleme/favori 404 (kimlik doğrulaması geçti, Game doğrulaması reddetti); silme idempotent 204.
- Sahiplik: başka kullanıcının koleksiyonu ve sayacı (iki yönde) 503 fail-closed; gövdede `userId` 400; query `userId`, `X-User-Id`, `X-Role` etkisiz.
- Token negatifleri: token yok / çöp / bozuk imza / sub değiştirilmiş payload / `alg=none` hepsi 401.
- İç uçlar: kullanıcı token'ı iç profil/öneri/D4 uçlarında 401; D4 ucu anonim 401; Gateway iç yolları 404.
- Gateway: çerezle GET 200; çerezle POST CSRF'siz 403; CSRF+Origin ile Library'ye ulaşır (404 olmayan oyun); anonim 401.
- Günlük: Library konteyner günlüklerinde `Bearer`/`eyJ`/`Authorization` ve hata/istisna satırı yok.
- Süre dolumu ve iptal gözlemi: bkz. §4.1.

Pozitif yazma akışı (gerçek oyunla ekleme/favori/bitirildi) bu çalışmada tekrarlanmadı: dev Game kataloğu boştur ve başka servisin DB'sine fixture yazılmadı.
Bu akış daha önce `docs-library/LTZ-HAM-LIB-01-E2E-Sonuc.md` (62/62) ile gerçek Auth oturumundan doğrulanmıştır; `ops/library-phase1-e2e.mjs` geçici Game fixture'ı oluşturduğundan kullanıcı onayıyla tekrarlanabilir.

Temizlik: dört sentetik hesabın hepsi için Auth API'siyle silme talebi ve onayı verildi (14 günlük bekleme süresi korundu); Library'de test verisi oluşmadı. Gerçek kullanıcı hesabına dokunulmadı. Bekleme bitince Auth'un bu hesaplar için yapacağı D4 çağrısı Library'de gerçek bir silme akışı olarak gözlenebilir.

### 4.1 Süre dolumu ve iptal (gözlem)

Auth access token ömrü bu ortamda 900 sn'dir. Canlı gözlem (6/6 PASS):

- Süresi dolmuş token (exp + 40 sn sonrası, 30 sn saat toleransı aşıldıktan sonra): Library doğrudan **401**, Gateway çerez yolu **401**.
- Oturumu kapatılmış (logout) token: Auth kendi ucunda **401** verir; Library token süresi dolana kadar aynı token'ı **200** ile kabul etti, `exp + 40 sn` sonrasında **401**.
  Bu, Auth'un belgelenmiş politikasıdır (introspection yok, iptal token ömrüyle yayılır) ve Library'nin yerel JWT doğrulamasının doğal sonucudur. Library bunu kapatmaya çalışmaz:
  Auth'un sunmadığı bir iptal sorgusu uydurulmadı. Pencere = access token ömrü (+ en çok 30 sn). Dev'de 900 sn, `docs/api.md` politikasında prod için 60 sn.
- Bu pencerede Library'nin sahiplik garantisi korunur: token yalnız kendi sahibinin verisine erişir; risk, iptal edilmiş oturumun kısa süre daha kendi verisini okuyabilmesidir.
  Daha kısa pencere gerekiyorsa karar Auth'undur (token ömrü veya ayrı bir iptal sözleşmesi).

## 5. Testler

- Maven (`library-service`, `-Pci`, izole PostgreSQL) `clean verify`: **110 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**
  (önceki 100 + `AuthErasureAdapterIntegrationTest` 2 + `UserTokenDecoderConfigurationTest` 8). Satır kapsamı **%98,39**, merkezi coverage kapısı PASS.
- Checkstyle/PMD ratchet: library-service yeni ihlal 0.
- Yerel mod (Testcontainers) için iki yeni test ayrıca koşuldu: PASS.
- Çalıştırılamayan/NOT VERIFIED: canlı Auth D4 silme akışı; gerçek oyunla pozitif yazma E2E'si bu turda; User/Recommendation/Social gerçek entegrasyonları.
- Mock/sahte ile doğrulanmış (gerçek servisle değil): User görünürlük kararı (test portu), Game kataloğu (MockRestServiceServer/mock), D4 imza anahtarı (test anahtarı).

## 6. Kalan bağımlılıklar

- **Auth (Kerem):** kullanıcıya/hedefe bağlı servis token'ı (T1/T2) ve `library-service`/`user-service`/`recommendation-service` istemcileri henüz yok. Library tarafı hazır bekliyor; yöntem Auth'un kararıdır (`docs-library/request/auth-library-Integration-request.md`).
- **User (Kerem):** gizlilik karar mekanizması (LIB-10/LIB-11). **Recommendation (Alper):** öneri izni ve tüketici (LIB-14). **Social (Umut):** FRIENDS/engel sorguları (User üzerinden).
- Auth sahibi bir sözleşme seçtiğinde Library'nin claim/kapsam okuması ve `LibraryVisibilityPort` HTTP adaptörü o sözleşmeye uyarlanacaktır (bu çalışmada yapılmadı).

## 7. Güncel checklist

Faz 1 — [x] LIB-01, [x] LIB-02, [x] LIB-12, [x] LIB-13, [x] LIB-15
Faz 2 — [x] LIB-03, [x] LIB-04
Faz 3 — [x] LIB-05, [x] LIB-06, [x] LIB-07, [x] LIB-08, [x] LIB-09, [x] LIB-16
Faz 4 — [ ] LIB-10, [ ] LIB-11, [ ] LIB-14, [x] LIB-18
Faz 5 — [x] LIB-17

Genel ilerleme **15/18**. Auth bağlantısı LIB-10/11/14'ü tamamlamaz.
