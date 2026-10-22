# LTZ mühendislik kalite altyapısı v1.0

Bu temel ürün özelliği geliştirmez. GitHub'da CI çalışması ve merge koruması henüz **NOT VERIFIED**.
Workflow dosyası tek başına merge'i engellemez; owner'ın aşağıdaki ayarları ayrıca doğrulaması gerekir.

## Araçların tek sahipliği

| Kontrol | Ana araç / aşama |
| --- | --- |
| Java isim/import/yapı | Maven Checkstyle 3.6.0 + motor 14.3.0 |
| Java davranış/complexity | Maven PMD 3.28.0 + motor 7.28.0 |
| JUnit ve modül coverage | JaCoCo 0.8.15 XML + merkezi progressive policy; Maven verify |
| Frontend | Mevcut npm ESLint, TypeScript, Vitest |
| Node otomasyonu | node:test; Linux ve Windows CI |
| Secret | Gitleaks CLI 8.30.1; yerel diff, PR history, haftalık full history |
| Hızlı dosya kontrolleri | prek 0.5.6 native hook'ları |
| Tamamlayıcı format pilotu | MegaLinter 10.1.0: Markdown/YAML/JSON/XML/Dockerfile/shell |
| Container/config | Trivy 0.76.0; misconfiguration ve image CVE |
| Dependency güncelleme | Haftalık, minor/patch gruplu Dependabot |
| Ana SAST | CodeQL devreye alma kararı bekliyor; mevcut remote setup bilinmiyor |

Semgrep eklenmedi: ikinci genel SAST için kanıtlanmış boşluk yok. SonarQube isteğe bağlı yerel araç olarak korunur;
PR içinde ikinci Maven clean verify/Sonar sunucusu çalıştırılmaz. MegaLinter Java/ESLint/secret/SAST taramalarını tekrarlamaz.

