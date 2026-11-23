# TASK 3 — test kanıtı

2026-11-15 · Recommendation · JDK 25 · disposable PostgreSQL 17 · **PASS**.

## Çalıştırma

Gerçek ENV okunmadan merkezi backend runner çalıştırıldı. Windows plugin keşfi için yalnız
komut enjeksiyonuyla kurulu Compose executable kullanıldı; ortak runner/policy değişmedi.
Compose `--env-file NUL`, rastgele test projesi/loopback portu, tmpfs PostgreSQL ve finally
cleanup kullanır. DEV/production veritabanına geri dönüş yoktur.

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node --input-type=module -e "import {backendCheck} from './pre-push/ci/backend.mjs'; import {execute,withRunLog} from './lib/quality-tools.mjs'; await withRunLog(process.cwd(),'recommendation-task-03',log=>backendCheck({log,modules:['services/recommendation-service'],runCommand:(cmd,args,options)=>execute(cmd==='docker'?'C:/Program Files/Docker/cli-plugins/docker-compose.exe':cmd,cmd==='docker'?args.slice(1):args,{...options,env:{...options.env,DOCKER_CONFIG:process.cwd()+'/.cache/docker-ci-config'}})}));"
```

Son log: `logs/recommendation-task-03-2026-11-15T08-55-49-948Z-34000-3fc54525.log`.
Maven clean verify: 43,219 saniye; bitiş 2026-11-15 11:56:44 Europe/Istanbul; runner exit 0.

| Kontrol | Sonuç |
| --- | --- |
| Testler | 66 PASS, 0 failure/error/skipped |
| Eski test regresyonu | 45 PASS |
| HardwareRequirementEvaluatorTest | 8 PASS |
| ComponentCompatibilityEvaluatorTest | 3 PASS |
| HardwareAssessmentApiIntegrationTest | 10 PASS |
| JaCoCo LINE | 811 covered / 21 missed; %97,47596 → %97,48 |
| Merkezi ek/değişen executable kod | 810 / 829; %97,70808 → %97,71 |
| Coverage baseline/ratchet | PASS; policy/manifest değişmedi |
| Checkstyle / PMD | 0 yeni ihlal, PASS |
| JAR dependency Trivy gate | 0 bulgu, PASS |

Merkezi değişen kod kapsamı origin/main karşılaştırmasıdır; önceki TASK 1/2 değişikliklerini
de kapsar. TASK 3'e özel izole diff kapsamı olarak sunulmaz.

## Kabul kanıtları ve sınırlar

Unit testler: tam model/missing/alternatif model, minimum ve recommended ayrımı,
ondalık/ikili birim belirsizliği, eksik modül ve quantity, kanıtlı RAM/disk açığı, socket/DDR
eşleşme/uyuşmazlık, çoklu CPU ve bilinmeyen teknik alanlar.

API testleri: gerçek Auth üretim issuer sınıfları ve JWKS; marka boş/manual owner profil,
profil sürümü, başka kullanıcının profili 404 ve sonraki çağrıların yapılmaması; yanlış
kaynak kimliği/duplicate/sayfalama/geçersiz kapasite; boş gereksinim/profil; minimumdan düşük
RAM/disk; uyumsuz socket; 0/59/reliable/outlier FPS; iki çözünürlükte de tahmin üretilmemesi;
profil/oyun binding ve bozuk istatistik; 503/timeout/redirect/bozuk JSON; JWT/rıza/request
validation/adult belirsizliği; rıza geri çekmede TASK 2 geçmişinin purge edilmesi.

Hardware/Game/Performance HTTP yanıtları gerçek DTO sözleşmelerinin test fixture'larıdır;
üretici servisleri canlı başlatılmadı. Bu, çok servisli production E2E PASS değildir.
Gerçek geçici PostgreSQL üzerinde mevcut Flyway V11/V12 ve önceki DB testleri geçti;
yeni migration yazılmadı. Assessment run/history kaydı oluşturmaz.

İstek preset/çözünürlüğü, ham Performance ortalamasını tahmine dönüştürmez. Hiçbir test,
bağlamı eksik reliable bayrağını güvenilir FPS tahmini kabul etmez. Pozitif FPS yolu
NOT IMPLEMENTED; REC-06 katalog batch ve REC-09 ölçüm bağlamı açık bağımlılıklardır.

## Ara başarısızlıklar

- Başlangıç 45 test PASS: `logs/recommendation-task-03-baseline-2026-11-15T08-32-21-841Z-41464-f7556e44.log`.
- İlk yeni test derlemesindeki MockMvcRequestBuilders import yolu düzeltildi.
- `logs/recommendation-task-03-2026-11-15T08-50-00-332Z-39232-4a673396.log`:
  66 test PASS, fakat 5 PMD complexity bulgusu nedeniyle runner FAIL. HTTP transport,
  yanıt okuyucuları, token ve fixture yardımcıları ayrıldı; kural/suppression/baseline değiştirilmedi.
- Yukarıdaki son koşu tüm kapıları geçti. Ara başarısızlıklar PASS sayılmadı.

Dokümantasyon kapanışında `git diff --check` ve yeni dosya whitespace kontrolü PASS.
Master checklist'in 23 REC adı/açıklaması kaynakla birebir karşılaştırıldı: PASS.
Hardware/Performance/Game/Auth üretici dizinleri, Recommendation resources/migration,
Compose, örnek ENV, parent POM ve coverage policy diff kontrolünde değişmedi.
Final SonarQube ve legacy pre-push TASK 5'e ayrılmıştır; bu rapor merge-ready kanıtı değildir.
