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
- [x] REV-10 — Servis sınırları ve entegrasyon — Review→User `ReviewSummaryChanged` DEV'de canlı doğrulandı (12 Kasım 2026)

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

**Faz 2 kısmen tamamlandı:** REV-06/REV-09 [x], REV-07/REV-10 [ ] ENTEGRASYON BEKLİYOR. Önceki durma noktası REV-09 sonrasıydı; sonraki açık kullanıcı onayıyla Faz 3 başlatılıp aşağıdaki kapsamda tamamlandı. Faz 4 geliştirilmedi. Commit/push/merge/branch değişikliği yapılmadı.

## Faz 3 — LTZ-HAM-REV-03

- [x] REV-08 — Ortalama oyun puanı (92/92 Review testi PASS; sayısal puan AVG/count, iki basamak HALF_UP, boş sonuç ve hesap temizliği)
- [x] REV-12 — Toplu oyun puanı özetleri (Review backend kabulü; 102/102 test PASS, 50 oyun/10.000 puanda tek aggregate SQL). **5 Kasım 2026:** Game→Review servis okuma iç ucu da Review'da uygulandı (`POST /internal/v1/games/rating-summaries`, `rating-summaries:read`, `sub=svc:game-service`; Review modülü 174/174 test PASS). Auth çifti `game-service → review-service / rating-summaries:read` Auth `origin/main`'de mevcut (bf473ed); Game tüketicisi bekliyor; global minRating/RATING için Seçenek A seçildi ve Review imleçli dışa aktarma ucu uygulandı (Game projeksiyonu bekliyor; Review modülü 174/174 test PASS).

Faz 3'te yalnız Review Maven test aşaması çalıştırıldı: REV-08 kapanışında 92, son REV-12 koşusunda **102 test, 0 failure/error/skipped, BUILD SUCCESS**. Başlangıç 85 teste 6 tekil API, 7 toplu API, 2 servis birim ve 2 gerçek JWT/HTTP testi eklendi. PostgreSQL 17/Flyway V51–V56 uygulama/checksum ve Hibernate validate PASS. Yeni migration yok; game_ratings game_id indeksi EXPLAIN ile doğrulandı. Test DB'si disposable container içindeydi ve sonunda kaldırıldı.

Tekil GET /games/{gameId}/rating-summary ve toplu POST /games/rating-summaries: yalnız sayısal puan AVG/count, iki basamak HALF_UP, puansız null/0. Metin/tavsiye/beğeni/Steam ayrıdır. Topluda en fazla 50 ham UUID, boş liste items=[], ilk görülme sırasında tekilleştirme; dolu listede tek SQL, boş listede sıfır SQL. Eşzamanlı okuyucu/yazıcı tutarlılığı doğrulandı. Ayrıntı: [Faz 3 raporu](REVIEW_SERVICE_PHASE_03_REPORT.md).

Kullanıcı kararıyla mevcut kullanıcı JWT güvenliği korundu; anonim veya yeni servis-token yetkisi açılmadı. Kullanıcı JWT'si Game servis kimliği yerine kullanılmayacak. **Game→Review servis erişimi NOT IMPLEMENTED / NOT VERIFIED, ENTEGRASYON BEKLİYOR.** Review tarafındaki REV-12 kabulünü kapatmak bu entegrasyonu kapatmaz.

- [game-review-integration.md](request/game-review-integration.md) — Game/Hamza: toplu özet tüketimi, servis okuma yolu, Auth kimliği, timeout/cache/hata davranışı ve global puan filtre/sıralaması için kararlar bekleniyor.
- [auth-review-integration.md](request/auth-review-integration.md) — Auth/Kerem: Game→Review çifti Auth main'de mevcut (`rating-summaries:read`, bf473ed); Review alıcısı bu kapsam adına uyarlandı (5 Kasım 2026); secret dağıtımı ve canlı doğrulama bekliyor. Auth ve diğer servisler değiştirilmedi. Admin/Notification talepleri beklemeyi sürdürür.

Pre-push/CI/backendCheck, Trivy ve Sonar Scanner/Quality Gate/A/A/A: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**. PMD/strict verify/coverage/ratchet için yeni sonuç yoktur. Önceki fazların PASS değerleri bu faza taşınmadı.

**FAZ 3 TAMAMLANDI — FAZ 4 İÇİN ONAY BEKLİYORUM.** REV-07/REV-10 bekliyor; bu görevlere veya Faz 4'e kendiliğinden geçilmedi. Commit/push/merge/branch değişikliği yapılmadı.

