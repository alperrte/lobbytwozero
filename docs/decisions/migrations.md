
# LTZ — Flyway Migration Numaralandırma Planı

## ALPER
- V1–V10       → HARDWARE-SERVICE
- V191        → HARDWARE-SERVICE (2026-11-07 açık kullanıcı onayı; GPU kaynak kategorisi için kullanıldı)
- V192        → HARDWARE-SERVICE (2026-11-08 açık kullanıcı onayı; yarım yıldız değerlendirme puanı için kullanıldı)
- V193        → HARDWARE-SERVICE (2026-11-08 açık kullanıcı onayı; bilgisayar profili kasa rengi için kullanıldı)
- V11–V20      → RECOMMENDATION-SERVICE
- V21–V30      → PERFORMANCE-SERVICE

## HAMZA
- V31–V40      → GAME-SERVICE (aktif başlangıç zinciri, 10 migration; tamamı kullanıldı)
- V171–V180    → GAME-SERVICE (2026-10-24 açık kullanıcı onayı; V171–V173 kullanıldı, V174–V180 boş)
- V41–V50      → LIBRARY-SERVICE
- V51–V60      → REVIEW-SERVICE

## KEREM
- V61–V70      → AUTH-SERVICE
- V71–V80      → USER-SERVICE
- V81–V90      → MAIL-SERVICE

## NISA
- V91–V100     → COMMUNITY-SERVICE
- V181–V190   → COMMUNITY-SERVICE (2026-11-07 açık kullanıcı onayı; V181 kullanıldı, V182–V190 boş)
- V101–V110    → CONTENT-SERVICE
- V111–V120    → EVENT-SERVICE

## UMUT
- V121–V130    → SOCIAL-SERVICE
- V131–V140    → MESSAGING-SERVICE
- V141–V150    → LFP-SERVICE

## ORTAK SERVİSLER
- V151–V160    → NOTIFICATION-SERVICE
- V161–V170    → ADMIN-SERVICE

Bu iki ortak servisin migration sorumlusu,
geliştirmeye başlanmadan önce belirlenmelidir.

---

## Migration Kuralları

### Hardware Service V193 — Bilgisayar profili kasa rengi, 2026-11-08

- LTZ Atölyesi (Bilgisayarlarım) kasa rengini profile kaydetmek için kullanıcı açıkça V193'ün alınmasını onayladı
  ("v193 ü al konteynırı yenile ve migrations.md'yi güncelle"). Yalnız V193 Hardware Service'e tahsis edildi;
  V194 ve sonrası için tahsis verilmedi.
- `V193__add_hardware_profile_case_color.sql`, `hardware_profiles` tablosuna `case_color varchar(16) NOT NULL DEFAULT 'BLACK'`
  kolonunu ve `ck_hardware_profiles_case_color` CHECK'ini (`BLACK`, `WHITE`, `GRAPHITE`, `RED`) ekler. Mevcut profiller
  `BLACK` alır; V1–V10, V191 ve V192 değiştirilmez.
- Bu karar diğer servislerin tahsislerini değiştirmez; numaralandırma sahibi ekip bilgilendirilmelidir.

### Hardware Service V192 — Yarım yıldız değerlendirme puanı, 2026-11-08

- V1–V10 dolu olduğundan kullanıcı açıkça V192'nin alınmasını onayladı ("Evet, V192'yi al").
  Yalnız V192 Hardware Service'e tahsis edildi; V193 o tarihte tahsisli değildi (aynı gün ayrıca onaylandı, yukarıya bakın).
- `V192__allow_half_star_review_ratings.sql`, `hardware_reviews.rating` kolonunu `integer`'dan `numeric(2,1)`'e çevirir ve
  CHECK'i "1–5 arası, 0,5 adımlı" olarak yeniden tanımlar. Mevcut tam sayı puanlar kayıpsız dönüşür; V1–V10 değiştirilmez.
- Bu karar diğer servislerin tahsislerini değiştirmez; numaralandırma sahibi ekip bilgilendirilmelidir.

### Hardware Service V191 — GPU kaynak kategorisi, 2026-11-07

- V1–V10 dolu olduğundan kullanıcı açıkça “191'i al migrations.md'yi güncelle” dedi.
  Yalnız V191 Hardware Service'e tahsis edildi (V192 daha sonra, 2026-11-08'de ayrıca onaylandı).
- `V191__allow_gpu_catalog_sources.sql`, kaynak tablosunun kategori CHECK'ine GPU ekler.
  V1–V10 değiştirilmez; mevcut ürün UUID'leri ve tüm referanslar korunur.
