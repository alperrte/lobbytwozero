# Auth Service Talepleri — Faz 1 (LTZ-ALP-PERF-01)

| Alan | Değer |
| --- | --- |
| Talep eden | Alper (performance-service) |
| Talep edilen | Kerem (auth-service sahibi) |
| Tarih | 2026-10-31 |
| Faz / Görev | LTZ-ALP-PERF-01 — PERF-01, PERF-02, PERF-17, PERF-18, PERF-22 |
| Karar | A + C: cihaz kimliği Auth'ta kalır; Performance ikinci bir eşleştirme/token/iptal sistemi kurmaz |
| Auth koduna müdahale | **Yapılmadı.** Bu belge yalnız talep ve doğrulama kanıtıdır. |

Durum değerleri: `EXISTS` (Auth'ta var, koddan doğrulandı) · `REQUESTED` (Auth'tan istendi) · `BLOCKED` (talep yanıtlanmadan Performance tarafı kapanamaz) · `VERIFIED` (gerçek Auth ile doğrulandı).
Şu an hiçbir madde `VERIFIED` değildir; Performance tarafında gerçek Auth ile uçtan uca doğrulama yapılmadı.
**Güncel durum (Faz 1 kodu tamamlandı, 2026-10-31):** Performance tarafı uygulandı ve test edildi (bkz. [phase-01-tests.md](../../testings/phase-01-tests.md)). Cihaz sahipliği, çağıranın Bearer'ı Auth `GET /api/v1/auth/devices` çağrısına iletilerek **açık bağlama (explicit link)** ile doğrulanıyor; Auth hata verirse fail-closed `503`. REQ-01-A ve REQ-01-C Auth değişikliği olmadan kapanamaz (`BLOCKED`).

## 1. Özet (Kerem için)

Performance servisi, masaüstü istemcisinden gelecek ölçüm senkronizasyonunu Auth'un **mevcut cihaz kimliği altyapısı** üzerinden kabul edecek. Mevcut yapıda cihaz token'ı yalnız `hardware-service` için üretiliyor
(`aud=hardware-service`, `scope=hardware:sync:write`). Performance'ın bu token'ı kabul etmesi, audience doğrulamasını gevşetmek demektir; bunu yapmayacağız.
Bu yüzden Auth'tan **servis özel** bir audience/scope çifti isteniyor. İstenen toplam iş üç parçadır:

| # | Talep | Öncelik | Performance'ı engeller mi? |
| --- | --- | --- | --- |
| REQ-01-A | Cihaz token'ı için `aud=performance-service` + `scope=performance:sync:write` | Zorunlu | Evet — gerçek cihaz senkronizasyonu E2E |
| REQ-01-B | Cihaz iptalinin Performance'a bildirilmesi (60 sn TTL'e bırakılmaması) | Yüksek | Kısmen — güvenlik süresi 60 sn'ye kadar uzar |
| REQ-01-C | Hardware→Performance servis token'ı için `devices:verify` kapsamı | Yüksek | Hardware'in aktif cihaz politikası için |
| REQ-01-D | Hesap silme çağrısı (`DELETE /internal/v1/accounts/{id}`) doğrulaması | Bilgi / doğrulama | Hayır — Auth tarafı zaten var |
| REQ-01-E | Hesap durum (ban/askı) bilgisi için sözleşme netleştirmesi | Bilgi | Hayır |

## 2. Doğrulanmış mevcut durum (Auth kaynak kodundan)

Aşağıdakiler Auth kodu **okunarak** doğrulandı (değişiklik yapılmadı). Kanıt dosyaları Auth sahibinin kontrolü için verilmiştir.