## Faz 4 — LTZ-HAM-REV-04

- [ ] REV-13 — Spam ve kötüye kullanım kontrolü — **KISMEN TAMAMLANDI — REV-07 ENTEGRASYONU BEKLİYOR.** Bağımsız kısım (inceleme oluşturma 5/60 sn, beğenme+kaldırma ortak 60/60 sn, 429 + `Retry-After`, PostgreSQL ortak sayaç) uygulandı; rapor kotası ve mükerrer rapor engeli REV-07 rapor altyapısı olmadığından uygulanamadı.
- [x] REV-14 — İnceleme ve puan istatistikleri — Review backend kabulü tamamlandı (`GET /api/v1/review/games/{gameId}/statistics`).
- [ ] REV-15 — Moderasyon sonrası içerik davranışı — **ENTEGRASYON BEKLİYOR (+ ürün kararı bekliyor).** Kod yazılmadı; mevcut durum doğrulandı, karar maddeleri belgelendi.

Faz 4 devralma notu: Önceki Codex oturumu ("İncele Faz 1 PostgreSQL promptu") REV-13 oran sınırı politikasının onayında bitti; kullanıcı politikayı onayladı (31 Ekim 2026, 12:07), ancak çalışma ağacında hiçbir Faz 4 kodu/belgesi yoktu (git temiz, son commit 920a3c4, son migration V56). Faz 4 çalışması bu kayıt üzerinden sıfırdan devralındı; Faz 1–3 koduna dokunulmadı.

**Faz 4 durumu: FAZ 4 KISMEN TAMAMLANDI — ENTEGRASYON BEKLEYEN GÖREVLER VAR.**

Doğrulama (yalnız Review Service Maven `test` aşaması, disposable PostgreSQL 17 Alpine, `-Pci` Surefire ayarları, offline Maven): **135 test, 0 failure/error/skipped, BUILD SUCCESS** (başlangıç 102 → +33: AbuseQuotaApiTest 14, AbuseQuotaServiceTest 4, AbuseControlPropertiesTest 2, AbuseQuotaFailureTest 1, ReviewStatisticsApiTest 10, ReviewHttpIntegrationTest +2). Flyway V51–V57 doğrulandı, Hibernate `validate` geçti. Mevcut `ReviewApiTest` yalnız test içi kota limiti yükseltilerek (`review-create.limit=1000`) çalıştırıldı; davranış beklentileri değişmedi.

Pre-push/CI/backendCheck, Trivy, SonarQube Scanner/Quality Gate/A/A/A, strict verify, PMD, merkezi coverage ve ratchet: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**. Önceki fazların kalite sonuçları Faz 4 koduna taşınmaz.

Ayrıntılar: [Faz 4 raporu](REVIEW_SERVICE_PHASE_04_REPORT.md), [API](REVIEW_SERVICE_API.md), [kararlar](REVIEW_SERVICE_DECISIONS.md). Talep belgeleri: yeni dosya açılmadı; [admin-review-integration.md](request/admin-review-integration.md) §13 REV-15 maddeleriyle genişletildi.

Kullanıcı Faz 4 raporunu kabul etti (31 Ekim 2026) ve REV-15 politikasını kısmen karara bağladı: gizlenen/arşivlenen incelemelerin bağımsız puanı ortalamaya dahil kalır; bu incelemeler herkese açık listelerde ve öne çıkanlarda gösterilmez. Beğeniler ve yazar müdahalesi gibi kalan kararlar Admin entegrasyonuyla netleşecek. Kod değişmedi; kararlar [DECISIONS](REVIEW_SERVICE_DECISIONS.md) içinde. `docs/api.md` ve `docs-security/` değiştirilmeyecek.

Commit/push/merge/branch değişikliği yapılmadı. REV-07, REV-10, REV-13 (raporlama kısmı) ve REV-15 entegrasyon bağımlılıkları açıktır; Game→Review entegrasyonu da başlatılmadı.

