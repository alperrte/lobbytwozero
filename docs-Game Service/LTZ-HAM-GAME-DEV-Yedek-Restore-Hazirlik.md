# Game Service — Gerçek DEV yedeği, restore provası ve geçiş hazırlığı

Tarih: 2026-10-21 · Branch: feature/game-service · Sorumlu: Hamza

## Kapsam ve sonuç

- **TAMAMLANDI:** DEV salt okunur envanteri ve Compose doğrulaması.
- **TAMAMLANDI:** yalnız game_db için transaction-tutarlı custom pg_dump yedeği ve dosya bütünlüğü.
- **TAMAMLANDI:** gerçek yedeğin ağsız geçici PostgreSQL 17'ye tam restore edilmesi.
- **TAMAMLANDI:** 22 tablo (21 uygulama + Flyway), satır/hash, history, şema, owner/ACL ve bağımlılık karşılaştırmaları.
- **TAMAMLANDI:** veri korumalı geçiş ve geri dönüş için uygulanabilir operasyon planı.
- **TAMAMLANDI:** DEV'e bağlanmayan gerçek Sonar taraması; 260 backend testi başarılı.
- **TAMAMLANDI:** dört test bakım bulgusu kaynakta giderildi; güncel izole Sonar
  Backend Analysis, Quality Gate ve Overall Targets PASS. Issue/hotspot 0/0 (§9).
- **KARAR DEĞİŞTİ (2026-10-21, güncel):** `game_db_v31v40` taşıma planı seçilmedi ve uygulanmadı.
  Kullanıcı eski DEV verilerinin korunmasına gerek olmadığını bildirdi ve `ltz-dev` Compose
  projesinin `down -v` ile sıfırlanıp temiz kurulmasını onayladı; bu uygulandı (bkz. §10).
  Aşağıdaki §6 taşıma planı **tarihsel alternatiftir**, seçilen yöntem değildir.

Yedek/restore ve kalite görevleri sırasında (§1–§9) DEV üzerinde CREATE/INSERT/UPDATE/DELETE,
repair/baseline/reset/history müdahalesi yapılmadı. Game Service veya başka çalışan servis
yeniden başlatılmadı. Ortamın sonradan sıfırlanması ayrı bir kararla §10'da kayıtlıdır. İlk yedek/restore
çalışmasında kaynaklar değiştirilmedi; sonraki kalite takip çalışmasında yalnız §9'daki
iki test dosyası düzenlendi. Migration, iş mantığı, POM, Compose ve ENV değişmedi.
Gerçek yedek yalnız yerel restore için kullanıldı; Sonar'a veya üçüncü tarafa gönderilmedi.

## 1. DEV envanteri (yedek anındaki, sıfırlama öncesi durum)

- Compose projesi: ltz-dev. Mevcut profil `config --quiet` ile doğrulandı; çözümlenmiş
  ENV/değerler terminale yazdırılmadı. Profil dosyaları elle açılmadı/kopyalanmadı.
- Game Service konteyneri: ltz-dev-game-service-1.
- Konteyner ID: b78a96507d2f8625b811742670d2fcdf375205c55cce4f2dde21ea82746be4f6.
- İmaj: ltz/game-service:dev.
- Image ID: sha256:7671aaa71469cf29c8852fc9799b819d537584401a8800f510e4d2dad81f36ef.
- DB adı: game_db; servis içi bağlantı postgres:5432. Çalışan konteynerde
  STEAM_CATALOG_ENABLED=false. Bunlar sır olmayan seçilmiş ayarlardır; parolalar
  rapora veya komut çıktısına yazılmadı.
- PostgreSQL 17; DB boyutu ölçüm anında **8.681.139 byte** (yaklaşık 8,28 MiB).
- Windows C: boş alan yaklaşık **51,9 GB**; PostgreSQL dosya sisteminde df çıktısına
  göre **922.533.252 KiB** kullanılabilir alan. Docker VM'nin sanal disk boşluğu
  fiziksel Windows disk kapasitesiyle aynı şey değildir; yedek için C: kontrolü esas alındı.
- Başlangıçta 21 mevcut ltz konteyneri kaydedildi. Game dışındaki 20 konteynerin ve
  Game'in ID/image/StartedAt/status/mount bilgileri korunma kontrolüne alındı.

Uygulama tablo sayıları: game_provider_sync_state=1; aşağıdaki 20 tablo=0:
games, catalog_terms, game_terms, game_developers, game_publishers, game_media,
game_system_requirements, game_languages, game_external_refs, game_identity_audits,
game_field_overrides, game_admin_audits, game_developer_grants, game_developer_contents,
game_developer_media, game_update_notes, game_language_supports, game_metadata_provenance,
game_achievement_definitions, game_developer_feedback.

