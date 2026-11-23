# community-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Nisa.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- GAME-11 bağlantı kaybı: Hibernate rollback hatası `JpaSystemException` içinde,
  Hikari'nin SQLState taşımayan `SQLException("Connection is closed")` sebebiyle
  gelebilir. Yalnız bu dar rollback yolu veya kanıtlı bağlantı/timeout sebebi 503'e
  çevrilir; diğer JPA/transaction hataları 500 kalır. TransactionSystemException'ın
  sakladığı asıl uygulama hatası da kontrol edilir; cause taraması 32 adımla sınırlıdır.
- `GameContentDatabaseOutageIntegrationTest`, liste ve detay senaryolarında kendine
  ait PostgreSQL konteynerini durdurup ilk HTTP yanıtını sınar. Paylaşılan
  PostgreSqlIntegrationTest konteyneri veya DEV DB durdurulmaz. Controller ve gerçek
  kesinti testleri birlikte 14/14 geçti (7 Kasım 2026); tam kalite sonucu Game kabul
  belgesinde ayrıca tutulur.
- Son tam doğrulama: **501/501 PASS**, LINE %99,13, değişen executable satırlar %94,12;
  yeni kalite ihlali 0 (mevcut PMD borcu 6). Trivy runtime HIGH/CRITICAL 0; merkezi
  kontrol `BACKEND TEST CHECK PASSED`. Game/Community/Gateway fixture kabulü 17/17 PASS.
