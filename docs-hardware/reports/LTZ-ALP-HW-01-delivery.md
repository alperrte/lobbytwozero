# LTZ-ALP-HW-01 — Bilgisayar Profili Oluştur, Düzenle ve Sil

Tarih: **2026-10-21** (Europe/Istanbul). Sorumlu: **Alper**.
Durum: **DONE — DEFERRED_AUTH**. Alt görevler **7/7**, toplam **7/17**.
Kullanıcı PDA kapanışını 2026-10-21 tarihinde bildirdi; kök master ana task kutusu [x].

## 1. Teslim kapsamı

Bir kullanıcıya ait birden fazla bilgisayar profili, manuel veya katalogdan seçilmiş
bileşenler, güncelleme/kaldırma, teknik detaylar, kayıtlı donanım özetleri ve profil silme
uygulandı. Kullanıcı kimliği istemciden alınmaz; tüm iş uçları owner/protected'dır.
Gerçek Auth doğrulayıcısı gelene kadar üretim iş istekleri reddedilir.

| Alt görev | Sonuç | Uygulama ve test kanıtı |
|---|---|---|
| HW-01 Profil oluşturma | PASS | HardwareProfileService.create; HardwareProfileControllerTest: owner, validation, mass assignment, CSRF ve anonim erişim |
| HW-02 Manuel/model/marka seçimi | PASS | HardwareComponentService.add; 12 kategori, markasız model, opsiyonel üretici, teknik alanlar ve V2 tekillik testleri |
| HW-03 Donanım güncelleme | PASS | Profil PUT, bileşen PUT/DELETE; IDOR, stale version, rollback, çapraz profil ve gerçek paralel writer testleri |
| HW-04 Birden fazla bilgisayar | PASS | Owner-scoped liste, DESKTOP/LAPTOP/OTHER, stabil sıralama ve sayfa boyutu sınırları |
| HW-05 Donanım detayları | PASS | Owner-scoped GET detay; sayfalı bileşenler, kayıtlı/null teknik değerler |
| HW-15 İstatistikler | PASS | HardwareStatisticsService.statistics; kayıt/adet ayrımı, yalnız kayıtlı kapasite, eksik kapasite ve owner izolasyonu |
| HW-16 Profil silme | PASS | Owner + güncel sürüm + CSRF; gerçek FK CASCADE, başka profilleri/kataloğu koruma ve silme sonrası özet testi |

## 2. API envanteri

