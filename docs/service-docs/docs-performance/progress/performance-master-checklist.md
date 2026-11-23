# Performance Service — Merkezi PDA Kontrol Listesi

Sorumlu: Alper · Servis: `performance-service` · DB: `performance_db` · Flyway: V21–V30 · Gateway: `/api/v1/performance/**`

Görev adları ve açıklamaları PDA kaynağından aynen alınmıştır; kod atlanmaz, görev başka faza taşınmaz.

İşaretleme: `[ ]` başlamadı veya tamamlanmadı · `[x]` kod + gerçek test + güvenlik kontrolü + dokümantasyon tamam.
Kısmi işler `[ ]` kalır, yanında `DURUM`, neden ve test bağlantısı yazılır. Ana task; alt tasklar ve faz kalite kapısı doğrulanmadan tamamlanmış sayılmaz.

Son güncelleme: 2026-11-06 · Faz 1 Auth kabulü PASS; REQ-04-A okuma izinleri de kullanıcının bildirdiği Kerem onayıyla eklendi. [Güncel kanıt](../integration/auth/auth-integration-06.md). Game/Hardware/Recommendation üretim tüketicileri, masaüstü ve ortak DEV kabulü ayrı bekler. Sonar bu değişiklikte NOT RUN. Faz kapanış işaretleri bütün ürünün production kabulü yerine geçmez.

## Karar özeti (Faz 1)

Cihaz kimliği, eşleştirme, cihaz token'ı ve cihaz iptali **Auth Service'tedir** (AUTH-17, `docs/api.md` "AUTH — Masaüstü cihaz kimliği"). Performance ikinci bir eşleştirme sistemi kurmaz;
yalnız cihazın Performance'taki **referansını** (`device_links`), izin durumunu ve senkronizasyon kaydını tutar. Ayrıntı: [phase-01-decisions.md](../decisions/phase-01-decisions.md).
Auth talebi: [auth-requests-01.md](../integration/auth-service-talepleri/auth-requests-01.md).

---

## Faz 1 — LTZ-ALP-PERF-01 — Cihaz Eşleştirme ve Güvenli Ölçüm Senkronizasyon API'leri

**Ana task açıklaması:** Gelecekteki masaüstü istemcisine yönelik izinli cihaz eşleştirme, tekrarsız veri kabulü ve erişim altyapısı kurulur.
**PDA gereksinimleri:** PERF-01, PERF-02, PERF-17, PERF-18, PERF-22
**Durum:** BACKEND AUTH KABULÜ PASS — gerçek Auth cihaz/istemci token'ları ve D4 işçisi izole HTTP ortamında doğrulandı. Ortak DEV, masaüstü ve Hardware üretim tüketicisi ayrıca bekler.

- [ ] **PERF-01 — Cihaz bağlantısı:** İleride masaüstü uygulamasından gelecek cihazların LTZ hesabıyla eşleştirilmesi için backend desteği.
  - Kabul: kanonik cihaz kimliği Auth'tadır; Performance cihaz referansını yalnız doğrulanmış Auth cihaz token'ıyla ve kullanıcının açık izniyle oluşturur; Hardware'in beklediği aktif cihaz kararı verilir.
  - Entegrasyon bağımlılığı: Auth `aud=performance-service` / `scope=performance:sync:write` (REQ-01-A), Hardware→Performance servis token'ı (REQ-01-C).
  - Durum: **AUTH ENTEGRASYONU PASS** — gerçek cihaz token'ı ve hesaba bağlı Hardware servis token'ı HTTP kabulünde doğrulandı; Hardware üretim istemcisi ayrı iş.
- [ ] **PERF-02 — Cihaz yönetimi:** Kullanıcının bağlantılı cihazları listeleyebilmesi ve bağlantılarını kaldırabilmesi için API desteği.
  - Kabul: kullanıcı yalnız kendi cihaz referanslarını listeler/kaldırır (IDOR → 404); kaldırılan cihaz yeni senkronizasyon yapamaz.
  - Entegrasyon kararı: Auth iptal olayı sağlamaz (REQ-01-B kararı). Yazma süresi ≤60 sn; cihaz listesi yenilendiğinde Auth'ta bulunmayan bağ DISABLED olur.
  - Durum: **AUTH ENTEGRASYONU PASS** — gerçek iptal ve liste yenilemesiyle mevcut token'ın reddi doğrulandı; yerel DISABLED kendiliğinden açılmaz.
