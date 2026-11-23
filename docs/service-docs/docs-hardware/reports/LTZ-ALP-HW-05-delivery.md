# LTZ-ALP-HW-05 — Masaüstü Donanım Tanıma ve İzinli Senkronizasyon Sözleşmesi

Tarih 2026-10-21; sorumlu Alper. **READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT**.
HW-06/HW-07 2/2; toplam 17/17 alt görev. İki nihai kalite kapısı PASS.
PDA kapanışı doğrulanan ana görev 4/5; HW-05 ana kutusu kullanıcı bildirimi gelene
kadar `[ ]`. Beşinci ve son feature mesajı kullanıcıya verilir; Git/PDA işlemi yapılmaz.

## Kapsam ve kabul kanıtı

| Alt görev | Yerel sonuç | Kanıt |
|---|---|---|
| HW-06 tarama kabul desteği | DONE; açık onaylı stateless owner preview | 30 yeni test; ilk alt görev verify 269 PASS/%98,57 |
| HW-07 izinli sync | DONE; grant/revoke, ayrı cihaz sınırı, idempotent snapshot | 24 yeni test; hardware 293 PASS/%98,75, V10 |

Kullanıcı önceki HW-04 PDA kapanışını ve HW-05 geçişini bildirdi. f558a69 commit'i
HW-04 teslimindeki 57 dosyayla eşleşti; başlangıç ağacı temiz ve upstream tracking
ahead/behind 0/0. [Model ve güvenli erteleme](../decisions/HW05-desktop-sync-proposal.md)
ayrı ayrı açıkça onaylandı. Sonraki kaynak geliştirmesi yalnız HW-05 kapsamındadır.

## Uygulanan akış

Preview owner profilini doğrular; processingConsent=true yalnız bu tek isteğin
açık onayıdır. Hardware/izin/source/profileVersion kaydetmez veya değiştirmez;
yalnız kısa TTL rate bucket'ı yazar. Normalleştirilmiş yanıt persisted=false taşır.
Masaüstü tarayıcı çalıştırılmaz, fiziksel donanım doğrulandığı iddia edilmez.

Scan v1 1–32 tekil opaque componentKey UUID, mevcut 12 kategori, zorunlu model,
opsiyonel marka, adet ve mevcut sekiz teknik alanı kabul eder. Ham/NFKC sonrası
metin, uzunluk/range ve capturedAt (son 24 saat, en fazla 5 dakika gelecek)
doğrulanır; zaman microsecond UTC, bileşen sırası key'e göre normalize edilir.
Boş snapshot, source/user/device ataması, katalog UUID'si, seri/MAC/hostname,
telemetri ve bilinmeyen alanlar reddedilir. Gövde 16 KiB; ham tarama loglanmaz.

Owner grant için profileVersion, deviceId ve accepted=true gerekir. DeviceId
yalnız seçim girdisidir; aktif owner cihazı güvenilir HardwareSyncDevicePolicy
doğrulamalıdır. Kayıt yokken NOT_GRANTED, izin verildiğinde GRANTED, iptalde
REVOKED. Aktif izin değiştirilmez; önce revoke gerekir. Her yeni grant farklı
generation ve sıfır sequence metadata'sı başlatır. GET yalnız owner'a son izin/
sync bilgilerini döndürür. Revoke dış cihaz erişilemese bile yapılabilir; donanımı
silmez. Zaten kapalı izni güncel version ile iptal etmek değişikliksiz yanıt verir.

Apply yalnız ayrı AuthenticatedHardwareDevice + hardware:sync:write authority,
aktif owner cihazı ve aynı profile/device/generation için GRANTED yerel izinle
çalışır. Kullanıcı actor'ı aynı authority'ye sahip olsa bile cihaz yerine geçmez;
cihaz principal'ı da owner kontrol API'lerine giremez. HTTP ve service sınırı
ayrıca kontrol edilir. Gerçek authenticator yoktur, production cihaz policy deny.
CSRF ve no-store/private korunur; header/body kimliği güven kaynağı değildir.

