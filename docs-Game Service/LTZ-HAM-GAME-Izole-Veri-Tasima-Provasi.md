# Game Service — İzole PostgreSQL veri taşıma provası ve DEV geçiş planı

Tarih: 2026-10-21 · Branch: feature/game-service · Sorumlu: Hamza

> **Takip çalışması:** Gerçek DEV yedeği alındı ve ağsız izole PostgreSQL'de restore
> doğrulandı; gerçek DEV geçişi yapılmadı. Güvenli izole Sonar taraması 260 testi
> başarıyla çalıştırdı, ancak dört bakım bulgusu nedeniyle Quality Gate FAIL verdi;
> bulgular giderildi ve tekrarlanan izole taramada gate PASS oldu (issue/hotspot 0/0).
> Güncel sonuçlar ve onay bekleyen plan:
> [Gerçek DEV yedek/restore hazırlığı](LTZ-HAM-GAME-DEV-Yedek-Restore-Hazirlik.md).
> Aşağıdaki “DEV'e bağlanılmadı / Sonar bekliyor” ifadeleri ilk sentetik prova çalışmasının tarihsel kapsamıdır.

## Sonuç ve kapsam

**TAMAMLANDI:** tarihsel 13 migration ile oluşturulmuş kaynak DB'den, yeni V31–V40
zincirli hedef DB'ye bütün uygulama verilerinin taşınması izole PostgreSQL 17 üzerinde
gerçekleştirildi. 21 uygulama tablosunda 34 sentetik kayıt, tam kolon içerikleri ve
SHA-256 özetleri eşleşti. Kaynak history 13, hedef history 10 kayıt olarak korundu.
Eski history hedefe kopyalanmadı; repair/baseline/reset kullanılmadı.

**GÜNCEL:** gerçek DEV veri geçişi yapılmadı; bunun yerine DEV ortamı sıfırlanıp yeni
zincirle kuruldu (bkz. yedek/restore raporu §10). Gerçek yedek/restore provası sonradan
yapıldı. **BEKLİYOR (NOT VERIFIED):** gerçek Auth entegrasyonu ve yetkili canlı işlemler.
Aşağıdaki ilk kapsam cümlesi tarihsel kayıttır: Bu görev gerçek DEV'e bağlanmaz ve DEV'den veri kopyalamaz.
Bu rapordaki PASS sonuçları yalnız izole testlere aittir.

## Test ortamı ve kodun konumu

- PostgreSQL: `postgres:17-alpine`, Testcontainers tarafından geçici konteyner ve
  rastgele portlarla başlatılır. DB adları yalnız o konteyner içinde UUID ile üretilir.
- Referans: açıkça `classpath:db/history/game-service-v173`; tarihsel V31–V40 + V171–V173.
- Hedef: açıkça `classpath:db/migration`; yeni aktif V31–V40.
- MigrationTransferSupport test-only yardımcıdır; src/main, Docker veya ENV'e taşınmaz.
- MigrationTransferTest: 8 senaryo. MigrationTransferApplicationTest: 1 gerçek HTTP
  uygulama senaryosu. Bunların yanında 4 architecture ve 2 tarihsel test korunur.
- Sentetik veri: `src/test/resources/db/rehearsal/synthetic_game_data.sql`.
  Tarihsel ve sentetik SQL'lerin uygulama JAR'ında bulunmadığı ayrıca doğrulanır.

## Sentetik kayıtlar ve karşılaştırma kapsamı

Bütün tablolarda en az bir satır vardır; iki oyun, dört sözlük türü, iki dil,
aktif/geri alınmış grant, görünür/gizli başarım ve iki özel feedback varyantı kullanılır.
Version değerleri sıfırdan farklıdır; UTC dışı giriş zamanı ve mikro saniyeli zamanlar
da bulunur. Null ve null olmayan kolon değerleri birlikte sınanır.

Kaynak ve hedefte eşleşen satır sayıları:

- games: 2; catalog_terms: 4; game_terms: 4.
- game_developers: 1; game_publishers: 1; game_media: 1; game_system_requirements: 1.
- game_languages: 2; game_external_refs: 1; game_provider_sync_state: 1.
- game_identity_audits: 1; game_field_overrides: 1; game_admin_audits: 1.
- game_developer_grants: 2; game_developer_contents: 1; game_developer_media: 1; game_update_notes: 1.
- game_language_supports: 2; game_metadata_provenance: 2.
- game_achievement_definitions: 2; game_developer_feedback: 2.

