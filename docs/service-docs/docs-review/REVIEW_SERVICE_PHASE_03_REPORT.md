# LTZ-HAM-REV-03 — Oyun puan ortalaması ve toplu özet API

Tarih: 31 Ekim 2026. Kullanıcı: Hamza. Branch: feature/review-service.

**FAZ 3 TAMAMLANDI — FAZ 4 İÇİN ONAY BEKLİYORUM.**

Bu kapanış REV-08 ve REV-12'nin Review Service backend kabulüdür. Game→Review servis kimliği/tüketimi henüz uygulanmadı ve NOT VERIFIED. REV-07/REV-10 ENTEGRASYON BEKLİYOR; Faz 2 kısmen tamamlanmış kalır. Faz 4 başlatılmadı.

## Ön inceleme ve kapsam

Başlangıç çalışma ağacı temizdi; branch feature/review-service. V51–V56 repository'de doğrulandı. V51 game_ratings: SMALLINT 1–10 CHECK, UNIQUE(user_id,game_id), game_id B-tree indeksi, UUID kimlikler, timestamptz ve version. Aktif/silinmiş puan statüsü yoktur; mevcut tüm puan kayıtları geçerlidir. Metin/tavsiye/beğeni ayrı tablolardadır. Puan güncellemesi UPSERT; aynı kullanıcı ikinci kayıt oluşturmaz. AccountErasureService puanı mevcut yerel transaction'ında fiziksel temizler; metin silmek puanı kaldırmaz.

Yalnız backend/services/review-service/ ve docs-review/ değiştirildi. Başka servis, frontend, Gateway, Compose, root POM, ortak kalite/CI veya ENV değişmedi. Gerçek ENV okunmadı/yüklenmedi. Commit/push/merge/branch değişikliği yapılmadı. PostgreSQL yalnız bağımsız disposable test container'ında kullanıldı; canlı/geliştirme DB'sine dokunulmadı.

## Tamamlanan görevler ve endpointler

- [x] REV-08: GET /api/v1/review/games/{gameId}/rating-summary. İlk kabul koşusu 92/92 test PASS; son toplu koşuda tekrar doğrulandı.
- [x] REV-12: POST /api/v1/review/games/rating-summaries. Review backend kabulü 102/102 test PASS. Game→Review gerçek entegrasyonunun tamamlandığı anlamına gelmez.

Controller, service, JDBC repository ve DTO'lar mevcut katman yapısında eklendi. Tekil kullanıcı puanı yazma/okuma API'si ve mevcut Faz 1/2 davranışları korundu. Sözleşme ayrıntıları [API kaydında](REVIEW_SERVICE_API.md), tasarım [kararlarda](REVIEW_SERVICE_DECISIONS.md).

## Ortalama ve boş sonuç sözleşmesi

Yalnız game_ratings.rating kullanılır: round(avg(rating),2), count(*). PostgreSQL AVG(smallint) numeric üretir; Java BigDecimal ile taşınır. Pozitif 1–10 puanlarda iki ondalık basamak HALF_UP; örneğin yedi adet 1 ve bir adet 2 için 1.125 → 1.13. IEEE floating-point kullanılmaz. JSON tüketicisinin 7.00'ı 7 olarak göstermesi aynı sayısal değerdir; biçimlendirme garantisi değildir.

Doğal tekillik nedeniyle count(*) benzersiz puan veren kullanıcı sayısıdır; metinsel inceleme sayısı değildir. Tavsiye, beğeni, Steam oranı veya metin durumu hesaba katılmaz. Rapor/moderasyon ya da Faz 4 histogram/analitik geliştirilmedi. Puan güncellemesi ve aynı puanın tekrarı sayıyı artırmaz; hesap silme puanı kaldırınca sonraki sorgunun ortalaması/sayısı değişir. Metin silinmiş olsa da bağımsız puan kalır.

Puan yoksa 200, averageRating=null ve ratingCount=0. Bilinmeyen kanonik UUID ile bilinen/puansız oyun aynı boş özeti alır. Bu yanıt Game kataloğundaki varlığı/görünürlüğü onaylamaz; katalog verisinin sahibi Game'dir. UUID ve query parametreleri SQL'e bağlanır; metin birleştirme ile oyun değeri aktarılmaz.

## Toplu API davranışı

