# LTZ-HAM-REV-01 — Faz 1 teslim raporu

Tarih: 31 Ekim 2026. Sorumlu: Hamza. Branch: `feature/review-service`.

Durum: **Faz 1 ve REV-11 tamamlandı.** 31 Ekim 2026 takip doğrulamasında yalnız Review Service kapsamındaki secretsiz backend runner **BACKEND TEST CHECK PASSED** verdi. Güncel/doğrulanmış verilerle Trivy HIGH/CRITICAL taraması ve gerçek SonarQube Quality Gate / A/A/A hedefleri geçti. Tüm repo için full-dev kontrolü veya production yayına hazırlık iddia edilmez.

## Teslim edilen davranış

Sayısal puan (1–10 tam sayı), metinsel inceleme (1–5000 karakter düz metin) ve öneriyorum/önermiyorum görüşü ayrı veri/işlemler olarak uygulanmıştır. Metinsiz puan/tavsiye verilebilir. Metin silme, puan ve tavsiyeyi korur. Aynı kullanıcı/oyun puan/tavsiye tekrarları yeni satır üretmez; ikinci aktif metin 409'dur. Başkasının incelemesini düzenleme/silme 403, eski inceleme sürümü 409 döner. Eşzamanlılık PostgreSQL tekillik, transaction advisory lock, metin satır kilidi ve JPA @Version ile korunur.

Kullanıcı kimliği Auth RS256/JWKS token'ının UUID `sub` alanından alınır. `at+jwt` ve `svc+jwt` yolları ayrıdır. Doğrudan cookie/istemci kimlik başlıkları kabul edilmez. Mevcut D4 hesap silme işlemi hedefin üç veri türünü aynı transaction'da temizler; onaylı geçici SHA-256 engeli eski token ile yeniden veri üretimini engeller. 17 dakikalık engel, Auth kaynak kodundaki 15 dakikalık üst TTL ve yerel saat toleranslarından hesaplanmıştır; ayrıntı [karar kaydı](REVIEW_SERVICE_DECISIONS.md) içindedir.

## Kabul kriterleri

- REV-01: metinsiz puan, 1/10 sınırları, geçersiz/null/ondalık/metin puan reddi, kendi puanını güncelleme/okuma, tekrarda aynı sürüm/zaman, eşzamanlı tekillik, Game unavailable/kesinti.
- REV-02: token yazar kimliği, bilinmeyen userId alanı reddi, metin sınırları/Unicode boşluk/kontrol karakteri, 201+Location, tek aktif metin ve yarışta tek kazanan.
- REV-03: yalnız yazar, bağımsız puan/tavsiye, createdAt koruma, güncel version zorunluluğu, no-op ve eşzamanlı düzenlemede 200/409.
- REV-04: yalnız yazar, sürüm, tekrar 204, metnin fiziksel temizlenmesi/DELETED görünürlüğü, yeni reviewId ile yeniden yayımlama, Game erişimi olmadan silme ve bağımsız puan/tavsiye koruma.
- REV-05: metinsiz tavsiye, true/false geçişi, eksik görüşün false sayılmaması, 7/10'un otomatik görüşe dönüşmemesi, tekrar ve eşzamanlı tekillik.
- REV-11: yukarıdaki kuralların DB ve API kabul testleri; silme/yazma yarışı, engel TTL sınırı ve temizliği; gerçek TCP/JWKS/imza/RestClient/PostgreSQL akışı, chunked istek sınırı ve gerçek Game okuma timeout'u.

## Migration ve API envanteri

Yeni PostgreSQL migration'ları:

- V51__create_game_ratings.sql — game_ratings; kullanıcı/oyun UNIQUE, puan/version/zaman CHECK.
- V52__create_text_reviews.sql — reviews; aktif kullanıcı/oyun partial UNIQUE, içerik/durum/zaman CHECK, JPA validate.
- V53__create_game_recommendations.sql — game_recommendations; kullanıcı/oyun UNIQUE ve nullable olmayan bağımsız boolean.
- V54__create_review_erasure_guards.sql — yalnız hesap özeti/sona erme zamanı, süre sonu indeksi.