Parent profil kilidi ve profileVersion owner edit/revoke/apply yarışını seri yapar.
Yeni sequence kesin artar; eski capturedAt reddedilir. Tam snapshot yalnız
DESKTOP_SYNC kümesini key'e göre ekler/günceller ve artık bulunmayan otomatik
satırları kaldırır. MANUAL kayıtları, profil adı/türü/gizlilik ve profilin MANUAL
oluşturulma kökeni korunur. Apply sonrası toplam kayıt en fazla 200'dür; mevcut
manuel API'ye yeni genel kota konmaz. Owner manuel PUT ile synced kaydı değiştirirse
kaynak MANUAL ve key null olur; sonraki scan bu kaydı ezmez, aynı key için ayrı
otomatik kayıt oluşturabilir. İzin sürerken manuel silinen otomatik kayıt tekrar
gelebilir; sürekli güncellemeyi durdurmak için revoke kullanılır.

## Idempotency ve veri sınırları

Idempotency-Key UUID zorunludur. Normalize scan ve hedef/user/device/generation/
sequence/expected-version fingerprint'i profil/key receipt'iyle eşleşirse 24 saat
aynı asgari yanıt döner; farklı hash 409. Retry güncel cihaz sahipliği ve aktif
izni yeniden doğrular; revoke veya eski generation cached yanıttan aşılamaz.
Receipt ilk apply'ın version/zaman/sıra ve değişiklik sayaçlarını taşır; güncel
profil görünümü değildir. Model metni, owner veya device UUID response cache'ine
kopyalanmaz. Donanım/state/version/receipt aynı transaction'da; kapasite/validation
hatası flush edilmiş değişiklikleri ve quota'yı da geri alır.

Profil başına en fazla 500 canlı receipt, 24 saat TTL ve saatlik temizleme vardır
(normal saklama yaklaşık 24–25 saat). Süre dolunca kalıcı lastSequence eski paket
uygulamasını yine engeller. Preview/grant/revoke ortak user 20/dakika; apply user
30/dakika, device 10/dakika; yeni işlemler ortak socket peer 120/dakika DB fixed
minute bucket'larını kullanır. Ham IP tutulmaz, XFF'a güvenilmez. Rollback olmuş
denemeler quota tüketmez; gerçek reverse-proxy client-IP zinciri NOT_VERIFIED.

## API ve DTO envanteri

Toplam 46 business endpoint'in bu görevde eklenen beşi:

| Method | Tam path | Request / response |
|---|---|---|
| POST | /api/v1/hardware/profiles/{profileId}/sync/preview | PreviewHardwareScanRequest → HardwareScanPreviewResponse |
| GET | /api/v1/hardware/profiles/{profileId}/sync-consent | Owner path → HardwareSyncConsentResponse |
| PUT | /api/v1/hardware/profiles/{profileId}/sync-consent | GrantHardwareSyncRequest → HardwareSyncConsentResponse |
| DELETE | /api/v1/hardware/profiles/{profileId}/sync-consent | profileVersion query → HardwareSyncConsentResponse |
| POST | /api/v1/hardware/profiles/{profileId}/sync/snapshots | ApplyHardwareSyncRequest + Idempotency-Key → HardwareSyncReceiptResponse |