- Bu karar Game V171–V180 ve Community V181–V190 tahsislerini değiştirmez.
- [GPU aktarım kanıtı](../../docs-hardware/reports/pc-part-gpu-import.md).

### Hardware Service V10 — PC Part Dataset, 2026-11-07

- Gerçek aktif klasör ve yerel hardware_db history V1–V9 olarak doğrulandı.
  Son boş V10, `V10__add_pc_part_dataset_import.sql` için kullanıldı.
- V1–V9 değişmedi. V10 kaynak provider CHECK'ini genişletir, kaynak commit'ini
  ve teknik import sonuç tablosunu ekler. Ürün/yorum/profil verisi silinmez.
- V1–V10 artık doludur. Sonraki şema değişikliği için ekipçe ek aralık gerekir;
  başka servisin numarası kullanılmaz. Eski V10 forum rezervasyonu bu açık
  kullanıcı veri aktarımı talimatıyla geçersizdir.
- [Karar ve komutlar](../../docs-hardware/decisions/pc-part-dataset-import.md).

### Community Service ek tahsisi — 2026-11-07

- Community Service'in V91–V100 aralığındaki 10 numaranın tamamı kullanılmıştır.
  Kullanıcının açık onayıyla **V181–V190 Community Service'e tahsis edildi**.
  İlk boş numara `V181__add_game_content_types.sql` için kullanıldı; V182–V190 boştur.
- V181, GAME-11 kapsamında `posts` tablosuna `content_type` ve `title` ekler.
  Eski gönderilere geriye dönük tür atanmaz; uygulanmış V91–V100 dosyaları değiştirilmez.
- Bu tahsis Game Service'in V171–V180 ek aralığını veya V1–V170 içindeki diğer
  servis tahsislerini değiştirmez. Aşağıdaki 2026-10-24 ve 2026-10-21 tarihli
  “V181–V190 tahsis edilmemiştir” ifadeleri kendi tarihlerindeki durumu anlatır;
  **güncel tahsis bu bölümdür**.
- V181'in izole PostgreSQL testinde V100 sonrası yükseltme ve eski gönderilerin
  korunması doğrulandı. Bu onay gerçek DEV/production veritabanına migration
  uygulama, Flyway `repair`/`baseline` veya veri sıfırlama izni değildir.

### Güncel Game Service ek tahsisi ve hesap silme hazırlığı — 2026-10-24

- **V173 kullanıldı (2026-11-05, GAME-24 / GAME-04 indirim filtresi):**
  `V173__create_game_price_cache.sql`, ITAD fiyat snapshot'larını, pazar bazlı
  yenileme durumunu ve tek worker kiralamasını ekler. Kullanıcının ITAD seçimi ve
  şablon/Compose için `ITAD_API_KEY` onayı bu oturumda alındı. Aktif klasör V31–V40,
  V171, V172 kontrol edilerek ilk boş V173 seçildi. Mevcut migration değiştirilmedi;
  gerçek DEV'e migration/deploy uygulanmadı. Kalan numaralar V174–V180'dir.
  Tarihsel fixture'daki V173 özel feedback'tir ve aktif V173 fiyat migration'ı ile
  karıştırılmamalıdır. Tarihsel eşdeğerlik testleri V40'ta, V172 puan yükseltmesi
  target=172'de kalır; fiyat tabloları yeni ITAD/PostgreSQL testlerinde doğrulanır.

- Kullanıcının açık talimatıyla **V171–V180 Game Service'e yeniden tahsis edildi**.
  Aşağıdaki 2026-10-21 tarihli ek aralık iptali kayıtları tarihseldir; güncel tahsis bu bölümdür.
  O tarihte V181–V190 tahsis edilmemişti; 2026-11-07 Community tahsisi için yukarıya bakın.
  V1–V170 içindeki diğer servis aralıkları değişmez.
- Tahsis öncesi aktif klasör kontrol edildi: yalnız V31–V40 vardı. İlk boş numara
  V171 seçildi: `V171__enable_controlled_account_erasure.sql`. Aktif zincir artık
  V31–V40 + V171'dir. Tarihsel fixture V171–V173 bu zincire dahil değildir.
  Eski 13 dosyalı DB geçmişiyle numara çakışabileceğinden bu tahsis, eski ortamda
  otomatik repair/baseline/yeniden numaralandırma izni anlamına gelmez.