İzole PostgreSQL'de Flyway history 51/52/53/54 için success=true doğrulandı. V51–V53 sonraki görevlerde yeniden yazılmadı. Canlı/DEV veritabanına uygulanmadı; gerçek DEV Flyway history/bağlantısı incelenmiş değildir. Kaynakta önceden Review iş migration'ı bulunmuyordu.

Yeni 8 iş endpoint'i:

- PUT ve GET `/api/v1/review/games/{gameId}/rating`.
- POST `/api/v1/review/games/{gameId}/reviews`.
- GET, PATCH ve DELETE `/api/v1/review/reviews/{reviewId}`.
- PUT ve GET `/api/v1/review/games/{gameId}/recommendation`.

Mevcut DELETE `/internal/v1/accounts/{accountId}` veri temizleme/yarış korumasıyla tamamlandı. Tam request/response/rol/sahiplik/hata/test sözleşmesi [API kaydı](REVIEW_SERVICE_API.md) içindedir. Kullanıcı yalnız docs-review değişikliğine izin verdiği için merkezi docs/api.md değiştirilmedi; bu görevde merkezi katalog zorunluluğunun yerine kullanıcı onaylı yerel kayıt tutulmuştur.

## Çalıştırılan doğrulama

- JDK 25.0.1, Maven 3.9.16, Spring Boot 4.1.1, PostgreSQL 17.11.
- Görev sırası boyunca test toplamları: REV-01 39, REV-02 45, REV-03 48, REV-04 50, REV-05 55.
- İlk teslimde 65 test; kalite takip görevindeki son `clean verify -Pci,strict-quality`: **67 test; 0 failure, 0 error, 0 skipped; BUILD SUCCESS**.
- Checkstyle: 0 ihlal. PMD: 0 ihlal. Ratchet: eski/yeni borç 0/0, PASS. Baseline/kalite kuralları değiştirilmedi.
- Merkezi coverage: toplam **364/376 satır, %96,81**; değişen satırlar **269/278, %96,76**; baseline ve changed-line gate PASS. Manifest değiştirilmedi.
- `git diff --check`: PASS.
- İzole geçici PostgreSQL'de V51–V54 Flyway ve JPA validate: PASS.
- Bağımlılık taraması: **PASS**. Trivy 0.76.0, paketlenmiş Review runtime JAR'ında **111 paket; HIGH 0, CRITICAL 0**. Ortak scanner'ın bütün beklenen nested JAR envanteri kontrolü de geçti. Önceki TIMEOUT aşağıdaki takip göreviyle çözüldü.
- SonarQube: **PASS**. Gerçek Quality Gate OK, Security/Reliability/Maintainability **A/A/A**, tüm kod coverage **%93,2**, duplication **%0**, bug/vulnerability/hotspot **0/0/0**. Yeni kod coverage **%100**, yeni issue **0**. Toplam 13 bakım bulgusu vardır; sıfır issue iddia edilmez.
- Son seçili backend runner sonucu: **BACKEND TEST CHECK PASSED**. Diğer servislerin testleri çalıştırılmadı.

Son Surefire test sınıfları: AccountErasureTest 16; RatingApiTest 9; RecommendationApiTest 8; ReviewApiTest 13; ReviewHttpIntegrationTest 4; GameServiceClientTest 3; AccountWriteLockTest 1; ServiceTokenDecoderConfigurationTest 3; ServiceTokenDecoderLifecycleTest 7; UserTokenDecoderConfigurationTest 2; ReviewServiceApplicationTests 1. Toplam 67.

Gerçek HTTP kabul testleri Review sunucusuna TCP üzerinden gider, gerçek imza/JWKS doğrulamasını ve PostgreSQL transaction'larını kullanır. Auth/Game karşı tarafları yerel sözleşme sunucularıdır; dağıtılmış gerçek Auth/Game/Gateway E2E **NOT VERIFIED**. Controller/DB testlerinde kimlik veya Game mock'u kullanılan senaryolar bu gerçek HTTP testinden ayrıdır.

## İlk teslimdeki komutlar ve ortam engelleri (tarihçe)

