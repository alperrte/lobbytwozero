# Auth entegrasyon sınırı — DEFERRED_AUTH

2026-10-21 kullanıcı onayıyla yalnız HW-01 yerel güvenlik sınırı uygulanmıştır.

`CurrentActor` uygulama portu UUID döndürür. `SecurityContextCurrentActor`, yalnız
authenticated ve typed `AuthenticatedActor` principal'ını kabul eder. Üretimde bu
principal'ı oluşturan filtre/provider yoktur; istek header'ı, Basic, cookie veya Bearer
girdisi kimlik oluşturmaz. Testlerde SecurityMockMvcRequestPostProcessors ile yalnız
test-scope Authentication kullanılır. Test actor'ı üretim bean'i değildir.

İş uçları owner/protected, diğer tüm yollar deny-by-default; mevcut health/liveness/readiness
probe erişimi korunur. CSRF kapatılmadı; oturumdan kimlik okunmaz, form/Basic login yoktur.
Özel yanıtlar private/no-store; hata gövdeleri güvenli ProblemDetail formatındadır.
İstek gövdeleri, Content-Length bulunmasa da 16 KiB ile sınırlanır.

Kerem ile sonraki mutabakat: issuer/audience, imza algoritması ve JWKS/anahtar,
UUID claim, roller, account status/revocation, Gateway token aktarımı, HttpOnly cookie ve
CSRF protokolü, clock skew. Hiçbir değer veya rol adı tahmin edilmedi; ENV eklenmedi.
Gerçek JWT imzası/süre/issuer/audience doğrulaması ve Auth E2E **NOT VERIFIED**.
Geçersiz bearer'ın reddi, gerçek JWT validator testi olarak raporlanmaz.

Mevcut durum production-ready değildir. Gerçek Auth adapter'ı ve E2E, yeni kullanıcı
talimatı ve onaylanmış sözleşmeyle uygulanacaktır. Diğer servisler bu görevde değiştirilmedi.
