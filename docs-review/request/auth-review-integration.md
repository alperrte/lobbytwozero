# AUTH–REVIEW entegrasyonu

Son güncelleme: 12 Kasım 2026. Bu belge yalnız **açık işleri ve beklentileri** içerir; tamamlanmış entegrasyonlar (kullanıcı JWT, roller, hesap silme, Game→Review puan okuma) bilerek yazılmamıştır. Kesin geliştirme emri değildir.

- Talep eden: `review-service` — Hamza. Hedef: `auth-service` — Kerem. Nihai yetki/istemci tasarımı Auth sahibince belirlenir.
- Auth kodu, izin listesi ve belgeleri değiştirilmedi. Kaynak: Auth `application.properties` (12 Kasım 2026, salt okunur), [Auth servis sözleşmeleri](../../docs-auth/servis-sozlesmeleri.md).

## 1. Açık çift/kapsam tablosu

| Çift | Yapılandırmadaki kapsam | Durum |
| --- | --- | --- |
| `game-service → review-service` | `game-references:read`, `game-identity:prepare`, `game-identity:finalize`, `game-identity:read` | Review alıcısı **yok** (GAME-26, [Game belgesi](game-review-integration.md) §3) |
| `review-service → game-service` | `game-identity:decision:read` | GAME-26 karar sorgusu için; Review istemcisi **yok** |
| `review-service → user-service` | `events:ingest` | Review üreticisi uygulandı ama `AUTH_REVIEW_SERVICE_CLIENT_SECRET` boşken kapalı; canlı doğrulama ve secret dağıtımı (ENV onayı) bekliyor |
| `review-service → admin-service` | **tanımsız** | REV-07 bekliyor |
| `admin-service → review-service` | **tanımsız** (Admin istemcisinin tek hedefi `community-service`) | REV-15 bekliyor |
| `review-service → notification-service` | **tanımsız** | Yalnız üretici Review seçilirse |

## 2. Açık işler ve Auth'tan beklenenler

1. **Çelişki — GAME-26 kapsamları [Auth/Kerem doğrulasın]:** `application.properties` `game-identity:*` kapsamlarını tanımlıyor; `docs/api.md` bunları "taslak, Auth'a uygulanmış değil" diye anıyor. Hangisinin güncel olduğu doğrulanmalı. Review bu kapsamlara bağımlı kod yazmadan önce teyit bekler; **yeni kapsam talep edilmiyor**.
2. **Secret dağıtımı [Auth + kullanıcı onayı]:** Review→User ve Review→Game çiftlerinin kullanılabilmesi için Review ortamında `AUTH_REVIEW_SERVICE_CLIENT_SECRET` gerekir. Bu bir **ENV değişikliğidir, kullanıcı onayı** gerektirir; Auth güvenli dağıtım/rotasyon yolunu belirtir. Gerçek değer belgeye/komuta yazılmaz.
3. **Review → Admin (rapor teslimi) [Auth + Admin]:** Alıcı protokolü onaylanınca en dar çift (öneri: `events:ingest`). Admin alıcısı olmadığı için bekliyor.
4. **Admin → Review (moderasyon kararı) [Auth + Admin]:** Çift ve kapsam yok. `events:ingest` bir olay alma kapsamıdır; moderasyon **komutu** için ayrı kapsam gerekip gerekmediği (veya `events:ingest` + Review'daki tam producer/olay-türü allowlist'inin yeterli olup olmadığı) **KARAR BEKLİYOR**.
5. **Review → Notification (koşullu) [Auth + Notification]:** Yalnız bildirim üreticisi Review seçilirse; Admin seçilirse Review izni gerekmez ([Notification belgesi](notification-review-integration.md)).
6. **Canlı uçtan uca [Tümü]:** Gerçek Auth token'ıyla canlı Auth↔Review ve tüm onaylı çiftlerde olumlu/olumsuz HTTP testleri henüz yapılmadı (NOT VERIFIED). Mock token üretimi teslim kabulü sayılmaz.
7. **İnsan moderatör yetkisinin Review'a aktarımı [Admin + Auth + Review] — KARAR BEKLİYOR:** Servis token'ı yalnız *hangi servisin* konuştuğunu kanıtlar. Kullanıcı `at+jwt`'si (`MODERATOR`/`ADMIN` dahil) Review'da moderasyon kararı yerine geçmez; Auth servis token'ından kullanıcı yetkisi türetmez ve delegasyon token'ı üretmez (servis-sözleşmeleri §3.5). Admin'in insanı doğrulayıp imzalı servis çağrısıyla audit aktör referansını taşıması mı, başka bir model mi kararlaştırılmalı; gövdedeki `actorId` tek başına kabul edilmez.
8. **Kapsam ayrımı korunmalı:** `rating-summaries:read` ile `game-identity:*` Review'da ayrı yetkilerdir; biri diğerine yetki vermez.

REV-13 (kota, tekillik, sayaç) Review içinde çözülür; Auth'tan yeni izin istemez.

## 3. Önerilen sözleşme (açık çiftler için — kesinleşmedi)