Request `{gameIds: UUID[]}`; zorunlu/null olmayan dizi, en fazla 50 ham eleman, null olmayan UUID'ler. Sınır tekilleştirmeden önce uygulanır: aynı ID'nin 51 tekrarı da 400. Boş dizi geçerli, 200/items=[] ve sıfır SQL. Tekrarlar ilk görülme sırasıyla tekilleştirilir; her benzersiz istenen oyun için tam bir öğe, aynı sırada döner. İstenmeyen oyun dönmez. Puanı olmayan öğe açık null/0 değerleriyle tamamlanır. Minimum temel istatistik ortalama ve puan veren kullanıcı sayısıdır; Faz 4 analitiği eklenmedi.

Eksik/null/bozuk/yanlış türde dizi, null/geçersiz UUID, 51+ eleman ve bilinmeyen JSON alanı ProblemDetail/400; mevcut 64 KiB body limiti 413. Korumalı kullanıcı JWT/rol politikası 401/403 ve private,no-store korunur. Servis token'ları kabul edilmez. Kullanıcı kararı gereği anonim okuma veya yeni servis-token yetkisi açılmadı.

## Veri bütünlüğü, performans ve migration

Tekil ve toplu endpoint aynı RatingSummaryRepository.findAll toplu aggregate sorgusuna dayanır. Tekilde tek UUID; topluda en çok 50 bağlı parametreli IN/GROUP BY kullanılır. Dönen gruplar bellekte istek sırasına dizilip eksikler null/0 ile tamamlanır. Oyun başına SQL/HTTP, Game callback veya başka DB erişimi yoktur.

Salt okunur 4 saniye transaction içinde bir SELECT çalışır. PostgreSQL tek statement snapshot'ı tüm grupları ve AVG/count'u tutarlı okur; kullanıcı güncellemesi/hesap silmesi commit öncesi ya da sonrası görünür, aynı yanıtın ortalaması ve sayısı farklı sorgulardan gelmez. İstekler arası sabit tarihsel snapshot garantisi yoktur. Kalıcı sayaç/cache/materialized view veya yeni tablo üretilmedi.

Yeni migration **yok**; son sürüm V56. V51–V56 değiştirilmedi. Mevcut ix_game_ratings_game_id gerçek sorguya uygundur. Geçici DB'de ANALYZE sonrası 200 puanlı tek oyun için EXPLAIN (ANALYZE, BUFFERS) Bitmap Index Scan on ix_game_ratings_game_id → Bitmap Heap Scan → GroupAggregate gösterdi; 200 satır, 5 shared-hit buffer, execution 0.059 ms. Bu sıcak yerel tek-oyun ölçümü production SLA'sı veya tüm batch'lerin aynı planı kullanacağı garantisi değildir.

50 oyun/10.000 gerçek test puanı için API testi JdbcClient spy ile **tam bir sql(String) çağrısı**, sıfır GameCatalogPort etkileşimi doğruladı. Boş/geçersiz listelerde sıfır SQL ayrıca doğrulandı. Test, canlı ağ/production ölçek yük testi iddiası taşımaz. İstek sayısı ve kimlik sayısı sınırlıdır; genel rate-limit/kötüye kullanım geliştirmesi REV-13'tedir.

## Çalıştırılan testler ve kanıt

JDK25, offline yerel Maven cache'i ve bağımsız PostgreSQL 17 Alpine container. Secretsiz alt süreçte mevcut statik Maven settings salt okunur kullanıldı. Profil adı ci olsa da yalnız Surefire'ın sentetik DB/JWKS ortam ayarları kullanıldı; pre-push/CI giriş noktası veya backendCheck çalıştırılmadı. İki koşu da yalnız Review modülünün Maven test aşamasıdır:

```text
mvn -o -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml -f backend/pom.xml -pl services/review-service -Pci -Dltz.test.postgres.port=<geçici port> test
REV-08: Tests run: 92, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
REV-12: Tests run: 102, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Yeni kanıtlar (başlangıç 85 → son 102; 17 yeni test):

- RatingSummaryApiTest: 6 PostgreSQL/MockMvc testi — boş sonuç/dış çağrı yok; HALF_UP ve puan dışı kayıt ayrımı; gerçek puan UPSERT/tekrar/güncelleme; hesap silme; aynı kullanıcı eşzamanlı güncelleme; anonim/rol/UUID hata davranışı.
- RatingSummariesApiTest: 7 PostgreSQL/MockMvc testi — sıra/tekrar/puansız öğe; tekil-toplu eşdeğerlik; boş liste/sıfır SQL; validation/sıfır SQL; 50 oyun/10.000 puan/tek SQL; eşzamanlı okuma/yazmada matematiksel AVG/count tutarlılığı; anonim ve gövde sınırı.
- RatingSummaryServiceTest: 2 birim testi — ilk görülme sırası/tekilleştirme/değiştirilemez yanıt ve decimal verinin korunması.
- ReviewHttpIntegrationTest: 2 yeni gerçek TCP/RS256/JWKS testi — tekil ve toplu kullanıcı erişimi, servis token'ının 401 alması, topluda hatalı gövde 400. Bu sınıf toplam 8 test oldu. Yerel JWKS/Game sözleşme sunucuları kullanılır; canlı Auth/Game/Gateway E2E değildir.
- Önceki 85 test son koşuda yeniden PASS; rating/review/recommendation/like/silme/hesap guard'ı/güvenlik regresyonları korundu.

Maven derleme/test PASS; test lifecycle içindeki validate/Checkstyle sıfır ihlal bildirdi. İlk koşuda boş PostgreSQL'e V51–V56 uygulandı; sonraki koşuda 6 migration checksum doğrulaması PASS, şema v56 güncel. Hibernate ddl-auto=validate ile uygulama bağlamları açıldı. flyway_schema_history'de V51–V56 success=true ayrıca doğrulandı. Test container'ı çalışma sonunda kaldırıldı.

## Çalıştırılmayan kalite kapıları

- Pre-push hook/CI pipeline: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**.
- backendCheck: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**.
- Trivy/güncel bağımlılık güvenlik taraması: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**.
- Sonar Scanner/analiz/Quality Gate/A/A/A: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**.
- Tüm repository kalite süreci çalıştırılmadı. verify aşaması ortak coverage aracını çağırdığı için kullanılmadı; PMD/strict verify/merkezi coverage/ratchet için bu faza ait yeni PASS sonucu yoktur. Önceki fazların güvenlik/coverage/Sonar sonuçları yeni kodun taze doğrulaması değildir.

## Dış bağımlılıklar ve açık riskler

[Game talebi](request/game-review-integration.md) 13 başlıkla oluşturuldu; [Auth talebi](request/auth-review-integration.md) Game→Review dar okuma kimliği ihtiyacıyla genişletildi. Scope ve servis okuma yolu yalnız öneri; yeni yetki, token, secret veya ENV oluşturulmadı. Kullanıcı JWT'si Game servis kimliği yerine kullanılmayacak.

Game'in anonim katalog sayfaları mevcut kullanıcı-korumalı Review API'sini sunucu kimliğiyle henüz tüketemez. Game→Review gerçek çağrısı, token edinimi, yetki ve cache/timeout/hata politikası sözleşme bekler. Global minRating filtresi/RATING sıralaması için önce sayfalayıp yalnız sayfayı zenginleştirmek yeterli değildir; bütün aday küme ve toplam doğruluğu ayrıca tasarlanmalıdır. Bu faz bu özellikleri Game'de uygulamadı.

REV-07 ve REV-10 ENTEGRASYON BEKLİYOR kalır. Mevcut Admin/Notification talepleri geçerlidir; rapor/outbox/relay/moderasyon/bildirim kodu eklenmedi. Game sunucu entegrasyonu ayrıca NOT IMPLEMENTED/NOT VERIFIED; Review tarafı REV-12 kabulünden ayrıdır.

## Değişen dosyalar ve kapanış

Yeni backend: RatingSummaryController, RatingSummaryService, RatingSummaryRepository; GameRatingSummaryResponse, RatingSummariesRequest, RatingSummariesResponse; RatingSummaryApiTest, RatingSummariesApiTest, RatingSummaryServiceTest. ReviewHttpIntegrationTest'e iki test eklendi. POM/security/ENV/migration değişikliği yoktur.

Belgeler: API, karar, ilerleme, bu Faz 3 raporu, yeni game-review-integration.md ve genişletilen auth-review-integration.md. Faz 1/2 uygulamaları korunur. REV-08/REV-12 [x]; Faz 4 görevleri [ ] ve onay bekler. Commit/push/merge veya branch değişikliği yapılmadı.
