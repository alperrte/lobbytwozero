# HW-10/HW-11 — GPU yerel katalog aktarımı

Tarih: 2026-11-08, Europe/Istanbul. Kullanıcının “gpuyuda aktarmanı istiyorum”
talimatıyla önceki dört kategoriye GPU eklendi. Kaynak tablosu yalnız dört kategori
kabul ettiğinden yeni migration gerekti. Kullanıcı açıkça **“191'i al migrations.md'yi
güncelle”** dedi; yalnız V191 Hardware'a tahsis edildi, V192 ve sonrası tahsis edilmedi.

## Kaynak

- Repository: [docyx/pc-part-dataset](https://github.com/docyx/pc-part-dataset/tree/c52a04ca9465c83997ed335f7767b09a2005dd26).
- Commit: `c52a04ca9465c83997ed335f7767b09a2005dd26` (önceki dört dosyayla aynı pin).
- Ek dosya: `data/json/video-card.json`, 6.636 kaynak satır, 1.026.098 byte.
- SHA-256: `bb0d199e880d02d512f312132d68dbe70b4bf7079bbc18b850b1f40513874575`.
- Manifestli indirme: `2026-11-07T21:01:13.498Z` = **2026-11-08 00:01:13 +03:00**.
  Kanıt: `test-results/hardware-gpu-download.json`. Scraper çalıştırılmadı.

## Veri modeli ve aktarım

`PcPartMapper` GPU whitelist'i: chipset, memory, core_clock, boost_clock, color,
length. Memory → capacityGb (VRAM GB); frekanslar kaynakta zaten MHz, çarpılmaz;
length → lengthMm (mm). Chipset ve renk korunur. Null değerler ve kesirli VRAM tam
saklanır; tam sayı olmayan kapasite ortak integer kolonda null, typed JSON'da tamdır.
GDDR türü, güç, soket, FPS veya kaynakta bulunmayan başka özellik uydurulmaz.
Club 3D çok sözcüklü marka öneki eklendi. Üretici/VRAM/renk/diğer teknik varyantlar
deterministik kimlikle ayrılır. Fiyat kimliği etkilemez ve parser tarafından atılır.

`V191__allow_gpu_catalog_sources.sql` yalnız kaynak kategori CHECK'ini GPU ile
genişletir. V1–V10 dosyaları değiştirilmedi. Yeni tablo, servis, ENV veya public
endpoint eklenmedi. Mevcut liste/detay ve `discovery=true` GPU'yu destekler;
frontend dosyaları değiştirilmedi. API kaydı yalnız HARDWARE bölümünde güncellendi.

| Kategori | GPU öncesi DB | Eklenen | Güncel DB |
| --- | ---: | ---: | ---: |
| CPU | 961 | 0 | 961 |
| GPU | 0 | 6547 | 6547 |
| MOTHERBOARD | 4972 | 0 | 4972 |
| RAM | 12158 | 0 | 12158 |
| STORAGE | 5662 | 0 | 5662 |
| **Toplam** | **23753** | **6547** | **30300** |

GPU kaynak satırı 6.636; aynı ad/teknik özellikte tekrar 89; kabul edilen benzersiz
GPU 6.547; reddedilen/başarısız/atlanan GPU 0. Dry-run aynı sonuçları verdi.
İlk gerçek GPU koşusunda eski 23.753 ürün unchanged, GPU added=6547, issues=[].
İkinci gerçek koşuda bütün kategorilerde added=0, updated=0; GPU unchanged=6547,
toplam unchanged=30300. Yeni mükerrer ürün sayısı **0**.

## Gerçek SQL doğrulaması

Hedef yalnız `ltz-dev-postgres-1` içindeki **hardware_db** oldu.

```sql
SELECT category,count(*) FROM hardware_products GROUP BY category ORDER BY category;
-- CPU 961; GPU 6547; MOTHERBOARD 4972; RAM 12158; STORAGE 5662

SELECT count(*),md5(string_agg(to_jsonb(p)::text,',' ORDER BY id))
FROM hardware_products p WHERE category<>'GPU';
-- Önce/sonra: 23753 / 4e86334c8af6af42e1b2366e0fca2be3

SELECT count(*),md5(string_agg(id::text||':'||version::text,',' ORDER BY id))
FROM hardware_products WHERE category='GPU';
-- İlk ve ikinci GPU importu: 6547 / 1cde88fd62e4da65586cbcff509c1754
```

Kaynak `(provider,external_product_id)` duplicate sorgusu 0; GPU typed_specs içinde
whitelist dışı anahtar 0; fiyat/mağaza/stok/para birimi/satın alma/URL taraması 0.
152 eski GPU'nun 1 GB altındaki VRAM değerleri teknik JSON'da korundu.
GPU dışı ürünlerin **bütün satır içerikleri ve UUID'leri** aynı kaldı; kaynak sync
metadata'sı importer'ın normal yeniden çalışması kapsamında yenilendi.
Başlangıçta profil/bileşen/yorum tabloları boştu ve öyle kaldı. Dolu manuel ürün,
yorum/profil FK'lerinin korunması PostgreSQL entegrasyon testinde doğrulanır.

Yerel kanıtlar: `test-results/hardware-gpu-before.sql.log`,
`hardware-gpu-dry-run.log`, `hardware-gpu-first.log`,
`hardware-gpu-after-first.sql.log`, `hardware-gpu-second.log`,
`hardware-gpu-after-second.sql.log`. Bunlar Git dışı üretilmiş dosyalardır.

## Test ve kalite

Son JAR yalnız mevcut `ltz-dev-hardware-service-1` içine yüklendi, önceki JAR
`/tmp/hardware-before-gpu-import.jar` olarak korundu ve bu servis yeniden başlatıldı.
Yeniden başlatma sonrası health **200 / UP**. Gerçek Gateway'de
`GET /api/v1/hardware/catalog/products?category=GPU&discovery=true` oturumsuz **401**;
canlı geçerli oturumlu HTTP kabulü önceki işteki gibi **NOT VERIFIED**.
Yetkili test aktörüyle GPU liste/detay okuması izole PostgreSQL/MockMvc'de PASS.
Auth/RBAC gevşetilmedi. HTTP kanıtı `test-results/hardware-gpu-http.log`.

- Odaklı mapper/reader/CLI/import/schema: **35 PASS, 0 failure/error/skipped**.
- Tüm Hardware regresyonu: **535 PASS, 0 failure/error/skipped**.
- V10 → V191 yükseltmesi, kaynak JSON/ürün UUID korunması ve GPU kategori kabulü PASS.
- GPU MHz dönüşümü, kesirli/null VRAM, fiyat dışlama, üretici/VRAM/renk varyantları,
  beş kategoride `discovery=true`, GPU detayındaki teknik alanlar ve ikinci import PASS.
- JaCoCo modül: **2065/2471 = %83,57**. Maven verify yine dalın önceki Icecat
  kodunu da içeren changed-line coverage kapısında FAIL verdi:
  `Coverage: changed-line coverage below policy`.
- Checkstyle/PMD: yeni GPU/importer ihlali 0. Önceki Icecat dosyalarındaki 7 baseline
  dışı ihlal devam ediyor; toplam 213 baseline / 220 ölçüm. Bu dosyalara dokunulmadı,
  kural kapatılmadı/baseline genişletilmedi. `SAFE TO COMMIT` alınmadı.
- Windows Mockito sorunu için önceki belgelenmiş ASCII geçici agent yöntemi kullanıldı.
  Testler/JaCoCo kapatılmadı. Çıktılar: `hardware-gpu-tests.log`,
  `hardware-gpu-verify.log`, `hardware-gpu-quality.log`.

Tekrar çalıştırma:

```powershell
$datasetDir = Join-Path $env:TEMP 'ltz-pc-part-c52a04ca'
node ops/hardware-catalog-import.mjs download $datasetDir
node ops/hardware-catalog-import.mjs dry-run $datasetDir
node ops/hardware-catalog-import.mjs import $datasetDir
```

Komut beş sabit dosyayı işler; mevcut ürünleri çoğaltmaz. Yeni veri pin'i/teknik
kimlik sınırları [ortak karar belgesinde](../decisions/pc-part-dataset-import.md).
PCPartPicker kökenli veri hakkı sınırı değişmedi; yalnız yerel geliştirme aktarımı,
production toplu yayını/deploy'u yoktur.

Bu eklemede değişenler: PcPartMapper, PcPartImportService, HardwareCatalogService,
dataset manifesti, V191; PcPartMapperTest, PcPartDatasetReaderTest,
PcPartImportIntegrationTest, HardwareSchemaMigrationTest; docs/api.md HARDWARE,
docs/database.md, docs/er-diagram.md, docs/decisions/migrations.md ve Hardware rapor/karar indeksi.
Önceki dört kategori aktarımının dosyaları ve çalışma ağacındaki dış değişiklikler korundu.
ENV dosyaları okunmadı/değiştirilmedi. Başka servis DB/kodu ve frontend değiştirilmedi.
Git add/commit/push yapılmadı.
