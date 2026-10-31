# Performance Service — Merkezi PDA Kontrol Listesi

Sorumlu: Alper · Servis: `performance-service` · DB: `performance_db` · Flyway: V21–V30 · Gateway: `/api/v1/performance/**`

Görev adları ve açıklamaları PDA kaynağından aynen alınmıştır; kod atlanmaz, görev başka faza taşınmaz.

İşaretleme: `[ ]` başlamadı veya tamamlanmadı · `[x]` kod + gerçek test + güvenlik kontrolü + dokümantasyon tamam.
Kısmi işler `[ ]` kalır, yanında `DURUM`, neden ve test bağlantısı yazılır. Ana task; alt tasklar ve faz kalite kapısı doğrulanmadan tamamlanmış sayılmaz.

Son güncelleme: 2026-10-31 · Genel durum: Faz 1 IN_PROGRESS (Auth bağımlılığı nedeniyle kısmen BLOCKED), Faz 2 kod/test/doküman tamam (gerçek Game/Hardware E2E bekliyor), Faz 3–4 PLANNED.

## Karar özeti (Faz 1)

Cihaz kimliği, eşleştirme, cihaz token'ı ve cihaz iptali **Auth Service'tedir** (AUTH-17, `docs/api.md` "AUTH — Masaüstü cihaz kimliği"). Performance ikinci bir eşleştirme sistemi kurmaz;
yalnız cihazın Performance'taki **referansını** (`device_links`), izin durumunu ve senkronizasyon kaydını tutar. Ayrıntı: [phase-01-decisions.md](../decisions/phase-01-decisions.md).
Auth talebi: [auth-requests-01.md](../integration/auth-service-talepleri/auth-requests-01.md).

---

## Faz 1 — LTZ-ALP-PERF-01 — Cihaz Eşleştirme ve Güvenli Ölçüm Senkronizasyon API'leri

**Ana task açıklaması:** Gelecekteki masaüstü istemcisine yönelik izinli cihaz eşleştirme, tekrarsız veri kabulü ve erişim altyapısı kurulur.
**PDA gereksinimleri:** PERF-01, PERF-02, PERF-17, PERF-18, PERF-22
**Durum:** IN_PROGRESS — gerçek Auth cihaz token'ıyla uçtan uca doğrulama BLOCKED (bkz. auth-requests-01)

- [ ] **PERF-01 — Cihaz bağlantısı:** İleride masaüstü uygulamasından gelecek cihazların LTZ hesabıyla eşleştirilmesi için backend desteği.
  - Kabul: kanonik cihaz kimliği Auth'tadır; Performance cihaz referansını yalnız doğrulanmış Auth cihaz token'ıyla ve kullanıcının açık izniyle oluşturur; Hardware'in beklediği aktif cihaz kararı verilir.
  - Entegrasyon bağımlılığı: Auth `aud=performance-service` / `scope=performance:sync:write` (REQ-01-A), Hardware→Performance servis token'ı (REQ-01-C).
  - Durum: **PARTIAL / BLOCKED** — Performance tarafı ve sözleşme testleri hazır; gerçek Auth cihaz token'ıyla E2E yapılamadı.
- [ ] **PERF-02 — Cihaz yönetimi:** Kullanıcının bağlantılı cihazları listeleyebilmesi ve bağlantılarını kaldırabilmesi için API desteği.
  - Kabul: kullanıcı yalnız kendi cihaz referanslarını listeler/kaldırır (IDOR → 404); kaldırılan cihaz yeni senkronizasyon yapamaz.
  - Entegrasyon bağımlılığı: Auth'ta gerçek iptal kullanıcı tarafından `DELETE /api/v1/auth/devices/{id}` ile yapılır; anında yayılım için REQ-01-B.
  - Durum: **PARTIAL / BLOCKED** — Performance tarafı hazır; Auth iptal olayı ve gerçek cihaz token'ı doğrulanmadı.
