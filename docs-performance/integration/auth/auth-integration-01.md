# Auth entegrasyonu 01 — LTZ-ALP-PERF-01

Tarih: 2026-10-31 · Durum: **PARTIAL / BLOCKED** — gerçek Auth cihaz token'ıyla uçtan uca doğrulama yapılmadı.
Talep belgesi: [auth-requests-01.md](../auth-service-talepleri/auth-requests-01.md). Auth kaynak koduna **müdahale edilmedi**.

## 1. Dürüstlük özeti

| Alan | Gerçek mi? | Not |
| --- | --- | --- |
| Kullanıcı token'ı doğrulaması (imza, `iss`, `aud`, `typ`, ömür) | Kaynak sözleşme | Token'lar gerçek Auth **üretim kaynağından** (`AccessTokenIssuer`, `JwksController`, `JwtSigningKey`) derlenip üretildi; çalışan bir Auth servisi **yoktu** |
| Servis token'ı (`svc+jwt`) | Kaynak sözleşme | Gerçek `ServiceTokenIssuer` ile üretildi; `sub=svc:auth-service` |
| Hardware cihaz token'ı Performance'ta reddi | Kaynak sözleşme | Gerçek `DeviceTokenIssuer` çıktısı (`aud=hardware-service`) 401 |
| Performance cihaz token'ı (`aud=performance-service`) | **Yerel imzalı** | Auth henüz üretmiyor (REQ-01-A); test anahtarı gerçek test JWKS ile eşleşir |
| Auth `GET /api/v1/auth/devices` | **Mock (yerel sapma)** | Belgelenmiş yanıt şekli taklit edilir |
| Çalışan Auth servisi ile uçtan uca | **YOK** | `NOT VERIFIED` |

Bu testlerin hiçbiri "gerçek Auth entegrasyonu tamamlandı" anlamına gelmez.

## 2. Doğrulanan sözleşme

- JWKS: `${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json`; imza RS256 + `kid`. JWKS erişilemezse fail-closed (401/503, hiçbir istek geçmez).
- Kullanıcı: `typ=at+jwt`, `iss=ltz-auth`, `aud∋ltz-api`, `sub`=hesap UUID, roller; hesap pasif/silinmiş durum uygulama kuralıyla reddedilir.
- Servis: `typ=svc+jwt`, tek `aud=performance-service`, `scope=account:erase` / `devices:verify`, `jti`, ≤ 60 sn, `sub` tam eşleşme.
- Cihaz: `typ=dev+jwt`, tek `aud=performance-service`, `scope=performance:sync:write`, `account_id`, `jti`, ≤ 60 sn.

## 3. Engeller ve talepler

| Talep | Durum | Etki |
| --- | --- | --- |
| REQ-01-A — `aud=performance-service` cihaz token'ı | BLOCKED | PERF-01/17 gerçek cihazla E2E yapılamaz |
| REQ-01-B — iptal yayılımı | REQUESTED | PERF-02: ≤60 sn pencere + kullanıcı DISABLED; kabul edilen risk (KARAR-08) |
| REQ-01-C — Hardware→Performance servis token'ı | BLOCKED | Hardware aktif cihaz kararı gerçek token ile alınamaz |
| REQ-01-D — hesap silme işçisi doğrulaması | NOT VERIFIED | Uç ve testler hazır; ortak ortam yok |
| REQ-01-E — hesap durumu gecikmesi | EXISTS | Fail-closed kabul edildi |

## 4. Auth tamamlanınca yapılacak doğrulama (kabul)

1. Auth DEV'de gerçek hesap ile cihaz bağla (`POST /api/v1/auth/devices`) → `deviceId`.
2. `POST /api/v1/performance/devices {deviceId}` → 200 ACTIVE.
3. `PUT /api/v1/performance/sync-consent` → granted.
4. Gerçek cihaz token'ı (`target=performance`) ile `POST /api/v1/performance/sync/batches` → 200; aynı anahtar → `replayed=true`.
5. Hardware hedefli token ile aynı uç → 401.
6. Auth'ta cihaz iptal → yeni token alınamaz; Performance'ta ≤ 60 sn içinde reddedilir.
7. Auth silme işçisi → `DELETE /internal/v1/accounts/{id}` → 204; sonrasında veri yok.

Bu adımlar yapılıp sonuçlar buraya yazılana dek Faz 1 **kapatılmaz**.