**Entegrasyon talepleri tamamlandı (yalnız dokümantasyon, 31 Ekim 2026):** REV-13'ün raporlamaya bağlı ihtiyaçları (kendi incelemesini raporlama yasağı, rapor tekilliği, rapor oran sınırı, mükerrer vaka engeli, güvenilir iletim/timeout/retry, spam kabul kriterleri) [Admin talebine](request/admin-review-integration.md); REV-15'in entegrasyon ihtiyaçları (sorumluluk matrisi, onaylı politikalar, `KARAR BEKLİYOR` listesi) Admin, [Auth](request/auth-review-integration.md) ve [Notification](request/notification-review-integration.md) taleplerine işlendi. Belgelemek ilgili görevi tamamlamaz; durumlar değişmedi: REV-07 ve REV-10 ENTEGRASYON BEKLİYOR, REV-13 KISMEN TAMAMLANDI — REV-07 RAPORLAMA ALTYAPISI BEKLENİYOR, REV-15 ENTEGRASYON BEKLİYOR, REV-14 tamamlandı. Kod, migration ve test değişmedi.

## Auth entegrasyonu (31 Ekim 2026)

Review tarafındaki Auth uyumu doğrulandı ve eksikler giderildi: kullanıcı `at+jwt` uyumu ve rol sertleştirmesi, `aud` eksik token'ın NPE yerine 401 olması, Auth servis token alıcısı, Auth'un gerçek silme adaptörüyle silme testi, REV-13 sayaçlarının hesap silmeyle uyumu. Sonuç: yalnız Review Maven `test`, **149 test, 0 failure/error/skipped** (iki ardışık koşu; 135 + 14 yeni). Auth'un gerçek kaynakları geçici dizine derlenip testin kendi anahtarıyla çalıştırıldı; **canlı Auth uçtan uca doğrulaması NOT VERIFIED**. Pre-push, backendCheck, Trivy, SonarQube: KULLANICI TALEBİYLE ÇALIŞTIRILMADI. Ayrıntı: [REVIEW_AUTH_INTEGRATION_REPORT.md](REVIEW_AUTH_INTEGRATION_REPORT.md); talep: [auth-review-integration.md](request/auth-review-integration.md).

REV-10 yalnız kısmen ilerledi (Auth→Review silme ve kullanıcı JWT tamam); Review→Admin, Admin→Review, Game→Review, koşullu Notification ve Review→User üretici bekliyor. Görev durumları değişmedi.

### Dört fazlık checklist

**FAZ 1** — [x] REV-01 · [x] REV-02 · [x] REV-03 · [x] REV-04 · [x] REV-05 · [x] REV-11

