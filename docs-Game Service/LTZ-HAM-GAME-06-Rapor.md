# LTZ-HAM-GAME-06 — Genişletilmiş detay ve geliştirici geri bildirimi

Tarih: 2026-10-21 · Sorumlu: Hamza · Branch: feature/game-service

## Güncel checklist

- [x] Başlangıç: temiz feature/game-service, Faz 5 commit'i cb1e85c.
- [x] GAME-11: başarım tanımları, ADMIN yönetimi ve sayfalı public modül okuması.
- [ ] GAME-11: **KISMEN TAMAMLANDI** — GUIDES/EASTER_EGGS/EXPERIENCES gerçek servis sahipliği/API entegrasyonu bekliyor.
- [x] GAME-14: **TAMAMLANDI (Game Service yerel kapsamı)** — özel geri bildirim, yetki, durum, transaction ve audit.
- [ ] Gerçek Auth JWT entegrasyonu: **BEKLİYOR / NOT VERIFIED**.
- [x] Normal Maven clean verify ve Game Service pre-push: 230 test, 0 failure/error/skipped; %98,74 JaCoCo satır kapsamı.
- [x] SonarQube: SONAR SCAN PASSED; kalite kapısı ve overall targets PASS, tüm rating'ler A; açık issue/hotspot 0.
- [x] DEV dağıtımı: yalnız Game Service güncellendi; V172/V173 normal Flyway başlangıcıyla uygulandı. Ayrıntılar son bölümde.

## Analiz, kaynaklar ve kararlar

Hamza görev dosyası, genel gereksinim docx'i (FR-GAME-14, FR-DEV-03/04/05/06),
AGENTS/CLAUDE, SECURITY, mimari, DB/ER, klasör/web/API/migration belgeleri ve gerçek
Game Service kaynak/testleri incelendi. GAME görev numaraları FR-GAME numaralarıyla
birebir aynı değildir. LTZ_PDA_Hamza_Game_Service_Sirali.md repoda bulunamadı;
mevcut sıralı görev belgesi LTZ-Hamza-Game Service.md kullanıldı.

Başlangıçta HEAD ile yerel origin/feature/game-service farkı 0/0 idi. Fetch yapılmadı;
bu tespit uzak sunucu canlı doğrulaması değil, yerel remote-tracking referansıdır.
Merkezi eski belgelerde Game Service hâlâ iskelet diye geçebilir; gerçek kod esas alındı.
Content, Community, Review, Library ve Auth main Java kodları henüz application
iskeletidir; bu servislerin iş API'leri API kataloğunda yoktur.

Başarım tanımı Game Service metadata'sıdır; oyuncu bazlı kazanma/ilerleme yoktur.
Library koleksiyon/favori/bitirildi; Review inceleme/puan/tavsiye; Auth kimlik/rol
sahipliğini korur. Genel kullanıcı deneyimi FPS/donanım deneyimiyle karıştırılmaz.
Eski docx rehberi Content'e bağlarken güncel Nisa görevleri Content'i haber/duyuruyla
sınırlar; rehber/easter egg/genel deneyim için yeni servis sahipliği atanmadı.
Bu verileri Game Service'te yöneten paralel tablolar veya adaptörler oluşturulmadı.

Yeni public modül endpoint'i, ADMIN başarım yönetimi ve GAME-14 özel erişim/durum
kuralları kullanıcı tarafından ayrı ve açık biçimde onaylandı. Yeni ENV, harici API,
bağımlılık veya ortak Gateway değişikliği yoktur.

## GAME-11 kabul kriterleri