| Konu | Mevcut davranış | Durum |
| --- | --- | --- |
| Kullanıcı token'ı | `typ=at+jwt`, RS256, `iss=ltz-auth`, `aud` içinde `ltz-api`, `sub`=hesap UUID, `roles` claim'i, 60 sn ömür | EXISTS |
| JWKS | `${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json` | EXISTS |
| Cihaz bağlama | `POST /api/v1/auth/devices` (parola, etkinse TOTP) → `deviceId` + bir kez gösterilen `deviceSecret` | EXISTS |
| Cihaz token'ı | `POST /api/v1/auth/devices/token` `{deviceId, deviceSecret}` → `typ=dev+jwt`, `sub=<deviceId>`, `account_id`, `jti`, 60 sn, `kid` başlığı | EXISTS |
| Cihaz token audience/scope | `aud=hardware-service`, `scope=hardware:sync:write` — `TokenClaims` içinde sabit (`HARDWARE_SERVICE_AUDIENCE`, `HARDWARE_SYNC_WRITE_SCOPE`) | EXISTS (yalnız Hardware) |
| Cihaz listeleme / iptal | `GET /api/v1/auth/devices`, `DELETE /api/v1/auth/devices/{deviceId}` (idempotent 204, başkasının cihazı için 404) | EXISTS |
| Limitler | Hesap başına 5 aktif cihaz, günde 10 bağlama, kimlik bilgisi 365 gün | EXISTS |
| Otomatik iptal | Parola değişimi/sıfırlama, hesap ACTIVE değil, silme onayı, e-posta değişimi | EXISTS |
| Servislere iptal bildirimi | Yok. İptal yeni token'ı hemen durdurur; mevcut token ≤60 sn geçerli kalır. Servis için introspection/olay yok. | YOK → REQ-01-B |
| Servis token'ı | `POST /internal/v1/service-tokens` (`svc+jwt`) yalnız `events:ingest` kapsamı, yalnız birkaç istemci çifti için | EXISTS (kısıtlı) → REQ-01-C |
| Hesap silme | `ltz.auth.deletion.targets` içinde `performance` var; Auth `DELETE /internal/v1/accounts/{accountId}` çağırır (D4 `svc+jwt`) | EXISTS |
| Hesap durumu claim'i | Token'da yok; ban/askı en çok ~90 sn gecikmeyle yansır | EXISTS (belgelenmiş sınır) |

Kanıt dosyaları: `DeviceTokenIssuer.java`, `DeviceCredentialService.java`, `DeviceProperties.java`, `TokenClaims`, `application.properties` (deletion targets), `docs/api.md` Auth cihaz sözleşmesi, `docs-auth/entegrasyon/performance-service.md`, `docs-auth/.../cihaz-kimligi-tasarimi.md`.

## 3. Talepler

### REQ-01-A — Performance için cihaz token'ı (ZORUNLU) — `REQUESTED` / `BLOCKED`

**Gerekçe.** Performance, cihaz token'ında `aud=performance-service` ve `scope=performance:sync:write` arayacak. Hardware için üretilmiş bir token'ı kabul etmek (çoklu audience veya audience gevşetme) token'ın başka servise kaydırılabilmesine (confused deputy) yol açar. Servis başına tek audience + tek scope tercih edilir.

**İstenen sözleşme (öneri; son karar Auth sahibinindir):**

- `POST /api/v1/auth/devices/token` isteğine **opsiyonel** `target` alanı eklenmesi: `hardware` (varsayılan, geriye dönük uyumlu) | `performance`.
- Üretilen token yine tek `aud`, tek `scope` taşır:

| Alan | `target=hardware` (mevcut) | `target=performance` (istenen) |
| --- | --- | --- |
| `typ` | `dev+jwt` | `dev+jwt` |
| `aud` | `hardware-service` | `performance-service` |
| `scope` | `hardware:sync:write` | `performance:sync:write` |
| `sub` | `<deviceId>` (kanonik UUID) | aynı |
| `account_id` | hesap UUID | aynı |
| `jti` | zorunlu | zorunlu |
| `exp − iat` | ≤ 60 sn | ≤ 60 sn |
| imza / başlık | RS256, `kid` | aynı |

- Tek token'a iki audience konmaması istenir. İstemci her servis için ayrı token alır.
- **Opsiyonel (en az yetki):** Bağlama sırasında cihazın hangi hedeflere token alabileceğinin seçilmesi/saklanması (ör. `capabilities=[hardware, performance]`). Mevcut cihazlar için varsayılan yalnız `hardware` olabilir; Performance kullanımı kullanıcı onayıyla açılır. Bu madde Performance'ı engellemez, tasarım tercihine bırakıldı.
- Limit/rate-limit: mevcut cihaz token'ı limitleri `target` bazında ayrılabilir veya paylaşılabilir; Auth karar versin.

**Kabul kriterleri (Auth tarafı):**

