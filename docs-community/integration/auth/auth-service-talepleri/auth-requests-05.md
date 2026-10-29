# Auth Talebi 05 — `AccountStatusChanged` alıcısı hazır, giden servis token'ı bağlandı; Auth'tan beklenen üç teyit

> Tarih: 2026-10-29 · Gönderen: Nisa (community-service) → Kerem (Auth) · Durum: **GÖNDERİLECEK**
> Not: Aşağıdakiler Auth'un yapısını değiştirme isteği değildir. Community, Auth'un yayımladığı sözleşmeye uydu; burada yalnız Auth tarafında yapılacak **ayar/teyit** istenir.
> Önceki talepler ve cevapları: `../entegrasyon-v1/auth-service-talepleri/auth-requests-01..04.md`. Bu talebin dayanağı: `docs-auth/entegrasyon/community-service.md` (2026-10-29) ve `docs-auth/verilen-cevaplar/community-service/*`.

## 1. Community'de ne bitti (Auth'un bilmesi için)

| Auth'un listesindeki madde | Durum | Not |
| --- | --- | --- |
| Kapsam adı (`SCOPE_events:ingest`) | Tamam | Eski `internal.events` kaldırıldı; `POST /internal/events` yalnız `events:ingest` ister |
| Gerçek JWT doğrulayıcı | Tamam | `at+jwt`, `iss`, `aud=ltz-api`, RS256/JWKS, `exp`/`nbf`/`iat`/`sub` zorunlu, `roles` → `ROLE_` |
| Olay ucu doğrulaması | Tamam | `typ=svc+jwt`, `aud=community-service`, `scope=events:ingest`, ömür ≤ 60 sn, `sub=svc:<producer>` |
| Giden çağrılarda token | **Tamam (bu aşama)** | `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens`, hedef başına ayrı token, 60 sn; `Authorization: Bearer` |
| `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` | Tamam | `docker-compose.yml` community ortamına eklendi; `.env.example`'da zaten vardı. Boşsa relay hiçbir olay denemez |
| Silme ucu | Tamam (önceki aşama) | `DELETE /internal/v1/accounts/{accountId}`, `account:erase`, tek işlem, idempotent |
| `AccountStatusChanged` alıcısı | **Tamam (bu aşama)** | Aşağıda §2 |

## 2. `AccountStatusChanged` alıcısı

