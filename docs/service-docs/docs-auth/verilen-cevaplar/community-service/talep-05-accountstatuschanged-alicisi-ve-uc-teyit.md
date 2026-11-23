# TALEP 05 — `AccountStatusChanged` ALICISI VE ÜÇ TEYİT

**Talep kodu:** COM-AUTH-REQ-05 · **Kimden:** Nisa (community-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-29
**Durum:** CEVAPLANDI — Auth tarafında bir kod değişikliği gerekmedi, bir ayar eksiği kapatıldı (aşağıda, madde 1). Gerçek Community ile uçtan uca deneme **henüz yapılmadı**

---

## NEYDİ

Community alıcı ucunu (`POST /internal/events`) ve giden servis token'ını bitirdi. Auth'tan üç teyit istedi: `community` hedefinin eklenmesi, `svc:auth-service → community-service` çiftinin hazır olduğu, ve `community-service` istemcisinin dev'de etkin olduğu.

## CEVAPLARIM

### 1. `community` hedefini kim ekler, haber kime?

**Auth sahibi (Kerem) ekler.** Ekleme, Auth'un ortamında tek bir değişkenle olur: `AUTH_EVENT_TARGETS=community` (virgülle ayrılmış servis adları; boşsa hiçbir olay gönderilmez). `COMMUNITY_SERVICE_URL` zaten tanımlı olmalı (Compose'ta zorunlu).

- **Eksik bulundu ve kapatıldı:** Bu ayar daha önce yalnız `application.properties`'te boş tanımlıydı ve `docker-compose.yml` Auth'a aktarmıyordu; yani Compose dev'de hedef eklemenin yolu yoktu. Artık `AUTH_EVENT_TARGETS` değişkeni `.env.example` ve `docker-compose.yml` üzerinden Auth'a geçiyor (varsayılan boş, davranış değişmedi). Testle doğrulandı (`EventConfigTest`).
- **Ne zaman:** Community'nin bu dalı birleşip **dev imajı güncellendikten ve `POST /internal/events` yayına girdikten sonra**. Daha önce eklenirse teslim 401/403/404 alır, Auth üstel geri çekilmeyle (10 sn'den en çok 1 saate) yeniden dener ve 20. denemede bir kez "takılı" uyarısı yazar, denemeye devam eder. Veri kaybı olmaz ama log gürültüsü yapar.
- **Haber:** "Alıcı yayında" haberini Kerem'e ver (bu belgenin sahibi). Kerem dev `.env`'ine `AUTH_EVENT_TARGETS=community` yazar ve Auth'u yeniden başlatır. Eski `.env` dosyalarında satır yoksa eklenir, `.env.example`'a bakılır.

### 2. `svc:auth-service → community-service` (`events:ingest`) çifti hazır mı?

**Evet.** `HttpAuthEventSender` her teslimde yeni bir servis token'ı üretir:

| Alan | Değer |
|---|---|
| Başlık `typ` | `svc+jwt` (RS256, JWKS, `kid`) |
| `iss` | `ltz-auth` |
| `sub` | `svc:auth-service` |
| `aud` | `community-service` (tek) |
| `scope` | `events:ingest` |
| Ek claim | `account_id` = olayın hesabı (alıcı yok sayabilir) |
| `jti`, `iat`, `nbf`, `exp` | var; ömür 60 sn |

Yol: `POST {COMMUNITY_SERVICE_URL}/internal/events`, `Authorization: Bearer <token>`. Yönlendirme izlenmez, yalnız `2xx` teslim sayılır.

Zarf (senin beklentinle birebir):

```json
{ "eventId": "UUID", "eventType": "AccountStatusChanged", "eventVersion": 1, "occurredAt": "...",
  "producer": "auth-service", "aggregateId": "<accountId>",
  "payload": { "accountId": "UUID", "status": "SUSPENDED" } }
```

`payload` yalnız `accountId` ve `status` içerir (e-posta ya da başka kişisel veri yok). `status` değerleri hesap durum adlarıdır (`ACTIVE`, `SUSPENDED`, `BANNED`, `DISABLED`). `producer` değeri token `sub`'ının `svc:` sonrasıyla eşleşir. Bu kurallar testle sabit (`AuthEventOutboxTest`: ömür ≤ 60 sn, `nbf`/`jti` var, `sub = svc:` + `producer`, payload anahtarları tam olarak `accountId` ve `status`).

**Olayın çıktığı tek yer:** yönetici durum değişikliği (askıya alma, yasaklama, devre dışı bırakma, yeniden açma). Silme isteği, iptal ve silme tamamlanması olay üretmez; onlar `DELETE /internal/v1/accounts/{accountId}` ile yürür.

### 3. `community-service` istemcisi dev'de etkin mi?

**Evet**, `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` doluysa (üretici betik `node ops/generate-auth-dev-keys.mjs` doldurur; dolu olana dokunmaz). Bu istemci için tanımlı çiftler: `admin-service` ve `notification-service`, ikisi de `events:ingest`. Başka hedef için token alamaz (`403 SERVICE_AUDIENCE_FORBIDDEN`), Auth'a da token alamaz.

Yanıt (`200`, `Cache-Control: no-store`): `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 60, "scope": "events:ingest" }`. Ek alanlar eklenirse haber verilir.

## UÇTAN UCA DENEME: DÜRÜST DURUM

- **Doğrulananlar (Auth tarafı):** Token, zarf ve payload, senin alıcı kurallarına karşı sahte bir alıcıyla testli. Servis token'ı ucu gerçek Auth ve Gateway ile duman testinde denendi.
- **Doğrulanmayanlar:** Gerçek Community jar'ıyla gerçek Auth'un birlikte çalıştığı bir deneme yapılmadı. Community'nin servisine Auth tarafından dokunulmadı.
- **Nasıl denenir** (hedef eklendikten sonra): dev'de bir test hesabının durumunu yönetici ucundan değiştir (`POST /internal/v1/accounts/{id}/status`, `admin-service` token'ı, `actorId` olarak ADMIN hesap). Sonra Auth tarafında `auth_event_outbox` tablosunda ilgili satırın `status=DELIVERED` olduğuna, Community tarafında `inbox_events` içinde olay kaydının oluştuğuna bak. Aynı `eventId` ikinci kez teslim edilirse sen `DUPLICATE` ile 200 dönersin, Auth bunu başarı sayar.

## SENDEN NE BEKLİYORUM

- "Alıcı yayında" haberi (kaç dakika içinde açılmasını istersen söyle).
- Dev'de gerçek bir silme denemesi (`AUTH_DELETION_GRACE_PERIOD=PT0S` yalnız dev) ve `community` görevinin `CONFIRMED` olması.
- Rapor saklama süresi Alper'in kararı; Auth bunu etkilemez.

## NEREDEN BAKARSIN

- Zarf ve hedef kuralları: `docs-auth/servis-sozlesmeleri.md` bölüm 3.5
- Servis token ucu: `docs/api.md` "Servis kimlikli iç uçlar"
- İptal ve durum yayılımı: `docs/api.md` "Token iptali ve hesap durumu yayılımı (politika)"
