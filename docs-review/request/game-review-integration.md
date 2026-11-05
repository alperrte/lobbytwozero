# GAME–REVIEW toplu puan özeti entegrasyon talebi

## 1. Talep eden servis

review-service — Hamza. Bu belge gereksinim bildirir; hedef servise kesin geliştirme emri değildir.

## 2. Hedef servis

game-service — Hamza. Nihai tüketim tasarımı Game/Auth/Review sahiplerince belirlenir. Game kodu değiştirilmedi.

## 3. İlgili task

REV-12 (Review API kabulü tamamlandı; Game→Review entegrasyonu bekliyor), REV-10. Game kataloğunda puan bağımlılığı açık GAME-04/05 ile ilişkilidir.

## 4. Talebin amacı

Game listelerinde LTZ puan ortalaması ve puan veren kullanıcı sayısını sayfa başına tek toplu çağrıyla okuyabilmek. Steam oranları bu verinin yerine kullanılamaz.

## 5. Mevcut durum

docs/api.md Game liste/arama sözleşmelerinde minRating/RATING sıralaması desteklenmiyor; GameExceptionHandler RATING_INTEGRATION_PENDING üretir. Game public POST /api/v1/game/games/batch yalnız ACTIVE kartlar sunar; LTZ puanı üretmez ve yeni Review API'sini tüketmez.

Review'da GET /api/v1/review/games/{gameId}/rating-summary ve POST /api/v1/review/games/rating-summaries uygulandı/test edildi; bu kullanıcı uçları yalnız kullanıcı at+jwt ve mevcut rollerle erişilir, servis token'ı kabul etmez. **Güncelleme (5 Kasım 2026):** Game sunucu çağrısı için Review'da ayrı `POST /internal/v1/games/rating-summaries` iç ucu uygulandı (§14). Auth application.properties içinde Game→Review hedef/kapsamı hâlâ yok.

## 6. Eksik gereksinimler

Game RestClient tüketimi, Auth hedefe özel servis kimliği/izin sözleşmesi ve Review alıcı doğrulaması gerekir. Auth talebi [auth-review-integration.md](auth-review-integration.md) içine eklendi. Bunlar bu fazda uygulanmadı. Kullanıcı JWT'si servis kimliği yerine taşınmayacak; anonim erişim açılmayacak.

## 7. Önerilen entegrasyon sözleşmesi

**Mevcut kullanıcı API veri sözleşmesi:** POST /api/v1/review/games/rating-summaries; application/json `{gameIds: UUID[]}`. Ham dizi en fazla 50; tekrarlar ilk görülme sırasıyla tekilleşir; boş dizi 200/items=[]. Yanıt `{items:[{gameId,averageRating,ratingCount}]}`; iki basamak HALF_UP, puansız oyun null/0. Bilinmeyen ve puansız oyun ayrılmaz; katalog varlık/görünürlük sahibi Game'dir. Mevcut hatalar: 400 validation, 401 kimlik, 403 rol, 413 gövde, 500 sunucu.

**Karar ve uygulama (5 Kasım 2026, Hamza):** Ayrı internal read yolu seçildi: `POST /internal/v1/games/rating-summaries`, scope `rating-summaries:read`, `sub=svc:game-service`. Kullanıcı namespace'ine servis erişimi açılmadı. Ayrıntı §14.

En fazla 50 oyunluk sayfa tek çağrıyla zenginleştirilebilir. Ancak global minRating filtresi/RATING sıralaması yalnız alınmış sayfaya puan eklenerek doğru uygulanamaz. Tüm aday kümede filtre/sıralama ile sayfalama ilişkisi ayrıca tasarlanmalı; bu belge veya sınırlı batch API global sıralamayı çözmüş sayılmaz.

## 8. Kimlik doğrulama ve yetkilendirme

Öneri: Auth D3 uyumlu, tek aud=review-service, sub=svc:game-service ve dar okuma kapsamı. Scope adı/yol henüz kesinleşmedi. RS256/JWKS, typ/issuer/audience/imza/zaman/jti ve tam çağıran/izin allowlist'i ortak sözleşmeyle doğrulanmalı. Kullanıcı rolleri, body/header servis ismi veya account:erase yetkisi bunun yerine geçmez. Secret/ENV veya yeni yetki oluşturulmadı.

