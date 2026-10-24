# Game Service — V31–V40 başlangıç migration mimarisi

Tarih: 2026-10-21 · Sorumlu: Hamza · Branch: feature/game-service

> **Takip sonucu:** Gerçek DEV yedeği/izole restore tamamlandı. DEV'e müdahale
> etmeyen ilk Sonar analizi dört test bakım bulgusuyla FAIL verdi; bulgular test
> kaynaklarında giderildi ve güncel izole Sonar Quality Gate PASS (260 test, issue/hotspot
> 0/0). Buradaki ilk teslimin Sonar BEKLİYOR ve takip FAIL kayıtları tarihsel durumdur.
> [Güncel hazırlık ve kalite raporu](LTZ-HAM-GAME-DEV-Yedek-Restore-Hazirlik.md) (§9).

## Güncel durum

- **TAMAMLANDI:** kesintisiz V31–V40, 10 gerçek migration dosyası, aktif kaynak ve JAR'da 0 ALTER TABLE.
- **TAMAMLANDI:** 21 tablo, 170 kolon, 101 constraint, 46 indeks, 2 fonksiyon ve 2 kullanıcı trigger'ı eşdeğerliği.
- **TAMAMLANDI:** tarihsel V40/V171 yükseltme testleri ve 13 SQL fixture için SHA-256 bütünlüğü.
- **TAMAMLANDI:** 21 tabloda 34 sentetik kayıtla izole veri aktarımı, içerik/hash ve hata testleri.
- **TAMAMLANDI:** taşınmış veritabanıyla gerçek Game Service başlangıcı, Hibernate validate, health ve HTTP API karşılaştırması.
- **TAMAMLANDI:** Game Service Maven clean verify/pre-push; 243 test, 0 failure/error/skipped, %98,74 satır kapsamı.
- **BAŞARISIZ (ilk takip taraması, tarihsel):** izole Sonar Quality Gate dört test bakım bulgusuyla FAIL; 260 test PASS.
- **TAMAMLANDI (takip çalışması):** gerçek DEV yedek ve ağsız restore provası.
- **TAMAMLANDI (kalite takibi):** dört bakım bulgusu test kaynaklarında giderildi; izole Sonar Quality Gate PASS, issue/hotspot 0/0.
- **TAMAMLANDI (2026-10-21, güncel DEV durumu):** veri korumalı taşıma yerine `ltz-dev` ortamı
  `down -v` ile sıfırlandı ve mevcut kaynakla temiz kuruldu. DEV `game_db`: V31–V40, 10 başarılı
  Flyway kaydı, V171–V173 yok, 21 tablo, STEAM kaydı var; Hibernate/Flyway validate ve yeniden
  başlatmada "No migration necessary". `game_db_v31v40` oluşturulmadı. Ayrıntı: yedek/restore raporu §10.
- İlk migration tesliminin testlerinde başarısız kontrol yoktu; takipteki Sonar FAIL ayrıca raporlanmıştır.

## Karar ve sınırlar

Kullanıcı önce bütün ALTER işlemlerinin ilgili CREATE tanımlarına alınmasını,
ardından önceki 10 dosyalı zincirin kesintisiz V31–V40 olarak yeniden
numaralandırılmasını açıkça onayladı. Bu iki adım bu çalışma için uygulanmış
migration değişmezliği kuralının önüne geçen açık istisnalardır.
İlk düzenlemedeki “numaralar tekrar kullanılmayacak” kararı, son açık eşleştirme
talimatıyla yalnız aktif başlangıç zinciri için değiştirilmiştir; tarihsel anlam gizlenmez.