- Hesap silme incelemesinde V34 `game_identity_audit_immutable` ve V35
  `game_admin_audit_immutable` trigger'larının hem UPDATE hem DELETE'i engellediği doğrulandı.
  Bu nedenle aktörleri nil UUID'ye çevirme önerisi mevcut şemada migration olmadan uygulanamaz.
- İlk aşamada politika onaysız olduğu için yalnız güvenlik ve bekleme davranışı geliştirildi.
  **Sonraki açık kullanıcı bildirimiyle Kerem'le ortak kararın alındığı teyit edildi:**
  hesaba ait içerik/medya bağlantısı/not/feedback/grant silinir; audit ve alan kilidi
  kayıtları korunur, ilgili aktörler nil UUID olur; ilgili nullable metinler NULL,
  NOT NULL evidence `[silinen hesap: metin kaldırıldı]` olur. Regex ile metin temizlenmez.
- V171 migration'ı veri silmez/anonimleştirmez; yalnız özel transaction bağlam tablosu,
  dar audit istisnası ve `erase_game_account_data(uuid)` fonksiyonunu tanımlar.
  Veri temizliği yalnız yetkili iç endpoint çağrıldığında tek transaction'da yapılır.
  Normal audit UPDATE/DELETE yasak kalır. PUBLIC'a fonksiyon veya bağlam DML yetkisi verilmez.
- Başkasının grant'ında revoked_by NULL yapılınca mevcut revoked_at CHECK'i bozulur;
  revoke edilmiş satırda nil UUID kullanılır ve revoked_at korunur. Uygun nullable
  editör/moderatör alanları NULL yapılır; başkasının içeriği silinmez.
- Game ancak temizleme ve son REF kontrolü tamamlanınca `204` döner. Geçici kilit/süre
  hatası veya eksik temizlikte `503 ACCOUNT_ERASURE_PENDING` ile Auth yeniden dener.
  Library/Review henüz kişisel veri tablosu taşımadığı için kendi güvenli uçlarında `204` döner.
- Bu çalışma gerçek DEV'e migration/deploy uygulamadı. Tarihsel şema eşdeğerliği testleri
  V40 sınırında tutulur; V171 yükseltmesi ve veri güvenliği ayrı PostgreSQL testleriyle doğrulanır.
- **V172 kullanıldı (2026-11-05, GAME-04/05/15):** `V172__create_game_rating_projection.sql` Review'ın
  imleçli dışa aktarma ucundan beslenen LTZ puan projeksiyonunu (`game_rating_projection`) ve tek satırlı
  tarama durumunu (`game_rating_sync_state`) ekler. Numara, yukarıdaki V171–V180 tahsisinin bir sonraki boş
  numarasıdır (aralık uydurulmadı; kullanıcı onayı oturumda alındı). Kalan boş numaralar V173–V180'dir.
  Devir notundaki (`docs-review/REVIEW_GAME_SERVICE_DEVIR.md`) "V31–V40 doldu, numara ekip kararı" ifadesi bu
  güncel tahsisten önceki durumu anlatır. V172 yalnız ekleme yapar (ALTER yok), veri silmez veya anonimleştirmez;
  `AccountErasureMigrationTest` V171 yükseltmesini `target=171` ile, V172'yi `RatingProjectionMigrationTest` ayrı doğrular.
