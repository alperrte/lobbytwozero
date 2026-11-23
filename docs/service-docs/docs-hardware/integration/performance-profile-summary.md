# HW-17 / PERF-21 — Profil doğrulaması ve performans özeti tüketicisi

Tarih: 2026-11-12. Sorumlu: Alper. Başlangıç kullanıcı commit'i `bd7e3bc0`; bu çalışma yeni commit/push oluşturmaz.

## Sonuç

Kullanıcının istediği iki açık nokta gerçek dört servisli HTTP kabulünde kapandı:

1. **Performance → Hardware:** owner profil listesinden seçilen profil, gerçek HW-17 batch endpoint'inde kullanıcının Bearer'ı ile doğrulanır ve Performance oturumu oluşturulur. Yabancı/olmayan profil reddedilir; Hardware kapalıyken 503 döner ve kayıt oluşmaz.
2. **Hardware → Performance özeti:** üretim `PerformanceServiceSummaryReader`, Auth'tan her okumada hesaba bağlı `summary:read` token'ı alıp Performance özet API'sini tüketir. Sonuç yalnız profil sahibine yeni Hardware okuma ucundan sunulur.

**Gerçek HTTP kabulü: 28/28 PASS**, exit 0. Dört JAR (Auth/Hardware/Performance/Gateway), üç ayrı geçici PostgreSQL ve Mailpit kullanıldı; dört süreç/dört container temizlendi. Game, oturum önkoşulu için tek fixture oyunu sunan sınırlı GAME-15 loopback kaynağıdır; gerçek Game kabulü bu sonuçtan çıkarılmaz. Recommendation servisi başlatılmadı veya geliştirilmedi; mevcut Auth scope negatif regresyonları korundu.

## Üretim akışı

`GET /api/v1/hardware/profiles/{profileId}/performance-summary`

- Gateway cookie/CSRF-Bearer altyapısı ve Hardware'ın mevcut typed user principal'ı kullanılır. Hesap kimliği istemciden alınmaz.
- `HardwarePerformanceService` yerel owner kontrolünü yapar, `PerformanceSummaryReader` portunu çağırır, cevap dönerken owner/hesap silme kontrolünü tekrar yapar. Dış HTTP boyunca DB transaction tutulmaz.
- Mevcut Hardware credential ile Auth `/internal/v1/service-tokens`, `audience=performance-service`, doğrulanmış `accountId`; yeni ENV veya scope istenmez.
- Mevcut internal `GET /internal/v1/accounts/{accountId}/performance-summary?hardwareProfileId={profileId}` kullanılır. Her okumada yeni token, sabit origin/path, redirect/retry yok; mevcut timeout ve 64 KiB JSON sınırı korunur.
- Profil bağı, kanonik metrik/birim, sayaç tipleri/toplamları, son ölçüm zamanı ve metrik zaman aralığı doğrulanır. İzinli DTO dışında alan veya ham gövde dışarı taşınmaz.
- Yalnız tamamlanmış oturumlar sayılır. Ölçümsüz tamamlanmış oturum sayısı korunur; eksik metrik sıfır olarak uydurulmaz. Silme sonrası veri tekrar okunur, token/özet cache'i yoktur.
- Yabancı/olmayan/silinmiş profil aynı 404. Auth/Performance kesintisi veya geçersiz yanıt `503 PERFORMANCE_SERVICE_UNAVAILABLE` + `Retry-After: 30`; hata/credential/token yanıt gövdesi veya cause olarak sızdırılmaz.
- Başarı/hata yanıtları private/no-store. Endpoint ve consumer kaydı `docs/api.md` HARDWARE/PERFORMANCE bölümlerindedir.

Yeni migration, bağımlılık, ENV, Auth/Gateway/Game kaynak değişikliği veya yeni güvenlik modeli yoktur. Performance'ta yalnız eski DEFERRED_CONTRACT Javadoc'u güncel runtime kabul kapsamıyla değiştirildi.

## Gerçek kabul kapsamı

