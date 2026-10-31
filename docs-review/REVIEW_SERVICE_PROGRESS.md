# Review Service ilerlemesi

Kapsam: yalnız `feature/review-service`, Review Service backend ve `docs-review/`.
Başlangıç: 31 Ekim 2026. İşaretler yalnız testlerle doğrulanan görevleri gösterir.

## Faz 1 — LTZ-HAM-REV-01

- [x] REV-01 — Oyun puanlama (39 test PASS; PostgreSQL 17/Flyway ve gerçek JWT/JWKS güvenlik testleri)
- [x] REV-02 — İnceleme oluşturma (45 test PASS; V52/Flyway/JPA validate ve eşzamanlı tekillik)
- [x] REV-03 — İnceleme güncelleme (48 test PASS; sahiplik/sürüm/zaman ve eşzamanlı düzenleme)
- [x] REV-04 — İnceleme silme (50 test PASS; sahiplik/idempotency/yeni inceleme/puan koruma)
- [x] REV-05 — Olumlu / olumsuz değerlendirme (55 test PASS; V53, bağımsız alanlar ve hesap silme)
- [x] REV-11 — Tekil değerlendirme/puanlama ve kalite kapanışı: 67 test; strict verify/coverage/ratchet PASS; Trivy HIGH/CRITICAL 0/0; Sonar Quality Gate OK ve A/A/A

Son doğrulama (31 Ekim 2026): yalnız services/review-service için **BACKEND TEST CHECK PASSED**. 67 test, 0 failure/error/skipped; strict Maven clean verify, Checkstyle/PMD ve merkezi coverage PASS; toplam satır %96,81, değişen satır %96,76. Trivy 0.76.0, güncel/verifiye DB ile 111 pakette HIGH 0 / CRITICAL 0. Sonar Quality Gate OK; Security/Reliability/Maintainability A/A/A; coverage %93,2, yeni kod %100, duplication %0, bug/vulnerability/hotspot 0/0/0. Yeni issue 0; toplam 13 bakım bulgusu vardır.

Önceki Trivy engeli kapandı: 992.405.588 baytlık Java DB arşivi, ölçülen bağlantı hızıyla 360 saniyeye sığmıyordu. Resmî kaynaktan parçalı indirme 825 saniyede tamamlandı; manifest/katman SHA-256 doğrulandı, DB metadata güncelliği kontrol edildi ve geçici cache ile değiştirilmemiş güvenlik kapısı çalıştırıldı. Skip/update bypass/TLS gevşetme yoktur.

Sonar .env.dev içindeki mevcut token'ı loadSonarEnv/loadProfileEnv ile güvenli biçimde yükledi; token geçerliydi. Önceki 401 özel secretsiz süreçte token'ın yüklenmemesiydi. Token/ENV değişmedi, yeni token üretilmedi. Yalnız Review için ltz-review-service projesi analiz edildi. Sonar'ın guard'da saptadığı nullable Boolean ve transaction self-invocation bulguları minimal Review düzeltmesiyle giderildi; iki regresyon testi eklendi.

Faz 1 kalite koşusu için açılan geçici PostgreSQL projeleri/ağları kapatıldı. Canlı karşı servis E2E ve tüm repo full-dev kontrolü o takip görevinin kapsamı dışındadır. Faz 1 tamamlandı. Kullanıcının sonraki açık onayıyla Faz 2 başlatıldı; Faz 3/4 onaylı değildir.

## Faz 2 — LTZ-HAM-REV-02

- [x] REV-06 — İnceleme beğenileri (75 test PASS; V55, gerçek JWT/HTTP, idempotency, eşzamanlılık ve silme tutarlılığı; final kalite sonuçları Faz 2 raporunda)
- [ ] REV-07 — Uygunsuz inceleme raporlama — ENTEGRASYON BEKLİYOR
- [x] REV-09 — Öne çıkan incelemeler (85/85 Review testi PASS; beğeni/tarih/UUID sıralaması, oyun/minLikes filtresi, sayfalama, V56 ve eşzamanlı snapshot doğrulaması)
- [ ] REV-10 — Servis sınırları ve entegrasyon — ENTEGRASYON BEKLİYOR

Faz 2 kapsamı yalnız Review backend ve docs-review. Başlangıç branch temizdi: feature/review-service. Önceki kök prompt dosyaları diskte bulunamadı; konuşmada okunan sıra ve güncel Hamza görev dosyası esas alındı. Admin/Notification kaynaklarında yalnız launcher bulunuyor; rapor alıcı sözleşmesi yok. Gerçek teslim ve tam REV-07/REV-10 kapanışı bu dış bağımlılık çözülmeden iddia edilmeyecek.

