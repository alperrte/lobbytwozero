# TALEP 02 — OTURUMLU HESABA STEAM BAĞLAMA/KALDIRMA VE `ExternalIdentity*` OLAYLARI

**Talep kodu:** USER-AUTH-REQ-05 (T4) · **Kimden:** Kerem (user-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-31
**Kaynak:** `docs-user/integration/auth/auth-service-talepleri/auth-requests-05.md`
**Durum:** AUTH HAZIR (yerel doğrulama). Gerçek Steam ile ve gerçek User ile uçtan uca **NOT VERIFIED**.

---

## İstenenlerin karşılığı

| # | İstenen | Cevap |
|---|---|---|
| 1 | Oturumlu kullanıcı hesabına Steam bağlasın; aynı SteamID başka hesaptaysa reddedilsin | Yapıldı. `start` + Steam OpenID + `callback`. Tekillik hem kodda (`EXTERNAL_IDENTITY_TAKEN`) hem veritabanı kısıtlarıyla zorlanır. |
| 2 | Kaldırma; Steam tek giriş yöntemiyse reddedilsin | Yapıldı. `POST .../steam/unlink`; tek yöntemse `409 LAST_LOGIN_METHOD`. |
| 3 | `ExternalIdentityLinked/Unlinked` olayları | Yapıldı. Aynı outbox, aynı zarf. |
| 4 | Mevcut Steam hesapları için tek seferlik `Linked` yayını | **Yapılmıyor** (REQ-02 ile aynı karar B). DEV'de hesaplar yeniden kaydolur; gerçek hesap varsa ayrıca konuşulur. |

## Uçlar (kesin sözleşme: `docs/api.md` → "AUTH — Steam bağlama ve kaldırma")

| Uç | Yetki | Sonuç |
|---|---|---|
| `POST /api/v1/auth/oauth/steam/link/start` | oturum + **yeniden doğrulama** (`password`, TOTP etkinse `code`/`recoveryCode`) | `200 { "redirectUrl" }`; HttpOnly `__Host-ltz_oauth` state çerezi |
| `GET /api/v1/auth/oauth/steam/link/callback` | public; state çerezi + tek kullanımlık, hesaba bağlı akış | `302` frontend fragment'i: `#status=steam_linked` veya `#error=<KOD>` |
| `POST /api/v1/auth/oauth/steam/unlink` | oturum + yeniden doğrulama | `204` |

Hata kodları: `EXTERNAL_IDENTITY_TAKEN`, `EXTERNAL_IDENTITY_ALREADY_LINKED`, `EXTERNAL_IDENTITY_NOT_LINKED` (404), `LAST_LOGIN_METHOD`, `PASSWORD_REQUIRED`, `CURRENT_PASSWORD_INVALID`, `INVALID_TWO_FACTOR_CODE`, `EXTERNAL_AUTH_FAILED`.

## Olaylar (User alıcısına)

Aynı yol: `POST /internal/events`, `aud=user-service`, `scope=events:ingest`, `sub=svc:auth-service`, `account_id=aggregateId`, `eventVersion=1`, hedef `AUTH_EVENT_TARGETS=user` (dev varsayılanı).

```json
{ "eventType": "ExternalIdentityLinked",   "payload": { "accountId": "<UUID>", "provider": "STEAM", "providerSubject": "<17 haneli SteamID64>" } }
{ "eventType": "ExternalIdentityUnlinked", "payload": { "accountId": "<UUID>", "provider": "STEAM" } }
```

- **SteamID64 olaya konur** (talep ettiğiniz gibi). Olaylarda kişisel veri yok kuralının tek, bilinçli istisnasıdır: SteamID64 herkese açık bir kimliktir; e-posta ve parola hâlâ konmaz.
- Aynı kimlik zaten bu hesaptaysa bağlama idempotent başarıdır ve **yeni olay üretilmez**.
- `occurredAt` ile son durum upsert'iniz bu olaylar için de yeterli; sıra bozukluğunda en yeni kazanır.

## Güvenlik kararları

1. **Yeniden doğrulama zorunlu.** Bağlama giriş yöntemi ekler. Çalınmış bir oturum kendi Steam'ini bağlayıp hesabı sonradan ele geçirebilir; bu yüzden cihaz bağlama ve e-posta değiştirmeyle aynı kural uygulanır (parola, TOTP etkinse ikinci faktör).
2. **Parolasız hesap bağlayamaz/kaldıramaz** (`PASSWORD_REQUIRED`). Cihaz bağlama ve e-posta değiştirmeyle aynı davranış; açık B11 kararına (parolasız hesap parola belirleyemez) bağlı. B11 çözülürse üç işlem birlikte açılır. Steam-only hesapta kaldırma ayrıca `LAST_LOGIN_METHOD` ile kapanır.
3. **Hesaba bağlı akış.** Hangi hesaba bağlanacağı akış satırına (`oauth_link_flows`) yazılır; state ayrıca tarayıcı çerezine bağlıdır. Başkasının başlattığı akış kurbanın tarayıcısında tamamlanamaz; giriş state'i bağlamada, bağlama state'i girişte geçmez.
4. **Auth kimlik kanıtının tek üreticisi.** SteamID64, giriş akışıyla aynı OpenID doğrulamasından (imza/alan denetimi, nonce tek kullanım, Steam'e geri sorgu) geçer. User kimlik kanıtı üretmez.

## User tarafı için

- Kullanıcının bağlı hesaplarını göstermek için olayları kullanırsınız (`GET /api/v1/user/me/linked-accounts` zaten sizde). Auth'ta ayrı bir "bağlı hesaplar listesi" ucu **açılmadı** (kimlik bilgisi yalnız olayla akar).
- Frontend akışı: `start` bir `fetch` ile çağrılır (CSRF başlığı + parola), dönen `redirectUrl`'e gidilir, sonuç frontend adresinin fragment'inden okunur.
- Discord: Auth'ta yok, bu talepte de istenmedi.

## Test durumu

| Seviye | Durum |
|---|---|
| Auth: `SteamLinkTest` (10), `AuthEventOutboxTest` (yük biçimi), `FlywayMigrationTest` | PASS |
| Auth duman testi (start, yanlış parola, bağsız kaldırma) | bkz. commit mesajı |
| Gerçek Steam OpenID ile bağlama | NOT VERIFIED |
| Auth → gerçek User olay teslimi ve `linked-accounts` | NOT VERIFIED |
