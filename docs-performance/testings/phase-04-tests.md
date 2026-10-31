# Faz 04 test raporu — LTZ-ALP-PERF-04

Tarih: 2026-10-31 · Ortam: JDK 25.0.4.1, Windows 11, PostgreSQL 17 (Testcontainers). Sonuçlar gerçek çalıştırmalardan alınmıştır.
Durum değerleri: PASS · FAIL · BLOCKED · NOT RUN.

## 1. Özet

| Kontrol | Sonuç | Kanıt / not |
| --- | --- | --- |
| Birim + entegrasyon testleri (134) | **PASS** | 0 başarısız, 0 atlanan (Faz 1: 31 + Faz 2: 46 + Faz 3: 34 + Faz 4: 23) |
| JaCoCo kapsamı | **PASS** | Toplam %97,07; değişen kod %97,22 (1223/1258 satır). Eşik ≥ %80 |
| Checkstyle / PMD ratchet (`node pre-push/ci/lint-ratchet.mjs check --modules services/performance-service`) | **PASS** | Eski borç 0, şimdi 0, yeni ihlal 0 |
| CI backend runner (`node pre-push/ci/backend.mjs --modules services/performance-service`) | **PASS** | EXIT 0, BUILD SUCCESS |
| Trivy (bağımlılık/yapılandırma) | **PASS** | Bulgu 0 |
| Standart repo pre-push (`.\pre-push\pre-push.cmd performance-service`) | **PASS** | BACKEND TEST CHECK PASSED, EXIT 0; Tests PASS, Coverage PASS (133 test / %97,07; test bölünmeden önceki koşu — CI backend runner bölünme sonrası 134 test PASS), Maven Verify PASS, Code Quality (yalnız yeni) PASS. Script ENV'i kendisi yükler; ENV dosyaları okunmadı |
| SonarQube | Performance-service bulgusu **0** (ilk taramada S5961 vardı: `sessionStatsAreExact…` testi 26 assertion → iki teste bölündü, yeniden tarama temiz). Genel kapı (Quality Gate / Overall Targets) **FAIL**: açık 4 hedef-bozan + 35 yeni-kod bulgusu tamamen başka servislerde (social, hardware S2259/S3358/S3776, game, user S6878, review, library); bu servislere dokunulmadı | `.\sonarqube\sonar.cmd scan dev` |
| Gerçek Auth servis token'ı ile Hardware/Recommendation uçları | **BLOCKED** | REQ-04-A / REQ-01-C: Auth her servis token'ında `sub=svc:auth-service` üretir. Testler yerel imzalı sözleşme token'ı kullanır |
| Gerçek Hardware / Recommendation tüketimi | **NOT RUN** | Tüketici kodu yok; bu servislere dokunulmadı |
| Gerçek cihaz verisiyle uçtan uca | **BLOCKED** | REQ-01-A |
| Şema değişikliği | Yok | V24 yazılmadı; Flyway `validate` (V21–V23) bağlam testinde PASS |

## 2. Test dağılımı (134)

| Sınıf | Tür | Adet |
| --- | --- | --- |
| `PerformanceHistoryIntegrationTest` | Entegrasyon (MockMvc + gerçek güvenlik zinciri + PostgreSQL) — **Faz 4** | 23 |
| `PerformanceSampleIntegrationTest` | Entegrasyon (Faz 3) | 27 |
| `SampleRulesTest` | Birim (Faz 3) | 7 |
| `PerformanceSessionIntegrationTest` | Entegrasyon (Faz 2; liste artık filtreli `Specification` kullanır) | 32 |
| `PerformanceSessionDomainTest` | Birim (Faz 2) | 14 |
| `PerformanceSyncIntegrationTest` | Entegrasyon (Faz 1) | 23 |
| `PerformanceDomainTest` | Birim (Faz 1) | 7 |
| `PerformanceServiceApplicationTests` | Bağlam yükleme + Flyway `validate` | 1 |

## 3. Kapsanan senaryolar (Faz 4)