1. Geçerli `deviceId` + `deviceSecret` ve `target=performance` ile alınan token'da `aud` yalnız `performance-service`, `scope` yalnız `performance:sync:write` olur.
2. `target` verilmezse davranış bugünküyle birebir aynıdır (Hardware token'ı).
3. İptal edilmiş cihaz `target=performance` ile token alamaz (mevcut iptal kuralları geçerli).
4. Bilinmeyen `target` değeri 400 (ProblemDetail) döner.
5. `docs/api.md` Auth bölümü güncellenir; Hardware'in token doğrulaması bozulmaz.

**Performance tarafı doğrulaması (Auth tamamlanınca):** gerçek bağlama → gerçek token → `POST /api/v1/performance/sync/batches` → 201; Hardware token'ıyla aynı uç → 401/403. Bu doğrulama yapılana dek PERF-01/02/17 `BLOCKED` kalır.

### REQ-01-B — Cihaz iptalinin yayılımı — `REQUESTED`

**Sorun.** Cihaz iptal edilince (kullanıcı, parola değişimi, hesap durumu, silme, e-posta değişimi) Performance bunu öğrenemiyor; yalnız token ömrü (≤60 sn) güvenlik sağlıyor ve Performance kendi cihaz referansını (`device_links`) ACTIVE tutmaya devam eder. Hardware'in "aktif cihaz" kararı için de aynısı geçerli.

**Önerilen seçenekler (Auth sahibi seçer):**

| Seçenek | Açıklama | Not |
| --- | --- | --- |
| 1 — Push (önerilen) | Auth, iptalde `DELETE/POST /internal/v1/devices/{deviceId}/revoked` benzeri bir iç uca `svc+jwt` (`aud=performance-service`, `sub=svc:auth-service`, yeni scope örn. `devices:revoke`) ile outbox/yeniden deneme üzerinden bildirir | Hesap kaynaklı toplu iptalleri de kapsamalı |
| 2 — Pull | Auth, servislerin sorabileceği iç uç sağlar: `GET /internal/v1/devices/{deviceId}/status` → `{active, accountId}` | Performance çağrıda fail-closed davranır |
| 3 — Kabul | Yalnız 60 sn TTL; Performance yeni token yoksa senkronizasyonu reddeder | Mevcut durum, belgelenmiş risk |

**Performance tarafı (güncel):** Faz 1'de iptal bildirimi alan bir uç **yazılmadı** (sözleşme yok; uç uydurulmadı). Bildirim/pull sözleşmesi Auth tarafından belirlendiğinde Performance ilgili `device_links` kaydını `DISABLED` yapacak şekilde (idempotent) genişletilecektir. Şimdilik hafifletme: cihaz token'ı ≤60 sn ve iptal edilen cihaz yeni token alamaz; kullanıcı `DELETE /api/v1/performance/devices/{linkId}` ile bağlantıyı anında devre dışı bırakabilir. Bu sınır KARAR-08'de kabul edilen risk olarak kayıtlıdır (bkz. [phase-01-decisions.md](../../decisions/phase-01-decisions.md)).

**Kabul kriterleri:** iptal sonrası ≤ birkaç saniyede Performance'ta cihaz DISABLED; olay tekrarı idempotent; hesap düzeyi iptaller (parola/e-posta/durum/silme) cihaz bazında yayılır veya hesap bazlı tek olayla gelir; olay payload'ında kişisel veri yok (yalnız `deviceId`, `accountId`, `revokedAt`, neden kodu).

### REQ-01-C — Hardware → Performance servis token'ı — `REQUESTED`

**Sorun.** Hardware'in `HardwareSyncDevicePolicy.isActiveOwnedDevice(userId, deviceId)` kararı Performance'tan gelecek (Auth tasarım notu: "Auth kaydı cihazın kimliğini, Performance cihazın çalıştığını söyler"). Auth `POST /internal/v1/service-tokens` bugün yalnız `events:ingest` ve birkaç çift için token verir; `hardware-service → performance-service` çifti ve `devices:verify` scope'u yoktur.

**İstenen:**

- İstemci: `hardware-service`; hedef: `performance-service`; scope: `devices:verify`.
- Token: `typ=svc+jwt`, `aud=performance-service`, `sub=svc:hardware-service`, `jti` zorunlu, ≤60 sn, tek scope.
- Performance'taki uç (**uygulandı**): `GET /internal/v1/accounts/{accountId}/devices/{deviceId}` → `204` (hesaba ait ve aktif) veya `404` (yok/yabancı/devre dışı; bilgi sızdırılmaz). `account_id` claim'i path ile eşleşmelidir. Uç gateway'den dışarı açılmaz. Sözleşme: `docs/api.md` → PERFORMANCE.
- **Auth'taki mevcut engel (koddan doğrulandı):** servis token üreticisi `sub` değerini her zaman `svc:auth-service` yapıyor; Performance ucu `sub=svc:hardware-service` şart koşar. Bu yüzden gerçek Auth servis token'ı bu uçta `403` alır (testle kanıtlandı). Auth'un `sub=svc:<istemci>` üretmesi ve `devices:verify` çiftini tanıması gerekir.

**Not (Hardware kodu):** Hardware'de `UnavailableHardwareSyncDevicePolicy` şu an her zaman `false` döner. Performance'ın ucu hazır olduğunda Hardware tarafında bir istemci yazılması gerekir. Bu **Hardware sahibine ayrı bir değişikliktir**; Alper'e ve Hardware sahibine önceden bildirilmeden yapılmayacaktır. Bu talep yalnız Auth tarafındaki token izni içindir.

### REQ-01-D — Hesap silme sözleşmesi (doğrulama) — `EXISTS`, Performance tarafı hazırlanıyor

Auth `ltz.auth.deletion.targets` içinde `performance` hedefi tanımlı (`${PERFORMANCE_SERVICE_URL:http://performance-service:8080}`). Performance, bu çağrıyı D4 sözleşmesine göre karşılayacak:

- `DELETE /internal/v1/accounts/{accountId}`; `svc+jwt`, **tek** `aud=performance-service`, `sub=svc:auth-service` (tam eşleşme), `scope=account:erase`, `account_id` = path, `jti`, ≤60 sn.
- Başarıda `204` (idempotent); temizlik bitmemişse `503` + `Retry-After`.
- Bu uç gateway'den açılmaz.

**Kerem'den beklenen:** Uç yayına girdiğinde Auth tarafında bekleyen `performance` silme görevinin ilerlediğinin DEV ortamında birlikte doğrulanması. (Performance bu doğrulamayı tek başına yapamaz → `NOT VERIFIED`.)

### REQ-01-E — Hesap durumu (bilgi) — `EXISTS`

Kullanıcı/cihaz token'ında hesap durumu claim'i yok; ban/askı en çok ~90 sn gecikmeyle yansır. Performance bu sınırı kabul edecek ve **fail-closed** davranacak (şüpheli durumda reddet). Bir değişiklik istenmiyor; sınırın hâlâ geçerli olduğunun teyidi yeterlidir.

## 4. Performance'ın yapmadıkları (kapsam sınırı)

- Cihaz eşleştirme kodu, cihaz secret'ı, cihaz token'ı üretimi veya cihaz iptali **yapılmaz** (Auth'tadır).
- Auth veritabanına erişilmez.
- Audience/scope doğrulaması gevşetilmez; Hardware token'ı Performance'ta **reddedilir**.
- Hiçbir mock Auth veya sahte JWT "gerçek entegrasyon" olarak raporlanmaz; sözleşme testleri açıkça "sözleşme testi" etiketlidir.

## 5. Performance'ın Auth'tan kullandığı mevcut sözleşme

| Kullanım | Sözleşme |
| --- | --- |
| Kullanıcı uçları | `Authorization: Bearer <at+jwt>`; JWKS ile imza, `iss=ltz-auth`, `aud∋ltz-api`, `sub`=hesap UUID, `roles` |
| Cihaz uçları | `Bearer <dev+jwt>`, `aud=performance-service`, `scope=performance:sync:write` (REQ-01-A sonrası) |
| Servis uçları | `Bearer <svc+jwt>`, `aud=performance-service`, `sub=svc:<istemci>`, `jti`, tek scope |
| Yapılandırma | Mevcut ENV adları: `AUTH_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` — **yeni ENV anahtarı eklenmedi** |

## 6. Engellenen işlemler ve geçici davranış

| İşlem | Durum | Geçici davranış |
| --- | --- | --- |
| Gerçek cihaz token'ıyla senkronizasyon (PERF-01/17) | BLOCKED (REQ-01-A) | Üretimde Hardware token'ı reddedilir; E2E yapılamaz |
| Anlık iptal yayılımı (PERF-02) | REQUESTED (REQ-01-B) | ≤60 sn TTL + kullanıcı DISABLED işlemi |
| Hardware aktif cihaz kararı | BLOCKED (REQ-01-C) | Performance ucu hazır (yerel imzalı token ile test edildi); Hardware tarafı bugünkü gibi fail-closed, Hardware kodu değiştirilmedi |
| Silme işçisi E2E (PERF-22) | NOT VERIFIED (REQ-01-D) | Uç ve testler hazır; ortak ortam doğrulaması yok |
| Cihaz bağlama E2E (PERF-01) | NOT VERIFIED | Auth cihaz listesi yerel sapmayla test edildi; gerçek Auth DEV ortamıyla doğrulama yok |

## 7. Yanıt formatı (Kerem için)

Her talep için lütfen şunu yazın: `Kabul / Kısmen / Reddedildi`, planlanan sözleşme (alan adları), tahmini tarih, geriye dönük uyumluluk etkisi. Cevap geldiğinde `auth-integration-01.md` güncellenecek ve gerçek doğrulama sonuçları oraya yazılacaktır.
