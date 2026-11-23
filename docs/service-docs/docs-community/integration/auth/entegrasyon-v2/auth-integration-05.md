# Auth Entegrasyonu 05 — İkinci aşama: beklentilerin karşılık denetimi ve kapatılan eksikler

> Tarih: 2026-10-29 · Servis: community-service (Nisa)
> Birinci aşama belgeleri: `../entegrasyon-v1/auth-integration-01..03.md`; talepler ve cevaplar: `../entegrasyon-v1/auth-service-talepleri/auth-requests-01..04.md`.
> Bu aşamanın talebi ve Kerem'in cevabı: `auth-service-talepleri/auth-requests-05.md` (§6; yazılı cevap `docs-auth/verilen-cevaplar/community-service/talep-05-*.md`). Auth'un yapısı değişmedi; yalnız community Auth'un yayımladığı sözleşmeye uyduruldu.

## 1. Yöntem

Önce community'nin dört talebi (`auth-requests-01..04`) ve Auth'un cevap dosyası (`docs-auth/entegrasyon/community-service.md`, `docs-auth/verilen-cevaplar/community-service/*`, `servis-sozlesmeleri.md` §3.5, `docs/api.md` Auth kayıtları) birlikte okundu. Auth'un söylediği her şey **iki kaynakta** sınandı: Auth'un kendi kodunda (salt okunur: token verme, olay gönderme, yük biçimi) ve community'nin koduyla testlerinde. "Cevaplandı" ile "gerçekten yerine getirildi" ayrı tutuldu.

## 2. Karşılık matrisi (beklenti → Auth'un cevabı → gerçek durum → community'de yapılan)