Hepsi 200 döndürür. Güvenli ProblemDetail: 400 input/kota üstü bileşen sayısı,
401 kimlik yok, 403 principal/scope/CSRF, 404 owner/cihaz/policy, 409 izin/
generation/sequence/version/hash çakışması, 413 body, 429 RATE_LIMITED veya
SYNC_RECEIPT_LIMIT_REACHED. Mevcut manuel PUT/DELETE ve owner detail/batch source
semantiği de [kanonik API kaydında](../../docs/api.md#hardware) güncellendi.

## Şema ve dosyalar

V10__add_hardware_desktop_sync.sql: hardware_sync_consents ve hardware_sync_receipts;
UUID/UTC/version, status/time/sequence/hash/JSON CHECK; profile CASCADE FK'leri,
profile/key ve profile/generation/sequence tekillikleri, expiry/device indeksleri.
Component sync_component_key ve source CHECK/partial unique indeks eklendi.
Device dış REF'tir; cross-DB FK veya ayrı credential/account tablosu yoktur.
Profil silme consent/receipt ve bileşenleri temizler, diğer profilleri korur.

V1–V10 izole PostgreSQL 17.11/Flyway/JPA ddl-auto=validate PASS. V1–V9 değişmedi;
test edilen V10 da yeniden yazılmaz. Hardware V1–V10 aralığı dolmuştur; sonraki SQL
sürümü ekip kararı ister. Canlı DB'ye V10 uygulanmadı, deploy/rebuild/reset yapılmadı.

37 hardware kaynak dosyası: 27 yeni production Java, dört mevcut production
değişikliği, bir migration ve beş yeni test. Ayrıntılı sınıf/metod listesi
[ilerleme kaydında](../progress/LTZ-ALP-HW-05.md#dosya-ve-use-case-envanteri).
Yeni controller/DTO/domain/mapper/service/port/security/repository katmanları;
mevcut HardwareComponent, HardwareComponentRepository, HardwareSecurityConfiguration,
HardwareExceptionHandler gerekli davranışları ekler. Başka servis/runner/ENV/
Compose/dependency/gateway kaynağı değiştirilmedi. Belgeler yalnız hardware
kısımlarında güncellendi; HW-04 kapanışı kullanıcı bildirimiyle kaydedildi.

## Nihai kalite

| Kontrol | Gerçek sonuç |
|---|---|
| HW-05 yeni test | 54 PASS: HW-06 30, HW-07 24 |
| Hardware | 293 PASS; failure/error/skipped 0 |
| Hardware JaCoCo | 1420/1438=%98,75; exclusion yok |
| Tüm backend | 18 modül / 311 test PASS; line coverage %96,51 |
| Pre-push full dev | PASS / exit 0; SAFE TO COMMIT, 20:49:04 +03 |
| Compose / runtime | Configuration PASS, 18/18 HTTP health PASS |
| Sonar scan dev | PASS / exit 0, 20:53:34 +03; A/A/A |
| Quality Gate | Sonar way VERIFIED, OK/PASS, new violations 0 |
| Sonar coverage/duplication | Overall %93,4, new %95,0, overall/new duplication %0 |
| Açık issue/hotspot | 0/0; sunucu API'siyle 20:53:53 +03'te doğrulandı |

Analysis 5494a478-d3da-4e12-9703-c6fdc06e5817 (20:53:05 +03), CE task
749310d1-c50b-443c-94dd-cec5eac1a95e SUCCESS (20:53:31 +03). Yerel Git branch
feature/hardware-service; runner ayrı branch parametresi vermediği için Sonar
projesi ltz-backend/main. Revision f558a69 önceki HEAD'dir; HW-05 kaynakları
çalışma ağacındadır, bu yeni commit SHA'sı değildir.

İlk Sonar dokuz bakım/test bulgusuyla FAIL oldu; GRANTED sabiti ve exception
lambda'ları düzeltildi, iki tam kapı tekrar PASS aldı. Eşik/skip/suppression/
güvenlik gevşetmesi yoktur. Komutlar, resmi log adları ve geçmiş
[kalite kaydında](../testing/LTZ-ALP-HW-05-quality.md) korunur. HTTP health mevcut
image'ların sağlığıdır; yeni kod deploy/E2E kanıtı değildir. Yeni akışlar gerçek
PostgreSQL + MockMvc/test-only kimlikle sınandı. Frontend repo içinde yoktur.

## Açık gerçek bağlantılar ve teslim durumu

Auth kullanıcı/cihaz token/claim/scope/JWKS/CSRF sözleşmesi DEFERRED_AUTH;
PERF-01 cihaz sahipliği/iptal kontratı ve gerçek desktop E2E DEFERRED_CONTRACT /
NOT_VERIFIED, açık kullanıcı onaylıdır. Production adapter deny; eşleştirme,
credential üretimi, gerçek scanner veya uydurma RestClient URL'si eklenmedi.
HW-03 User/Social ve Performance/Recommendation consumer E2E, HW-04 Admin/
Notification/User-Social UGC ertelemeleri korunur. Gerçek Auth doğrulanmadı;
servis production-ready değildir.

[Toplu final servis raporu](HARDWARE_SERVICE_FINAL_REPORT.md), tüm 46 endpoint,
17 alt görev, V1–V10 şema, entegrasyon matrisi ve kalanları içerir.
Yerel kabul ve iki kalite kapısı doğrulandı; kullanıcı HW-05'i PDA'da kapatabilir.
Commit/push/PDA işlemleri kullanıcıya aittir. Ana HW-05 `[ ]`; tek son feature
mesajı sunulur ve STOP yapılır.