- [x] İçerik tipleri ACHIEVEMENTS, GUIDES, EASTER_EGGS, EXPERIENCES olarak ayrıldı.
- [x] Başarım tanımları yalnız ADMIN tarafından yönetilir; kişisel ilerleme tutulmaz.
- [x] Kanonik gameId FK'si ve active oyun denetimi korunur. Public okuma arşiv/MERGED kaydı açmaz.
- [x] Yeni tanımlar eski oyunlarda zorunlu değildir. Gerçek kayıt yoksa EMPTY; dış modül bağlı değilse INTEGRATION_PENDING.
- [x] Bekleyen modüller items=[] ve totalElements=null döndürür; başarıyla sorgulanmış boş koleksiyon değildir.
- [x] Public listede yalnız visible=true; ADMIN listesinde gizli kayıtlar da görülebilir.
- [x] page 0–10000, size 1–50 (varsayılan 20), code ASC/id ASC ile deterministik ve sınırlı okuma.
- [x] En çok 1000 tanım/oyun; unique(game_id,code), oyun satır kilidi ve optimistic version ile yazma.
- [x] Metin/code/gerekçe doğrulaması, anonim/yanlış rol, sürüm ve audit testleri.
- [x] Kart/detail/metadata sözleşmeleri değişmedi; eski Faz 1–5 testleri tekrar geçti.
- [ ] Gerçek GUIDES/EASTER_EGGS/EXPERIENCES okuması: veri sahibi ve gerçek API sözleşmesi bekler.
- [ ] Dış servis kesintisine karşı canlı adaptör testi: adaptör/servis olmadığı için uygulanabilir değil.
  Mevcut modül okuması dış ağa çıkmadığından katalog dış servis beklemez.

Public sözleşme sabit sayfa zarfı ve tip/durum ayrımı sunar; gelecekte veri sahibine
uygun DTO/adaptör eklenebilir. Şu anda items'ın somut kayıt tipi yalnız Achievement'tır;
bağlı olmayan tipler için uydurma payload veya çağrılabilir HTTP sözleşmesi yoktur.

Başarım tanımı title 1–120, description opsiyonel en çok 2000, code 1–64 küçük
harf/rakam/tire. PUT reason 8–1000; oluştururken version yok, düzenlerken mevcut
version zorunlu. Gizleme visible=false ile olur, fiziksel silme ucu yoktur.
Kaynak ADMIN; sunucu updatedAt kaydı dış sağlayıcı doğrulaması değildir.
Mevcut AdminAuditService UPDATE_GAME eylemini achievement ID'si/gerekçeyle yazar;
başlık/açıklama audit'e kopyalanmaz. İçerik başka servis veya otomatik sağlayıcıyla ezilmez.

İlk task kontrolü GameModuleTest (6 yeni) + mevcut LocalizationMigrationTest (1)
7/7 PASS: logs/phase6-game11-tests.log. Ardından GAME-14'e geçildi.

## GAME-14 kabul kriterleri

- [x] GENERAL, BUG_REPORT, EARLY_ACCESS; Review inceleme/puan/rapor verisi değildir.
- [x] Oluşturan kullanıcı kimliği mevcut doğrulanmış Actor/JWT sub bağlamından alınır.
- [x] İstemcinin userId/authorId/status/version eklemesi reddedilir; yeni kayıt OPEN.
- [x] Oyuncu yalnız kendi tekil/sayfalı kayıtlarına erişir; başkasının kimliği 404 ile gizlenir.
- [x] GAME_DEVELOPER rolü VE aktif oyun grant'i birlikte zorunlu; başkasının oyununa erişim yoktur.
- [x] ADMIN/MODERATOR rolü tek başına özel kayda erişim vermez. Bu roller yalnız kendi yazdıkları kaydı oyuncu yolundan okuyabilir.
- [x] OPEN -> IN_PROGRESS -> RESOLVED; atlama, tekrar ve yeniden açma 409 ile engellenir.
- [x] Durumu yalnız ilgili oyuna yetkili geliştirici değiştirir; oyuncuya durum yazma yolu yoktur.
- [x] EARLY_ACCESS yalnız earlyAccess=true; false/null reddedilir.
- [x] Oluşturmada active oyun gerekir. Arşiv sonrası yazar eski kaydını okuyabilir;
  geliştirici active oyun kuralına tabi kalır. Oyun fiziksel silinirse FK RESTRICT veri kaybını engeller.
