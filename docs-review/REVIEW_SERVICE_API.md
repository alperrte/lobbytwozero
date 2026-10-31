# Review Service API sözleşmesi

Sorumlu: Hamza. Gateway öneki: `/api/v1/review`. Merkezi kataloğa aktarım, kullanıcının yalnız `docs-review/` değişikliği sınırı nedeniyle bu görevde yapılmaz.

## Ortak güvenlik ve hata sözleşmesi

İş uçları korumalıdır; `Authorization: Bearer` ile Auth RS256/JWKS `at+jwt` token'ı gerekir. `iss=JWT_ISSUER` (varsayılan `ltz-auth`), `aud` içinde `JWT_AUDIENCE` (varsayılan `ltz-api`), kanonik UUID `sub`, `kid`, ham `iat/nbf/exp` ve 30 saniye zaman toleransı doğrulanır. Auth'un PLAYER/CONTENT_CREATOR/GAME_DEVELOPER/MODERATOR/ADMIN rollerinden biri gerekir. Rol sahipliğin yerine geçmez. Cookie veya `X-User-Id` kimlik kaynağı değildir. Başka kullanıcı adına yazma alanı yoktur; bilinmeyen JSON alanları reddedilir.

Kullanıcı token'ı en fazla 15 dakika ömürlü olabilir (Auth JwtProperties sınırı). JSON gövdesi 64 KiB ile sınırlıdır; Content-Length olmadan gelen chunked istekler de sınırlanır. Korunan iş yanıtları `Cache-Control: private, no-store` taşır. Sayısal/boolean alanlara metin değeri verilmesi kabul edilmez.

401: kimlik yok/geçersiz; 403: rol/sahiplik yetersiz veya hesap silme engeli; 404: kaynak veya kullanılabilir oyun yok; 409: tekillik/sürüm çakışması; 400: geçersiz UUID/gövde/alan; 413: gövde sınırı aşıldı; 503: Game Service kesintisi veya bozuk sözleşme; 500: beklenmeyen hata. Hatalar `application/problem+json` (`ProblemDetail`); SQL/token/iç adresler dönmez.

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
- Hata Durumları: 400/401/403/404/409/503/500. Aynı kullanıcı/oyun için ikinci aktif metin her zaman 409; POST tekrarında önceki metin değiştirilmez.
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
