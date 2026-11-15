# recommendation-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Alper.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

### TASK 2 güncel durum — 2026-11-14

- JWT/JWKS kullanıcı ve D4 doğrulaması, canonical role, ProblemDetail/no-store, body ve oran
  sınırları uygulanır. Varsayılan in-memory kullanıcı/Basic/form fallback yoktur.
- User rızası ve Auth account-bound ACTIVE grant kişisel işlemlerde kontrol edilir; session
  revocation için online introspection yoktur, 60 sn access TTL + 30 sn tolerans geçerlidir.
- V12 owner history/feedback; cursor, UNIQUE/version/account lock, 30 gün/500 run retention,
  consent purge ve D4 cascade testlidir. Gerçek kaynak servisler ve Auth worker E2E NOT VERIFIED.
- REC-02/03/05 sözleşme eksikleri, REC-16 sistem filtresi kısmi durumdadır; Task 2 bütünü PARTIAL.
  [Güncel checkpoint](../../docs-recommendation/progress/task-02.md).
- 45 test PASS; toplam LINE %96,83, merkezi ek/değişen kod %97,15; Checkstyle/PMD 0 ihlal,
  dependency gate PASS. [Komut/kanıt](../../docs-recommendation/testings/task-02-tests.md).

### TASK 1 tarihsel notu

- 2026-11-14 / TASK 1: servis içi sürümlü motor ve V11 motor analitiği temeli eklendi;
  iş HTTP ucu ve Recommendation'ın Auth/downstream tüketici adaptörleri henüz yoktur.
  Başlangıç 401/CSRF 403 testleri gerçek JWT/RBAC/IDOR kabulü değildir.
- DB kullanan testler `DisposableDatabaseInitializer` ile yalnız `-Pci` disposable
  PostgreSQL'e bağlanır. Gerçek DEV/production bağlantısına geri dönüş yoktur.
- Son doğrulama: 29 test, JaCoCo toplam %98,57 / değişen kod %100; Checkstyle/PMD
  0 ihlal, dependency gate PASS. [Tam komut ve Windows Compose uyarlaması](../../docs-recommendation/testings/task-01-tests.md).