## 9. Hata ve güvenilirlik davranışı

Bounded connect/read timeout, sınırlı retry ve token yenileme bütçesi netleştirilmeli. 401/403'te kontrolsüz retry veya kullanıcı token'ına fallback olmamalı. Kesintide puan 0 gösterilmemeli; erişilemeyen veri ile doğrulanmış null/0 ayrılmalı. Cache seçilirse tazelik ve puan güncelleme/hesap silme gecikmesi belgelenmeli. Salt okunur sorgu için outbox/teslim ACK'i gerekmez; rapor altyapısı eklenmedi.

## 10. Kabul kriterleri

- Gerçek Auth token'ıyla yetkili Game çağrısı; yanlış audience/scope/sub/typ, expired token ve kullanıcı-token fallback reddi doğrulanır.
- Sayfa başına tek Review çağrısı; 50 oyun için tek aggregate SQL, N+1 yoktur.
- Tekrar/boş/puansız kimlikler, sıra, yuvarlama, timeout ve yetkisizlik karşılıklı test edilir.
- Puan güncelleme ve hesap silme sonrası gerçek Game görünümü doğrulanır; canlı entegrasyon öncesinde PASS iddiası kurulmaz.
- Global puan filtresi/sıralaması hedeflenirse toplamlar ve sayfa doğruluğu ayrıca test edilir.

## 11. Bağımlılık ve etki

REV-08/REV-12 Review backend kabulünü engellemez. Game tüketimi ve REV-10 kapanışı Auth/alıcı/tüketici sözleşmesine bağlıdır; REV-07 Admin bağımlılığı sürer.

## 12. Durum

**REVIEW TARAFI UYGULANDI (5 Kasım 2026) — GAME TÜKETİCİSİ VE CANLI DOĞRULAMA BEKLİYOR.** Review iç ucu IMPLEMENTED (yerel testler PASS); Auth `game-service → review-service / rating-summaries:read` çifti Auth `origin/main`'de MEVCUT (bf473ed); Game RestClient tüketicisi NOT IMPLEMENTED; canlı Auth/Game uçtan uca NOT VERIFIED. Review dışa aktarma ucu (Seçenek A) uygulandı (§14.4); Game projeksiyonu NOT IMPLEMENTED.

## 13. Karar bekleyen konular

Servis okuma yolu/kapsamı, token dağıtımı ve ömrü, timeout/retry bütçesi, cache tazeliği, hata sunumu, sayfa büyüklüğü uyumu ve global puan filtre/sıralama mimarisi netleştirilmeli. Review onaylanan sözleşmeye sonradan uyarlanacak; nihai tasarım ilgili servis sahiplerine aittir.

## 14. Review tarafında uygulananlar ve Game'e devir notu (5 Kasım 2026)

### 14.1 Uygulanan Review iç ucu

`POST /internal/v1/games/rating-summaries` — Gateway'e yönlendirilmez. Gövde ve yanıt kullanıcı toplu ucuyla birebir aynıdır (`{gameIds:[≤50 UUID]}` → `{items:[{gameId,averageRating,ratingCount}]}`; sıra ilk görülme, tekilleştirme, puansız/bilinmeyen oyun `null/0`, iki basamak HALF_UP). Tam kayıt: [docs/api.md → REVIEW](../../docs/api.md) "Game → Review toplu puan özeti iç ucu".

Kabul edilen kimlik: Auth'un `svc+jwt` token'ı, `aud=review-service`, `sub=svc:game-service` (tam eşleşme), `scope` içinde `rating-summaries:read`, ≤60 sn ömür. Kullanıcı token'ı, başka servis, `account:erase` veya başka kapsam reddedilir; kullanıcı namespace'i (`/api/v1/review/**`) servis token'ı kabul etmeye devam etmez.

### 14.2 Game'in yapması gerekenler (Game kodu bu çalışmada değiştirilmedi)

