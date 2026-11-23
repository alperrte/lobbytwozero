# TASK 4 — test ve sözleşme kanıtı

2026-11-15 · Recommendation · özellik durumu **BLOCKED — 0/3 DONE**.
Test kapısının geçmesi REC-13/14/15'in uygulandığı anlamına gelmez.

## Komut

TASK 3'te kullanılan merkezi backend runner, JDK25 ve disposable PostgreSQL ile tekrar çalıştırılır.
Gerçek ENV okunmaz: rastgele test projesi/loopback portu, tmpfs PostgreSQL, `--env-file NUL`
ve finally cleanup. Windows Docker plugin keşfi için aynı argümanlar kurulu Compose executable'a
aktarılır; ortak runner, coverage policy, lint baseline veya bağımlılıklar değişmez.

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node --input-type=module -e "import {backendCheck} from './pre-push/ci/backend.mjs'; import {execute,withRunLog} from './lib/quality-tools.mjs'; await withRunLog(process.cwd(),'recommendation-task-04',log=>backendCheck({log,modules:['services/recommendation-service'],runCommand:(cmd,args,options)=>execute(cmd==='docker'?'C:/Program Files/Docker/cli-plugins/docker-compose.exe':cmd,cmd==='docker'?args.slice(1):args,{...options,env:{...options.env,DOCKER_CONFIG:process.cwd()+'/.cache/docker-ci-config'}})}));"
```

## Koşu geçmişi

| Koşu | Kanıt | Gerçek sonuç |
| --- | --- | --- |
| Başlangıç | `logs/recommendation-task-04-baseline-2026-11-15T10-21-04-725Z-42504-a551b9f1.log` | 66 PASS; Maven 42,316 sn; lint/coverage/dependency gate PASS; exit 0 |
| İlk yeni test | `logs/recommendation-task-04-2026-11-15T10-22-40-957Z-38004-98993a53.log` | 69 test, 3 failure; anonim POST için yanlış 401 beklentisi, mevcut CSRF 403; exit 1 |
| İkinci koşu | `logs/recommendation-task-04-2026-11-15T10-25-20-063Z-43604-5382c6e5.log` | 69 test, 1 failure; yeni üç sınır testi PASS, eski kota testi dakika geçişinde beklenen 429 yerine 200; exit 1 |
| Son koşu | `logs/recommendation-task-04-2026-11-15T10-27-41-144Z-13004-4a3a9f13.log` | 69 PASS; Maven 36,893 sn, bitiş 13:28:30 Europe/Istanbul; tüm hedefli kapılar PASS; exit 0 |

Anonim POST beklentisi düzeltildi; production CSRF zinciri aynı kaldı. Kota testine yalnız
test context'inde sabit Clock kullanan gerçek limiter eklendi. Bu düzenleme limit denetimini
atlamaz; 10 generation, 11. istekte 429 ve mevcut retention/withdrawal kontrolleri sürer.
Başarısız koşular PASS sayılmaz.

| Son kapı | Gerçek sonuç |
| --- | --- |
| Maven clean verify / Surefire | 69 PASS; 0 failure / error / skipped (66 regresyon + 3 boundary) |
| PostgreSQL / Flyway / JPA | Disposable PostgreSQL 17.11, mevcut V11/V12; PASS |
| JaCoCo toplam LINE | 811 covered / 21 missed; %97,47596 → %97,48 |
| Merkezi ek/değişen executable kod | 810 / 829; %97,70808 → %97,71; PASS |
| Coverage baseline | PASS; manifest/policy değişmedi |
| Checkstyle / PMD | Baseline 0, şimdi 0, yeni 0; PASS |
| Dependency JAR Trivy gate | 0 bulgu; PASS |

Merkezi coverage karşılaştırması origin/main'e göre önceki taskları da kapsar; bu task yeni
production Java satırı eklemedi. Yüzdeler TASK 4'e özel yeni özellik kapsamı değildir.
Mevcut Lombok Unsafe, Surefire skipExec ve PMD rule/auxClasspath araç uyarıları bastırılmadı.

## Üç yeni sınır testinin kanıtladıkları

`RecommendationApiIntegrationTest` içindeki unavailablePlayer/Friend/EventRecommendationsRemainClosed:

- `/api/v1/recommendation/{players|friends|events}` negatif probe'ları; yeni API değildir.
- Her probe için anonim GET 401; Bearer/CSRF taşımayan POST mevcut CSRF filtresinde 403.
- Gerçek Auth issuer token'larıyla A ve B PLAYER ve ADMIN için GET/POST 403.
- Header/query/body sahte kimlik, candidateIds ve includePrivate erişim açmaz.
- Auth D4 servis token'ı kullanıcı yollarında 401.
- Kullanıcı ret yanıtı ACCESS_DENIED ProblemDetail/no-store; items/candidateIds veya B kimliği dönmez.
- JWKS dışında kaynak iş HTTP çağrısı ve Recommendation run/observation yazısı oluşmaz.

Production Auth kaynakları testte derlenip issuer/JWKS kullanılır. Yeni testler User/Social/Event
üreticisini çalıştırmaz ve onların kabulünü taklit etmez. Gerçek PostgreSQL/Flyway V11/V12 ve
önceki 66 test regresyon kapsamındadır. Yeni migration veya production Java değişikliği yoktur.

## Uygulanmamış feature testleri

| Master kabulü | Durum / neden |
| --- | --- |
| Self exclusion, existing friend, A→B/B→A block | NOT IMPLEMENTED — izinli aday + Recommendation Social eligibility sözleşmesi yok |
| Private/izinli tercih yok/deleted/disabled | NOT IMPLEMENTED — User kartı yeterli öneri eligibility kanıtı değil |
| Private/full/cancelled event | NOT IMPLEMENTED — Event iş API'si yok |
| Farklı timezone, kayıt penceresi/kapasite | NOT IMPLEMENTED — gerçek Event zaman/katılım sözleşmesi yok |
| Aday batch/page/deterministik sıralama ve timeout | NOT IMPLEMENTED — gerçek aday kaynağı/ranker/tüketici yok |
| Canlı User/Social/Event + Recommendation E2E | NOT VERIFIED — üretici çağrısı yapılmadı |

Eski testlerdeki timeout/owner/consent kanıtları yeni sosyal/etkinlik feature kabulü diye
sunulmaz. [Owner talepleri ve yeniden açılma koşulları](../decisions/task-04-decisions.md).

## Dosya/dokümantasyon kapanış kontrolü

- `git diff --check`: PASS; yeni izlenmeyen dosyalarda trailing whitespace kontrolü PASS.
- Yeni TASK 4 Markdown dosyalarının yerel bağlantıları: PASS.
- Master checklist'in 23 REC adı/açıklaması kaynakla birebir aynı; 13 DONE işareti korundu.
- Production Java/resources, Auth/User/Social/Event/Hardware/Performance, Compose,
  `.env.example`, parent POM, kalite manifestleri ve DB/ER diff'i boş: PASS.
- Son testten sonra yalnız dokümantasyon kapanış kayıtları değişti; test/production kodu aynı.

Final pre-push ve SonarQube TASK 5'e ayrıldı; merge-ready iddiası yoktur.
