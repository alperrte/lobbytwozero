# TASK 1 sorun ve sınırlama kaydı

2026-11-14 · TASK 1 kod/test kapsamını bloke eden açık sorun yoktur.

| Kayıt | Durum | Kanıt / çözüm |
| --- | --- | --- |
| Windows araç ortamında Docker Compose eklentisi bulunamıyor | RESOLVED (yerel komut uyarlaması) | İzole runner'ın `runCommand` enjeksiyonuyla aynı Compose argümanları kurulu docker-compose.exe'ye verildi; gerçek ENV ve ortak runner değişmedi |
| Mockito inline agent Türkçe Windows yolunda yüklenemiyor | RESOLVED | Yalnız interface kullanan unit testlerde ajan gerektirmeyen küçük sözleşme test uygulamaları kullanıldı; production mock yok |
| Launcher'a annotation eklemek merkezi coverage'da ölçülemeyen semantik değişiklik | RESOLVED | Scheduling analitik bileşenine taşındı; launcher orijinal kaldı; policy/manifest değişmedi |
| Eski docx ve mimari metinleri güncel kodla çelişiyor | DOCUMENTED | PostgreSQL/Java25/Boot4.1.1 ve güncel Auth kodu esas; `integration/contracts.md` |
| Bağımsız `LTZ_PDA_Alper*.md` repoda yok | DOCUMENTED | Master v2 §4 isim/açıklamaları aynen kullanıldı; ayrı kaynakla karşılaştırma iddiası yok |
| Gerçek Recommendation Auth/downstream tüketicisi ve kullanıcı HTTP akışı yok | NOT IMPLEMENTED | TASK 1 yalnız servis içi temel; 401/403 smoke testi JWT/RBAC/IDOR kabulü değildir |
| REC-23 feedback/gösterim/tıklama olayları yok | NOT IMPLEMENTED (ürün akışları) | Kaynak işlem olmadan sayaç icat edilmedi; TASK 1 motor kullanım/sonuç gözlemleri ve tasarım sınırı testli |
| Python çalışma modeli | KARAR BEKLİYOR | Mevcut mimari §10 korunur; TASK 1 sürümlü port Python çalıştırıldığı iddiası taşımaz |

Sonar ve legacy pre-push final gate bu checkpoint'te çalıştırılmadı; TASK 5'e ayrılan
kapıların geçtiği veya projenin merge-ready olduğu iddia edilmez.

## TASK 2 güncel kayıt — 2026-11-14

TASK 2 **PARTIAL**; üstteki TASK 1 envanteri kendi teslim tarihini anlatır.

| Kayıt | Durum | Sonuç / owner |
| --- | --- | --- |
| Recommendation Auth/HTTP/history/feedback tüketicisi yok | RESOLVED (TASK 2 kapsamı) | Gerçek JWT/JWKS ve kaynak RestClient adaptörleri; 45 test PASS; canlı çok servis E2E NOT VERIFIED |
| REC-02/05 Game batch metadata | BLOCKED | Hamza; [Game sözleşme talebi](../integration/dependencies/game-service-requests.md). Kartlara tür uydurulmadı; N+1 yapılmadı |
| REC-03 kişisel Review rating batch/paging | BLOCKED | Hamza; [Review talebi](../integration/dependencies/review-service-requests.md). Genel rating kişisel veri yerine kullanılmadı |
| REC-16 sistem uygunluğu filtresi | PARTIAL | Genre/platform/size var; donanım uygunluğu henüz uygulanmadı, destekleniyormuş gibi kabul edilmez |
| Mevcut secret'ın Recommendation ortamına aktarımı | APPROVED / RESOLVED | Kullanıcı açık onay verdi; gerçek ENV okunmadı/değişmedi |
| Negatif JWT testinde tarih parse hatası | RESOLVED | ClaimsSet.Builder; son koşuda PASS |
| PMD complexity (2 production, 1 test) | RESOLVED | Anlamlı schema/fixture metotları ayrıldı; suppression/baseline değişmedi; 0 ihlal |
| Auto-config in-memory kullanıcı | PREVENTED | JwtDecoder'lar explicit bean; testte UserDetailsService yokluğu doğrulandı; Basic/form kapalı |
| Consent reddinde kota sıfırlanması | RESOLVED | Geçmiş purge ve D4 limiter cleanup ayrıldı; 11. denied generation 429 regresyonu PASS |

Kapsam içindeki uygulanabilir işler test/dokümantasyonuyla hazırdır; 9/9 kabul veya merge-ready
iddiası yoktur. Sonraki ana task için açık kullanıcı talimatı beklenir; Task 3 başlatılmadı.

