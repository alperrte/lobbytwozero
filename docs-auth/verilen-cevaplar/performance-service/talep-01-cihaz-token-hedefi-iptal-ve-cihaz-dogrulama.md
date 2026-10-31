# TALEP 01 — PERFORMANCE CİHAZ TOKEN HEDEFİ, İPTAL YAYILIMI VE CİHAZ DOĞRULAMA

**Kimden:** Alper (performance-service) · **Kime:** Kerem (auth-service) · **Kaynak:** `docs-performance/integration/auth-service-talepleri/auth-requests-01.md` (`feature/performance-service`)
**Cevap tarihi:** 2026-10-31 · **Durum:** REQ-01-A ve REQ-01-C AUTH HAZIR · REQ-01-B KARAR: kabul (iptal 60 sn) · D/E teyit

---

## Özet

| Talep | Cevap | Durum |
|---|---|---|
| **A** Cihaz token'ı `aud=performance-service`, `performance:sync:write` | Yapıldı. `POST /api/v1/auth/devices/token` gövdesine isteğe bağlı `target` eklendi. | AUTH HAZIR |
| **B** Cihaz iptalinin Performance'a bildirilmesi | **Yapılmıyor (kabul).** Gerekçe aşağıda. | KARAR VERİLDİ |
| **C** Hardware → Performance `devices:verify` | Yapıldı, hesaba bağlı token. | AUTH HAZIR |
| **D** Hesap silme (`performance` hedefi) | Auth tarafı hazır, ortak DEV doğrulaması bekliyor. | NOT VERIFIED |
| **E** Hesap durumu gecikmesi | Teyit: ban/askı en çok ~90 sn. | KAPANDI |

## A — Cihaz token'ı hedefi: kesin sözleşme

```http
POST {AUTH_SERVICE_URL}/api/v1/auth/devices/token
{ "deviceId": "<UUID>", "deviceSecret": "<anahtar>", "target": "performance" }
```

| Alan | `target` yok / `hardware` | `target=performance` |
|---|---|---|
| `typ` | `dev+jwt` | `dev+jwt` |
| `aud` | `hardware-service` | `performance-service` |
| `scope` | `hardware:sync:write` | `performance:sync:write` |
| `sub` | `<deviceId>` | aynı |
| `account_id` | hesap UUID | aynı |
| `jti`, `exp - iat` | dolu, 60 sn | aynı |

