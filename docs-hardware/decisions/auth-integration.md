# Auth entegrasyon sınırı — DEFERRED_AUTH

2026-10-21 kullanıcı onayıyla HW-01 ve HW-02 yerel güvenlik sınırları uygulanmıştır.

`CurrentActor` uygulama portu UUID döndürür. `SecurityContextCurrentActor`, yalnız
authenticated ve typed `AuthenticatedActor` principal'ını kabul eder. Üretimde bu
principal'ı oluşturan filtre/provider yoktur; istek header'ı, Basic, cookie veya Bearer
girdisi kimlik oluşturmaz. Testlerde SecurityMockMvcRequestPostProcessors ile yalnız
test-scope Authentication kullanılır. Test actor'ı üretim bean'i değildir.

Profil iş uçları owner/protected; katalog ve kampanya okumaları typed actor'a,
yazmaları ayrıca `hardware:catalog:write` uygulama iznine bağlıdır. Bu izin Auth rolü
veya JWT claim'i değildir; gerçek eşleme Kerem ile mutabakat bekler. Katalog yazmaları
uygulama portunda da bağımsız kontrol edilir; gerekçeli audit/rate limit uygulanır.
Diğer tüm yollar deny-by-default; mevcut health/liveness/readiness
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