- [ ] **PERF-17 — Veri senkronizasyonu:** Gelecekteki masaüstü istemcisinden izinli veri kabulü için backend sözleşmesi ve işleme altyapısı.
  - Kabul: yalnız geçerli cihaz token'ı + açık kullanıcı izni + aktif cihaz referansı ile kabul; zarf (envelope) sözleşmesi v1; örnek (sample) kaydı Faz 3'tedir.
  - Durum: **AUTH ENTEGRASYONU PASS** — v1 gerçek Auth HTTP kabulü PASS; v2 testleri gerçek Auth üreticisini kullanır. Masaüstü E2E ayrı kapsam.
- [ ] **PERF-18 — Tekrar gönderim koruması:** Tekrarlanan ölçüm isteklerinin mükerrer kayda yol açmaması.
  - Kabul: `UNIQUE(device_link_id, idempotency_key)` + istek özeti; aynı içerik tekrarı aynı yanıt, farklı içerik 409; eşzamanlı gönderim tek kayıt.
  - Durum: Performance tarafı hazır; PostgreSQL testleri kanıtı için bkz. [phase-01-tests.md](../testings/phase-01-tests.md).
- [ ] **PERF-22 — Veri erişim ve gizlilik:** Ölçüm ve performans kayıtlarına yalnızca yetkili kişilerin erişebilmesi.
  - Kabul: varsayılan reddet; kullanıcı yalnız kendi verisine erişir; cihaz token'ı kullanıcı uçlarında geçmez; hesap silme ucu (D4) ve silme sonrası yazma engeli.
  - Entegrasyon kanıtı: gerçek Auth silme işçisi → Performance 204, görev CONFIRMED, tekrar/hesap bağı/yazma çiti PASS.
  - Durum: **AUTH ENTEGRASYONU PASS** — izole kabul doğrulandı; ortak DEV ve tüm servis Saga doğrulaması ayrı bekler.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.
**Faz 1 kapanış durumu:** Auth kod/entegrasyon engelleri kapandı. İzole kabul ve merkezi kalite kapıları PASS; ortak DEV/production ve gerçek masaüstü kabulü bu kanıtın kapsamında değildir. Önceki faz kapanış işaretleri bu görevde topluca değiştirilmedi.

---

## Faz 2 — LTZ-ALP-PERF-02 — Performans Oturumu ve Oyun Raporu Yönetimi

**Ana task açıklaması:** Kullanıcı manuel oturum açar/kapatır, oyun bazlı performans raporu kaydeder ve kendi kayıtlarını silebilir.
**PDA gereksinimleri:** PERF-03, PERF-04, PERF-13, PERF-20
**Durum:** KOD/TEST/DOKÜMAN TAMAM — kalite kapısı ve gerçek servis entegrasyonu kısmen bekliyor (bkz. [phase-02.md](phase-02.md), [phase-02-tests.md](../testings/phase-02-tests.md))

- [ ] **PERF-03 — Performans oturumu oluşturma:** Kullanıcının performans oturumu kaydı oluşturabilmesi.
  - Durum: **PARTIAL** — `POST/GET /sessions`, `GET /sessions/active|{id}`; tek aktif oturum, terk edilmiş oturum, sunucu zamanı, 1000 sınırı PostgreSQL'de kanıtlandı.
  - Bekleyen: Game GAME-15 / Hardware HW-17 gerçek servis E2E (yalnız yerel mock). Auth cihaz token'ı hazır; cihaz örneklerinin `source=DEVICE` olması oturumun `MANUAL` kaynağını değiştirmez.
- [x] **PERF-04 — Performans oturumu sonlandırma:** Kaydedilmiş performans oturumunun tamamlanması.
  - Durum: **DONE (servis içi)** — idempotent bitirme, çelişkili bitirme 409, eşzamanlı bitirme/silme, iyimser kilit, IDOR.
