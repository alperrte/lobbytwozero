# LTZ-HAM-REV-02 — Faz 2 çalışma raporu

Durum: REV-06 ve REV-09 tamamlandı; servis bazlı talepler hazır. Kullanıcının son açık talimatıyla REV-07/REV-10 beklerken yalnız REV-09 geliştirildi. **Faz 2 kısmen tamamlandı**. REV-07/REV-10 ENTEGRASYON BEKLİYOR; Faz 3/4'e geçilmedi. Kapsam yalnız `backend/services/review-service/` ve `docs-review/`; branch `feature/review-service`.

## Doğrulanan görevler

- REV-06: PUT/GET/DELETE `/api/v1/review/reviews/{reviewId}/like`; V55 ile doğal tekillik, gerçek FK, idempotency ve silme tutarlılığı. **75 test PASS** (başlangıç 67 + 7 beğeni kabul testi + 1 gerçek JWT/HTTP beğeni akışı).
- REV-09: GET `/api/v1/review/games/{gameId}/reviews/featured`; beğeni/tarih/UUID sıralaması, oyun/minLikes filtresi, sınırlı sayfalama ve V56. Güncel Review koşusu **85 test PASS** (önceki 75 + 10 yeni test). Kabul ve sınırlamalar aşağıdadır.

## Açık görevler ve dış bağımlılıklar

- REV-07: **ENTEGRASYON BEKLİYOR.** Rapor endpoint'i/tablosu, outbox, relay veya geçici teslim kodu yazılmadı. Admin alıcı API'si ve Review→Admin yetkisi bulunmuyor. Rapor teslimi NOT IMPLEMENTED/NOT VERIFIED; görev kapatılmadı.
- REV-10: **ENTEGRASYON BEKLİYOR.** İhtiyaç analizi/talep belgeleri hazır; dış servis entegrasyonu kodlanmadı. Başka servis DB'sine erişim yok.

## REV-06 test kanıtı

PostgreSQL 17 disposable test projesi ve `-Pci -Dltz.test.postgres.port=<geçici port>` ile yalnız Review modülü doğrulandı. İlk beğeni koşusu 74; gerçek JWT/HTTP kabulü eklenen son koşu **75 test, 0 failure/error/skipped, BUILD SUCCESS**.

Yeni kabul kanıtları: tekrar PUT/DELETE, actor izolasyonu, anonim/self-vote/body reddi, eşzamanlı aynı/farklı kullanıcı, soft delete temizliği, silinmiş hedef, hesap silme/tekrar engeli, karşılıklı oy veren iki hesabın eşzamanlı silinmesi ve beğeni/silme yarışı. Gerçek TCP/RS256/JWKS testinde self-vote 403, servis token'ı 401, PUT/DELETE tekrarları ve silinmiş hedef 404 doğrulandı. Test karşı tarafları yerel Auth JWKS/Game sözleşme sunucularıdır; canlı karşı servis E2E iddiası yoktur.

Kendi incelemesini beğenmeme/raporlayamama tercihi kullanıcı tarafından açıkça onaylandı. Beğeni yasağı uygulanıp test edildi. Raporlama kodu bu çalışmada yazılmadığından kendi incelemesini raporlama yasağı yalnız gelecekteki REV-07 kabul beklentisidir; bugün test edilmiş gösterilmez.

## PostgreSQL, veri bütünlüğü ve API

- Yalnız yeni `V55__create_review_likes.sql` eklendi; V51–V54 değiştirilmedi. `(review_id,user_id)` PK, review FK ON DELETE CASCADE, version CHECK ve user_id indeksi var.
- Sayaç ayrı artırılan sütun değildir. Doğal tekillik, hesap guard'ı ve parent review kilidi altında gerçek satırlardan count/aktör durumu döner. GET REPEATABLE_READ snapshot kullanır.
- Metin silme aynı transaction'da beğenileri temizler. Hesap silme verilen oyları ve yazarın incelemelerinin oylarını temizler. Karşılıklı beğenilerde iki hesap silmesinin FK lock çevrimini önlemek için yalnız erasure işlemleri kısa bir transaction advisory lock ile sıralanır.
- Beğeni metni, puanı, tavsiyeyi veya inceleme version'ını değiştirmez. Başka aktörün oyuna body/query ile müdahale edilemez; dolu mutation gövdesi 400. DELETE gövdesi de mevcut 64 KiB filtresine alındı; aşım 413 test edildi.
- [Yerel API kaydı](REVIEW_SERVICE_API.md) üç endpoint'in auth, DTO, validation, hata ve test sözleşmesini içerir. Kullanıcı kapsamı gereği ortak docs/api.md değiştirilmedi.

