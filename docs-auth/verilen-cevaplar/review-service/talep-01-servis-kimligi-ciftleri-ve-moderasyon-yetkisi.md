# TALEP 01 — REVIEW SERVİS KİMLİĞİ ÇİFTLERİ VE MODERASYON YETKİSİ

**Kimden:** Hamza (review-service) · **Kime:** Kerem (auth-service) · **Kaynak:** `docs-review/request/auth-review-integration.md` (§5 tablo, §6, §13)
**Cevap tarihi:** 2026-10-31 · **Durum:** Game → Review AUTH HAZIR · Review/Admin/Notification çiftleri BİLİNÇLİ OLARAK AÇILMADI (alıcı yok) · moderasyon yetkisi KARAR VERİLDİ
**Auth sahibi kararı:** önceki servisler arası kararlarımızla aynı çizgi (varsayılan imtiyaz yok, alıcısı olmayan izin tanımlanmaz, servis token'ından kullanıcı yetkisi türetilmez).

---

## Madde madde cevap (talebin §5 tablosu)

| # | Madde | Cevap | Durum |
|---|---|---|---|
| 1–5 | Kullanıcı JWT, roller, Auth → Review silme, kota etkileşimi | Review tarafında tamam. Auth'tan ek iş yok. | KAPANDI |
| 6 | Review → User `events:ingest` (`ReviewSummaryChanged`) | Çift Auth'ta hazır ve testli (`review-service` → `user-service`). Review üreticisi gelince `AUTH_REVIEW_SERVICE_CLIENT_SECRET` aynı değerle Review ortamına verilir. | AUTH HAZIR |
| 7 | Review → Admin rapor (`events:ingest`) | **Tanımlanmadı.** Admin'de alıcı yok, izin tek başına bir şeyi açmaz. Alıcı sözleşmesi gelince tek satır yapılandırma. | ALICI BEKLİYOR |
| 8 | Admin → Review moderasyon kararı | Ayrı kapsam **gerekmez**: `events:ingest` yeterli, ama çift yine alıcı gelince açılır. Yetki modeli aşağıda. | KARAR VERİLDİ |
| 9 | Game → Review toplu özet okuma | **Hazır.** `game-service` istemcisi, hedef `review-service`, kapsam `rating-summaries:read`. | AUTH HAZIR |
| 10 | Review → Notification | **Tanımlanmadı.** Önerimiz: bildirimi kararı veren taraf (Admin) göndersin; Review'a gereksiz bir izin açılmasın. Ürün kararı sizin, karar gelince tek satır. | KARAR SİZDE |
| 11 | İnsan moderatör yetkisinin aktarımı | Aşağıda. | KARAR VERİLDİ |
| 12 | Canlı Auth ↔ Review uçtan uca | Yapılmadı. | NOT VERIFIED |

## Game → Review (madde 9): kesin sözleşme

```http
POST {AUTH_SERVICE_URL}/internal/v1/service-tokens
{ "clientId": "game-service", "clientSecret": "<AUTH_GAME_SERVICE_CLIENT_SECRET>", "audience": "review-service" }
```

Yanıt `scope: "rating-summaries:read"`. Token: `typ=svc+jwt`, `aud=review-service`, `sub=svc:game-service`, `scope=rating-summaries:read`, 60 sn, `account_id` yok (kullanıcıya bağlı değil).

**Review'ın alıcı denetimi (hepsi zorunlu):** `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud` yalnız `review-service`, `sub` tam `svc:game-service`, `scope` içinde `rating-summaries:read`, dolu `jti`, `exp - iat ≤ 60`, saat sapması ≤ 30 sn. Bu token yalnız `GET /games/{gameId}/rating-summary` ve `POST /games/rating-summaries` okuma uçlarında geçer; başka uca, başka kapsama ve kullanıcı `at+jwt`'sine fallback yoktur. Kullanıcı JWT'si ve `account:erase` token'ı bu uçta kabul edilmez.

Gizli anahtar: `AUTH_GAME_SERVICE_CLIENT_SECRET` (Auth ortamı; `node ops/generate-auth-dev-keys.mjs` üretir, boşsa istemci kapalı). Aynı değer Game ortamına verilir; ben hiçbir ortam dosyasına dokunmadım.

## Moderasyon yetkisi (madde 8 ve 11): karar

**Yetki servis kimliğinden gelir; insan, Admin'in sorumluluğundadır.**

1. **Ayrı bir "moderasyon komutu" kapsamı açmıyoruz.** `events:ingest` yeterli. Alıcı Review, tam `sub` (`svc:admin-service`) ve olay türü eşleşmesini zaten kendisi uygular (`ModerationActionTaken` yalnız Admin ile). Kapsam adını artırmak güvenliği artırmaz; asıl sınır çağıranın kimliği ve olay türüdür.
2. **İnsan moderatörü Admin doğrular.** Admin, Gateway'den gelen kullanıcı `at+jwt`'sindeki rolü (`MODERATOR`/`ADMIN`) kendi uçlarında denetler. Auth'un rolleri token'a koyması ve değişimde 90 sn içinde yansıtması zaten sözleşmedir. Admin → Auth yönündeki `actorId` yeniden doğrulaması (hesap `ACTIVE` ve `ADMIN`) aynı modeldir.
3. **Review `actorId`'yi yetki olarak kullanmaz.** Yalnız denetim kaydına yazar. Yetki kararı: doğru servis (`svc:admin-service`) + doğru olay türü. Gövdedeki `actorId` tek başına hiçbir yetki vermez.
4. **Kullanıcı `at+jwt`'si moderasyon komutu yerine geçmez.** Review `/api/v1/review/**` uçlarında `MODERATOR`/`ADMIN` rolüne ek yetki tanımaz (zaten böyle).
5. **Auth, Review için yeni bir "bu kullanıcı moderatör mü?" ucu açmaz.** Bu, Review'a hesap/rol okuma yetkisi verir ve Auth'u senkron bağımlılık yapar. Kullandığımız modelle çelişir (servis token'ından kullanıcı yetkisi türetilmez, hesap bilgisi olaylarla akar).

**Yeniden bakma koşulu:** Admin'in güveni yetersiz bulunursa (örneğin Admin ele geçirilmesine karşı ikinci savunma istenirse) o zaman aktöre bağlı kısa ömürlü bir token ayrıca tasarlanır. Şimdilik gereksinim gerektirmiyor.

## Kimin ne yapacağı

| Sahip | İş |
|---|---|
| **Auth (Kerem)** | Yapıldı: `game-service` istemcisi ve kapsamı, testler, belgeler. Bekleyen: Admin alıcı sözleşmesi gelince Review ↔ Admin çiftleri. |
| **Review (Hamza)** | Game için okuma alıcısı (yukarıdaki denetimlerle). `ReviewSummaryChanged` üreticisi ve token istemcisi (REV-10). Moderasyon alıcısında `sub` + olay türü allowlist'i, `actorId` yalnız denetim. |
| **Game** | `AUTH_GAME_SERVICE_CLIENT_SECRET` ile token alıp Review özet uçlarını çağıran istemci. |
| **Admin** | Alıcı protokolü, insan moderatörün rolünü kendi doğrulaması. |

## Test durumu

| Seviye | Durum |
|---|---|
| Auth birim/entegrasyon (`ServiceAudienceTokenTest`, `ServiceClientConfigTest`) | PASS |
| Auth smoke (gerçek geçici yığın): `game-service` token'ı | bkz. commit mesajı |
| Game ↔ Review gerçek Auth token'ıyla uçtan uca | NOT VERIFIED (Game'de tüketici yok) |
