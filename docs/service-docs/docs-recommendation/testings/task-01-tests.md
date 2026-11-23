# TASK 1 — gerçek test kanıtı

2026-11-14 16:38 (Europe/Istanbul) · Son kaynak sürümü · **PASS**, çıkış kodu 0.

## Tam çalıştırma komutu

Repo kökünden PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node --input-type=module -e "import {backendCheck} from './pre-push/ci/backend.mjs'; import {execute,withRunLog} from './lib/quality-tools.mjs'; await withRunLog(process.cwd(),'recommendation-task-01',log=>backendCheck({log,modules:['services/recommendation-service'],runCommand:async(cmd,args,options)=>{const docker=cmd==='docker';const r=await execute(docker?'C:/Program Files/Docker/cli-plugins/docker-compose.exe':cmd,docker?args.slice(1):args,{...options,env:{...options.env,DOCKER_CONFIG:process.cwd()+'/.cache/docker-ci-config'}});if(docker&&r.code)log(r.output);return r;}}));"
```

Windows'taki dar araç ortamında Docker CLI Compose eklentisini bulamadığından, runner'ın
desteklediği `runCommand` enjeksiyonu yalnız `docker compose` çağrısını kurulu Compose
executable'ına yönlendirir. Aynı --env-file NUL, rastgele ltz-test proje adı, compose.test.yml,
tmpfs PostgreSQL, rastgele loopback port ve finally cleanup argümanları korunur. Boş Docker
config dizini seçilir; gerçek Docker credential/ENV dosyası okunmaz. Ortak runner değişmedi;
test/coverage/quality/dependency gate fonksiyonlarının hiçbiri değiştirilmedi veya atlanmadı.

Runner JDK25'i doğrulayıp şu Maven argümanlarını üretti (değişken alanlar son koşunun değerleridir):

```text
mvn.cmd -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml
  -Dmaven.repo.local=<repo>/.cache/m2
  -Dproject.build.outputTimestamp=<runner başlangıç UTC zamanı>
  -f backend/pom.xml -pl services/recommendation-service -Pci
  -Dltz.test.postgres.port=<rastgele disposable port> --fail-at-end clean verify
```

Kesin argüman üretimi `pre-push/ci/backend.mjs` içindedir. Ortam allowlist'tir; sentetik
test credential kullanılır. DisposableDatabaseInitializer yalnız CI profilinin loopback
`recommendation-service` DB'sini kabul eder; normal DEV/production DB'sine geri dönüş yoktur.
Disposable proje sonlandırıldı; gerçek veri volume'ü/DB'si kullanılmadı.

## Sonuçlar

| Kontrol | Gerçek sonuç |
| --- | --- |
| Maven clean verify | BUILD SUCCESS, 14,960 saniye; runner exit 0 |
| RuleBasedRecommendationEngineTest | 11 PASS |
| RecommendationExecutionServiceTest | 7 PASS |
| RecommendationObservationTest | 1 PASS |
| RecommendationAnalyticsIntegrationTest | 7 PASS, gerçek PostgreSQL 17.11 |
| RecommendationSecuritySurfaceTest | 2 PASS |
| RecommendationServiceApplicationTests | 1 PASS |
| Toplam | **29 PASS / 0 failure / 0 error / 0 skipped** |
| Flyway / Hibernate validate | V11 temiz kurulumu PASS; ddl-auto=validate/open-in-view=false korundu |
| JaCoCo LINE | **138 covered / 2 missed = %98,5714** |
| Merkezi ek/değişen executable kod | **137/137 = %100**, baseline PASS |
| Checkstyle / PMD | 0 eski / 0 yeni / 0 toplam ihlal; ratchet PASS |
| Uygulama JAR dependency taraması | Trivy 0 bulgu; HIGH/CRITICAL gate PASS |
| git diff --check | Exit 0; whitespace hatası yok |
| Legacy pre-push / SonarQube | NOT RUN — master protokolünde TASK 5 final gate |
| Gerçek Auth/downstream E2E | NOT RUN — bu taskta tüketici veya iş HTTP API'si yok |

JaCoCo'nun iki eksik satırı değişmemiş bootstrap main metodudur; policy manifest'i, production
exclude veya skip flag'i değiştirilmedi. Repository arayüzü merkezi gate'in kendi “executable
satırı olmayan interface” kararıyla raporlandı; elle coverage istisnası eklenmedi.
Mevcut araç uyarıları: Lombok Unsafe kullanımı, Surefire deprecated skipExec parametresi,
PMD kural adı/auxClasspath uyarısı. Bunlar build hatası veya gizlenmiş ihlal değildir.

## Senaryolar ve sınırlar

- 0/1/N aday, input sırası değişse de aynı ranking, skor eşitliğinde UUID sırası, ilk N sınırı.
- 0–10000 sinyal sınırı, eksik/sıfır sinyal, max skor, katkı yuvarlama ve gerekçe toplamı.
- Mükerrer aday, oversized input, geçersiz limit/sürüm/gerekçe, immutable input/output.
- İkinci algoritma uygulaması, aynı servis/sonuç sözleşmesi; bozuk adapter hedef/rank/duplicate reddi.
- Algoritma unavailable sonucu doğru ölçülür; unavailable ve analitik kayıt hatasında sahte başarı yok.
- PostgreSQL Flyway/JPA şeması, kişisel alan yokluğu, tarih indeksi, PK ve CHECK ihlalleri.
- Eşzamanlı 12 teslim + tekrar aynı olay yalnız bir kayıt/sayaç; payload conflict aynı kaydı değiştirmez.
- 30 günlük saklama, gelecekteki/eski olay reddi, expired fiziksel temizleme ve maliyetli aralık reddi.
- Sahte X-User-Id/X-Role/cookie ile anonim yüzey 401; CSRF'siz mutasyon 403.
  Bunlar Spring başlangıç yüzeyi testleridir; gerçek JWT, rol/ownership, ProblemDetail veya
  kullanıcı endpoint'i kabulü değildir. İlgili HTTP endpoint bu aşamada yoktur.

## Kanıt dosyaları ve başarısız denemeler

- Günlük: `logs/recommendation-task-01-2026-11-14T13-37-59-858Z-10784-c1dd6751.log`
- Surefire: `backend/services/recommendation-service/target/surefire-reports/`
- JaCoCo: `backend/services/recommendation-service/target/site/jacoco/{jacoco.xml,index.html}`
- Checkstyle/PMD: aynı target altında `checkstyle-result.xml`, `pmd.xml`.
- Runner coverage context ve dependency inventory: `test-results/` altında üretilir.

Önceki denemeler PASS sayılmadı: Docker eklenti keşfi başlangıcı engelledi; ilk çalışan Maven
koşusunda 25 testin 6'sı Mockito agent yol hatası verdi; sonraki koşuda testler geçti fakat
launcher annotation değişikliği merkezi coverage tarafından reddedildi. İki neden de giderilip
son kaynakla yukarıdaki **29 testli tam runner** yeniden çalıştırıldı.