- [x] Başlık 1–120, gövde 1–4000 düz metin; boş/kontrol karakteri/geçersiz tip reddedilir.
- [x] Filtre gameId (oyuncu listesi), type, status; page/size sınırları ve createdAt DESC/id ASC stabil sıralama.
- [x] Yanıt yazar UUID/profilini açmaz; Cache-Control private, no-store.
- [x] DTO debug çıktıları içerik maskeler; MVC validation ayrıntısı ve SQL bind loglarına özel içerik yazılması kapatıldı.
- [x] Mevcut GameAccessPolicy kilit sonrası grant iptalini yeniden denetler.
- [x] Aynı version ile yarışan iki işlemden yalnız biri commit olur; diğerine VERSION_CONFLICT.
- [x] Durum olayı mevcut append-only audit'e aktör, GAME_DEVELOPER, feedback ID,
  önceki/sonraki durum ve zaman ile yazılır; özel metin audit'e taşınmaz.
- [x] Audit hatasında flush edilmiş durum/sürüm dahil transaction geri alınır.
- [x] Oluşturma dış transaction'a katılır; rollback kaydı da geri alır.
- [x] Kullanıcı ve IP token bucket limitleri/Retry-After; mevcut bellek sınırlı limiter ve IP çözümleme yeniden kullanılır.
- [ ] Gerçek Auth ile uçtan uca akış: NOT VERIFIED, ayrı entegrasyon bekler.

Bildirim, dosya yükleme, yorumlaşma, yeniden açma ve geliştirici yanıt mesajı bu fazın
onaylı kapsamına dahil değildir; eksikleri geçici veri veya bypass ile kapatılmadı.

## API değişiklikleri

GAME-11:

- GET /api/v1/game/games/{gameId}/modules/{type} — Public.
- GET /api/v1/game/admin/games/{gameId}/achievements — ADMIN, gizli tanımlar dahil.
- PUT /api/v1/game/admin/games/{gameId}/achievements/{code} — ADMIN oluşturma/düzenleme/gizleme.

GAME-14:

- POST /api/v1/game/feedback — doğrulanmış kullanıcı oluşturur, 201.
- GET /api/v1/game/feedback — yalnız kendi kayıtlarını listeler.
- GET /api/v1/game/feedback/{id} — yalnız kendi kaydı.
- GET /api/v1/game/developer/games/{gameId}/feedback — rol + aktif oyun yetkisi.
- GET /api/v1/game/developer/games/{gameId}/feedback/{id} — aynı yetki + çapraz oyun denetimi.
- PATCH /api/v1/game/developer/games/{gameId}/feedback/{id}/status — aynı yetki + version/geçiş.

Dokuz yeni method/path docs/api.md GAME bölümünde DTO, rol, hata, bağlantı ve test
bilgileriyle kayıtlıdır. Eski endpoint/DTO alanları kaldırılmadı veya değiştirilmedi.
Güvenlik yapılandırması yalnız onaylı yeni public okuma ve korumalı feedback yollarını
kapsayacak biçimde genişledi; çerez/CSRF/JWT doğrulaması gevşetilmedi.

## Modeller, migration ve kaynaklar

- V172__create_game_achievement_definitions.sql: UUID PK, game FK, code/title/description,
  visible, version ve UTC zamanlar; unique ve sayfa indeksi.
- V173__create_game_developer_feedback.sql: UUID PK, game FK RESTRICT, author UUID REF,
  type/status CHECK, title/body, version/UTC zamanlar ve yazar/oyun filtre indeksleri.
- V31–V40 ve V171 değişmedi. Yeni boş tablolar yalnız eklemelidir; eski verilere
  başarım/feedback backfill'i yoktur. Sıradaki kullanılmamış numara V174.
- Dolu V171 -> V173 testinde oyun başlığı/dili, eski dil listesi ve provenance korunur;
  yeni tablolar boş kalır. FK RESTRICT gerçek PostgreSQL'de doğrulanır.

