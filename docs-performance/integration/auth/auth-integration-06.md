# REQ-04-A — Auth okuma izinleri ve kapanış kanıtı

Tarih: 2026-11-06 · Uygulayan: Alper · Servisler: Auth ve Performance.

Kullanıcı, Kerem'den gerekli izni aldığını bildirerek Auth tarafındaki bu değişikliği açıkça yetkilendirdi. Bu kayıt uygulama/test kanıtıdır; Kerem adına verilmiş bir cevap değildir.

## Sonuç ve kapsam

**REQ-04-A Auth izin engeli KAPANDI.** Mevcut Auth servis token ucundan alınan gerçek Hardware ve Recommendation token'ları Performance'ın ilgili okuma uçlarında doğrulandı. Ortak DEV/production kabulü, Hardware/Recommendation üretim tüketici istemcileri ve bütün hesap silme Saga'sının tamamlanması bu sonuçtan ayrı tutulur.

| İstemci | Tek audience | Konu | Kapsamlar | Hesap bağı |
| --- | --- | --- | --- | --- |
| Hardware | `performance-service` | `svc:hardware-service` | `devices:verify summary:read` | `account_id` zorunlu; ACTIVE hesap |
| Recommendation | `performance-service` | `svc:recommendation-service` | `reference:read` | `account_id` zorunlu; ACTIVE hesap |

Auth hedefin yapılandırılmış bütün kapsamlarını aynı token'a yazar; Hardware'in cihaz doğrulama izni korunurken özet okuma izni eklenmiştir. Mevcut servis token üretici/servis/controller imzaları korundu. Tek audience ve tek hesap bağı, 60 sn ömür, `typ=svc+jwt`, RS256/JWKS, `jti` ve `iat/nbf/exp` kuralları değişmedi.

Auth `TokenClaims`, `ServiceClientConfig` ve `application.properties` güncellendi. İki izin hem bilinen hem hesaba bağlı kapsam listesinde yer alır; bağsız kapsamlarla karıştırılamaz. Performance'ın üretim alıcı yetki denetimleri korunur. Olumlu kaynak sözleşme testleri gerçek `ServiceClientTokenIssuer` kullanır; geçersiz konu/kapsam testleri yerel imzalıdır.

## ENV

**Yeni ENV anahtarı gerekmez.** `.env.example` ve Auth Compose aktarımı mevcut iki adı zaten içeriyor:

- `AUTH_HARDWARE_SERVICE_CLIENT_SECRET`
- `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET`

Gerçek `.env` dosyaları okunmadı/değiştirilmedi. Mevcut değerlerin dolu olduğu doğrulanmış sayılmaz: boşsa ilgili istemci mevcut fail-closed davranışla kapalı kalır. Auth ve üretim tüketici servisinin aynı istemci için aynı mevcut sırla yapılandırılması gerekir. Yeni migration veya bağımlılık eklenmedi.

## Testler

| Kontrol | Sonuç |
| --- | --- |
| Değişiklik öncesi Auth ilgili testleri | 22 PASS (`ServiceAudienceTokenTest`, `ServiceClientConfigTest`) |
| Değişiklik sonrası Auth tam paket | **537 PASS, failure/error/skipped 0** |
| Performance tam paket | **138 PASS, failure/error/skipped 0** |
| Auth JaCoCo LINE | **3688/3945, %93,49** |
| Auth merkezi değişen satır kapısı | **69/69, %100 PASS** |
| Performance JaCoCo LINE | **1239/1276, %97,10** |
| Performance merkezi değişen satır kapısı | **23/23, %100 PASS** |
| Maven `clean verify` | **BUILD SUCCESS** |
| Checkstyle/PMD ratchet (iki servis) | **PASS, yeni ihlal 0** |
| Trivy runtime bağımlılık kapısı | **PASS, HIGH/CRITICAL 0** |
| Ortak HTTP yardımcılarının Node testleri | **9 PASS** |
| HTTP betiği sözdizimi / `git diff --check` | **PASS** |
| SonarQube | **NOT RUN** |

Değişen satır sayıları merkezi kapının Git tabanına göre hesapladığı mevcut branch/worktree kapsamıdır; yalnız bu iki yeni sabitin satır sayısı değildir. Yeni executable kodu olmayan sabit sınıflarında merkezi politika `exempt` kaydı üretir; production exclude veya test atlama eklenmedi.

Auth `ServiceAudienceTokenTest` 16 test, `ServiceClientConfigTest` 10 test içerir. Yeni kapsamlar için eksik/geçersiz/olmayan hesap, SUSPENDED/BANNED/DISABLED hesap, yanlış istemci sırrı ve yetkisiz istemci retleri sınandı. Mevcut diğer servis hedeflerinin davranışları tam pakette kontrol edildi.

