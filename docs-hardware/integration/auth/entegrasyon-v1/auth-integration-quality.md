# Hardware–Auth entegrasyonu — kalite ve doğrulama kaydı

Tarih: 2026-10-24. Sorumlu: Alper. Durum: **QUALITY_BLOCKED — Social kaynaklı ortak Sonar kapısı**.

## Son durum
Hardware kullanıcı JWT/JWKS, RBAC ve D4 yerel uygulaması ile kendi testleri tamamlandı.
Son kod için tam pre-push PASS; gerçek tüm-backend Sonar gate FAIL. Hardware için
son analizde açık bulgu yok; kalan 17 bulgunun tamamı Social'da. Kullanıcı Social
müdahalesini reddettiği için değiştirilmedi; iş commit/PDA teslimine hazır gösterilmez.
Gerçek paketli Auth/Gateway/Hardware izole HTTP kabulü **16 kontrol PASS**;
gerçek Auth worker'ı hardware görevini CONFIRMED yaptı. Ortak dağıtım ve diğer
15 hedefi kapsayan tüm D4 Saga E2E NOT_VERIFIED; [runtime kaydı](auth-integration-04.md).

## Kapsam ve ortam
Hardware üretim kodu, V8, kendi API/DB/ER belgeleri ve testler. Auth/Gateway üretim
kaynakları yalnız okunup geçici test dizininde derlenir. Gerçek ENV, anahtar veya kullanıcı verisi okunmaz.
Testler geçici 3072 bit RSA anahtarı, loopback JWKS sunucusu ve PostgreSQL 17 Testcontainers kullanır.

## Kalan Hardware işlerinin son doğrulaması — 2026-10-24
Kullanıcı kalan işleri tamamlamayı istedi; Social için tekrar **“Kendi servisimize bakıyoruz biz”**
diyerek sınırı korudu. Bu turda yalnız Hardware'a ait kabul betiği/testleri ve belgeleri eklendi;
backend üretim kaynakları, ENV, Compose, bağımlılıklar ve migration'lar değişmedi.

| Kontrol | Son gerçek sonuç |
| --- | --- |
| Tam pre-push `full dev` | PASS / exit 0; 16:38:24 +03 Maven bitişi; 1138 test, 0 failure/error/skipped; %95,88 JaCoCo; 18/18 HTTP; SAFE TO COMMIT |
| Gerçek Auth/Gateway/Hardware HTTP kabulü | 16 kontrol PASS / exit 0; 16:40:10 +03; Auth hardware görevi CONFIRMED; 3 süreç + 3 fixture container/volume temizlendi |
| Node regresyonları | 72 PASS; 65 mevcut runner + 7 yeni fixture güvenlik/doğrulama testi |
| Sonar taze backend testleri | 1138 PASS; BUILD SUCCESS; 16:56:46 +03 |
| Hardware JaCoCo | 1577/1595 = %98,8715; 18 missed; 356 test PASS |
| Sonar gerçek analiz | 16:59:25 +03; Hardware 0 açık yeni bulgu; diğer 17 bulgunun tamamı Social'da |
| Genel kalite kapısı | FAIL / exit 1; Security A / Reliability C / Maintainability A; overall %91,9 / new %92,3 / duplication %0,7 |

Son CE task: `df1c0733-4eb9-4d7f-b2c4-59810383ca45`, proje `ltz-backend`;
kapsam tüm backend, branch `feature/hardware-service`, HEAD `be5f205b1753189060f12f1017cae53c0105deda` + çalışma kopyası.
Kanıtlar: `logs/hardware-auth-runtime-pre-push.log`, `logs/hardware-auth-runtime-e2e-final.log`,
`logs/hardware-auth-runner-runtime-final-tests.log`, `logs/hardware-auth-runtime-sonar.log`.
Etiketli fixture container kalmadığı ayrıca Docker ile doğrulandı; Auth/Gateway/Social/Game Git diff yok;
API kataloğunun Hardware öncesi/sonrası ve master §0–11 aynı; Git whitespace kontrolü PASS.

**Hardware uygulama ve izole gerçek HTTP kabulü VERIFIED.** Ortak Compose/TLS/tarayıcı,
diğer 15 Saga ACK'i ve yayımlanmamış Auth D3/cihaz/PERF/User-Social sözleşmeleri bu kabulün
parçası olarak hazır gösterilmez. Genel teslim **EXTERNAL_QUALITY_BLOCKED**;
kapı/kapsam/issue durumu değiştirilmedi, hazır commit/PDA kapanışı önerilmez.