Yeni bileşenler: GameAchievementDefinition, GameModuleType, GameAchievementRepository,
AchievementRequest, AchievementEdit, GameModuleResponse, AchievementMapper, GameModuleService,
GameModuleController, AdminAchievementController; GameDeveloperFeedback,
FeedbackType/FeedbackStatus, GameFeedbackRepository, FeedbackRequest/FeedbackStatusRequest,
FeedbackResponse/FeedbackPageResponse, FeedbackMapper, GameFeedbackService,
GameFeedbackController, DeveloperFeedbackController, FeedbackPrivacyAndRateLimitFilter.

Değişen mevcut kaynaklar: GameSecurityConfiguration.java ve application.properties
(gizli içerik log koruması). POM/bağımlılık/ENV/Compose ve başka servis kaynakları değişmedi.
Merkezi docs/api.md, docs/database.md, docs/er-diagram.md; sıralı Game Service checklist'i,
Game-Service-Bekleyen-Tasklar.md ve bu rapor güncellendi.

## Kalite ve test kanıtları

Yeni testler: GameModuleTest (6), GameFeedbackTest (9), GameFeedbackTransactionTest (4),
FeedbackRateLimitTest (2), PhaseSixMigrationTest (1): toplam 22 yeni; 208 -> 230.
Game Service clean verify/pre-push son turu 230/230, 0 failure/error/skipped,
JaCoCo line %98,74: logs/phase6-game-service-final.log.
Runner sonucu BACKEND TEST CHECK PASSED; full çalıştırılmadığı için SAFE TO COMMIT denmez.
Faz 1–5 regresyonları ve gerçek PostgreSQL concurrency/Flyway testleri aynı pakettedir.
Yeni table/index sorguları sınırlı sayfalıdır; mevcut büyük katalog performans testi
bu tam pakette yeniden geçer. Yeni feedback uçları için üretim yük testi/SLO garantisi yoktur.

İlk GAME-11 test denemesi otomatik onay incelemesinin kullanım limitine takıldı;
kullanıcının devam talimatı sonrasında çalıştı. GAME-14 ilk fixture'larında JDBC/JPA
önbellek ve grant iptal sözleşmesi düzeltildi. Audit hata enjeksiyonu transaction
proxy'si yerine gerçek spy hedefinde kurulup rollback testi düzeltildi. Testler
kapatılmadı; kalite eşiği veya kapsam dışlama değiştirilmedi.
Son kontrol logunda sentetik özel başlık/gövde metni bulunmadığı ayrıca kontrol edildi.

SonarQube ilk turu (logs/phase6-sonar.log): kalite kapısı iki bakım bulgusuyla FAIL.
S107 için başarım düzenleme parametreleri AchievementEdit komutunda toplandı;
S5778 için rollback testinin exception lambda'sında aktör önceden hazırlandı.
Ardından son kodla Game Service pre-push tekrar 230 test/%98,74 PASS verdi.
SonarQube son turu logs/phase6-sonar-final.log: SONAR SCAN PASSED.
Backend Reports, Backend Analysis, Quality Gate Backend ve Overall Targets Backend PASS.
Sonar API üzerinden tüm unresolved issue ve hotspot listeleri ayrıca sorgulandı: 0/0.
Reliability/security/maintainability genel ve yeni kod rating'leri A; duplication %0.
Sonar backend coverage %90,7, new coverage %91,8; bu birleşik metrik JaCoCo line ile aynı değildir.
Game Service JaCoCo LINE 1490 covered / 19 missed = %98,74.
Sonar runner'ındaki tüm backend testleri 247/247, 0 failure/error/skipped.
Kalite eşiği düşürülmedi, bulgular bastırılmadı, test veya kod kapsamı dışlanmadı.

Geliştirme sonundaki, DEV dağıtımı öncesi Git kontrolü: feature/game-service, 7 modified ve 31 untracked dosya;
staged dosya yok. Yeni dosyalar 23 main Java bileşeni, 5 test sınıfı, 2 migration
ve bu rapordur. Eski migration dosyalarının hiçbirinde diff yok; git diff --check başarılı.

## Bekleyenler ve riskler

