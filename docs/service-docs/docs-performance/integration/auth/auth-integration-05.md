# Auth entegrasyonu — 2026-11-06 kabul ve kalite kanıtı

> Bu kayıt REQ-01 kabulünün tarihçesidir (13 HTTP kontrolü, 138 Performance testi). Daha sonra kullanıcı onayıyla REQ-04-A Auth izinleri de eklendi; güncel durum ve genişletilmiş kabul [auth-integration-06.md](auth-integration-06.md) içindedir. Aşağıdaki eski açık talep bölümü artık güncel değildir.

Sorumlu: Alper · Kapsam: PERF-01/02/17/18/22, REQ-01-A/B/C/D/E.

## Sonuç

**PASS — hazır Auth sözleşmeleri Performance tarafında entegre edildi.** Çalışan Auth, Performance ve Gateway ile izole HTTP kabulünde **13 kontrol PASS**. Bu sonuç ortak DEV/production kabulü veya bütün hesap silme Saga'sının tamamlandığı iddiası değildir.

Auth ve Gateway kaynakları değiştirilmedi. Gerçek ENV dosyaları okunmadı/değiştirilmedi. Yeni ENV, üretim bağımlılığı veya migration eklenmedi. Donanım ve Recommendation tüketici koduna dokunulmadı.

## Gerçek HTTP kabulü