## REV-06 önceki kalite ve güvenlik kanıtı — REV-09 için yeniden çalıştırılmadı

Mevcut secretsiz `backendCheck` yalnız `services/review-service` için çalıştırıldı; önceki doğrulanan geçici Trivy cache'i yalnız çalışma zamanı `--cache-dir` ile kullanıldı. Windows eklenti dizinleri/JDK25/yerel Maven cache/strict profil adaptörleri yalnız alt süreçte verildi; ortak runner veya politika değiştirilmedi. Ayrıntılı aynı çalışma yöntemi [Faz 1 kalite raporunda](REVIEW_SERVICE_PHASE_01_REPORT.md) bulunur.

- Son taze build: **BACKEND TEST CHECK PASSED**.
- Testler: 75/75; strict Maven clean verify PASS; Checkstyle/PMD sıfır ihlal; ratchet sıfır yeni ihlal.
- Merkezi coverage: **407/418 satır, %97,37**; değişen satırlar **312/320, %97,50**; baseline/changed gate PASS. Bu oranlar merkezi branch karşılaştırmasının sonucudur, yalnız REV-06 satırlarının oranı diye sunulmaz.
- Trivy: paketlenmiş runtime JAR ve tam nested JAR envanteri, **111 paket; HIGH 0, CRITICAL 0; PASS**. Skip/ignore/insecure veya kalite eşiği değişikliği yok. Bağımlılık/POM/ENV değiştirilmedi.
- Sonar: **Quality Gate OK**, Security/Reliability/Maintainability **A/A/A**, tüm kod coverage **%93,8**, yeni kod **%94**, duplication **%0**, yeni issue **0**, bug/vulnerability/hotspot **0/0/0**. İlk fazdan kalan 13 bakım bulgusu sürüyor; yeni issue eklenmedi. Mevcut `.env.dev` güvenli loader'ı kullanıldı; token/dosya değiştirilmedi ve değer loglanmadı.
- Gerçek Sonar task **19b597a4-6eab-498f-bc43-4724c6d575ba**, status SUCCESS; analysis ID **ff6c4f1f-5c27-4d9f-b740-5bbffdb2c66b**. Gate API yeni coverage≥80, yeni duplication≤3 ve yeni issue=0 koşullarını gerçek değerlerle OK döndürdü; ignoredConditions=false. Ortak `overallTargets` helper'ı true döndü. Proje: [LTZ Review Service](http://localhost:5199/dashboard?id=ltz-review-service).
- SCM/blame uyarısı commitlenmemiş dosyalar nedeniyle sürer; staging/commit veya SCM devre dışı bırakma yapılmadı. Bu uyarı gerçek gate sonucu yerine PASS sayılmadı; gate ayrıca doğrulandı.

## Servis bazlı talep dosyaları ve beklenen kararlar

Her dosya kullanıcının istediği 13 bilgi başlığını içerir; kesinleşmemiş endpoint/DTO/olay/izinler açıkça öneridir. Belgeler hedef servis sahibine kesin geliştirme emri değildir. Nihai API/yetki/protokol sahibi ilgili ekip; Review karşılıklı onaylı sözleşmeye uyarlanacak.

- [auth-review-integration.md](request/auth-review-integration.md) — **Auth/Kerem**: Review→Admin ve Admin→Review hedef/kapsam çiftleri, olay/senkron protokol, insan moderatör güven modeli; Notification üreticisi Review seçilirse koşullu izin. Mevcut Review→User izni ve D4 ayrımı korunur.
- [admin-review-integration.md](request/admin-review-integration.md) — **Admin/ortak sorumlu atanmalı**: kalıcı rapor kabulü, event/report/action tekilliği, vaka durumları, yetkili karar, Review uygulama ACK'i, retention ve hata/yeniden teslim modeli.
- [notification-review-integration.md](request/notification-review-integration.md) — **Notification/ortak sorumlu atanmalı**: SECURITY.md §5 gereği uygulanmış moderasyonun sahibine bildirimi. Tek üretici (Admin veya Review), alıcı/tercih, veri minimizasyonu, dedup ve kalıcı ACK kararı. REV-06 için beğeni bildirimi veya raporlayana sonuç bildirimi uydurulmadı.

Mevcut `docs-library/` bu checkout'ta bulunamadı. Repository'deki Auth'un Library talep yanıtı ve User'ın Library/Review talep belgeleri salt okunur incelendi; ihtiyaç–mevcut durum–karar–kabul ayrımı korundu ve kullanıcının 13 alanlı formatı uygulandı.