**[Tarihsel — 2026-10-21'de DEV sıfırlanarak çözüldü] Eski DEV V31–V40 + V171–V173 geçmişindeydi. Yeni V35–V40 aynı numaralı
eski kayıtlarla aynı işlemleri temsil etmez. Yeni kaynaklar mevcut DEV DB üzerinde
başlatılamaz.** B migration, baselineOnMigrate, repair veya validate bypass eklenmedi.
DEV, ENV, Docker imajı/konteynerleri ve volume'leri değiştirilmedi. Diğer servislerin
koduna/veritabanına müdahale edilmedi. Commit/push/merge/rebase/reset/add yapılmadı.

## Nihai on dosya ve birebir eşleştirme

Aşağıdaki kaynak eşleştirmesi ilk yeniden yapılandırma sonrasındaki 10 dosyalı
zinciri esas alır; tarihsel 13 SQL fixture'ı yeniden numaralandırmaz.

1. **V31 -> V31__create_game_metadata.sql:** games ve ilişkili katalog metadata'sı;
   catalog_terms, game_terms, game_developers, game_publishers, game_media,
   game_system_requirements, game_languages. Nihai oyun kolonları ve keşif indeksleri.
2. **V32 -> V32__create_game_external_references.sql:** game_external_refs,
   provider/externalId unique, nihai Steam AppID CHECK ve oyun indeksi.
3. **V33 -> V33__create_game_provider_sync_state.sql:** cursor/checkpoint,
   retry/backoff durumu ve sabit UUID'li STEAM başlangıç kaydı.
4. **V34 -> V34__audit_verified_game_identity_changes.sql:** game_identity_audits,
   indeks, append-only fonksiyon ve trigger.
5. **V38 -> V35__create_admin_game_management.sql:** game_field_overrides,
   LOCALIZATION dahil nihai field CHECK, game_admin_audits ve append-only mekanizması.
6. **V39 -> V36__create_game_developer_grants.sql:** oyun bazlı grant/revoke,
   aktif grant partial unique ve kullanıcı indeksi.
7. **V40 -> V37__create_game_developer_content.sql:** geliştirici açıklaması,
   medya, güncelleme notları ve indeksleri; üç ilişkili tablo birlikte kalır.
8. **V171 -> V38__create_localization_and_metadata_provenance.sql:**
   game_language_supports ve game_metadata_provenance.
9. **V172 -> V39__create_game_achievement_definitions.sql:** tanımlar ve public sayfalama indeksi.
10. **V173 -> V40__create_game_developer_feedback.sql:** özel geri bildirim ve yazar/oyun filtre indeksleri.

Yeniden adlandırmada SQL byte içerikleri korunmuştur; çakışan eski adlar bir bütün
olarak okunup sonra hedef isimlere yazılmıştır. Tablolar farklı domain'ler arasında
yeniden dağıtılmadı. Uygulama şeması, entity, endpoint veya DTO değişmedi.

## Tarihsel işlemlerin CREATE tanımlarına taşınması

- Tarihsel V32 origin/completeness/status -> aktif V31 CREATE games.
- Tarihsel V33 normalized_title genişletmesi ve title pattern_ops -> aktif V31.
- Tarihsel V34 Steam CHECK -> aktif V32 CREATE game_external_refs.
- Tarihsel V35 game_type ve aktif tür/başlık indeksi -> aktif V31.
- Tarihsel V36 games üzerindeki 7 detay/güncellik kolonu -> aktif V31;
  sync failure_count/next_attempt_at -> aktif V33.
- Tarihsel V37 early_access/editör alanları, DEFERRABLE INITIALLY DEFERRED unique,
  game_languages ve keşif/kapak indeksleri -> aktif V31.
- Tarihsel V171 games dil alanları -> aktif V31; LOCALIZATION field CHECK -> aktif V35;
  destek/provenance tabloları -> aktif V38.

Önce işlevsiz kalan tarihsel V35/V36/V37 kaynak dosyaları kaldırılmıştı. Son kararda
bu aktif sürümler sırasıyla yönetim, grant ve geliştirici içerik gruplarına atandı.
Eski tarihsel sürümlerin anlamı test arşivinde ve merkezi karar belgesinde korunur.
V171–V173 yalnız eski zincirde kullanılmış tarihsel sürümlerdir. Sonradan alınan kararla
V171–V190 ek aralığı ve V174–V190 rezervasyonu kaldırılmıştır; gelecekteki Game Service
migration'ı için numara aralığı ekipçe belirlenir. Diğer servislerin V41–V170 tahsisleri değişmez.

## Tarihsel testler ve lokasyon ayrımı

`src/test/resources/db/history/game-service-v173/` eski 13 migration'ın byte düzeyinde
kopyasıdır. SHA256SUMS ve EOL dönüşümünü kapatan yerel .gitattributes korunur.
Bu görevde arşiv içerikleri/sürüm adları değiştirilmemiştir; hash'ler ayrıca Git HEAD
ile karşılaştırılmıştır. Eski SQL'deki ALTER'lar yalnız tarihsel test fixture'ındadır.

- LocalizationMigrationTest tarihsel lokasyonda önce V40 kurar; version=40 ve henüz
  game_language_supports yokluğunu doğrular. Dolu oyun/dil ile eski V40 -> V173 testini korur.
- PhaseSixMigrationTest tarihsel lokasyonda önce V171 kurar; başarımlar/feedback henüz
  yoktur. Dolu V171 -> V173, içerik uydurmama ve feedback FK RESTRICT testlerini korur.
- MigrationArchitectureTest aktif sürümlerin tam olarak 31…40 olduğunu, dosya sayısını,
  0 ALTER'ı, tarihi hash'leri ve yeni history sırasını doğrular.
- Aynı test **aktif V40=feedback**, **tarihsel V40=developer_content** ayrımını history
  script adlarından açıkça doğrular. Yanlış lokasyon seçimi bu kontrolleri bozar.
- Eski geçmiş üzerine yeni zincirin migrate çağrısı FlywayValidateException verir;
  izole dolu kayıt ve history değişmez. Başarısızlığı gizlemek için validation kapatılmaz.

Aktif `classpath:db/migration` 10 dosyadır. Tarihsel `classpath:db/history/game-service-v173`
13 dosyadır. Referans ve yeni history'nin farklı olması beklenir; yalnız uygulama
şeması ve verisi eşit olmalıdır. Test kaynakları uygulama JAR'ına girmez.

## PostgreSQL ve uygulama kanıtı

Flyway 12.4.0 / PostgreSQL 17 üzerinde iki bağımsız kurulum karşılaştırılmıştır.
Kolon sıra/tür/uzunluk/null/default/identity, constraint adı/türü/deferrability,
FK silme davranışı, indeks tanımı/predicate/pattern_ops, fonksiyon ve trigger
tanımları, persistence/RLS özellikleri ve STEAM başlangıç satırı eşleşir.
Hibernate 7.4.5.Final gerçek entity sınıflarıyla iki şemayı validate eder.

Yeni zincir ikinci migrate çağrısında 0 işlem yapar. Geçici V174 probe yalnız @TempDir
içinde iki ayrı kendi geçmişi tutarlı kurulum üzerinde uygulanır; gerçek V174 tüketilmez.
Owner/grant, üretim yükü ve production yedek/restore uyumluluğu bu kanıtın kapsamı değildir.

İzole veri taşıma ve gerçek HTTP başlangıcı ayrıntıları:
[İzole veri taşıma provası](LTZ-HAM-GAME-Izole-Veri-Tasima-Provasi.md).
Bu, güncel Game Service kodunun iki farklı kurulumdaki davranış karşılaştırmasıdır;
eski Docker binary'sinin entegrasyon testi veya canlı DEV/Gateway doğrulaması değildir.

## Güncel kalite sonuçları

- Hedefli ilk tur: 15/15 PASS; logs/migration-renumber-targeted.log.
- Son hâlle `.\pre-push\pre-push.cmd game-service`: **243/243 PASS**, 0 failure/error/skipped.
- Maven clean verify ve JaCoCo: **1490 covered / 19 missed = %98,74 LINE**, eşik %80.
- Runner: **BACKEND TEST CHECK PASSED**; full çalıştırılmadığından SAFE TO COMMIT iddiası yoktur.
- Son log: logs/migration-renumber-game-service-final.log. Bu son tur HASH_MISMATCH
  hata kodu assertion'ını da kapsar; önceki tam tur da 243/243 başarılıdır.
- Paketlenen JAR: yalnız V31–V40, 10 SQL, 0 ALTER; history/rehearsal fixture'ları yok.
- git diff --check başarılı; index'e dokunulmadı.

Son Git durumu: feature/game-service; staged 0, tracked 7 modified + 11 deleted,
33 untracked dosya (önceki yeniden yapılandırma çalışmaları dahil). Git'te eski
dosya adları deleted, yeni adlar untracked görünür; bu, henüz stage edilmemiş yeniden
adlandırmalarla uyumludur. 33 sayısı bu talimatta sıfırdan yazılan dosya sayısı değildir.
Kullanıcının önceki çalışma ağacı korundu; commit/push/add yapılmadı.
Son bütünlük kanıtı: logs/migration-renumber-final-integrity.json.

**İlk teslimde SonarQube BEKLİYORDU / NOT VERIFIED:** yerel sunucu API'si UP (26.9.0.129388).
Mevcut `sonarqube/sonar.mjs` scan, bütün backend için clean verify çalıştırıp Compose
profilinden DEV bağlantılarını tüm modül testlerine iletir; Game Service seçim desteği
yoktur. Bu görevin başka servis DB'lerine dokunmama sınırı nedeniyle runner başlatılmadı.
Token/ENV değiştirilmedi, rapor tazelik veya kalite kapısı kontrolü atlatılmadı.
Önceki Sonar PASS yeni değişikliklerin PASS sonucu değildir. Kullanıcı mevcut
runner'ı manuel çalıştırabilir veya ayrı bir izole backend kalite ortamı onaylayabilir.
Sonraki açık onayla izole ortamda gerçek tarama yapılmış, dört bakım bulgusu nedeniyle
gate FAIL vermiştir; bulgular giderilip tarama tekrarlanmış ve gate PASS almıştır.
Güncel ayrıntılar bu raporun başındaki takip bağlantısındadır.

## Değişen test ve belge dosyaları

- MigrationArchitectureTest: yeni sıralı sürümler, açık V40 anlam ayrımı ve şema sayıları.
- MigrationSchemaSupport: ortak salt okunur şema/Hibernate karşılaştırması.
- MigrationTransferSupport: yalnız test kapsamlı transaction'lı kopyalama, seed eşitleme,
  tam içerik/hash kontrolü ve readiness kapısı. DEV taşıma aracı değildir.
- MigrationTransferTest (8): başarı + güvenli hata senaryoları.
- MigrationTransferApplicationTest (1): gerçek loopback HTTP uygulama başlangıcı/karşılaştırması.
- db/rehearsal/synthetic_game_data.sql: gerçek kullanıcı verisi içermeyen 34 satırlık fixture.
- Merkezi docs/decisions/migrations.md, docs/database.md ve docs/er-diagram.md güncellendi.
- Önceki baseline araştırması tarihsel kayıt olarak korunur; B173 uygulanmış değildir.

## Gerçek DEV geçişi

**[Tarihsel — güncel durum yukarıdaki TAMAMLANDI maddesindedir; DEV sıfırlandı, veri taşıma planı seçilmedi]**
**BEKLİYORDU:** bu görevde DEV'e SELECT dahil bağlantı kurulmamıştır; önceki oturumun
DEV tarihçe kanıtı güncel canlı doğrulama diye sunulmaz. Gerçek geçiş öncesinde
yeniden history/checksum doğrulaması ve açık onay gerekir.
Kesin sıralı plan ve yapılandırma gereksinimleri izole prova raporunun DEV geçiş
bölümündedir. Ayrı hedef DB ve veri aktarımı olmadan yeni zincirle restart edilmez.
Yeni hedefte yazma başladıktan sonra eski DB'ye kör dönüş veri kaybettirir.

Çalışma kaynak/test/dokümantasyon teslimiyle durur; gerçek DEV geçişi uygulanmaz.