- Tablo ilişkileri, kısıtlar ve karar bekleyen öneriler:
  [Game Auth entegrasyon belgesi §13](../../docs-Game%20Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md#13-hesap-silme-veri-envanteri-ve-karar-raporu--2026-10-24).

### Game Service başlangıç zinciri ve yeniden numaralandırma kararı — 2026-10-21

Kullanıcının açık ve öncelikli talimatıyla Game Service başlangıç kaynakları yeniden
düzenlenmiştir. Bu, aşağıdaki genel uygulanmış migration değişmezliği kuralına yalnız
bu çalışma için verilen istisnadır; diğer servisleri kapsamaz.

- Aktif temiz kurulum zinciri kesintisiz **V31–V40**, toplam **10 migration** içerir.
- Tüm nihai kolonlar/constraint'ler ilgili CREATE tanımlarındadır; ilişkili tablolar
  domain ve FK bağımlılıklarına göre gruplanır. Tüm indeks, seed ve audit davranışı korunur.
- İlk yeniden yapılandırmada boşalan tarihsel V35/V36/V37 dosyaları kaldırılmıştı.
  Kullanıcının sonraki açık kararıyla aktif kaynaklar aşağıdaki şekilde yeniden
  numaralandırıldı. Aktif V35–V40, aynı numaralı eski (tarihsel) zincir kayıtlarıyla aynı işleri temsil etmez.
- Aktif SQL zincirinde ALTER TABLE yoktur. B migration veya otomatik baseline eklenmemiştir.
- Eski 13 dosya yalnız Game Service test kaynaklarında `db/history/game-service-v173`
  altında arşivlenmiştir; uygulama migration lokasyonu ve paketlenen JAR dışındadır.
- **Eski zincirle oluşmuş bir `game_db` yeni kaynaklarla uyumlu değildir; yeni imajı
  böyle bir DB üzerinde başlatmayın.** Karar (2026-10-21): veri korumalı taşıma yerine
  `ltz-dev` Compose projesi `down -v` ile tamamen sıfırlandı ve mevcut kaynakla temiz
  kuruldu. DEV `game_db` yeni V31–V40 zinciriyle çalışır; repair/baseline/history
  müdahalesi yapılmadı. Eski DB'si olan ekip üyeleri yerel ortamlarını aynı şekilde sıfırlamalıdır.
- Game Service için V171–V190 ek aralığı (2026-10-21'de geçici olarak onaylanmıştı) artık
  kullanılmayacaktır; tahsis ve V174–V190 rezervasyonu kaldırılmıştır. Aktif zincirde boş
  numara yoktur: Game Service'in yeni migration'a ihtiyacı olursa ekipçe yeni bir numara
  aralığı belirlenmelidir; numara uydurulmaz.
- V171–V173 yalnızca eski (tarihsel) 13 dosyalı zincirde kullanılmış Game Service sürümleridir;
  aktif migration değildir. V171–V190 o tarihte hiçbir servise tahsisli değildi; yeniden tahsis ekip
  kararı gerektirir. V41–V170 diğer servislerin tahsisidir ve değişmez.
- Ayrıntılar ve onay öncesi geçiş planı:
  [Game Service yeniden yapılandırma raporu](../../docs-Game%20Service/LTZ-HAM-GAME-Migration-Yeniden-Yapilandirma.md).

#### Önceki 10 dosyalı kaynak zinciri -> nihai aktif dosyalar

- V31 -> V31__create_game_metadata.sql
- V32 -> V32__create_game_external_references.sql
- V33 -> V33__create_game_provider_sync_state.sql
- V34 -> V34__audit_verified_game_identity_changes.sql
- V38 -> V35__create_admin_game_management.sql
- V39 -> V36__create_game_developer_grants.sql
- V40 -> V37__create_game_developer_content.sql
- V171 -> V38__create_localization_and_metadata_provenance.sql
- V172 -> V39__create_game_achievement_definitions.sql
- V173 -> V40__create_game_developer_feedback.sql

Bu harita önceki yeniden yapılandırılmış 10 dosyalı kaynağı esas alır. DEV'deki
tarihsel V35 (game_type), V36 (retry/detail) ve V37 (discovery) işlemleri artık V31/V33
CREATE tanımlarının içindedir; onlar için birebir yeni migration satırı yoktur.
**Tarihsel V40 geliştirici içeriğidir; aktif V40 özel geri bildirimdir.**
Tarihsel V40 -> V173 ve V171 -> V173 testleri yalnız `db/history/game-service-v173`
fixture lokasyonunu; yeni kurulum testleri yalnız `db/migration` lokasyonunu kullanır.

#### Gelecekteki Game Service migration politikası

- Yeni tablo için aynı geliştirmede gerekli kolonlar mümkün olduğunca aynı CREATE
  tanımında tamamlanır; gereksiz küçük ALTER migration'ları üretilmez.
- Kalıcı ortama uygulanmış yeni migration keyfî biçimde yeniden yazılmaz.
- Gerçek şema yükseltmesi gerektiğinde yeni versioned migration kullanılır; bu
  başlangıç temizliği gelecekteki zorunlu şema yükseltmelerini yasaklamaz.
- Aktif aralık (V31–V40) dolmuştur ve ek aralık tahsisi kaldırılmıştır. Gelecekte yeni
  Game Service migration'ı gerektiğinde numara aralığı ekipçe yeniden belirlenir; bu karar
  alınmadan yeni migration dosyası eklenmez.
- İzole PostgreSQL veri taşıma provası yapılmıştır; ancak seçilen DEV yöntemi veri
  taşıma değil, ortamın sıfırlanmasıdır (aşağıya bakınız). Taşıma planı tarihsel alternatiftir.
  [İzole veri taşıma provası](../../docs-Game%20Service/LTZ-HAM-GAME-Izole-Veri-Tasima-Provasi.md).

#### Game Service gerçek DEV yedek/restore hazırlığı

2026-10-21 tarihli ayrı açık onayla yalnız game_db'nin transaction-tutarlı yedeği
Git dışında korundu ve ağsız geçici PostgreSQL 17'ye restore edildi. Eski 13 history
satırı, checksum'lar ve bütün uygulama verileri korundu. Bu yedekleme adımında gerçek DEV hedef DB
oluşturulmadı; ENV/imaj/konteyner ve migration geçmişi değiştirilmedi (sonraki karar: aşağıdaki DEV sıfırlaması).
Güvenli izole Sonar taraması başta dört test bakım bulgusuyla Quality Gate FAIL verdi;
bulgular test kaynaklarında giderildi ve tekrarlanan taramada Quality Gate PASS oldu
(260 test, issue/hotspot 0/0).
[Yedek/restore raporu ve tarihsel geçiş planı](../../docs-Game%20Service/LTZ-HAM-GAME-DEV-Yedek-Restore-Hazirlik.md).

#### Uygulanan DEV kararı: `ltz-dev` ortamının sıfırlanması — 2026-10-21

Eski verilerin korunmasına gerek kalmadığı için `game_db_v31v40` taşıma yaklaşımı
bırakıldı. `ltz-dev` Compose projesi `down -v` ile kapatıldı (yalnız bu projenin
postgres volume'ü ve ağı silindi), ardından mevcut kaynakla `up -d --build` yapıldı.

- Game Service `game_db`: Flyway history **10 kayıt, V31–V40, hepsi success**; V171–V173 yok;
  21 uygulama tablosu, 2 fonksiyon + 2 kullanıcı trigger'ı, STEAM sync başlangıç kaydı mevcut.
- Hibernate `ddl-auto=validate` ve Flyway validate başarılı; yeniden başlatmada
  "validated 10 migrations / No migration necessary".
- Game Service dahil 18 servisin tamamı ve postgres çalışıyor (hepsi health UP, restart=0).
  Game dışı 16 servis veritabanında henüz migration kaydı yoktur; onların tablo tasarımları
  `database.md` içinde `[ÖNERİ]`dır, uygulanmış şema değildir.
- `ltz-sonar` projesi ve Git dışı Game Service yedeği (SHA-256 değişmedi) korunmuştur.
- Kaynak kod değişmediğinden Sonar yeniden taranmadı; önceki PASS korunur.
- Yetkili (JWT'li) uçlar canlıda **NOT VERIFIED**; anonim istekler 401 verir.

Game Service'in V31–V40 aralığı, yeniden yapılandırılan aktif başlangıç zinciri (10 migration,
Faz 1–6 sonuç şemasının tamamı) tarafından tamamen kullanılmıştır; bu aralıkta boş numara kalmamıştır.
Eski (tarihsel) zincirdeki V31–V40 ve V171–V173 numaraları aktif numaralarla aynı işleri temsil etmez.
Game Service için V171–V190 ek aralığı ve V174–V190 rezervasyonu kaldırılmıştır; ilerleyen şema
ihtiyaçları için yeni aralık ekipçe belirlenir. Mevcut ve rezerve V1–V170 tahsisleri değişmez.
Bu kayıt ortak branch'e aktarılarak ekiple paylaşılmalıdır. Yukarıdaki 2026-10-21
yeniden numaralandırma kararı, yalnız Game Service başlangıç kaynaklarına verilen açık izindir.

1. Her servis yalnızca kendisine ayrılan
   migration numaralarını kullanır.

2. Migration dosya adlandırması:
   V{NUMARA}__{aciklama}.sql

3. Örnekler:
   V1__create_hardware_tables.sql
   V31__create_game_tables.sql
   V61__create_auth_tables.sql
   V121__create_social_tables.sql
   V151__create_notification_tables.sql

4. Her servisin migration dosyaları:
   backend/services/{service-name}/src/main/resources/db/migration/

5. Uygulanmış migration dosyaları değiştirilmez.
   Şema değişiklikleri yeni migration ile yapılır.

6. Migration numaraları, servisin kendi aralığı
   içerisinde artan sırayla kullanılır.

7. İlk 10 migration yeterli olmazsa yeni bir
   numara aralığı ekipçe kararlaştırılır.

8. Aynı servis üzerinde birden fazla geliştirici
   çalışıyorsa migration numaraları koordine edilir.

9. Servisler birbirlerinin veritabanlarına
   doğrudan müdahale etmez.

10. Migration dosyaları Git'e gönderilmeden önce
    mevcut migration geçmişi kontrol edilir.