Önceki durma noktası REV-06 kalite doğrulaması + entegrasyon talepleriydi. Kullanıcının sonraki açık talimatıyla yalnız REV-09'a geçildi ve tamamlandı. REV-07, outbox, relay ve geçici teslim altyapısı kodlanmadı. Kendi incelemesine beğeni/rapor yasağı onaylı; rapor yasağının uygulanması gelecekteki REV-07'ye aittir.

Entegrasyon talep dosyaları (hepsi öneri/sözleşme bekliyor):

- [auth-review-integration.md](request/auth-review-integration.md) — Auth/Kerem: Review→Admin ve Admin→Review için hedefe bağlı izin/protokol; Notification üreticisi Review olursa koşullu ayrı izin; insan moderatör güven modeli.
- [admin-review-integration.md](request/admin-review-integration.md) — Admin/ortak sorumlu atanmalı: kalıcı rapor kabulü, event/rapor tekilleştirme, vaka durumları, yetkili karar ve Review uygulama ACK'i.
- [notification-review-integration.md](request/notification-review-integration.md) — Notification/ortak sorumlu atanmalı: uygulanmış moderasyonun sahibine bildirimi, tek üretici, alıcı/dedup/tercih ve teslim sözleşmesi. Beğeni bildirimi ek talep edilmedi.

Game için mevcut toplu kart sözleşmesi var. User'da önerilen ReviewSummaryChanged alıcısı ve Auth'ta Review→User events:ingest izni var; eksik kalan Review üreticisidir. Bu nedenle bu iki servise doğrulanmamış yeni özellik talebi açılmadı. Belgeler hedef servisler için kesin geliştirme emri değildir; karşılıklı sözleşme/kullanıcı kararı beklenir.

REV-06 önceki kalite (REV-09 için yeniden çalıştırılmadı): **BACKEND TEST CHECK PASSED**, 75/75 test, strict verify/Checkstyle/PMD/ratchet ve merkezi coverage PASS (%97,37 LINE; değişen satır %97,50). Trivy 111 pakette HIGH/CRITICAL 0/0. Gerçek Sonar Quality Gate OK ve A/A/A; coverage %93,8, yeni kod %94, yeni issue 0, duplication %0. Analysis ID ff6c4f1f-5c27-4d9f-b740-5bbffdb2c66b.

REV-09 güncel doğrulama: yalnız Review Service Maven **test** aşaması; **85 test, 0 failure/error/skipped, BUILD SUCCESS**. 7 yeni PostgreSQL/MockMvc, 2 servis birim ve 1 gerçek imzalı JWT/HTTP testi; önceki 75 test yeniden geçti. Geçici PostgreSQL 17 üzerinde V51–V56 Flyway uygulanması/JPA validate ve V56 indeks tanımı doğrulandı. Maven validate adımı Checkstyle sıfır ihlal bildirdi. Gerçek ENV kullanılmadı, geliştirme DB'sine dokunulmadı. Test container'ı çalışma sonunda kaldırıldı.

Pre-push/CI/backendCheck, Trivy, Sonar Scanner/analiz/Quality Gate/A/A/A: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**. Strict verify/PMD/merkezi coverage/ratchet için yeni sonuç yoktur; önceki sonuçlar yeni REV-09 koduna mal edilmez.

Sıralama: likeCount DESC, createdAt DESC, id ASC; yalnız VISIBLE ve gameId/minLikes filtreleri. Snapshot tek istek içindir; ayrı sayfalarda yeni oy/içerik/silme sonuçları değiştirebilir. Rapor/moderasyon verileri bulunmadığından kullanılmadı; REV-07/REV-15 uygulanmış sayılmaz. Dış servisten yeni özellik gerekmemiştir. Ayrıntı: [Faz 2 çalışma raporu](REVIEW_SERVICE_PHASE_02_REPORT.md), [API](REVIEW_SERVICE_API.md), [kararlar](REVIEW_SERVICE_DECISIONS.md).

**Faz 2 kısmen tamamlandı:** REV-06/REV-09 [x], REV-07/REV-10 [ ] ENTEGRASYON BEKLİYOR. REV-09 sonrasında duruldu; Faz 3 için açık kullanıcı onayı bekleniyor. Faz 3/4 geliştirilmedi. Commit/push/merge/branch değişikliği yapılmadı.

## Faz 3 — LTZ-HAM-REV-03

- [ ] REV-08 — Ortalama oyun puanı
- [ ] REV-12 — Toplu oyun puanı özetleri

## Faz 4 — LTZ-HAM-REV-04

- [ ] REV-13 — Spam ve kötüye kullanım kontrolü
- [ ] REV-14 — İnceleme ve puan istatistikleri
- [ ] REV-15 — Moderasyon sonrası içerik davranışı