Tablo başına kayıtlar bütün kolonlarıyla `row_to_json` üzerinden UTC oturumunda
kanonik ve C collation sıralı okunur. Her kaydın UTF-8 uzunluğu ve byte'ları SHA-256'ya
katılır. Satır sayısı/hash yanında tam satır içerikleri de eşitlik kontrolünden geçer.
Böylece UUID, FK, tarih, timestamp, version, görünürlük, özel içerik, provenance,
grant/revoke ve audit değerleri kapsanır. Kanıt dosyasında yalnız tablo adı/sayı/hash
bulunur; özel feedback gövdeleri veya bağlantı sırları rapora yazılmaz.

Yerel kanıt: `logs/migration-transfer-digests.txt`. Örneğin STEAM sync satırının
version=19, cursor=991231, failure_count=3 ve diğer tüm kolonları hedefte korunur;
hedefteki default satırın kaynağın güncel durumunu ezmesi engellenmiştir.

## Aktarım mekanizması

1. Kaynak/hedef şema imzaları karşılaştırılır. Fark varsa kopyalama başlamaz.
2. Kaynakta read-only REPEATABLE READ transaction, hedefte tek transaction açılır.
3. Hedefin uygulama tabloları boş olmalıdır; yalnız kanonik UUID/provider ile tek
   STEAM başlangıç satırı kabul edilir. Beklenmeyen hedef kayıtları ezilmez.
4. Sabit allowlist'teki 21 tablo FK bağımlılık sırasıyla aktarılır. Kolonlar JDBC
   metadata'sından alınır, tür değerleri parametreli statement ile taşınır.
5. Yalnız game_provider_sync_state için PK bazlı UPSERT kullanılır; kaynak satırın
   bütün diğer kolonları hedefe geçirilir. Diğer tablolarda normal INSERT vardır.
   Çakışma sessizce atlanmaz; ON CONFLICT DO NOTHING kullanılmaz.
6. Audit kayıtları yalnız INSERT ile taşınır. Trigger/FK/CHECK/unique devre dışı
   bırakılmaz. Deferred constraint'ler commit sırasında da doğrulanır.
7. Commit öncesi bütün tablo hash'leri ve tam satırlar karşılaştırılır. Hata varsa
   transaction rollback edilir; başarı makbuzu üretilmez.
8. Commit sonrası makbuz hedef tablo hash'lerini taşır. requireReady, kaynak/hedef
   içeriğini ve makbuzu tekrar kontrol eder; Flyway validate ve pending=0 ister.
   Uygulama testi yalnız bu kapı geçildikten sonra uygulamayı başlatır.

Bu test kapısı gerçek DEV için kalıcı bir deployment kilidi değildir. Üretim/DEV
geçiş prosedürü de aynı kontrolü ve operatör onayını uygulamalıdır; otomatik bir
deployment aracı veya yeni servis endpoint'i eklenmemiştir.

## Hata ve veri koruma senaryoları

- **TAMAMLANDI — Kesinti:** 11 tablo sonrasında sentetik exception. Oyunlar, audit
  INSERT'leri ve önceden UPSERT edilmiş seed dahil transaction tamamen geri alınır.
- **TAMAMLANDI — Çakışma:** aktarılmış oyun PK'si tekrar INSERT edilir; hata oluşur,
  kayıt atlanmaz, tüm hedef verisi aktarım öncesine döner.
- **TAMAMLANDI — FK hatası:** olmayan game UUID'siyle dil satırı eklenir; FK reddeder,
  transaction geri alınır. Constraint kapatma yoktur.
- **TAMAMLANDI — Eksik kayıt:** commit öncesi bir feedback satırı çıkarılır;
  karşılaştırma farkı bulur ve seed dahil aktarım geri alınır.
- **TAMAMLANDI — Aynı sayıda yanlış içerik:** feedback body değiştirilir; sayı eşit
  olsa da hash farkı yakalanır, transaction commit edilmez.
- **TAMAMLANDI — Şema farkı:** hedefte fazladan indeks oluşturulur; şema karşılaştırması
  kopyalamayı başlatmaz, hedef verisi değişmez, readiness makbuzsuz reddedilir.
