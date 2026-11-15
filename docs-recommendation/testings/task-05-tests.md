# TASK 5 — gerçek test ve runtime kanıtı

2026-11-15 · Alper · REC-22 feature PASS; final kapılar [ayrı raporda](pre-merge-quality.md).

## Maven / disposable PostgreSQL

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
node --input-type=module -e "import {backendCheck} from './pre-push/ci/backend.mjs'; import {execute,withRunLog} from './lib/quality-tools.mjs'; await withRunLog(process.cwd(),'recommendation-task-05-final',log=>backendCheck({log,modules:['services/recommendation-service'],runCommand:(cmd,args,options)=>execute(cmd==='docker'?'C:/Program Files/Docker/cli-plugins/docker-compose.exe':cmd,cmd==='docker'?args.slice(1):args,{...options,env:{...options.env,DOCKER_CONFIG:process.cwd()+'/.cache/docker-ci-config'}})}));"
```

Runner'ın gerçek Maven komutu: `mvn.cmd -B -ntp -s pre-push/ci/maven-settings.xml -gs
pre-push/ci/maven-settings.xml -Dmaven.repo.local=<repo>/.cache/m2
-Dproject.build.outputTimestamp=<run timestamp> -f backend/pom.xml
-pl services/recommendation-service -Pci -Dltz.test.postgres.port=<random loopback port>
--fail-at-end clean verify`. Değişken timestamp/port runner tarafından belirlenir;
test atlama veya güvenlik/coverage bypass flag'i yoktur.

Final log: `logs/recommendation-task-05-final-2026-11-15T10-54-11-555Z-42936-24daa515.log`.
Exit **0**, Maven **33,958 sn**, bitiş **2026-11-15 13:54:54 Europe/Istanbul**.

| Kontrol | Gerçek sonuç |
| --- | --- |
| Java test | 80 PASS, failure/error/skipped 0; önceki 69 + 7 engine/process + 4 assistant API |
| PostgreSQL/Flyway/JPA | Disposable PostgreSQL 17.11, V11/V12; PASS |
| JaCoCo LINE | 980 covered / 25 missed, %97,51244 → %97,51 |
| Merkezi ek/değişen executable kod | 979/1002, %97,70459 → %97,70; PASS |
| Baseline / lint | Coverage baseline PASS; Checkstyle/PMD 0 yeni ihlal |
| JAR dependency | Trivy 0 bulgu, PASS |

Merkezi kapsam origin/main karşılaştırmasıdır; önceki taskları da kapsar. Manifest/policy,
suppression/exclude veya bağımlılık değiştirilmedi. Geçici PG rastgele `ltz-test-*` projesi,
tmpfs ve `--env-file NUL`; finally yalnız bu projeyi kapatır, DEV/production DB kullanılmaz.

## Python ve API kanıtı

```powershell
& ./.cache/tools/python/3.14.10/python.exe -I -S -B -m unittest discover -s backend/services/recommendation-service/src/test/python -v
```

7 PASS; son doğrudan Node process wrapper exit 0 ve
`logs/recommendation-python-final-2026-11-15T11-01-41-307Z-37664-cc48ecd3.log`.
Windows portable runtime indirme/hash onayı [ADR 0003](../../docs/decisions/0003-recommendation-python-yardimcisi.md).

- Engine/process: gerçek CPython; üç konu ve iki LTZ intent'i, deterministik çıktı/sürüm,
  missing/unknown/deficit/incompatible, malicious input, unavailable, busy, timeout,
  stdin/stdout/stderr limitleri, nonzero exit, interrupt restore ve temiz child environment.
- Reader: bozuk/null/array/trailing JSON, sürüm/kimlik bağlamı/status/template/message ihlali;
  kapalı step ve plan doğrulaması. Raw process çıktısı hata response'una taşınmaz.
- API: gerçek Auth üretim issuer/JWKS; Python gerçek süreç, Hardware/Game/Performance
  HTTP contract fixture. Oyun/usage rehberi history yazmaz ve sadece consent/grant çağırır.
  Owner donanım, foreign profile 404, eksik alan, yanlış intent/locale/version/identity/shell
  payload 400, servis/geçersiz token 401, consent 403 ve source 503 doğrulanır.
- Process unavailable/timeout/invalid output unit seviyesinde gerçek child process ile
  testlidir; canlı Gateway/kaynak servis E2E veya HTTP üzerinden runtime arızası kabulü değildir.

## Docker dağıtım kanıtı

POM/config ve yalnız Recommendation src/Dockerfile'dan oluşturulan secretsiz dar context
(yaklaşık 389 KiB) kullanıldı. Gerçek ENV veya kullanıcı Docker credential dosyası context'e alınmadı.

```text
docker-buildx.exe build --load --progress plain --build-arg SERVICE_NAME=recommendation-service
  -t ltz-recommendation-task05:local -f <dar-context>/backend/services/recommendation-service/Dockerfile <dar-context>