- **Filtre birleşimleri (PERF-19):** oyun, donanım profili, cihaz bağı, durum; ikili/üçlü birleşimler; eşleşmeyen filtre boş sonuç.
- **Zaman sınırları:** `[startedFrom, startedTo)` alt sınır dahil / üst sınır hariç; yalnız alt veya yalnız üst sınır; kapsam dışı boş sonuç.
- **Sayfalama ve deterministik sıra:** aynı `startedAt` taşıyan 7 oturum 4 sayfada eksiksiz ve tekrarsız, `id DESC` ile sabit sıra; son sayfadan sonrası boş; `totalElements` doğru.
- **Boş sonuç ve geçersiz girdi:** boş geçmiş; `size`/`page` sınırları, bilinmeyen durum/metrik, bozuk UUID/tarih, `from >= to`, aşırı tarih → 400 `INVALID_REQUEST`.
- **Ham örnekler:** `captured_at, metric, id` sırası, birim, iç kimlik sızdırmama (alan adları sabit), `metric`/`from`/`to` filtreleri, sayfalama (`hasNext`), 500 sınırı.
- **IDOR:** başka hesabın oturumu ve olmayan oturum üç okuma ucunda da **aynı** 404 (`detail`/`status`/`code` eşit); başka hesabın oturumları listede ve hesap istatistiğinde görünmez.
- **Kimlik:** kimliksiz, cihaz token'ı, servis token'ı ve bozuk token kullanıcı uçlarında 401.
- **İstatistik doğruluğu (PERF-15):** oturum avg/min/max/count/firstAt/lastAt elle hesaplanmış değerlerle, `metric`/`from` filtresiyle; metrik toplam sayımı = oturum `sampleCount` = listelenen örnek sayısı; `avg` SQL referansına eşit; örneksiz metrik/oturum için sıfır doldurma yok (`metrics=[]`).
- **Hesap istatistiği:** oyuna/zaman aralığına göre filtre, örnek ağırlıklı ortalama, `sessionCount` (örneksiz dahil) ve metrik `sessionCount`, başka hesabın 999/888 değerlerinin sızmaması.
- **Grafik serisi (PERF-16):** küçük seri ham ve zaman sıralı; 20.000 örnekli seri 100 noktaya indirgenir (`bucketMillis=199991`, `downsampled=true`, kova hizası, `count` toplamı = 20.000, ağırlıklı ortalama 54,5, min 30, max 79); 1.500 örnekli çok günlük aralıkta `maxPoints` sınırı, pencere daraltma, aralık dışı boş seri, tek örneklik sınır; geçersiz `metric`/`maxPoints`/zaman → 400.
- **İndeks davranışı:** 5.000 örnekli oturumda `EXPLAIN` planı `uq_performance_samples_point` indeksini kullanır, `Seq Scan` yoktur (küçük tabloda `enable_seqscan=off` altında).
- **Hardware sözleşmesi (PERF-21):** yalnız `COMPLETED` oturumlar ve ilgili profil; ABANDONED, başka profil ve başka hesap verisi hariç; yanıt alan adları sabit; oturum/oyun/cihaz kimliği ve oyun adı yanıtta geçmez; ölçüm yoksa `sessionCount=0`, `metrics=[]`, `lastMeasuredAt=null`.
- **Recommendation sözleşmesi (PERF-21):** 59 FPS örneğinde `reliable=false` ve değerler `null`; 100 örnekte `reliable=true`, avg SQL referansına eşit; yalnız FPS ve COMPLETED; başka hesap / ABANDONED hariç; `hardwareProfileId` filtresi; olmayan profil → sayım 0.
- **Servis kimliği (yanlış/eksik → yetkisiz):** yanlış servis konusu (Recommendation→Hardware ucu ve tersi) 403; yanlış kapsam / diğer ucun kapsamı / `devices:verify` 403; başka hesaba bağlı `account_id` 403; yanlış audience, kimliksiz, kullanıcı ve cihaz token'ı 401; gerçek Auth `ServiceTokenIssuer` token'ı (`sub=svc:auth-service`) iki uçta 403; POST ve bilinmeyen iç yol 403; eksik zorunlu parametre 400.

## 4. Çalıştırma sırasında bulunan hatalar

- **Kapsam adı Auth desenine uymuyordu:** ilk taslak `performance:summary:read` Auth `ServiceTokenIssuer` kapsam desenini (`^[a-z]+:[a-z]+$`) geçemiyordu (gerçek issuer `IllegalArgumentException` fırlattı). Kapsamlar `summary:read` / `reference:read` yapıldı (KARAR-P4-08, I-29).
- Test verisi üreticisi yüzde metriği için 101 değer üretip DB `CHECK`'ine takıldı; üretici 30..79 aralığına çekildi (DB savunması çalıştığının da kanıtı).
- Hatalı test beklentileri düzeltildi: kova genişliği formülü (`ceil((span+1)/maxPoints)` = 199.991 ms), 404 gövdesindeki `instance` farkı (karşılaştırma `detail/status/code`), güvenlik zincirinin POST/bilinmeyen yolu 405 yerine 403 reddetmesi.

## 5. Sınırlamalar (dürüst durum)

- Hardware/Recommendation uçları **yerel imzalı sözleşme token'ı** ile test edildi; bu gerçek Auth/Hardware/Recommendation entegrasyonu değildir (BLOCKED).
- Veri hacmi testleri 20.000 satıra kadardır; hesap kotası sınırında (500.000) hesap istatistiği süresi ölçülmedi (I-27).
- Okuma uçlarında hız sınırı yoktur (I-26); bu yüzden hız sınırı testi yazılmadı.
