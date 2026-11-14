# recommendation-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Alper.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- 2026-11-14 / TASK 1: servis içi sürümlü motor ve V11 motor analitiği temeli eklendi;
  iş HTTP ucu ve Recommendation'ın Auth/downstream tüketici adaptörleri henüz yoktur.
  Başlangıç 401/CSRF 403 testleri gerçek JWT/RBAC/IDOR kabulü değildir.
- DB kullanan testler `DisposableDatabaseInitializer` ile yalnız `-Pci` disposable
  PostgreSQL'e bağlanır. Gerçek DEV/production bağlantısına geri dönüş yoktur.
- Son doğrulama: 29 test, JaCoCo toplam %98,57 / değişen kod %100; Checkstyle/PMD
  0 ihlal, dependency gate PASS. [Tam komut ve Windows Compose uyarlaması](../../docs-recommendation/testings/task-01-tests.md).