1. **Auth çifti hazır** (Auth `origin/main`, bf473ed; bkz. [Auth talebi](auth-review-integration.md) §14): `game-service` istemcisi + `targets.review-service=rating-summaries:read`. Secret yoksa/yanlışsa 401 `INVALID_CLIENT`, çift yanlışsa 403 `SERVICE_AUDIENCE_FORBIDDEN`.
2. **Token edinimi:** `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens`, gövde `{clientId:"game-service", clientSecret, audience:"review-service"}`. Yanıt `accessToken/tokenType/expiresIn/scope`. Token 60 sn yaşar; hedef başına bellekte kısa süre tutulup bitimden ~15 sn önce yenilenir. Hazır örnek: social-service `AuthServiceTokenClient` (aynı kalıp). İstemci secret'ı yeni ENV'dir (öneri: `AUTH_GAME_SERVICE_CLIENT_SECRET` Auth tarafında; Game tarafında `AUTH_SERVICE_URL` zaten var) — **ENV değişikliği kullanıcı onayı gerektirir**, bu çalışmada hiçbir ENV değiştirilmedi.
3. **Adres:** Game'e `REVIEW_SERVICE_URL` (Review'ın iç adresi) gerekir; mevcut değilse ENV onayıyla eklenir.
4. **RestClient adaptörü:** `Authorization: Bearer <svc+jwt>`, `Content-Type: application/json`. Sayfa başına tek çağrı (≤50 `gameId`), oyun başına çağrı yok. Bounded connect/read timeout (öneri ≤1 sn / ≤2 sn). **401/403'te yeniden deneme yok** ve kullanıcı token'ına fallback yok; Review bu uçta oran sınırı (429) uygulamaz, 5xx/zaman aşımında en çok bir kısa yeniden deneme.
5. **Kesinti davranışı:** Review erişilemezse sayfa yine kartlarla döner, puan alanı "bilinmiyor" olur — **0 gösterilmez**. Doğrulanmış `averageRating=null, ratingCount=0` ("henüz puan yok") ile "veri alınamadı" ayrı temsil edilmelidir.
6. **Önbellek (isteğe bağlı):** Review tarafında cache yoktur, her çağrı canlı veri döner. Game cache kullanırsa tazelik gecikmesini ve hesap silme/puan güncelleme etkisini belgelemelidir.

### 14.3 Test edilen Review davranışı (kanıt)

`InternalRatingSummaryTest` (18 test) + `ReviewHttpIntegrationTest` (gerçek TCP/RS256/JWKS/PostgreSQL). Auth'un gerçek `ServiceClientTokenIssuer`'ı ile üretilen `game-service → review-service / rating-summaries:read` token'ı kabul edilir; `library-service` istemcisi, `events:ingest` kapsamı, `user-service` audience'ı, kullanıcı token'ı ve `account:erase` token'ı reddedilir. Review modülü 174/174 test PASS, Maven clean verify + coverage-policy PASS, Checkstyle/PMD yeni ihlal 0. **Canlı Auth token'ı ve gerçek Game çağrısı NOT VERIFIED.**

### 14.4 GAME-04 / GAME-05 — karar: Seçenek A uygulandı (5 Kasım 2026, Hamza)

`minRating` filtresi ve `sort=RATING` tüm aday küme üzerinde sayfalama ile doğru olmak zorundadır; ≤50 oyunluk uç bunu çözmez. **Seçenek A seçildi:** Game, Review'dan puan okuma projeksiyonu tutar ve filtre/sıralamayı kendi SQL'inde yapar. Review karşılığı **uygulandı**:

`GET /internal/v1/games/rating-summaries?after={uuid}&limit={1..500}` → `{items:[{gameId,averageRating,ratingCount}], nextAfter}`; yalnız puanı olan oyunlar, `gameId` artan, `after` dışlayıcı imleç, `nextAfter=null` son sayfa. Aynı kimlik/kapsam (`rating-summaries:read`, `sub=svc:game-service`). Tam kayıt: [docs/api.md → REVIEW](../../docs/api.md) "Game → Review puan dışa aktarma iç ucu".

Game'in yapması gerekenler için ayrıntılı rehber: [REVIEW_GAME_SERVICE_DEVIR.md](../REVIEW_GAME_SERVICE_DEVIR.md). Özet: periyodik tam tarama, yalnız hatasız tamamlanan taramada görülmeyen satırları puansız yapma, projeksiyon tablosu için Game migration numarası ekip kararı (V31–V40 dolu), puansız oyunların filtre/sıralamadaki konumunun belgelenmesi.

Seçenek B (Review'da sıralı liste) ve C (kapsamı daraltma) uygulanmadı.
