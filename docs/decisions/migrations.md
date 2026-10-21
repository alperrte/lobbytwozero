
# LTZ — Flyway Migration Numaralandırma Planı

## ALPER
- V1–V10       → HARDWARE-SERVICE
- V11–V20      → RECOMMENDATION-SERVICE
- V21–V30      → PERFORMANCE-SERVICE

## HAMZA
- V31–V40      → GAME-SERVICE (aktif başlangıç zinciri, 10 migration; tamamı kullanıldı; yeni migration için ekipçe yeni aralık belirlenmelidir)
- V41–V50      → LIBRARY-SERVICE
- V51–V60      → REVIEW-SERVICE

## KEREM
- V61–V70      → AUTH-SERVICE
- V71–V80      → USER-SERVICE
- V81–V90      → MAIL-SERVICE

## NISA
- V91–V100     → COMMUNITY-SERVICE
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
  aktif migration değildir. V171–V190 şu an hiçbir servise tahsisli değildir; yeniden tahsis ekip
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
