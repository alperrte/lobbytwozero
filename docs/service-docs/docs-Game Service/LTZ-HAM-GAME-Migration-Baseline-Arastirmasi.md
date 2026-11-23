# Game Service — Migration optimizasyonu ve baseline araştırması

> Tarihsel araştırma kaydıdır. Kullanıcı daha sonra B173 önerisi yerine gerçek V
> kaynaklarının yeniden düzenlenmesini açıkça istedi. Güncel uygulama ve DEV geçiş
> sınırları [yeniden yapılandırma raporundadır](LTZ-HAM-GAME-Migration-Yeniden-Yapilandirma.md).
> Aşağıdaki öneriler bu son kararın yerine geçmez. Bu rapordaki V numaraları ve "mevcut DEV"
> ifadeleri eski 13 dosyalı zincire aittir; 2026-10-21'de `ltz-dev` sıfırlanmış ve DEV `game_db`
> yeni V31–V40 zinciriyle kurulmuştur. §11'deki V174–V190 önerisi/rezervasyonu sonradan
> kaldırılmıştır; geçerli karar docs/decisions/migrations.md içindedir.

Tarih: 2026-10-21 · Sorumlu: Hamza · Branch: feature/game-service
Durum: araştırma tamamlandı; gerçek migration düzenlemesi için kullanıcı kararı bekleniyor.

## 1. Sonuç ve öneri

**En güvenli sadeleştirme: mevcut 13 V dosyasını koruyarak, ayrıca onaylanacak B173 ile yeni kurulum yolunu sadeleştirmek (Yöntem B).**
Bu araştırma için hazırlanan geçici snapshot, tüm final kolon ve constraint'leri CREATE TABLE içinde toplar;
21 tabloyu sıfır ALTER TABLE ile kurar. Gerçek migration klasörüne B dosyası eklenmedi.

İki hedef aynı şey değildir: B173 yeni boş kurulumda çalıştırılan dosyayı 13'ten 1'e indirir;
repodaki dosya sayısını ise 13'ten 14'e çıkarır. V36 ve diğer tarihsel V dosyalarını fiziksel
olarak azaltmaz veya mevcut DEV geçmişini sıkıştırmaz. Hem uygulanmış geçmişi aynen koruyup
hem o geçmişin doğruladığı kaynakları silmek bu projede güvenli bir varsayım değildir.

**Önemli koşul:** Flyway 12.4.0 üzerinde boş DB'de B173 görünürken target(40) ve target(171)
denemeleri de B173'ü uyguladı. Dolayısıyla mevcut tarihsel migration testleri B dosyasını
GÖRMEYEN, yalnız V dosyalarını içeren bir lokasyonla çalışacak şekilde ayrıca uyarlanmalıdır.
Sadece B173 dosyasını ekleyip testlerin anlamının aynı kaldığını kabul etmek doğru değildir.

Mevcut V173 DEV geçmişi B173 ile sıfır migration çalıştırdı; geçmiş ve örnek kayıt korundu.
V40 geçmişi olan ortam B173'ü atlayıp V171/V172/V173 ile yükseldi. Geçici V174 hem zincirle
hem B173 ile kurulmuş ortamda uygulandı. Bunlar yalnız izole PostgreSQL deneyleridir.

## 2. Başlangıç, sürümler ve DEV incelemesi

- Branch feature/game-service; başlangıç çalışma ağacı temiz; HEAD 491f3bd (Faz 6).
- Game Service'te 13 versioned dosya: V31–V40 ve V171–V173.
- DEV geçmişinde aynı 13 sürüm başarılı. Yerel dosyaların Flyway uyumlu CRC32 değerleriyle
  DEV checksum'ları 13/13 eşleşti. DEV katalogda oyun 0, provider sync satırı 1.
- Spring Boot parent 4.1.1. Paketlenmiş uygulama ve test runtime JAR'ında flyway-core ve
  flyway-database-postgresql **12.4.0**; Hibernate **7.4.5.Final** doğrulandı.
  Maven cache'inde başka Flyway sürümleri olması proje sürümü olarak yorumlanmadı.