- Token yine **tek `aud`, tek kapsam** taşır; bir hedef için alınan token başka hedefte geçmez. İstemci her servis için ayrı token alır.
- `target` verilmezse davranış bugünkü ile birebir aynı (Hardware token'ı); Hardware'in doğrulayıcısı bozulmadı, `DeviceTokenIssuer.issue(UUID, UUID)` imzası korundu ve `ConsumerContractTest` ile kilitlendi.
- Tanımsız `target` (`game`, boş, büyük harf) `400`. İptal edilmiş cihaz, yanlış anahtar ve aktif olmayan hesap hedeften bağımsız aynı `401` yanıtını alır (hedef sızmaz).
- Limitler paylaşılır (bağlama sınırları cihaz üzerinedir; token almanın ek sınırı yoktur, Gateway IP sınırı geçerli).

**Performance'ın alıcı denetimi (hepsi zorunlu):** `typ=dev+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud` yalnız `performance-service`, `scope` içinde `performance:sync:write`, `sub` kanonik UUID, `account_id` UUID, dolu `jti`, `exp - iat ≤ 60`. Hardware token'ı (`aud=hardware-service`) ve kullanıcı `at+jwt`'si reddedilir.

**Bilinçli sınır (en az yetki):** bugün bir hesabın her aktif cihazı hem Hardware hem Performance için token alabilir. "Cihaz bağlanırken hangi servislere token alabileceğini seçme" (`capabilities`) bu turda yapılmadı: ek bir şema ve kullanıcı arayüzü getirir, güvenlik kazancı bugünkü iki servis için küçüktür (aynı cihaz, aynı sahibin hesabı, iki ölçüm servisi). İhtiyaç doğarsa ayrı bir migration olarak eklenir.

## B — İptal yayılımı: neden yapmıyoruz

**Karar: seçenek 3 (kabul).** Gerekçe:

1. İptal edilmiş cihaz **yeni token alamaz**; elde kalan token en çok **60 sn** yaşar. Yani cihaz kaynaklı yetki en geç 60 sn'de biter. Bu, kullanıcı token'ı için kabul ettiğimiz 90 sn sınırından da kısa.
2. Merkezi iptal bildirimi/introspection Auth'un çizgisi değil (Game, Hardware, Community, Library için aynı kararı verdik). Her servise iptal olayı göndermek, herkese iş ve Auth'a teslim yükü çıkarır; kazanç 60 sn'lik pencereyi kapatmak.
3. Performance'ın `device_links` kaydını ACTIVE tutması bir **yetki** değildir: veri yazmak için geçerli bir cihaz token'ı gerekir. Kayıt eskiyebilir ama sızıntı olmaz.

**Önerim:** Performance cihaz listesini gerektiğinde kullanıcının kendi Bearer'ıyla Auth `GET /api/v1/auth/devices` çağrısından tazeler (zaten yaptığınız açık bağlama bu). Şüpheli durumda fail-closed.

**Yeniden bakma koşulu:** 60 sn içinde bile yazılmaması gereken yüksek riskli bir Performance işlemi çıkarsa, o işlem için cihazı Auth'a senkron sormak (pull) veya Auth olayı (push) ayrıca tasarlanır. Şimdilik gereksinim yok.

## C — Hardware → Performance `devices:verify`: kesin sözleşme

```http
POST {AUTH_SERVICE_URL}/internal/v1/service-tokens
{ "clientId": "hardware-service", "clientSecret": "<AUTH_HARDWARE_SERVICE_CLIENT_SECRET>",
  "audience": "performance-service", "accountId": "<hesap UUID>" }
```

Yanıt `scope: "devices:verify"`. Token: `typ=svc+jwt`, `aud=performance-service`, `sub=svc:hardware-service`, `scope=devices:verify`, `account_id` = istekteki hesap, `jti`, 60 sn. **Hesaba bağlıdır** (`signals:read` ile aynı kural): `accountId` zorunlu, yoksa `400 SERVICE_ACCOUNT_BINDING_INVALID`; hesap yok ya da `ACTIVE` değilse `403 SERVICE_TARGET_ACCOUNT_UNAVAILABLE`.

Bu, Performance'ın tarif ettiği `account_id == yoldaki accountId` eşitliğiyle uyumludur. Her cihaz sorgusu için ilgili hesap adına token alınır (60 sn içinde aynı hesap için yeniden kullanılabilir, başka hesap için kullanılamaz).

**Düzeltme (belgenizdeki "Auth'taki mevcut engel"):** servis token'ının `sub` değerini her zaman `svc:auth-service` yapması yalnız Auth'un **kendi çıkış çağrılarındaki** `ServiceTokenIssuer` içindir (hesap silme). Servislerin `POST /internal/v1/service-tokens` ile aldığı token'da `sub=svc:<istemci>` üretilir (`svc:hardware-service` gibi); gerçek Auth token'ı bu uçta `403` almamalı. `hardware-service` → `performance-service` çifti artık tanımlı olduğu için uçtan uca denenebilir.

**Hardware tarafı:** `UnavailableHardwareSyncDevicePolicy` yerine bu token'ı alıp Performance ucunu çağıran istemci Hardware sahibinin işidir (Alper bilgilendirilmeli).

## D ve E

- **D:** Auth `ltz.auth.deletion.targets` içinde `performance` var; uç yayına girince DEV'de sentetik hesapla birlikte doğrulanır (`AUTH_DELETION_GRACE_PERIOD=PT0S`, izole). Şu an **NOT VERIFIED**.
- **E:** Kullanıcı/cihaz token'ında hesap durumu claim'i yok; ban/askı kullanıcı token'ında en çok ~90 sn, cihaz token'ında 60 sn gecikmeyle yansır. Sınır geçerli, Performance'ın fail-closed yaklaşımı doğru.

## Kimin ne yapacağı

| Sahip | İş |
|---|---|
| **Auth (Kerem)** | Yapıldı: `target`, `devices:verify`, testler, belgeler. Ortak DEV silme doğrulaması. |
| **Performance (Alper)** | Cihaz token'ı alıcı denetimleri (yukarıdaki), `device_links` tazeleme kuralı, REQ-01-D için ortak DEV denemesi. |
| **Hardware** | `devices:verify` token'ıyla Performance cihaz doğrulama istemcisi; `AUTH_HARDWARE_SERVICE_CLIENT_SECRET` zaten Auth'ta tanımlı, aynı değer Hardware ortamında olmalı. |
| **Masaüstü istemcisi** | Her servis için ayrı `target` ile token almak. |

## Test durumu

| Seviye | Durum |
|---|---|
| Auth: `DeviceCredentialTest` (hedef seçimi, tanımsız hedef, iptal), `ServiceAudienceTokenTest`, `ConsumerContractTest` | PASS |
| Auth smoke (gerçek geçici yığın) | bkz. commit mesajı |
| Performance ↔ gerçek Auth token'ıyla uçtan uca | NOT VERIFIED |