Bu gerçek yedekte dolu oyun/özel feedback yoktur. Gerçek backup/restore kanıtı
sentetik 34 kayıtla yapılan dolu veri taşıma kanıtının yerine geçmez; ikisi birlikte değerlendirilir.

## 2. Eski Flyway geçmişi (tarihsel; sıfırlamayla ortadan kalktı)

13 migration başarıyla uygulanmış: V31–V40 ve V171–V173. Her birinin script adı ve
checksum'ı mevcut tarihsel fixture'ın Flyway uyumlu CRC32 değeriyle karşılaştırıldı:

- V31=544546224; V32=1904067040; V33=-1593376719; V34=1612787787.
- V35=-1658130016; V36=-943308524; V37=-1370159271; V38=1212553273.
- V39=-1832370452; V40=1034506753; V171=450374004.
- V172=2116306688; V173=-1294749646.

Yeni aktif V31–V40 dosyalarının bu geçmişle uyuşmadığı kaynak eşleştirmesinden ve
MigrationArchitectureTest'in gerçek PostgreSQL üzerinde verdiği validation reddinden
bilinir. Bu görevde DEV üzerinde Flyway migrate/validate komutu çalıştırılmadı;
geçmiş yalnız SELECT ile okundu. Mevcut DB'ye yeni imajla restart yapılmamalıdır.

## 3. Gerçek yedek ve korunması

Yedek zamanı: **2026-10-21 20:38 UTC / 23:38 Europe/Istanbul**.