- [ ] **PERF-17 — Veri senkronizasyonu:** Gelecekteki masaüstü istemcisinden izinli veri kabulü için backend sözleşmesi ve işleme altyapısı.
  - Kabul: yalnız geçerli cihaz token'ı + açık kullanıcı izni + aktif cihaz referansı ile kabul; zarf (envelope) sözleşmesi v1; örnek (sample) kaydı Faz 3'tedir.
  - Durum: **PARTIAL / BLOCKED** — gerçek cihaz token'ı yok.
- [ ] **PERF-18 — Tekrar gönderim koruması:** Tekrarlanan ölçüm isteklerinin mükerrer kayda yol açmaması.
  - Kabul: `UNIQUE(device_link_id, idempotency_key)` + istek özeti; aynı içerik tekrarı aynı yanıt, farklı içerik 409; eşzamanlı gönderim tek kayıt.
  - Durum: Performance tarafı hazır; PostgreSQL testleri kanıtı için bkz. [phase-01-tests.md](../testings/phase-01-tests.md).
- [ ] **PERF-22 — Veri erişim ve gizlilik:** Ölçüm ve performans kayıtlarına yalnızca yetkili kişilerin erişebilmesi.
  - Kabul: varsayılan reddet; kullanıcı yalnız kendi verisine erişir; cihaz token'ı kullanıcı uçlarında geçmez; hesap silme ucu (D4) ve silme sonrası yazma engeli.
  - Entegrasyon bağımlılığı: Auth silme işçisi → `DELETE /internal/v1/accounts/{id}` gerçek ortak ortam doğrulaması yapılmadı.
  - Durum: Performance tarafı hazır; ortak DEV doğrulaması NOT VERIFIED.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.
**Faz 1 kapanış durumu:** AÇIK — Auth değişikliği (REQ-01-A) gelmeden kapatılamaz.

---

## Faz 2 — LTZ-ALP-PERF-02 — Performans Oturumu ve Oyun Raporu Yönetimi

**Ana task açıklaması:** Kullanıcı manuel oturum açar/kapatır, oyun bazlı performans raporu kaydeder ve kendi kayıtlarını silebilir.
**PDA gereksinimleri:** PERF-03, PERF-04, PERF-13, PERF-20
**Durum:** KOD/TEST/DOKÜMAN TAMAM — kalite kapısı ve gerçek servis entegrasyonu kısmen bekliyor (bkz. [phase-02.md](phase-02.md), [phase-02-tests.md](../testings/phase-02-tests.md))

- [ ] **PERF-03 — Performans oturumu oluşturma:** Kullanıcının performans oturumu kaydı oluşturabilmesi.
  - Durum: **PARTIAL** — `POST/GET /sessions`, `GET /sessions/active|{id}`; tek aktif oturum, terk edilmiş oturum, sunucu zamanı, 1000 sınırı PostgreSQL'de kanıtlandı.
  - Bekleyen: Game GAME-15 / Hardware HW-17 gerçek servis E2E (yalnız yerel mock); cihaz kaynaklı oturum Faz 3 (REQ-01-A).
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
**Durum:** PLANNED

- [ ] **PERF-14 — Ölçüm geçmişi:** Kullanıcının geçmiş performans kayıtlarının listelenmesi.
- [ ] **PERF-15 — Performans istatistikleri:** Ortalama, minimum ve maksimum değerlerin hesaplanması.
- [ ] **PERF-16 — Performans grafikleri API'si:** Web modülü için zaman serisi verilerinin sunulması.
- [ ] **PERF-19 — Ölçüm filtreleme:** Cihaz, oyun, tarih ve ölçüm türüne göre filtreleme.
- [ ] **PERF-21 — Servis entegrasyonu:** Hardware ve Recommendation servislerine gerekli verilerin API sözleşmeleriyle sunulması.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.

---

## Sonraki adım: Flyway konsolidasyonu

Dört özellik commit'inden sonra ayrı bir aşamadır (Master Prompt §10). Uygulanmış migration geçmişi bozulmadan ve kullanıcı onayıyla yapılır. Durum: PLANNED.