**FAZ 2** — [x] REV-06 · [ ] REV-07 ENTEGRASYON BEKLİYOR · [x] REV-09 · [x] REV-10 (Review→User teslimi DEV'de canlı doğrulandı, 12 Kasım 2026)

**FAZ 3** — [x] REV-08 · [x] REV-12 (Game entegrasyonu bekliyor)

**FAZ 4** — [ ] REV-13 KISMEN TAMAMLANDI (raporlama REV-07'yi bekliyor) · [x] REV-14 · [ ] REV-15 ENTEGRASYON BEKLİYOR

**Kalite kapıları (kullanıcının sonraki açık talimatıyla, 31 Ekim 2026):** Review modülü için pre-push izole backend hattı **PASS** (149 test; coverage %98, değişen satır %98,01; Checkstyle/PMD ratchet yeni ihlal yok; Trivy HIGH/CRITICAL kapısı PASS) ve Sonar `ltz-review-service` **Quality Gate OK** (A/A/A, coverage %95,0, duplication %0, açık issue 0). İlk Sonar koşusunda bulunan 31 bulgu (1 BLOCKER bug dahil) düzeltildi. Ayrıntı: [REVIEW_AUTH_INTEGRATION_REPORT.md](REVIEW_AUTH_INTEGRATION_REPORT.md) sonundaki "Ek". Önceki satırlardaki "KULLANICI TALEBİYLE ÇALIŞTIRILMADI" ifadeleri o fazların kayıtlarıdır; Faz 4 ve Auth entegrasyon kodu için bu kapılar artık çalıştırılmıştır. Canlı Auth uçtan uca ve tüm-repo `full` pre-push (dev PostgreSQL profili) çalıştırılmadı.

**FULL pre-push (31 Ekim 2026): FULL PRE-PUSH PASSED** — 18 backend modülü, 2274 test (0 başarısız/atlanan), kapsam %96,30, yeni Checkstyle/PMD ihlali 0, Git/Gitleaks/hijyen, Docker yapılandırması ve 18/18 HTTP health PASS; testler izole geçici PostgreSQL'de koşuldu (dev DB'ye dokunulmadı). `AbuseQuotaInterceptor` kota aşımında artık `false` döner ve tek 429 yanıtı yazar (153 Review testi PASS). Ayrıntı: [REVIEW_AUTH_INTEGRATION_REPORT.md](REVIEW_AUTH_INTEGRATION_REPORT.md) "Ek 2". Görev durumları değişmedi (REV-07, REV-10, REV-13 raporlama, REV-15 entegrasyon bekliyor).

**Review Sonar yeniden doğrulaması (31 Ekim 2026, AbuseQuotaInterceptor düzeltmesi sonrası):** izole pre-push hattı PASS (153 test, coverage %98) ve `ltz-review-service` Quality Gate **OK** (A/A/A, coverage %95,2, açık issue 0). Tam repo Sonar (`ltz-backend`) Auth'un aralıklı `MailpitEndToEndTest` testi nedeniyle tamamlanamadı; Review kaynaklı değildir. Ayrıntı: REVIEW_AUTH_INTEGRATION_REPORT.md "Ek 2".

## Entegrasyon talepleri güncellemesi (12 Kasım 2026)

[request/](request/) altındaki beş belge güncel kodla karşılaştırılıp güncellendi (yalnız dokümantasyon; hiçbir servis kodu değişmedi): [game](request/game-review-integration.md) (Game tüketicisi uygulanmış; Review frontend beklentileri; GAME-26 katılımcı uçları — Review NOT IMPLEMENTED), [auth](request/auth-review-integration.md) (güncel çift/kapsam tablosu, oturum/Gateway beklentileri), [admin](request/admin-review-integration.md) (frontend hazırlığı ve raporlama/moderasyon beklentileri), [notification](request/notification-review-integration.md) (Review rota/yük beklentileri) ve **yeni** [user](request/user-review-integration.md) (ReviewSummaryChanged üreticisi ve yazar görünümü kararı).

## Entegrasyon bağımlı görevlerin durum özeti (12 Kasım 2026, REV-07/REV-10 Review payı sonrası)

Yeniden doğrulandı: Admin ve Notification Service'te yalnız launcher vardır; Auth'ta Review→Admin, Admin→Review ve Review→Notification çiftleri tanımsızdır. Aşağıdaki "yapıldı" yalnız Review'ın kendi kodu ve testleridir (Review modülü 213/213 Maven testi PASS; canlı Auth/Admin/Notification/User kanıtı yoktur).

| Görev | Review'ın payı | Kalan / dış bağımlılık |
| --- | --- | --- |
| **REV-07** raporlama | **Review tarafı tamam:** rapor kaydı (V58), kullanıcı API'si, mükerrer engeli (409), kendi incelemesi (403), kota, hesap silme etkileşimi, frontend rapor arayüzü | Admin'e **teslim** ve Admin'den durum/karar: Admin alıcısı, Auth çifti (Review→Admin) |
| **REV-10** servis entegrasyonları | Auth→Review hesap silme, kullanıcı JWT/rol, Game→Review iç uçları (Game tüketicisi çalışıyor), Review→Game doğrulama, DB ayrımı, **Review→User `ReviewSummaryChanged` üreticisi (V59; secret boşken kapalı)** | Üreticiyi açmak için secret (ENV onayı) + backfill + canlı doğrulama; Review↔Admin, Notification, GAME-26 katılımcı uçları |
| **REV-13** spam kontrolü | **Tamam (Review payı):** inceleme 5/60 sn, beğeni 60/60 sn, **rapor 10/saat (geçici)**, mükerrer rapor engeli | Rapor kotasının nihai sayısı Admin ile onaylanmalı |
| **REV-15** moderasyon sonrası | Politika + yapısal zemin (`VISIBLE` beyaz liste, puan politikası) | `HIDDEN/ARCHIVED`, karar alıcı, rapor kapatma, bildirim: Admin kararı + sözleşme, Auth, Notification — Review'ın bağımsız yapabileceği parça yok |

**Kalite kapıları (12 Kasım 2026, REV-07/REV-10 Review payı sonrası, yalnız Review modülü):** `pre-push/ci/backend.mjs --ci --modules services/review-service` (geçici PostgreSQL 17, kök Compose ve gerçek ENV kullanılmadı) **PASS** — 213 test, 0 hata/atlanan; merkezi coverage kapısı PASS (toplam satır %97,6, değişen satır %96,2); Checkstyle/PMD yeni ihlal 0; Trivy 0 bulgu, HIGH/CRITICAL kapısı PASS. Sonar `ltz-review-service` **Quality Gate OK** (güvenlik/güvenilirlik/bakım A/A/A, coverage %94,7, duplication %0, bug/vulnerability/hotspot 0, açık issue 0). İlk Sonar koşusu kırmızıydı (1 bug: `UserSummaryOutbox` nullable `single()`; 6 kod kokusu) ve düzeltildi; ilk pre-push koşusunda test içi `ExecutorService` için PMD `CloseResource` bulundu ve düzeltildi. Notlar: `docker compose` runner'ın kısıtlı ortamında bulunamadığından (`ProgramFiles` izin listesinde yok) aynı runner CI kipinde, aynı init betiğiyle elle başlatılan geçici PostgreSQL'e karşı çalıştırıldı; `JAVA_HOME` yalnız süreç düzeyinde verildi. Tüm-repo `full` pre-push ve tüm-modül Sonar çalıştırılmadı; canlı Auth/Admin/User kanıtı yoktur.

**Auth cevabı (12 Kasım 2026):** Auth tarafında iş yok; GAME-26 kapsamları Auth'ta uygulanmış; secret dev'de otomatik üretiliyor (Review ortamına aktarım Review'ın ENV işi, onay gerekir). **Model değişikliği önerisi:** moderasyon/rapor uçlarını Review kendisi sunsun (kullanıcı JWT + `MODERATOR`/`ADMIN`); kabul edilirse REV-07 teslimi (Admin push) ve REV-15 servis çiftleri/aktör taşıması düşer. **Hamza kararı bekleniyor**; `MODERATOR` 2FA kararı da açık. Kod yazılmadı.

## Moderasyon uygulandı (12 Kasım 2026, Auth cevabının kabulü sonrası)

REV-15 ve REV-07'nin yönetim tarafı Review'da tamamlandı: V60 (`HIDDEN/ARCHIVED`, rapor kapatma, moderasyon denetim kaydı), `/api/v1/review/admin/**` uçları (rol: `MODERATOR`/`ADMIN`), yazar görünürlüğü ve `ReviewResponse.status`, frontend yazar görünümü. Review modülü **229/229** Maven testi PASS, frontend birim ve E2E (14/14, fixture) PASS. **Pre-push ve Sonar bu değişiklikten sonra yeniden çalıştırılmadı.** Açık: `MODERATOR` 2FA kararı (canlıya açmadan önce), Admin paneli ekranları (Admin sahibi), yazara bildirim (Notification), canlı Auth/Gateway doğrulaması. **V60 migration aralığının son numarasıdır.**

**Kalite kapıları (moderasyon sonrası, 12 Kasım 2026):** izole pre-push hattı (`--ci`, geçici PostgreSQL) **PASS** — 229 test, toplam kapsam %97,9 (değişen satır %97,7), Checkstyle/PMD yeni ihlal 0, Trivy 0 bulgu; Sonar `ltz-review-service` **Quality Gate OK** (A/A/A, coverage %94,8, açık issue 0; ilk koşuda 4 S1192 kod kokusu çıkmış ve düzeltilmişti). Frontend: lint, typecheck, 165 dosya / 1712 birim testi, derleme ve E2E 14/14 (fixture) PASS. Tüm-repo `full` pre-push ve canlı servis doğrulaması yapılmadı.

**User cevabı (12 Kasım 2026):** Profil sayacı (A) User tarafından onaylandı; Review'da ilk etkinleştirme **backfill'i uygulandı** (230 test; pre-push PASS: kapsam %97,7, yeni ihlal 0; Sonar Quality Gate OK, açık issue 0). Yazar görünümü (B): User seçenek 2'yi seçti ve Hamza kabul etti (uygulandı, aşağıdaki "Yazar kimliği" bölümü). Secret aktarımı ve dev dağıtımı sonradan yapıldı (aşağıdaki "DEV dağıtımı" bölümü).

## Yazar kimliği (12 Kasım 2026)

- `ReviewResponse.authorUserId` uygulandı (oluşturma/okuma/düzenleme, öne çıkanlar, `reviews/mine`); moderatör/rapor/denetim görünümlerinde yok. `docs/api.md` ve `REVIEW_SERVICE_API.md` güncellendi; testler eklendi.
- Frontend yazar satırı uygulandı: User `POST /api/v1/user/profiles/batch` (sayfa başına tek istek), kartı olmayan yazar "Bir oyuncu", profil bağlantısı `/oyuncular/{userId}`.
- Açık: canlı Gateway doğrulaması (NOT VERIFIED) ve aydınlatma metni (kart profil gizliliğine bakmaz): madde taslağı [REVIEW_KVKK_MADDE_ONERISI.md](REVIEW_KVKK_MADDE_ONERISI.md); `/yasal/kvkk` sayfası henüz yayınlanmış metin içermiyor, metin sahibi maddeyi ekleyecek.

## DEV dağıtımı ve canlı doğrulama (12 Kasım 2026)

- **Dağıtım:** `docker-compose.yml` review-service bloğuna `AUTH_REVIEW_SERVICE_CLIENT_SECRET` aktarımı eklendi (değer `.env.dev`te mevcuttu). Önce `review_db` yedeği alındı (`pg_dump -Fc`, arşiv `pg_restore -l` ile doğrulandı) ve eski imaj `ltz/review-service:dev-rollback` etiketlendi. İmaj derlendi, jar'daki migration listesi doğrulandı (yalnız Review'ın V58–V60'ı), yalnız review-service yeniden oluşturuldu (`up -d --no-deps --no-build`). Flyway V57'den V60'a 3 migration'ı uyguladı.
- **Backfill:** açılışta 1092 kullanıcı için olay kuyruğa alındı; relay hepsini hatasız teslim etti (1092/1092, yeniden deneme 0). Auth'tan servis token'ı alındı ve User `/internal/events` olayları kabul etti.
- **Sonuç doğrulaması (salt okunur):** User `profile_counters.review_count` toplamı 1344 (903 kullanıcı) ve Review'daki `VISIBLE` inceleme sayısı 1344 (903 kullanıcı) — eşleşiyor.
- **Henüz canlıda denenmeyen senaryolar:** aynı `eventId` tekrarı, eski `occurredAt`, yanlış kapsam/`aud`, hesap silme sonrası gecikmiş olay, canlı moderasyon/silme sonrası sayaç değişimi ve Gateway üzerinden yazar kartı akışı (NOT VERIFIED).
- **Geri alma:** `docker tag ltz/review-service:dev-rollback ltz/review-service:dev` ve compose `up -d --no-deps --no-build review-service`. Not: V58–V60 uygulandı; eski imaj bu şemayla da çalışır (eklemeli migration'lar) ama eski imaja dönmek yeni uçları kapatır.


## DEV veritabanı olayı ve kalıcı önlem (12 Kasım 2026)

- **Olay:** resmi `pre-push.cmd review-service dev` komutu Review testlerini DEV `review_db` üzerinde koşturdu; testlerin tablo temizliği DEV verisini sildi. Veri yedekten geri yüklendi.
- **Sıfırlama (kullanıcı talebiyle):** `review_db` ve bozuk kopya silindi, boş `review_db` yeniden kuruldu (Flyway V51-V60 uygulandı). Review boş olduğundan User sayaçları da sıfırlandı: 930 kullanıcı için `reviewCount=0` olayı outbox üzerinden teslim edildi (930/930); User `profile_counters` toplamı 0.
- **Kalıcı önlem:** `DisposableDatabaseInitializer` (test kaynağı, `META-INF/spring.factories`). Yerelde her test JVM'i geçici Testcontainers PostgreSQL'ine bağlanır, CI'da `-Pci` DB'sini kullanır. Çözümlenen bağlantı bu ikisi değilse Spring bağlamı hiç kurulmaz. Docker yoksa test başarısız olur, DEV'e geri dönülmez. `DisposableDatabaseInitializerTest` geliştirici DB URL'lerinin reddini kanıtlar.
- **Kanıt:** önlemden sonra aynı resmi komut çalıştırıldı. Testler `review-phase-test` konteynerinde koştu (233 test, kapsam %97,71). DEV `review_db` değişmedi (0 inceleme, 0 puan, outbox 930/930).
- `pre-push/pre-push.md`: `<servis> dev`, `backend` ve `full` modlarının profil DB'siyle koştuğuna dair uyarı eklendi. Diğer servis sahipleri için aynı korumanın uygulanması önerildi.

## Game cevabı sonrası (12 Kasım 2026)

- Game `batch` 429 dayanıklılığı uygulandı: oyun kartı yüklemesinde "tekrar dene" düğmesi `Retry-After` dolana kadar kapalıdır ve kalan saniyeyi gösterir (`RetryButton`, oyun başlığı ve "incelemelerim"). Kart gelmese de inceleme/puan verisi gizlenmez; otomatik yeniden deneme yoktur. `useRetryCountdown.start` kararlı kimliğe alındı (efekt bağımlılığı olarak kullanılınca geri sayımı her render'da yeniden başlatıyordu).
- `game-review-integration.md` Game cevabına göre güncellendi: canlı doğrulama NOT VERIFIED (adımlar belgede), `batch` kovası, +18 kararı (şimdilik değişiklik yok), Review'ın V51-V60 aralığının dolduğu, Game bağlantısının merge sonrası yapılacağı.
- Doğrulama: frontend lint, typecheck, 166 dosya / 1727 birim test, derleme geçti; Review E2E 14/14 (gerçek yapılandırma, iki ardışık koşu). Bir ilk koşuda "oturum yokken yönlendirme" testi soğuk başlangıçta bir kez düştü, tek başına ve tam set tekrarında geçti.
- `node pre-push/ci/hooks.mjs` (`prek` 0.5.6, `install-tool.mjs prek` ile PowerShell'den kuruldu) çalıştırıldı: `end-of-file-fixer` FAILED, diğer altı kontrol PASS. Düzelttiği 6 dosya Review'a ait değil: `backend/services/auth-service/src/main/resources/application-dev.properties` (çift son satır) ve `frontend/public/assets/team/player-{alper,hamza,kerem,nisa,umut}.svg` (son satır sonu yok). `origin/main`'de de aynı durum var; CI'daki Fast Source Checks hatası Review kaynaklı değildir. Araç çalışma ağacını değiştirdiği için bu 6 dosya HEAD haline geri alındı (sahipleri düzeltmeli). Review dosyalarında ihlal yok.
- Açık: `origin/main` birleştirmesi (kullanıcı yapar), CI'daki kırmızı işlerin sahiplerine bildirilmesi, canlı doğrulama §3.5.

## Canlı doğrulama (DEV, Gateway üzerinden, 12 Kasım 2026) — KISMEN

Oturumsuz yapılabilen adımlar gerçek yığında (Gateway + Game + Review) doğrulandı:
- Game listesi 200; `ratingsSyncedAt` Review sıfırlamasından sonraki bir taramayı gösteriyor ve tüm oyunlar `UNRATED` (Game→Review `svc+jwt` puan projeksiyonu çalışıyor; `averageRating=null`, `ratingCount=0`, 0 puan uydurulmuyor).
- Game `batch` çerezsiz 200; olmayan UUID `200` + `unavailable` (404 değil).
- Review uçları oturumsuz 401 (okuma, yazma ve `/admin/**`); `/internal/**` Gateway'den 404.
- Review→User sayaç teslimi gerçek Auth servis token'ıyla doğrulandı (yukarıdaki DEV dağıtımı bölümü).

**NOT VERIFIED (oturumlu adımlar):** merkezde kapak/başlıkların tarayıcıda dolması, puan/tavsiye/inceleme yazma (Game doğrulaması başarılı), olmayan UUID ile yazmada 404, Game durdurulunca yazmada 503 ve okuma ekranlarının açık kalması, yazar kartı (User toplu profil), `batch` 429 geri sayımı, oturumsuz yönlendirmenin canlıda dönüşü.
Neden: DEV'de e-posta gerçek Gmail SMTP'ye gidiyor (Mailpit'e düşmüyor), doğrulama kodu okunamadı; hesabı Auth veritabanında elle aktifleştirmek başka servis sahibinin kaynağı olduğu için yapılmadı. Çözüm: bilinen bir DEV hesabıyla giriş (kullanıcı) veya Mailpit'in kullanılması.
Not: bu denemede `rev-live-*@example.test` adlı bir `PENDING_VERIFICATION` hesabı Auth DEV veritabanında kaldı (ürün işlevi yok, e-posta doğrulanmadı).

## +18 yaş kapısı uyumu (12 Kasım 2026)

Game yaş kapısı ve Auth `adult_verified` iddiası `main`'e girdikten sonra Review merkezi (`/incelemeler`) buna uyduruldu. **Review backend'inde değişiklik yoktur** (yeni uç, migration veya ENV yok); `docs/api.md` REVIEW bölümüne kayıt gerekmedi.

| Dosya | Değişiklik |
| --- | --- |
| `services/review-api.ts` | `search` `includeAdult` alır; yalnız `true` iken parametre ve `credentials: "include"`. `batch` çağrıları çerezsiz kaldı |
| `types/review.ts` | `includeAdultApplied?`, `adult?`, `gameSearch.includeAdult?` |
| `schemas/review-schema.ts` | `hubQueryFrom` `includeAdult` döndürür (yalnız tek `"true"`) |
| `hooks/use-adult-verified.ts` (yeni) | Oturumdan yaş doğrulama durumu |
| `components/review-hub.tsx` | Kutu / doğrulama bağlantısı, `includeAdultApplied` ipucu, "+18" rozeti; kutu URL'ye yazılır ve sayfayı sıfırlar |
| `tr.json`, `en.json` | `Review.hub.adultToggle`, `adultVerifyHint`, `adultVerifyLink`, `adultNotApplied`, `adultBadge` |
| Testler | `review-api.test`, `review-schema.test`, `review-hub.test` (+7 senaryo), `e2e/review.spec.ts` (+2), `e2e/fixtures/review-gateway.ts` (Game kapısı taklidi) |

Sonuç: lint ve typecheck temiz, 1783 birim test geçti, Playwright `review.spec.ts` 16/16. Tam E2E'de başarısız olan altı test (navbar misafir menüsü, hardware ×3, auth-experience ×2) bu işle ilgili değildir; aynı testler Game dalında da başarısızdı veya yük altında kararsızdı.

## Girişli canlı doğrulama (yerel ltz-dev, Gateway :5080, 12 Kasım 2026)

Önceki "NOT VERIFIED (oturumlu adımlar)" maddeleri, yığın sıfırlanıp tüm servisler yeniden kurulduktan sonra gerçek Auth + Gateway + Game + Review + User ile denendi. Kullanıcı onayıyla `.env.dev`'den yalnız dev hesap parolası okundu (yazdırılmadı). Hesaplar: `user` (yazar), `creator` (raporlayan), `moderator`.

| # | Senaryo | Sonuç |
| --- | --- | --- |
| 1 | Game `batch` çerezsiz (kart) | 200, `items=1`, `unavailable=0` |
| 2 | Puan (8), tavsiye, inceleme yazma (Game doğrulaması başarılı) | 200 / 200 / 201, `status=VISIBLE`, `authorUserId` yazarın kimliği, `ownedByActor=true` |
| 3 | Aynı oyuna ikinci inceleme | 409 |
| 4 | Oyundaki incelemem, istatistik, puan özeti | 200; `ratingCount=1`, `averageRating=8` |
| 5 | Olmayan oyun UUID'sine puan | 404 |
| 6 | Geçersiz puan (11) / geçersiz oyun kimliği | 400 / 400 |
| 7 | Oturumsuz yazma / oturumsuz puan özeti okuma | 401 / 401 |
| 8 | User `profiles/batch` ile yazar kartı | 200, kart döndü |
| 9 | Kendi incelemeni raporlama / başka kullanıcı raporlar / mükerrer rapor | 403 / 201 / 409 |
| 10 | Moderatör rapor listesi, inceleme detayı (sürümle) | 200 / 200; sıradan kullanıcı admin ucuna 403 |
| 11 | Moderatör HIDE | 200; yazar `status=HIDDEN` görür; herkese açık öne çıkanlar listesinde yok |
| 12 | Raporları kapat (`REVIEWED`), RESTORE | 200 / 200 |
| 13 | User profil sayacı (yazarın inceleme sayısı) | Yazınca `AVAILABLE`, `count=1`; silince `count=0` (outbox → User çalışıyor) |
| 14 | **Game durdurulunca** puan yazma | **503** |
| 15 | Game durdurulmuşken Review okuma (puan özeti) | 200 (okuma Game'e bağlı değil) |
| 16 | Game yeniden başlatıldı | 200 |
| 17 | Temizlik: inceleme `DELETE ?version=` | 204 (sürüm 2: HIDE ve RESTORE sürümü artırır) |

Notlar:
- Oturumsuz `GET /games/{id}/rating-summary` **401** döner (Review okuma uçları oturum ister); Game `batch` ve Game listesi anonim çalışır.
- `DELETE /reviews/{id}` iyimser kilit için `?version=` ister; eksikse 400.
- Puan ve tavsiye için silme ucu yoktur: `user@ltz.com`'un denenen oyundaki puanı (8) ve tavsiyesi yerel `review_db`'de kaldı (yerel test verisi).
- Hesap durumları: seed hesapların 2FA'sı yoktur; `moderator` doğrudan girdi.

**Hâlâ NOT VERIFIED:** `batch` 429 (kota) davranışı canlıda; aynı `eventId` tekrarı, eski `occurredAt`, yanlış kapsam/`aud` ile Review→User olayı, hesap silme sonrası gecikmiş olay; +18 merkezinin tarayıcıda elle denenmesi.