- [ ] **PERF-13 — Oyun performans raporu:** Oyun, donanım, çözünürlük ve grafik ayarlarıyla ilişkili performans raporlarının oluşturulması.
  - Durum: **PARTIAL** — manuel rapor (`PUT/GET/DELETE /sessions/{id}/report`), ölçüm uydurma yok, DB CHECK'leri doğrulandı.
  - Bekleyen: oyun/donanım bağı Game/Hardware gerçek E2E'ye bağlı; otomatik/cihaz ölçümü Faz 3.
- [x] **PERF-20 — Performans verisi silme:** Kullanıcının kendi ölçüm ve performans raporlarını silebilmesi.
  - Durum: **DONE (servis içi)** — oturum/rapor/toplu silme (`confirm=true`), CASCADE, hesap silme fonksiyonu genişledi. Ham ölçüm (örnek) silme Faz 3 tablosuyla gelir; Auth silme işçisi DEV doğrulaması NOT VERIFIED (REQ-01-D).

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.
**Faz 2 kapanış durumu:** Commit metni hazırlandı; PERF-03/PERF-13 gerçek Game/Hardware E2E'si olmadan tam kapatılmadı.

---

## Faz 3 — LTZ-ALP-PERF-03 — FPS, Sıcaklık ve Sistem Telemetrisi Kayıt Altyapısı

**Ana task açıklaması:** Desteklenen kaynaklardan CPU/GPU/RAM/VRAM/disk/fan/FPS ölçümlerini doğrulayıp saklayan backend kuruludur.
**PDA gereksinimleri:** PERF-05, PERF-06, PERF-07, PERF-08, PERF-09, PERF-10, PERF-11, PERF-12
**Durum:** KOD/TEST/DOKÜMAN TAMAM — gerçek cihaz/Auth E2E bekliyor (bkz. [phase-03.md](phase-03.md), [phase-03-tests.md](../testings/phase-03-tests.md))

