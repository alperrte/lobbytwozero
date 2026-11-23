# LTZ-ALP-HW-02 kalite kanıtı

**Son durum: PRE-PUSH PASS / SONAR A/A/A + QUALITY GATE PASS.**
134 hardware testi; %98,02 (694/708). Sonar öncesi 152 backend testi / 18 modül.
Son analiz: 1d274361-0d59-4aee-9e54-3303c0ef52e5.
Önceki ara/başarısız sonuçlar aşağıda tarihsel kanıt olarak korunur.

Tarih: 2026-10-21. Kapsam: hardware-service. Yerel branch: feature/hardware-service.

## Başlangıç regresyonu — PASS

Komut: `mvn -f backend/pom.xml -pl services/hardware-service -am verify -B -ntp`.
Bitiriş: 2026-10-21 01:34:51 +03:00. Exit code: 0, BUILD SUCCESS.
58 test, 0 failure, 0 error, 0 skipped. Java 25.0.4.1 / Maven 3.9.16.
Testcontainers PostgreSQL 17.11, mevcut V1/V2 Flyway ve JPA validate başarılı.
JaCoCo: 321 covered / 11 missed / 332 toplam satır = %96,69.
Yeni HW-02 kodu henüz yok; bu ölçüm mevcut HW-01 davranışını doğrular.

## HW-02 final kapıları

HW-11 ara doğrulaması: aynı Maven verify komutu 2026-10-21 01:47:00 +03:00
exit 0 / BUILD SUCCESS. 117 PASS, 0 failure/error/skipped.
HardwareCatalogSearchTest 27 yeni senaryo; JaCoCo 576/590 = %97,63.
PostgreSQL 17.11 / V1–V4 Flyway/JPA validate PASS; indeks varlığı doğrulandı.
Log: logs/hw02-hw11-verify.log.

HW-10 ara doğrulaması: ilk 90 testten 2 sürüm beklentisi hatası düzeltildi.
Son aynı Maven verify komutu 2026-10-21 01:44:06 +03:00 exit 0 / BUILD SUCCESS:
90 PASS, 0 failure/error/skipped. HardwareCatalogControllerTest 32 yeni senaryo.
JaCoCo 532 covered / 14 missed / 546 toplam = %97,44; exclusion yok.
V1/V2/V3 Flyway ve JPA validate PostgreSQL 17.11 üzerinde PASS.
Log: logs/hw02-hw10-verified.log. Bu ara kontrol final kapılarının yerine geçmez.

- Pre-push: PASS, hardware-service / 134 test / %98,02 / exit 0.
- Sonar Security/Reliability/Maintainability: A/A/A.
- Sonar Quality Gate: PASS; yeni issue 0.
- HW-10/HW-11/HW-14 yeni davranış testleri: PASS, son Maven verify 134 test.
- Gerçek Auth entegrasyonu: DEFERRED_AUTH / NOT_VERIFIED.

Önceki HW-01 sonuçları bu görevin final PASS kanıtı olarak kullanılmaz.
İki sözleşme kullanıcı tarafından onaylandı; kod ve V3/V4/V5 uygulandı.

## Son yerel doğrulama — PASS

`mvn -f backend/pom.xml -pl services/hardware-service -am verify -B -ntp`.
2026-10-21 01:53:29 +03:00, exit 0, BUILD SUCCESS.
134 PASS, 0 failure/error/skipped: kampanya 15 + katalog 33 + arama 27 +
uygulama güvenlik portu 1 + önceki 58. JaCoCo 688 covered / 14 missed / 702 toplam,
%98,01; exclusion yok. PostgreSQL 17.11 / V1–V5 Flyway / JPA validate PASS.
Log: logs/hw02-final-local-verify.log. V1/V2 diff boş; git diff --check PASS.
Önceki clean verify 01:52:24 +03:00 tarihinde 133 PASS; ardından Unicode-normalizasyon
negatif testi eklendi ve son verify tekrarlandı. Bu sonuç final pre-push/Sonar değildir.

## Final pre-push — PASS

