# LTZ-ALP-HW-03 — Donanım Gizliliği, Karşılaştırma ve Servis API'leri

Tarih: 2026-10-21. Sorumlu: Alper.
Durum: **DONE — DEFERRED_AUTH / DEFERRED_CONTRACT**.
Kullanıcı 2026-10-21 tarihinde PDA kapanışını ve HW-04 geçişini bildirdi;
kullanıcı commit'i 104524a doğrulandı. Aşağıdaki teknik kanıt teslim snapshot'ıdır.

## Kapsam ve kabul kanıtı

| Alt görev | Sonuç | Kanıt |
|---|---|---|
| HW-08 gizlilik | Yerel uygulama/test DONE | V6/V7, HardwarePrivacyControllerTest 17 PASS |
| HW-09 karşılaştırma | DONE | HardwareComparisonControllerTest 9 PASS |
| HW-17 teknik veri sözleşmesi | Yerel sözleşme DONE; consumer E2E ertelendi | HardwareBatchControllerTest 14 PASS |

Teslim anında 3/3 aktif alt görev; toplam 13/17. O sırada PDA kapanışı doğrulanmış
ana görev sayısı 2/5 idi. Sonraki kullanıcı bildirimiyle HW-03 ana kutusu `[x]`,
PDA kapanışı doğrulanmış ana görev sayısı 3/5 oldu.
Kullanıcı HW-03 erişim modelini ve dış sözleşmelerin güvenli ertelenmesini ayrı ayrı
onayladı: [karar](../decisions/HW03-privacy-integration-proposal.md).

## Çalışan yerel davranış

Yeni ve mevcut profillerin paylaşım varsayılanı PRIVATE. Owner profil düzeyini,
altı alanı ve 12 kategoriyi PRIVATE/FRIENDS/PUBLIC ile sınırlar. Eksik map kuralı
PRIVATE; PUT map'leri tamamen değiştirir. Gizlilik güncellemesi mevcut profileVersion
ve parent pessimistic lock ile component/profil mutasyonlarıyla aynı aggregate'a katılır.

Shared okuma anonim değildir. Non-owner için profil, alan ve kategori izniyle
birlikte güvenilir global User görünürlüğü ve Social ilişki/engel kararı gerekir.
FRIENDS doğrulanmış arkadaşlığı gerektirir. Dış deny/null/hata ile olmayan kaynak
aynı 404 olur. Üretim UnavailableHardwareVisibilityPolicy daima deny eder;
gerçek User/Social URL veya kimlik protokolü uydurulmadı. İzin veren mock yalnız testtedir.
Owner kendi shared önizlemesini dış karardan bağımsız görür; mevcut owner API'leri korunur.

Shared DTO yalnız izinli alanları taşır. Component/model/marka ID'leri, owner UUID,
sürüm/tarih/kurallar yoktur. Gizli kategoriler sayfalama ve toplamdan çıkarılır;
bütün component alanları gizliyse component varlığı ve sayacı da gösterilmez.

Karşılaştırmalar iki owner profili veya iki owner bileşeni için çalışır. Profil
özetinde kategori kayıt/adetleri, RAM/depolama bilinen kapasitesi ve kapasitesi
bilinmeyen adet ayrıdır. Teknik karşılaştırma sekiz kayıtlı alanı döndürür; eksik
veri null kalır. Genel model karşılaştırması aynı kategoride iki ACTIVE modeldir;
marka seçimi veya katalog yazma izni gerekmez. FPS, benchmark, kazanan, performans
puanı veya uyumluluk önerisi üretilmez.

Batch API kullanıcı adına doğrulanmış actor ile yalnız owner profillerini okur.
1..20 tekil profil ve istenen size toplamı en fazla 200; her sayfa 0..10000,
size 1..100. Bir yabancı/olmayan profil bütün isteği 404 yapar. İstek sırası ve
createdAt,id sırası korunur; toplam, hasNext ve nextPage açıktır. DTO kişisel profil
adı, owner UUID, seri numarası ve gizlilik ayarı içermez. Dört sınırlı sorgu:
owner profilleri, toplu count, sabit SQL/parametrik array unnest + LATERAL sayfa
ID'leri, tek component hydration. Boş sonuçta hydration sorgusu atlanır.
20 profil senaryosunda üç ORM statement ölçüldü; ilave tek JDBC sayfa sorgusu vardır.
Read-only işlemler REPEATABLE_READ snapshot kullanır.

## API envanteri

Hepsi tam `/api/v1/hardware` prefix'ini karşılar; gateway değişmedi.
31 iş endpoint'inin bu görevde eklenen yedisi:

| Method | Path | Erişim / DTO |
|---|---|---|
| GET | /api/v1/hardware/profiles/{profileId}/privacy | Owner; HardwarePrivacyResponse |
| PUT | /api/v1/hardware/profiles/{profileId}/privacy | Owner + CSRF + profileVersion; UpdateHardwarePrivacyRequest |
| GET | /api/v1/hardware/profiles/{profileId}/shared | Typed actor; yerel + dış policy; SharedHardwareProfileResponse |
| POST | /api/v1/hardware/comparisons/profiles | İki owner profili + CSRF; CompareHardwareIdsRequest |
| POST | /api/v1/hardware/comparisons/components | İki owner bileşeni, aynı kategori + CSRF; CompareHardwareComponentsRequest |
| POST | /api/v1/hardware/comparisons/catalog-products | İki aktif aynı kategori model + CSRF; CompareHardwareIdsRequest |
| POST | /api/v1/hardware/profiles/batch | Owner + CSRF; HardwareBatchRequest / HardwareBatchResponse |

Ayrıntılı input/output, validation, HTTP ve test kayıtları
[docs/api.md HARDWARE](../../docs/api.md#hardware) altındadır.
Başarı yanıtları no-store, private; hatalar güvenli ProblemDetail.
400 validation, 401 actor yok, 403 principal/CSRF, 404 owner/görünürlük/eksik kaynak,
409 eski aggregate sürümü, body >16 KiB için 413. Yeni anonim iş endpoint'i yok.

## Şema ve migration

- V6__add_hardware_profile_privacy.sql: profile visibility DEFAULT PRIVATE;
  hardware_profile_field_visibility ve hardware_profile_category_visibility
  composite PK, CHECK ve profil CASCADE FK'leri; component kategori sayfa indeksi.
- V7__name_hardware_privacy_constraints.sql: test edilmiş V6 değiştirilmeden
  otomatik constraint/index adlarının docs/database.md standardına uyarlanması.
  Veri veya erişim kuralı değişmez.
- Kural collection'ları bağımsız entity değildir; aggregate version ve zaman bilgisi
  parent hardware_profiles üzerindedir. Dış User/Social verisi bu DB'ye kopyalanmaz.
- V1–V5 ve test edilmiş V6 yeniden yazılmadı. V1–V7 izole PostgreSQL 17.11,
  gerçek Flyway ve JPA ddl-auto=validate ile PASS. V6/V7 canlı DB'ye uygulanmadı;
  mevcut canlı şemanın tamamı ayrıca doğrulanmadı. Sonraki boş hardware sürümü V8.

## Değişen kod ve belgeler

Hardware içinde 22 yeni production Java, 5 mevcut Java, iki migration ve üç yeni
integration test sınıfı; toplam 32 kaynak dosyası:

- Controller: HardwarePrivacyController, HardwareComparisonController, HardwareBatchController.
- Request: UpdateHardwarePrivacyRequest, CompareHardwareIdsRequest,
  CompareHardwareComponentsRequest, HardwareBatchRequest.
- Response: HardwarePrivacyResponse, SharedHardwareProfileResponse,
  SharedHardwareComponentResponse, HardwareComparisonResponse,
  HardwareProfileComparisonItem, HardwareTechnicalComparisonItem, HardwareBatchResponse.
- Service: HardwarePrivacyService, HardwareComparisonService, HardwareBatchService.
- Port/adapter: HardwareVisibilityPolicy, UnavailableHardwareVisibilityPolicy.
- Domain enum: HardwareVisibility, HardwarePrivacyField.
- Persistence: HardwareBatchQuery; mevcut HardwareProfileRepository ve
  HardwareComponentRepository'ye owner batch/aggregate/filtreli sayfa sorguları.
- Mevcut HardwareProfile'e privacy rule collection'ları; HardwareComponentMapper'a
  teknik özellik DTO dönüşümü; HardwareSecurityConfiguration'a typed actor comparison yolu.
- Test: HardwarePrivacyControllerTest, HardwareComparisonControllerTest,
  HardwareBatchControllerTest. Önceki testler değiştirilmedi ve geçmeye devam ediyor.

docs/api.md yalnız HARDWARE bölümü, docs/database.md hardware şeması,
docs/er-diagram.md hardware diyagramı ve docs/architecture.md hardware akışı güncellendi.
Kök master, docs-hardware README/karar/progress/quality/teslim kayıtları güncel.
HW-02 progress/teslim kayıtları kullanıcının önceki PDA kapanış bildirimiyle tamamlandı.

Önceden mevcut beş pre-push değişikliği korunmuştur ve HW-03 kodu diye sahiplenilmez:
backend-check.mjs, menu.test.mjs, pre-push.md, quality.mjs, quality.test.mjs.
Bu dosyalar bu çalışmada düzenlenmedi. Başka servis, ENV, Compose, bağımlılık,
gateway davranışı veya production config değişmedi. Git yazma işlemi yapılmadı.

## Final kalite kanıtı

| Kontrol | Gerçek sonuç |
|---|---|
| Yeni testler | 17 privacy + 9 comparison + 14 batch = 40 PASS |
| Hardware toplamı | 174 PASS; failure/error/skipped 0 |
| Hardware JaCoCo | 884/898 satır = %98,44; exclusion yok |
| Runner regresyonları | 47 PASS; mevcut beş betik test dosyasının kümesi |
| Pre-push full dev | PASS / exit 0; SAFE TO COMMIT |
| Tam backend | 18 modül / 192 test PASS; ağırlıklı satır kapsamı %94,94 |
| Compose ve runtime | Configuration PASS, 18/18 HTTP health PASS |
| Sonar scan dev | PASS / exit 0; SONAR SCAN PASSED |
| Sonar notları | Security A / Reliability A / Maintainability A |
| Sonar kapsamı | Overall %91,2; new %93,7; overall/new duplication %0 |
| Sonar gate | PASS / OK, Sonar way VERIFIED; new violations 0 |
| Açık issue / hotspot | 0 / 0; API ile bu analiz için doğrulandı |

Komutlar `.\pre-push\pre-push.cmd full dev` ve `.\sonarqube\sonar.cmd scan dev`.
Pre-push log kapanışı 2026-10-21 06:23:51 +03; Sonar log kapanışı 06:27:35 +03.
Yerel Git branch feature/hardware-service; runner ayrı Sonar branch parametresi
vermez, sunucu main dalını kullanır. Analiz tüm backend'i kapsar.
Sonar analysis ad6e17fe-a77a-4269-92b7-9a64fda8e8c9; CE task
df8d4cdf-a7e7-4c8b-a24f-1c705ef247c5 SUCCESS; analiz tarihi 06:27:15 +03,
işleme bitişi 06:27:33 +03. Sonar revision mevcut HEAD 6756210'ı gösterir;
HW-03 kaynakları çalışma ağacındadır, bu bir HW-03 commit SHA'sı değildir.

Başarısız ilk DB koşusu ve üç MINOR Sonar bulgusunun düzeltme geçmişi dahil tüm
komut/log kanıtı [kalite kaydında](../testing/LTZ-ALP-HW-03-quality.md).
Mevcut LTZ container'ları durmuştu; yalnız mevcut image/volume ile başlatıldı.
Rebuild/deploy veya veri silme yapılmadı. HTTP health mevcut image'ları kontrol eder;
yeni kaynak kodun deploy/E2E testi değildir. Yeni kod MockMvc/PostgreSQL ile doğrulandı.
Frontend repository'de bulunmadığından frontend kontrolleri uygulanmadı.

## Açık entegrasyonlar ve sınırlar

| Bağımlılık | Gerçek durum | Sonraki doğrulama |
|---|---|---|
| Auth | DEFERRED_AUTH / NOT_VERIFIED | Gerçek issuer/audience/JWKS/claim ve actor eşlemesi, token negatifleri, CSRF transport E2E |
| User/Social | DEFERRED_CONTRACT / NOT_VERIFIED; production deny | Gerçek global visibility, doğrulanmış arkadaşlık ve çift yönlü block kararı; timeout/error testleri |
| Performance/Recommendation | Yerel owner batch DTO TESTED; consumer E2E NOT_VERIFIED | Doğrulanmış kullanıcı bağlamıyla gerçek tüketici çağrıları ve sözleşme testi |

Bu ertelemeler açık kullanıcı onaylıdır; gerçek servis bağlantısı veya production
hazırlığı olarak gösterilmez. CSRF, no-store, typed actor, IDOR ve fail-closed korunur.
Gerçek Auth entegrasyonu doğrulanmadı; servis production-ready değildir.
Yeni gerçek adapter veya diğer ana görev yalnız sonraki açık kullanıcı talimatıyla ele alınır.

## Teslim durumu

Yerel alt görevler ve iki kalite kapısı doğrulandı; tek feature mesajı kullanıcıya
verildi. Kullanıcı daha sonra PDA kapanışını ve HW-04 geçişini bildirdi; 104524a
commit'i ve temiz çalışma ağacı doğrulandı. HW-03 ana kutusu `[x]`; HW-04 kendi
checkpoint'inden ilerler. Git yazma veya PDA kapatma ajan tarafından yapılmadı.