Community `POST /internal/events` ucunda şu olayı kabul eder (Auth'un kodundaki gerçek biçime göre):

```json
{ "eventId": "UUID", "eventType": "AccountStatusChanged", "eventVersion": 1, "occurredAt": "…",
  "producer": "auth-service", "aggregateId": "<accountId>",
  "payload": { "accountId": "UUID", "status": "SUSPENDED" } }
```

- Token: `svc:auth-service`, `aud=community-service`, `scope=events:ingest`. Zarf `producer` değeri token `sub`'ıyla (`svc:` + producer) eşleşmezse 403.
- `accountId` ve `status` zorunludur; eksikse 400 (Auth'un kendi yükünde ikisi de hep vardır). Bilinmeyen durum adları kabul edilir.
- `eventId` ile tekilleştirilir (ikinci teslim `DUPLICATE`, yine 200). **İçeriğe etki etmez**: askıdaki/yasaklı hesabın içeriği gizlenmez; Auth bunu zorunlu kılmıyor ve bu bir ürün kararıdır (karar gelirse ayrı bir görevle eklenir).

## 3. Auth'tan istenenler

1. **Hedef listesine `community` eklenmesi.** Alıcı uç yayında olduğunda (community'nin bu dalı birleşip dev imajı güncellenince) `ltz.auth.events.targets` içine `community` eklenir. Bundan önce eklenirse teslim 401/403/404 alır ve Auth'un geri çekilme/`stuck` mantığına boşuna takılır. **Soru:** Ekleme zamanını Kerem mi Alper mi yapıyor; community "hazır" haberini kime vermeli?
2. **Çiftlerin tanımlı olduğunun teyidi.** Auth belgesi (`community-service.md`) şu çiftleri "tanımlı" diyor: `admin-service → community`, `social-service → community`, `community-service → admin-service`, `community-service → notification-service` (hepsi `events:ingest`). **Teyit istenen:** `svc:auth-service → community-service` (`events:ingest`) çiftinin Auth'un olay göndericisinde (`HttpAuthEventSender`) bu kapsamla token üretmeye hazır olduğu. `servis-sozlesmeleri.md` §3.5 tablosunda 5. satır hâlâ "olay tanımı gelince" diyor; tablo güncellenecek mi?
3. **`community-service` istemcisinin dev'de gerçekten etkin olduğu.** Community artık `clientId=community-service` ile `POST /internal/v1/service-tokens` çağıracak (`audience` = `admin-service` veya `notification-service`). Dev'de `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` üretici betikle dolduruluyor; **soru:** bu istemci dev Auth'ta kayıtlı ve etkin mi, yanıt alanları `accessToken`/`expiresIn` (api.md kaydındaki gibi) mi? Community bilinmeyen ek alanları yok sayar.

## 4. Önceki dört talebin durumu (01–04) ve hâlâ açık olanlar

| Talep | Konu | Durum | Açık kalan |
| --- | --- | --- | --- |
| 01 | Servis token'ı (`events:ingest`), giden çağrılar için token | Cevaplandı; community tarafı tamamlandı (alıcı + giden token) | §3 madde 2 ve 3 (çift teyidi, istemci etkinliği); **gerçek Auth token'ıyla uçtan uca doğrulama yapılmadı (NOT VERIFIED)**: gelen `events:ingest` ve giden `service-tokens` |
| 02 | Anonimleştirme onayı, dev doğrulaması | Onaylandı (sabit kimlik, `[removed]`) | Rapor saklama süresi (Alper'in kararı); dev'de hesap silip `community` görevinin `CONFIRMED` olması |
| 03 | `AccountStatusChanged`, rol iptali, JWKS | Cevaplandı; rol iptali ve JWKS kapandı; alıcı yazıldı | §3 madde 1 (Auth'ta `community` hedefi eklenmesi) |
| 04 | Kerem'in cevapları ve kapsam istek listesi | Cevap belgesi; listedeki 5 çiftin community tarafı hazır | §3 madde 2 (5. satır: `auth-service → community-service` teyidi) |

Açık maddelerin ayrıntısı:

- **Dev'de hesap silme `CONFIRMED` denemesi** (`auth-requests-02/04`): şartlar (yeni Auth dev'de, `AUTH_DELETION_GRACE_PERIOD=PT0S` yalnız dev'de, community container'ı güncel imajla) ekibin tarafında; community hazır.
- **Rapor saklama süresi** (Alper'in kararı): Auth artık "kimlik bağını koparıp anonim bırak ya da sil" diyor; community anonim bırakıyor. Süre kararı gelirse ayarlanabilir temizlik eklenir, o zamana kadar süresiz.
- **`AccountDeleted` olayı:** Auth olay olarak yayınlamıyor, senkron `DELETE` kullanıyor; community'nin buna ihtiyacı yok (COM-04 raporundaki beklenti geçersiz sayıldı).

## 5. Kabul kriteri

- Auth §3'ü yanıtlar ve `community` hedefini eklediğinde dev'de bir hesabın durumu yönetici ucundan değiştirilir; community `inbox_events` tablosunda olay kaydı oluşur ve Auth'un outbox satırı teslim edilmiş görünür.
- Community relay'i (`LTZ_OUTBOX_RELAY_ENABLED=true`) açıldığında dev'de bir rapor/yorum/beğeni olayı admin/notification'a Auth token'ıyla `2xx` ile teslim edilir (alıcı uçları hazırsa).
