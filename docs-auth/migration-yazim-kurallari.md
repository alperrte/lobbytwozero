# Auth migration yazım kuralları

Auth migration aralığı **V61–V70**. Şu an kullanılan: **V61–V64** (donuk). **V65–V70 boş.**

## Mevcut dosyalar (konuya göre)

| Dosya | İçerik |
| --- | --- |
| `V61__accounts_roles_and_audit.sql` | hesaplar, roller, denetim kayıtları (güvenlik olayı, durum ve rol geçmişi) |
| `V62__sessions_and_credentials.sql` | oturum, e-posta kodları, parola sıfırlama, 2FA, cihaz kimliği |
| `V63__external_identities.sql` | Steam/Google bağı ve giriş akışları |
| `V64__account_deletion_and_event_outbox.sql` | hesap silme koordinasyonu ve olay kuyruğu |

## Yazarken

1. **Yeni tablo = tek dosyada `CREATE TABLE` + kendi indeksleri.** Tablonun altına hemen `CREATE INDEX` yazılır.
2. **Dosya konuya göre adlanır** (`V65__external_identity_linking.sql`), "create/add" gibi fiil değil, ne hakkında olduğu.
3. **Dosyanın başında içindekiler** (hangi tablolar) ve her bölümün başında `=====` başlığı olur.
4. **Aynı işin `ALTER`'leri tek dosyada toplanır**, tek amaçla. Bir tabloyu bir dosyada değiştirip başka dosyada tekrar değiştirmeyin.
5. **Bir CHECK listesini değiştirmek** (örneğin yeni olay türü) için `DROP CONSTRAINT` + `ADD CONSTRAINT` çifti yazılır; tek satırlık değişiklik için bile bu çift birlikte, aynı dosyada kalır.
6. **Her kısıt ve indekse ad verilir** (`uq_`, `ck_`, `ix_` önekleri). Anlamı bir yorum satırıyla yazılır.

## Donuk kuralı

Bir migration push edilip bir ortamda uygulandıktan sonra **değiştirilmez**; her değişiklik yeni sürümdür. Flyway her dosyanın sağlama toplamını (checksum) kaydeder ve açılışta karşılaştırır, uyuşmazlıkta uygulama açılmaz. `flyway repair` ile susturulmaz.

**Tek istisna (2026-10-31, proje sahibi onayıyla):** V61–V68, okunabilirlik için 4 dosyaya indirildi. Şema birebir aynıdır: eski ve yeni dosyalar iki boş PostgreSQL 17'ye uygulanıp `pg_dump -s` çıktıları karşılaştırıldı (23 tablo, 61 indeks, 127 kısıt, fark yok). İstisna tekrarlanmaz.

## Yerel `auth_db`'nizi bir kez sıfırlayın

Eski sürümde Auth'u çalıştırmış ve veritabanını oluşturmuş herkes için: eski sağlama toplamları artık dosyalarla uyuşmuyor, Auth açılırken Flyway hatası verir. Yerel geliştirme verisi olduğu için şemayı sıfırlayın (diğer servislerin veritabanlarına dokunmaz):

```powershell
docker exec ltz-dev-postgres-1 psql -U ltz_dev -d auth_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose up -d auth-service
```

Veritabanı adı `.env`'deki `DB_AUTH_NAME` (varsayılan `auth_db`). Auth yeniden açılırken Flyway 4 dosyayı sıfırdan uygular. Testler (Testcontainers) ve duman testi her seferinde boş veritabanıyla çalıştığı için bu adıma ihtiyaç duymaz.

## Derleme klasörünü de temizleyin

Dosya adları değiştiği için Maven'in eski derleme klasörü (`target/classes/db/migration`) hâlâ eski adlı dosyaları taşır. Temizlemeden test koşarsanız `Found more than one migration with version 61` hatası alırsınız. Bir kez `clean` ile derleyin (IDE'de "Rebuild Project"):

```powershell
mvn -f backend/pom.xml -pl services/auth-service clean verify
```
