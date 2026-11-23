# Hardware geliştirme migration temizliği

Tarih: 2026-10-21. Sorumlu: Alper. Başlangıç commit'i: `28ad1a6`.
Bu çalışma yeni bir PDA ana görevi değildir; HW-05 sonrasında kullanıcının açık isteğidir.
HW-05'in 2/2 alt görevi ve geçmiş kalite kanıtları korunur; PDA kapanışı bildirilmedi.

## Kullanıcı kararı ve sınır

Kullanıcı geliştirme ortamını kendisi sıfırlayacağını belirterek uygulanmış hardware
migration'larının yeniden düzenlenmesini istedi. Sonraki açık seçimi:
**“7 ilişkili grup halinde uygula”**. Bu, yalnız bu geliştirme temizliğinde uygulanmış
SQL dosyalarının değişmezliği kuralına istisnadır; genel ekip kuralı değiştirilmedi.
Alt sürüm veya V1–V10 dışına çıkış gerekmez. Başka servislerin SQL'i değiştirilmez.

Eski V1–V10 zinciri kaldırıldı. Güncel dosyalar boş `hardware_db` içindir;
eski `flyway_schema_history` ile yerinde yükseltme sağlamaz. Eski ortama Flyway
repair/baseline/clean uygulanmaz. Uygulama DB/volume silme, Compose down/up veya
image rebuild ajan tarafından yapılmadı; sıfırlama kullanıcıya aittir.

## Güncel migration envanteri

Konum: `backend/services/hardware-service/src/main/resources/db/migration/`.
Her tablo kendi CREATE TABLE tanımında son sütun, DEFAULT ve named constraint'leriyle
oluşturulur. İndeksler ve iki append-only audit fonksiyonu/trigger'ı ilgili dosyadadır.
ALTER TABLE, ALTER INDEX, DROP ve constraint rename adımı yoktur.

| Dosya | Tablolar |
|---|---|
| V1__create_hardware_profiles_and_privacy.sql | hardware_profiles, hardware_profile_field_visibility, hardware_profile_category_visibility |
| V2__create_hardware_catalog.sql | hardware_brands, hardware_products, hardware_product_brands, hardware_catalog_audit |
| V3__create_hardware_profile_components.sql | hardware_profile_components |
| V4__create_hardware_campaigns.sql | hardware_campaigns |
| V5__create_hardware_reviews.sql | hardware_reviews, hardware_review_audit, hardware_write_limits |
| V6__create_hardware_review_reports_and_outbox.sql | outbox_events, hardware_review_reports |
| V7__create_hardware_desktop_sync.sql | hardware_sync_consents, hardware_sync_receipts |

V8–V10 şu anda kullanılmıyor. Sonraki şema değişiklikleri normal ileri migration
kuralına tabidir; tekrar topluca yeniden yazım için bu onay genelleştirilmez.

## Değişiklik ve yöntem

- Eski SQL kaynakları yalnız ignore edilen yerel test kanıtına alındı.
- Profil visibility, katalog status/version, bileşen sync_component_key/source ve
  genişletilmiş review audit action CHECK ilk CREATE TABLE içine taşındı.
- V7 ile eskiden düzeltilen privacy constraint/index adları doğrudan doğru adla oluşturulur.
- Ürün/marka composite FK, CASCADE/SET NULL, partial unique, filtre/TTL indeksleri korunur.
- API, entity, iş kuralı, Auth/CSRF, dependency, ENV ve Compose değişmedi.
- HardwareSchemaMigrationTest.initializesAllHardwareTablesWithSevenOrderedMigrations:
  boş PostgreSQL'de 1–7 sırasını, checksum doğrulamasını ve tam 16 tabloyu kontrol eder.
- HardwareSchemaMigrationTest.repeatedStartupValidatesChecksumsWithoutExecutingAnotherMigration:
  tekrar migrate çağrısının SQL çalıştırmamasını ve geçerli checksum'ları kontrol eder.

## Şema eşdeğerlik kanıtı

2026-10-21 22:27:39 +03: PostgreSQL 17.11 üzerinde ağ erişimi/host portu olmayan
geçici test container'ında iki boş DB oluşturuldu. Birine eski 10 dosya, diğerine
yeni 7 dosya uygulandı. İki DB'nin pg_catalog çıktıları birebir eşit: **16 tablo,
145 sütun, 93 constraint, 51 indeks, 2 trigger, 2 fonksiyon**. Sütun adı/tipi/null/default,
constraint tanımı ve adları, index tanımları, trigger/fonksiyon gövdeleri karşılaştırıldı.
Sütunların fiziksel sırası karşılaştırmaya dahil değildir; şema davranışı korunmuştur.
51 indeksin 27'si açık CREATE INDEX, 24'ü PK/UNIQUE kaynaklıdır.
Uygulama DB'sine erişilmedi, geçici container işlem sonunda kaldırıldı.

İlk yardımcı denemede PostgreSQL'in geçici init sunucusu hazırlık kontrolünü erken
geçirdi; TCP hazır olma kontrolüyle düzeltildi ve karşılaştırma PASS oldu. Bu bir
uygulama SQL hatası değildi. Yerel kanıt: `logs/hardware-schema-equivalence.json`,
`logs/hardware-schema-before.json`, `logs/hardware-schema-after.json`.

## Güncel durum

`COMPLETE / SAFE TO COMMIT`: temiz Maven verify exit 0, 295 PASS (2 yeni; failure/error/skipped 0),
2026-10-21 22:28:37 +03. Tam pre-push exit 0, 313 test/%96,51/18 HTTP PASS ve
SAFE TO COMMIT, 22:32:12 +03. Hardware JaCoCo 1420/1438 = %98,75.
Sonar exit 0, A/A/A + gerçek gate OK, 22:36:04 +03; açık issue/hotspot 0/0,
analysis `7443910a-4dc9-4a35-b432-ebbc5a107df7`. Tüm backend yeniden 313 PASS.
Eski HW-05 PASS sonuçları bu değişikliğin kalite sonucu sayılmaz.
[Güncel kalite kaydı](../testing/hardware-migration-cleanup-quality.md).

Paketlenen kaynaklar yalnız yeni 7 SQL dosyasını içeriyor. Son whitespace/kapsam
kontrolleri PASS. Kullanıcı DB sıfırlamasından sonra hardware image'ını güncel
kaynaklarla yeniden build ederek başlatmalı; bu operasyon çalıştırılmadı.
İş API'leri ve dış entegrasyon ertelemeleri değişmedi. HW-05 PDA kapanışı beklenir;
yeni ana task veya Git yazma işlemi yoktur. STOP.