## Gerçek Auth + Performance + Gateway HTTP kabulü

[ops/performance-auth-e2e.mjs](../../../ops/performance-auth-e2e.mjs) mevcut kabul betiği genişletildi. Auth/Performance güncel `clean verify` JAR'ları ve mevcut güncel Gateway JAR'ı kullanıldı; eski JAR denetimi korunur.

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
node ops/performance-auth-e2e.mjs
```

**15 kontrol PASS.** Önceki cihaz/izin/replay/iptal/D4 kontrolleri korundu; buna ek olarak gerçek client-credentials yoluyla:

- Hardware token'ıyla cihaz doğrulama **204**, performans özeti **200**; FPS referansı ve hesap silme **403**.
- Recommendation token'ıyla FPS referansı **200**; veri yokken `fpsSampleCount=0`, `reliable=false` (FPS uydurulmaz).
- Recommendation ile Hardware özeti, cihaz doğrulama, başka hesap ve hesap silme **403**.
- İki istemcide de token isteğinde eksik `accountId` **400**.
- Auth cihaz iptali sonrasında liste yenilemesi DISABLED yapar; mevcut cihaz token'ı sync'te reddedilir.
- Gerçek Auth D4 işçisi Performance'tan **204** alır ve görevi **CONFIRMED** yapar; diğer 15 görev sahte ACK verilmeden PENDING kalır.

Son çıktı:

```text
PERFORMANCE AUTH RUNTIME E2E PASS: 15 checks; global Saga NOT_VERIFIED; Game/Hardware consumers NOT_TESTED
[CLEANUP] 3 fixture processes and 3 owned containers removed
```

İki ayrı geçici PostgreSQL 17 ve Mailpit kullanıldı. Anahtarlar/sırlar koşu içinde üretildi; gerçek ENV kullanılmadı. Diğer servisler ACK vermeyen `503` fixture'ına yönlendirildi. Hardware/Recommendation uygulamalarının üretim istemcileri bu kabulde çalıştırılmaz; gerçek Auth üretimi ve Performance alıcı uçları HTTP ile sınanır. HTTP okuma testleri boş hesap yanıtını doğrular; gerçek ölçümlerin özet/referans hesaplamaları PostgreSQL kaynak sözleşme testlerinde sınanır.

## Windows test ortamı ve tekrar çalıştırma

İlk tam Auth koşusunda **18 test Mockito self-attach hatasıyla başlayamadı** (`Could not initialize plugin: MockMaker`); bu koşu PASS sayılmadı. Mevcut Mockito JAR'ını JVM başlangıcında ajan olarak yükleyen deneme ilgili 7 testi geçirdi. Ardından Auth ve Performance'ın bütün `clean verify` koşusu yeniden çalıştırıldı; yukarıdaki 537 + 138 test bu son koşuya aittir. JaCoCo veya güvenlik kontrolleri kapatılmadı; ortak POM değiştirilmedi.

Önceki koşuda görülen Docker Compose keşif sorunu için yalnız işletim sistemi dizin değişkenleri aktarılır. Mockito ajanı yalnız bu test child process'lerine verilir; uygulama ENV sözleşmesi değildir:

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
@'
import path from 'node:path';
import {backendCheck} from './pre-push/ci/backend.mjs';
import {execute,repositoryRoot} from './lib/quality-tools.mjs';
const agent=path.join(repositoryRoot,'.cache/m2/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar');
await backendCheck({modules:['services/auth-service','services/performance-service'],
  runCommand:(cmd,args,options)=>execute(cmd,args,{...options,env:{...options.env,
    ProgramFiles:process.env.ProgramFiles,ProgramData:process.env.ProgramData,
    LOCALAPPDATA:process.env.LOCALAPPDATA,JAVA_TOOL_OPTIONS:'-javaagent:'+agent}})});
'@ | node --input-type=module
```

Gerçek ENV kullanan `pre-push.cmd service` çalıştırılmadı; `SAFE TO COMMIT` çıktısı alınmış gibi raporlanmaz. Yukarıdaki komut aynı merkezi Maven/test/coverage/kalite/dependency kapılarını çağırır; onay mekanizması, threshold veya manifest değiştirilmedi.

## Kalan işler

Auth'tan istenen REQ-01-A/B/C/D/E ve REQ-04-A için mevcut kod/izole kabul kapsamındaki engeller kapandı. Gerçek masaüstü sensör akışı, Game/Hardware profil doğrulama E2E'si, Hardware/Recommendation üretim tüketici istemcileri, bütün kullanıcı oturum/rapor akışlarının çalışan servislerle kabulü ve ortak DEV/production doğrulaması ayrı işlerdir. Tüm ürün veya tüm hesap silme Saga'sı tamamlandı olarak işaretlenmez.
