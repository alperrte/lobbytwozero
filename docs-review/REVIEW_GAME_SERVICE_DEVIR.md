# Review → Game Service devir notu (REV-12 · GAME-04 / GAME-05 / GAME-15)

**Tarih:** 5 Kasım 2026 · **Hazırlayan:** Review tarafı (Hamza) · **Hedef:** Game Service'te çalışan kişi / yapay zekâ asistanı

Bu belge, Game Service'in `GAME-04`, `GAME-05` ve `GAME-15` için Review'dan beklediği şeylerin **Review tarafında hazır olduğunu** ve Game'in artık ne yapması gerektiğini söyler. Game kodu, Game migration'ları, Auth ve ENV dosyaları bu çalışmada **değiştirilmedi**.

> AI asistanlar için: Bu belgede yazmayan bir Review davranışını varsayma. `review_db`'ye asla erişme; yalnız aşağıdaki iç uçları kullan. Sözleşmenin asıl kaydı `docs/api.md` → `## REVIEW` içindeki iki "Game → Review" kaydıdır.

## 1. Tek bakışta durum

| Parça | Durum |
| --- | --- |
| Review iç ucu: toplu puan özeti `POST /internal/v1/games/rating-summaries` | **Hazır** (Review tarafı, yerel testler PASS) |
| Review iç ucu: imleçli dışa aktarma `GET /internal/v1/games/rating-summaries` | **Hazır** (Review tarafı, yerel testler PASS) |
| Auth: `game-service` istemcisi + `review-service` hedefi + `rating-summaries:read` kapsamı | **Auth tarafında MEVCUT** — `origin/main`, commit bf473ed (LTZ-KER-AUTH-37): `game-service.targets.review-service=rating-summaries:read`. Game'in secret'ı (`AUTH_GAME_SERVICE_CLIENT_SECRET`) ve dağıtımı ENV onayı bekliyor; canlı doğrulama yapılmadı |
| Game: RestClient adaptörü, token istemcisi, ENV | **Yapılmadı — Game** |
| Game: puan projeksiyonu tablosu + migration | **Yapılmadı — Game** (migration numarası ekip kararı) |
| Game: `minRating` / `sort=RATING` açılması | **Yapılmadı — Game** (şu an 400 `RATING_INTEGRATION_PENDING`) |
| Canlı Auth/Game/Review uçtan uca | **NOT VERIFIED** |

Review doğrulaması: Review modülü **174/174 test PASS** (failure/error/skipped 0/0/0), Maven `clean verify` + coverage-policy PASS, Checkstyle/PMD yeni ihlal 0. Testler gerçek TCP/RS256/JWKS/PostgreSQL ve Auth'un gerçek `ServiceClientTokenIssuer`'ı ile üretilen `game-service → review-service / rating-summaries:read` token'ını kapsar. Bunlar canlı Auth veya gerçek Game çağrısı kanıtı değildir.

## 2. Review'ın sunduğu iki uç

Ortak kural: Gateway'e yönlendirilmez; yalnız servis token'ı. Kabul edilen token: `typ=svc+jwt`, `iss=ltz-auth`, **tek** `aud=review-service`, `sub=svc:game-service` (tam eşleşme), `scope` içinde `rating-summaries:read`, ≤60 sn ömür. Kullanıcı token'ı (ADMIN dahil) 401; başka kapsam/başka çağıran 403. Kullanıcı token'ına geri düşme (fallback) yoktur. Yanıtlarda `Cache-Control: private, no-store`.

### 2.1 Toplu özet — sayfa/kart zenginleştirme (GAME-15, kart puanı)

`POST /internal/v1/games/rating-summaries` · gövde `{"gameIds":[…]}` (**en fazla 50**, boş dizi geçerli, tekrarlar ilk görülme sırasıyla tekilleşir, gövde ≤64 KiB).
Yanıt `{"items":[{"gameId","averageRating","ratingCount"}]}` — istek sırasında, her benzersiz oyun için bir satır. Puansız veya Review'ın hiç bilmediği oyun: `averageRating=null`, `ratingCount=0` (404 yok). `averageRating` iki ondalık (HALF_UP), `ratingCount` benzersiz puan veren kullanıcı sayısı. Review katalog varlığını doğrulamaz; o Game'in işidir.

### 2.2 İmleçli dışa aktarma — puan projeksiyonu (GAME-04/05)