İlk `node pre-push/ci/backend.mjs --modules services/review-service` denemesi, runner'ın Windows ortam allowlist'inde Compose eklenti dizinleri bulunmadığından geçici PostgreSQL başlangıcında başarısız oldu. Ortak runner değiştirilmedi. İlk Maven sandbox ağ hatası ve ayrı `.cache/m2` indirme beklemesi ardından mevcut yerel Maven cache ile `-o` kullanıldı. İlk strict PMD karmaşıklık bulgusu GameServiceClient doğrulamasını ayrı metoda bölerek giderildi; kural kapatılmadı.

Son secretsiz CI runner çağrısı aşağıdaki geçici çağrı uyarlamasını kullandı. Windows dizin değişkenleri eklendi, Maven yerel önbelleği seçildi, strict profil etkinleştirildi. Kapsam services/review-service olarak kaldı. Hiçbir gate atlanmadı.

```javascript
import path from 'node:path';
import { backendCheck } from './pre-push/ci/backend.mjs';
import { execute } from './lib/quality-tools.mjs';

process.env.JAVA_HOME = 'C:/Program Files/Java/jdk-25';
const keys = ['ProgramFiles', 'ProgramData', 'APPDATA', 'LOCALAPPDATA'];
const windowsEnv = Object.fromEntries(keys.filter(k => process.env[k]).map(k => [k, process.env[k]]));
const cache = path.join(process.env.USERPROFILE, '.m2', 'repository');
const runCommand = (command, args, options) => {
  const env = { ...options.env, ...windowsEnv, LOG_LEVEL: 'INFO' };
  const actual = command === 'mvn.cmd'
    ? ['-o', ...args.map(arg => arg.startsWith('-Dmaven.repo.local=')
      ? '-Dmaven.repo.local=' + cache : arg === '-Pci' ? '-Pci,strict-quality' : arg)]
    : args;
  return execute(command, actual, { ...options, env });
};
await backendCheck({ modules: ['services/review-service'], runCommand });
```

Açık JDK yolu ilk çağrıda tanımlı olmadığından Maven/coverage/ratchet sonrasında JAR dependency inventory `jar ENOENT` ile durdu. Derlenen aynı JAR, açık JAVA_HOME ile `dependencyCheck({modules:['services/review-service'], since:context.started})` kullanılarak ayrıca tarandı; runtime envanteri geçildi, Trivy 360 saniye sonunda `category=TIMEOUT`, `stage=timeout` ile tamamlanamadı. Bu nedenle runner bütünüyle PASS sayılmaz. Normal pre-push servis yolu gerçek ENV dosyasını yükleyebildiği için çağrılmadı; eşdeğer secretsiz backend runner seçildi. Frontend testleri kapsam dışıdır.