`.\pre-push\pre-push.cmd hardware-service`, exit 0,
2026-10-21 01:55:58 +03:00. Çıktı: BACKEND TEST CHECK PASSED.
134 PASS, 688/702 satır = %98,01. Kapsam yalnız hardware-service backend testleri;
full Compose/health veya Sonar sonucu değildir.
Log: logs/pre-push-2026-10-20T22-55-35-104Z-11284-0c20bfbe.log.
Sonar scan dev mevcut oturum onayıyla başlatıldı; sonuç henüz yok.

## İlk Sonar denemesi — FAIL

`.\sonarqube\sonar.cmd scan dev` exit 1. Backend Reports PASS; Backend Analysis,
Quality Gate Backend ve Overall Targets Backend FAIL. Security A, Reliability C,
Maintainability A; overall coverage %89,2 / duplication %0; new coverage %92,4.
Atanmış Sonar way politikası VERIFIED; yeni issue sayısı sıfır şartı sağlanmadı.
Analiz b07f63aa-3816-4ec8-83ef-8e5e1ac757c9; CE task
89193099-ce25-4df3-a079-450aab0774b7; hotspot 0.
Log: logs/sonarqube-scan-2026-10-20T22-56-19-391Z-10628-427d9766.log.

Onaylı mevcut Sonar yardımcılarıyla yalnız kural/dosya/satır bilgisi okundu;
secret değerleri yazdırılmadı. 9 açık bulgu (kritik/blocker/vulnerability yok):
S2259 nullable authentication; dört S107 uzun entity imzası; S1130/S112 gereksiz
generic throws; S6878 record pattern; S5778 test lambda'sında ikinci çağrı.
Yalnız hardware kapsamındaki kapı düzeltmeleri yapıldı: açık null guard,
Campaign.Terms / Component.Selection / Specifications.Values domain record'ları,
gereksiz throws kaldırılması, record pattern ve test UUID üretimini lambda dışına alma.
API JSON/endpoint/Flyway davranışı değişmedi. Son kod için iki kapı yeniden çalıştırılacak.

## Sonar düzeltmeleri sonrası pre-push — PASS

Aynı onaylı hardware-service pre-push komutu exit 0, 2026-10-21 02:01:49 +03:00:
134 PASS, %98,02, BACKEND TEST CHECK PASSED. Eski profil/bileşen regresyonları da PASS.
Log: logs/pre-push-2026-10-20T23-01-26-677Z-14516-d2f69fe4.log.
İkinci Sonar taraması başlatıldı; logs/sonarqube-scan-2026-10-20T23-02-21-151Z-40956-fd31b65b.log.

## Son Sonar — PASS

`.\sonarqube\sonar.cmd scan dev`, exit 0, SONAR SCAN PASSED.
Backend Reports: 152 PASS, 0 failure/error/skipped, 18 modül; yerel ağırlıklı
JaCoCo %93,68. Hardware son XML 694 covered / 14 missed / 708 toplam = %98,02.
Sonar Access / Reports / Analysis / Quality Gate / Overall Targets beş satır PASS.
Security A, Reliability A, Maintainability A; overall coverage %89,2, duplication %0.
Yeni kod coverage %92,3, duplication %0, issue 0; PREVIOUS_VERSION baseline.
Atanmış Sonar way politikası VERIFIED; gerçek analiz koşulları OK.

Analiz 1d274361-0d59-4aee-9e54-3303c0ef52e5,
2026-10-21 02:04:53 +03:00 (server 2026-10-20T23:04:53+0000).
CE task af69ad0f-b846-4f1c-87b6-04604cd76d36; scan bitişi 02:05:11 +03:00.
Proje ltz-backend; yerel Git branch feature/hardware-service; mevcut runner ayrı
Sonar branch parametresi göndermez. Log:
logs/sonarqube-scan-2026-10-20T23-02-21-151Z-40956-fd31b65b.log.

Son analizden sonra aynı izinli yerel yardımcılarla API kontrolü: açık issue 0,
vulnerability 0, hotspot 0. Dokuz önceki bulgu kapandı. Token/profil gerçek
değerleri çıktıya yazdırılmadı. Pre-push ve Sonar son kodda PASS;
gerçek Auth/JWT/rol eşlemesi/E2E hâlâ DEFERRED_AUTH / NOT_VERIFIED.