`GET /internal/v1/games/rating-summaries?after={uuid}&limit={1..500}` · `limit` varsayılan 200, `after` dışlayıcı imleç (yoksa baştan).
Yanıt `{"items":[{"gameId","averageRating","ratingCount"}],"nextAfter":"<uuid>|null"}`. Yalnız **en az bir puanı olan** oyunlar, `gameId` artan sırada. `nextAfter` null ise son sayfa. Listede olmayan oyun = puansız.

## 3. Game'in yapması gerekenler

### 3.1 Önce (bloklayıcı)
1. **Auth tarafı hazır** (`origin/main`, bf473ed): `game-service` istemcisi, `targets.review-service=rating-summaries:read`, secret ENV adı `AUTH_GAME_SERVICE_CLIENT_SECRET`. Game çalışma dalına `main`'in alınması (merge) senin işindir. Auth'un doğrulaması: `game-service` + doğru secret + `audience=review-service` → 200, `scope=rating-summaries:read`, `aud=["review-service"]`, `sub=svc:game-service` (`ServiceAudienceTokenTest`). **Kapsam adı `rating-summaries:read`'tir** (önceki `ratings:read` önerisi kullanılmıyor). Canlı Review çağrısı için secret'ın Game ortamına ENV onayıyla verilmesi gerekir; ondan önce geliştirme/test yerel imzalı token + sahte Review sunucusuyla yapılabilir.
2. **ENV onayı** (kullanıcı onayı şart, `.env*` dosyalarına dokunma): Game için `REVIEW_SERVICE_URL` (Review iç adresi) ve Game'in Auth istemci secret'ı. Önce `.env.example` önerisi sun.

