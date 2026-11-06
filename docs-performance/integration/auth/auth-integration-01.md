# Auth entegrasyonu 01 — LTZ-ALP-PERF-01

Güncelleme: 2026-11-06 · Durum: **KOD + KAYNAK TESTLERİ + İZOLE GERÇEK HTTP KABULÜ PASS**. Kanıt: [auth-integration-05.md](auth-integration-05.md).
Talep belgesi: [auth-requests-01.md](../auth-service-talepleri/auth-requests-01.md). Auth kaynak koduna **müdahale edilmedi**.

## 1. Dürüstlük özeti

| Alan | Gerçek mi? | Not |
| --- | --- | --- |
| Kullanıcı token'ı doğrulaması (imza, `iss`, `aud`, `typ`, ömür) | Kaynak sözleşme | Token'lar gerçek Auth **üretim kaynağından** (`AccessTokenIssuer`, `JwksController`, `JwtSigningKey`) derlenip üretildi; çalışan bir Auth servisi **yoktu** |
| Auth çıkış token'ı (`svc+jwt`, D4) | Kaynak sözleşme | `ServiceTokenIssuer`, `sub=svc:auth-service`; yalnız hesap silme |
| Hardware istemci token'ı (`svc+jwt`) | Kaynak sözleşme | Gerçek `ServiceClientTokenIssuer`, `sub=svc:hardware-service`, `devices:verify`, hedef hesaba bağlı |
| Hardware cihaz token'ı Performance'ta reddi | Kaynak sözleşme | Gerçek `DeviceTokenIssuer` çıktısı (`aud=hardware-service`) 401 |
| Performance cihaz token'ı (`aud=performance-service`) | Kaynak sözleşme | Olumlu testler gerçek `DeviceTokenIssuer.issue(device, account, audience, scope)` kullanır; `target=performance` HTTP sözleşmesine karşılık gelir |
| Auth `GET /api/v1/auth/devices` | **Mock (yerel sapma)** | Belgelenmiş yanıt şekli taklit edilir |
| Çalışan Auth servisi ile uçtan uca | Ayrı HTTP kabulü | Sonuçlar ve sınırlar [auth-integration-05.md](auth-integration-05.md) içinde; kaynak testleriyle karıştırılmaz |

Kaynak sözleşme testleri tek başına çalışan Auth entegrasyonu kanıtı değildir. Bozuk claim testleri bilinçli olarak yerel imza kullanır.

## 2. Doğrulanan sözleşme

- JWKS: `${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json`; imza RS256 + `kid`. JWKS erişilemezse fail-closed (401/503, hiçbir istek geçmez).
- Kullanıcı: `typ=at+jwt`, `iss=ltz-auth`, `aud∋ltz-api`, `sub`=hesap UUID, roller. Hesap durumu claim'i yoktur; Auth iptali en çok 90 sn gecikmeyle yansır. Yerel hesap silme çiti yeniden yazmayı engeller.
- Servis: `typ=svc+jwt`, tek `aud=performance-service`, `scope=account:erase` / `devices:verify`, `jti`, ≤ 60 sn, `sub` tam eşleşme.
- Cihaz: `typ=dev+jwt`, tek `aud=performance-service`, `scope=performance:sync:write`, `account_id`, `jti`, ≤ 60 sn.

## 3. Kerem'in yanıtı ve uygulanan kararlar

Kaynak: [Auth talep 01 yanıtının git geçmişindeki sürümü](https://github.com/alperrte/lobbytwozero/blob/f59565b54f03cc8f422dd8cf50a1da9baa1c1b43/docs-auth/verilen-cevaplar/performance-service/talep-01-cihaz-token-hedefi-iptal-ve-cihaz-dogrulama.md). Kapanmış eski cevap ve 2026-11-05 bilgilendirmesi `main` dalındaki belge temizliğinde kaldırıldı; yanıtın tarihçesi korunur. Güncel Auth giriş noktası [AUTH_REHBERI.md](../../../docs-auth/AUTH_REHBERI.md), Performance kabul kanıtı [auth-integration-06.md](auth-integration-06.md) içindedir.

| Talep | Durum | Etki |
| --- | --- | --- |
| REQ-01-A — `aud=performance-service` cihaz token'ı | AUTH HAZIR / tüketici uyumlu | Audience/scope gevşetilmedi; olumlu sync testleri Auth üreticisini kullanıyor |
| REQ-01-B — iptal yayılımı | KABUL EDİLDİ / UYGULANDI | Push/pull ucu yok. Cihaz token'ı `exp` anında biter (≤60 sn). GET cihaz listesi Auth listesini çağırır, bulunmayan aktif bağları DISABLED yapar; yerel DISABLED kendiliğinden açılmaz |
| REQ-01-C — Hardware→Performance servis token'ı | AUTH HAZIR / tüketici uyumlu | `ServiceClientTokenIssuer` doğru istemci konusunu üretir. Gerçek üretici token'ıyla 204; yanlış hesapta 403; kapalı/yok cihazda 404 |
| REQ-01-D — hesap silme işçisi doğrulaması | İZOLE GERÇEK HTTP PASS | Gerçek işçi → Performance 204, görev CONFIRMED; bütün servislerin Saga tamamlanması ve ortak DEV ayrıca bekler |
| REQ-01-E — hesap durumu gecikmesi | TEYİT / KAPANDI | Kullanıcı token'ında ≤90 sn; cihaz token'ında ≤60 sn. Durum claim'i varsayılmaz |

Auth listeleme hatası (401/5xx, bozuk veya eksik/tekrarlı UUID listesi) `503 AUTH_UNAVAILABLE` + `Retry-After: 30` döner. Yerel bağlar kısmen değiştirilmez; başka hesaplar etkilenmez. Auth HTTP çağrısı DB transaction'ı dışında, yerel mutasyon hesap kilidi altında yapılır. Migration, yeni ENV veya yeni üretim bağımlılığı eklenmedi.

**Önceki tespitin düzeltmesi:** Auth'un `ServiceTokenIssuer` sınıfı kendi çıkışları içindir. İstemciler `/internal/v1/service-tokens` üzerinden `ServiceClientTokenIssuer` kullanır; `sub` üretimi engeli yoktur. REQ-04-A'nın eksik okuma izinleri de kullanıcının bildirdiği Kerem onayıyla sonradan eklendi; [güncel kanıt](auth-integration-06.md). Hardware'in güncel token'ı `devices:verify summary:read` taşır; cihaz doğrulama denetimi `devices:verify` yetkisini arar.

## 4. Gerçek HTTP kabul senaryosu

1. İzole Auth dev profili/sentetik hesap ile cihaz bağla (`POST /api/v1/auth/devices`) → `deviceId`.
2. `POST /api/v1/performance/devices {deviceId}` → 200 ACTIVE.
3. `PUT /api/v1/performance/sync-consent` → granted.
4. Gerçek cihaz token'ı (`target=performance`) ile `POST /api/v1/performance/sync/batches` → 200; aynı anahtar → `replayed=true`.
5. Hardware hedefli token ile aynı uç → 401.
6. Auth'ta cihaz iptal → yeni token alınamaz; Performance'ta ≤ 60 sn içinde reddedilir.
7. Auth silme işçisi → `DELETE /internal/v1/accounts/{id}` → 204; sonrasında veri yok.

Çalıştırılabilir kabul betiği: `ops/performance-auth-e2e.mjs`. Kaynak testleri ile HTTP kanıtının ayrımı, gerçekten çalıştırılan adımlar ve kapsam dışında kalan gerçek masaüstü/Game/Hardware akışları [auth-integration-05.md](auth-integration-05.md) içindedir.