| # | Community'nin beklentisi | Auth'un cevabı | Auth tarafında gerçek durum | Community'de durum (bu aşama sonu) |
| --- | --- | --- | --- | --- |
| 01-a | Gelen olay çağrıları için `events:ingest` kapsamlı servis token'ı (`admin→community`, `social→community`) | Kabul, çift başına tanım | Çiftler `docs-auth` belgesinde "tanımlı" | Alıcı hazır ve testli. Gerçek Auth token'ıyla uçtan uca: **NOT VERIFIED** |
| 01-b | Giden çağrı için token edinme yolu | Auth'un token ucu + community'ye özel istemci ve gizli anahtar | `POST /internal/v1/service-tokens` yayında (`api.md`), `community-service` istemcisi `admin`/`notification` hedefleri için tanımlı | **Bu aşamada bağlandı:** `AuthServiceTokenClient` + `EventDeliveryClient` `Authorization: Bearer` + relay koruması |
| 01-c | Kapsam adı `events:ingest` uygun mu | Uygun | — | Sabit `TokenClaims.EVENT_INGEST_SCOPE`; `SecurityConfig` `SCOPE_events:ingest` ister (eski `internal.events` kalmadı) |
| 02-a | Anonimleştirme, sabit kimlik, `[removed]` yeterli mi | Çelişmiyor; Auth çizgisi "silinen kullanıcının kaydı tutulmaz, anonim bırak ya da sil" | — | Karşılandı; raporlar anonim ve süresiz (süre kararı Alper'de) |
| 02-b | Rapor saklama süresi / hukuki gerekçe | Auth'un kararı değil, Alper'in | — | **Açık** (Alper). Süre ayarı kodlanmadı: yanlış süreyle veri silmemek için |
| 02-c | Dev'de silip `CONFIRMED` görmek | Evet; dev'de bekleme süresi `PT0S`, yeni Auth + güncel community imajı | Auth `main`'de; dev ayarı ekipte | **Açık** (ortam). Community ucu hazır |
| 03-a | `AccountStatusChanged` ne zaman, içerik gizlensin mi | Auth'ta hazır (2026-10-29, outbox), yalnız yönetici durum değişikliğinde, hedef listesi boş; gizleme community'nin kararı | `AuthEventRelay` yükü `{accountId, status}`, `producer=auth-service`, token `events:ingest` | **Bu aşamada alıcı yazıldı** (kayıt + tekilleştirme, içeriğe etki yok). Auth'ta `community` hedefi eklenmeli → `auth-requests-05.md` §3 |
| 03-b | Rol iptali gecikmesi | Ek önlem yok, token ömrü içinde yayılır | Kullanıcı token'ı 60 sn + 30 sn tolerans | Ek önlem yok (kabul) |
| 03-c | JWKS önbelleği / anahtar dönüşümü | ≤ 5 dk önbellek, bilinmeyen `kid`'de bir kez yenile | — | Zaten uyumlu (`JwtConfigTest`) |
| 03-d | JWKS kapalıyken davranış | fail-closed beklenir | — | 401 (testli) |
| Auth listesi | Gerçek JWT doğrulayıcı | `README.md` §1 kuralları: `exp/nbf/iat/sub` zorunlu | — | **`nbf` zorunluluğu bu aşamada eklendi** (daha önce `exp/iat/sub`); eksik `nbf`/`iat` reddedilir |
| Auth listesi | Silme ucu | `DELETE /internal/v1/accounts/{id}` | `account_deletion_tasks` 16 hedef | Hazır (birinci aşama) |
| Auth listesi | `COM-04` `AccountDeleted` beklentisi | Olay yok, senkron `DELETE` | — | Geçersiz sayıldı (belgelendi) |
| Auth listesi | Görsel çerez akışı | Gateway çerezi Bearer'a çevirir | — | Community'de değişiklik yok: yine yalnız `Authorization` doğrular |

**Özet:** Auth'un community için söz verdiği her şey Auth tarafında **yerinde** görünüyor; "yerine getirilmemiş" bir Auth maddesi bulunmadı. Community'de eksik kalanlar üçtü ve üçü de bu aşamada kapatıldı: (1) giden token, (2) `nbf` zorunluluğu, (3) `AccountStatusChanged` alıcısı. Kalan açık işler **community dışıdır** (Alper'in saklama kararı, ortam/imaj, Auth'ta hedef ekleme).

## 3. Yapılan değişiklikler (v2)

### 3.1 Giden servis token'ı

```
OutboxRelay ──(tick)──► ServiceTokenPort.isConfigured()? ──hayır──► uyar (bir kez), hiçbir olay denenmez
      │ evet
      ▼
OutboxDispatcher ► EventDeliveryClient.deliver(hedef)
      │                    │ tokenFor("admin-service" | "notification-service")
      │                    ▼
      │            AuthServiceTokenClient ──► POST {AUTH_SERVICE_URL}/internal/v1/service-tokens
      │                    │                  { clientId:"community-service", clientSecret:<ENV>, audience:<hedef> }
      │                    ◄── { accessToken, expiresIn:60, ... }
      ▼
POST {hedef}/internal/events   Authorization: Bearer <accessToken>
```

- **Hedef başına ayrı token** (`aud` o hedefe bağlı; admin token'ı notification'da geçmez).
- **Önbellek kısa:** Auth "uzun süre önbelleğe alma" der. İstemci token'ı yalnız bitişine 15 sn kalana kadar yeniden kullanır, yanıt ne derse desin en çok 60 sn tutar; 15 sn'den kısa ömürlü token hiç önbelleğe alınmaz. Böylece relay'in bir partisindeki onlarca olay tek token isteğiyle gider, süresi dolmak üzere olan token gitmez.
- **Fail-closed:** Gizli anahtar boşsa (`AUTH_COMMUNITY_SERVICE_CLIENT_SECRET`) istemci kapalıdır; `OutboxRelay` hiç dispatch etmez, olaylar `PENDING` kalır ve **deneme hakkı yanmaz** (yoksa 10 denemede `FAILED` olurlardı).
- **Sızıntı yok:** Hata iletilerinde yanıt gövdesi, token ve gizli anahtar yoktur (yalnız istisna türü); test bunu doğrular.
- **Yeni ENV:** yok. `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` `.env.example`'da vardı; bu aşamada `docker-compose.yml` community ortamına geçirildi (**kullanıcı onayıyla**, ortak dosya). Değeri hiçbir yerde okunmadı/yazılmadı.
- Yeni ayarlar: `ltz.clients.auth-service.url`, `ltz.community.auth.client-id=community-service`, `ltz.community.auth.client-secret`.

### 3.2 `nbf` zorunluluğu

Auth `README.md` §1: kullanıcı ve servis token'larında `exp`, `nbf`, `iat`, `sub` bulunur. `JwtConfig.REQUIRED_CLAIMS` bunlara `nbf`'yi ekledi. Auth'un üreticileri zaten `nbf` koyuyor; yalnız elle üretilmiş eksik token reddedilir.

### 3.3 `AccountStatusChanged` alıcısı

`InboundEventService`: `producer=auth-service` olmak zorunda; yük `{accountId: UUID, status: metin}` doğrulanır (geçersiz yük inbox'a "işlendi" diye yazılmaz); `eventId` ile tekilleştirilir; **içeriğe etki yok**. Karar (kullanıcıyla): Auth gizlemeyi zorunlu kılmıyor ve bu ürün kararı; alıcı yazılır ama içerik gizlenmez. Daha sonra "yasaklıysa içeriği gizle" kararı verilirse etki yalnız bu metoda eklenir.

## 4. Testler (yeni/değişen)

| Test | Kanıt |
| --- | --- |
| `AuthServiceTokenClientTest` (yeni) | İstek gövdesi (istemci, gizli anahtar, hedef); yanıt ayrıştırma (bilinmeyen alanlar yok sayılır); önbellek: yeniden kullanım, 45. sn sonrası yenileme, hedef başına ayrı, 60 sn üst sınır, kısa ömürlü token önbelleğe alınmaz; boş gizli anahtar = Auth'a hiç gitmez; 401/403/5xx → `ServiceTokenUnavailableException` ve **gizli anahtar mesajda yok**; başarısız istek önbelleğe girmez; boş/eksik `accessToken` reddedilir |
| `EventDeliveryClientTest` | Her hedefe doğru `Authorization: Bearer` (admin ve notification ayrı); token alınamazsa istek hiç gönderilmez |
| `OutboxRelayTest` | Gizli anahtar yokken dispatch yok (deneme yanmaz); anahtar gelince devam eder |
| `JwtConfigTest` | Eksik `nbf`/`iat` kullanıcı token'ında, eksik `nbf` servis token'ında reddedilir |
| `InboundEventServiceIntegrationTest` (gerçek PostgreSQL) | `AccountStatusChanged`: bir kez işlenir (ikincisi `DUPLICATE`), içerik değişmez, `admin-service` adına gelen reddedilir, eksik/boş yük reddedilir ve inbox'a yazılmaz |

## 5. Hâlâ doğrulanamayan şeyler (dürüst liste)

- **Gerçek Auth token'ıyla uçtan uca** (hem gelen `events:ingest` hem giden `service-tokens`): bu makinede Auth çalıştırılmadı, test token'ları Auth'un belgelenmiş biçimiyle üretilir → **NOT VERIFIED**. Yanıt alan adları (`accessToken`, `expiresIn`) `docs/api.md` Auth kaydına ve Auth kodundaki göndericiye göre yazıldı.
- **Admin/notification alıcı uçları** yazılana kadar relay'in gerçek teslimi doğrulanamaz; relay varsayılan KAPALI kalır (`ltz.outbox.relay.enabled=false`). Açmadan önce: gizli anahtar tanımlı, alıcı uç yayında, Auth'ta çiftler etkin.
- **Dev'de hesap silme `CONFIRMED`:** ortam şartları (`auth-requests-04.md` §4).
- **Dev DB:** paylaşılan `community_db` migration V92'de; bu aşama migration eklemedi (yeni şema yok).

## 6. Kerem'in cevabından sonra (2026-10-29)

Auth üç teyidi de verdi (`auth-requests-05.md` §6): `community` hedefini Kerem `AUTH_EVENT_TARGETS=community` ile ekleyecek (Compose'a aktarım eksiğini kendisi kapattı); `svc:auth-service → community-service` çifti hazır (token ek olarak `account_id` taşır, community yok sayar ve testle sabit); `community-service` istemcisi dev'de etkin, yanıt alanları bizim ayrıştırmamızla aynı. Auth'a yönelik açık istek kalmadı. Hâlâ yapılmamış olan, iki tarafın da **gerçek jar'larla birlikte** bir denemesidir (NOT VERIFIED).

## 7. Sonraki adımlar

1. Nisa → Kerem: **"alıcı yayında" haberi** (bu dal birleşip dev imajı güncellenince); Kerem `AUTH_EVENT_TARGETS=community` ekler.
2. Nisa → Alper: rapor saklama süresi; Gateway'in `/internal/**` yolunu route etmediği teyidi.
3. Birlikte: dev'de (a) hesap silme `CONFIRMED`, (b) durum değişikliğinin community inbox'ında görünmesi, (c) relay'i açıp rapor olayının admin'e Auth token'ıyla ulaşması.
