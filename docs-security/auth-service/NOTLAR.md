# auth-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Kerem.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- Testler paylaşılan (CI) test veritabanında ikinci koşuda veri kalıntısı yüzünden kırılabilir; GitHub CI'da her koşu sıfır veritabanı açar. Yerelde `node pre-push/ci/backend.mjs` her seferinde yeni geçici veritabanı kullanır.
- 2026-11-06, Windows/JDK25: tam test koşusunda Mockito self-attach başarısız olup 18 test `Could not initialize plugin: MockMaker` ile başlayamadı. Mevcut `mockito-core` JAR'ının yalnız test koşusu için `-javaagent` olarak JVM başlangıcında yüklenmesi sorunu giderdi; JaCoCo ve testler kapatılmadı. Komut ve son tam koşu sonucu: [REQ-04-A kalite kaydı](../../docs-performance/integration/auth/auth-integration-06.md). Ortak POM veya gerçek ENV değişikliği yapılmadı.