Git dışında korumalı yerel klasör:
`C:\Users\T_451\AppData\Local\LTZ\Backups\game-service\2026-10-21T20-38-19-612Z\`

- game_db.dump: **58.013 byte**, PostgreSQL custom archive, yalnız game_db.
- SHA-256: `2cadc46ef93109771d6f28b0b1bd998d46e52fe2241f03c3df15ac4a1c4e863a`.
- game_db.dump.sha256: dosya bütünlük kaydı.
- manifest.json: teknik envanter, tablo sayı/hash ve sonuç metadata'sı.

Klasör ACL mirası kapatıldı; mevcut Windows kullanıcısı ve SYSTEM dışında erişim
kuralı yoktur. Yedek dosyasında da yalnız bu iki principal için iki kural doğrulandı.
Yedek repoya kopyalanmadı, stage edilmedi veya paylaşılmadı. Bu ACL korumasıdır;
ayrıca dosya şifrelemesi yapıldığı iddia edilmez. Yedek kullanıcı onayı olmadan silinmez.

Kaynakta REPEATABLE READ READ ONLY transaction içinde pg_export_snapshot alındı.
pg_dump --format=custom --snapshot ve kaynak tablo/history hash'leri **aynı snapshot**
ile üretildi. Böylece dump ile karşılaştırma envanterinin farklı zamanlara ait olması
engellendi. Kaynakta yalnız olağan pg_dump read lock'ları kullanıldı; veri yazılmadı.

Archive TOC okunarak flyway_schema_history TABLE DATA girdisi doğrulandı. Dosya
yazıldıktan sonra diskten yeniden okunup SHA-256 kontrol edildi. Dump tamamlanmadan
restore başlatılmadı; snapshot transaction'ı sonlandırıldı.

## 4. Tam restore provası ve bağımlılıklar

PostgreSQL 17 geçici konteyneri **--network none**, yayınlanmış port olmadan,
PGDATA için 512 MiB tmpfs üzerinde başlatıldı. Gerçek DEV ağına eklenmedi. Yedek
pg_restore stdin üzerinden aktarıldı; konteynere veya başka servise kalıcı dump
kopyası bırakılmadı. --single-transaction --exit-on-error kullanıldı.

Restore için gereken bir sahip rolü izole ortamda NOLOGIN olarak oluşturuldu;
geçici restore operatörü ayrı role sahipti. Hedef DB'nin owner'ı kaynak DB ile
eşleştirildi. Kaynak rollerinin parola/login özellikleri kopyalanmadı; pg_dump yalnız
game_db yedeğidir, global rol yedeği değildir. Mevcut owner, tablo/şema/fonksiyon ACL
metadata'sı korundu. Extension listesi yalnız **plpgsql**; ayrıca pgcrypto kurulmadı.

Geri yükleme tamamlandıktan sonra:

- 21 uygulama tablosu ve flyway_schema_history okunabildi.
- Uygulama toplamı 1 satır, history 13 satır; kaynak/restore sayı ve SHA-256'ları eşit.
- Hash'ler tüm kolon içeriklerini kapsar; gerçek satırlar terminale veya rapora yazılmaz.
- Kolonlar, default/null/tür/sıra, constraint özellikleri, indeksler, fonksiyonlar,
  kullanıcı trigger'ları, owner/ACL ve extension listesi karşılaştırıldı.
- Kaynak veriler ve geçmiş, sonraki salt okunur kontrolde de yedek snapshot'ıyla eşleşti.
- Geçici restore konteyneri durduruldu; --rm/tmpfs sayesinde verisi kaldırıldı.
  Mevcut Docker volume'leri silinmedi veya değiştirilmedi.

### CHECK metin farkının teşhisi

İlk restore karşılaştırması katı constraint metni eşitliği nedeniyle başarısızdı;
bu deneme PASS gösterilmedi. Tablo hash'leri ve history o denemede de eşleşiyordu.
İncelemede 24 enum CHECK ifadesinde pg_dump round-trip gösterim farkı görüldü:
toplu varchar[] -> text[] cast, eleman bazında varchar -> text cast olarak deparse edilmişti.

Yalnız bu **tam sabit-array cast kalıbı** eşdeğer biçime kanonikleştirildi. Constraint
isimleri, enum sabitleri, kolonlar, operatörler, validation ve deferrability özellikleri
çıkarılmadı veya görmezden gelinmedi. Diğer bütün şema imzaları katı karşılaştırıldı.
Son restore karşılaştırması bu sınırlı kanonikleştirmeyle PASS verdi. Migration SQL'i
değiştirilmedi. Bu nedenle “raw constraint metinleri byte düzeyinde aynı” iddiası yoktur.

## 5. Gerçek veri için aktarım yönteminin uygunluğu

Mevcut gerçek veri yalnız tek provider state satırıdır; önceki test-only JDBC aktarım
yönteminin bellek kullanımı bu veri hacmi için uygundur. Bununla birlikte 21 tablonun
tamamı, UUID/version/timestamp/provenance/grant/feedback/audit alanları ve hash kapıları
korunmalıdır. Başlangıçta boş olan tablo daha sonra dolabilir; son geçiş snapshot'ında
envanter tekrar alınır. 34 sentetik kayıtla dolu veri ve rollback testleri önceki raporda bulunur.

Bu görevde gerçek yedek **eski history ile restore edildi**. Gerçek verilerin yeni
V31–V40 history'li ikinci hedefe aktarılması veya bu hedefte uygulama başlatılması
yapılmadı. Yeni zincirle uygulama testi önceki sentetik provanın kanıtıdır.

Yeni hedef temiz kurulur. Tarihsel flyway_schema_history aktarım allowlist'ine
girmez; hedefin 10 yeni kaydı normal Flyway ile oluşur. Tek kanonik Steam seed
satırında bütün kaynak kolonları UPSERT edilir; kaynak verisi default ile ezilmez.
Diğer tablolarda beklenmeyen kayıt/çakışma varsa işlem durur; kör conflict-ignore yoktur.

## 6. Veri korumalı DEV geçiş planı — TARİHSEL ALTERNATİF (seçilmedi, uygulanmadı)

> Bu bölüm artık güncel yöntem değildir; ortam sıfırlanarak çözüldü (§10). Veri
> korunması yeniden gerekirse başvurulacak plan olarak saklanır.

Önerilen adlar: eski **game_db**, aday yeni **game_db_v31v40**. Yeni ad henüz
oluşturulmadı ve ayarlara yazılmadı; kullanıcı açıkça onaylamalıdır.

1. **Açık onay:** hedef DB adı, yalnız Game Service kesintisi, mevcut rol/izin
   kullanımı, DB_GAME_NAME değişikliği, yeni image build/recreate ve geri dönüş
   politikası birlikte onaylanır. DB parolası/ortak rol değiştirmek bu işte gerekmez.
2. **Güncel envanter:** eski 13 history/CRC, tablo sayı/hash ve disk yeniden doğrulanır.
   Bu yedek 23:38 snapshot'ıdır; geçiş anının yedeği diye varsayılmaz.
3. **Eski imajı koruma:** çalışan sha256:7671aaa... image ID'si kaydedilir; geçiş
   öncesinde ayrıca onaylı geri dönüş etiketiyle korunur. Yeni dev tag'i eski imajın
   tek referansını kaybettirmemelidir. Gerekirse korumalı image save ayrıca yapılır.
4. **Yazma kesintisi:** yalnız Game Service konteyneri kontrollü durdurulur; provider
   sync aynı JVM'de olduğu için o da durur. Sadece mevcut STEAM_CATALOG_ENABLED=false
   değerine güvenilmez. Eski game_db üzerinde açık uygulama transaction'ı/başka
   yazıcı kalmadığı kontrol edilir. Diğer servisler yeniden başlatılmaz.
5. **Kesin son yedek:** durdurulmuş kaynak için yeni transaction-tutarlı dump ve
   manifest alınır. Gereken son restore doğrulaması yapılır. Kaynak DB korunur.
6. **Yeni hedef oluşturma:** aynı postgres:5432 instance'ında yalnız aday DB,
   mevcut Game DB owner'ı ve gerekli CONNECT/schema izinleriyle oluşturulur.
   Bu, mevcut volume/init betiğinin kendiliğinden oluşturacağı bir DB değildir.
   Diğer DB'ler ve ortak rol/parola değişmez. Hedef adı zaten varsa otomatik drop/truncate yoktur.
7. **Normal Flyway başlangıcı:** yeni V31–V40 zinciri aday boş DB'de normal Flyway
   ile kurulur. Uygulama trafiğine açılmayan kontrollü başlangıç/tek seferlik migration
   adımı kullanılır; kaynak DB'ye yeni zincirle bağlantı kesinlikle verilmez.
8. **Aktarım:** son kaynak snapshot'ından yalnız 21 uygulama tablosu FK sırasıyla
   tek hedef transaction'a aktarılır. History dışarıda bırakılır. Sadece kanonik
   Steam seed UPSERT edilir. Audit'ler INSERT; trigger/FK/CHECK'ler açık kalır.
9. **Geçiş kapısı:** bütün kolon/hash/sayılar, source13/target10 history ayrımı,
   seed, FK/audit, Flyway validate ve pending=0, Hibernate validate ve izole health/API
   başarılı olmadan bağlantı değiştirilmez. Hata varsa rollback, Game eski image/DB ile
   kontrollü geri açılır; doğrulanmamış yeni hedef kullanılmaz.
10. **Bağlantı değişikliği:** mevcut DB_GAME_NAME=game_db yalnız onayla aday ada alınır.
    Compose'da bu değişken yalnız Game Service environment'ına aktarılır; postgres
    init belgesindeki DB oluşturma bağlamı ayrıca değerlendirilir. Diğer servisler
    farklı DB_*_NAME anahtarlarını kullanır. POSTGRES_HOST/PORT/user/password ve
    Gateway adresi değişmez; yeni ENV anahtarı gerekmez.
11. **Yalnız Game Service build/recreate:** yeni imaj build edilir; yalnız
    game-service --no-deps --no-build ile güncellenir. Yeni konteynerde hedef DB
    seçimi, history=V40, validate ve 0 bekleyen migration doğrulanır. Diğer mikroservisler değişmez.
12. **Yazma açılmadan kontroller:** Game ve Gateway health, public katalog/metadata/
    modüller, anonim korumalı uçlar ve veri hash'leri doğrulanır. Gerçek JWT yoksa
    yetkili canlı işlemler NOT VERIFIED bırakılır. Kayıt yoksa dolu katalog yanıtı
    canlıda doğrulanmış sayılmaz. Diğer konteyner ID/StartedAt/mount'ları karşılaştırılır.
13. **Paralel yazmayı engelleme:** eski Game konteyneri kapalı tutulur; aynı anda
    iki Game instance'ı iki farklı DB'ye yazma almaz. Yeni instance ilk doğrulamada
    trafiğe/sync'e açılmaz. Compose restart politikası ve manuel eski instance
    başlatılması kontrol edilir. Kaynak DB başka yazıcı almamalıdır.
14. **Yazma öncesi geri dönüş:** eski imaj + eski DB_GAME_NAME birlikte geri seçilir,
    yalnız Game yeniden oluşturulur. Eski history aynen korunur; repair yapılmaz.
15. **Yazma sonrası geri dönüş:** yeni DB kayıt almaya başladıysa eski DB geridedir.
    Kör dönüş yasaktır. Yazmalar durdurulup yeni kayıtlar için doğrulanmış delta/ters
    taşıma veya ileri düzeltme planı ayrıca onaylanır. Yalnız image rollback yeterli değildir.
16. **Saklama:** eski DB, yedek ve eski imaj silinmez; volume temizliği yapılmaz.
    Ekipte eski DB'si olanlar da otomatik restart etmez; temiz DB veya aynı kontrollü
    taşıma gerekir. V171–V173 yalnız tarihsel sürümlerdir; V171–V190 ek aralığı ve V174 rezervasyonu sonradan kaldırılmıştır, yeni migration için aralık ekipçe belirlenir.

Bu plan uygulanmadı. Gerçek hedef DB oluşturma, bağlantı değişikliği, source Game
durdurma ve image/konteyner güncellemesi hâlâ ayrı kullanıcı onayı gerektirir.

## 7. SonarQube güvenlik değerlendirmesi

Mevcut runner docker compose config sonucundan DEV PostgreSQL bağlantılarını çıkarır,
bütün backend için Maven clean verify çalıştırır. Game Service çoğunlukla Testcontainers
kullansa da diğer servislerin context testleri verilen datasource ile JPA/Flyway
başlatır. Dolayısıyla mevcut runner'ı olduğu gibi kullanmak DEV'de schema/history
yazma/migration riski taşır; yalnız scanner'ın statik olması bu test aşamasını güvenli kılmaz.

Güvenli alternatif, mevcut runner'ın runCommand bağımlılık enjeksiyonunu kullanır:

- Ayrı geçici PostgreSQL ve ayrı, DEV ağına bağlı olmayan Docker ağı oluşturulur;
  yalnız loopback portu yayınlanır. Bu kalite DB'sinde gerçek yedek/veri yoktur.
- 17 sentetik servis DB'si kurulur. Compose model çözümleme komutu gerçekten
  çalıştırılmak yerine yalnız test bağlantılarını içeren bellek içi modelle karşılanır.
- Maven clean verify başlamadan önce host/port/role/password ve bütün DB_*_NAME
  değerleri geçici ortama ait mi guard ile doğrulanır; uyuşmazlıkta testler başlamaz.
- Mevcut runner'ın taze test/JaCoCo, gerçek scanner, Quality Gate ve overall targets
  kontrolleri aynı kalır. Test atlama, coverage/gate eşiği değiştirme veya rapor sahteleme yoktur.
- ENV, POM veya resmi Sonar script'i değiştirilmez. Sonar'ın mevcut yerel bağlantı
  ayarları standart loader tarafından kullanılır; değerler/token yazdırılmaz.
- Kalite konteyneri görev etiketi doğrulanarak, bu çalışmanın oluşturduğu geçici
  ağıyla birlikte kaldırılır.

İlk internal network denemesinde Docker Desktop loopback port binding sağlamadı;
Maven/scanner başlamadan durdu ve geçici ortam temizlendi. Sentetik kalite ortamı
ayrı bridge ağıyla yeniden kuruldu. Bu ağ **network none/internal değildir**;
gerçek yedek restore'u ise daima network none ortamında yapılmıştır. İki ortam karıştırılmaz.

### İlk güvenli analizin sonucu — düzeltme öncesi tarihsel kayıt

- SonarQube Access: PASS; Backend Reports: PASS.
- Maven clean verify: **260 test, 0 failure/error/skipped**; Game Service bunun
  **243** testini içerir. Bütün modüller için taze Surefire/JaCoCo raporları kullanıldı.
- Yerel birleşik JaCoCo LINE: 1507 covered / 53 missed = **%96,60**.
  Game Service LINE yine 1490 covered / 19 missed = **%98,74**.
- Backend Analysis/Quality Gate: **FAIL**, gerçek analiz gate status=ERROR.
  Sebep new_violations=4; izin verilen eşik 0.
- Overall Targets Backend: PASS. Genel/yeni security/reliability/maintainability
  rating'leri A, duplication %0; bunlar dört bulgu nedeniyle gate'in FAIL olmasını değiştirmez.
- Sonar overall coverage %90,7; new coverage %91,8. Bunlar JaCoCo LINE ile aynı metrik değildir.
- Atanan Sonar way gate politikası VERIFIED; eşikler değiştirilmedi veya bastırılmadı.
- 17 sentetik DB, veri bağlantı guard'ı ve bellek içi Compose model değişimi
  doğrulandı. Gerçek DEV DB veya gerçek yedek kullanılmadı. Geçici kalite konteyneri
  ve ağı temizlendi; kaynak/DEV konteynerleri korunma kontrolünden geçti.

Dört açık bulgunun tamamı Game Service test kaynaklarında MAJOR CODE_SMELL:

1. java:S9142 — MigrationArchitectureTest.java:55: Pattern.compile döngü dışına taşınmalı.
2. java:S9142 — MigrationArchitectureTest.java:57: döngü içindeki regex kullanımı dışarıda derlenmiş Pattern kullanmalı.
3. java:S9142 — MigrationArchitectureTest.java:64: döngü içindeki regex kullanımı dışarıda derlenmiş Pattern kullanmalı.
4. java:S5778 — MigrationTransferTest.java:136: runtime exception assertion lambda'sı
   tek olası exception çağrısı içerecek şekilde düzenlenmeli; receipt.get() sonucu
   lambda öncesinde alınabilir.

İlk görev yalnız yedek/restore ve güvenli kalite doğrulamasını kapsadığından kaynak
koduna düzeltme uygulanmadı. Migration dosyaları değiştirilmedi. Dört bulgu
giderilip test/scan tekrarlanmadan kalite onayı tamamlanmış sayılmaz; DEV cutover
öncesi bu adım tamamlanmalıydı. Sonraki açık onayla §9'da düzeltmeler uygulanmış ve
güncel gate PASS alınmıştır. Buradaki FAIL yalnız ilk taramanın tarihsel kaydıdır.

Kanıtlar: logs/sonar-isolated-quality-final.log, sonar-isolated-quality-result.json,
sonar-isolated-issues.json ve sonar-isolated-quality-cleanup.json.

## 8. Kanıtlar ve kalan sınırlamalar

Korumalı yerel yedek ve manifest yukarıdaki Git dışı klasördedir. Git dışı yardımcı
betikler/özetler: logs/dev-backup-restore.mjs, dev-backup-restore-result.json,
dev-backup-location.json, sonar-isolated-quality.mjs ve kalite logları. Bunlar gerçek
dump içeriğini rapora kopyalamaz; özel veri/token komut çıktılarına yazılmaz.

Bu görev DB taşıma veya deployment değildir. Kaynak aktif kodu yeni migration
geçmişiyle değiştirmek, mevcut DEV history'sini uyumlu hâle getirmez. Backup/restore
başarısı tek başına gerçek yeni hedefte uygulama trafiğini açma izni değildir.
Kesinti süresi, gerçek yeni hedefe son aktarım ve yazma sonrası ters delta ayrı
operasyon adımlarıdır. Mevcut veri hacmi küçük olsa da bu kapılar atlanmaz.

Son salt okunur kontrol: kaynak/restore 22 tablo ve history/hash/şema eşitliği,
yedek dosya hash'i, Game ve diğer 20 mevcut konteynerin ID/image/StartedAt/mount
bilgileri korunmuştur. Geçici restore ve kalite ortamları temizlendi. On aktif
migration dosyasının önceki yeniden numaralandırma öncesi içerikleriyle SHA-256
eşleşmesi tekrar doğrulandı; uygulama/test kaynağı bu görevde değiştirilmedi.
Git: feature/game-service, staged 0; önceki çalışmalar dahil 7 modified,
11 deleted ve 34 untracked dosya. Bu sayılar yalnız bu görevde yazılan dosyalar
değildir. Commit/push/merge/rebase/reset/add yapılmamıştır.
Son yerel kanıt: logs/dev-preparation-final-integrity.json.

## 9. Sonar bakım düzeltmeleri — takip çalışması

Bu takip çalışması yalnız dört bakım bulgusuyla ilişkili iki test kaynağını
değiştirir. Yukarıdaki ilk taramanın FAIL kaydı tarihsel kanıt olarak korunur;
güncel son kalite durumu bu bölümde raporlanır.

### Kaynak düzeltmeleri

- **TAMAMLANDI — java:S9142 (3 bulgu):** MigrationArchitectureTest içinde
  ALTER_TABLE, VERSION_SEPARATOR ve MANIFEST_SEPARATOR static final Pattern olarak
  bir kez derlenir. Döngülerde Pattern.compile/String.split yerine bu sabitlerin
  matcher/split işlemleri kullanılır. ALTER ifadesi aynen `\balter\s+table\b`,
  CASE_INSENSITIVE ile korunur; boşluk/satır sonu ve harf duyarsızlığı değişmez.
  Separator ifadeleri ve varsayılan split davranışı aynıdır. SHA256SUMS ve eski SQL
  fixture'ları değiştirilmez.
- **TAMAMLANDI — java:S5778 (1 bulgu):** MigrationTransferTest.assertRejected
  içinde receipt.get() sonucu failedReceipt yerel değişkenine lambda öncesinde
  alınır. Assertion lambda'sı yalnız requireReady çağrısını içerir. Beklenen
  IllegalStateException ve başarısız aktarımın readiness reddi aynen korunur.

İş mantığı, entity, API, aktif SQL, tarihsel fixture, manifest, migration numarası,
POM, Compose ve ENV değişmedi. Issue bastırma, false-positive işaretleme, kalite
eşiği veya coverage/test dışlaması yapılmadı. Mevcut kaynak değişiklikleri korunur.

### Güncel testler

- **TAMAMLANDI:** MigrationArchitectureTest 4, MigrationTransferTest 8,
  MigrationTransferApplicationTest 1, LocalizationMigrationTest 1,
  PhaseSixMigrationTest 1: **15/15 PASS**, 0 failure/error/skipped.
  Kanıt: logs/sonar-maintenance-targeted.log.
- **TAMAMLANDI:** `.\pre-push\pre-push.cmd game-service` Maven clean verify
  çalıştırdı; **243/243 PASS**, 0 failure/error/skipped, **%98,74 LINE**.
  Sonuç BACKEND TEST CHECK PASSED; full çalıştırılmadığından SAFE TO COMMIT iddiası yoktur.
  Kanıt: logs/sonar-maintenance-game-service.log.
- Aynı testler sıralı V31–V40/10 dosya/0 ALTER, 13 tarihi SHA, şema eşitliği,
  seed, bütün tablo aktarımı/rollback, audit/FK ve gerçek HTTP başlangıcını korur.

### İzole Sonar ve son bütünlük

**TAMAMLANDI:** aynı güvenli yöntemle 17 sentetik PostgreSQL DB'si oluşturuldu.
Maven başlamadan önce bağlantı guard'ı PASS; Compose test model değişimi bellek
içinde doğrulandı. Taze test/JaCoCo raporları ve gerçek scanner kullanıldı.
DEV PostgreSQL bağlantısı veya gerçek yedek kullanılmadı. Öncelikli Spring/JVM
datasource override'ı bulunmadığı ayrıca değerleri yazdırılmadan kontrol edildi.

- Tüm backend Maven clean verify: **260/260 PASS**, 0 failure/error/skipped.
- Game Service: **243/243**, LINE **%98,74** (1490 covered / 19 missed).
- Birleşik yerel JaCoCo LINE: **%96,60** (1507 covered / 53 missed).
- SonarQube Access, Backend Reports, Backend Analysis, Quality Gate Backend,
  Overall Targets Backend: **tamamı PASS**, runner **SONAR SCAN PASSED**.
- Gerçek analiz gate durumu OK; Sonar way politika kontrolü VERIFIED.
- Sonar genel/yeni security, reliability, maintainability rating'leri **A**.
- Overall coverage **%90,7**, new coverage **%91,8**, duplication **%0**.
- Sonar API bağımsız son kontrolü: **çözülmemiş issue=0, hotspot=0, açık hotspot=0**.
  Dört bulgu kaynak düzeltmesiyle kalktı; manuel issue/false-positive işlemi yapılmadı.
- Coverage/duplication/new-issue eşikleri, security ve test kontrolleri korunur.
- Geçici kalite PostgreSQL konteyneri ve ağı temizlendi.

**Korunma kontrolleri:** düzeltme sonrasında kaydedilen tüm Game Service src
dosyalarının hash'leri test/tarama sonunda aynı; aktif SQL ve tarihsel fixture'lar
değişmedi. Mevcut **21** DEV/Sonar konteynerinin ID/image/StartedAt/mount bilgileri
aynı. Korumalı dump SHA-256 ve mtime aynı; dump/manifest değiştirilmedi veya silinmedi.
Bu takipte DEV'e SQL çalıştırılmadı; canlı satır hash'leri yeniden ölçüldü iddiası yoktur.

**Git:** feature/game-service, staged 0; önceki çalışmalar dahil 7 modified,
11 deleted ve 34 untracked. Bu görevde yalnız MigrationArchitectureTest.java,
MigrationTransferTest.java test kaynakları ve ilgili belge sonuçları değiştirildi.
Önceki migration düzenlemeleri korundu; git diff --check PASS, index'e dokunulmadı.
Commit/push/merge/rebase/reset/add çalıştırılmadı.

Kanıtlar: logs/sonar-maintenance-targeted.log, sonar-maintenance-game-service.log,
sonar-maintenance-isolated.log, sonar-maintenance-findings.json,
sonar-maintenance-final-integrity.json. Önceki FAIL analiz metadata'sı
logs/sonar-maintenance-previous-result.json içinde ayrıca korundu.

**[Tarihsel — §10 ile geçersiz] Gerçek DEV geçişi BEKLİYOR:** kalite engeli kaldırıldı; §6'daki hedef DB oluşturma,
yazma kesintisi, güncel son yedek, gerçek yeni zincire aktarım, DB_GAME_NAME değişikliği,
yalnız Game Service imaj/konteyner güncellemesi ve canlı Gateway doğrulaması ayrı
açık onay gerektirir. Bu çalışma bunların hiçbirini başlatmaz. Yedek hazırlığı ve
sentetik taşıma kanıtı gerçek cutover yapılmış anlamına gelmez.

## 10. UYGULANAN DEV KARARI — `ltz-dev` ortamının sıfırlanması (2026-10-21)

Kullanıcı, eski DEV verilerinin korunmasına ihtiyaç olmadığını ve `ltz-dev` Compose
projesinin tamamının `down -v` ile sıfırlanıp mevcut kaynakla yeniden build/up
edilmesini açıkça onayladı. §6'daki `game_db_v31v40` planı bu nedenle seçilmedi;
`DB_GAME_NAME=game_db` olarak kaldı. Hiçbir ENV dosyası okunmadı veya değiştirilmedi.

### Yapılanlar

- Compose dosyası `docker-compose.yml`, proje `ltz-dev`, profil `dev` (`config --quiet` OK).
  `ltz-dev` etiketli tek volume `ltz-dev_postgres_data` ve `ltz-dev_ltz_network` ağı vardı;
  paylaşılan/harici volume yoktu.
- `docker compose -p ltz-dev down -v --remove-orphans`: konteyner, volume ve ağ silindi.
- `docker compose -p ltz-dev up -d --build`: PostgreSQL 17 temiz volume ile kuruldu,
  `postgres/init` betiği 17 DB'yi oluşturdu, 18 servis mevcut kaynakla build/başlatıldı.
- Flyway repair/baseline/history müdahalesi yapılmadı; eski history geri yüklenmedi.

### Game Service doğrulaması (`game_db`)

- Flyway history: **10 kayıt, V31–V40, success=true**; V171–V173 yok.
  İlk başlangıç: boş şemadan 10 migration, "now at version v40".
- Game Service yeniden başlatıldı: "Successfully validated 10 migrations", "Schema
  public is up to date. No migration necessary"; history hâlâ 10 kayıt.
- 21 uygulama tablosu; 2 fonksiyon (`reject_game_identity_audit_mutation`,
  `reject_game_admin_audit_mutation`) ve 2 kullanıcı trigger'ı. İndeks/constraint sayıları
  (48/102) beklenen 46/101 değerinin üstüne Flyway'in kendi history tablosunu (2 indeks, 1 PK) ekler.
- `game_provider_sync_state`: STEAM başlangıç kaydı (version=0, failure_count=0).
- `ddl-auto=validate` ile uygulama iki kez açıldı (Hibernate validation başarılı).

### Diğer servisler

18 servisin tamamı (auth, user, mail, game, library, review, social, messaging, lfp,
community, content, event, hardware, recommendation, performance, admin, notification,
api-gateway) `running`, restart=0, `/actuator/health` 200 UP, startup loglarında ERROR/Exception yok.
Game dışı 16 servisin `flyway_schema_history` tablosu oluşmuş ama 0 kayıt içeriyor: bu servislerde
henüz migration dosyası yok (migration'lar ilgili sahiplerin görevidir; kaynakları değiştirilmedi).
Flyway çıktıları "Schema public is up to date. No migration necessary".

### API ve Gateway (Game Service :5084, Gateway :5080 — sonuçlar aynı)

- 200: `/actuator/health`, `GET /games`, `/games/search`, `/games/popular` (katalog boş: `items=[]`,
  beklenen sonuç), `POST /games/batch` (bilinmeyen kimlik `unavailable` içinde).
- 404: olmayan oyun için `/{gameId}/card` ve `/games/{gameId}/modules/ACHIEVEMENTS`.
- 401: `POST/GET /feedback`, `/admin/games/{id}/achievements`, `/developer/games/{id}/feedback` (anonim).
- **NOT VERIFIED:** gerçek JWT gerektiren tüm yetkili işlemler; güvenlik bypass'ı uygulanmadı.
  Dolu katalog yanıtları canlıda doğrulanamaz (katalog boş); izole testlerde doğrulanmıştır.

### Korunanlar

- `ltz-sonar` projesi (SonarQube + sonar-db) kimlik ve StartedAt değerleriyle değişmedi.
- Git dışı Game Service yedeği silinmedi; SHA-256 `2cadc46e…863a` aynı.
- Kaynak kod değişmediği için Sonar yeniden taranmadı; önceki Quality Gate PASS korunur.
- Git: commit/push/add/merge/rebase/reset yapılmadı; staged dosya yok.
