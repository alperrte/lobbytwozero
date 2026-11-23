# Hardware migration temizliği — kalite kanıtları

Tarih: 2026-10-21. Kapsam: 7 ilişkili migration / 16 tablo; başlangıç `28ad1a6`.
[Kullanıcı kararı, envanter ve ilerleme](../progress/hardware-migration-cleanup.md).

| Kontrol | Sonuç |
|---|---|
| Eski 10 / yeni 7 SQL şema eşdeğerliği | PASS — PostgreSQL 17.11; 16 tablo, 145 sütun, 93 constraint, 51 indeks, 2 trigger/fonksiyon |
| İzole hardware clean verify | PASS / exit 0 — 295 test, failure/error/skipped 0; 22:28:37 +03; logs/hardware-migration-local-verify.log |
| Hardware JaCoCo line | 1420 covered / 18 missed = %98,75; exclusion yok |
| Tam pre-push full dev | PASS / exit 0 — 313 test, %96,51 backend kapsam, 18 HTTP PASS, SAFE TO COMMIT; 22:32:12 +03 |
| Sonar scan dev / gerçek quality gate | PASS / exit 0 — Security A, Reliability A, Maintainability A; gate OK; 22:36:04 +03 |

Komutlar:

```powershell
mvn -f backend/pom.xml -pl services/hardware-service -am clean verify -B -ntp
.\pre-push\pre-push.cmd full dev
.\sonarqube\sonar.cmd scan dev
```

Maven clean eski dosya adlarının target/classes içinde kalmasını önler.
Gerçek ENV dosyaları açılmaz; onaylı kalite betikleri profili alt süreçte kullanır.
Uygulama DB sıfırlaması kullanıcıya aittir. İzole DB testleri canlı migration veya
yeniden build edilmiş servis image'ının çalışması olarak sunulmaz.
Gerçek Auth/cihaz/consumer entegrasyonları önceki onaylarla DEFERRED / NOT_VERIFIED kalır.

## Pre-push kanıtı

Resmi log: `logs/pre-push-2026-10-21T19-29-29-937Z-31300-50347f7b.log`.
Konsol: `logs/hardware-migration-pre-push-console.log`.
Git/Secret, Backend Tests/Build/Coverage, Docker Configuration ve Backend HTTP Health
satırlarının tamamı PASS. 17 iş servisi ve gateway kapsandı; bu test sayısı diğer
servislerin iş gereksinimlerinin tamamlandığı iddiası değildir. HTTP health mevcut
container image'larını sınar; yeni migration'ın uygulama DB'sine deploy kanıtı değildir.
295 hardware ve diğer modüllerde toplam 18 test: failure/error/skipped 0.

## SonarQube kanıtı

Resmi log: `logs/sonarqube-scan-2026-10-21T19-32-31-071Z-44864-4c1b8419.log`.
Konsol: `logs/hardware-migration-sonar-console.log`.
Yerel API kanıtı: `logs/hardware-migration-sonar-evidence.json`, 22:36:41 +03.

- Kapsam tüm backend / 18 modül; scan öncesi taze clean verify 313 PASS.
- Yerel Git branch `feature/hardware-service`, başlangıç revision `28ad1a6`;
  scanner ayrı branch parametresi vermediğinden Sonar projesi `ltz-backend/main`.
  Analiz çalışma ağacındaki yeni SQL/test kaynaklarını içerir; yeni commit atılmadı.
- CE task `bd8707e9-a85b-434d-8607-a3604b24d207`: SUCCESS, 22:36:02 +03.
- Analysis `7443910a-4dc9-4a35-b432-ebbc5a107df7`; gerçek Quality Gate `OK`.
- Security/Reliability/Maintainability `1.0 = A`; overall coverage %93,4,
  new coverage %95,0, duplication %0, yeni issue 0, açık issue/hotspot 0/0.
- Gate politikası VERIFIED / Sonar way; koşul gevşetme, exclusion veya suppression yok.

## Son doğrulama ve sınır

Derlenmiş `target/classes/db/migration` yalnız yeni 7 dosyayı içeriyor ve içerikleri
kaynaklarla birebir aynı. Hardware Surefire 295 test / 0 failure / 0 error / 0 skipped.
Yeni SQL kaynaklarında ALTER TABLE/INDEX, DROP ve rename adımı yok; 16 CREATE TABLE var.
`git diff --check` PASS; üretim Java ve HARDWARE dışındaki API bölümleri değişmedi.
Yeni belgelerin link/UTF-8/whitespace kontrolleri PASS. Git add/commit/push yapılmadı.

**Sonuç: bakım COMPLETE / iki kalite kapısı PASS / SAFE TO COMMIT.**
Kullanıcının boş DB kurulumu ve yeni kaynakla hardware image rebuild/start işlemi
bu testlerin dışındadır; ajan mevcut uygulama DB/history/volume'ünü değiştirmedi.
Yeni başlangıç migration'ları eski history ile kullanılmaz. HW-05 PDA kapanışı
bildirilmedi; ana kutusu `[ ]` olarak korundu.
