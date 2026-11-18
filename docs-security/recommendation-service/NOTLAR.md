# recommendation-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Alper.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

### Library entegrasyonu ve REC-03 — 2026-11-18

- Library `recommendation-signals` Auth hesaba bağlı `signals:read` ile tüketilir; 403 → `SOURCE_ACCESS_DENIED`, diğer hatalar 503, tek deneme, 500 sinyal sınırı. Karar kaydı: [library-integration-decisions](../../docs-recommendation/decisions/library-integration-decisions.md).
- REC-03: Review `ratings/mine` + Game GAME-15 tür kodları `RATING_AFFINITY` olarak sıralamaya girer; kaynak hatasında istek 503 ile durur. Birim testleri PASS; PostgreSQL gerektiren IT bu oturumda koşulmadı, gerçek çok servis E2E NOT VERIFIED.

### REC-15 etkinlik önerisi — 2026-11-18

- `GET /api/v1/recommendation/events`: Event EVT-04 listesi (kullanıcı token'ı, ≤4×50) + Game GAME-15 türleri; PLANNED/ACTIVE, bitmemiş, kendi etkinliği hariç, Event `joinable=false` (katılmış/dolu) elenir. Katılım cezası öneriyi etkilemez (cezalı kullanıcıya da öneri yapılır); ceza sınırlaması yoktur. 11 birim testi PASS (EventRecommendationServiceTest 6, HttpEventDiscoverySourceTest 5); `mvn -pl services/recommendation-service test` tamamı bu oturumda PostgreSQL gerektiren IT'ler nedeniyle çalıştırılamadı (context hatası, NOT VERIFIED); Event ile canlı E2E ve PostgreSQL IT yok (NOT VERIFIED). Yeni ENV/migration yok.

### TASK 5 / final güncel durum — 2026-11-15

- REC-22 kapalı rehberlik gerçek CPython ile çalışır; POST assistant mevcut JWT/rol/rıza/
  ACTIVE/kota modelini kullanır. Kullanıcı kimliği/token/ham profil Python'a taşınmaz;
  2 process/2 sn, stdin/stdout/stderr sınırları ve finite template/step kontrolü vardır.
- Runtime/Docker/portable kurulum açık kullanıcı onaylı [ADR 0003](../../docs/decisions/0003-recommendation-python-yardimcisi.md).
  LLM/sidecar/pip/ENV/migration yoktur. Geçici script normal bean kapanışında temizlenir.
- 80 Java + 7 Python test PASS; LINE %97,51, merkezi ek/değişen kod %97,70; lint yeni ihlal 0,
  JAR ve final image CVE gate PASS. Docker non-root/ağsız/read-only packaged smoke PASS.
- Native pre-push/Sonar gerçek profil okuması gerektiriyor; oturumluk korumada BLOCKED.
  Sonar localhost:5199 erişilemedi, terminal token'ı yok; scanner/gate/A'lar UNKNOWN.
  CI PASS bunların yerine sayılmaz. [Final kapı kanıtı](../../docs-recommendation/testings/pre-merge-quality.md).
- Genel durum 14/23 DONE (desteklenen kapsam); önceki Game/Review/User/Social/Event ve
  Performance bağlamı açıkları sürer. Canlı çok servis E2E NOT VERIFIED; **NOT READY**.
- Windows quoting, PMD kaynak/cause/complexity ve Docker stdlib sorunları düzeltildi;
  [ara koşular dahil tam rapor](../../docs-recommendation/testings/task-05-tests.md).

### TASK 4 güncel durum — 2026-11-15

- REC-13/14/15 **BLOCKED (0/3)**: izinli User aday/tercih batch'i ve Social eligibility/
  Recommendation grant'ı yok; Event yalnız launcher. [Karar ve owner talepleri](../../docs-recommendation/decisions/task-04-decisions.md).
- Production allowlist/denyAll değişmedi. Üç yeni negatif probe testi A/B/ADMIN reddini,
  sahte kimlik/private alanlarının etkisizliğini, servis token ayrımını ve kaynak iş çağrısı/
  DB yazısı yokluğunu doğrular. Yeni öneri endpoint'i veya sıralama özelliği değildir.
- Anonim GET 401, Bearer/CSRF'siz POST mevcut CSRF'de 403; kullanıcı token'ıyla GET/POST 403.
  API testindeki gerçek kota limiter'ına test Clock import'u eklendi; dakika sınırı kararsızlığı giderildi.
- 69 test PASS, LINE %97,48, merkezi ek/değişen kod %97,71; Checkstyle/PMD yeni ihlal 0,
  dependency gate PASS. [Tüm koşular ve sınırlar](../../docs-recommendation/testings/task-04-tests.md).
- User/Social/Event canlı E2E ve feature eligibility/ranking NOT VERIFIED/NOT IMPLEMENTED.
  ENV/üretici/DB/migration/production Java değişmedi. TASK 5 ve final Sonar/pre-push başlatılmadı.

### TASK 3 güncel durum — 2026-11-15

- Owner hardware assessment JWT/rıza/ACTIVE grant ve ortak generation kotası kullanır;
  kaynak profil/oyun kimliği, sürüm, tam sayfa ve yanıt şeması doğrulanır. Rıza geri çekilmesi
  önceki owner geçmişini temizler. Assessment profil snapshot'ı veya history yazmaz.
- Minimum/recommended alan bazlı karşılaştırma, koşullu grafik, kanıtlı RAM/disk açığı,
  socket/DDR kontrolü testlidir; eksik teknik alanlar UNKNOWN kalır.
- REC-06 katalog batch ve REC-09 bağlamlı FPS eksik: reliable bayrağı tahmin güveni değildir;
  FPS INSUFFICIENT_DATA ve değerler null. TASK 3 PARTIAL; [checkpoint](../../docs-recommendation/progress/task-03.md).
- 66 test PASS; LINE %97,48, merkezi ek/değişen kod %97,71; Checkstyle/PMD yeni ihlal 0,
  dependency gate PASS. Gerçek Auth issuer/JWKS ve geçici PostgreSQL; kaynak HTTP fixture'ları
  canlı çok servis E2E yerine sayılmadı. [Kanıt](../../docs-recommendation/testings/task-03-tests.md).
- ENV/bağımlılık/migration ve üretici servis değişikliği yok; final Sonar/pre-push TASK 5'te.

### TASK 2 güncel durum — 2026-11-14

- JWT/JWKS kullanıcı ve D4 doğrulaması, canonical role, ProblemDetail/no-store, body ve oran
  sınırları uygulanır. Varsayılan in-memory kullanıcı/Basic/form fallback yoktur.
- User rızası ve Auth account-bound ACTIVE grant kişisel işlemlerde kontrol edilir; session
  revocation için online introspection yoktur, 60 sn access TTL + 30 sn tolerans geçerlidir.
- V12 owner history/feedback; cursor, UNIQUE/version/account lock, 30 gün/500 run retention,
  consent purge ve D4 cascade testlidir. Gerçek kaynak servisler ve Auth worker E2E NOT VERIFIED.
- REC-02/05 favori/tamamlanan tür benzerliği 2026-11-18'de bağlandı (HttpLibraryTasteSource; Library signals + Game GAME-15,
  fail-closed). Güncellenen PostgreSQL IT'si çalıştırılamadı: NOT VERIFIED. REC-16 sistem filtresi kısmi; Task 2 bütünü PARTIAL.
  [Güncel checkpoint](../../docs-recommendation/progress/task-02.md).
- 45 test PASS; toplam LINE %96,83, merkezi ek/değişen kod %97,15; Checkstyle/PMD 0 ihlal,
  dependency gate PASS. [Komut/kanıt](../../docs-recommendation/testings/task-02-tests.md).

### TASK 1 tarihsel notu

- 2026-11-14 / TASK 1: servis içi sürümlü motor ve V11 motor analitiği temeli eklendi;
  iş HTTP ucu ve Recommendation'ın Auth/downstream tüketici adaptörleri henüz yoktur.
  Başlangıç 401/CSRF 403 testleri gerçek JWT/RBAC/IDOR kabulü değildir.
- DB kullanan testler `DisposableDatabaseInitializer` ile yalnız `-Pci` disposable
  PostgreSQL'e bağlanır. Gerçek DEV/production bağlantısına geri dönüş yoktur.
- Son doğrulama: 29 test, JaCoCo toplam %98,57 / değişen kod %100; Checkstyle/PMD
  0 ihlal, dependency gate PASS. [Tam komut ve Windows Compose uyarlaması](../../docs-recommendation/testings/task-01-tests.md).