Mevcut token ucu (`POST /internal/v1/service-tokens`) kullanılır; yeni token endpoint'i istenmiyor. Kavramsal istek: `clientId=review-service`, bellekte taşınan secret, `audience=<hedef>`. En dar çiftler: review→admin `events:ingest` (olay modeli seçilirse), admin→review `events:ingest` (geri bildirim olay modeli seçilirse; Review ayrıca tam producer/olay türü allowlist'i uygular), review→notification `events:ingest` (yalnız üretici Review ise). Bunlar bugün yapılandırılmış izin değildir. Senkron komut/durum sorgusu seçilirse farklı kapsam gerekip gerekmediğine Auth ve hedef sahipleri karar verir.

## 4. Kimlik doğrulama ve hata beklentileri (açık çiftler için)

- Mevcut D3 modeli: RS256/JWKS, `typ=svc+jwt`, geçerli `kid`, issuer, tek ve doğru hedef `aud`, tam `sub=svc:<çağıran>`, scope, ham `iat/nbf/exp/jti`, ≤60 sn ömür, sözleşmedeki saat toleransı. Alıcı token `sub` ile zarf producer eşleşmesini ayrıca denetler. Kullanıcı token'ı, başka audience/servis kimliği ve D4 `account:erase` kabul edilmez.
- Secret boş/geçersiz, token üretimi başarısız, hedef/kapsam reddi veya süresi dolmuş token: teslim fail-closed; kullanıcı JWT'sine fallback yok; token kısa süre bellekte; gizli değerler loglanmaz. 401/403 ayrımı korunur; yetkisizlikte kontrolsüz retry yok; timeout/yenileme/retry bütçesi gerçek teslim sözleşmesiyle belirlenir.
- Outbox/relay yalnız gelecekteki güvenilirlik gereksinimidir; Review'da geliştirilmedi.

## 5. Kabul kriterleri (açık işler için)

- Her onaylı çift doğru audience ve kapsamla token alır; onaylanmayan çiftler 403, yanlış secret 401 alır.
- Alıcılar yanlış alg/imza/typ/issuer/audience/producer/scope, eksik zaman claim'i, expired token ve kullanıcı token'ını reddeder; yetki yayılımı oluşmaz; mevcut Auth→Review D4 ve Review→User regresyonları geçer.
- REV-15 (karar onaylanırsa): insan moderatör kullanıcı token'ı, başka servis token'ı, doğru servis ama yanlış kapsam ve doğru token fakat sahte gövde aktör referansı Review karar alıcısında reddedilir.
- Gerçek Auth token'ıyla Admin/Review alıcı uçları arasında olumlu ve olumsuz HTTP testleri.

## 6. Review frontend'in oturum/Gateway beklentileri (bilgi; yeni geliştirme istenmiyor)

Review frontend'i yeni bir oturum mekanizması kurmaz; mevcut sözleşmeye dayanır. Bu varsayımların kırılmaması beklenir:

- **Kimlik:** Tarayıcı HttpOnly oturum çerezi gönderir; Gateway `CookieBearerFilter` çerezi `Authorization: Bearer` access token'a çevirir; Review `at+jwt`'yi doğrular (`iss=ltz-auth`, `aud=ltz-api`, kanonik UUID `sub`, `roles` listesi). Review `Cookie` veya `X-User-Id`'yi kimlik saymaz.
- **Yenileme:** Access token ömrü kısadır (varsayılan 60 sn, en çok 15 dk). Frontend 401'de bir kez `POST /api/v1/auth/session/refresh` çağırıp isteği tekrarlar (yalnız oturum ipucu çerezi varsa). Review'ın 401 (kimlik yok/geçersiz) ↔ 403 (rol/sahiplik/silme engeli) ayrımı korunmalı.
- **CSRF:** PUT/POST/PATCH/DELETE istekleri `X-XSRF-TOKEN` taşır (`GET /api/v1/auth/csrf` ile hazırlanır); reddedilirse `403` + `code=CSRF_TOKEN_INVALID`, frontend belirteci yenileyip **yalnız bir kez** tekrarlar. Toplu puan özeti (`POST …/rating-summaries`) okuma olsa da POST olduğundan CSRF ister.
- **`GET /api/v1/auth/me`:** `accountId` ve `roles`; Review sayfaları içeriği `accountId` ile anahtarlar (hesap değişiminde eski veri kalmaz).
- **CORS:** Gateway `Retry-After` ve `X-Request-Id` yanıt başlıklarını açar (Review'ın 429 geri sayımı buna dayanır); `PATCH` ve `X-XSRF-TOKEN` izinli kalmalı.
- **Hesap silme:** Silinmiş hesabın eski token'ı Review yazmalarında 403 alır (17 dk koruma); frontend "bu işlem için izin yok" gösterir.

## 7. Durum

**Açık:** §2'deki maddeler. Admin→Review ve Review→Admin/Notification çiftleri ile insan moderatör güven modeli sözleşme beklediği için REV-07, REV-10 ve REV-15 tamamlanmış sayılmaz.
