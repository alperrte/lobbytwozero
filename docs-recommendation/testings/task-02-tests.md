# TASK 2 — test ve kalite kanıtı

2026-11-14 18:23 Europe/Istanbul · Son kaynak: **PASS**, runner exit 0.
Task gereksinim durumu PARTIAL'dır; testlerin geçmesi eksik sözleşmeleri tamamlamaz.

## Tam komut

Repo kökü / PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node --input-type=module -e "import {backendCheck} from './pre-push/ci/backend.mjs'; import {execute,withRunLog} from './lib/quality-tools.mjs'; await withRunLog(process.cwd(),'recommendation-task-02-final',log=>backendCheck({log,modules:['services/recommendation-service'],runCommand:(cmd,args,options)=>execute(cmd==='docker'?'C:/Program Files/Docker/cli-plugins/docker-compose.exe':cmd,cmd==='docker'?args.slice(1):args,{...options,env:{...options.env,DOCKER_CONFIG:process.cwd()+'/.cache/docker-ci-config'}})}));"
```

Bu TASK 1'de doğrulanan secretsiz Windows Compose executable uyarlamasıdır. Ortak runner,
Maven/coverage/quality/dependency gate değiştirilmedi veya atlanmadı. Runner gerçek ENV okumaz;
--env-file NUL ile yalnız rastgele ltz-test disposable PostgreSQL, tmpfs ve loopback port kullanır,
finally cleanup yapar. Maven `-Pci clean verify`, sentetik DB değerleri ve proje Maven settings
dosyasıyla çalışır. DB initializer normal DEV/production URL'sini reddeder.

## Gerçek sonuçlar

| Kontrol | Sonuç |
| --- | --- |
| Maven clean verify | BUILD SUCCESS / 25,143 sn / exit 0 |
| RuleBasedRecommendationEngineTest | 11 PASS |
| RecommendationExecutionServiceTest | 7 PASS |
| RecommendationObservationTest | 1 PASS |
| RecommendationAnalyticsIntegrationTest | 7 PASS |
| RecommendationSecuritySurfaceTest | 2 PASS |
| RecommendationApiIntegrationTest | 12 PASS |
| RecommendationJwtContractTest | 4 PASS |
| RecommendationServiceApplicationTests | 1 PASS |
| Toplam | **45 PASS / 0 failure / 0 error / 0 skipped** |
| PostgreSQL/Flyway/JPA | V11 + V12 temiz migration, Hibernate validate, JDBC constraint/FK/transaction PASS |
| JaCoCo LINE | **581 covered / 19 missed = %96,8333** |
| Merkezi ek/değişen executable coverage | **580/597 = %97,1524**, baseline PASS |
| Checkstyle / PMD ratchet | 0 eski / 0 yeni / 0 toplam; PASS |
| Dependency taraması | Trivy JAR inventory; 0 bulgu; HIGH/CRITICAL gate PASS |
| Compose syntax | `docker --config .cache/docker-ci-config compose --env-file NUL -f docker-compose.yml config --no-interpolate --quiet` exit 0 |
| git diff --check | exit 0; ayrıca yeni dosyaların whitespace kontrolü |
| Legacy pre-push / SonarQube final | NOT RUN — master protokolünde TASK 5 |
| Tam canlı User/Game/Library/Auth worker/Gateway E2E | NOT RUN; HTTP kaynakları kontrollü contract fixture |

Merkezi coverage origin/main tabanını kullanır; branch'teki TASK 1 ve TASK 2 eklerini kapsar,
yalnız TASK 2 için bağımsız yüzde olduğu iddia edilmez. Politika/manifest/exclude/skip değişmedi.
Araçların mevcut Lombok Unsafe, Surefire deprecated parametre ve PMD auxClasspath/kural adı
uyarıları bastırılmadı; bunlar yeni ihlal veya test hatası değildir.

## Kabul edilen senaryolar

- Gerçek Auth AccessTokenIssuer → JWKS → Recommendation signature/typ/iss/aud/time/sub doğrulaması;
  kullanıcı token'ı iç uçta, servis token'ı kullanıcı ucunda reddedilir. İmza bozma ve eksik rol reddi.
- Boş token/invalid token 401, sahte header/cookie 401, CSRF'siz mutasyon 403,
  varsayılan local UserDetailsService bulunmadığının doğrulanması; Basic/form auth açılmadı.
- User A/B tür farkı, Game'in kanonik ID'si, eksik adult metadatası, soğuk başlangıç,
  genre/platform/size, gerçek neden katkısı ve PARTIAL limitations.
- Auth ServiceClientTokenIssuer ile gerçekten imzalanmış Library account-bound token;
  request audience/clientId/accountId ve iletilen token subject/audience/account_id sözleşmesi.
- Library favori/completed dışlaması; bu benzerlik analizi testi değildir (REC-02/05 BLOCKED).
- User rıza reddi, Auth ACTIVE grant reddi, source 503, bozuk/boş/nesne olmayan JSON, redirect takip etmeme,
  timeout, Library veri penceresi taşması ve yanlış profil owner'ında fail-closed sonuç.
- Run/item A/B ownership, cursor owner izolasyonu ve yeni run eklenince devam sayfasının
  kaymaması; UUID/unknown JSON/body boyutu/unsupported sistem filtresi doğrulaması.
- Feedback ilk state, version, duplicate aynı yanıt, 409 conflict, 12 concurrent teslim/tek satır;
  NOT_INTERESTED etkisi; rıza reddinin geçmiş/feedback'i silmesi ve kota sayacını sıfırlamaması.
- D4 gerçek ServiceTokenIssuer, account binding 403, idempotent 204, FK cascade.
- Rate limit, 500 run quota, quota hatasında V11 gözlem ve yeni run'ın atomik rollback'i;
  retention, indeks/PK/check/FK davranışı gerçek PostgreSQL'de.

Auth üretim issuer/JWKS sınıfları kaynak checkout'tan test geçici dizinine derlenir; başka
servise kaynak değişikliği veya runtime modül bağımlılığı eklenmez. Testte üretilen anahtarlar
gerçek credential değildir ve diske secret olarak yazılmaz. User/Game/Library/API grant
endpoint handler'ları contract fixture'dır; gerçek kaynak servis DB/security/E2E kabulü değildir.

## Kanıt ve önceki denemeler

- Son günlük: `logs/recommendation-task-02-final-2026-11-14T15-23-08-491Z-39624-4ac5ba4d.log`
- Surefire: `backend/services/recommendation-service/target/surefire-reports/`
- JaCoCo: `backend/services/recommendation-service/target/site/jacoco/{jacoco.xml,index.html}`
- PMD/Checkstyle: aynı target altında `pmd.xml`, `checkstyle-result.xml`.
- Başlangıç (TASK 1 kaynakları) runner'ı 29 PASS / %98,57; günlük
  `logs/recommendation-task-02-baseline-2026-11-14T14-46-02-223Z-20624-2707dec8.log`.

İlk TASK 2 koşusu 37 testte 1 negatif-JWT tarih dönüşüm hatası verdi; JWTClaimsSet.Builder ile
düzeltildi. Sonraki 37 testli koşuda Maven geçti ancak üç yeni PMD complexity bulgusu runner'ı
durdurdu. Şema doğrulaması ve fixture dispatcher küçük metotlara ayrılarak bulgular kaldırıldı;
kural/baseline değişmedi. Son kaynak, ek concurrency/rıza/kota testleri dahil yukarıdaki
**45 testli tam runner** ile yeniden PASS doğrulandı.