Gerçek Auth JWT/hesap durumu ve anahtar dağıtımı entegrasyonu NOT VERIFIED. Test
anahtarları yalnız Testcontainers/test JVM içindedir; DEV/production'a taşınmadı.
GAME-11 dış UGC tipleri sahibin API/görünürlük/moderasyon sözleşmesini bekler.
GAME-04/05 puan, GAME-15, GAME-17 gerçek detay, GAME-24 fiyat ve GAME-26 kimlik
mutasyonu bağımlılıklarına dokunulmadı.

Feedback oluşturma kullanıcı başına ani 5 + sürekli 1/dk, IP başına ani 30 + sürekli
30/dk ile sınırlıdır. Limitler süreç içidir; çoklu replica koordinasyonu değildir.
Gateway gerçek IP iletimi doğrulanana kadar Gateway trafiği IP kotasını paylaşabilir;
kullanıcı kotası ayrıca JWT sub'dan uygulanır. Yeni ENV veya Gateway değişikliği yapılmadı.
Kalıcı özel veri için üretim saklama/imha ve gerçek hesap silme koordinasyonu ayrıca
ürün/servisler arası politika gerektirir; bu faz fiziksel silme veya Auth entegrasyonu eklemez.

## DEV dağıtımından önceki plan (tarihsel kayıt)

V172/V173 yalnız geçici test PostgreSQL'lerine uygulandı. DEV game_db ve çalışan
Game Service imajı bu fazda güncellenmedi; dağıtım için ayrı açık kullanıcı onayı gerekir.
Onay sonrasında önce gerçek Flyway geçmişi/veri özetleri doğrulanmalı, yalnız Game
Service imajı yeniden derlenip normal başlangıçla migration uygulanmalı, health/API ve
veri koruması tekrar ölçülmelidir. Geçerli DEV JWT yoksa canlı rol testleri izole test
kanıtından ayrı raporlanmalıdır. Şu an DEV'de bu faz endpoint'leri varmış gibi varsayılmamalı.
Commit/push/merge/rebase/reset yapılmadı. Faz 6'nın sonunda durulur.

## Onaylı DEV dağıtımı ve son doğrulamalar — 2026-10-21

Kullanıcı Faz 6 DEV dağıtımını açıkça onayladı. Pre-push ve SonarQube'nin manuel
PASS olduğunu bildirdi; bu dağıtım çalışmasında iki kalite runner'ı tekrarlanmadı.
Kaynak kodu, ENV, Compose veya migration dosyasında değişiklik gerekmemiştir;
önceki PASS sonuçlarının kod kapsamı değişmedi. Yalnız teknik belgeler güncellendi.

### 1. Migration ön kontrolü

DEV game_db başlangıçta 11 başarılı Flyway satırı içeriyordu. Yerel SQL dosyalarının
Flyway uyumlu CRC32 checksum'ları hesaplanıp geçmişle karşılaştırıldı; hepsi eşleşti:

- V31: 544546224; V32: 1904067040; V33: -1593376719; V34: 1612787787.
- V35: -1658130016; V36: -943308524; V37: -1370159271; V38: 1212553273.
- V39: -1832370452; V40: 1034506753; V171: 450374004.

Yeni iki tablo başlangıçta yoktu. V172/V173 yalnız CREATE TABLE/INDEX yapar;
mevcut tabloyu silme, kolon değiştirme veya eski kayda UPDATE/DELETE yoktur.
games UUID PK'ye servis içi FK bağlanır; kullanıcı UUID'si REF olup başka DB'ye
FK/erişim yoktur. V172 CASCADE yalnız yeni tanımlara aittir; migration oyun silmez.
V173 RESTRICT geri bildirim bulunan oyunun fiziksel silinmesini engeller.
Unique(game_id,code), type/status CHECK'leri ve sorgu indeksleri uygundur.
Yıkıcı değişiklik/geçmiş uyumsuzluğu tespit edilmedi; sadeleştirme yapılmadı.

### 2. İmaj, konteyner ve Flyway sonucu

