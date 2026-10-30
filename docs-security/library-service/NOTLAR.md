# library-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Hamza.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- `contextLoads` testi `AUTH_SERVICE_URL` ister; CI test profili (`-Pci`) bunu sağlar. Profil dışı koşuda değişkeni ver.
- `LibraryApiTest` tabloyu temizlediği için geliştirici DB'sine (`library_db`/`ltz_dev`) asla bağlanmaz: yerelde (pre-push `full dev` dahil) Testcontainers ile geçici PostgreSQL açar, CI'da (`-Pci`) izole `library-service`/`ltz_test` DB'sini kullanır. Docker çalışıyor olmalıdır.
