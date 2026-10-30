# USER-SVC-REQ-01 — Social, Library/Review ve Hardware'den beklenenler (T6, T7, T8)

Tarih: 2026-10-30. Gönderen: Kerem / user-service. Durum: OPEN — user-service tarafı hazır ve testli.
Ön koşul: Auth'ta kapsam ve çiftler ([USER-AUTH-REQ-04](../auth/auth-service-talepleri/auth-requests-04.md)).
Bu belge diğer servislerin koduna dokunmaz; yalnız istenen sözleşmeyi tarif eder.

## T6 — Umut / social-service

1. **Olayları `user` hedefine de göndermek:** `FriendshipChanged`, `UserBlocked`, `UserUnblocked`. Biçim social-service'in mevcut
   olay kayıtlarıyla **aynıdır**, değişiklik gerekmez (`{userLowId, userHighId, change}`, `{blockerId, blockedId}`). User
   alıcısı sıra bozukluğuna ve tekrara dayanıklıdır (`occurredAt` + `eventId`).
2. **Başlangıç aktarımı:** User yansıması boş başlar. Mevcut arkadaşlıklar ve engeller için tek seferlik yeniden yayın gerekir
   (her aktif ilişki için `ADDED` / `UserBlocked`). Bu yapılmadan User'da `ltz.user.social.projection-enabled=true` yapılmamalıdır.
3. **`FriendsVisibilityPort` gerçek adaptörü:** `POST /internal/v1/privacy/decisions` (`field=FRIENDS_LIST`, sahipler listesi).
   Social engel ve arkadaşlığı kendisi bildiği için yanıttaki **`setting`** alanını kullanması yeterlidir; `allowed` alanı User
   yansıması güvenilir ilan edilene kadar sahibi olmayan izleyici için hep `false`'tur. Ulaşılamazsa mevcut fail-closed (`PRIVATE`)
   davranışı korunur.
4. **Ad/avatar:** arkadaş listesi kimlik döner; ad/avatarı frontend `POST /api/v1/user/profiles/batch` (en çok 50) ile alır.
   Social'ın User'ı çağırması gerekmez.

## T7 — Hamza / library-service ve review-service

1. **Özet olayları (önerilen sözleşme):** koleksiyon/inceleme değiştiğinde `outbox` ile `user` hedefine:
   - `LibrarySummaryChanged` (`producer=library-service`, `aggregateId=userId`): `{userId, collectionCount, completedCount, favoriteCount}`
   - `ReviewSummaryChanged` (`producer=review-service`, `aggregateId=userId`): `{userId, reviewCount}`

   Sayılar **mutlaktır** (fark değil): tekrar ve sıra bozukluğu sonucu bozmaz, eksik olay bir sonrakiyle düzelir. Sayılar
   negatif olmayan tam sayıdır. Alan adları ya da sayılar değişecekse önce bu belgede anlaşalım.
2. **LIB-11 gizliliği:** başkasının koleksiyonu için `POST /internal/v1/privacy/decisions` (`field=LIBRARY`); `allowed=false` ya da
   User'a ulaşılamaması gizli veriyi göstermemeli (fail-closed).

## T8 — Alper / hardware-service

1. **`HardwareVisibilityPolicy` adaptörü:** `POST /internal/v1/privacy/decisions` (`field=HARDWARE` ya da `PERFORMANCE`,
   `viewerId`, `ownerIds`). Eşleme: `Decision(allowed, friends)` ← yanıttaki `allowed`, `friends`. Engel (iki yön), arkadaşlık ve
   hesap durumu `allowed` içinde zaten hesaba katılır; yansıma güvenilir değilken `allowed=false` döner (sözleşmenizdeki
   "unknown → deny" ile aynı).
2. **`HardwareReviewAudiencePolicy`:** bu arayüz izleyicinin tüm engel listesini (`blockedAuthors`) istiyor; User şu an
   "izleyici → sahipler" kararı verir, engel listesi dökmez. İhtiyaç sürerse ayrı bir uç konuşulmalı (yorum yazarları listesiyle
   aynı karar ucunu `HARDWARE` alanıyla toplu çağırmak bir seçenek).
