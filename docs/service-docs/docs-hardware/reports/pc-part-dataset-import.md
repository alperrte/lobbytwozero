# HW-10/HW-11 — Gerçek yerel katalog aktarımı

> Bu rapor ilk dört kategori aktarımının tarihsel kanıtıdır. Kullanıcının sonraki
> GPU talimatı ve güncel toplam için [GPU ekleme raporuna](pc-part-gpu-import.md) bakın.

Tarih: 2026-11-07, Europe/Istanbul. **Gerçek veri aktarımı ve ikinci import tamamlandı.**
Yerel `ltz-dev-postgres-1` içindeki `hardware_db` 23.753 ürün içeriyor.
Kalite kapısı ve canlı oturumlu HTTP kabulü aşağıda ayrı, açık eksiklerdir;
`SAFE TO COMMIT` veya production hazır beyanı verilmez.

## Kaynak ve bütünlük

Kaynak: [docyx/pc-part-dataset](https://github.com/docyx/pc-part-dataset/tree/c52a04ca9465c83997ed335f7767b09a2005dd26).
Sabit commit: `c52a04ca9465c83997ed335f7767b09a2005dd26`.
İndirme: **2026-11-07 23:23:00–23:23:01 +03:00**
(`2026-11-07T20:23:00.430Z` başlangıç). Scraper çalıştırılmadı.
Manifest: `backend/services/hardware-service/src/main/resources/catalog/pc-part-dataset.json`.
Dosya indirme zamanları/boyutları/hash'ler yerel `test-results/hardware-dataset-download.json`
ve dataset klasöründeki `download.json` içinde kayıtlıdır. Ham dataset Git dışında,
geçici klasörde tutulur; rapor/staging/DB'ye ham satır kopyalanmaz.

| Dosya | Byte | SHA-256 |
| --- | ---: | --- |
| cpu.json | 224558 | `1e3fd9ba6bf11fca4217675d67ed5edd0d60cc6cb2604418c443cf474fe58c1c` |
| motherboard.json | 707766 | `7bf4ecb66756c40234ccc1f6e2e43ca477dea7ef3f6e5c92d9b432d5776c6a1b` |
| memory.json | 2275175 | `2c70f628bebd31bb47ef5b46b39de52c41a417b17dfcc0c63991b2d27310de63` |
| internal-hard-drive.json | 988514 | `0634f9c8157d649cf4e6a67ffcaa69ae22695f7a0befb1c60fdfffe921845d3b` |

## Gerçek aktarım sonuçları

| Kategori | Kaynak satır | Kaynakta aynı ad/teknik tekrar | İlk import eklenen | Gerçek DB | İkinci import eklenen | İkinci import değişmeyen |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CPU | 1413 | 452 | 961 | 961 | 0 | 961 |
| MOTHERBOARD | 4973 | 1 | 4972 | 4972 | 0 | 4972 |
| RAM | 13553 | 1395 | 12158 | 12158 | 0 | 12158 |
| STORAGE | 6461 | 799 | 5662 | 5662 | 0 | 5662 |
| **Toplam** | **26400** | **2647** | **23753** | **23753** | **0** | **23753** |

Dry-run aynı sayıları öngördü ve DB'yi boş bıraktı. Her iki gerçek koşuda
updated=0, skipped=0, failed=0, hatalı kaynak satır=0, issues=[].
2.647 sayı, kaynak içi teknik tekrarların atılmasıdır; farklı teknik varyantlar
atılmadı. İkinci koşuda oluşan yeni mükerrer ürün sayısı **0**.
İki kalıcı `hardware_dataset_import_runs` kaydı da `SUCCEEDED`.

İlk import öncesi ürün, profil, bileşen, yorum ve katalog audit tabloları boştu.
Gerçek kullanıcı satırı yokken varmış gibi korunma iddiası yapılmaz: önce/sonra
profil/bileşen/yorum/audit sayıları 0 ve içerik özetleri aynıdır.
Dolu yorum/profil ilişkileri ile manuel ürün korunması ayrıca gerçek PostgreSQL
entegrasyon testinde doğrulandı.

## SQL kanıtı

Aşağıdaki sorgular **gerçek yerel hardware_db** üzerinde çalıştırıldı:

```sql
SELECT category,count(*) FROM hardware_products GROUP BY category ORDER BY category;
-- CPU 961; MOTHERBOARD 4972; RAM 12158; STORAGE 5662

SELECT count(*),md5(string_agg(id::text||':'||version::text,',' ORDER BY id))
FROM hardware_products;
-- İlk ve ikinci import sonrası: 23753, 2d6636cd6183745feae002e22529b973

SELECT count(*) FROM (
  SELECT source_provider,external_product_id FROM hardware_product_sources
  GROUP BY 1,2 HAVING count(*)>1
) d;
-- 0

SELECT model,capacity_gb,count(*) FROM hardware_products
WHERE category='STORAGE' AND normalized_model='990 pro'
GROUP BY model,capacity_gb ORDER BY capacity_gb;
-- 990 Pro / 1000 / 1; 990 Pro / 2000 / 1; 990 Pro / 4000 / 1
```

Bütün ürün UUID'leri ve product version değerleri ikinci importta aynı kaldı.
Kaynak metadata'sının sync zamanı/version'ı yenilenir; ürün sürümü gereksiz artmaz.
İki 73,4 GB disk teknik JSON'da tam ondalık değerle korunur; integer kapasiteye
yuvarlanmaz. Tüm kaynak satırlarında provider ve commit doğru, başka sağlayıcı satırı 0.
Yetim yorum ve profil ürün referansı sorguları 0 döndü.

**Fiyat dışlama:** `hardware_products`, `hardware_product_sources`,
`hardware_dataset_import_runs` kolonlarında fiyat/para birimi/mağaza/stok/satın alma
alanı bulunmadı (0). `typed_specs`, `feature_groups`, `gtins` içinde satış anahtarı/URL
taraması 0; tüm teknik anahtarların whitelist kontrolü 0 uygunsuz anahtar;
kalıcı import `files/summary` JSON'larında satış alanı/URL taraması 0.
Parser bilinmeyen alanları materialize etmeden atar. Testlerde fiyat ve mağaza
sentinellerinin entity/işlenmiş JSON/rapora geçmediği ve fiyatın kimliği etkilemediği doğrulandı.
Önceden var olan kampanya tabloları bu görevde değiştirilmedi.

Yerel kanıtlar: `test-results/hardware-import-before.sql.log`,
`hardware-import-dry-run.log`, `hardware-import-first.log`,
`hardware-import-after-first.sql.log`, `hardware-import-second.log`,
`hardware-import-after-second.sql.log`. Bunlar üretilmiş, Git dışı dosyalardır.

## Migration ve çalışan servis

- Aktif klasör ve gerçek DB başlangıcı V1–V9 olarak okundu.
- Son uygun V10: `V10__add_pc_part_dataset_import.sql` uygulandı.
  Flyway history V1–V10, 10/10 success; önceki migration dosyaları değiştirilmedi.
- Reset, tablo/veri silme, clean/repair/baseline, volume silme veya başka servis DB erişimi yok.
- Son JAR yalnız `ltz-dev-hardware-service-1` konteynerine yüklendi ve bu servis
  yeniden başlatıldı. Önceki JAR `/tmp/hardware-before-pc-part-import.jar` olarak
  konteynerde korunur. ENV dosyası/servis ayarı değişmedi.
- Yeniden başlatma sonrası Hardware `/actuator/health`: **200 / UP**;
  ürün sayısı ve UUID/version özeti değişmedi. Diğer uygulama servisleri yeniden başlatılmadı.
- Yalnız bu görev için açılan `ltz-test-hw-import` geçici test PostgreSQL konteyneri
  ve ağı kapatıldı. Gerçek geliştirme volume'leri korunur.

## Gerçek çalıştırılan testler ve kalite sınırı

1. Odaklı mapper/reader/CLI/PostgreSQL/schema koşusu: **32 PASS, 0 hata, 0 atlama**.
   Dört yeni test sınıfında 29 senaryo ve 3 mevcut schema testi.
2. Son tüm Hardware koşusu: **532 test, 0 failure, 0 error, 0 skipped**.
   JSON parsing, dört kategori, fiyat dışlama, GHz/MHz, RAM kit hesabı, kesirli/null
   değerler, marka/model/varyant, idempotency, UUID, manuel ürün, yorum/profil FK,
   batch rollback ve sonraki batch'in devamı, liste/detay/filtre, anonim 401 kapsanır.
3. V7→V10 dolu fixture yükseltmesi, V1–V10 PostgreSQL/Flyway/JPA validate PASS.
4. JaCoCo: modül **2056/2462 = %83,51**; yeni `PcPart*.java` kaynakları
   **252/282 = %89,36**. Merkezi kapının tüm dal diff'i **394/746 = %52,82**;
   dalda bu görevden önce bulunan, test edilmemiş Icecat kodu da bu diff'e dahildir.
   Maven `verify` bu nedenle **FAIL: `Coverage: changed-line coverage below policy`**.
5. Checkstyle/PMD ratchet **FAIL**: baseline 213, ölçüm 220; 7 ek ihlal,
   `IcecatProductParser` (2 unused import), `IcecatHttpClient` (1 CloseResource,
   1 complexity), `IcecatXmlReader` (2 complexity), `LocalCatalogMediaStorage`
   (1 complexity). Bu dört dosya bu görevde değiştirilmedi (`git diff --exit-code` 0).
   Yeni importer dosyalarında Checkstyle/PMD ihlali **0**; kural kapatılmadı,
   baseline genişletilmedi. İptal edilmiş Icecat implementasyonu kapsam dışı bırakıldı.
6. `node --check ops/hardware-catalog-import.mjs` ve `git diff --check` PASS.

İlk geniş koşuda belgelenmiş Windows Türkçe yol / Mockito self-attach problemi
nedeniyle 98 test error oluştu. `docs-security/hardware-service/NOTLAR.md` içindeki
yöntemle mevcut Mockito agent'ı ASCII geçici yoldan yalnız test sürecine verilerek
giderildi; son 532 testte error yok. JaCoCo/testler kapatılmadı.

Çalıştırılan temel komutlar:

```powershell
node pre-push/ci/backend.mjs --modules services/hardware-service
# Ortam hatası: Disposable PostgreSQL startup failed; cleanup failed: ltz-test-1553cbff
# PASS sayılmadı. Aynı secretsiz test Compose tanımı doğrudan başarıyla açıldı/kapatıldı.

$agentPath = Join-Path $env:TEMP 'ltz-mockito-core-5.23.0.jar'
Copy-Item '.cache/m2/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' $agentPath
$env:JAVA_TOOL_OPTIONS = '-javaagent:' + $agentPath
mvn -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml "-Dmaven.repo.local=.cache/m2" -f backend/pom.xml -pl services/hardware-service verify
# 532 PASS; verify coverage-policy aşamasında FAIL
node pre-push/ci/lint-ratchet.mjs check --modules services/hardware-service
# Yukarıdaki 7 mevcut Icecat ihlali nedeniyle FAIL
```

Eski pre-push backend yolu gerçek ENV yükleyebildiği için kullanılmadı; gerçek ENV
okumayan belgelenmiş CI runner tercih edildi. `SAFE TO COMMIT` /
`BACKEND TEST CHECK PASSED` alınmadı. Test/coverage/ratchet çıktıları:
`test-results/hardware-import-tests.log`, `hardware-import-verify-final.log`,
`hardware-import-quality.log`. Son raporlar `hardware-service/target/` altındadır.

## API kabulü

`PcPartImportIntegrationTest` güvenliği açık gerçek PostgreSQL/MockMvc koşusunda,
doğrulanmış test `AuthenticatedActor` ile listeyi, ürün detayını ve dört kategori
filtresini 200; kaynağı/teknik özellikleri ve kapasite filtresini doğru döndürdü.
Kaynaklar sayfa başına toplu yüklenir. Yeni HTTP import endpoint'i yoktur.

Gerçek yerel Gateway `127.0.0.1:5080` üzerinden son koşu:

| İstek | Gerçek HTTP sonucu |
| --- | --- |
| GET /api/v1/hardware/catalog/products | 401 |
| GET /api/v1/hardware/catalog/products/{imported-id} | 401 |
| GET /api/v1/hardware/catalog/products?category=CPU | 401 |
| GET /api/v1/hardware/catalog/products?category=MOTHERBOARD | 401 |
| GET /api/v1/hardware/catalog/products?category=RAM | 401 |
| GET /api/v1/hardware/catalog/products?category=STORAGE | 401 |

Geçerli mevcut kullanıcı oturumu bu görevde mevcut değildi. Gerçek DB aktarımı
SQL ile kanıtlandı; **canlı oturumlu HTTP 200 kabulü NOT VERIFIED**.
Auth/RBAC gevşetilmedi ve token üretilmedi/okunmadı. Kalan kullanıcı adımı:
mevcut normal giriş akışından giriş yapıp aynı liste/detay/filtreleri çağırmak.
Kanıt: `test-results/hardware-import-http.log`.

## Eklenen/değiştirilen dosyalar

Hardware Service altında:

- `application/mapper/PcPartMapper.java`, `application/service/PcPartImportService.java`,
  `domain/entity/PcPartProduct.java`: whitelist normalizasyon, teknik varyant, batch işlemi.
- `infrastructure/client/PcPartDatasetReader.java`,
  `infrastructure/config/PcPartImportCommand.java`,
  `infrastructure/persistence/repository/PcPartImportRepository.java`: pin/hash kontrolü,
  dev CLI, sadece kaynak eşlemesine ait ürün upsert'i ve teknik run raporu.
- `application/service/HardwareCatalogService.java`,
  `infrastructure/persistence/repository/HardwareCatalogSourceRepository.java`:
  mevcut liste/detayda toplu kaynak yükleme ve doğru provider.
- `src/main/resources/catalog/pc-part-dataset.json`,
  `src/main/resources/db/migration/V10__add_pc_part_dataset_import.sql`.
- `PcPartMapperTest`, `PcPartDatasetReaderTest`, `PcPartImportCommandTest`,
  `PcPartImportIntegrationTest` eklendi; `HardwareSchemaMigrationTest` güncel zincire uyarlandı.

Diğer dosyalar:

- `ops/hardware-catalog-import.mjs`: sabit GitHub indirme/hash manifesti, dev konteyner komutu.
- `docs/api.md` yalnız HARDWARE liste/detay kayıtları; `docs/database.md`,
  `docs/er-diagram.md` yalnız Hardware modeli; `docs/decisions/migrations.md` Hardware V10 kaydı.
- `docs-hardware/decisions/pc-part-dataset-import.md`, bu rapor,
  `docs-hardware/README.md`, önceki V2 uygulama ve Icecat karar belgelerine güncel kaynak notları.

## Tekrar import ve kalan işler

```powershell
$datasetDir = Join-Path $env:TEMP 'ltz-pc-part-c52a04ca'
node ops/hardware-catalog-import.mjs download $datasetDir
node ops/hardware-catalog-import.mjs dry-run $datasetDir
node ops/hardware-catalog-import.mjs import $datasetDir
```

JAR paketleme/komut/teknik eşleme ayrıntıları [karar belgesinde](../decisions/pc-part-dataset-import.md).
Tekrar indirme aynı pin/hash'i kullanır. Kaynağın kalıcı upstream ürün ID'si yoktur;
gelecekte teknik fingerprint değişirse otomatik eski UUID birleştirmesi yapılmaz.
Bu sınır ve elle incelenmiş eşleme ihtiyacı karar belgesinde açıkça kayıtlıdır.

Kalanlar: canlı geçerli oturumla HTTP kabulü; dalın mevcut Icecat kalite/coverage
borcunun ayrı kapsamda ele alınması; yeni migration ihtiyacında ek aralık ekip kararı.
PCPartPicker kökenli verinin yeniden yayımlama hakları doğrulanmadı; yalnız yerel
geliştirme importu yapıldı, production yayını/deploy'u yapılmadı.

**Git add/commit/push yapılmadı. ENV secret dosyaları okunmadı/değiştirilmedi;
başka servis koduna/DB'sine dokunulmadı, frontend değiştirilmedi.**
