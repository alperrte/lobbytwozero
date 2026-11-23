# USER-SVC-REQ-02 — library-service ve review-service'ten beklenenler (T7)

Kime: Hamza / library-service ve review-service. Gönderen: Kerem / user-service. Tarih: 2026-10-30. Durum: OPEN.
User tarafı hazır, testli ve gerçek Auth ile Docker'da doğrulandı. Bu belge sizin koda dokunmaz; yalnız sözleşmeyi tarif eder.

## Ön koşul (Auth hazır, sizin işiniz ENV)
- Auth'ta çiftler tanımlı (`aud=user-service`): **library** → `privacy:read` + `events:ingest`; **review** → `events:ingest`.
- **Yeni gizli anahtarlar:** `AUTH_LIBRARY_SERVICE_CLIENT_SECRET`, `AUTH_REVIEW_SERVICE_CLIENT_SECRET`. Dev için
  `node ops/generate-auth-dev-keys.mjs` üretir; aynı değeri kendi servis ortamınıza vermeniz gerekir.
- Token: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` ile `audience=user-service`.
- User adresi: `USER_SERVICE_URL` (Compose'ta `http://user-service:8080`).

## İstekler
1. **Özet olayları** (outbox ile `POST {USER_SERVICE_URL}/internal/events`, `scope=events:ingest`; önerilen sözleşme):
   - library-service: `LibrarySummaryChanged` — `producer="library-service"`, `aggregateId=userId`,
     payload `{userId, collectionCount, completedCount, favoriteCount}`.
   - review-service: `ReviewSummaryChanged` — `producer="review-service"`, `aggregateId=userId`, payload `{userId, reviewCount}`.

   Sayılar **mutlak** (fark değil) ve negatif olmayan tam sayı: tekrar ve sıra bozukluğu sonucu bozmaz, eksik olay bir sonrakiyle
   düzelir. `userId` zarftaki `aggregateId` ile aynı olmalı. Alan adı/sayı değişecekse önce bu belgede anlaşalım.
   Profilde şu an bu sayılar `UNAVAILABLE` görünüyor; olaylar gelince `AVAILABLE` olur.
2. **Başlangıç aktarımı (tek seferlik):** mevcut kullanıcılar için güncel sayıları bir kez yayınlayın.
3. **LIB-11 gizliliği (yalnız library):** başkasının koleksiyonu için `POST {USER_SERVICE_URL}/internal/v1/privacy/decisions`
   `{viewerId, field:"LIBRARY", ownerIds:[...]}`. `allowed=false` ya da User'a ulaşılamaması **gizli veriyi göstermemeli**
   (fail-closed). review-service bu uca erişemez (`403`), gerek de yok.

## Kabul
- Bir koleksiyon değişince `GET /api/v1/user/profiles/{id}` yanıtında `summary.library.status=AVAILABLE` ve doğru sayılar.
- Sözleşme: `docs/api.md` → USER → "Servisler arası olay girişi" ve "Gizlilik kararı".