- PostgreSQL 17: izole deney postgres:17-alpine ile gerçek PostgreSQL üzerinde koştu.
- Yapılandırma: spring.flyway.enabled=true, locations=classpath:db/migration,
  Hibernate ddl-auto=validate. Özel B prefix veya baselineOnMigrate etkinleştirmesi yok.
- DEV yalnız docker exec + psql SELECT ile okundu; profile ENV içerikleri okunmadı/yazılmadı.

## 3. Migration envanteri ve bağımlılıklar

### V31 — create_game_metadata
7 tablo: games, catalog_terms, game_terms, game_developers, game_publishers, game_media, game_system_requirements.
UUID PK'ler, servis içi FK'ler, CHECK/unique ve ilişki indeksleri kurulur.
Sonraki katalog/geliştirici/feedback tablolarının games ve metadata bağımlılığını oluşturur.

### V32 — add_game_view_and_external_references
Games'e origin, metadata_completeness, status ekleyen 3 ALTER; game_external_refs tablosu,
(provider,external_id) unique ve game indeksi. V31 games'e bağlıdır.

### V33 — create_local_catalog_index
Games.normalized_title varchar(256) -> varchar(1024); pattern_ops arama indeksi.
Game_provider_sync_state tablosu ve STEAM için sabit UUID'li başlangıç INSERT'i.
Bu referans satırı salt şema snapshot'ında unutulmamalıdır; katalog akışları ona dayanır.

### V34 — audit_verified_game_identity_changes
Game_external_refs için Steam pozitif uint32 kimlik CHECK'i; game_identity_audits tablosu,
FK'ler, indeks, append-only reject fonksiyonu ve trigger. V31/V32 gerekir.

### V35 — classify_catalog_item_types
Games.game_type UNKNOWN varsayılanı ve CHECK; aktif türe göre partial pattern_ops indeksi.
V32 status ve V33 genişletilmiş normalized_title üzerinde çalışır.

### V36 — add_catalog_retry_and_detail_freshness
2 ALTER, toplam 9 yeni kolon. games V31'de; game_provider_sync_state V33'te doğmuştur.
Ayrı tablo veya açık INSERT/UPDATE/DELETE yoktur. NOT NULL/default 0 kolonları eski
satırlarda başarısızlık sayısını 0 olarak tanımlar; bu veri etkisi atlanmamalıdır.

### V37 — add_catalog_discovery_metadata
Games'e early_access, editor_rank, popularity_updated_at, editor_actor_id, editor_evidence;
deferrable initially deferred uq_games_editor_rank. game_languages ve dil indeksi;
partial keşif/title/release indeksleri ve cover sıralama indeksi. V31/V33/V35/V36'ya bağlıdır.

### V38 — add_admin_game_management
Game_field_overrides ve game_admin_audits; alan/rol/eylem CHECK'leri, unique/FK/indeks,
append-only admin audit fonksiyon ve trigger'ı. V171 override CHECK'ini genişletir.

### V39 — create_game_developer_grants
Game_developer_grants, granted/revoked actor REF'leri, revoked_at/revoked_by tutarlılık
CHECK'i, aktif yetkiye partial unique ve kullanıcı indeksi. Kimlik verisi kopyalanmaz.

### V40 — create_game_developer_content
Game_developer_contents, game_developer_media, game_update_notes; durum/URL/metin
CHECK'leri ve tarihe göre indeksler. Katalog metadata'sından farklı veri sahipliğidir.

### V171 — add_localization_and_metadata_provenance
Games.metadata_language ve language_data_state; game_language_supports ve
game_metadata_provenance. V38 override CHECK'ini drop/add ile LOCALIZATION içerecek
şekilde değiştirir. Önce eski CHECK'i oluşturup sonra snapshot'ta yeniden düşürmek gerekmez.