- **TAMAMLANDI — Commit sonrası bozulma:** oyun başlığı değiştirilir; makbuz hash'i
  farklıdır, requireReady HASH_MISMATCH ile hedefin kullanıma alınmasını reddeder.
- **TAMAMLANDI — Audit ve FK koruması:** başarılı hedefte admin audit UPDATE,
  identity audit DELETE ve referanslı game DELETE işlemleri reddedilir. Sonrasında
  kaynak/hedef hash'leri hâlâ eşittir.

Kesinti/çakışma/FK/eksik/içerik hatalarında hedefin başlangıç digest'i, kaynak digest'i
ve hedef history'nin değişmediği doğrulanır. Başarısız aktarımda makbuz null kalır;
readiness kapısı geçilemez. Tamamlanmamış hedefte uygulama başlatılmaz.

## Gerçek Game Service başlangıcı ve HTTP sonuçları

**TAMAMLANDI:** SpringApplicationBuilder ile gerçek uygulama iki kez başlatılır:
önce aynı güncel kod + tarihsel referans DB, sonra güncel kod + taşınmış yeni DB.
Her seferinde yalnız 127.0.0.1 üzerinde rastgele HTTP portu kullanılır.
Datasource/Flyway lokasyonu açık test parametreleriyle verilir; ddl-auto=validate.
Test JVM'sinde provider ve sync kapalıdır; dış sağlayıcıya çıkılmaz. DEV ayarı değişmez.
Kimlik doğrulama ve RBAC aynı kalır; test için token üretilmez veya bypass eklenmez.

Her iki uygulamada 200 dönüp aynı JSON'u veren 9 okuma:

- /actuator/health: status=UP.
- GET /api/v1/game/games?size=10: iki oyun.
- GET /api/v1/game/games/search?q=Synthetic.
- GET /api/v1/game/{gameId}/card: doğru kanonik UUID ve Synthetic Atlas başlığı.
- GET /api/v1/game/{gameId}/metadata: metadataLanguage=tr, iki dil desteği ve genel dil listesi.
- GET /api/v1/game/games/{gameId}: detay/freshness dahil eşit yanıt.
- GET /api/v1/game/games/{gameId}/modules/ACHIEVEMENTS: AVAILABLE, yalnız bir görünür tanım.
- GET /api/v1/game/games/{gameId}/modules/GUIDES: INTEGRATION_PENDING.
- GET /api/v1/game/games/{gameId}/developer-content.

Yeni hedef uygulamasında feedback, ADMIN başarım listesi ve geliştirici feedback
listesi anonim erişimde 401 verir. Toplam 18 public/health + 3 anonim güvenlik isteği
başarıyla doğrulanır. HTTP okumaları sonrası bütün tablo hash'leri tekrar eşleşir.
Flyway validate ve ikinci migrate=0 başarılıdır; Hibernate gerçek uygulama başlangıcında
ve ayrıca programatik validate ile geçer.

Karşılaştırma eski binary ile yapılmamıştır; aynı güncel servis kodunun eski/yeni DB
düzenindeki davranışıdır. Gerçek Gateway, Auth JWT veya canlı DEV testi değildir.

## Güncel test ve kalite durumu

- **TAMAMLANDI:** ilk hedefli tur 15 test PASS: architecture 4, tarihsel 2, transfer 8,
  HTTP uygulama 1. Kanıt: logs/migration-renumber-targeted.log.
- **TAMAMLANDI:** son Game Service clean verify/pre-push 243 test, 0 failure/error/skipped,
  JaCoCo LINE 1490 covered / 19 missed = %98,74. Eşik düşürülmedi veya test dışlanmadı.
  Son kanıt: logs/migration-renumber-game-service-final.log.
- **TAMAMLANDI:** yeni zincirin şema sayıları ve hash kontrolleri test assertion'larıdır;
  sadece doküman iddiası değildir. Eski fixture SHA'ları Git HEAD ile de eşleşir.
- **İlk teslimde BEKLİYOR:** SonarQube güncel analiz. Yerel sunucu UP; mevcut scan runner'ı bütün
  backend testlerini DEV bağlantılarıyla çalıştırdığı için bu kapsamda başlatılmadı.
  Önceki PASS güncel sayılmaz; yalnız sunucu erişimi analiz PASS anlamına gelmez.
