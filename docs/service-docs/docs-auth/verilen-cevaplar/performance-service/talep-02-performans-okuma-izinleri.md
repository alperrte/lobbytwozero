# TALEP 02 — Performance Request 04 okuma izinleri uygulama kaydı

Tarih: 2026-11-06 · Talep sahibi/uygulayan: Alper · Auth sahibi: Kerem.

**Durum: KAPANDI (Auth izinleri).** Auth 537 + Performance 138 test, 15 gerçek HTTP kabul kontrolü PASS. Üretim Hardware/Recommendation tüketici istemcileri ayrı iştir.

Kullanıcı, Kerem'den gerekli izni aldığını belirterek bu değişikliği açıkça yetkilendirdi. Bu belge uygulama kaydıdır; Kerem adına verilmiş bir cevap değildir.

Kaynak talep: [auth-requests-04.md](../../../docs-performance/integration/auth-service-talepleri/auth-requests-04.md), REQ-04-A. Güncel test ve gerçek HTTP kabul sonucu: [auth-integration-06.md](../../../docs-performance/integration/auth/auth-integration-06.md).

## Uygulanan sözleşme

Mevcut `POST /internal/v1/service-tokens` kullanılır. `audience=performance-service` ve hedef `accountId` zorunludur; hesap yok/pasifse `403 SERVICE_TARGET_ACCOUNT_UNAVAILABLE`, hesap eksikse `400 SERVICE_ACCOUNT_BINDING_INVALID` döner. Yanlış/devre dışı istemci veya yanlış sır `401 INVALID_CLIENT` alır.

| İstemci | `sub` | Verilen kapsamlar |
| --- | --- | --- |
| `hardware-service` | `svc:hardware-service` | `devices:verify summary:read` |
| `recommendation-service` | `svc:recommendation-service` | `reference:read` |

İki token da `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, tek `aud=performance-service`, `account_id`, `jti`, `iat/nbf/exp` ve 60 sn ömür taşır. Auth mevcut davranışıyla hedefe tanımlı bütün kapsamları aynı token'a yazar; yeni istek alanı eklenmedi. Kullanıcı rolü taşınmaz.

Hardware özetini Recommendation okuyamaz; Hardware FPS referansı okuyamaz. Recommendation cihaz doğrulayamaz. İki istemci de hesap silemez ve token'ını başka hesap için kullanamaz. Performance alıcı denetimleri korunmuştur.

## Dosyalar

- Auth `TokenClaims.java`: iki kapsam sabiti.
- Auth `ServiceClientConfig.java`: bilinen ve hesaba bağlı hedef kapsamları.
- Auth `application.properties`: Hardware hedefi genişletildi, Recommendation hedefi eklendi.
- Auth `ServiceAudienceTokenTest` ve `ServiceClientConfigTest`: olumlu/olumsuz kabul.
- Performance kaynak sözleşme testleri ve `ops/performance-auth-e2e.mjs`: iki gerçek Auth istemci token'ıyla doğrulama.
- `docs/api.md` ve `docs-auth/servis-sozlesmeleri.md`: güncel kapsam/hesap bağı kaydı.

## ENV ve kapsam

Yeni ENV/migration/bağımlılık gerekmez. Mevcut `AUTH_HARDWARE_SERVICE_CLIENT_SECRET` ve `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET` adları hem `.env.example` hem Auth Compose aktarımında zaten tanımlıdır. Gerçek `.env` dosyası okunmadı/değiştirilmedi; mevcut değerlerin dolu olduğu doğrulanmış sayılmaz. Boş sır, mevcut fail-closed davranışla istemciyi kapatır.

Hardware ve Recommendation üretim tüketici istemcileri ayrı entegrasyon işidir. Bu görev Auth izinlerini ve Performance'ın gerçek token kabulünü kapsar.