### V172 — create_game_achievement_definitions
Game_achievement_definitions, game FK CASCADE, unique(game_id,code), görünürlük/sıralama indeksi.
Kişisel başarım ilerlemesi değildir; başka metadata tablosuna zorla birleştirilmemelidir.

### V173 — create_game_developer_feedback
Game_developer_feedback, game FK RESTRICT, author REF, type/status CHECK'leri,
yazar/oyun sayfalama indeksleri. Özel feedback verisi Review incelemesiyle birleşmez.

**Toplam:** 21 uygulama tablosu; 12 ALTER TABLE ifadesi. Açık veri DML'si V33'teki tek
referans INSERT'idir; zincirde açık UPDATE/DELETE veya veri taşıma INSERT SELECT yoktur.
Audit trigger fonksiyonlarının gövdesindeki RAISE ifadeleri veri DML'si sayılmadı.
Fonksiyon, trigger ve seed korunmadan yalnız tablo karşılaştırması yeterli değildir.

## 4. Birleştirme adayları ve korunacak işlemler

Buradaki adaylar yeni snapshot CREATE tanımları içindir; uygulanmış V dosyalarına
bugün taşınacak düzenleme talimatı değildir. Ek A dört birleşik CREATE tanımını gösterir.

- **V32 -> V31 games:** origin, metadata_completeness, status; NOT NULL/default ve üç CHECK.
  V32 external_refs yaratımı ve unique/FK/indeksi ayrı mantıksal blok olarak korunur.
  V32 tamamen kaldırılırken external_refs unutulursa referans sistemi kaybolur.
- **V33 -> V31 games:** normalized_title ilk tanımda varchar(1024) olabilir. pattern_ops
  indeksi ayrıca korunur. Sync tablosu ve STEAM seed'i yalnız tip değişikliğiyle birlikte silinemez.
- **V34 -> V32 game_external_refs:** ck_game_external_refs_steam_appid CREATE'e taşınabilir.
  Audit tablosu, FK'leri, indeks, fonksiyon ve trigger bağımsız işlevdir; aynı snapshot'ta yaşamalıdır.
- **V35 -> V31 games:** game_type, UNKNOWN varsayılanı ve CHECK ilk tanıma girer.
  Aktif tür/title partial indeksi korunur; status ve normalized_title nihai tipleri hazır olmalıdır.
- **V36 -> V31 games / V33 sync_state:** dokuz kolon, iki default 0 ve iki sayısal CHECK
  ilk CREATE'lere alınabilir. Nihai snapshot'ta V36 için ayrı ALTER bloğu kalmaz.
  Eski ortamlarda V36 normal yükseltmedir; kaldırmak checksum/missing migration uyuşmazlığı yaratır.
- **V37 -> V31 games:** beş kolon ve uq_games_editor_rank; DEFERRABLE INITIALLY DEFERRED
  niteliği mutlaka korunur. game_languages ve tüm indeksler ayrıca korunur. Sıradan unique ile
  değiştirmek editör sıralaması transaction semantiğini değiştirebilir.
- **V171 -> V31 games:** metadata_language, language_data_state default UNKNOWN ve CHECK.
  V38 game_field_overrides CREATE'inde final allowlist doğrudan LOCALIZATION içerebilir;
  snapshot'ta drop/add çifti gerekmez. Destek/provenance tabloları ve doğrulama zamanı CHECK'i kalır.

V31/V32/V33/V34/V37/V38/V39/V40/V171/V172/V173'ün oluşturduğu farklı tablolar veri
sahipliği ve FK'leri nedeniyle tek fiziksel tabloya birleştirilmez. Tek baseline dosyası
çok sayıda bağımsız CREATE içerir; dosya birleştirmek tablo birleştirmek değildir.

## 5. V36 için somut öneri

Games tarafındaki description varchar(16000), release_date date, details_fetched_at,
details_expires_at, details_next_attempt_at (timestamptz), details_failure_count integer
NOT NULL DEFAULT 0 CHECK >=0 ve details_last_error_code varchar(64), birleşik games
CREATE'ine alınır. Sync tarafındaki failure_count integer NOT NULL DEFAULT 0 CHECK >=0
ve next_attempt_at timestamptz birleşik sync CREATE'ine alınır.

