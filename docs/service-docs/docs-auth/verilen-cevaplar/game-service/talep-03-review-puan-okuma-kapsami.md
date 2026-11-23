# TALEP 03 — `game-service → review-service` puan okuma kapsamı

**Kimden:** Hamza (game-service, review-service) · **Kime:** Kerem (auth-service) · **Tarih:** 2026-11-05 · **Durum:** KAPANDI (Hamza 2026-11-05: Review uyarlandı, Auth'ta iş yok)

---

## NEYDİ

Game Service, LTZ oyun puanı filtre/sıralaması (GAME-04/05) ve kart puanı (GAME-15) için Review Service'ten sunucu-sunucu puan okuyacak. Review alıcı uçları hazır:
`POST /internal/v1/games/rating-summaries` (toplu özet) ve `GET /internal/v1/games/rating-summaries` (imleçli dışa aktarma). Review bu uçlarda `svc:game-service` → `aud=review-service` token'ında `ratings:read` kapsamını bekliyor ve Auth'tan bu kapsamlı yeni bir çift istedi.

## NE SORULDU

1. `ratings:read` kapsam adı onaylanıyor mu?
2. Yapıldı mı (dosyalar, test sonuçları)?
3. ENV: `AUTH_GAME_SERVICE_CLIENT_SECRET` nasıl dağıtılır, Game hangi adla okur, üretim/rotasyon yöntemi?
4. Game'in token alma kuralları (ömür, önbellek, yenileme payı)?
5. Ne zaman main'e girer?
6. Başka beklenti var mı?

## BENİM CEVABIM

**1. Karar: yeni kapsam eklenmiyor; mevcut `rating-summaries:read` kullanılacak.**
Bu çift Auth'ta **zaten tanımlı** ve `origin/main`'de (2026-10-31, commit `bf473ed` "LTZ-KER-AUTH-37: Review ve Performance Talepleri - game->review, hardware->performance…"): `svc:game-service → review-service`, kapsam `rating-summaries:read`
([servis-sozlesmeleri.md](../../servis-sozlesmeleri.md) §3.5, tablo satır 12; `application.properties`:
`ltz.auth.service-clients.clients.game-service.targets.review-service=rating-summaries:read`). Dolayısıyla "Game denerse
`403 SERVICE_AUDIENCE_FORBIDDEN` alır" bugün doğru değil: Game, `audience=review-service` ile token alabilir ve token'daki `scope` **`rating-summaries:read`** olur.
Eksik olan Auth'ta değil, **Review'da**: Review `ratings:read` bekliyor (`InternalRatingSummaryController.SCOPE`), Auth `rating-summaries:read` veriyor.
**Review'ın sabiti `rating-summaries:read` olarak değiştirilmeli.** Aynı sabit iki iç ucu da (toplu özet ve dışa aktarma) kapsar.

**2. Yapıldı mı: Auth'ta değişiklik yok, gerekmiyor.** Mevcut testler bu çifti zaten kanıtlıyor (`ServiceAudienceTokenTest`: `game-service` + `review-service` → 200, `scope=rating-summaries:read`,
`sub=svc:game-service`; `admin-service`, `user-service`, hedefsiz istek → 403). 2026-11-05 Auth duman testi (Auth + Gateway, gerçek HTTP) bu çifti de kapsayarak 41/41 geçti.
`ServiceClientTokenIssuer` imzası ve claim şekli **değişmedi**. Review ↔ Game uçtan uca: **NOT VERIFIED** (Game tarafı yazılınca).

**3. ENV.** `AUTH_GAME_SERVICE_CLIENT_SECRET` **zaten var**: `.env.example`, Auth'un Compose bloğu ve `ops/generate-auth-dev-keys.mjs` (boşsa üretir, doluya dokunmaz). Game'in Compose bloğunda yok;
Game'in kendi bloğuna **aynı adla** (`AUTH_GAME_SERVICE_CLIENT_SECRET: ${AUTH_GAME_SERVICE_CLIENT_SECRET:-}`) eklemesini ve aynı adla okumasını öneriyoruz (aynı değer iki serviste). Mevcut tüketiciler de böyle: social, messaging ve community kendi Compose bloklarında `AUTH_<SERVİS>_SERVICE_CLIENT_SECRET` adını Auth'taki gibi aynen kullanıyor; `AUTH_SERVICE_CLIENT_SECRET` gibi ortak bir ad kullanma, sır istemciye özgüdür. Dev'de sırrı
`node ops/generate-auth-dev-keys.mjs` üretir; production'da sır yöneticisinde tutulur. İstemci sırrı için otomatik rotasyon betiği yok: yeni değer Auth ve Game'e birlikte verilir; boş sır = istemci kapalı.