### 3.2 Token ve istemci
- Token: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens`, gövde `{"clientId":"game-service","clientSecret":"…","audience":"review-service"}` → `accessToken/tokenType/expiresIn/scope`. 60 sn yaşar; bitimden ~15 sn önce yenile, hedef başına bellekte tut. Hazır örnek: `backend/services/social-service/.../AuthServiceTokenClient.java` (aynı kalıbı Game'in katman kurallarına göre uyarla; kopyalarken `docs/folder-structure.md`'ye uy).
- RestClient: `Authorization: Bearer <svc+jwt>`. Bounded connect/read timeout (öneri ≤1 sn / ≤2 sn). **401/403'te yeniden deneme yok.** 5xx/zaman aşımında en çok bir kısa yeniden deneme. Hata iletilerine token, secret, yanıt gövdesi yazma.
- **Review erişilemezse puanı 0 gösterme.** "Henüz puan yok" (`null/0`, doğrulanmış) ile "veri alınamadı" ayrı temsil edilmeli; listeler puansız/bilinmeyen alanla yine dönebilir, Game Review'a senkron bağımlı kalıp kendi sayfasını kırmamalı.

### 3.3 GAME-15 — kart/sayfa zenginleştirme
Sayfa başına **tek** toplu çağrı (≤50 `gameId`), oyun başına çağrı yok (N+1 yasak). Mevcut `POST /games/batch` kartları LTZ puanı döndürmüyordu; puan alanı eklenecekse bunu yeni `docs/api.md` GAME kaydıyla belgele (alan adı, `null` semantiği, "alınamadı" temsili).

### 3.4 GAME-04 / GAME-05 — puan projeksiyonu (Seçenek A)

Neden projeksiyon: ≤50'lik uç yalnız getirilmiş sayfayı zenginleştirir; tüm katalogda `minRating` filtresi ve `sort=RATING` sıralaması sayfalama ile birlikte ancak Game'in kendi SQL'inde doğru olur.

**Önerilen şema (Game sahibi karar verir):** `game_rating_projection(game_id uuid PK, average_rating numeric(4,2) NULL, rating_count bigint NOT NULL, synced_at timestamptz NOT NULL)`; `game_id` Game kataloğunda varsa REF. Migration numarası **ekip kararı**: Game aralığı V31–V40 doldu (`docs/decisions/migrations.md`); numara uydurma, kullanıcıya sor. `ddl-auto=validate` nedeniyle entity ve migration birlikte gelir.

**Tarama protokolü:**
1. `after` olmadan başla, `nextAfter` null olana kadar sayfaları sırayla çek (`limit=500` makul).
2. Her sayfayı upsert et (`average_rating`, `rating_count`, `synced_at`).
3. Tarama **hatasız tamamlanırsa**, bu taramada görülmeyen satırları puansız yap (`average_rating=NULL`, `rating_count=0`). Son puanı hesap silmeyle kalkan oyun listede görünmez; bu adım olmadan eski puan sonsuza dek kalır.
4. Tarama yarıda kesilirse (hata, timeout, 401/403) **hiçbir satırı sıfırlama**; bir sonraki taramaya bırak.
5. Periyot Game'in kararıdır (örn. birkaç dakika); tazelik sınırı bu periyottur. Gerçek zamanlı tazelik garantisi yoktur; sayfalar ayrı snapshot'tır, bir oyunun ortalaması ile sayısı daima aynı snapshot'tandır.
6. Çok örnekli Game dağıtımında taramayı tek örneğin yapması gerekir (kilit/lider); sürece göre karar ver.

**Filtre/sıralama kuralları (GAME-04/05 kabulü "eksik değerlerin davranışı belgelenmiş olmalı" der):** Öneri — `minRating=x` yalnız projeksiyonda `average_rating >= x` olanları döndürür, **puansız oyunlar hariç**; `sort=RATING` puansız oyunları her iki yönde **sona** koyar (`NULLS LAST`), eşitlikte `gameId` ile stabil bağlayıcı sıralama. Karar sende; `docs/api.md` GAME kaydında açıkça yaz. `minRating` aralığını (örn. 1–10, iki ondalık) doğrula. Projeksiyon hiç taranmamışsa durumu (boş tablo ≠ "kimsenin puanı yok") ayrıca ele al; öneri: ilk tarama tamamlanana kadar `RATING_INTEGRATION_PENDING` davranışını koru.

### 3.5 Test beklentisi (Game tarafı)
Sahte Review sunucusuyla (imzalı yerel token + `HttpServer`): olumlu toplu özet; `null/0`; kesintide "alınamadı" ≠ 0; 401/403'te yeniden deneme yok; token yenileme; çok sayfalı tarama; yarıda kesilen taramada satır sıfırlanmaması; tamamlanan taramada görülmeyenin puansız olması; `minRating` / `sort=RATING` sayfalama tutarlılığı (tekrar/atlama yok), puansız konumu. Gerçek Auth token'ı ile Game↔Review entegrasyon testi Kerem'in değişikliği gelince ayrıca yazılmalı; o zamana dek "Auth ile doğrulandı" denmez.

### 3.6 Kayıt yükümlülükleri
`docs/api.md` GAME bölümünü yalnız kendi değişikliklerin için güncelle (puan alanı, `minRating`/`sort=RATING` artık 400 vermiyor, hata davranışı, puansız semantiği). `docs-security/game-service/` kalite borcunu artırma. Game belgelerindeki ("Game-Service-Bekleyen-Tasklar.md") GAME-04/05/15 satırlarını gerçek duruma göre güncelle; PASS iddiasını yalnız çalıştırdığın testlerle kur.

## 4. Review tarafında değişen dosyalar (Review sahibi bilgi için)

- Yeni: `InternalRatingSummaryController`, `RatingSummaryPageResponse`; testler `InternalRatingSummaryTest` (18).
- Değişen: `AccountErasureSecurityConfiguration` (iç yol kuralları), `ReviewRequestFilter` (iç yola 64 KiB gövde sınırı + no-store), `RatingSummaryService`/`RatingSummaryRepository` (`page`/`findPage`), `AccountErasureExceptionHandler` (403 mesajı genelleştirildi), `ReviewHttpIntegrationTest`, `RatingSummaryServiceTest`, `ProductionServiceTokenSupport`.
- Migration, ENV, yeni bağımlılık, Auth/Game kodu: **yok**.

## 5. Bilinen sınırlar

- Canlı Auth ve gerçek Game çağrısı doğrulanmadı. Secret verilmemişse veya kapsam adı/çift yanlışsa Auth `401 INVALID_CLIENT` / `403 SERVICE_AUDIENCE_FORBIDDEN` döner; 401/403'te yeniden deneme yok.
- Dışa aktarma sorgusunun planı ölçülmedi (EXPLAIN yok). Binlerce oyunluk puan tablosunda sayfa başı ≤500 grup okunur; çok büyük hacimde ölçüm Game entegrasyonuyla yapılmalı.
- Review tarafında cache yoktur; her çağrı canlı `game_ratings` verisidir. Game cache/projeksiyon tazeliğini kendisi belgelemelidir.
- Gizlenen/arşivlenen incelemelerin bağımsız puanı REV-15 kararı gelene dek ortalamaya dahildir (`REVIEW_SERVICE_DECISIONS.md`).

Ayrıntılı geçmiş ve kararlar: [game-review-integration.md](request/game-review-integration.md) · [auth-review-integration.md](request/auth-review-integration.md) · [REVIEW_SERVICE_DECISIONS.md](REVIEW_SERVICE_DECISIONS.md).