## Mevcut kanıt
- İlk JWT bağlantısı ve V8 ilk regresyonu: ayrı koşularda mevcut 295 test PASS.
- `mvn -f backend/pom.xml -pl services/hardware-service -am verify -B -ntp`:
  352 test, failure/error/skipped 0; BUILD SUCCESS, 03:04:55 +03.
  Kayıt: `logs/hardware-auth-local-verify.log`. Bu, nihai iki kalite kapısı yerine geçmez.
- Yeni 57 senaryo: gerçek Auth AccessTokenIssuer/ServiceTokenIssuer ve JWKS uyumu,
  Gateway cookie/CSRF/Origin, beş rol, yanlış claim/tür/imza, D4 binding, tablo temizliği,
  audit koruması, rollback, fence expiry ve silme/yazma yarışı; JWKS cache/rotation ve hata sınırları.
- Ek JWKS gövde timeout testiyle HardwareJwksConfigurationTest 14/14 PASS;
  `logs/hardware-auth-jwks-timeout-test.log`, 03:07:22 +03. Başlıkları gelen ama gövdesi
  bekletilen yanıt da 500 ms read timeout ile kapanır. Toplam yeni senaryo sayısı 58 oldu.

## Düzeltilen ilk denemeler
Merge'den kalan ortak kalite betiği çatışması: pre-push aynı blokta iki `result`
tanımı ve iki Maven çağrısı içeriyordu. Tek Compose tabanlı full/Sonar akışı korundu;
Sonar'daki gereksiz ikinci profil yükleme kaldırıldı. Backend-only akışındaki sabit dev
profili ve redaction listesi atlaması mevcut wrapper kullanılarak düzeltildi.
64 runner regresyonu PASS; seçilen prod profili/redaction için ek regresyon eklendi.
`ops/ops.md` kullanıcı metni korundu; yalnız Git whitespace kapısını bozan satır sonu boşluğu kaldırıldı.

PowerShell Maven `-D` argümanları alıntılandı. Test anahtarı gerçek Auth'un 3072 bit
alt sınırına uyarlandı. Synthetic NumericDate biçimi düzeltildi. Test JWKS origin bean'i
açıkça ayrıldı. JWKS erişim hatası güvenli 401'e dönüştürüldü. D4 yalnız servis-token
akışı için ilk denemede dar CSRF istisnası aldı; bu açık istisna Sonar düzeltmesinde
kaldırıldı. Son kod CSRF'yi kapatmaz veya özel ignore matcher tanımlamaz; standart
Resource Server Bearer davranışı korunur. D4'te Bearer taşımayan CSRF reddi controller'a
geçmeden 401'e çevrilir; Gateway cookie CSRF/Origin koruması sürer. Spring repository exception
translation beklentisi düzeltildi. Bu ilk FAIL kayıtları başarı diye gösterilmez.

## Son kontroller
İlk tam pre-push (03:19:45 +03, exit 1): Hardware 354 PASS, 18 HTTP PASS;
Auth'ta iki OAuth log gizliliği testi FAIL; Game migration karşılaştırma/transfer
testlerinde CRLF/LF farkından 5 FAIL + 3 ERROR var. Host araç ortamındaki genel `DEBUG`
bayrağı Spring framework DEBUG loglamasını da açıyordu. Yalnız bu bayrak alt süreçten
çıkarıldığında aynı iki Auth sınıfının 22 testi kod değişmeden PASS (03:20:27 +03;
`logs/hardware-auth-debug-isolation-diagnostic.log`). Auth kaynağı değiştirilmedi.
Full/backend-only/Sonar test JVM'lerine genel host DEBUG aktarılmaz; açık Spring
test logger/property ayarları korunur. Gerçek ENV/çalışan servis log seviyesi değişmez.
Bu çalışma DEBUG/TRACE etkin üretim Auth loglarını güvenli diye doğrulamaz.