Yalnız aşağıdaki build/up kapsamı kullanıldı:

```text
docker compose -p ltz-dev --env-file .env.dev -f docker-compose.yml build game-service
docker compose -p ltz-dev --env-file .env.dev -f docker-compose.yml up -d --no-deps --no-build game-service
```

İmaj `ltz/game-service:dev`; yeni image ID:
`sha256:7671aaa71469cf29c8852fc9799b819d537584401a8800f510e4d2dad81f36ef`.
Konteyner `ltz-dev-game-service-1`; ID:
`b78a96507d2f8625b811742670d2fcdf375205c55cce4f2dde21ea82746be4f6`.
Durum running; başlangıç 21 Ekim 2026 22:28:32 Europe/Istanbul.
Uygulama 6,761 saniyede başladı; normal başlangıçta 13 migration doğrulandı.

- installed_rank=12, V172__create_game_achievement_definitions.sql,
  checksum=2116306688, success=true.
- installed_rank=13, V173__create_game_developer_feedback.sql,
  checksum=-1294749646, success=true.

Şema V173'te. Eski 11 geçmiş satırı aynen korundu; son durumdaki 13 SQL checksum'ı
da yerel dosyalarla eşleşir. Repair/baseline/reset veya geçmişe elle yazma yoktur.
Yeni şemada PK/FK/unique/CHECK ve altı indeks (PK/unique indeksleri dahil) PostgreSQL
kataloğundan ayrıca kontrol edildi. Hibernate validate ile uygulama başarıyla başladı.

### 3. Veri ve diğer konteynerlerin korunması

Başlangıç ve son durumda games=0, game_external_refs=0; yalnız bir provider sync
durumu satırı vardı. Eski 19 uygulama tablosunun kolon bazlı tüm satır sayıları ve
sıralı satır MD5 özetleri karşılaştırıldı: değişen tablo yok. Yeni başarım ve feedback
tabloları boş. DEV'e test oyunu, başarım veya geri bildirim eklenmedi.
Bu canlı kontrol dolu katalog korumasının kanıtı değildir; dolu V171 geçişi aşağıdaki
gerçek PostgreSQL izole testiyle ayrıca doğrulandı.

Önceden çalışan diğer 20 konteynerin ID, StartedAt ve mount bilgileri aynı kaldı.
PostgreSQL volume'ü/veritabanı korunmuştur. Testcontainers yalnız geçici test DB'leri
kullandı; başka mikroservisin kodu, konteyneri veya veritabanı değiştirilmedi.

### 4. Canlı servis ve Gateway: 56 kontrol PASS

Servis http://127.0.0.1:5084 ve Gateway http://127.0.0.1:5080 üzerinden:

- İki health isteği 200/UP.
- Eski catalog araması/status, games listesi/search/popular ve batch okuma 200;
  boş sayfalama, items/unavailable ve üst seviye alanlar korundu.
- Olmayan oyun UUID'si için eski metadata/card/detail beklenen 404 ProblemDetail.
- Eski GAME-26 yönetim girişleri anonim 401.
- Dört GAME-11 modül yolu olmayan oyun için 404 `Oyun bulunamadı.` döndürdü;
  kayıt bulunamadığı için bekleyen UGC modülü bile oyun varmış gibi sunulmadı.
- Public modül size=0, size=51, page=-1 ve geçersiz tip istekleri 400.
- ADMIN başarım GET/PUT uçları anonim 401.
- GAME-14 POST, kendi liste/tekil GET, geliştirici liste/tekil GET ve status PATCH
  yollarının tamamı servis ve Gateway üzerinden anonim 401.

Toplam: 24 eski API/health + 32 yeni API sınır kontrolü; 56/56 PASS.
Eski 24 isteğin önce/sonra durumları ve üst seviye alanları birebir aynı.
Yeni public modül yolları dağıtım öncesi 401 iken şimdi controller'ın 404/400 yanıtını
veriyor. Eski imajda henüz tanımlanmamış POST /feedback 403 idi; yeni korumalı
feedback girişinde anonim yanıt 401 oldu. İlk baseline ölçümünde bu eski davranışın
401 olacağı varsayımı düzeltilmiştir; üretim kodu veya güvenlik kuralı değiştirilmedi.