```

Son build exit **0**: `logs/recommendation-image-final-2026-11-15T10-59-44-087Z-8972-7fdff50a.log`.
Image: `sha256:c7a17aa83e70b08cae78f21d0e66d38f49833289ab2a2f38fc3691485ff9d64d`, user `ltz`.
Runtime Ubuntu Python 3.12.3 (dağıtım güvenlik patch paketi); Windows 3.14.10 ile protokol uyumlu.

`docker run --rm -i --network none --read-only --cap-drop ALL --security-opt no-new-privileges
--memory 64m --pids-limit 32 --tmpfs /tmp:rw,noexec,nosuid,size=4m` ile packaged smoke:
JAR içindeki `BOOT-INF/classes/assistant/assistant_rules.py` hash'i kaynakla karşılaştırıldı;
gerçek Linux Python HARDWARE_DEFICIT/INSUFFICIENT_DATA döndürdü (exit 0). Ayrı GAME smoke de PASS.
Tam uygulama konteynerinin DB/Auth/Gateway ile E2E başlatılması değildir.

Mevcut sabit Trivy `image --image-src docker --scanners vuln --severity HIGH,CRITICAL
--exit-code 1 --timeout 5m ltz-recommendation-task05:local`: **CLEAN / HIGH 0 / CRITICAL 0**.
Kanıt: `logs/recommendation-image-trivy-2026-11-15T11-00-03-930Z-4180-71495227.log` ve
`logs/recommendation-image-trivy.safe.json`. Ham scanner çıktı/değerleri yayınlanmadı.

## Ara hatalar ve düzeltmeler

| Koşu / sorun | Gerçek sonuç ve düzeltme |
| --- | --- |
| Baseline `logs/recommendation-task-05-baseline-2026-11-15T10-38-57-257Z-41780-ca54b469.log` | Önceki 69 test ve merkezi kapılar PASS |
| `logs/recommendation-task-05-2026-11-15T10-46-09-906Z-39004-5c90b3d4.log` | 80 testte 2 failure/3 error; Windows uzun `-c` argümanı quoting sorunu, sabit script dosyasıyla düzeltildi |
| `logs/recommendation-task-05-2026-11-15T10-49-08-963Z-38568-b1b89531.log` | 80 test PASS, runner FAIL: PMD complexity/cause/resource bulguları; validator/fixture ayrımı, cause koruması ve try-with-resources ile düzeltildi |
| İlk Docker smoke | Minimal paket `json` içermedi, exit 1; gerekli libpython3-stdlib eklendi, yeniden build/smoke PASS |
| PowerShell stderr + Tee wrapper | Test/image başarılı çıktısına rağmen wrapper exit 1 üretti; doğrudan Node process runner ile build ve Python test exit 0 ayrıca doğrulandı |

Başarısız ara koşular PASS sayılmadı. Mevcut Lombok Unsafe, Surefire skipExec, PMD
auxClasspath/rule ve Docker named-pipe uyarıları bastırılmadı. Son Maven/Node kapısı ve image
build gerçek exit 0'dır. Son testten sonra Java/Python kaynakları değişmedi; Docker stdlib
düzeltmesi ayrıca rebuild/smoke/image scan ile doğrulandı.

Native pre-push/Sonar BLOCKED ve final readiness kararı [ayrı tabloda](pre-merge-quality.md).