İkinci tam pre-push: Auth 290/290, Hardware 354/354 ve 18 HTTP PASS; Game satır sonu farkı
nedeniyle FAIL. Kullanıcı iki SQL çalışma kopyasını LF yapmayı ve `.gitattributes`
SQL kuralını açıklama sonrası onayladı. V34/V35 normalleştirildi; her iki dosya Git HEAD
ile tam bayt eşit, Game altında Git diff yok. Tarihsel ek kayıt adı:
`quality-blocker-game-eol.md` (arşivleme sırasında checkout'ta bulunmuyordu).
Son tam pre-push `full dev`: PASS / exit 0; `logs/hardware-auth-pre-push-after-eol.log`.
Maven bitişi 03:41:13 +03; 1138 test, failure/error/skipped 0; backend line coverage
%95,88; 18/18 HTTP health PASS, Git/secret/Compose PASS, **SAFE TO COMMIT**.
Hardware 356/356 PASS; Game MigrationArchitectureTest 4/4 ve MigrationTransferTest 8/8 PASS.
Taze Sonar test koşusunun Hardware JaCoCo raporu: 1573 covered / 1591 toplam satır,
18 missed, **%98,8686**. Exclusion eklenmedi.
Paketli temizleme ve yarım kalan audit/outbox referanslarını koruma için iki yeni test dahil;
başlangıçtaki 295 üzerine toplam 61 yeni Hardware senaryosu var.

İlk Sonar `scan dev`: erişim FAIL (yerel sunucu kapalı); analiz/gate SKIPPED.
Mevcut izole `sonar.cmd up dev` başarılı; SonarQube readiness UP, 26.9.0.129388.
`logs/hardware-auth-sonar-up.log`. Veri silme, uygulama Compose veya ENV değişikliği yok.
İlk tamamlanan analiz `logs/hardware-auth-sonar-after-eol.log`: 1138 test PASS;
39 yeni bulgu (Hardware 22, Social 17), Quality Gate FAIL. Bu sonuç başarı değildir.
Hardware bulguları kendi kapsamında düzeltildi: nullable claim yerel değişkenleri,
CSRF açık istisnasının kaldırılması, tekrar sabitleri, kullanılmayan catch değişkenleri,
matcher ve test assertion/rotation bekleme düzenlemeleri. Son yerel Maven verify
356/356 PASS, 03:55:54 +03 (`logs/hardware-auth-sonar-fixes-local.log`).
Kullanıcı Social müdahalesini açıkça reddetti; öneri uygulanmadı. Tarihsel ek kayıt adı:
`quality-blocker-social-sonar.md` (arşivleme sırasında checkout'ta bulunmuyordu).
Son Hardware kodu için iki tam kapı tekrar çalıştırılıyor. Social değişmeden kapı
başarısız kalırsa teslim `QUALITY_BLOCKED`; commit/PDA hazır bildirimi yapılmaz.

Son Hardware düzeltmelerinden sonraki `full dev`: PASS / exit 0;
`logs/hardware-auth-pre-push-confirmation.log`, Maven bitişi **04:06:20 +03**.
**1138 test**, failure/error/skipped 0; Hardware **356/356**; genel JaCoCo **%95,88**;
**18/18 HTTP PASS**, Git/secret/Compose PASS, **SAFE TO COMMIT**.
Ardından `scan dev` başlatıldı: `logs/hardware-auth-sonar-confirmation.log`.

Bu analiz **04:16:04 +03** tamamlandı: taze 1138 test PASS; Security **A**,
Reliability **C**, Maintainability **A**; overall coverage **%91,9**, new **%92,3**,
duplication **%0,7**. İlk 22 Hardware bulgusu kapandı; güvenlik zincirinde iki yeni
bakım bulgusu (S1192 sabit tekrarı, S3776 karmaşıklık) kaldı. Social 17 bulgu aynı.
Toplam 19 yeni bulgu nedeniyle gerçek gate **FAIL / exit 1**; suppression yapılmadı.
Son Hardware JaCoCo: **1580/1598 = %98,8736**, 18 missed; 356 test PASS.

İki Hardware bakım bulgusu için hata kodu sabiti tanımlandı ve mevcut cihaz/katalog
yetki ifadeleri ayrı private yardımcılara taşındı; yetki veya endpoint davranışı değişmedi.
Bu son kod için `logs/hardware-auth-pre-push-final-gate.log` ve ardından yeni Sonar
koşusu kullanılacak. Önceki iki kapı bu son düzenlemenin nihai kanıtı değildir.

Son kod `full dev` sonucu: **PASS / exit 0**, Maven bitişi **04:25:25 +03**;
1138 test, failure/error/skipped 0; Hardware 356 PASS, genel JaCoCo %95,88,
18 HTTP PASS, **SAFE TO COMMIT** (`logs/hardware-auth-pre-push-final-gate.log`).
Ardından son tüm-backend `scan dev` başladı (`logs/hardware-auth-sonar-final-gate.log`).

## Önceki iki kapı — 2026-10-24 sabah
- `pre-push.cmd full dev`: **PASS / exit 0**, Maven bitişi **04:25:25 +03**;
  1138 test, 0 failure/error/skipped; genel JaCoCo %95,88; 18/18 HTTP PASS;
  Git/secret/Compose PASS; çıktı **SAFE TO COMMIT**. Bu yalnız pre-push sonucudur.
- `sonar.cmd scan dev`: taze Maven testleri **BUILD SUCCESS**, **04:32:23 +03**;
  Hardware 356 PASS. JaCoCo **1577 covered / 1595 toplam = %98,8715**, 18 missed.
  Gerçek analiz bitişi **04:33:32 +03**, **exit 1 / Quality Gate FAIL**.
- Sonar genel notları: **Security A / Reliability C / Maintainability A**.
  Overall coverage %91,9; new coverage %92,3; duplication %0,7.
  Son iki Hardware bakım bulgusu da kapandı: **Hardware 0**, **Social 17** yeni açık bulgu.
  Reliability C'yi iki Social `JwtConfig` S6831 BUG bulgusu; sıfır-yeni-bulgu koşulunu
  kalan toplam 17 Social bulgusu engelliyor. Bir CRITICAL S6809 code smell de Social'da.
- Proje `ltz-backend`, kapsam tüm 18 backend modülü; branch `feature/hardware-service`,
  SCM HEAD `be5f205b1753189060f12f1017cae53c0105deda` üzerindeki çalışma kopyası.
  CE task `e5b28384-28e4-4827-a0c1-cb2f57d82316`;
  [yerel sonuç](http://localhost:5199/dashboard?id=ltz-backend).
  Yeni dosyalar henüz commit edilmediği için SCM blame uyarıları var; dosyalar analiz edildi.
- Kayıtlar: `logs/hardware-auth-pre-push-final-gate.log`,
  `logs/hardware-auth-sonar-final-gate.log`, `backend/target/sonar/report-task.txt`.
  Son kontrol: Social/Auth/Gateway/Game Git diff yok; master §0–11 ve API kataloğunun
  Hardware öncesi bölümleri aynı; `git diff --check` PASS. Game V34/V35 HEAD ile tam bayt eşit.

Tarama kapsamı, kalite kapısı, issue durumu veya exclusion değiştirilmedi. Kullanıcı
izni dışında Social düzeltmesi yapılmayacak; Umut/Social sahibi bulguları gidermeden
genel teslim kapısı geçmez. **QUALITY_BLOCKED; hazır commit mesajı/PDA kapatma önerisi yok.**

## Doğrulanmayan dış akışlar
Gerçek paketli uygulamalarla izole HTTP kabulü artık VERIFIED: Auth kayıt/giriş,
Mailpit doğrulama, Gateway reverse proxy/cookie/CSRF, Hardware sahiplik ve D4 worker ACK'i.
2026-10-24 16:40:10 +03 biten `logs/hardware-auth-runtime-e2e-final.log`:
**16 kontrol PASS / exit 0**, CLEANUP 3 Java süreci + 3 etiketli container/volume.
`ops/hardware-auth-e2e.mjs` bu kabulü gerçek ENV okumadan tekrarlar; yedi güvenlik
yardımcı testi, mevcut runner regresyonlarıyla birlikte **72 PASS**.
İlk CORS 403 yanıtını yanlış JSON parse eden betik hatası düzeltildi; ilk FAIL
`logs/hardware-auth-runtime-e2e.log` içinde tutulur. Servis kodu değişmedi.

Ortak Compose/TLS/tarayıcı ve 16 hedefin tamamlandığı D4 Saga E2E NOT_VERIFIED.
Diğer 15 hedef fixture'da 503 döner; Auth hedef listesi daraltılmaz veya sahte ACK verilmez.
Gerçek cihaz credential/PERF ve User/Social policy sözleşmeleri ertelenmiştir.
İlgili resmi talep kayıtları aynı klasörün `auth-service-talepleri/` altındadır.

## Git ve kullanıcı değişiklikleri
Git yazma komutu çalıştırılmaz. Önceden var olan `docs-Game Service/` → `docs-game/`
taşıması ve `ops/ops.md` değişikliği kullanıcıya aittir; entegrasyon bu değişiklikleri sahiplenmez.