Kanonik sözleşme: [docs/api.md — HARDWARE](../../docs/api.md#hardware).
Controller'lar tam Gateway prefix'ini karşılar; Gateway kodu değiştirilmedi.

| Method | Path | DTO / davranış |
|---|---|---|
| POST | `/api/v1/hardware/profiles` | CreateHardwareProfileRequest → HardwareProfileResponse, 201 |
| GET | `/api/v1/hardware/profiles` | HardwareProfilePageResponse, 200 |
| GET | `/api/v1/hardware/profiles/{profileId}` | HardwareProfileDetailResponse, 200 |
| PUT | `/api/v1/hardware/profiles/{profileId}` | UpdateHardwareProfileRequest → HardwareProfileResponse, 200 |
| DELETE | `/api/v1/hardware/profiles/{profileId}` | profileVersion query, 204 |
| GET | `/api/v1/hardware/profiles/statistics` | HardwareStatisticsResponse, 200 |
| POST | `/api/v1/hardware/profiles/{profileId}/components` | SaveHardwareComponentRequest → ComponentMutationResponse, 201 |
| PUT | `/api/v1/hardware/profiles/{profileId}/components/{componentId}` | Aynı request/response, 200 |
| DELETE | `/api/v1/hardware/profiles/{profileId}/components/{componentId}` | profileVersion query; 204 + X-Profile-Version |

Liste/detay: varsayılan sayfa 0, boyut 20; boyut 1..100. Sıralama createdAt/id ile sabittir.
Mutasyonlarda güncel profileVersion gerekir; create dışındaki yazmalar parent profil
satırını kilitler. Eski sürüm 409, olmayan/başkasına ait kaynak aynı güvenli 404 yanıtını alır.
POST/PUT gövdeleri Content-Length bulunmasa da 16 KiB ile sınırlıdır (413).
Validation/JSON hataları 400; kimlik yoksa 401, CSRF/güvenilmeyen principal için 403.
Hatalar güvenli ProblemDetail, özel yanıtlar private/no-store biçimindedir.

## 3. Veri modeli ve migration'lar

DB sahibi yalnız hardware-service; `user_id` dış UUID REF'tir, başka DB'ye FK/SQL yoktur.

| Migration | İçerik |
|---|---|
| V1__create_hardware_profiles.sql | UUID profil/owner REF, ad, bilgisayar türü, MANUAL kaynak, @Version karşılığı, UTC zamanları, CHECK ve owner/created_at/id indeksi |
| V2__create_hardware_components_and_selection_catalog.sql | Bileşenler ve seçim için asgari genel ürün/marka/ürün-marka tabloları, FK/unique/CHECK/index'ler |

- `hardware_profiles` → `hardware_profile_components`: gerçek FK, ON DELETE CASCADE.
- `hardware_products`: `(category, normalized_model)` tekilliği markadan bağımsızdır;
  nullable marka birleşimi üzerinden mükerrer genel model oluşturulmaz.
- `hardware_brands`: normalized_name unique. `hardware_product_brands`: bileşik PK ve iki FK.
- Bileşenin ürün/kategori ve opsiyonel ürün/marka ilişkisi bileşik FK'lerle de korunur.
- Bileşen model/marka adları snapshot'tır; katalog seçimi ve manuel alanlar karıştırılmaz.
- capacityGb, clockMhz, coreCount, powerWatts, memoryType, socket, interfaceType,
  formFactor sınırlı teknik alanlardır; eksik değer uydurulmaz.
- JPA `ddl-auto=validate`, `open-in-view=false` korunur. Entity API'ye dönmez.
- Uygulanan V1 değiştirilmedi; V2 sonraki boş numaradır. Reset/repair/clean migration yapılmadı.
  Test seed'i prod migration içinde değildir. Canlı hardware DB migration durumu doğrulanmadı.

Mutasyonlar application/service transaction sınırında; PESSIMISTIC_WRITE parent kilidi,
istemci profileVersion kontrolü ve JPA @Version birlikte kullanılır. Aynı sürümlü iki
paralel writer testinde yalnız biri 201, diğeri 409 aldı. Detay ve istatistikler
REPEATABLE_READ snapshot kullanır. İstatistikler owner filtresiyle DB'de toplanır;
RAM/depolama için yalnız kayıtlı capacityGb × quantity toplamı ve bilinmeyen adetler sunulur.

## 4. Dosya ve katman envanteri

Kaynak kökü: `backend/services/hardware-service/src/main/java/com/ltz/hardware/`.

- API: HardwareProfileController, HardwareComponentController; dört request ve sekiz
  response record'u; HardwareExceptionHandler.
- Application: HardwareProfileService, HardwareComponentService, HardwareStatisticsService;
  HardwareProfileMapper, HardwareComponentMapper; CurrentActor ve AuthenticatedActor.
- Domain: HardwareProfile, HardwareComponent, HardwareSpecifications, HardwareProduct,
  HardwareBrand; DeviceType, ComponentCategory; not-found/conflict istisnaları.
- Infrastructure: dört repository, HardwareSecurityConfiguration,
  SecurityContextCurrentActor, HardwareRequestBodyLimitFilter.
- POM: parent tarafından yönetilen WebMvc test ve PostgreSQL Testcontainers bağımlılıkları
  yalnız test scope'ta; kullanıcı onaylıdır. Üretim bağımlılığı eklenmedi.
- Test: HardwareProfileControllerTest, HardwareComponentControllerTest,
  HardwareProfileTest, PostgresIntegrationSupport ve izole DB ile contextLoads.
- Doküman: kök master/checklist; docs/api.md (yalnız HARDWARE), database/ER/architecture
  hardware bölümleri, folder-structure rapor kökü ve docs-hardware kayıtları.
- Kullanıcının ayrıca onayladığı dar kalite düzeltmesi: lib/profile-env.mjs,
  sonarqube/sonar.mjs, sonar.test.mjs ve sonarqube.md. Mevcut DB ayarları yalnız Maven
  test alt sürecine aktarılır; host portu eşlenir, secret'lar maskelenir.

Kesin çalışma günlüğü: [progress](../progress/LTZ-ALP-HW-01.md).

## 5. Test ve iki zorunlu kalite kapısı

| Kontrol | Gerçek sonuç |
|---|---|
| Hardware testleri | 58 PASS, 0 failure/error/skipped: controller 32 + 16, domain 9, context 1 |
| Gerçek DB | Testcontainers PostgreSQL 17.11; V1/V2 Flyway ve JPA validate PASS |
| Hardware JaCoCo | 321/332 satır = **%96,69**; hariç tutma eklenmedi |
| Ortak runner regresyonları | **44 PASS**, 0 failure/skipped; sentetik fixture'lar |
| Sonar öncesi bütün backend | **76 PASS**, 0 failure/error/skipped, 18 modül |
| Final pre-push | **PASS**, exit 0; hardware-service scope, 2026-10-21 01:13:28 +03:00 |
| Sonar Security / Reliability / Maintainability | **A / A / A** |
| Sonar Quality Gate / overall targets | **PASS / PASS**, scan exit 0 |
| Sonar overall coverage / duplication | **%84,3 / %0** |
| Sonar yeni kod dönemi | Coverage %100, duplication %0; mevcut Sonar baseline'ına göredir |
| Açık BLOCKER/CRITICAL / vulnerability / hotspot | Son analiz sonrasında yerel API'den **0 / 0 / 0** |

Komutlar:

```text
node --test sonarqube/sonar.test.mjs pre-push/menu.test.mjs pre-push/quality.test.mjs pre-push/runtime-checks.test.mjs ops/env.test.mjs
.\pre-push\pre-push.cmd hardware-service
.\sonarqube\sonar.cmd scan dev
```

Pre-push gerçek çıktısı `BACKEND TEST CHECK PASSED`; full Compose/health kontrolü iddiası yoktur.
Sonar bütün Maven backend'ini kapsar; ortak JS runner ayrıca 44 regresyon testiyle doğrulandı.
Proje `ltz-backend`, Sonar branch **main**; yerel Git dalı **chore/architecture**.
Son analiz: **9be6f62f-5bf5-44f8-9d1d-53ca6d9350fc**,
2026-10-21 **01:16:26 +03:00**. CE task: **6a037410-68d3-48ca-b4fc-2d6b14e3e6c5**.
Atanmış **Sonar way** politikası **VERIFIED**; gate gerçek analiz kimliğiyle kontrol edildi.
[Yerel Sonar paneli](http://localhost:5199/dashboard?id=ltz-backend).

Kanıt logları (UTC dosya adları):

- `logs/pre-push-2026-10-20T22-13-08-268Z-41784-a352df7e.log`
- `logs/sonarqube-scan-2026-10-20T22-13-44-831Z-24048-199ccbc3.log`

Ayrıntılı başarısız denemeler ve düzeltmeler [kalite raporunda](../testing/LTZ-ALP-HW-01-quality.md).
İlk Sonar erişim/DB ortamı engelleri gizlenmedi; runner düzeltmesi kullanıcı onayıyla yapıldı.
İlk başarılı analizdeki tek CRITICAL karmaşıklık bulgusu resolveBrandName ayrıştırmasıyla
kapatıldı ve son kod üzerinde pre-push/Sonar tekrar PASS oldu.

## 6. Güvenlik ve açık bağımlılıklar

**DEFERRED_AUTH:** Gerçek Auth/JWT/cookie entegrasyonu doğrulanmadı; **production-ready değildir**.
Typed actor yalnız doğrulanmış SecurityContext sözleşmesinden alınır; üretimde actor
oluşturan bir doğrulayıcı/provider yoktur. Header, Basic, Bearer veya cookie kimlik oluşturmaz.
Mock aktör yalnız test kapsamındadır. CSRF korunur; JWT claim/issuer/audience/rol modeli,
anahtar dağıtımı, revocation ve cookie taşıma protokolü tahmin edilmedi.

User global görünürlük, Performance/Recommendation veri paylaşımı, katalog CRUD/arama,
yorum/moderasyon ve masaüstü sync sonraki ana görevlerin kapsamıdır; uygulanmış sayılmaz.
Bu görevde başka kullanıcıya veri açan API veya harici servis çağrısı yoktur.
FPS/sıcaklık/ölçüm üretilmedi veya hardware DB'sine taşınmadı.
[Auth mutabakat kaydı](../decisions/auth-integration.md),
[onaylı runner kararı](../decisions/quality-runner-env-proposal.md).

## 7. Kullanıcı teslim adımı

Kullanıcı PDA üzerinden **LTZ-ALP-HW-01** kapanışını bildirdi; ana task checkbox'ı
işaretlendi. Kullanıcının `fc23563` commit'i feature/hardware-service üzerinde
salt okunur doğrulandı; kapsam test edilen sürümle aynı. Git add/commit/push,
branch değişikliği veya PDA durum değişikliği ajan tarafından yapılmadı.
[Kapanış kaydı](../progress/LTZ-ALP-HW-01.md#pda-kapanışı--2026-10-21).
Sonraki ana taska başlanmadı; yeni kullanıcı talimatına kadar **STOP**.