45 saniyelik sınırlı teşhis ham logları yayımlamadan Java DB indirme aşamasını saptadı; veritabanı kilidi sinyali yoktu. [Trivy resmî DB dokümanındaki](https://trivy.dev/docs/dev/configuration/db/) `public.ecr.aws/aquasecurity/trivy-java-db:1` kaynağı ile yalnız Java metadata DB indirmesi 50 saniyelik sınırla ayrıca denendi; o da ETIMEDOUT verdi. Repository/ortak ayar değişmedi, DB güncelleme veya tarama atlanmadı, doğrulanmamış cache temiz tarama olarak sunulmadı. Ağ mı, uzak kaynak mı, indirme hızı mı olduğu kesinleştirilmedi.

Geçici test PostgreSQL projesi ve test ağı kapatıldı; CI runner'ın kendi disposable projesi de finally temizliğiyle kapandı. Kalıcı DEV servisi/volume'ü değiştirilmedi. Son kapsam kontrolü yalnız Review backend ve docs-review değişikliklerini doğruladı.

## REV-11 takip doğrulaması — 31 Ekim 2026

### Ağ, proxy ve cache teşhisi

- `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`, `NO_PROXY` ve küçük harfli karşılıkları bu ajan sürecinde tanımlı değildi. WinHTTP doğrudan erişimde, WinINET ProxyEnable kapalıydı. GCR erişimi için proxy gereksinimi saptanmadı.
- GCR/GHCR HTTPS registry uçları beklenen 200/401 yanıtlarını verdi; TLS sertifika hatası gözlenmedi. AWS ECR ve Docker Hub registry alan adlarında curl DNS çözümlemesi başarısız oldu. GHCR blob yönlendirmesinde de bağlantı zaman aşımı gözlendi; bu iki kaynak indirme çözümü olarak kullanılmadı.
- Ana Trivy vulnerability DB cache'i vardı ve günceldi; Java DB cache'i yoktu. Java arşivi **992.405.588 bayt**; ilk 8 MiB örnek **10,529 saniye** sürdü. Cold-cache aktarımı mevcut scanner alt sürecinin 360 saniyelik sınırına sığmıyordu.
- Resmî GCR registry'sinden 8 paralel, 8 MiB HTTP Range parçasıyla indirme **825 saniyede** tamamlandı. Her response için 206/Content-Range/uzunluk kontrol edildi; eksik parça tamamlanmış kabul edilmedi. Arşivin tamamı ayrıca SHA-256 ile doğrulandı.

Doğrulanan OCI manifest özeti:
`sha256:98ebf51b04b55d545042d50f32db1c3874792cb88743e6f0d9438213640ffeef`

Doğrulanan Java DB katman/arşiv özeti:
`sha256:bc7eeaedef759f6a71815b303f777561df0b1de0dfa51964ccb76437fc41a4a4`

GHCR'nin bağımsız manifesti aynı katman özetini/boyutunu bildirdi. Manifest metni de GCR `Docker-Content-Digest` başlığıyla doğrulandı. [Resmî manuel cache yerleştirme akışına](https://trivy.dev/docs/latest/guide/advanced/self-hosting/) uygun biçimde yalnız `metadata.json` ve `trivy-java.db` düzenli dosyaları çıkarıldı; farklı ad, link, traversal veya beklenmeyen boyut reddedildi. [Java DB, JAR kimliklendirme indeksi; vulnerability DB ise CVE verisidir.](https://trivy.dev/docs/dev/configuration/db/)

Kaynak metadata zamanları (UTC):

- Java DB Version 1: UpdatedAt **2026-10-31 01:06:44**, NextUpdate **2026-11-03 01:06:44**.
- Vulnerability DB Version 2: UpdatedAt **2026-10-31 07:07:33**, NextUpdate **2026-11-01 07:07:33**.
- Java arşivinin gerçek yerel indirme tamamlanma zamanı **2026-10-31 10:44:43 UTC**. Kaynak metadata'daki `DownloadedAt=0001-01-01` aynen korundu; UpdatedAt/NextUpdate alanları yeniymiş gibi değiştirilmedi. Normal Trivy güncellik kontrolü cache'i kabul etti.

Cache yalnız `C:/Users/T_451/AppData/Local/Temp/ltz-review-trivy-MDrbjK/cache` altında kullanıldı. Bu, repository yapılandırması değildir; tekrar koşusunda yalnız çalışma zamanı `--cache-dir` argümanı gerekir. Ana DB dosyaları geçici cache'e kopyalandı; paylaşılan cache dosyaları değiştirilmedi. Skip/ignore/insecure bayrağı kullanılmadı; ortak 360 saniyelik tarama timeout'u ve HIGH/CRITICAL eşiği değişmedi. Büyük arşiv aktarımı, güvenlik taramasından önce ayrı hazırlık adımı olarak tamamlandı.

### Gerçek bağımlılık taraması

Son taze build'de Trivy **0.76.0**, **1 sonuç girdisi / 111 paket**, **HIGH 0 / CRITICAL 0**, exit **0**. `dependencyCheck` paketlenmiş runtime JAR'ının her beklenen nested JAR girişini `scannerContract` ile doğruladı ve PASS verdi. Yalnız POM üzerinden yaklaşık tarama yapılmadı. HIGH/CRITICAL bulgu olmadığı için bağımlılık sürümü veya parent POM değiştirilmedi; LOW/MEDIUM için temiz sonuç iddia edilmez.

Son taranan JAR SHA-256:
`9877fcaf2cb8cd73f800f7d61d7f85b78b1f5eb34de0a1b83864dfe5dba7315e`

Tarama tamamlanma zamanı: **2026-10-31 13:47:52 +03:00**.

### Sonar ortam yükleme ve gerçek Quality Gate

Normal `sonar.cmd scan dev` akışı `runScan → loadSonarEnv → loadProfileEnv` kullanır. Loader yalnız allowlist'teki Sonar alanlarını `.env.dev` profilinden okur ve terminal değerlerini önceliklendirir. Bu takip görevinde aynı mevcut loader kullanıldı: terminalde token yoktu, profil yüklendikten sonra token vardı ve `/api/authentication/validate` **valid=true** döndü. Önceki anonim 401, özel secretsiz süreçte token'ın yüklenmemesinden kaynaklanıyordu; geçersiz token veya token yetki hatası değildi.

Token yalnız bellekte ve scanner child environment'ında taşındı; komut argümanına/sohbete/loga yazılmadı. `.env`/`.env.dev` değiştirilmedi; yeni token oluşturulmadı. Normal tam-repo runner başka modülleri de analiz ettiğinden doğrudan çağrılmadı; mevcut loader/API helper'ları ve Maven Sonar plugin'i yalnız Review modülünde kullanıldı. Analiz `ltz-review-service` adlı ayrı proje anahtarına gönderildi; ortak `ltz-backend` analizi üzerine Review alt kümesi yazılmadı. Sonar way gate ayarları değiştirilmedi ve mevcut koşulların repo eşikleriyle uyumlu olduğu doğrulandı.

İlk gerçek analizde gate OK olmasına rağmen tüm kod Reliability **C** idi. `AccountWriteLock` için `java:S2259` potansiyel nullable Boolean unboxing bulgusu ve `java:S6809` transaction self-invocation bulguları saptandı. Yalnız gerekli Review düzeltmesi yapıldı: null sonuç fail-closed 403 olur; kilit yardımcısı private olurken dış `requireWritable`/`block` metotlarının MANDATORY transaction zorunluluğu korunur. Yeni null ve transaction sınırı regresyonlarıyla toplam test 65'ten 67'ye çıktı. Davranış/API/migration/servis sınırı değişmedi.

Son gerçek analiz kanıtı:

- Proje: [LTZ Review Service](http://localhost:5199/dashboard?id=ltz-review-service).
- CE task: `090ded7c-c222-40c1-8043-7e3268252e56`, status **SUCCESS**.
- Analysis ID: `24ed45a0-20c4-494e-9f58-1db932feda68`.
- `/api/qualitygates/project_status?analysisId=...`: **OK**, ignoredConditions=false.
- Gerçekte değerlendirilen yeni kod koşulları: coverage **%100 ≥ %80**, duplication **%0 ≤ %3**, yeni issue **0**.
- Tüm kod hedefleri: coverage **%93,2**, duplication **%0**, Security/Reliability/Maintainability **A/A/A**; ortak `overallTargets` helper'ı **true** döndü.
- Bug **0**, vulnerability **0**, hotspot **0**. Toplam **13 CODE_SMELL** kaldı (çoğu test/biçem notu); bunlar sıfır bulgu gibi gösterilmedi, ignore/false-positive olarak kapatılmadı. Bu takip görevi gereksiz biçem refactor'una genişletilmedi.
- Henüz commitlenmemiş dosyalar için SCM/blame uyarısı vardır. Git staging/commit yapılarak veya SCM analizi kapatılarak gizlenmedi; gerçek gate ve yeni kod koşulları yine değerlendirilip geçti.

Sonar kapsam oranı ile JaCoCo LINE oranı farklı ölçümlerdir; %93,2 Sonar coverage ile %96,81 JaCoCo satır kapsamı aynı isimle sunulmaz.

### Son toplu yeniden doğrulama

Trivy ve Sonar kontrollerinin ardından mevcut `backendCheck` yalnız `services/review-service` için tekrar çalıştırıldı. Runner kendi disposable PostgreSQL projesini oluşturup finally kapattı. Windows eklenti dizinleri, JDK25, mevcut Maven cache'i ve `strict-quality` yalnız alt süreçte verildi. Güvenlik adımı aşağıdaki çalışma zamanı adaptörüyle **aynı** `dependencyCheck/runTrivy/scannerContract` üzerinden gerçekten çalıştırıldı; boş/no-op kontrol enjekte edilmedi:

```javascript
import { spawnSync } from 'node:child_process';
import { dependencyCheck } from './pre-push/ci/dependencies.mjs';

const cache = 'C:/Users/T_451/AppData/Local/Temp/ltz-review-trivy-MDrbjK/cache';
const scanDependencies = options => dependencyCheck({
  ...options,
  run: (command, args, settings) => spawnSync(command,
    /trivy(?:\.exe)?$/i.test(command) ? [...args, '--cache-dir', cache] : args,
    settings),
});
// Önceki örnekteki JDK/Windows/Maven runCommand adaptörüyle birlikte:
await backendCheck({ modules: ['services/review-service'], runCommand, scanDependencies });
```

Sonuç **BACKEND TEST CHECK PASSED**: 67/67 test, strict Checkstyle/PMD 0 ihlal, ratchet 0 yeni ihlal, merkezi coverage/baseline PASS ve bağımlılık taraması PASS. Manuel ve runner'a ait geçici PostgreSQL projeleri/ağları kapatıldı; DEV PostgreSQL veya kalıcı volume'lere müdahale edilmedi. Tüm repo full-dev, frontend ve canlı karşı servis E2E çalıştırılmadı.

Bu takip görevinde kod değişikliği yalnız `AccountWriteLock.java`, `RecommendationApiTest.java` ve yeni `infrastructure/persistence/AccountWriteLockTest.java` ile sınırlıdır. Dokümantasyon değişikliği bu rapor ve `REVIEW_SERVICE_PROGRESS.md` içindedir. Diğer servisler, root/ortak araçlar, ENV, Compose, frontend ve Gateway değişmedi. Faz 2/3/4'e geçilmedi.

## Sınırlar ve Faz 2 ön koşulları

- Yalnız Review backend ve docs-review değiştirildi. Diğer servisler, frontend, Gateway, Compose, ENV, parent POM ve ortak kalite politikaları değişmedi.
- Faz 2/3/4 kodu, migration'ı veya kabul testi geliştirilmedi. Beğeni/rapor/moderasyon/sıralama ve puan ortalama/batch özet uçları bu teslimde yoktur.
- Faz 2 ancak kullanıcı onayıyla başlar; Admin/Notification sözleşmeleri ve mevcut User olay sözleşmesi ilgili görevde okunur. Bu rapor yeni dış entegrasyon kararı veya uygulama içermez.
- Canlı Auth/Game/Gateway akışı ve Swagger ekranı doğrulanmadı. Sonar yalnız Review projesinde takip görevinde doğrulandı. Deploy/restart yapılmadı.
- JWT iptal/rol/hesap durumu değişiklikleri için mevcut Auth sözleşmesindeki token ömrü gecikmesi sürer. Geçici engel yalnız tamamlanan hesap silmesinden sonra veri yeniden oluşturmayı korur; introspection yerine geçmez.
- Guard temizliği servisin çalışmasını gerektirir. Hashler kişisel veriyle ilişkilendirilebilir teknik özetlerdir; açık kimlik tutulmaması sınırsız saklama izni değildir.
- Commit/push/stage/merge veya branch değişikliği yapılmadı; başlangıçtaki iki takip edilmeyen kullanıcı Markdown dosyası korundu.

## PR açıklama taslağı

Başlık: `feature: LTZ-HAM-REV-01 bağımsız puan, inceleme ve tavsiye işlemleri`

Review Service artık doğrulanmış kullanıcıların kanonik LTZ oyunlarına metinsiz 1–10 puan, metin incelemesi ve bağımsız tavsiye yazmasını destekler. Metin yalnız sahibi tarafından sürüm kontrolüyle düzenlenir/silinir; silme puan/tavsiyeyi korur. PostgreSQL tekillik/kilitleri tekrar ve eşzamanlı istekleri güvenceye alır. D4 hesap silmesi tüm yerel veriyi temizler ve kaynak koddan doğrulanan token üst ömrünü kapsayan geçici hash engeliyle yeniden yazmayı durdurur.

V51–V54, sekiz korumalı iş endpoint'i ve yerel API/karar/ilerleme kayıtları eklendi. 67 test, PostgreSQL/Flyway/JPA, gerçek HTTP/JWKS/timeout/istek boyutu testleri ve strict verify PASS; toplam satır kapsamı %96,81. Trivy 111 pakette HIGH/CRITICAL 0/0; Review Sonar Quality Gate OK ve A/A/A. Canlı karşı servis E2E çalıştırılmadı. Merkezi API kataloğu, kullanıcının dokümantasyon kapsam sınırı nedeniyle değiştirilmedi.

Review kapsamındaki kalite kapanışı tamamlandı. Ortak ayar/politika değiştirilmedi; son runner BACKEND TEST CHECK PASSED verdi. Sonar kaynak/blame uyarıları ve kalan 13 bakım bulgusu aşağıdaki kanıtta ayrıştırılmıştır.

## Değişen dosyalar

Aşağıdaki liste Git çalışma alanındaki bu göreve ait dosyaları kapsar; üretilen target/.cache çıktıları dahil değildir.

- `backend/services/review-service/src/main/java/com/ltz/review/api/controller/RatingController.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/controller/RecommendationController.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/controller/ReviewController.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/dto/request/CreateReviewRequest.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/dto/request/RateGameRequest.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/dto/request/RecommendGameRequest.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/dto/request/UpdateReviewRequest.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/dto/response/RatingResponse.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/dto/response/RecommendationResponse.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/dto/response/ReviewResponse.java`
- `backend/services/review-service/src/main/java/com/ltz/review/api/exception/AccountErasureExceptionHandler.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/mapper/ReviewMapper.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/port/GameCatalogPort.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/AccountErasureService.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/RatingService.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/RatingWriter.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/RecommendationService.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/RecommendationWriter.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/ReviewService.java`
- `backend/services/review-service/src/main/java/com/ltz/review/application/service/ReviewWriter.java`
- `backend/services/review-service/src/main/java/com/ltz/review/domain/entity/Review.java`
- `backend/services/review-service/src/main/java/com/ltz/review/domain/exception/ReviewException.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/client/dto/response/GameBatchResponse.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/client/GameServiceClient.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/config/ErasureGuardCleanup.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/persistence/repository/AccountWriteLock.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/persistence/repository/RatingRepository.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/persistence/repository/RecommendationRepository.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/persistence/repository/ReviewRepository.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/security/AccountErasureSecurityConfiguration.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/security/ReviewRequestFilter.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/security/UserTokenDecoderConfiguration.java`
- `backend/services/review-service/src/main/java/com/ltz/review/infrastructure/security/UserTokenPolicy.java`
- `backend/services/review-service/src/main/resources/application.properties`
- `backend/services/review-service/src/main/resources/db/migration/V51__create_game_ratings.sql`
- `backend/services/review-service/src/main/resources/db/migration/V52__create_text_reviews.sql`
- `backend/services/review-service/src/main/resources/db/migration/V53__create_game_recommendations.sql`
- `backend/services/review-service/src/main/resources/db/migration/V54__create_review_erasure_guards.sql`
- `backend/services/review-service/src/test/java/com/ltz/review/api/controller/AccountErasureTest.java`
- `backend/services/review-service/src/test/java/com/ltz/review/api/controller/RatingApiTest.java`
- `backend/services/review-service/src/test/java/com/ltz/review/api/controller/RecommendationApiTest.java`
- `backend/services/review-service/src/test/java/com/ltz/review/api/controller/ReviewApiTest.java`
- `backend/services/review-service/src/test/java/com/ltz/review/api/controller/ReviewHttpIntegrationTest.java`
- `backend/services/review-service/src/test/java/com/ltz/review/infrastructure/client/GameServiceClientTest.java`
- `backend/services/review-service/src/test/java/com/ltz/review/infrastructure/persistence/AccountWriteLockTest.java`
- `backend/services/review-service/src/test/java/com/ltz/review/infrastructure/security/UserTokenDecoderConfigurationTest.java`
- `docs-review/REVIEW_SERVICE_API.md`
- `docs-review/REVIEW_SERVICE_DECISIONS.md`
- `docs-review/REVIEW_SERVICE_PHASE_01_REPORT.md`
- `docs-review/REVIEW_SERVICE_PROGRESS.md`