## TASK 3 güncel kayıt — 2026-11-15

**PARTIAL — 5/7 DONE.** Önceki checkpoint'ler tarihseldir; TASK 2 açıkları kapanmadı.

| Kayıt | Durum | Sonuç / owner |
| --- | --- | --- |
| REC-06 katalog genelinde donanım seçimi | PARTIAL | Owner tek oyun assessment var. Game batch gereksinimleri yok; Hamza, [talep](../integration/dependencies/game-service-requests.md). N+1 eklenmedi |
| REC-09 karşılaştırılabilir FPS kanıtı | PARTIAL | Performance aggregate resolution/preset/provenance/ölçüm anı profil sürümü taşımıyor; Alper, [talep](../integration/dependencies/performance-service-requests.md). Her zaman INSUFFICIENT_DATA |
| Dar Performance DTO ilavesiyle FPS üretmek | NOT SUFFICIENT | Manual report ile ham örnek arasında kanıtlı bağ yok; veri üretim modeli değişmeden alan eklemek çözüm değil, üreticiye müdahale edilmedi |
| CPU/GPU metin karşılaştırması ve kapasite birimi | LIMITED BY SOURCE | Tam tek model dışında UNKNOWN; ondalık/ikili byte sınırları; toplam disk boş disk sayılmaz |
| Yeni testte hatalı import | RESOLVED | MockMvcRequestBuilders doğru Spring test paketine alındı; son derleme/test PASS |
| İlk 66 test koşusu sonrası 5 PMD complexity bulgusu | RESOLVED | Transport/reader ve token/fixture yardımcıları ayrıldı; suppression/policy/baseline değişmedi |
| Canlı Hardware/Game/Performance E2E | NOT VERIFIED | HTTP contract fixture; gerçek Auth issuer/JWKS ve disposable PostgreSQL testli |

Son koşu 66 PASS, coverage ve tüm hedefli kalite kapıları PASS; [kanıt](../testings/task-03-tests.md).
Final pre-push/Sonar TASK 5'e ayrıldı. TASK 4 başlatılmadı.

## TASK 4 güncel kayıt — 2026-11-15

**BLOCKED — REC-13/14/15 açık (0/3 DONE).** Önceki task kayıtları tarihseldir.

| Kayıt | Durum | Kanıt / aksiyon ve owner |
| --- | --- | --- |
| İzinli aday/tercih batch'i | BLOCKED | User batch/search yalnız kart; [Kerem talebi](../integration/dependencies/user-service-requests.md). İsim taraması/N+1/özel library-rating yerine kullanılmadı |
| Social eligibility ve Auth caller izni | BLOCKED | blocks/check yalnız Messaging/LFP; [Umut + Kerem talebi](../integration/dependencies/social-service-requests.md). Recommendation kimliğine izin uydurulmadı |
| Açık/geçerli Event keşfi ve katılım uygunluğu | BLOCKED | Üreticide yalnız launcher, katalog boş; [Nisa talebi](../integration/dependencies/event-service-requests.md). Sahte etkinlik/popülerlik üretilmedi |
| İlk yeni boundary testindeki anonim POST beklentisi | RESOLVED | Beklenen 401, gerçek mevcut CSRF reddi 403; yalnız test düzeltildi, güvenlik zinciri değişmedi |
| Eski kota testinde dakika sınırı kararsızlığı | RESOLVED | İkinci koşuda 11. istek yeni dakikaya geçti; yalnız API test context'ine sabit Clock + gerçek limiter import edildi; son koşuda 429/retention/withdrawal testleri PASS |
| Pozitif öneri / eligibility / ranking / timezone kabulü | NOT IMPLEMENTED | Kritik producer sözleşmeleri olmadan feature kabulü üretilemez; üç erişim sınırı testi yerine sayılmadı |
| Canlı User/Social/Event E2E | NOT VERIFIED | Bu task üretici çağrısı yapmaz; mevcut Auth issuer/JWKS ile negatif erişim testlenir |

Güvenli bağımsız çalışma test, karar, sözleşme talepleri ve dokümantasyonla tamamlanır.
Son koşu 69 PASS / 0 skipped, coverage/lint/dependency kapıları PASS;
[tüm koşu kanıtları](../testings/task-04-tests.md). Feature blokajları çözülmüş sayılmaz.
Başka owner'ın kodu, Auth izinleri veya ENV değiştirilmedi. [Checkpoint](../progress/task-04.md).
TASK 5 başlatılmadı; final pre-push ve SonarQube bu checkpoint'in PASS iddiası değildir.
