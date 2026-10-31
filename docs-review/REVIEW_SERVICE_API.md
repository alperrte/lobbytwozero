# Review Service API sözleşmesi

Sorumlu: Hamza. Gateway öneki: `/api/v1/review`. Endpointler kullanıcının merkezi kayıt talebiyle [docs/api.md REVIEW bölümüne](../docs/api.md#review) aktarıldı (31 Ekim 2026). Bu belge faz bazlı ayrıntılı sözleşme ve doğrulama geçmişini korur; güncel kalite sonuçları [Auth entegrasyon raporunun Ek 2 bölümündedir](REVIEW_AUTH_INTEGRATION_REPORT.md).

## Ortak güvenlik ve hata sözleşmesi

İş uçları korumalıdır; `Authorization: Bearer` ile Auth RS256/JWKS `at+jwt` token'ı gerekir. `iss=JWT_ISSUER` (varsayılan `ltz-auth`), `aud` içinde `JWT_AUDIENCE` (varsayılan `ltz-api`), kanonik UUID `sub`, `kid`, ham `iat/nbf/exp` ve 30 saniye zaman toleransı doğrulanır. Auth'un PLAYER/CONTENT_CREATOR/GAME_DEVELOPER/MODERATOR/ADMIN rollerinden biri gerekir. Rol sahipliğin yerine geçmez. Yetki yalnız `roles` **listesindeki** bu beş kanonik rolden gelir; liste olmayan, bilinmeyen veya metin dışı `roles` hiçbir yetki vermez, `MODERATOR`/`ADMIN` Review'da ek yetki taşımaz. `aud` claim'i eksik imzalı token 401'dir. Silinmiş hesabın eski token'ı yazma işlemlerinde 403 alır (kota sayacı da yazılmaz). Ayrıntı: [Auth entegrasyon raporu](REVIEW_AUTH_INTEGRATION_REPORT.md). Cookie veya `X-User-Id` kimlik kaynağı değildir. Başka kullanıcı adına yazma alanı yoktur; bilinmeyen JSON alanları reddedilir.

Kullanıcı token'ı en fazla 15 dakika ömürlü olabilir (Auth JwtProperties sınırı). JSON gövdesi 64 KiB ile sınırlıdır; Content-Length olmadan gelen chunked istekler de sınırlanır. Korunan iş yanıtları `Cache-Control: private, no-store` taşır. Sayısal/boolean alanlara metin değeri verilmesi kabul edilmez.

401: kimlik yok/geçersiz; 403: rol/sahiplik yetersiz veya hesap silme engeli; 404: kaynak veya kullanılabilir oyun yok; 409: tekillik/sürüm çakışması; 400: geçersiz UUID/gövde/alan; 413: gövde sınırı aşıldı; 429: kullanıcı kotası aşıldı (`Retry-After` saniye; yalnız inceleme oluşturma ve beğenme/kaldırma, bkz. REV-13); 503: Game Service kesintisi veya bozuk sözleşme; 500: beklenmeyen hata. Hatalar `application/problem+json` (`ProblemDetail`); SQL/token/iç adresler dönmez.

## REV-01 — Puan yazma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend (Gateway üzerinden); Faz 3 toplu özet tüketicileri bu fazda uygulanmaz.
- Endpoint: `PUT /api/v1/review/games/{gameId}/rating`.
- Yetkilendirme: Korumalı; yalnız kendi puanı, `userId=sub`.
- Path/Query Parameters: `gameId` zorunlu kanonik LTZ UUID; query yok.
- Request Body: `{"rating":7}`; zorunlu 1–10 tam sayı. Ondalık ve null reddedilir.
- Response: 200; `gameId`, `rating`, `version`, `createdAt`, `updatedAt` (UTC ISO-8601).
- Hata Durumları: ortak 400/401/403/404/503/500.
- Bağlantı: `RatingController → RatingService → GameCatalogPort`, ardından transaction içinde `RatingWriter → RatingRepository`. Game çağrısı DB transaction'ı dışında, mevcut batch API ile bir kez yapılır.
- Test Durumu: UNIT + INTEGRATION — PostgreSQL/Flyway; sınırlar, aynı/farklı kullanıcı, tekrar, eşzamanlılık; gerçek imza/JWKS HTTP güvenlik doğrulaması.

Metin/tavsiye gerekmez. `(user_id,game_id)` UNIQUE ve `rating BETWEEN 1 AND 10` CHECK V51'de korunur. Aynı değer tekrarında sürüm ve zamanlar aynı kalır. Farklı değerler PostgreSQL satır kilidiyle sıralanır; son başarılı yazma geçerlidir. Bir kullanıcının tekrar isteği yeni puan satırı üretmez.

## REV-01 — Kendi puanını okuma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend.
- Endpoint: `GET /api/v1/review/games/{gameId}/rating`.
- Yetkilendirme: Korumalı; yalnız token sahibinin puanı.
- Path/Query Parameters: `gameId` zorunlu UUID; query/body yok.
- Response: 200; puan yazma yanıtıyla aynı DTO.
- Hata Durumları: 400/401/403/404/500. Kayıt yoksa 404; başka kullanıcının puanı dönmez.
- Bağlantı: Yerel Review verisi okunur; geçmiş puan Game kesintisinde de okunabilir.
- Test Durumu: INTEGRATION — kullanıcı izolasyonu, bulunamayan puan ve gerçek HTTP okuması.

## REV-02 — Metin oluşturma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend (Gateway).
- Endpoint: `POST /api/v1/review/games/{gameId}/reviews`.
- Yetkilendirme: Korumalı; yazar token `sub` alanından alınır.
- Path/Query Parameters: `gameId` zorunlu UUID; query yok.
- Request Body: `{"body":"Oyun deneyimim"}`. 1–5000 UTF-16 karakter; baş/son whitespace temizlenir. Unicode yalnız boşluk içeriği ve newline/tab dışındaki kontrol karakterleri reddedilir. HTML etkinleştirilmez; içerik düz metindir, istemci metin olarak render etmelidir.
- Response: 201 ve `Location: /api/v1/review/reviews/{id}`; `id`, `gameId`, `body`, `ownedByActor`, `version`, `createdAt`, `updatedAt`.
- Hata Durumları: 400/401/403/404/409/429/503/500. 429 için REV-13 bölümüne bakın. Aynı kullanıcı/oyun için ikinci aktif metin her zaman 409; POST tekrarında önceki metin değiştirilmez.
- Bağlantı: Game batch doğrulaması transaction dışında; ReviewWriter içinde JPA save/flush. V52 partial UNIQUE aktif kullanıcı/oyun metnini korur.
- Test Durumu: INTEGRATION — 45 testlik koşu PASS; sınırlar, bağımsız puan, kimlik, Game kesintisi ve eşzamanlı oluşturma.

## REV-02 — Tek inceleme okuma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend.
- Endpoint: `GET /api/v1/review/reviews/{reviewId}`.
- Yetkilendirme: Korumalı; görünür içerik diğer kayıtlı kullanıcılarca okunabilir. `ownedByActor` token sahibiyle hesaplanır.
- Path/Query Parameters: `reviewId` zorunlu UUID; body/query yok.
- Response: 200; oluşturma yanıtıyla aynı DTO. Ham yazar UUID'si dönmez.
- Hata Durumları: 400/401/403/404/500.
- Bağlantı: Yerel JPA okuması; Game kesintisi okumayı durdurmaz.
- Test Durumu: INTEGRATION — oluşturma, başka aktör okuması ve bulunamayan kimlik PASS.

## REV-03 — Yalnız metni düzenleme

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend.
- Endpoint: `PATCH /api/v1/review/reviews/{reviewId}`.
- Yetkilendirme: Korumalı; yalnız gerçek yazar. ADMIN/MODERATOR rolü bu uçta sahibin yerine geçmez.
- Path/Query Parameters: `reviewId` zorunlu UUID; query yok.
- Request Body: `{"version":0,"body":"Yeni deneyimim"}`. Her iki alan zorunlu; version >=0. Null/eksik metin silme anlamına gelmez; 400 döner. Puan/tavsiye ayrı uçlardan yönetilir ve PATCH bunları kabul etmez.
- Response: 200; ReviewResponse, değişiklik varsa artan sürüm ve updatedAt; createdAt korunur. Aynı metin/güncel sürüm no-op'tur. Eski sürümle tekrar 409 döner; otomatik retry yapılmaz.
- Hata Durumları: 400/401/403/404/409/503/500.
- Bağlantı: Sahiplik kontrolü, Game doğrulaması (transaction dışında), ardından yeniden sahiplik/sürüm kontrolüyle pessimistic satır kilidi ve JPA @Version. Çakışan sürüm tek yazmayı kabul eder.
- Test Durumu: INTEGRATION — sahiplik, createdAt koruma, no-op, eski sürüm 409, eşzamanlı düzenleme ve bağımsız puan/tavsiye. Gerçek HTTP PATCH akışı da sınanır.

## REV-04 — Metin silme

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend.
- Endpoint: `DELETE /api/v1/review/reviews/{reviewId}?version=0`.
- Yetkilendirme: Korumalı; yalnız gerçek yazar. Yönetici/moderasyon yetkisi bu uca eklenmez.
- Path/Query Parameters: reviewId UUID; version zorunlu tam sayı >=0.
- Request Body: Yok.
- Response: 204; içerik NULL, status=DELETED ve deletedAt atanır. Puan ve bağımsız tavsiye korunur. Aynı yazarın silinmiş kayda tekrarı sürümden bağımsız 204'tür; version parametresi yine geçerli olmalıdır.
- Hata Durumları: 400/401/403/404/409/500. Aktif kayıtta eski sürüm 409; silinmiş kayıt GET/PATCH ile 404. Eksik kimlik 404. Başkasının silinmiş kaydı da 403.
- Bağlantı: ReviewWriter satır kilidi/sürüm kontrolüyle yerel transaction. Game kesintisi silmeyi engellemez. Yeniden POST yeni kimlik oluşturur; önceki kayıt geri getirilmez.
- Test Durumu: INTEGRATION — sahiplik, eski/geçersiz sürüm, eşzamanlı tekrar, metin temizleme, tekrar oluşturma, Game kesintisi ve puan/tavsiye koruma; gerçek HTTP DELETE akışı.

## REV-05 — Tavsiye yazma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend (Gateway).
- Endpoint: `PUT /api/v1/review/games/{gameId}/recommendation`.
- Yetkilendirme: Korumalı; yalnız token sahibinin tavsiyesi.
- Path/Query Parameters: gameId zorunlu UUID; query yok.
- Request Body: `{"recommended":false}`; gerçek JSON boolean zorunlu. Null, sayı/metin, userId/body/rating alanları reddedilir.
- Response: 200; `gameId`, `recommended`, `version`, `createdAt`, `updatedAt`.
- Hata Durumları: 400/401/403/404/413/503/500.
- Bağlantı: Game batch doğrulaması transaction dışında, sonra RecommendationWriter/Repository PostgreSQL UPSERT. V53 kullanıcı/oyun tekilliği. Aynı değer tekrarı sürüm/zaman değiştirmez; farklı değerlerde son başarılı DB yazması geçerlidir.
- Test Durumu: INTEGRATION — metin/puan olmadan yazma; true/false geçişi, tekrar, kullanıcı izolasyonu, kesinti ve eşzamanlı tekillik. Gerçek HTTP akışında da sınanır.

## REV-05 — Kendi tavsiyesini okuma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend.
- Endpoint: `GET /api/v1/review/games/{gameId}/recommendation`.
- Yetkilendirme: Korumalı; yalnız token sahibi.
- Path/Query Parameters: gameId zorunlu UUID; query/body yok.
- Response: 200; tavsiye yazma yanıtıyla aynı DTO. Kayıt bulunmaması false anlamına gelmez, 404 döner.
- Hata Durumları: 400/401/403/404/500.
- Bağlantı: Yalnız Review verisi; sayısal puandan tavsiye türetilmez.
- Test Durumu: INTEGRATION — eksik kayıt, başka kullanıcı, false değerini ayırma, metin silindikten sonra koruma ve gerçek HTTP.

## Hesap silme entegrasyonu

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: auth-service.
- Endpoint: `DELETE /internal/v1/accounts/{accountId}`.
- Yetkilendirme: Yalnız aşağıda tanımlı D4 servis kimliği; kullanıcı rolleri yetki vermez.
- Path/Query Parameters: accountId zorunlu UUID, token account_id ile tam eşleşme; query yok.
- Request Body: Yok.
- Response: 204; hedef veri yoksa veya tekrar çağrıda da 204. Başka hesaba ait veri korunur.
- Hata Durumları: 400 geçersiz UUID; 401 kimlik/imza/tür/issuer/audience/süre/claim eksikliği; 403 scope/çağıran/account_id uyuşmazlığı; 500 beklenmeyen sunucu hatası, silme onayı sayılmaz.
- Bağlantı: Auth mevcut REVIEW_SERVICE_URL ile doğrudan çağırır; Gateway route'u eklenmez. AccountErasureService, AccountWriteLock ve V54 aynı yerel transaction'ı kullanır.

Mevcut `DELETE /internal/v1/accounts/{accountId}` yolu ve D4 güvenliği korunur. Auth servis token'ı (`typ=svc+jwt`, `sub=svc:auth-service`, tek `aud=review-service`, `scope=account:erase`, eşleşen `account_id`) gerekir. 204 idempotenttir; hedef hesaba ait puan, metin ve tavsiyeler aynı transaction'da silinir. Kullanıcı token'ı bu iç uçta kabul edilmez.

Hesap bazlı PostgreSQL transaction advisory lock tüm mutasyonlarla ortaktır. Aynı transaction'da V54 tablosuna yalnız hesap UUID'sinin SHA-256 özeti ve sona erme zamanı yazılır. Sonraki 17 dakika tüm kullanıcı yazmaları 403 alır; aktif engel her kullanıcı yazmasında transaction içinde yeniden kontrol edilir. Silme tekrarı süreyi uzatabilir. Süresi dolan özetler dakikalık job ile kaldırılır (çalışan serviste yaklaşık 17–18 dakika; servis kapalıysa sonraki çalışan temizliğe kadar). Ayrıntılı TTL kanıtı karar belgesindedir.

Test Durumu: Gerçek PostgreSQL silme/yazma yarışı, süre sonu temizliği ve gerçek TCP üzerinden imzalı D4 silme + eski kullanıcı token'ıyla yeniden yazmanın 403 alması PASS. JWKS/Game bu testlerde yerel sözleşme sunucularıdır; gerçek Auth/Game/Gateway ile canlı E2E **NOT VERIFIED**.

## REV-06 — İnceleme beğenme

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend, Gateway üzerinden.
- Endpoint: `PUT /api/v1/review/reviews/{reviewId}/like`.
- Yetkilendirme: Ortak korumalı kullanıcı JWT/rol sözleşmesi; aktör yalnız doğrulanmış sub. Kendi incelemesini beğenme 403. Servis token'ı kabul edilmez.
- Path/Query Parameters: reviewId zorunlu UUID; query yok.
- Request Body: Yok. Boş/whitespace dışında gövde 400; userId/rol/oy sahibi seçilemez.
- Response: 200 `ReviewLikeResponse`: `reviewId`, `likeCount` (long), `likedByActor` (boolean). Tekrar PUT aynı oy/sayıyı korur. Beğeni yazarın inceleme sürümünü, metnini, puanını veya tavsiyesini değiştirmez.
- Hata Durumları: 400 geçersiz UUID/gövde; 401 kimlik yok/geçersiz; 403 rol, self-vote veya silinen hesap engeli; 404 olmayan/silinmiş inceleme; 429 kullanıcı kotası aşıldı (REV-13); 500 beklenmeyen hata. ProblemDetail ortak sözleşmesi geçerli.
- Bağlantı: ReviewLikeService → hesap silme guard'ı → inceleme satır kilidi → ReviewLikeRepository atomik INSERT ON CONFLICT DO NOTHING → gerçek DB satırlarından count/aktör durumu. V55 PK `(review_id,user_id)` ve review FK. Dış servis çağrısı yok.
- Test Durumu: UNIT/INTEGRATION — 75 testlik strict koşuda PASS; PostgreSQL tekillik/yarışlar ve gerçek TCP üzerinden RS256/JWKS kullanıcı token'ı ile beğeni/tekrar/self-vote/servis kimliği reddi.

## REV-06 — Beğeniyi kaldırma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend.
- Endpoint: `DELETE /api/v1/review/reviews/{reviewId}/like`.
- Yetkilendirme: Korumalı kullanıcı JWT; yalnız aktörün oyunu kaldırır. İnceleme sahibi olmak başkalarının oyunu silme yetkisi vermez.
- Path/Query Parameters: reviewId zorunlu UUID; query yok.
- Request Body: Yok; dolu gövde 400.
- Response: 200 `ReviewLikeResponse`. Zaten oy yoksa aynı sıfır/aktör-false sonucu; başka kullanıcıların oyları korunur. Kendi incelemesine oy veremeyen yazarın DELETE'i no-op olabilir.
- Hata Durumları: 400/401/403/404/429/500. 429 REV-13 kotasıdır (PUT ile ortak). Olmayan veya DELETED hedefte 404; silinmiş incelemenin beğenileri metin silme transaction'ında zaten temizlenir.
- Bağlantı: Aynı hesap guard'ı ve parent review kilidi; yalnız `(reviewId,sub)` satırı silinir. Fiziksel hesap silmesinde verilen oylar ve silinen incelemelerin oyları temizlenir. Karşılıklı oyları olan iki hesabın silmesi kısa erasure advisory lock ile sıralanır.
- Test Durumu: INTEGRATION — tekrar DELETE, kullanıcı izolasyonu, author no-op, içerik silmesi, hesap silmesi/tekrar engeli, karşılıklı hesap silme ve beğeni/silme yarışı PASS; gerçek HTTP DELETE akışı PASS.

## REV-06 — Beğeni durumunu okuma

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend.
- Endpoint: `GET /api/v1/review/reviews/{reviewId}/like`.
- Yetkilendirme: Korumalı kullanıcı JWT/rol. Görünür inceleme için toplam ve yalnız token sahibinin oy durumu; oy veren kullanıcı kimlikleri döndürülmez.
- Path/Query Parameters: reviewId zorunlu UUID; query/body yok.
- Response: 200 `ReviewLikeResponse`. Oy yoksa `likeCount=0`, `likedByActor=false`. `Cache-Control: private, no-store`.
- Hata Durumları: 400/401/403/404/500. Silinen hedefin varlığı veya oy listesi açılmaz.
- Bağlantı: REPEATABLE_READ salt okunur transaction ile görünürlük ve count/aktör sorgusu aynı snapshot'ta; N+1 HTTP çağrısı yok.
- Test Durumu: INTEGRATION — farklı aktör görünümü, toplam, sıfır, anonim/silinmiş hedef ve gerçek JWT/HTTP PASS.

REV-07 rapor API'si, outbox/relay ve REV-10 dış teslimi uygulanmadı. Önerilen hedef sözleşmeler [request/](request/) altında; mevcut endpoint olarak kullanılamaz.

## REV-13 — Kullanıcı kotası ve 429 sözleşmesi

- Durum: IMPLEMENTED (yalnız bağımsız kısım). Rapor oluşturma kotası ve mükerrer rapor engeli REV-07'ye bağlıdır: NOT IMPLEMENTED, ENTEGRASYON BEKLİYOR.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend (Gateway üzerinden); yeni endpoint yoktur.
- Kapsam (yeni endpoint değil, mevcut üç işleme uygulanan kota):
  - `POST /api/v1/review/games/{gameId}/reviews` → kullanıcı başına **60 saniyede 5** deneme (`REVIEW_CREATE`).
  - `PUT` ve `DELETE /api/v1/review/reviews/{reviewId}/like` → ortak kota, kullanıcı başına **60 saniyede toplam 60** deneme (`REVIEW_LIKE`).
  - Diğer uçlar (puan/tavsiye PUT, inceleme PATCH/DELETE, tüm GET'ler) sınırsızdır; bunlar için sayısal politika onaylanmadı.
- Yetkilendirme: Kota yalnız kimliği doğrulanmış ve rolü uygun kullanıcı isteğinde tüketilir. Anonim/geçersiz token 401, rol/servis token'ı 403/401 mevcut davranışını korur ve sayaç yazmaz. Sayaç anahtarı token `sub` alanıdır; istemci seçemez.
- Sayım kuralı: Kimlik doğrulama geçildikten sonra ilgili rotaya gelen her istek bir denemedir; sonucundan bağımsızdır: 201/200, tekrar (409), kendi incelemesini beğenme (403), olmayan hedef (404), gövde/alan doğrulama hatası (400), Game kesintisi (503). Kota iş transaction'ından önce kendi transaction'ında kesinleşir; iş transaction'ı geri alınsa da deneme geri verilmez. 413 (gövde sınırı) güvenlik/filtre katmanında kaldığından sayılmaz.
- Pencere: Epoch'a hizalı sabit pencere (örn. 12:00:00–12:01:00 UTC). Sayaç `limit+1`'de durur. Pencere sınırında art arda iki pencerede toplam `2×limit` işleme izin verilebilir; bu sabit pencere algoritmasının bilinen sınırıdır.
- Response (aşımda): 429, `application/problem+json` (`ProblemDetail`, `status=429`), `Retry-After: <saniye>` (pencere bitimine kalan süre, yukarı yuvarlanmış, en az 1) ve `Cache-Control: private, no-store`. İş mantığı çalıştırılmaz; kayıt oluşturulmaz/değiştirilmez.
- Hata Durumları: 429 yukarıdaki gibi; kota deposu (PostgreSQL) hatasında istek güvenli tarafta 500 ile reddedilir.
- Bağlantı: `AbuseQuotaInterceptor` (Spring MVC, yalnız eşleşen rota ve metotlarda) → `AbuseQuotaService` → `AbuseQuotaRepository` (REQUIRES_NEW, tek atomik `INSERT … ON CONFLICT DO UPDATE … RETURNING`). Tablo `review_rate_limits` (V57): SHA-256 hesap özeti, eylem, pencere başlangıcı/bitişi, sayaç; ham kullanıcı kimliği yoktur. Süresi dolan satırlar (bitişten 5 dakika sonra) dakikada bir temizlenir. Sınırlar `ltz.review.abuse.*` ayarlarıdır. Dış servis çağrısı yoktur.
- Test Durumu: UNIT + INTEGRATION — PostgreSQL 17 üzerinde kontrollü saatle: 5/60 ve 60/60 sınırları, `Retry-After`, pencere yenilenmesi, tekrar/geçersiz/self-like/404/503 sayımı, eşzamanlı 16 inceleme ve 75 beğeni isteğinde kesin sayım, ortak beğeni/kaldırma kotası, GET'lerin sınırsızlığı, 401/403'ün sayaç yazmaması, temizlik ve tablo kısıtları; gerçek TCP/RS256/JWKS ile 429 + `Retry-After`. Ayrıntı: [Faz 4 raporu](REVIEW_SERVICE_PHASE_04_REPORT.md).

## REV-09 — Öne çıkan incelemeler

- Durum: IMPLEMENTED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend, mevcut Gateway yolu üzerinden; bu görevde frontend/Gateway değiştirilmedi.
- Endpoint: `GET /api/v1/review/games/{gameId}/reviews/featured`.
- Yetkilendirme: Mevcut korumalı kullanıcı JWT/rol sözleşmesi; PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR veya ADMIN. Aktör doğrulanmış `sub`; servis token'ı kabul edilmez. Anonim endpoint değildir.
- Path/Query Parameters: `gameId` zorunlu kanonik UUID; `page` tamsayı 0–10000, varsayılan 0; `size` tamsayı 1–50, varsayılan 20; `minLikes` tamsayı 0–2147483647, varsayılan 0. Oyun ve minimum beğeni filtreleri birlikte uygulanır. Görünürlük yalnız sunucunun `VISIBLE` koşuludur; istemci silinmiş kayıtları açamaz. Serbest sıralama/rapor/moderasyon filtresi sunulmaz.
- Request Body: Yok.
- Response: 200 `FeaturedReviewsResponse`: `items` (`FeaturedReviewResponse[]`), `page`, `size`, `totalElements` (long), `totalPages` (long). Her öğe `review` (mevcut ReviewResponse: id/gameId/body/ownedByActor/version/createdAt/updatedAt) ve `likeCount` (long) içerir. Yazar ve oy veren kullanıcı UUID'leri döndürülmez. `ownedByActor` token sahibine göre hesaplanır.
- Sıralama: `likeCount DESC, createdAt DESC, id ASC`; aynı snapshot/veride deterministiktir. Sıfır beğenili görünür kayıtlar varsayılan filtreye dahildir. İnceleme düzenlenmesi oluşturulma tarihini değiştirmez, sıralamada updatedAt kullanılmaz. Rapor/şikâyet cezası veya moderasyon skoru yoktur.
- Boş durum: İnceleme olmayan/Review'de bilinmeyen oyun UUID'si 200, `items=[]`, `totalElements=0`, `totalPages=0`. Game Service çağrısı yapılmaz; bu yanıt oyunun katalogda varlığını ya da güncel erişilebilirliğini doğrulamaz. Son sayfa dışına taşan geçerli sayfa 200/boş items döndürür; toplamlar filtrelenen kümenin toplamıdır.
- Hata Durumları: 400 geçersiz UUID/tamsayı/aralık (ProblemDetail); 401 eksik/geçersiz kullanıcı kimliği veya servis token'ı; 403 mevcut rol politikası; 500 beklenmeyen sunucu hatası. `Cache-Control: private, no-store`.
- Bağlantı: FeaturedReviewController → FeaturedReviewService → FeaturedReviewRepository. Parametreli PostgreSQL toplulaştırması; gerçek review_likes satırlarından sayım, sıfır beğeniler için LEFT JOIN. İki set sorgusu (toplam + sayfa) tek REPEATABLE_READ salt okunur, 4 saniye timeout'lu transaction içinde çalışır. N+1 sorgu/HTTP yok. V56 görünür oyun/tarih/UUID kısmi indeksi; V55 review_id önekli PK beğeni JOIN'ini destekler. Beğeniye göre son sıralama hesaplanır; indeksin bütün sıralamayı kaldırdığı iddia edilmez.
- Sayfalama sınırı: Snapshot tek istek içindir. Ayrı sayfa istekleri arasında beğeni, ekleme veya silme değişirse sonuçlar yer değiştirebilir; istekler arası sabit snapshot/cursor garantisi verilmez.
- Test Durumu: UNIT/INTEGRATION — Review modülünün 85/85 testlik Maven test koşusunda PASS. Yeni 7 PostgreSQL/MockMvc testi + 2 servis birim testi + 1 gerçek TCP/RS256/JWKS testi. Canlı Auth/Game/Gateway E2E iddiası yoktur. Pre-push/backendCheck/Trivy/Sonar: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**.

## REV-08 — Tek oyun LTZ puan özeti

- Durum: IMPLEMENTED — Review tarafı.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend; Game sunucu entegrasyonu ayrıca sözleşme bekliyor.
- Endpoint: `GET /api/v1/review/games/{gameId}/rating-summary`.
- Yetkilendirme: Mevcut kullanıcı JWT/rol politikası. Anonim ve servis token erişimi yoktur. Kullanıcı JWT'si servis kimliği yerine kullanılamaz.
- Path/Query Parameters: gameId zorunlu kanonik LTZ UUID; query yok.
- Request Body: Yok.
- Response: 200 `{gameId, averageRating, ratingCount}`. averageRating PostgreSQL numeric/Java BigDecimal, iki ondalık basamak, pozitif puanlarda HALF_UP; ratingCount long ve benzersiz puan veren kullanıcı sayısıdır. Puan yoksa averageRating=null/ratingCount=0; 404 değildir. 0 puan uydurulmaz. JSON sayısı tüketicide sondaki sıfırları korumayabilir.
- Hata Durumları: 400 geçersiz UUID (ProblemDetail), 401 kimlik yok/geçersiz/servis token'ı, 403 izinli kullanıcı rolü yok, 500 beklenmeyen hata.
- Bağlantı: RatingSummaryController → RatingSummaryService → RatingSummaryRepository; tek parametreli AVG/count SQL sorgusu. V51 kullanıcı/oyun tekilliği nedeniyle count(*) benzersiz kullanıcı sayısıdır. Yalnız game_ratings kullanılır; inceleme, beğeni, tavsiye ve Steam oranları katılmaz. Mevcut game_id indeksi yeterlidir; migration eklenmedi. Salt okunur transaction, 4 saniye timeout, tek SQL snapshot; dış servis çağrısı yok.
- Silme/boş sonuç: Metin silmek bağımsız puanı etkilemez; hesap silme puanını aynı mevcut transaction içinde kaldırır. Bilinmeyen/puan verilmeyen oyun aynı boş özeti alır; bu Game katalog görünürlüğü/varlık doğrulaması değildir. Cache-Control private,no-store.
- Test Durumu: INTEGRATION — 6 yeni PostgreSQL/MockMvc ve 1 gerçek TCP/RS256/JWKS testi; REV-08 kapanışında 92/92 Review testi PASS. Pre-push/backendCheck/Trivy/Sonar: KULLANICI TALEBİYLE ÇALIŞTIRILMADI.

## REV-12 — Toplu LTZ oyun puanı özetleri

- Durum: IMPLEMENTED — Review backend kabulü; Game→Review sunucu entegrasyonu SÖZLEŞME BEKLENİYOR / NOT IMPLEMENTED / NOT VERIFIED.
- Sorumlu: Hamza.
- Tüketici servisler: Frontend; Game hedef tüketicidir, servis kimliği henüz yetkilendirilmedi. Kullanıcı JWT'si servis kimliği yerine taşınmaz.
- Endpoint: `POST /api/v1/review/games/rating-summaries`.
- Yetkilendirme: Mevcut kullanıcı at+jwt ve PLAYER/CONTENT_CREATOR/GAME_DEVELOPER/MODERATOR/ADMIN rol politikası. Anonim veya svc+jwt erişimi açılmadı. Mevcut CSRF/Bearer ayrımı ve Cache-Control private,no-store korunur.
- Path/Query Parameters: Yok.
- Request Body: application/json `{gameIds: UUID[]}`; gameIds zorunlu, null olamaz; ham dizi en fazla 50 eleman, her eleman null olmayan UUID. Sınır tekilleştirmeden önce uygulanır. Boş dizi geçerli; tekrarlar ilk görülme sırasıyla tekilleştirilir. Bilinmeyen alanlar/tür dönüşümü reddedilir. Mevcut 64 KiB gövde sınırı geçerlidir.
- Response: 200 `{items:[{gameId,averageRating,ratingCount}]}`; yalnız istenen benzersiz oyunlar, ilk görülme sırasıyla. Puan verilmemiş/bilinmeyen UUID için averageRating=null/ratingCount=0; boş dizi items=[]. Ortalama REV-08 ile aynı iki basamak HALF_UP BigDecimal/numeric hesabıdır. ratingCount benzersiz kullanıcı sayısı; histogram/tavsiye oranı/inceleme adedi/Steam oranı eklenmedi.
- Hata Durumları: 400 eksik/null/51+ dizi, null/geçersiz UUID, yanlış JSON türü/bilinmeyen alan/bozuk gövde (ProblemDetail); 401 kimlik eksik/geçersiz veya servis token'ı; 403 izinli kullanıcı rolü yok; 413 gövde limiti; 415 desteklenmeyen içerik türü; 500 beklenmeyen hata. Sonuç 404 veya kısmi hata listesi değildir; katalog varlığı doğrulanmaz.
- Bağlantı: RatingSummaryController → RatingSummaryService → RatingSummaryRepository; tek parametreli IN/GROUP BY AVG/count SQL'i, game_ratings dışına JOIN/HTTP yok. En fazla 50 anahtar bellekte sıralanıp eksik oyunlar null/0 tamamlanır; oyun başına SQL yoktur. Boş listede SQL yok. Tek salt okunur transaction/4 saniye; tek statement snapshot'ı bütün gruplar ve ortalama/count için tutarlıdır. Aynı sorgu tekil özette de kullanılır. Yeni tablo/migration/bağımlılık/kalıcı sayaç eklenmedi.
- Test Durumu: UNIT/INTEGRATION — 102/102 Review testi PASS. 50 oyun/10.000 puanda JdbcClient spy ile tam 1 SQL ve sıfır Game çağrısı doğrulandı; boş/geçersiz listede sıfır SQL. Eşzamanlı okuma/yazma ve gerçek TCP/RS256/JWKS olumlu/olumsuz testleri PASS. Canlı Game/Auth servis tüketimi NOT VERIFIED. Pre-push/backendCheck/Trivy/Sonar: KULLANICI TALEBİYLE ÇALIŞTIRILMADI.
- Bekleyen sözleşme: [Game talebi](request/game-review-integration.md), [Auth talebi](request/auth-review-integration.md). Servis okuma yolu ve dar izin adı yalnız öneridir; mevcut yetki olarak kullanılamaz.

## REV-14 — Oyun inceleme ve puan istatistikleri

- Durum: IMPLEMENTED — Review backend kabulü. Endpoint adı (`/statistics`) ve DTO bu görevde mevcut `rating-summary` adlandırmasına uygun seçilmiş yeni sözleşmedir; Frontend/Game tüketimi henüz yoktur (NOT VERIFIED).
- Sorumlu: Hamza.
- Tüketici servisler: Frontend (Gateway). Game→Review servis erişimi tanımlı değildir; bu uç svc+jwt kabul etmez.
- Endpoint: `GET /api/v1/review/games/{gameId}/statistics`.
- Yetkilendirme: Mevcut kullanıcı at+jwt ve PLAYER/CONTENT_CREATOR/GAME_DEVELOPER/MODERATOR/ADMIN rolleri. Anonim erişim ve servis token'ı yoktur. Kimlikler yanıtta dönmez.
- Path/Query Parameters: `gameId` zorunlu kanonik UUID; query/body yok.
- Response: 200.
  ```json
  {
    "gameId": "…uuid…",
    "ratings": {
      "ratingCount": 6,
      "averageRating": 5.50,
      "distribution": [{"rating": 1, "userCount": 2}, …, {"rating": 10, "userCount": 1}]
    },
    "reviews": {"activeCount": 2},
    "recommendations": {
      "recommendedCount": 3, "notRecommendedCount": 1, "totalCount": 4,
      "recommendedPercentage": 75.00, "notRecommendedPercentage": 25.00
    }
  }
  ```
  - `ratings`: yalnız sayısal puanlar. `ratingCount` puan veren **benzersiz kullanıcı** sayısıdır (kullanıcı/oyun başına tek puan); REV-08/REV-12 ile aynıdır. `distribution` her zaman 1–10 arası 10 kova içerir, boş kovalar `userCount=0` ile açıkça yazılır; kovaların toplamı `ratingCount`'a eşittir. `averageRating` REV-08/REV-12 ile aynı `round(avg(rating),2)` ifadesidir (iki ondalık, HALF_UP), puan yoksa `null`.
  - `reviews.activeCount`: yalnız `VISIBLE` metin incelemeleri. Silinmişler sayılmaz; puan veren veya tavsiye veren kullanıcı sayısıyla ilgisi yoktur (puan/tavsiye verip metin yazmayan kullanıcı bu sayıya girmez).
  - `recommendations`: yalnız `game_recommendations`. `totalCount = recommendedCount + notRecommendedCount`. Yüzde = `100 × sayı / totalCount`, iki ondalık, HALF_UP, **0–100 ölçeği**; payda `totalCount`'tur (tavsiye vermeyen veya yalnız puan veren kullanıcılar paydaya girmez). `totalCount=0` ise iki yüzde de `null` (0 değil). Her yüzde bağımsız yuvarlandığından tam eşitlik durumlarında toplam 100,01 veya 99,99 olabilir (örn. 1/800 → 0,13 ve 99,88).
  - Boş oyun (bilinmeyen UUID dahil): tüm sayılar 0, `averageRating=null`, 10 sıfırlı kova, yüzdeler `null`; HTTP 200, Game kataloğu çağrılmaz ve oyun varlığı doğrulanmaz (REV-08 ile aynı politika).
- Hata Durumları: 400 geçersiz UUID; 401 kimlik yok/geçersiz veya servis token'ı; 403 izinli rol yok; 405 desteklenmeyen metot; 500 beklenmeyen hata. `Cache-Control: private, no-store`.
- Bağlantı: `ReviewStatisticsController → ReviewStatisticsService → ReviewStatisticsRepository`. Tek salt okunur transaction/4 saniye ve **tek SQL ifadesi**: bağımsız skaler alt sorgular tek snapshot'tan okur; dağılım `generate_series(1,10)` ile tamamlanır. Oyun başına/kova başına ayrı sorgu (N+1) yoktur; yeni tablo, kalıcı sayaç, materialized view veya migration eklenmedi (V57 yalnız REV-13 kotasıdır). Mevcut `ix_game_ratings_game_id` ve `ix_game_recommendations_game_id` kullanılır (disposable PostgreSQL'de 10.000 puan + 4.000 tavsiye + 2.000 inceleme için ~3,8 ms, sıcak yerel ölçüm; üretim SLA'sı değildir). Dış servis çağrısı yoktur.
- Davranış: Puan PUT güncellemesi kullanıcının kovasını taşır, sayıyı artırmaz; aynı değerin tekrarı değişiklik üretmez. Tavsiye değişikliği iki sayıyı tutarlı kaydırır. Metin silme yalnız `activeCount`'u düşürür; puan/tavsiye korunur. Hesap silme kullanıcının puan, tavsiye ve metnini aynı mevcut transaction'da kaldırdığından sonraki okuma doğru değerleri verir. Gizlenmiş/arşivlenmiş durumlar şemada yoktur; REV-15 politikası kararlaştırılana kadar `activeCount` yalnız `VISIBLE` sayar.
- Test Durumu: UNIT/INTEGRATION — PostgreSQL 17: boş sonuç, dağılım/kova toplamı, inceleme–puan–tavsiye ayrımı, REV-08 ortalamasıyla eşitlik, yüzde yuvarlama (33,33/66,67 ve 0,13/99,88), puan/tavsiye güncellemesi, metin silme bağımsızlığı, hesap silme, 10.000 puan/tek SQL (JdbcClient spy), eşzamanlı yazıcılarla iç tutarlılık, 401/403/400/405; gerçek TCP/RS256/JWKS ile kullanıcı token'ı 200, svc+jwt ve anonim 401. Pre-push/backendCheck/Trivy/Sonar: KULLANICI TALEBİYLE ÇALIŞTIRILMADI.
