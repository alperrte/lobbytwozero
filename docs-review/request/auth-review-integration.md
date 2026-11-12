# AUTH–REVIEW entegrasyonu

Son güncelleme: 12 Kasım 2026. Bu belge yalnız **açık işleri ve beklentileri** içerir; tamamlanmış entegrasyonlar (kullanıcı JWT, roller, hesap silme, Game→Review puan okuma) bilerek yazılmamıştır. Kesin geliştirme emri değildir.

- Talep eden: `review-service` — Hamza. Hedef: `auth-service` — Kerem. Nihai yetki/istemci tasarımı Auth sahibince belirlenir.
- Auth kodu, izin listesi ve belgeleri değiştirilmedi. Kaynak: Auth `application.properties` (12 Kasım 2026, salt okunur), [Auth servis sözleşmeleri](../../docs-auth/servis-sozlesmeleri.md).

## 1. Auth'un cevabı (12 Kasım 2026) ve sonuç

Auth sahibinin cevabı (kaynak: `application.properties`, `TokenClaims.java`, salt okunur): **Auth tarafında Review için bugün yapılacak kod işi yoktur; yeni kapsam istenmez.**

- **GAME-26 kapsamları** (`game-identity:prepare|finalize|read|decision:read`) Auth'ta **uygulanmış ve testlidir**. `docs/api.md`'deki "taslak, uygulanmamış" ifadesi eskidir; Auth düzeltecek. (Önceki "çelişki" maddesi kapandı.)
- **Hazır çiftler:** `review-service → user-service`: `events:ingest`; `review-service → game-service`: `game-identity:decision:read`; `game-service → review-service`: `rating-summaries:read`, `game-references:read`, `game-identity:*`. `rating-summaries:read` ile `game-identity:*` ayrı kapsamlardır (Review alıcısı bu ayrımı uygular).
- **Secret:** dev'de `ops/dev.mjs`, `AUTH_REVIEW_SERVICE_CLIENT_SECRET`'ı otomatik üretip `.env.dev`'e yazar. Review servisinin ortamına aktarım **yapıldı** (compose, 12 Kasım 2026; DEV yeniden oluşturuldu) (ENV değişikliği → kullanıcı onayı). **ENV onayı verildi (12 Kasım 2026, Hamza).** Yerel ltz-dev'de Auth ve Review aynı değerle çalışıyor (canlı kanıt: Review→User sayaç olayları teslim ediliyor, yazınca 1 silince 0). Kodda ek değişiklik gerekmedi: Auth (`application.properties` satır 126), Review (`application.properties` satır 66) ve `docker-compose.yml` zaten aynı değişkeni okuyor, `.env.example` yer tutucusu mevcut. **Prod değeri bu repoda yoktur** (`.env.prod` yok, ortak/üretim ortamının tanımı repoda bulunmuyor): üretim ortamının sahibi bir değer üretip Auth ve Review'a AYNI değerle verir; boşsa Review→User teslimi kapalı kalır (kapalı varsayılan, outbox birikir). Rotasyon: yeni değer önce Auth'a, sonra Review'a verilip ikisi yeniden başlatılır; arada Review→User teslimi 401/403 alır: relay token önbelleğini temizler, satırı silmez ve üstel geri çekilmeyle (uzun bekleme, sıkı döngü yok) yeniden dener; değerler eşleşince bekleme süresi dolduğunda teslim edilir (`UserSummaryRelay`). Değer belgeye yazılmaz.
- **Gateway beklentileri (§5):** çerez→Bearer, refresh ve CSRF mevcut sözleşmeyle uyumlu, kırılma planı yok. CORS başlıkları (`Retry-After`, `X-Request-Id`, `PATCH`) için Gateway yapılandırması Auth tarafında ayrıca teyit edilecek. (Review, Gateway kaynağını salt okunur inceledi: `GatewaySecurityConfig` `PATCH` ve `X-XSRF-TOKEN`'a izin veriyor, `Retry-After` ve `X-Request-Id`'yi açıyor; bu bir canlı doğrulama değildir.)
- **Canlı uçtan uca: KISMEN YAPILDI.** DEV'de Review gerçek Auth servis token'ıyla User'a 1092/1092 olay teslim etti (`events:ingest`). Yanlış kapsam/`aud`, süresi dolmuş token ve GAME-26 çiftleri canlıda denenmedi (NOT VERIFIED).

## 2. Model değişikliği önerisi: moderasyon/rapor uçlarını Review sunar (KABUL EDİLDİ — 12 Kasım 2026, Hamza)

Auth'un bildirdiği güncel Admin modeli: Admin paneli (login + dashboard) hazırdır; **her servis kendi admin uçlarını kendisi açar, panel onları çağırır.** Auth'un önerisi: Review moderasyon ve rapor uçlarını kendisi sunar; bu uçlar kullanıcının `at+jwt`'sini (Gateway çerezi Bearer'a çevirir) alır ve **`MODERATOR`/`ADMIN` rolünü Review'da doğrular.** Bu modelde:

