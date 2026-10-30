# USER-SVC-REQ-01 — social-service'ten beklenenler (T6)

Kime: Umut / social-service. Gönderen: Kerem / user-service. Tarih: 2026-10-30. Durum: OPEN.
User tarafı hazır, testli ve gerçek Auth ile Docker'da doğrulandı. Bu belge social-service koduna dokunmaz; yalnız sözleşmeyi tarif eder.

## Ön koşul (Auth hazır, sizin işiniz ENV)
- Auth'ta `social-service → user-service` için **`privacy:read` + `events:ingest`** tanımlı (`aud=user-service`).
- Gizli anahtar `AUTH_SOCIAL_SERVICE_CLIENT_SECRET` zaten sizde. Ek anahtar yok.
- Token: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` ile `audience=user-service` (bkz. `docs-auth/servis-sozlesmeleri.md` §3.5).
- User adresi: `USER_SERVICE_URL` (Compose'ta `http://user-service:8080`).

## İstekler
1. **Olayları `user` hedefine de gönderin** (`POST {USER_SERVICE_URL}/internal/events`, `scope=events:ingest`): `FriendshipChanged`,
   `UserBlocked`, `UserUnblocked`. Biçim mevcut olay kayıtlarınızla **aynı**, değişiklik yok:
   `{userLowId, userHighId, change: ADDED|REMOVED}` ve `{blockerId, blockedId}`. Zarf: `eventId, eventType, eventVersion=1,
   occurredAt, producer="social-service", aggregateId, payload`. Tekrar ve sıra bozukluğu sorun değil (`eventId` + `occurredAt`).
2. **Başlangıç aktarımı (tek seferlik):** User'ın yansıması boş. Mevcut her aktif arkadaşlık için `ADDED`, her aktif engel için
   `UserBlocked` yeniden yayınlayın. Bu bitmeden User'da `ltz.user.social.projection-enabled=true` **yapılmayacak**; haber verin.
3. **`FriendsVisibilityPort` gerçek adaptörü:** `POST {USER_SERVICE_URL}/internal/v1/privacy/decisions`
   `{viewerId, field:"FRIENDS_LIST", ownerIds:[...]}` (en çok 100). Siz engel ve arkadaşlığı kendiniz bildiğiniz için yanıttaki
   **`setting`** (`PUBLIC|FRIENDS|PRIVATE`) yeterli; `allowed` yansıma güvenilir ilan edilene kadar sahibi olmayan için hep `false`.
   Ulaşılamazsa mevcut fail-closed (`PRIVATE`) davranışınız kalsın.
4. **Ad/avatar:** User'ı çağırmanız gerekmez. Arkadaş listesi kimlik döner; ad/avatarı frontend
   `POST /api/v1/user/profiles/batch` ile alır.

## Kabul
- Bir arkadaşlık eklenince User'da `social_friendships` satırı oluşur (User tarafı `ProjectionEventTest` ile kanıtlı).
- Başlangıç aktarımı sonrası siz "tamam" deyince ben `projection-enabled=true` yaparım.
- Sözleşme: `docs/api.md` → USER → "Servisler arası olay girişi" ve "Gizlilik kararı".