Araç ZIP/tar SHA256 değerleri resmî release asset metadata'sından alınarak
[toolchain.json](../pre-push/ci/toolchain.json) içine sabitlendi. Installer yalnız beklenen executable'ı açar;
indirilmiş installer betiği çalıştırmaz. İncelenen kaynaklar:
[Gitleaks assets](https://github.com/gitleaks/gitleaks/releases/expanded_assets/v8.30.1),
[Trivy assets](https://github.com/aquasecurity/trivy/releases/expanded_assets/v0.76.0),
[prek assets](https://github.com/j178/prek/releases/expanded_assets/v0.5.6).
Platform kapsamı şimdilik Windows x64 ve Linux x64'tür. İmzalı attestation doğrulaması ayrıca yapılmadı.

## Başlangıç ve yerel hook

Gerekli: Node 24, JDK 25, Maven 3.9+; gerçek .env dosyası gerekmez.
Komutlar repository kökünden çalıştırılır. CI Ubuntu runner'ın Maven sürümünü loglar; kesin sürüm kilidi ayrıca takip edilir.

```powershell
node pre-push/ci/install-tool.mjs gitleaks
node pre-push/ci/install-tool.mjs prek
```

Kurulumun indirme/hash/extraction adımları başarılı olmadan devam edilmez. Binary yerel .cache/tools altında kalır.
PowerShell'de:

```powershell
& ./.cache/tools/prek/0.5.6/prek-x86_64-pc-windows-msvc/prek.exe validate-config
& ./.cache/tools/prek/0.5.6/prek-x86_64-pc-windows-msvc/prek.exe install --hook-type pre-commit
```

Linux:

```sh
.cache/tools/prek/0.5.6/prek-x86_64-unknown-linux-gnu/prek validate-config
.cache/tools/prek/0.5.6/prek-x86_64-unknown-linux-gnu/prek install --hook-type pre-commit
```

Hook kurmak .git/hooks yazımıdır. Bu teslimde araç indirilemediği için hook kurulmadı;
mevcut pre-push betikleri silinmedi/değiştirilerek otomatik ağır hook'a çevrilmedi.
Builtin hook'lar yalnız staged dosyada conflict/whitespace/EOF/JSON/YAML/XML/boyut kontrolü yapar.
Whitespace/EOF hook'u yerel dosyayı düzeltebilir; değişikliği inceleyip kendiniz stage etmelisiniz.
Commit hook'unda Maven, Docker, Sonar ve E2E yoktur. Hook atlanması merkezi PR secret taramasını atlatmaz.

## Secret komutları ve gizlilik

```powershell
node pre-push/ci/secrets.mjs staged
node pre-push/ci/secrets.mjs worktree
node pre-push/ci/secrets.mjs range <40-haneli-base-SHA> <40-haneli-head-SHA>
node pre-push/ci/secrets.mjs history
```

İlk iki komut yalnız tracked değişiklik diff'ini bellekte stdin'e verir; staged mod index değişikliğini tarar.
Yeni untracked kaynak önce stage edilmedikçe bu yerel diff taramasına girmez. Gerçek ENV/key dosyası track edilmişse
içerik okunmadan filename gate başarısız olur. Mevcut filename-only kontrol korunmuştur ve içerik taramasının yerine geçmez.

Range modu base..head aralığındaki tüm commit patch'lerini tarar; son commit'te silinmiş ara secret da kapsamdadır.
Full history komutu yerel clone'un tüm ref'lerini tarar; shallow clone history/range taramasında reddedilir.
CI'da fetch-depth: 0 kullanılır. Unreachable object, uzak silinmiş ref veya erişilmeyen fork kapsamı garanti edilmez.

Tarayıcı --redact=100 kullanır; stdout/stderr hiçbir durumda terminale, Markdown'a veya artifact'e aktarılmaz.
Yalnız PASS / FINDING / ERROR sonucu döner. Exit 0 temiz tarama, 1 bulgu/filename reddi, 2 tarayıcı/Git/kurulum hatasıdır.
Inline allow, kullanıcı config ENV'si ve kök .gitleaksignore ile sessiz bypass kullanılmaz; açık config boş suppression ile çalışır.
Tespit varsa public issue açmayın. Doğrulanmış özel kanalda security sorumlusu/owner ile triage ve gerekiyorsa
rotation/revocation yapın. Özel kanal ve security sorumlusu henüz ekipçe teyit edilmelidir.

## Backend ve coverage sözleşmesi

Tek sayısal politika kaynağı [coverage-policy.json](../backend/config/coverage-policy.json), enforcement
[coverage.mjs](../pre-push/ci/coverage.mjs) dosyasıdır. Başlangıçta incelenen18 modül bootstrap:
her biri yalnız Spring Boot launcher içerir, LINE covered1/missed2 (%33,33). Bu değer başarıyla%80'e ulaşılmış gibi sunulmaz.
Game Service'te ek RestClient config testi vardır; production iş mantığı/controller yoktur.

- Bootstrap: gerçek, başarılı, atlanmamış JUnit; taze, geçerli Surefire/JaCoCo XML; tüm production source/bytecode eşlemesi;
  baseline oranı ve covered sayısında gerilememe; başlangıç denominator'ında yapay azalmama zorunlu.
- Yeni/değişen executable Java satırları manifest'teki minimumu (başlangıç%80) sağlamalı. Eşleme modül + package/sourcefile
  + JaCoCo line numarasıyla yapılır. Ek dosyalar ve rename yeni satır olarak ölçülür; silinen dosya head'de satır üretmez.
- Başlangıçta kayıtlı dosyalarda yalnız değişmemiş standart class/default-constructor satırı ve tam standart main metodu
  bootstrap borcunu koruyabilir. Diğer bütün executable satırlar her koşuda kümülatif minimuma tabidir; yalnız test silen
  PR bu kontrolü atlayamaz. Main'e iş mantığı eklenirse main istisnası kalkar. Yeni dosya/modül otomatik istisna alamaz.
- Comment/whitespace-only fark veya Java kaynak değişikliği olmaması `NOT_APPLICABLE` ve null yüzde üretir; sahte%100 yoktur.
  Semantik değişiklikte ölçülebilir yeni satır bulunmazsa güvenli olarak FAIL/review gerekir. Salt deklarasyon/import veya
  satır azaltan refactor bu muhafazakâr kapıya takılabilir; boş suppression/bypass eklemeyin, politikayı ayrıca review edin.
- Strict: toplam modül LINE coverage minimumu zorunlu; strict → bootstrap otomatik veya manifest düzenleyerek yasaktır.
  Bootstrap → strict açık manifest değişikliğidir; mevcut ölçüm minimuma ulaşmalı ve bağımsız politika review'ü alınmalıdır.
  Modülü yayınlama/hazır kabul hedefi toplam%80 olarak korunur. Toplam repository oranı düşük modülü gizleyemez.

İlk politika kabulü ve sonraki manifest değişiklikleri current PR head için bağımsız repository owner approval gerektirir.
Kontrol GitHub'ın read-only reviews API'sini kullanır; eski head onayı, dismissed/changes-requested veya yazarın kendi onayı
yetmez. Owner kendi PR'ını onaylayamaz; politika PR'ının farklı geliştirici tarafından açılması gerekir. Yeni modül manifest'te
strict olarak kaydedilmelidir. Bootstrap borç sayaçları/source hash listesi dondurulmuştur; manifest değiştirilerek büyütülemez.
İlk manifest local origin/main'de henüz yoksa yerel prepare `POLICY_REVIEW_REQUIRED` ile durur; manuel skip flag'i yoktur.
GitHub approval/Rulesets/CODEOWNERS enforced durumu yerel kodla garanti edilmez. Branch protection ayrıca owner işidir.

Maven parent'ta prepare-agent ve report korunur; eski tüm-modül jacoco:check execution'ı merkezi Node gate'e taşındı.
exec-maven-plugin3.6.4 yalnız verify aşamasında her modülde aynı gate'i çağırır. Node/JDK25 gereklidir. Runner context'i Maven öncesinde hazırlar; doğrudan verify çağrısı Maven build başlangıç timestamp'ini kullanır. package/Docker build Node veya Git coverage önkoşuluna bağlanmaz. verify açık skipTests/maven.test.skip/maven.test.skip.exec/jacoco.skip flag'lerini reddeder; bootstrap istisnası test atlama izni değildir.
service/backend/full ve izole CI runner da aynı evaluator'ı çağırır; ayrı80 sabitleri yoktur. Optional Sonar genel kalite
hedefi strict/release niteliğini korur ve sayısal coverage minimumunu manifest'ten okur; bootstrap PR engeli değildir.
JaCoCo skip, production exclude veya yapay test eklenmedi. JDK XML parser'ı DTD/entity/URL çözümlemez.

Yerel default Git base origin/main merge-base'idir; origin/main yoksa fail olur. Değişen tracked/untracked Java kaynaklar
hesaplanır. CI base SHA, PR head ve gerçek test edilen merge commit SHA'yı açıkça sağlar; merge-base ve ancestor doğrulanır,
base tip → tested merge diff'i kullanılır. Böylece PR head satırları farklı merge kaynaklarıyla yanlış eşlenmez.
Context prepare sırasında source hash inventory dondurulur; kaynak sonradan değişirse gate FAIL olur. Filtreli test
kopyasında Git yoktur; dış verification script'i context'i önce orijinal salt-okunur Git metadata'sından hazırlar.
Yeni XML yokken veya tazelik doğrulanamıyorken eski artifact audit'i bir build/test PASS sayılmaz.

Coverage yüzdesi Auth/User/Mail/Gateway güvenliğinin kanıtı değildir. Özellik geldikçe authentication failure,
401/403, token expiration/revocation, privilege escalation, ownership/IDOR, input validation ve rate-limit için
olumlu/olumsuz senaryolar servis review checklist'inde aranmalı. Henüz olmayan endpoint için sahte test yazmayın.

```powershell
node pre-push/ci/backend.mjs
# İsteğe bağlı dar yerel doğrulama; varsayılan komut 18 modülün tamamını kapsar.
node pre-push/ci/backend.mjs --modules services/game-service,services/library-service
```

Komut önce Maven'in JDK25 kullandığını denetler; sonra yalnız disposable ltz-test-* Compose projesini açar.
Root docker-compose.yml ve gerçek ENV kullanılmaz. Explicit --env-file NUL veya /dev/null auto .env yüklemesini engeller.
PG localhost rastgele port, tmpfs, sentetik credential ve servis başına ayrı test DB kullanır; finally yalnız bu projeyi kapatır.
Üretim DB'si/volume'u kullanılmaz. Testler mevcut Flyway ve JPA validate ayarlarını korur; auth devre dışı bırakılmaz.
Spring mail hedefi test profilinde loopback kapalı porta yöneltilir; gerçek SMTP/OAuth/DB ortamı child process'e taşınmaz.

CI aynı modeli geçici service container ile uygular; --ci dış DB modu yalnız GITHUB_ACTIONS=true iken çalışır.
Maven settings açıkça credentialsız proje dosyasından okunur; yerel dependency cache .cache/m2, CI cache ~/.m2/repository.
Rapor tazeliği, en az bir test/modül, atlanmamış test ve coverage mevcudiyeti Node runner'da ayrıca kontrol edilir.

### Politika review ve Trivy triage

`backend/config/coverage-policy.json`, coverage.mjs, CoverageXml.java, backend/pom.xml ve workflow değişiklikleri
hassas altyapıdır. CODEOWNERS'a birincil/yedek atanması ve bağımsız required review owner checklist'ine dahildir;
bilinmeyen sahip atanmamıştır. Approval event'lerinde CI yeniden değerlendirilir; GitHub enforcement NOT VERIFIED.

Trivy image taraması HIGH/CRITICAL eşiklerini korur. `images.mjs` her image için `LTZ_IMAGE_RESULT:` protokolüyle
yalnız image, status, sayılar ve VulnerabilityID/Severity/PkgName/InstalledVersion/FixedVersion alanlarını iletir.
Wrapper bunları yeniden allowlist ile doğrular ve repository DIŞINDA koşunun `trivy-images.safe.json` dosyasına yazar.
Target path, description, title, excerpt, ham stdout/stderr veya credential kopyalanmaz. Kayıtlar kırpılmaz;
buffer/parser/metadata hatasında sonuç SCANNER_ERROR'dur. Eksik image, geçersiz JSON, uyuşmayan toplam ve nonzero-without-findings
clean sayılmaz. Scanner error'da sayılar null'dır; sıfır CVE iddiası değildir.

CVE triage güvenlik sorumlusu + ilgili container bakımcısı tarafından image bazında yapılır. FixedVersion yoksa fix uydurulmaz.
Paket fix'i tek başına yeni image tag/digest'in güvenli olduğunu kanıtlamaz; resmi image metadata'sı ve yeni scanner sonucu,
Java25/Maven/runtime/OS uyumluluğu değerlendirilmelidir. Tag/digest otomatik değiştirilmez. Risk kabulünü owner ve güvenlik
sorumlusu gerekçesi, kapsamı, bitiş tarihi ve takip işiyle açıkça onaylamalı; blanket ignore/threshold düşürme yoktur.
Maven'e doğrudan erişim root'tan -f backend/pom.xml ile; child wrapper komutlarının farklı .mvn kökü desteklenmiş sayılmaz.
Container Dockerfile mevcut farklı build ortamıyla korunur; aynı kalite config'i için CI verify esas doğrulamadır.
Ortak kalite kökü `backend/.mvn/maven.config` işaretçisiyle belirlenir; mevcut Dockerfile'lardaki `COPY backend/`
bu işaretçiyi ve `backend/config` kurallarını birlikte taşır. Docker build başarısı burada doğrulanmadı.

## PR profili ve performans

.github/workflows/ci.yml tüm PR'larda ve main push'larında başlar; workflow-level path filtresi yoktur.
Sabit **Quality Gate** sonucu secrets, Node ve hızlı kaynak kontrollerini her zaman bekler.
Backend/front/config seçimi ayrı scope job'undan gelir. Sadece ürün Markdown/SVG değişikliğinde Maven/npm atlanabilir;
bilinmeyen yol, otomasyon veya ortak config geniş kapsam seçer. Servis içi dar değişikliklerde yalnız ilgili Maven
modülleri `-pl` ile seçilir. Parent, ortak kalite veya policy değişikliğinde tüm backend modülleri kapsanır.
Gateway, servis POM'u, API/domain/application sözleşmesi, uygulama ayarı ve migration değişiklikleri de tüketici
grafiği doğrulanmadığı için tüm backend'e genişler. Silinen/bilinmeyen servis güvenli geniş kapsam seçer.
Rename/delete değişiklik listesine dahildir. Mevcut modüllerde Maven modülleri arası bağımlılık yoktur; yeni
ortak modül eklendiğinde bağımlılık seçimi ve sentetik test DB listesi yeniden doğrulanmalıdır.

Fork PR'larda pull_request kullanılır; pull_request_target, repository secret, privileged downstream veya self-hosted runner yoktur.
GITHUB_TOKEN yalnız contents:read; checkout persist-credentials:false, Action'lar doğrulanmış tam SHA ile sabittir.
Kullanıcı kaynaklı metin shell'e expression olarak gömülmez; SHA'lar env üzerinden ve format doğrulamasıyla kullanılır.
PR başına concurrency eski koşuyu iptal eder; job'larda 3–25 dakika timeout vardır.
Cache yalnız Maven/npm bağımlılıklarıdır; hedef çıktılar, ENV, token veya secret raporu cache/artifact'e girmez.
Hiç artifact upload edilmez; retention depolama yükü yoktur. GitHub log retention ayarı owner kontrolündedir.
Hosted runner, dependency kurulumu ve taramalar için ağ gerekir; bu makinede erişim engeli görüldü.
Kesin süre/kazanç ölçülmedi.

```powershell
node --test ops/env.test.mjs pre-push/menu.test.mjs pre-push/quality.test.mjs pre-push/runtime-checks.test.mjs sonarqube/sonar.test.mjs pre-push/ci/foundation.test.mjs
```

Frontend için mevcut npm ci --ignore-scripts, npm run lint/typecheck/test kullanılır.
Vitest test yapılandırmasında envDir:false gerçek .env dosyalarının otomatik yüklenmesini engeller; bu yalnız test altyapısı değişikliğidir. [Vite envDir](https://vite.dev/config/shared-options.html#envdir).
Playwright ve 18 servis runtime smoke bu hızlı PR tabanına eklenmedi; ürün akışları olgunlaşınca ayrı kabul kapsamı gerekir.

## MegaLinter / Trivy pilotu

```powershell
node pre-push/ci/install-tool.mjs trivy
node pre-push/ci/formats.mjs trivy
node pre-push/ci/images.mjs
node pre-push/ci/formats.mjs mega
```

Format/config taraması yalnız Git'in tracked + ignore edilmeyen kaynaklarını geçici snapshot'a kopyalar;
gerçek ENV, .git, cache ve node_modules aktarılmaz; symlink reddedilir. Snapshot iş bitince kaldırılır.
MegaLinter kaynağı read-only bind eder, APPLY_FIXES=none; otomatik commit/push/comment yoktur.
Cold pull ve geniş image maliyeti nedeniyle yalnız manuel opsiyonel pilot. Pilotun başarısı doğrulanmadı;
başarısızlık başarılı gösterilmez. Image v10.1.0 sabit sürümlü tag'dir; registry digest teyidi bekliyor.
Config taraması HIGH/CRITICAL misconfiguration'da; image taraması HIGH/CRITICAL CVE'de hata döndürür.
Trivy JSON çıktısı yalnız bellekte işlenir; loga kimlik/önem/konum aktarılır, kaynak excerpt/description aktarılmaz. MegaLinter ham stdout/stderr yayımlanmaz, kalıcı reporter'lar kapalıdır. Trivy config kapsamı bu repository'de yalnız Dockerfile'dır; Compose parser/schema kontrolü güvenlik policy taraması değildir.
Trivy secret scanner çalıştırılmaz; npm/Maven dependency update sahibi Dependabot'tur.
Base image taraması, yayımlanmamış uygulama image'ının tarandığı anlamına gelmez.

.github/workflows/security-audit.yml haftalık history + config/base-image taraması yapar.
MegaLinter yalnız workflow_dispatch megalinter_pilot seçeneğiyle eklenir; kritik secret/config audit bu seçenekten bağımsızdır.
Schedule sonucu geçmiş merge'i geri almaz; bulgu triage sahibi atanmalıdır.

## Owner devreye alma listesi

- Aktif hesap, repo visibility/owner.type, gerçek Write/Admin yetkileri teyit edilsin; mevcut gh config engeli aşılmaya çalışılmadı.
- CI workflow'ları normal PR ile gözlemlensin: backend, frontend, docs-only, bilinmeyen dosya, rename/delete ve başarısız job senaryoları.
- Quality Gate gerçekten üretildikten sonra main ruleset'te required check yapılsın; kaynak GitHub Actions App ile sınırlandırılsın.
- En az bir bağımsız review, son push'u yapan dışındaki onay, stale review temizliği, conversation resolution;
  force-push/delete engeli ve en dar kayıtlı bypass değerlendirilsin.
- CODEOWNERS servis hesaplarının Write erişimi teyit edilsin. Ortak CI/policy/quality/Gateway/admin/notification ve
  frontend altyapısına gerçek birincil/yedek sahibi atansın. Bilinmeyen alanlara rastgele kişi atanmadı.
- Code owner review zorunluluğu ayarlansın; workflow, security policy, tarayıcı ve ruleset dosyası değişiklikleri
  bağımsız güvenlik review'ü görsün. Bu dosyalar için sahip atanana kadar teknik CODEOWNERS kapsamı eksiktir.
- Actions contents:read default, full-SHA politikası, fork onayı ve izin verilen Action'lar gözden geçirilsin.
- Secret scanning, repo push protection, dependency graph, Dependabot alerts/security updates ve private reporting teyit edilsin.
- CodeQL default setup durumu önce okunsun. Default aktifse advanced workflow eklenmesin. Uygunluk/plan/lisans,
  Java25 extraction ve gerçek build denemesi başarılıysa tek setup seçilip devreye alınsın. Bu teslimde CodeQL etkinleştirilmedi.
- Lisans seçimi ekibe aittir; LICENSE eklenmedi. Private disclosure kanalı, security sorumlusu/yedeği ve istisna SLA'sı kararlaştırılsın.

Java25 desteği için [PMD Java tablosu](https://docs.pmd-code.org/latest/pmd_languages_java.html),
[Checkstyle](https://checkstyle.org/), [PMD plugin motor seçimi](https://maven.apache.org/plugins/maven-pmd-plugin/examples/upgrading-PMD-at-runtime.html)
ve [Checkstyle plugin motor seçimi](https://maven.apache.org/plugins/maven-checkstyle-plugin/examples/upgrading-checkstyle.html) incelendi.
Resmî dil desteği, LTZ build'inin başarı kanıtı değildir.
