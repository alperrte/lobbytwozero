# Hardware yerel katalog aktarımı — PC Part Dataset

2026-11-07 kullanıcının açık talimatı, önceki Open Icecat görevini bu aktarım için iptal eder.
Kaynak yalnız [docyx/pc-part-dataset](https://github.com/docyx/pc-part-dataset),
commit `c52a04ca9465c83997ed335f7767b09a2005dd26` ve beş JSON dosyasıdır.
İlk aktarım dört kategoriydi; kullanıcının sonraki açık talimatıyla
`data/json/video-card.json` → `GPU` kapsama eklendi.
Scraper çalıştırılmaz. Eski Icecat kodu/şeması geçmiş uyumluluğu için korunur;
bu komut onları çağırmaz. Frontend ve fırsat forumu kapsam dışıdır.

## Kimlik ve veri modeli

- V9 zaten `(category, normalized_model, catalog_variant)` tekilliğini sağlar.
  V10, kaynak sağlayıcı CHECK'ine `PC_PART_DATASET`, kaynağa `source_revision`
  ve kalıcı teknik import özetleri için `hardware_dataset_import_runs` ekler.
  V1–V9 değiştirilmez. Hardware V1–V10 aralığı bu işlemle dolar.
- Kullanıcı yalnız V191'i ayrıca Hardware'a tahsis etti. V191, kaynak kategorisi
  CHECK'ine GPU ekler; uygulanmış V1–V10 değişmez. V192 ve sonrası tahsis edilmedi.
- Kaynak satırlarında ürün ID, MPN veya GTIN yoktur. Bunlar uydurulmaz.
  External ID = `SHA-256(pc-part-v1 + kategori + NFKC/Locale.ROOT tam ad +
  sıralı, normalize, yalnız teknik alanlar)`; varyant `PC_PART_DATASET:<hash>`.
  Fiyat ve JSON alan/satır sırası kimliği etkilemez. Marka, açık ad önekinden ayrılır;
  Western Digital, Silicon Power, SK Hynix, Super Talent, Neo Forza, Water Panther,
  Fantom Drives, Oyen Digital ve Club 3D çok sözcüklü öneklerdir. Diğerleri ilk sözcüktür.
- Bütün farklı teknik varyantlar ayrı kalır. Kaynağın gizlediği SKU farkları
  kanıtlanamaz; aynı ad ve teknik veri tek kayıt sayılır. Fiyat tek başına ürün ayracı değildir.
- Aynı pin tekrarlandığında UUID korunur. Güncelleme yalnız bu sağlayıcının
  eşlediği, kendi varyantını taşıyan aktif ürün içindir. Manuel kayıt eşleştirilmez,
  değiştirilmez; arşivlenmiş ürün yeniden açılmaz. Yorum/profil FK'leri değişmez.
- **Kaynak sınırı:** kalıcı upstream ID olmadığından gelecekteki teknik düzeltme ile
  yeni varyant otomatik ayırt edilemez. Teknik fingerprint değişikliği yeni kimliktir;
  eski UUID'ye taşımak gerekiyorsa ayrıca incelenmiş eşleme gerekir. Benzer isimden
  sessiz birleştirme yapılmaz. Yeni pin ve checksum'lar kod incelemesiyle güncellenir.

## Teknik eşleştirme

`source.typedSpecifications` mevcut `Value(number,text,unit,featureId)` yapısını
kullanır. `featureId` kaynaktaki alanı gösterir. Kaynak null değerleri null kalır;
olmayan alanlar eklenmez. Kaynak zaman damgası yoktur: `sourceUpdatedAt=null`,
dil kanıtı olmadığından `locale=und`, GTIN/görsel listeleri boş, MPN null'dır.
`VERIFIED` yalnız mapper/doğrulama sonucudur; yayımlama lisansı doğrulaması değildir.

| Kategori | Kaynak → teknik alanlar |
| --- | --- |
| CPU | `core_count`→coreCount; `core_clock`/`boost_clock` GHz ×1000→clockMhz/boostClockMhz; `tdp`→powerWatts (W); microarchitecture, graphics→integratedGraphics; varsa smt |
| GPU | chipset; memory→capacityGb (VRAM, GB); core_clock/boost_clock→clockMhz/boostClockMhz (kaynak zaten MHz, ×1000 yapılmaz); length→lengthMm (mm); color |
| MOTHERBOARD | socket, form_factor→formFactor; max_memory→maximumMemoryGb (GB); memory_slots→memorySlots; color |
| RAM | speed `[DDR sürümü,MHz]`→memoryType/clockMhz; yalnız sayı ise DDR uydurulmaz; modules `[adet,GB]`→moduleCount/moduleCapacityGb ve çarpımları capacityGb; first_word_latency→firstWordLatencyNs; cas_latency→casLatency; color |
| STORAGE | capacity→capacityGb (kaynak zaten GB); cache→cacheMb; type SSD/metin veya sayısal HDD RPM→storageType/rotationRpm; form_factor M.2 metni veya sayısal inç; interface→interfaceType |

Ortak sekiz kolon korunur. Anakart maksimum RAM'i ürün kapasitesine yazılmaz.
73,4 GB gibi kesirli kapasiteler teknik JSON'da tam saklanır; integer ortak kolonda
null kalır, yuvarlanmaz. Kaynağın MHz etiketli RAM hızı aynı birimle aktarılır;
ek MT/s veya gerçek osilatör frekansı tahmin edilmez. Renk de varyant ayracıdır.
GPU'daki 0,125 GB gibi VRAM değerleri de teknik JSON'da tam saklanır; tam sayı
ortak kapasite kolonu null kalır. GPU güç tüketimi, GDDR türü veya soket uydurulmaz.

## Fiyat dışlama ve işlem sınırları

Streaming parser yalnız kategori whitelist'indeki alanları materialize eder.
`price`, `price_per_gb`, mağaza, URL, stok ve bilinmeyen alanlar `skipChildren`
ile atılır. Ham satır/staging JSON DB'ye yazılmaz. Loglarda yalnız sayılar, satır
indeksi, teknik hata kodu, commit ve SHA-256 bulunur. Ham dosyalar fiyat içerdiği
için yalnız geçici klasöre indirilir; repoya veya rapora ürün JSON'u kopyalanmaz.

Dosyaların hepsi SHA-256 ve satır sayısıyla import öncesi doğrulanır. Varsayılan
50, izinli 1–100 satırlık batch; batch başına ayrı transaction/advisory lock,
5 sn lock / 30 sn statement / 60 sn transaction sınırı vardır. Hatalı batch
tümden rollback olur, sonraki batch devam eder. Tamamlanan batch'ler kalıcıdır;
kalanlar aynı komutla tekrar denenir. Hatalar CLI'yi başarısız çıkışla bitirir.
Dry-run migration uygulamaz, ürün/marka/kaynak/run kaydı yazmaz.

## Kullanım

Önce Hardware JAR'ını testlerle paketle; JDK25 ve Docker gereklidir:

```powershell
mvn -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml "-Dmaven.repo.local=.cache/m2" -f backend/pom.xml -pl services/hardware-service package
$datasetDir = Join-Path $env:TEMP 'ltz-pc-part-c52a04ca'
node ops/hardware-catalog-import.mjs download $datasetDir
node ops/hardware-catalog-import.mjs dry-run $datasetDir
node ops/hardware-catalog-import.mjs import $datasetDir
node ops/hardware-catalog-import.mjs import $datasetDir
```

Wrapper yalnız `ltz-dev-hardware-service-1` Compose proje/servis etiketlerini kontrol eder;
ENV dosyası veya `Config.Env` okumaz. JAR/beş dosyayı konteynerin geçici klasörüne
kopyalar; mevcut konteyner ortamını içeride kullanan ayrı Java ana sınıfını çalıştırır.
CLI ayrıca tam `dev` profili, `hardware_db` adı ve yerel/Postgres Compose host'unu
ister. Varsayılan yazma kapalıdır; yalnız `--execute` açar. Public endpoint, scheduler,
auth bypass veya yeni ENV anahtarı yoktur. Gerçek DB yalnız `hardware_db`'dir.

Konteyner içinden alternatif komut:

```text
java -Dloader.main=com.ltz.hardware.infrastructure.config.PcPartImportCommand -cp /tmp/ltz-pc-part-import/import.jar org.springframework.boot.loader.launch.PropertiesLauncher /tmp/ltz-pc-part-import --execute --batch=50
```

Kaynağın MIT lisans beyanı, PCPartPicker kökenli verilerin yeniden yayımlama hakkını
tek başına doğrulamaz. Bu karar yalnız yerel geliştirme içindir; production toplu
yayını/deploy'u yoktur. Sonuç/kanıt: [ilk aktarım raporu](../reports/pc-part-dataset-import.md)
ve [GPU ekleme raporu](../reports/pc-part-gpu-import.md).
