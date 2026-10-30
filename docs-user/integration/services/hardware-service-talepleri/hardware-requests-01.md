# USER-SVC-REQ-03 — hardware-service'ten beklenenler (T8)

Kime: Alper / hardware-service. Gönderen: Kerem / user-service. Tarih: 2026-10-30. Durum: OPEN.
User tarafı hazır, testli ve gerçek Auth ile Docker'da doğrulandı. Bu belge hardware-service koduna dokunmaz.

## Ön koşul (Auth hazır, sizin işiniz ENV)
- Auth'ta `hardware-service → user-service` için yalnız **`privacy:read`** tanımlı (`aud=user-service`). `events:ingest` **yok**
  (donanım olayı User'a gitmez).
- **Yeni gizli anahtar:** `AUTH_HARDWARE_SERVICE_CLIENT_SECRET` (dev: `node ops/generate-auth-dev-keys.mjs`; aynı değeri kendi
  ortamınıza verin).
- Token: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` ile `audience=user-service`.
- User adresi: `USER_SERVICE_URL` (Compose'ta `http://user-service:8080`).

## İstekler
1. **`HardwareVisibilityPolicy` adaptörü:** `POST {USER_SERVICE_URL}/internal/v1/privacy/decisions`
   `{viewerId, field:"HARDWARE"|"PERFORMANCE", ownerIds:[...]}` (en çok 100; misafir için `viewerId:null`).
   Eşleme: `Decision(allowed, friends)` ← yanıttaki `allowed`, `friends`. Engel (iki yön), arkadaşlık ve hesap durumu `allowed`
   içinde hesaba katılır. Social yansıması güvenilir ilan edilene kadar sahibi olmayan izleyici için `allowed=false` döner
   (sizin sözleşmenizdeki "unknown → deny" ile aynı). Ulaşılamazsa fail-closed kalın.
2. **`HardwareReviewAudiencePolicy` (açık soru):** arayüzünüz izleyicinin tüm engel listesini (`blockedAuthors`) istiyor; User
   "izleyici → sahipler" kararı verir, engel listesi dökmez. Yorum yazarları listesi için aynı karar ucunu `HARDWARE` alanıyla
   toplu çağırmak bir seçenek. İhtiyaç sürüyorsa ayrı bir uç konuşalım.

## Kabul
- Bir sahibin donanım ayarı `PRIVATE` iken başka izleyici için `allowed=false`; sahip kendi için `allowed=true`.
- Kapsamsız token `403`, kullanıcı token'ı `401` (User tarafı testli).
- Sözleşme: `docs/api.md` → USER → "Gizlilik kararı".