- Gerçek SMTP kayıt/doğrulama, oturum, Gateway cookie→Bearer, consent, cihaz bağlama/iptal ve hesap silme regresyonları.
- Gerçek Hardware listesinden owner profil seçimi; Performance'ın gerçek Hardware owner batch doğrulamasıyla oturum açması.
- Başka hesabın profili/olmayan profil için 422 ve sıfır oturum oluşturma; Hardware summary'de anonim 401 ve foreign/missing 404.
- Gerçek Auth cihaz token'ıyla v2 FPS/CPU sıcaklığı örnek kabulü; aktif oturumun Hardware özetinden dışlanması.
- Tamamlanınca gerçek özet: bir oturum, iki örnek, FPS 60 / CPU 65; ham oturum/oyun/cihaz/hesap kimliği yok.
- Diğer hesap kendi ölçümsüz tamamlanmış oturumunu görür; metrikler uydurulmaz.
- Oturum silinince Hardware özetinde sayaçlar ve son ölçüm güncellenir; eski ölçüm dönmez.
- Gerçek Hardware süreci kapatıldığında Performance 503; doğrulanmamış yeni oturum oluşmaz.
- Mevcut sync idempotency, cihaz iptali ve D4 account-bound silme kontrolleri korunur; diğer 15 hedefe sahte ACK verilmez.

## Test ve kalite

| Kontrol | Sonuç |
| --- | --- |
| Ops Node testleri | 100/100 PASS, exit 0 |
| İlk dört modülün gerçek Maven test/build'i | Auth 620, Hardware 609, Performance 148, Gateway 18; 1395 test, failure/error/skip 0; BUILD SUCCESS |
| İlk kalite kapısı | FAIL: yeni reader `parse` metodu PMD complexity 18; baseline/kural değiştirilmeden metrik listesi ve tarih doğrulaması ayrıldı |
| Son Hardware izole tekrar | 609 PASS, %93,77 LINE; değişen satırlar %86,98; Checkstyle/PMD yeni ihlal 0; runtime Trivy HIGH/CRITICAL 0; exit 0 |
| `node ops/dev.mjs e2e performance` | **28/28 PASS**, exit 0; cleanup PASS |
| Standart worktree + yeni kaynak secret kontrolü | Gitleaks PASS; 13 değişen/yeni kaynak/betik, untracked Java dosyaları dahil |
| Son gerçek servis pre-push | Hardware 609 / %93,79, Performance 148 / %97,18; iki modül exit 0 ve BACKEND TEST CHECK PASSED |
| İlk Sonar tekrarı | Hardware bug/güvenlik 0/0; altı bakım bulgusu (çoklu değişken tanımı ve Retry-After tekrarı) düzeltildi; son pre-push tekrar PASS |
| Son Sonar tam backend test turu | 4397 test, failure/error/skip 0; build ve merkezi coverage PASS, LINE %95,54 |
| Son Hardware Sonar | A/A/A; bug/vulnerability/code smell 0/0/0; coverage %90,2, duplication %1,4 |
| Son Performance Sonar | A/A/A; bug/vulnerability/code smell 0/0/0; coverage %93,6, duplication %2,7 |
| Global Sonar Quality Gate | FAIL, exit 1; iki servis dışındaki 237 açık issue / 233 yeni issue; global B/C/A, coverage %91,8, duplication %3,2. Yeni kod duplication %1,64 ve coverage %91,8 geçer; new_violations kapıyı kırar |

Yerel kanıtlar: `test-results/performance-web/hardware-performance-integration-backend.log`,
`hardware-performance-integration-backend-final.log`, `integration-node.log`, `hardware-performance-runtime.log`.
Son kanıtlar: `integration-pre-push-hardware-final.log`, `integration-pre-push-performance.log`,
`integration-sonar.log`, `integration-sonar-after.json`, `integration-sonar-gate.json` aynı yerel kanıt klasöründedir.
Sonar CE: `619a1c64-0a1a-4c04-94dd-3cf473ee14a5`, 12 Kasım 2026. Son gerçek runner logu:
`logs/sonarqube-integration-quality-2026-11-12T11-09-26-057Z-53276-4b33deea.log`.
İki servis için component metrikleri ve tüm açık issue sayfaları doğrulandı; ayrı servis Quality Gate'i iddia edilmez.
Scanner SCM revision `bd7e3bc0`; yeni entegrasyon dosyaları henüz kullanıcı tarafından commit edilmediğinden eksik blame uyarıları vardır.
Kapsam tamamlandı; global kapı başarısız olduğundan depo bütünü için merge'e hazır sonucu verilmez.

Ortak DEV/TLS/tarayıcı, gerçek Game, User/Social paylaşımı, masaüstü sensör uygulaması, Recommendation ve global hesap silme Saga'sı bu iki bağlantının kabulünden ayrı işlerdir. Gerçek ENV dosyaları açılmadı/değiştirilmedi; kalite betiklerinin önceki izin kapsamındaki alt süreç kullanımı korunur.