### 5. Canlıda doğrulanamayanlar ve izole test kanıtı

DEV kataloğu boş olduğundan mevcut oyun üzerinde ACHIEVEMENTS EMPTY/AVAILABLE,
görünür/gizli tanım ve GUIDES/EASTER_EGGS/EXPERIENCES INTEGRATION_PENDING payload'ı
canlıda doğrulanamadı. Bunlar GAME-11'in izole testinde başarıyla doğrulandı.

Geçerli gerçek DEV JWT olmadığı için kullanıcı sahipliği, rol/grant, ADMIN başarım
yazması, yetkili geliştirici durum geçişleri ve özel yanıt gövdeleri canlıda
doğrulanmadı. Anonim 401 tek başına korumalı controller'ın işlevini veya gerçek Auth
entegrasyonunu kanıtlamaz. Bu sonuçlar izole testlerden ayrı tutulmalıdır.
Test token üreticisi/anahtarı DEV'e taşınmadı; güvenlik gevşetilmedi.

Bu çalışma sırasında yeniden koşan **44 hedefli test: 44 PASS, 0 failure/error/skipped**:

- GameModuleTest 6: EMPTY/pending ayrımı, ADMIN, görünürlük, kota, sayfalama, eski metadata.
- GameFeedbackTest 9: JWT yazarı/IDOR, roller/grant, EARLY_ACCESS, doğru/geçersiz durum,
  arşiv, filtreler, gizli içerik/cache ve debug çıktısı.
- GameFeedbackTransactionTest 4: aynı sürüm yarışı, grant iptal yarışı,
  audit hatasında flush edilmiş durum/sürüm rollback ve oluşturma rollback.
- FeedbackRateLimitTest 2: kullanıcı/IP, Retry-After ve no-store.
- PhaseSixMigrationTest 1: dolu V171 -> V173 ve feedback FK RESTRICT.
- LocalizationMetadataTest 6, GameDiscoveryControllerTest 9,
  GameBatchControllerTest 7: seçili Faz 1–5 regresyonları.

Testler mevcut güvenli JWT altyapısı ve geçici gerçek PostgreSQL üzerinde çalıştı.
Önceki 230 test/%98,74 ve Sonar PASS geliştirme kanıtı korunur; bu dağıtımda yeniden
çalıştırılan test kapsamı 44'tür, tam pre-push/Sonar tekrar çalıştırılmış gibi gösterilmez.
Gerçek Auth entegrasyonu NOT VERIFIED; GAME-11 dış UGC sahipliği/API bağımlılığı sürer.

### 6. Git, dokümantasyon ve kanıt dosyaları

Başlangıçta kullanıcı Faz 6'nın 38 dosyasını zaten stage etmişti. Bu index'e dokunulmadı;
başlangıç/son cached diff SHA-256 aynı:
`39d6e661478b57b037fee7c0ff46fb9db36cf240a87c0bf92976ed39c7a597b7`.
Yeni dağıtım notları çalışma ağacındadır; kullanıcının commit öncesi belge güncellemelerini
ayrıca stage etmesi gerekir. Son durum: 38 staged dosya, bunların 5 belgesinde
ek unstaged değişiklik; untracked dosya yok. Kaynak/migration değişikliği yapılmadı.
Commit/push/merge/rebase/reset/add çalıştırılmadı. Eski migration'lara dokunulmadı.

Yerel kanıtlar (Git dışında): logs/phase6-dev-before.json, phase6-dev-after.json,
phase6-dev-build.log, phase6-dev-restart.log, phase6-dev-new-api-before.json,
phase6-dev-new-api-after.json, phase6-dev-legacy-api-before.json,
phase6-dev-legacy-api-after.json ve phase6-dev-isolated-tests.log.
Dağıtım ve uygulanabilir son kontroller tamamlandı; yeni geliştirmeye geçilmedi.