## Yeni talep açılmayan doğrulanmış sözleşmeler

- Game: mevcut anonim toplu kart API'si kanonik gameId doğrulamasını karşılıyor; yeni Game özelliği talebi yok.
- User: `ProjectionEventHandler.reviewSummaryChanged` ve EventType içinde önerilen T7 ReviewSummaryChanged alıcısı var; Auth'ta Review→User events:ingest zaten tanımlı. Eksik Review üreticisi bu servisin ilerideki kendi işidir. Karşılıklı T7 mutabakatı/Review üretimi henüz kapanmadı; var olan alıcı için gereksiz yeni User API talebi açılmadı. İlgili mevcut talep: `docs-user/integration/services/library-review-talepleri/library-review-requests-01.md`.

## Önceki REV-06 çalışmasının dosyaları

Review backend: ReviewLikeController, ReviewLikeResponse, ReviewLikeService, ReviewLikeRepository, V55 migration, ReviewLikesApiTest eklendi. ReviewWriter/AccountErasureService silme temizliği, ReviewRequestFilter DELETE sınırı ve ReviewHttpIntegrationTest gerçek JWT/HTTP beğeni akışı güncellendi. Belgeler yalnız docs-review içinde: API, karar, ilerleme, bu rapor ve üç request dosyası.

## REV-09 — Kabul ve geliştirme doğrulaması (31 Ekim 2026)

- Yeni endpoint, DTO'lar, FeaturedReviewService ve FeaturedReviewRepository eklendi. Mevcut tekil inceleme ve beğeni API'leri değiştirilmedi. API alanları/validation/hatalar [yerel API kaydında](REVIEW_SERVICE_API.md), kararlar [karar belgesinde](REVIEW_SERVICE_DECISIONS.md).
- Beğeni sayısı DESC → createdAt DESC → id ASC. Güncelleme tarihi sıralamaya katılmaz. Oyun UUID'si ve minLikes filtresi yalnız görünür kümeye uygulanır. Sıfır beğenili incelemeler varsayılan listede bulunur. DELETED kayıtları, kalıntı beğeni satırları olsa da dışarıda kalır. Aynı oyların tekilliği V55 ile korunur; yeni sayaç sütunu yoktur.
- page=0..10000 (varsayılan 0), size=1..50 (varsayılan 20), minLikes=0..2147483647 (varsayılan 0). Toplam, filtrelenmiş kümenindir. Boş oyun/son sayfa sonrası 200 ve boş items; toplamı sıfır olan kümede totalPages=0.
- İki parametreli set sorgusu aynı salt okunur REPEATABLE_READ/4 saniye transaction'ında çalışır; N+1 veya dış servis çağrısı yoktur. Yazar/oy veren UUID'leri yanıtlanmaz; ownedByActor mevcut sözleşmeyi korur. Mevcut JWT/rol ve private,no-store politikası kullanılır.
- V56__index_featured_reviews.sql eklendi: görünür oyun kayıtları için game_id/created_at DESC/id ASC kısmi B-tree indeksi. V51–V55 değiştirilmedi. Beğeni JOIN'i mevcut review_id önekli V55 PK'sini kullanabilir. Beğeni skoru toplulaştırılır ve son sıralama ayrıca yapılır; indeksin tüm sıralamayı ortadan kaldırdığı iddia edilmez.

### Çalıştırılan doğrulamalar

Ortak pipeline çağrılmadan doğrudan Maven **test** aşaması yalnız `services/review-service` için çalıştırıldı. JDK25, yerel Maven cache'i, secretsiz alt süreç ve bağımsız geçici PostgreSQL 17 Alpine container kullanıldı. Gerçek ENV dosyaları yüklenmedi/okunmadı. Maven'in mevcut statik secretsiz settings dosyası salt okunur kullanıldı; pre-push giriş noktası/CI script'i veya backendCheck çağrılmadı. Geçici DB yalnız bu modül içindi; başka servis testleri başlatılmadı.

