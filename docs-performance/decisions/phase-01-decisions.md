# Faz 01 kararları — LTZ-ALP-PERF-01

Tarih: 2026-10-31 · Sorumlu: Alper · Servis: `performance-service`

## KARAR-01 — A + C: cihaz kimliği Auth'ta kalır (kullanıcı onaylı)

- Eşleştirme, cihaz secret'ı, cihaz token'ı üretimi ve iptal **Auth Service'te** kalır. Performance ikinci bir eşleştirme/token/iptal sistemi kurmaz.
- Performance yalnız: cihaz↔performans ilişkisi (`device_links`), senkronizasyon izni (`sync_consents`), tekrarsız kabul makbuzu (`sync_batches`), gizlilik ve silme.
- Auth DB'sine erişilmez. Sahiplik/etkinlik Auth'un belgelenmiş sözleşmesiyle doğrulanır.
- Planlanan `devices` ve `device_pairing_codes` tabloları **kurulmadı** (database.md güncellendi).
- Alternatifler: B (Performance'ta ayrı eşleştirme) — iki kaynak kimlik, iptal tutarsızlığı, ek saldırı yüzeyi nedeniyle reddedildi.

## KARAR-02 — Cihaz sahipliği: çağıranın Bearer'ı Auth'a iletilir

- `POST /devices`: çağıranın kullanıcı token'ı ile Auth `GET /api/v1/auth/devices` çağrılır; `deviceId` bu listede yoksa `403 DEVICE_NOT_LINKED`.
- Neden: Auth'ta cihaz doğrulama için servisler arası uç yok (REQ-01-B pull seçeneği istendi). Kullanıcının kendi listesi, başkasının cihazını bağlamayı yapısal olarak engeller.
- Auth erişilemez/bozuk yanıt → fail-closed `503 AUTH_UNAVAILABLE` + `Retry-After: 30`; hiçbir zaman "izin ver" yoluna düşmez.
- Auth çağrısı **DB transaction'ı dışında** yapılır (Hikari havuzu 3; yavaş Auth havuzu tüketmesin).
- Risk: Bearer iletimi kullanıcı token'ını Auth'a (aynı güven alanı, ENV'den gelen sabit URL) taşır. URL doğrulaması: yalnız http/https, userinfo/path/query/fragment yok.

## KARAR-03 — Cihaz token'ı: servis özel audience, gevşetme yok

- Senkronizasyon ucu yalnız `typ=dev+jwt`, `iss=ltz-auth`, **tek** `aud=performance-service`, `scope=performance:sync:write`, `sub`=UUID, `account_id`, `jti`, ömür ≤ 60 sn, RS256 + JWKS `kid` kabul eder.
- Hardware için üretilmiş (`aud=hardware-service`) token **reddedilir** (test: gerçek Auth kaynak kodundan üretilmiş token 401).
- Çoklu audience, scope gevşetme veya imza bypass'ı yoktur.
- Sonuç: Auth REQ-01-A yapılana dek gerçek cihaz token'ıyla senkronizasyon E2E **BLOCKED**.

## KARAR-04 — Üç ayrı kimlik doğrulama yöneticisi, varsayılan reddet

- Kullanıcı (`at+jwt`, `aud∋ltz-api`), servis (`svc+jwt`, yalnız `/internal/**`), cihaz (`dev+jwt`, yalnız `POST /sync/batches`). Token türleri uçlar arası geçmez.
- `anyRequest().denyAll()`; sağlık uçları dışında `permitAll` yok.
- CSRF filtresi açık bırakıldı (Bearer istekleri muaf). Bearer'sız yazma isteklerinde CSRF reddi **401 `AUTHENTICATION_REQUIRED`** olarak döner: cookie/oturum desteklenmediği için bu bir kimlik doğrulama eksikliğidir (403 değil).

## KARAR-05 — Senkronizasyon akışı ve idempotency

1. Hız sınırı → hesap kilidi/aktiflik → açık izin (yoksa `403 SYNC_CONSENT_REQUIRED`) → bağlı ve aktif cihaz (yoksa `403 DEVICE_NOT_LINKED`) → `clientSentAt` aralığı −24 sa..+5 dk (`422 SYNC_CLIENT_TIME_INVALID`) → idempotency.
2. `UNIQUE(device_link_id, idempotency_key)` + `request_hash` (SHA-256, kanonik biçim): aynı anahtar + aynı içerik = tekrar (200, `replayed=true`); farklı içerik = `409 IDEMPOTENCY_KEY_REUSED`; eşzamanlı aynı anahtar tek kayıt.
3. Cihaz başına en çok 500 canlı makbuz (`409 SYNC_BATCH_LIMIT_REACHED`); makbuz 24 saat sonra silinir.
4. Faz 1 gövdesi yalnız zarf (`schemaVersion=1`, `clientSentAt`, `sampleCount` 0..500); örnek içeriği Faz 3. Bilinmeyen alanlar reddedilir.

## KARAR-06 — Limitler

Hesap başına en çok 5 aktif cihaz bağlantısı (Auth limitiyle aynı); aynı Auth cihazı başka hesapta bağlıysa `409 DEVICE_LINK_CONFLICT`. İstek gövdesi üst sınırı filtre düzeyinde (413). Yazma hız sınırı PostgreSQL penceresiyle (çok örnekli güvenli).

## KARAR-07 — Hesap silme (D4) ve yazma çiti

`DELETE /internal/v1/accounts/{id}` → `erase_performance_account(uuid)` (V21) tek geçişte siler; tamamlanmadıysa `503 ACCOUNT_ERASURE_PENDING` (Auth yeniden dener, silme asla yanlışlıkla onaylanmaz). Başarılı silme sonrası düz hesap kimliği tutulmadan (SHA-256) çit kurulur; çit süresince bu hesap için yazma engellenir. Uç idempotenttir.

## KARAR-08 — Cihaz iptali yayılımı (kabul edilen risk)

Auth iptali Performance'a bildirilmez (REQ-01-B). Hafifletme: cihaz token'ı ≤ 60 sn; iptal edilen cihaz yeni token alamaz; kullanıcı `DELETE /devices/{linkId}` ile Performance bağlantısını anında devre dışı bırakabilir. **Risk:** Auth'ta iptal edilmiş bir cihazın Performance bağlantısı kullanıcı kaldırana veya Auth olay/pull ucu gelene dek `ACTIVE` görünebilir; yeni token alınamadığı için senkronizasyon en çok 60 sn devam edebilir.

## KARAR-09 — Hardware → Performance aktif cihaz ucu

`GET /internal/v1/accounts/{accountId}/devices/{deviceId}` (204/404) yalnız `sub=svc:hardware-service`, `scope=devices:verify` ile açıldı. Auth bu çifti üretmediği için gerçek token yok → **BLOCKED (REQ-01-C)**. Hardware kodu **değiştirilmedi**; istemci yalnız öneridir ve Hardware sahibine önceden bildirilmelidir.

## KARAR-10 — Bağımlılık ve ortam

- Yeni kütüphane yok. Test kapsamında `spring-boot-starter-webmvc-test` ve `testcontainers-postgresql` (Hardware'de zaten kullanılıyor) eklendi.
- Yeni ENV anahtarı yok; mevcut `AUTH_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` kullanılır. Gerçek `.env` dosyaları okunmadı.
- Boş `.gitkeep` dosyaları (artık içi dolu paketlerde) kaldırıldı.

## KARAR-11 — Test stratejisi ve dürüstlük sınırı

- `ProductionAuthSupport`: gerçek Auth üretim kaynaklarını derleyip JWKS ve token üretir → **kaynak sözleşme testi**, E2E değil.
- `PerformanceTestAuth`: yerel JWKS + `GET /api/v1/auth/devices` **sapması** (mock). Performance-audience cihaz token'ı **yerel imzalıdır**.
- Hiçbiri "gerçek Auth entegrasyonu" olarak raporlanmaz.