Betik: [ops/dev.mjs e2e performance](../../../ops/dev.mjs e2e performance).

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
node ops/dev.mjs e2e performance
```

Önkoşul: güncel `auth-service`, `performance-service`, `api-gateway` çalıştırılabilir JAR'ları, JDK25 ve Docker. Betik eski JAR'ı reddeder. Auth/Gateway bu koşu için `clean package -DskipTests` ile paketlendi; bu paketleme onların JUnit paketinin geçtiği şeklinde raporlanmaz. Performance'ın tüm testleri ayrıca çalıştırıldı.

Fixture: iki ayrı geçici PostgreSQL 17, Mailpit `v1.31.0`, üç gerçek uygulama süreci; yalnız rastgele loopback portları. Anahtar/parola/istemci sırrı koşu içinde üretilir; loglanmaz. Diğer servis hedefleri ACK vermeyen `503` fixture'ına gider. Silme çağrısını gözlemleyen loopback relay token'ı değiştirmeden gerçek Performance'a iletir.

| Kontrol | Sonuç |
| --- | --- |
| Performance V21–V23 ve `ddl-auto=validate` | PASS |
| Gerçek SMTP kayıt/doğrulama, login, Gateway cookie→Bearer, kullanıcı izni | PASS |
| Auth cihaz sahipliğiyle açık bağlama, yabancı hesapta 404 | PASS |
| `target=performance`, 60 sn, `performance:sync:write` | PASS |
| Gerçek cihaz token'ıyla v1 sync 200; aynı anahtar/gövdeyle replay | PASS |
| Hardware hedefli token sync'te, cihaz token'ı kullanıcı ucunda 401 | PASS |
| Gerçek `/internal/v1/service-tokens`: `devices:verify`, eksik hesap 400, doğru hesap 204, başka hesap/özet yetkisi 403 | PASS |
| Auth iptali yeni token'ı engeller; liste yenilemesi DISABLED yapar; mevcut token sync'te 403, iç cihaz sorgusu 404 | PASS |
| Gerçek Auth silme işçisi: `sync_consents` temizliği | PASS |
| Gerçek Auth silme işçisi: `device_links` temizliği | PASS |
| Gerçek Auth silme işçisi: `sync_batches` temizliği | PASS |
| Auth'ta Performance görevi CONFIRMED; aynı işçi token'ıyla tekrar 204, başka hesap 403; diğer hesap korunur, eski JWT ile yeniden yazma 401 | PASS |
| Diğer 15 silme görevi sahte ACK verilmeden PENDING | PASS |

Son çıktı:

```text
PERFORMANCE AUTH RUNTIME E2E PASS: 13 checks; global Saga NOT_VERIFIED; Game/Hardware consumers NOT_TESTED
[CLEANUP] 3 fixture processes and 3 owned containers removed
```

Gerçek Game/Hardware, Recommendation tüketici istemcileri, v2 sensör örneklerinin çalışan servislerle E2E'si ve masaüstü istemcisi bu kabulün kapsamında değildir. D4 oturum/rapor/örnek temizliği servis entegrasyon testlerinde sınanır; HTTP fixture yalnız izin/cihaz/makbuz verisi üretir.

## Performance kalite kontrolü

| Kontrol | Sonuç |
| --- | --- |
| JUnit / gerçek PostgreSQL / gerçek güvenlik zinciri | **138 test PASS, hata/atlama 0** |
| Toplam JaCoCo LINE | **1239/1276, %97,10** |
| Merkezi coverage: değişen Java satırları | **23/23, %100 PASS** |
| Checkstyle / PMD ratchet | **Yeni ihlal 0, PASS** |
| Trivy runtime bağımlılık taraması | **HIGH/CRITICAL 0, PASS** |
| Maven `clean verify` + merkezi coverage | **BUILD SUCCESS** |
| HTTP fixture'ın kullandığı ortak yardımcıların Node testleri | **9 PASS** (`node --test ops/hardware-auth-e2e.test.mjs`) |
| Yeni kabul betiği sözdizimi / diff whitespace | **PASS** (`node --check`, `git diff --check`) |
| SonarQube | **NOT RUN** — eski rapor bu değişikliklerin kanıtı değildir |

Olumlu cihaz/istemci token testleri Auth'un gerçek üretim sınıflarını derleyip kullanır. Sahte/bozuk claim testleri yerel imzalıdır; Auth cihaz listesi JUnit'te yerel HTTP double'dır. Yukarıdaki ayrı HTTP kabulünde gerçek Auth kullanılır.

Yeni regresyonlar: Auth iptalinin listeyle yansıması, yerel DISABLED'ın kendiliğinden açılmaması, başka hesapların korunması, 401/503/bozuk/eksik/tekrarlı UUID yanıtında kısmi güncelleme olmaması, cihaz ömrünün 60 saniyeyi aşmaması ve `exp` anında reddedilmesi.

Standart `node pre-push/ci/backend.mjs --modules services/performance-service` bu Windows ortamında kısıtlı child ENV nedeniyle Docker Compose eklentisini bulamadı (`unknown flag: --env-file`). Ortak altyapı değiştirilmedi. Aynı `backendCheck` fonksiyonu ve bütün kalite kapıları yalnız işletim sistemi dizinleri eklenerek başarıyla çalıştırıldı:

```powershell
$env:JAVA_HOME = 'C:/Users/alper/Tools/jdk-25.0.4.1+1'
@'
import {backendCheck} from './pre-push/ci/backend.mjs';
import {execute} from './lib/quality-tools.mjs';
await backendCheck({modules:['services/performance-service'],
  runCommand:(cmd,args,options)=>execute(cmd,args,{...options,
    env:{...options.env,ProgramFiles:process.env.ProgramFiles,
      ProgramData:process.env.ProgramData,LOCALAPPDATA:process.env.LOCALAPPDATA}})});
'@ | node --input-type=module
```

Gerçek ENV kullanan `pre-push.cmd service` çalıştırılmadı; `SAFE TO COMMIT` çıktısı alınmış gibi sunulmaz. İzole runner'ın Maven, coverage, kalite ve bağımlılık kapıları PASS'tir. Teşhis için açılan `ltz-test-performance-preflight` projesi de kapatıldı.

İlk HTTP denemesi eski Auth JAR'ını reddetti; ikinci deneme temizlenmemiş `target/classes` içindeki eski V61 dosyası yüzünden başlamadı. `clean package` sonrası güncel migration'larla kabul geçti. Auth migration kaynakları değiştirilmedi.

## Bu koşu sırasında kalan Auth talebi (sonradan uygulandı)

**REQ-04-A açık:** Hardware için `summary:read`, Recommendation için `reference:read` hâlâ Auth izin listesinde ve hedef çiftlerinde yok. Kerem'in bunları hesaba bağlı token olarak tanımlaması gerekir. Cihaz izni bu okuma izinlerinin yerine kullanılmadı. Ayrıntı: [auth-integration-04.md](auth-integration-04.md).