```text
mvn -o -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml -f backend/pom.xml -pl services/review-service -Pci -Dltz.test.postgres.port=<geçici port> test
Tests run: 85, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- Maven üretim/test derlemesi PASS. Maven test yaşam döngüsündeki mevcut validate/Checkstyle adımı sıfır ihlal bildirdi. PMD/strict verify/merkezi coverage kapısı bu koşuda çalıştırılmadı; güncel PASS iddiası yoktur.
- Flyway V51–V56 boş PostgreSQL üzerinde uygulandı; logda 6 başarılı migration ve v56 görüldü. Hibernate şema validate ile Spring bağlamları açıldı. Flyway history'de altı success=true ve pg_indexes'te V56 kısmi indeks tanımı ayrıca salt okunur doğrulandı. Canlı/geliştirme DB'sine migration uygulanmadı.
- 7 yeni PostgreSQL/MockMvc testi: beğeni önceliği/tarih/UUID eşitliği ve tekrarlanabilir sonuç; oyun/görünürlük/minLikes ve sıfır beğeni; kararlı sayfalar/toplamlar/son sayfa sonrası; boş oyun/üst sınırlar/dış çağrı yapılmaması; geçersiz UUID/tamsayı/aralık ProblemDetail ve anonim erişim; gerçek beğeni/kaldırma/silme sonrası sıralama; toplam ile sayfa arasına başka connection'dan commit edilen eklemenin tek snapshot tutarlılığını bozmaması.
- 2 yeni servis birim testi: kısmi son sayfa hesabı/filtre/offset aktarımı ve tam sayfa katında fazladan sayfa oluşmaması.
- 1 yeni gerçek TCP/RS256/JWKS testi: kullanıcı token'ıyla liste/önbellek başlığı, servis token'ına 401, geçersiz sayfalama için 400. Toplam HTTP sınıfı 6 testtir. Yerel Auth JWKS/Game sözleşme sunucuları kullanılır; canlı karşı servis E2E değildir.
- Önceki 75 test de aynı koşuda yeniden geçti; puan/metin/tavsiye/beğeni/sahiplik/hesap silme ve eşzamanlılık regresyonları doğrulandı. Yeni toplam **85/85**.

### Bu görevde çalıştırılmayan kontroller

- Pre-push hook/CI ve backendCheck: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**.
- Trivy / güncel bağımlılık güvenlik taraması: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**. REV-06'nın önceki HIGH/CRITICAL 0/0 sonucu bu yeni kod için taze tarama sayılmaz.
- SonarQube Scanner/analiz/Quality Gate/A/A/A: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**. Yukarıdaki Sonar değerleri yalnız önceki REV-06 çalışmasına aittir.
- Tüm repository kalite pipeline'ı çalıştırılmadı. Güncel coverage/ratchet/PMD sonucu üretilmedi; başarısız veya çalıştırılmamış kontrol PASS gösterilmedi.

### Sınırlamalar ve sonraki sözleşmeler

Mevcut şema yalnız VISIBLE/DELETED ve beğeni verisi sunar. Şikâyet, rapor durumu ve moderasyon kararı henüz yoktur; eksik sinyaller sıfırmış gibi alan/skor üretilmedi. Görünürlük ve sinyal toplama sorgu sınırı gelecekte karşılıklı onaylı sözleşmeyle genişletilebilir. REV-07/REV-15 veya kötüye kullanım önleme uygulanmış sayılmaz. Mevcut veriyle beğeni/popülerlik sıralaması sunulur; kötü niyetli oy tespiti REV-13'te kalır.

Snapshot yalnız tek istek içindir; ayrı sayfa istekleri arasında oy/ekleme/silme değişirse sayfalar kayabilir. Sabit geçmiş snapshot/cursor garantisi yoktur. Yerel boş sonuç Game kataloğunda varlık/erişilebilirlik kanıtı değildir. Başka servisten yeni özellik gerekmemiştir; mevcut Auth/Admin/Notification talep dosyaları değişmeden bekler.

REV-09 kapsamında eklenen dosyalar: FeaturedReviewController, FeaturedReviewResponse, FeaturedReviewsResponse, FeaturedReviewService, FeaturedReviewRepository, V56__index_featured_reviews.sql, FeaturedReviewsApiTest ve FeaturedReviewServiceTest. ReviewHttpIntegrationTest'e bir yeni test eklendi. Belgeler yalnız docs-review içinde güncellendi. Önceki REV-06 değişiklikleri korundu.

## Güncel durma noktası

REV-06 ve REV-09 checklist [x]. REV-09'un kullanıcı tarafından istenen kapsam içi kabul ve testleri doğrulandı; yasaklanan kalite kapıları çalıştırılmadı. REV-07/REV-10 ENTEGRASYON BEKLİYOR ve [ ] kalır. Rapor/outbox/relay/teslim veya moderasyon/bildirim kodu yoktur. Faz 2 kısmen tamamlandı. Başka servis, frontend, Gateway, Compose, ortak Maven/CI/kalite veya ENV değişikliği yapılmadı. Commit/push/merge veya branch değişikliği yapılmadı. Faz 3/4 geliştirilmedi; Faz 3 için açık kullanıcı onayı beklenir.