- **BAŞARISIZ (ilk takip taraması, tarihsel):** DEV'e bağlanmayan izole taramada Quality Gate
  dört test bakım bulgusuyla FAIL. 260 backend testi PASS.
- **TAMAMLANDI (kalite takibi):** dört bulgu giderildi; tekrarlanan izole taramada Quality Gate
  PASS, 260/260 test, issue/hotspot 0/0. Ayrıntı: yedek/restore raporu §9.

## Gerçek DEV için kesin geçiş planı — TARİHSEL ALTERNATİF (seçilmedi, uygulanmadı)

> **Güncel durum (2026-10-21):** Bu veri korumalı plan seçilen yöntem değildir. Eski DEV
> verilerine ihtiyaç kalmadığından `ltz-dev` Compose projesi `down -v` ile sıfırlanıp
> mevcut kaynakla temiz kuruldu; DEV `game_db` yeni V31–V40 zinciriyle (10 kayıt) çalışıyor.
> Ayrıntı ve kanıtlar: [Yedek/restore raporu §10](LTZ-HAM-GAME-DEV-Yedek-Restore-Hazirlik.md).
> Plan, veri korunması yeniden gerekirse başvurulacak alternatif olarak saklanır.

Aşağıdaki adımlar yalnız ayrıca açık onay verildikten sonra uygulanabilirdi.

1. **Envanter ve onay:** o günkü DEV game_db history, success ve eski SQL checksum'ları
   tarihsel fixture ile doğrulanır. Mevcut çalışan Game Service image/container ID'si,
   diğer servis container ID/StartedAt ve mount bilgileri kaydedilir. Aday hedef DB
   adı (örneğin game_db_v31v40), yedek konumu, erişim, kesinti penceresi ve bağlantı
   değişikliği kullanıcı tarafından onaylanır. Şu an DEV'e bağlanılmamıştır.
2. **Transaction-tutarlı yedek:** yalnız game_db için korumalı pg_dump yedeği alınır;
   dosya checksum'ı ve gerekli rol/izin bilgileri güvenli saklanır. Token/parola/özel
   kayıt içerikleri terminale veya Git'e konmaz. Flyway history yedeğe dahildir;
   history üzerinde yazma yapılmaz.
3. **Restore provası:** yedek gerçek DEV'den ayrı izole PostgreSQL 17 ortamına geri
   yüklenir. Eski history korunarak şema, satır/hash, FK/audit ve okunabilirlik kontrol
   edilir. Bu görev sentetik kopyalamayı doğrular; pg_dump/pg_restore provası yapılmadı.
4. **Ayrı hedef DB:** yalnız yeni Game Service hedef DB'si, uygun owner/CONNECT/schema
   izinleriyle kurulur. Ortak PostgreSQL rol/parola veya diğer DB izinleri değiştirilmez.
   Yeni V31–V40 zinciri normal Flyway başlangıcıyla uygulanır; kendi 10 kayıtlık history'si
   oluşur. Eski history hedefe kopyalanmaz. Dolu DB'ye baseline uygulanmaz.
5. **Yazmaları durdurma:** yalnız Game Service'in API yazmaları ve provider sync işi
   durdurulur; uygulama durduktan sonra açık transaction kalmadığı doğrulanır. Diğer
   mikroservisler yeniden başlatılmaz. Son veri snapshot'ı ve hash envanteri alınır.
6. **Aktarım:** restore edilmiş kaynak/prova ile son snapshot tutarlılığı sağlanır;
   21 tablo FK sırasıyla tek hedef transaction içinde, tüm UUID/version/timestamp ve
   özel/audit verileriyle taşınır. Kaynak read-only snapshot'tır. Trigger/constraint
   kapatılmaz. Hedef mevcut data içeriyorsa otomatik birleştirme yapılmaz.
7. **Seed eşitleme:** yalnız kanonik Steam sync PK'si için bütün kaynak kolonları
   hedefe UPSERT edilir. Provider, cursor, watermark, retry, counters ve version
   korunur. Diğer tablolarda çakışma hatadır; sessiz kayıt atlama yoktur.