**4. Token kuralları.** `POST /internal/v1/service-tokens` gövdesi: `{ "clientId": "game-service", "clientSecret": "…", "audience": "review-service" }`. `accountId` **verilmez** (bu kapsam hesaba bağlı değil; verilirse `400`).
Token ömrü **sabit 60 sn** (`exp`), `iat/nbf/jti` zorunlu, `typ=svc+jwt`, `iss=ltz-auth`, `aud=["review-service"]`, `sub=svc:game-service`. **İstek oranı:** bu uç için Auth'ta istek oranı sınırı uygulanmıyor (kodda yok); `/internal/**` Gateway'e açılmaz, yalnız iç ağdan çağrılır. **Öneri (kural değil):** token'ı bellekte tutun ve
`exp`'ten 10–15 sn önce yenileyin (60 sn ömürde 15 sn uygun, social-service `AuthServiceTokenClient` kalıbı); her istekte yeni token almayın.

**5. Zamanlama.** Auth'ta bekleyen değişiklik yok, çift bugün main'de. Game sahte sunucuya mecbur değil: gerçek Auth'tan `rating-summaries:read` token'ı alabilir.

**6. Başka.** `GAME_DEVELOPER` rolü yalnız yönetici uçlarıyla atanır (`/api/v1/auth/admin/accounts/{id}/roles/grant`), kendi kendine atama yok; zorunlu 2FA yalnız `ADMIN` ve `MODERATOR` için.
Hesap durumu/rol değişikliği diğer servislerde en çok 60 sn gecikmeyle görünür (kullanıcı token ömrü).

**Kanıt (kendin doğrula):**

```text
git fetch origin
git show origin/main:backend/services/auth-service/src/main/resources/application.properties | grep -n "clients.game-service"
  -> ltz.auth.service-clients.clients.game-service.secret=${AUTH_GAME_SERVICE_CLIENT_SECRET:}
  -> ltz.auth.service-clients.clients.game-service.targets.review-service=rating-summaries:read
git log origin/main --oneline | grep AUTH-37
```

- Kapsam sabiti: `TokenClaims.RATING_SUMMARIES_READ_SCOPE = "rating-summaries:read"`, `ServiceClientConfig.ALLOWED_TARGET_SCOPES` içinde.
- Test: `ServiceAudienceTokenTest.gameYalnizReviewIcinPuanOzetiOkumaTokeniAlirKullaniciBagiTasimaz`.
- Gerçek yanıt biçimi (`game-service` + doğru secret + `audience=review-service`): `200 { "accessToken": "<svc+jwt>", "tokenType": "Bearer", "expiresIn": 60, "scope": "rating-summaries:read" }`; token'da `aud=["review-service"]`, `sub=svc:game-service`, `roles/sid/ver/amr/account_id` yok.
- Sonuç, kendi yerel `origin/main` kopyan eskiyse (`git fetch` yapılmadıysa) çifti göremezsin.

## NEDEN

- Aynı çift ve aynı veri (puan özeti, salt-okunur) için iki ayrı kapsam adı tutmak sözleşmeyi bulandırır; Auth'ta kapsam adı sözleşmedir ve servisler Auth'a uyar.
- Yeni ENV, yeni kod, migration veya mevcut 13 çiftte risk gerekmez.
- Dışa aktarmanın ayrı bir izin olması gereken bir gerekçe varsa (ör. daha geniş veri), bunu yaz: `targets.review-service=rating-summaries:read,ratings:read` biçiminde ikinci bir kapsam eklenebilir ve Review iki ucu ayrı kapsama bağlayabilir. Şimdilik gerekçe görünmüyor.

## SENDEN NE BEKLİYORUM

- **Review:** `InternalRatingSummaryController.SCOPE` değerini `rating-summaries:read` yap, Review testini bu kapsama göre güncelle (Auth'un gerçek `ServiceClientTokenIssuer`'ı `issue("game-service", "review-service", Set.of("rating-summaries:read"))` ile aynı token'ı üretir).
- **Game:** Compose bloğuna `AUTH_GAME_SERVICE_CLIENT_SECRET` ekle, token'ı önbellekle, `accountId` gönderme.
- Gerçek Auth ile uçtan uca denemeyi Game entegrasyonu bitince yap ve sonucu `docs-review`/`docs-game` kayıtlarına yaz.
- Dışa aktarma için ayrı kapsam gerekçen varsa bu talebe yanıt olarak yaz.

## DURUM

**KAPANDI** — Hamza'nın 2026-11-05 cevabı: Review `InternalRatingSummaryController.SCOPE`'u `rating-summaries:read` yaptı (Review 174/174 test), eski `ratings:read` reddediliyor. Auth'ta iş yok. Kalan (Game tarafı, bilgi): Game Compose bloğuna `AUTH_GAME_SERVICE_CLIENT_SECRET` ve gerçek Auth↔Game↔Review uçtan uca doğrulaması (**NOT VERIFIED**).