V36 dosyası V zincirinde kalır. B173 yeni DB'de onu zaten çalıştırmaz; mevcut DB'ye
kolonları yeniden eklemeye kalkmaz. Snapshot'ta son şemanın kolon sırası dahi korundu.
Yalnız V36'yı erkene taşımak fiziksel kolon sırasını değiştirebilir; SELECT * veya
row_to_json kullanan araçlar etkilenebilir. Buradaki deney tüm final CREATE'i aynı sırada kurdu.
Ek A'da games ve sync tanımları vardır; geçici B173'te hiçbir ALTER TABLE kalmamıştır.

## 6. B migration, baseline komutu ve baselineOnMigrate ayrımı

B173 SQL, migrate sırasında yeni ortamı kuran snapshot'tır; V173 ile aynı sürümü
paylaşabilir. Var olan geçmişte yükseltme değildir. Referans:
[Redgate baseline migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations).

`flyway baseline`, mevcut şemayı bir sürümden başlıyor kabul eden geçmiş işaretlemesidir;
B173 CREATE SQL'ini uygulamakla aynı işlem değildir. Gerçek şemanın snapshot'a eşitliğini
bu komut kendiliğinden kanıtlamaz. Bu araştırmada komut hiç kullanılmadı.
[Redgate baseline komutu](https://documentation.red-gate.com/flyway/reference/commands/baseline).

`baselineOnMigrate`, geçmişsiz dolu şemada otomatik baseline işaretlemesidir; varsayılanı
false. Yanlış DB'ye bağlanma güvenlik kontrolünü zayıflatabilir. Açılması önerilmez.
B173 deneyinde false bırakıldı; dolu/geçmişsiz DB beklenen şekilde reddedildi.
[Redgate baselineOnMigrate](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

Mekanizmanın resmi örneği:
[Redgate baseline migration öğreticisi](https://documentation.red-gate.com/flyway/reference/tutorials/tutorial-baseline-migrations).
Destek yalnız belgeye dayanılarak varsayılmadı; projenin lisans/konfigürasyon eklenmemiş
Flyway 12.4.0 runtime'ında B173 gerçekten SQL_BASELINE olarak uygulandı.

## 7. A, B ve üçüncü alternatif

### Yöntem A — Geçmiş zinciri yeniden yazmak
Yeni kurulum tek dosyayla eşdeğer şema oluşturabilir; izole tek V173 snapshot denemesi bunu doğruladı.
Dosya sayısı örnekte 13 -> 1. Ancak mevcut 13 satırlık geçmiş bu dosya setiyle validate
edilemedi; beklenen FlywayValidateException alındı. Bu DEV arızası değildir, geçici DB'deki
olumsuz doğrulama testidir. Tam V36-only rewrite ayrı denenmedi; test uç örnek konsolidasyondur.

Eski CREATE'e kolon eklemek eski checksum'ı değiştirir; V36'yı silmek geçmişe ait kaynağı
kaybettirir. Repair SQL'i yeniden uygulamaz; yeni dosyaya uydurulan checksum şema eşitliğinin
kanıtı değildir. Baseline komutu uygulanmış geçmişi koruyarak yeniden yazma yöntemi değildir.
A yaklaşımı ancak tüm ortamlar, yedek/restore planı, tarihsel kayıt politikası ve koordineli
geçiş açıkça onaylanırsa ayrıca tasarlanabilir. Bu görevde geçmiş uyarlaması veya veri
sıfırlama denenmedi; risksiz yapılabildiği iddia edilmez. Mevcut sınırlar altında önerilmez.

### Yöntem B — Eski V'ler + yeni kurulum snapshot'ı
Mevcut ortamın geçmişi aynı kalır; boş kurulum 1 B SQL çalıştırır. Repo 14 dosya olur.
Veri taşıma/reset veya repair gerekmez. Ancak schema/seed tanımı iki yolda temsil edilir;
kalıcı eşdeğerlik testleri ve snapshot disiplinine ihtiyaç vardır. B173 de bir ortamda
uygulandıktan sonra değişmez kabul edilmelidir. Eski V'leri kaldırma izni sağlamaz.
DEV'deki migrate=0 sonucu tek başına snapshot'ın doğru olduğunun kanıtı değildir; yeni
kurulum + eski zincir + gelecek migration karşılaştırması zorunludur.

### Yöntem C — Şimdilik 13 dosyayı korumak, yalnız gelecekteki düzeni iyileştirmek
En düşük değişiklik riski; repo 13, yeni kurulum 13 migration. Aynı yeni özellikteki ilgili
DDL tek yeni V dosyasında toplanabilir; mevcut tabloya sonradan eklenen ALTER yükseltme
olduğu için normaldir. Sayısal startup performans kazanımı bu araştırmada ölçülmedi;
13 dosyanın performans sorunu olduğu söylenemez. B bakım maliyeti istenmiyorsa güvenli alternatiftir.

## 8. İzole PostgreSQL deneyleri

Tek JUnit araştırma testi içinde aşağıdaki senaryolar çalıştırıldı; bunlar ayrı ayrı
12 JUnit test diye sayılmamalıdır. Son Maven sonucu 1 test, 0 failure/error/skipped.

- Referans: boş DB + 13 V migration -> V173, PASS.
- B yolu: boş DB + eski V'ler + B173 -> yalnız 1 SQL_BASELINE; PASS.
- Snapshot'ta 21 CREATE TABLE, 0 ALTER TABLE; seed dahil.
- A yolu: boş DB + tek yeniden yazılmış V173 -> referansla eşdeğer şema; PASS.
- Eski V173 geçmişi + B173 -> migrate=0, geçmiş ve eklenmiş örnek oyun korunur; PASS.
- Aynı eski geçmiş + yeniden yazılmış A seti -> validate reddi; beklenen negatif test PASS.
- Önceden V40'a kurulmuş DB + B173 ve eski V'ler -> V171/V172/V173 (3 dosya), B yok; PASS.
- Dolu, geçmişsiz DB + baselineOnMigrate=false -> reddedilir; beklenen negatif test PASS.
- Referans ve B173 üzerinde gerçek entity'lerle Hibernate validate -> PASS.
- Boş DB + B173 görünür + target(40) -> 1 B173, sürüm 173; gözlenen uyumluluk kısıtı.
- Boş DB + B173 görünür + target(171) -> 1 B173, sürüm 173; gözlenen uyumluluk kısıtı.
- Geçici V174 probe -> hem referans hem B ortamında 1 dosya uygulanır; son şemalar eşit, PASS.

V174 probe yalnız geçici JUnit lokasyonundaydı; gerçek numara tahsis edilmedi veya dosya eklenmedi.
Hibernate kontrolü Boot ile uyumlu PhysicalNamingStrategySnakeCaseImpl kullanan programatik
SessionFactory validate işlemidir. Tam Spring uygulaması/B173 Docker imajı bu araştırmada
başlatılmadı; üretim güvenliği veya yeni deployment doğrulanmış sayılmaz.
İlk testte dahili VersionPrinter erişimi, sonraki denemede manuel Hibernate naming eksikliği
düzeltildi. Nihai başarılı log her iki sorunun giderilmiş halini gösterir.

## 9. Şema eşdeğerliği kapsamı

Flyway history tablosu kasıtlı olarak karşılaştırma dışıdır: B yolunda tek SQL_BASELINE,
V yolunda 13 SQL satırı olması beklenir. Referans/snapshot karşılaştırmasında:

- 21 tablo; 170 kolon: sıra, isim, SQL/UDT türü, uzunluk/precision/scale, nullable,
  default, collation, identity/generated bilgileri eşleşti.
- 101 constraint: PK/FK/unique/CHECK tanımı, ad, deferrable/deferred/validated özellikleri eşleşti.
- 46 indeks: ad ve pg_getindexdef eşdeğeri tanım; partial koşul, pattern_ops ve sıralama dahil eşleşti.
- 2 fonksiyon + 2 kullanıcı trigger'ı: tanımları eşleşti; append-only mekanizması korundu.
- Tablo persistence/RLS flag'leri ve referans STEAM sync satırı eşleşti.
- Geçici V174 sonrasında iki yol yine eşleşti; mevcut entity mappings validate geçti.

Owner/grant karşılaştırması kapsam dışıdır: ayrı production DB rolleri/grant sistemi bu
migration zincirinde tanımlı değildir. Extensions, gerçek ekip verileri, üretim yükü,
backup restore veya bütün eski uygulama sürümleriyle uyumluluk test edilmedi.

## 10. DEV ve ekip ortamları

DEV üzerinde yalnız SELECT yapıldı. Önce/sonra 13 geçmiş satırı, checksum, success,
oyun sayısı ve sync satırı sayısı aynı kaldı. Docker build/up veya volume işlemi yoktur.
Mevcut V173 ekip ortamı normal V history'sini korur; V40 gibi gerideki ortama kalan
V'ler gerekir. Eski dosyalar bu nedenle yerinde kalmalıdır. Flyway kullanmamış dolu
bir ekip DB'si otomatik güvenli baseline kabul edilmez; ayrıca incelenmelidir.

## 11. V174–V190 ve uygulanmadan önce onaylar

- Gerçek yeni schema değişikliği V174'ten itibaren boş numarayla normal V migration olur.
  B173 mevcut DEV için upgrade değildir; V174'ü yalnız snapshot'a yazarak atlamak yanlıştır.
- B173 şu anki 173 durumunu temsil eder; B190 diye adlandırıp ara sürümleri atlatma yapılmaz.
- B Game Service'in aynı lokal db/migration kaynağında olmalı; ortak/global lokasyona veya
  başka servise konmamalıdır. Diğer servislerin tahsisleri ve yapılandırmaları değişmez.
- Gerçek B173 eklenmesi, snapshot kapsamı/seed/check/trigger tanımları ve mevcut V'lerin
  saklanması için kullanıcı onayı gerekir. Bu rapor uygulama onayı sayılmaz.
- LocalizationMigrationTest target(40) ve PhaseSixMigrationTest target(171) mevcut kaynakları
  incelendi. İlki artık eski şemadan yükseltmeyi gerçekten sınamayabilir; ikincisinin 2 V
  satırı beklentisi tek B satırıyla bozulur. Tarihsel testlere yalnız V dosyalı geçici
  lokasyon, ayrıca B-vs-chain/future-V/Hibernate karşılaştırma testleri eklenmesi onaylanmalıdır.
- Kalıcı kod/real migration eklenince ilgili tam regresyon/kalite kontrolleri tekrar çalıştırılmalıdır.
- DEV dağıtımı ayrıca açık onay gerektirir; bu görevde uygulanmaz. B yeni kurulumda kullanıldıktan
  sonra checksum'ı korunur; yeni snapshot gerekiyorsa yeni mevcut sürüme uygun ayrı B tasarlanır.

## 12. Kanıtlar ve çalışma sınırları

Kalıcı sonuç bu Markdown raporudur. Geçici araştırma testi/SQL'i gerçek migration klasörüne
bırakılmadı; orijinal 13 dosya SHA-256 ile önce/sonra doğrulandı. Pre-push ve SonarQube
tekrarlanmadı; önceki kalite sonucu araştırma senaryolarının sonucuymuş gibi sunulmadı.
Commit/push/merge/rebase/reset/add yapılmadı; rapor sunulduktan sonra durulur.

Yerel (Git dışı) kanıtlar: logs/baseline-dev-before.json, baseline-dev-after.json,
baseline-research-test-final.log; logs/game-baseline-research/results.txt,
schema-reference.txt, schema-baseline.txt, inventory.json, geçici B173 SQL'i ve araştırma testinin kopyası.
Bunlar inceleme artefaktıdır; gerçek kaynak veya onaylı baseline değildir.

## Ek A — Geçici snapshot'taki birleşik CREATE adayları

Aşağıdaki dört tanım izole olarak karşılaştırılan snapshot'tan alınmıştır. Diğer 17
CREATE, tüm indeksler, fonksiyonlar, trigger'lar ve V33 seed INSERT'i de snapshot'ın
parçasıdır; bu dört blok tek başına tam kurulum dosyası değildir.

### games

```sql
create table games (
    id uuid primary key,
    title varchar(256) not null check (length(trim(title)) > 0),
    normalized_title varchar(1024) not null check (length(trim(normalized_title)) > 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    origin varchar(32) not null default 'INTERNAL'
    check (origin in ('INTERNAL','EXTERNAL')),
    metadata_completeness varchar(32) not null default 'INDEX_ONLY'
    check (metadata_completeness in ('INDEX_ONLY','PARTIAL','FULL')),
    status varchar(32) not null default 'ACTIVE'
    check (status in ('ACTIVE','ARCHIVED','MERGED')),
    game_type varchar(32) not null default 'UNKNOWN'
    check(game_type in ('MAIN_GAME','DLC','DEMO','SOUNDTRACK','SOFTWARE','VIDEO','PACKAGE','HARDWARE','UNKNOWN')),
    description varchar(16000),
    release_date date,
    details_fetched_at timestamptz,
    details_expires_at timestamptz,
    details_next_attempt_at timestamptz,
    details_failure_count integer not null default 0 check (details_failure_count >= 0),
    details_last_error_code varchar(64),
    early_access boolean,
    editor_rank integer check (editor_rank between 1 and 100),
    popularity_updated_at timestamptz,
    editor_actor_id uuid,
    editor_evidence varchar(1000),
    constraint uq_games_editor_rank unique (editor_rank) deferrable initially deferred,
    metadata_language varchar(35),
    language_data_state varchar(16) not null default 'UNKNOWN'
        check(language_data_state in ('UNKNOWN','EMPTY','INVALID','AVAILABLE'))
);
```

### game_provider_sync_state

```sql
create table game_provider_sync_state (
    id uuid primary key,
    provider varchar(32) not null unique,
    cursor bigint not null default 0 check(cursor >= 0),
    run_mode varchar(32) check(run_mode in ('INITIAL','INCREMENTAL')),
    sync_status varchar(32) not null default 'IDLE' check(sync_status in ('IDLE','RUNNING','COMPLETE','FAILED')),
    query_since timestamptz,
    run_started_at timestamptz,
    last_successful_at timestamptz,
    processed_count bigint not null default 0 check(processed_count >= 0),
    last_error_code varchar(64),
    version bigint not null default 0,
    failure_count integer not null default 0 check (failure_count >= 0),
    next_attempt_at timestamptz
);
```

### game_external_refs

```sql
create table game_external_refs (
    id uuid primary key,
    game_id uuid not null references games(id) on delete restrict,
    provider varchar(32) not null check (provider ~ '^[A-Z][A-Z0-9_]*$'),
    external_id varchar(128) not null check (length(trim(external_id)) > 0),
    last_synced_at timestamptz,
    version bigint not null default 0,
    constraint uq_game_external_refs_provider_external_id unique(provider, external_id),
    constraint ck_game_external_refs_steam_appid check (
    provider <> 'STEAM' or case when external_id ~ '^[1-9][0-9]{0,9}$'
        then external_id::bigint <= 4294967295 else false end
)
);
```

### game_field_overrides

```sql
create table game_field_overrides (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    field varchar(32) not null check (field in ('TITLE','DESCRIPTION','RELEASE_DATE','GAME_TYPE','DEVELOPERS','PUBLISHERS','LOCALIZATION')),
    actor_id uuid not null,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_game_field_overrides_game_field unique (game_id, field)
);
```