8. **Zorunlu kapı:** tüm 21 tablonun tam kolon içerikleri, sayıları ve kanonik
   hash'leri; history ayrımı; FK/audit; yeni Flyway validate/pending=0; Hibernate
   ve isolated health/API doğrulaması geçmeden hedef seçilmez. Başarısız transaction
   geri alınır; kaynak DB hiçbir zaman bu nedenle silinmez veya değiştirilmez.
9. **Bağlantı hazırlığı:** Compose'da Game Service'in `DB_GAME_NAME` değişkeni ve
   uygulamadaki mevcut datasource şablonu kullanılır. Yeni değişken gerekmez.
   Gerçek .env.dev içeriği bu görevde okunmamış/değiştirilmemiştir. Onay sonrasında
   yalnız DB_GAME_NAME hedef adla güncellenir; POSTGRES_HOST/PORT/user/password,
   Gateway servis adresi ve diğer DB_* adları değiştirilmez. Yeni DB, mevcut volume
   üzerinde init SQL'in tekrar çalışacağını varsayarak oluşturulmaz; ayrı onaylı
   oluşturma gerekir. Parola rotasyonu bu işin kapsamında değildir.
10. **Yalnız Game Service dağıtımı:** eski imaj ID/digest'i korunarak yeni imaj build
    edilir; yalnız game-service `--no-deps` ile güncellenir. Boot sırasında yeni DB
    üzerinde normal Flyway validate ve 0 migration beklenir; app Hibernate validate
    ile açılır. Repair/baseline/reset/history düzenlemesi yapılmaz.
11. **Canlı son kontroller:** Game Service ve Gateway health, public/anonim korumalı
    endpoint'ler, kanonik gameId ve veri hash'leri kontrol edilir. Gerçek geçerli
    JWT yoksa yetkili canlı yazma PASS sayılmaz. Diğer konteynerlerin ID/StartedAt/
    mount'ları aynı olmalıdır. Eski volume/DB/imaj korunur.
12. **Yazma öncesi geri dönüş:** yeni hedef henüz yazma kabul etmediyse DB_GAME_NAME
    eski DB'ye ve Game Service eski imaj digest'ine döndürülür; yalnız servis yeniden
    başlatılır. Eski image + eski history birlikte kullanılır.
13. **Yazma sonrası geri dönüş:** yeni DB yazma almaya başladıysa eski DB artık geride
    kalır. Kör bağlantı geri alma veri kaybettirir. Önce yazmalar durdurulur, yeni
    değişiklikler için doğrulanmış delta/ters taşıma ya da ileri düzeltme planı
    kullanıcıya sunulur. Delta kanıtı/onayı olmadan eski DB'ye dönülmez.
14. **Saklama:** eski DB, volume, yedek ve eski imaj otomatik silinmez. Kullanıcı
    onayı ve saklama kararı olmadan temizlik yapılmaz. Tüm sonuçlar rapora eklenir.

Bu plan uygulanmadı ve seçilmedi (DEV ortamı sıfırlandı; yukarıdaki güncel durum notuna bakınız). İzole test yardımcı kodu büyük
veri aktarımı için performanslı operasyon aracı olarak sunulmaz: satırlar bellekte
karşılaştırılır, gerçek veri hacmi/backup restore/süre/rol/grant ve kesinti SLO'su
ayrı prova gerektirir. Kaynak veya hedefin eşzamanlı yazılması bu kontrollü geçiş
modelinin parçası değildir; gerçek kesinti penceresi zorunludur.

## Ekip ve teslim sınırları

Yeni Game Service zinciri temiz kurulum zinciridir. Eski DB'si olan ekip üyeleri
aynı kaynağı çekip mevcut DB üzerinde restart etmemelidir; aynı sürüm numaralarının
anlamı değişmiştir. Yeni temiz DB veya onaylı veri korumalı taşıma gerekir.
V171–V173 yalnız eski zincirde kullanılmış tarihsel sürümlerdir; V171–V190 ek aralığı ve V174 rezervasyonu sonradan kaldırılmıştır, yeni migration için aralık ekipçe belirlenir.
Diğer servislerin V41–V170 tahsisleri ve migration kaynakları değişmez.

Commit/push/merge/rebase/reset/add yapılmaz. Gerçek DEV geçişi, ENV değişikliği,
imaj/container güncellemesi, yedek/restore veya DB oluşturma yapılmadan durulur.