- [ ] **PERF-05 — CPU sıcaklık ölçümleri:** Desteklenen kaynaktan gelen CPU sıcaklık ölçümlerinin alınması ve saklanması.
  - Durum: **PARTIAL** — `CPU_TEMP_C` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). Sıcaklık 0 < v ≤ 150 (0 ölçülememiş sayılır, reddedilir).
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).
- [ ] **PERF-06 — GPU sıcaklık ölçümleri:** Desteklenen kaynaktan gelen GPU sıcaklık ölçümlerinin alınması ve saklanması.
  - Durum: **PARTIAL** — `GPU_TEMP_C` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). Sıcaklık 0 < v ≤ 150.
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).
- [ ] **PERF-07 — CPU kullanım ölçümleri:** İşlemci kullanım oranlarının kaydedilmesi.
  - Durum: **PARTIAL** — `CPU_USAGE_PERCENT` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). Yüzde 0..100.
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).
- [ ] **PERF-08 — GPU kullanım ölçümleri:** Ekran kartı kullanım oranlarının kaydedilmesi.
  - Durum: **PARTIAL** — `GPU_USAGE_PERCENT` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). Yüzde 0..100.
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).
- [ ] **PERF-09 — RAM ve VRAM ölçümleri:** Bellek kullanım bilgilerinin saklanması.
  - Durum: **PARTIAL** — `RAM_USED_MB, VRAM_USED_MB` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). MB; negatif reddedilir.
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).
- [ ] **PERF-10 — Disk performans verileri:** Desteklenen disk kullanım ve performans ölçümlerinin yönetilmesi.
  - Durum: **PARTIAL** — `DISK_READ_MBPS, DISK_WRITE_MBPS` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). Yalnız okuma/yazma verimi; doluluk/gecikme sözleşmesi yok (I-18).
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).
- [ ] **PERF-11 — Fan hızı verileri:** Desteklenen sensörlerin fan RPM verilerinin saklanması.
  - Durum: **PARTIAL** — `FAN_RPM` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). Hangi fan olduğu belirsiz (I-17); negatif reddedilir.
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).
- [ ] **PERF-12 — FPS ölçümleri:** Desteklenen kaynaktan gelen FPS ölçümlerinin kaydedilmesi.
  - Durum: **PARTIAL** — `FPS` v2 örnekleriyle `POST /sync/batches` üzerinden saklanır (PostgreSQL'de kanıtlı: aralık, hep-ya-hiç, idempotency, IDOR, kota). 0 < v ≤ 1000; negatif/0 reddedilir.
  - Bekleyen: gerçek Auth cihaz token'ı ve gerçek masaüstü istemcisiyle E2E yok (REQ-01-A, I-16, I-21).

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.
**Faz 3 kapanış durumu:** Commit metni hazırlanacak; PERF-05..12 gerçek Auth cihaz token'ı ve masaüstü istemcisi olmadan tam kapatılmadı.

---

## Faz 4 — LTZ-ALP-PERF-04 — Performans Geçmişi, İstatistik ve Grafik API'leri

**Ana task açıklaması:** Gerçek kayıtlardan sayfalı/filtreli ölçüm geçmişi, özet değerler ve grafik zaman serileri sunulur.
**PDA gereksinimleri:** PERF-14, PERF-15, PERF-16, PERF-19, PERF-21
**Durum:** PARTIAL — Auth okuma izinleri uygulandı; Hardware/Recommendation üretim tüketici istemcileri ayrı iş

- [ ] **PERF-14 — Ölçüm geçmişi:** Kullanıcının geçmiş performans kayıtlarının listelenmesi.
  - Durum: **PARTIAL** — `GET /sessions` filtre/sayfa/`totalElements`, `GET /sessions/{id}/samples`; sabit sıra, IDOR ve sınırlar PostgreSQL ile kanıtlı. Auth cihaz token engeli kapandı; gerçek masaüstü verisiyle E2E ayrıca bekler.
- [ ] **PERF-15 — Performans istatistikleri:** Ortalama, minimum ve maksimum değerlerin hesaplanması.
  - Durum: **PARTIAL** — oturum ve hesap istatistikleri (gerçek örneklerden avg/min/max/count, örnek ağırlıklı, sıfır doldurma yok) PostgreSQL ile kanıtlı. Bekleyen: büyük hesap maliyeti (I-27).
- [ ] **PERF-16 — Performans grafikleri API'si:** Web modülü için zaman serisi verilerinin sunulması.
  - Durum: **PARTIAL** — `GET /sessions/{id}/series`: ham veya açıkça belirtilmiş kova ortalaması, `maxPoints` sınırı, indeks kullanımı (EXPLAIN) kanıtlı. Bekleyen: Performance web (PWEB) ile gerçek tüketim.
- [ ] **PERF-19 — Ölçüm filtreleme:** Cihaz, oyun, tarih ve ölçüm türüne göre filtreleme.
  - Durum: **PARTIAL** — oyun, donanım profili, cihaz, durum, tarih ve metrik filtreleri birleşimli test edildi. Bekleyen: yok (servis içi); web tüketimi PWEB.
- [ ] **PERF-21 — Servis entegrasyonu:** Hardware ve Recommendation servislerine gerekli verilerin API sözleşmeleriyle sunulması.
  - Durum: **PARTIAL (tüketiciler)** — Hardware özeti ve Recommendation FPS referansı için Auth izinleri eklendi; olumlu sözleşme testleri gerçek Auth istemci üreticisini kullanır. [Güncel HTTP kabulü](../integration/auth/auth-integration-06.md). Hardware/Recommendation üretim tüketici istemcileri ayrı iş; eksik Auth izni engeli kalmadı.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.
**Faz 4 kapanış durumu:** PERF-21 Auth izin işi tamam; tüketici istemcileri ayrıca bekler. PERF-14/15/16/19 servis içi tamam, gerçek masaüstü verisi ve web tüketimi ayrı kapsamdadır.

---

## Sonraki adım: Flyway konsolidasyonu

Dört özellik commit'inden sonra ayrı bir aşamadır (Master Prompt §10). Uygulanmış migration geçmişi bozulmadan ve kullanıcı onayıyla yapılır. Durum: PLANNED.