- `admin-service → review-service` servis çifti, ayrı komut kapsamı ve insan aktör taşıması **gerekmez**; REV-15 sadeleşir ve önceki "insan moderatör güven modeli" sorusu kapanır.
- Panel raporları Review'ın admin ucundan okuyacaksa `review-service → admin-service` çiftine de gerek kalmaz. Admin servisi rapor **push** ile almak isterse en dar çift (`events:ingest`) o zaman ayrıca eklenir. `review-service → notification-service` yalnız üretici Review seçilirse eklenir. **Şimdiden izin açılmaz.**

**Bu, Review'ın mevcut kararını değiştirir** (bugün `MODERATOR`/`ADMIN` Review'da ek yetki taşımaz; kullanıcı JWT'si moderasyon kararı yerine geçmez — [kararlar](../REVIEW_SERVICE_DECISIONS.md)). Kabul edilirse etkisi: Review'da rol tabanlı yeni yetki sınıfı, moderasyon/rapor yönetim uçları ve `HIDDEN/ARCHIVED` durumu eklenir; Admin ve Notification belgeleri buna göre yeniden yazılır ([Admin belgesi](admin-review-integration.md) §15). Kabul edilene kadar Review'da kod yazılmaz.

**Rol/2FA uyarısı [Auth]:** `ADMIN` hesaplar Auth'ta zorunlu 2FA ile girer. `MODERATOR` için 2FA zorunluluğu **ayrı bir karardır ve Review uçları açılmadan önce netleşmelidir** (2FA'sız MODERATOR token'ıyla içerik gizleme riski).

## 3. Açık işler

1. ~~Model kararı~~ **Kabul edildi ve Review'da uygulandı** (12 Kasım 2026; `/api/v1/review/admin/**`, `MODERATOR`/`ADMIN` kullanıcı JWT'si; Auth'tan yeni çift/kapsam yok).
2. **MODERATOR için 2FA kararı [Auth + ürün]** (§2).
3. **Secret'ın Review ortamına aktarımı [Hamza + kullanıcı onayı]:** Review compose/ENV; üretici yalnız secret verilince açılır.
4. **Gateway CORS teyidi [Auth]** (§1).
5. **Canlı uçtan uca [Tümü]:** NOT VERIFIED (§1).
6. **Auth düzeltmesi [Auth]:** `docs/api.md`'deki GAME-26 kapsamı ifadesinin güncellenmesi.

Auth'tan yeni kapsam/çift istenmiyor; REV-13 (kota, tekillik, sayaç) Review içinde çözülür.

## 4. Kabul kriterleri (açık işler için)

- (Model kabul edilirse) Review'ın moderasyon/rapor uçları: `MODERATOR`/`ADMIN` olmayan kullanıcı 403, rol yalnız `roles` listesindeki kanonik değerlerden, anonim 401, servis token'ı reddedilir; yetki yayılımı olmaz.
- Secret verildiğinde Review→User olayı gerçek Auth token'ıyla kabul edilir; yanlış secret/kapsam reddedilir; mevcut Auth→Review D4 ve Game→Review regresyonları geçer.
- Canlı Auth↔Review olumlu ve olumsuz HTTP testleri.

## 5. Review frontend'in oturum/Gateway beklentileri (bilgi; yeni geliştirme istenmiyor)

Review frontend'i yeni bir oturum mekanizması kurmaz; mevcut sözleşmeye dayanır. Bu varsayımların kırılmaması beklenir:

- **Kimlik:** Tarayıcı HttpOnly oturum çerezi gönderir; Gateway `CookieBearerFilter` çerezi `Authorization: Bearer` access token'a çevirir; Review `at+jwt`'yi doğrular (`iss=ltz-auth`, `aud=ltz-api`, kanonik UUID `sub`, `roles` listesi). Review `Cookie` veya `X-User-Id`'yi kimlik saymaz.
- **Yenileme:** Access token ömrü kısadır (varsayılan 60 sn, en çok 15 dk). Frontend 401'de bir kez `POST /api/v1/auth/session/refresh` çağırıp isteği tekrarlar (yalnız oturum ipucu çerezi varsa). Review'ın 401 (kimlik yok/geçersiz) ↔ 403 (rol/sahiplik/silme engeli) ayrımı korunmalı.
- **CSRF:** PUT/POST/PATCH/DELETE istekleri `X-XSRF-TOKEN` taşır (`GET /api/v1/auth/csrf` ile hazırlanır); reddedilirse `403` + `code=CSRF_TOKEN_INVALID`, frontend belirteci yenileyip **yalnız bir kez** tekrarlar. Toplu puan özeti (`POST …/rating-summaries`) okuma olsa da POST olduğundan CSRF ister.
- **`GET /api/v1/auth/me`:** `accountId` ve `roles`; Review sayfaları içeriği `accountId` ile anahtarlar (hesap değişiminde eski veri kalmaz).
- **CORS:** Gateway `Retry-After` ve `X-Request-Id` yanıt başlıklarını açar (Review'ın 429 geri sayımı buna dayanır); `PATCH` ve `X-XSRF-TOKEN` izinli kalmalı.
- **Hesap silme:** Silinmiş hesabın eski token'ı Review yazmalarında 403 alır (17 dk koruma); frontend "bu işlem için izin yok" gösterir.

## 6. Durum

**Auth tarafında iş yok.** Açık: §3 (model kararı, MODERATOR 2FA, secret aktarımı, canlı doğrulama). REV-07'nin Admin'e teslimi ve REV-15 model kararına bağlıdır.
