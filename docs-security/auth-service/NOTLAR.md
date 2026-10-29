# auth-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Kerem.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- Testler paylaşılan (CI) test veritabanında ikinci koşuda veri kalıntısı yüzünden kırılabilir; GitHub CI'da her koşu sıfır veritabanı açar. Yerelde `node pre-push/ci/backend.mjs` her seferinde yeni geçici veritabanı kullanır.
