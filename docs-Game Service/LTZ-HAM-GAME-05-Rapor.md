# LTZ-HAM-GAME-05 — Yerelleştirme, fiyat hazırlığı ve manuel düzeltmeler

Tarih: 2026-10-21 · Sorumlu: Hamza · Branch: feature/game-service

## Sıra ve durum

- [x] GAME-23: yerel dil desteği ve kaynak/doğrulama izlenebilirliği uygulandı; 9 hedefli + 2 ek regresyon testi PASS.
- [x] GAME-24 altyapısı: sağlayıcıdan bağımsız bölgesel fiyat sözleşmesi ve 3 test PASS.
- [ ] GAME-24 gerçek fiyat/indirim: izinli sağlayıcı bağlama ve ENV onayı bekler; fiyat uydurulmaz.
- [x] GAME-26 yerel kapsamı: mevcut arşivleme/kilit/audit ve yeni ADMIN kimlik yönetim girişleri; 8 yeni test.
- [ ] GAME-26 gerçek yeniden eşleme/ayırma: Library/Review referans koordinasyonu bekler; HTTP mutasyonu engellenir.
- [x] Game Service pre-push: 208 test, %98,63 satır kapsamı; BACKEND TEST CHECK PASSED.
- [x] Faz sonu SonarQube: SONAR SCAN PASSED; backend kalite kapısı PASS, tüm rating'ler A.

## GAME-23

Mevcut POST/PATCH admin oyun uçlarına opsiyonel `localization` nesnesi eklendi.
Eski istekler, `languages`, kartlar, filtreler ve detay yolları korunur. Public
metadata/detail yanıtlarına metadataLanguage, languageDataState, languageSupport,
provenance eklenir. Yeni public endpoint ve ENV yoktur. JWT/RBAC değişmedi.

Arayüz/ses/altyazı durumu ayrı ayrı UNKNOWN/SUPPORTED/UNSUPPORTED. Eksik bilgi
UNSUPPORTED olmaz. Genel dil listesi üç tür desteğin kanıtı değildir. Dil kodu
normalizasyonu Türkçe/Turkish/Turkce ve Unicode geniş TR etiketlerini ele alır;
HTML/asterisk içeren yanlış metin reddedilir, destek yok sonucuna çevrilmez.
Kaynak durumları UNKNOWN, EMPTY, INVALID ve AVAILABLE ayrıdır.

Admin verisi ADMIN kaynağıyla işaretlenir. recordedAt bir kayıt/alım zamanıdır,
doğrulama değildir. `verified=true`, gerekçesiyle admin'in açık doğrulama beyanıdır;
bu dış sağlayıcı doğrulaması olarak gösterilmez. Tarih sunucudan gelir. UNKNOWN
ve INVALID doğrulanmış sayılamaz. Sonraki veri değişikliği eski doğrulamayı kaldırır.
Normal admin düzenlemesi verifiedAt üretmez. Public yanıtta aktör/gerekçe yoktur.
LOCALIZATION doğrulaması yalnız metadata dili/dil desteği beyanını kapsar;
description veya diğer metadata'nın dilini/doğruluğunu doğrulamaz. Otomatik dil tespiti yoktur.

Kaynak izi alan başınadır; mevcut katalog ve detay yazma yolları yalnız gerçekten
kabul edilen alanları sağlayıcı kodu ve kayıt zamanıyla işler. Manuel kilitli
alanların izi sağlayıcıya çevrilmez. Eski kayıtlara sağlayıcı veya doğrulama tarihi
uydurulmadı. Mevcut game_field_overrides ve append-only admin audit yeniden kullanılır.

V171: games'e iki alan; ayrıntılı destek için game_language_supports ve alan
kaynakları için game_metadata_provenance. Yeni ayrıntılar eski genel dil listesinden
çıkarılamadığı için ayrı koleksiyonlar gereklidir. Paralel yönetim veya audit
sistemi kurulmadı. Veri silinmez; eski FK/kimlikler korunur; manuel kilit CHECK'i
eski değerleri koruyup LOCALIZATION ile genişletilir. V31–V40 değiştirilmedi.

Testler: LocalizationServiceTest (2), LocalizationMetadataTest (6),
LocalizationMigrationTest (1). Dolu V40 PostgreSQL verisi V171'e geçirilerek başlık,
genel dil listesi ve UNKNOWN/boş kanıt davranışı doğrulandı. Tüm DB'ler geçici
Testcontainers DB'leridir; ilk geliştirme doğrulamasında DEV migration'ı uygulanmadı.
Sonraki kullanıcı onaylı DEV dağıtımı aşağıdaki bölümde kayıtlıdır.
İlk test turunda opsiyonel primitive verified alanının Jackson record çözümlemesinde
400 oluşturduğu görüldü; Boolean yapılıp eksik değer false'a açıkça çevrildi.
Kanıt: logs/phase5-game23-tests-final.log.

Son incelemede UNKNOWN oyun türünün provenance durumunun AVAILABLE olmaması sağlandı.
MetadataProvenanceServiceTest içindeki iki ek regresyon testi UNKNOWN/EMPTY ayrımını,
alan adı normalizasyonunu ve localization doğrulamasının ayrı tutulmasını sınar.

Gerçek GAME-17 detay sağlayıcısı olmadığından dış kaynaktan dil alma/ayrıştırma
ve gerçek dış doğrulama uygulanmış sayılmaz. Şimdiki doğrulama yerel/admin akışıdır.

## GAME-24 — koşullu

GamePriceProvider portunda toplu kimlik, zorunlu ülke/para birimi, mağaza, kaynak,
normal/güncel fiyat ve fetchedAt/expiresAt sözleşmesi vardır. Geçerlilik sonu hariçtir;
gelecek veya süresi dolmuş snapshot güncel sayılmaz. Ücretsiz fiyat sıfırdır;
eksik fiyat null ile uydurulamaz. Sahte üretim verisi, bean, ağ çağrısı, fiyat
endpoint'i veya kullanılmayan fiyat tablosu yoktur. Testlerdeki TEST kaynağı sentetiktir.

21 Ekim 2026 resmî belge incelemesi: IsThereAnyDeal açık uygulamalarda ticari kullanıma
koşullu izin verir; kaynak gösterimi önerilir, fiyat/bağlantıları değiştirmeme ve rekabet
oluşturmama koşulları vardır. API anahtarı uygulama kaydı gerektirir. LTZ'ye uygulanacak
kullanım ve kalıcı saklama süresi bu incelemede kesinleşmedi; erişim/hesap kotası
canlı doğrulanmadı. Bu nedenle onaylanmış bir sağlayıcı adaptörü seçilmedi.

Kaynak: [sağlayıcının resmî API/koşul belgesi](https://raw.githubusercontent.com/IsThereAnyDeal/API/master/docs/docs.yaml).
Bu araştırma sağlayıcı adına hukuki izin veya yeni ENV onayı değildir. Gerçek
entegrasyon ayrı onay ve erişim bekler. Kanıt: logs/phase5-game24-tests.log (3 PASS).

## GAME-26 güvenlik sınırı

Mevcut GameIdentityService dış referansı taşır; UUID satırının silinmemesi,
Library/Review'daki verinin oyun anlamını da koruduğunu kanıtlamaz. Bu servislerin
referans sahipliği/koordinasyon sözleşmesi olmadan gerçek taşıma/ayırma güvenle
açılamaz. Yönetim uçları bu durumda FOREIGN_REFERENCE_SAFETY_UNVERIFIED ile
fail-closed olmalıdır; operatör gerekçesi veya istemci bayrağı engeli aşamaz.
Kanonik oyun silme, başka servisin DB'sine erişim ve toplu veri taşıma kapsam dışıdır.

Yeni uçlar: POST /api/v1/game/admin/identity/reassign ve /split. Controller,
AdminGameIdentityService üzerinden mevcut GameIdentityService'i kullanır.
Anonim 401; USER, GAME_DEVELOPER ve MODERATOR 403. Yalnız ADMIN kabul edilir.
Mevcut Steam katalog/reference/oyun kilitleri ve transaction'lar yeniden kullanılır.
Beklenen eşleme eskiyse 409 REFERENCE_MAPPING_CONFLICT; eksik referans/aktif hedef
404; geçersiz gerekçe/kimlik veya force gibi ek alan 400 döner. Reassign yalnız
mevcut=beklenen=hedef ise 200 no-op döner. Yeni fiziksel oyun veya başarı audit'i
üretilmez. Gerçek iki işlem de dış güvenlik doğrulanana kadar 409 döndürür.

Mevcut içeriden çağrılan reassign/split iş mantığı ve başarı audit'i korunmuştur;
HTTP'ye doğrudan açılmadı. Gerçek başarılı kimlik mutasyonu audit'i için mevcut
testler; güvenlik kapısı, rollback ve yarış için AdminGameIdentityControllerTest (6)
ve GameIdentitySafetyTest (2) çalıştırıldı. Sonuncusu gerçek PostgreSQL'de ayrı
transaction'larda bekleme ve geri alma sonrasında kaynak kimliğini doğrular.

Mevcut archive/restore, manuel alan kilidi açma, admin düzenleme ve senkronizasyonda
kilit koruması testleri tam pakette tekrar geçti; bu işlemler için yeni paralel
endpoint veya tablo eklenmedi. Archive/restore kabul kriteri önceki Faz 4 koduyla sağlanır.

## API, migration ve doğrulama özeti

Değişen sözleşmeler: admin POST /api/v1/game/admin/games ve PATCH
/api/v1/game/admin/games/{gameId} isteğinde opsiyonel localization; mevcut
metadata/detail/admin oyun yanıtında ek metadata alanları. Eski alanlar korunur.
İki yeni ADMIN kimlik ucu yukarıda listelenmiştir. Kayıtlar docs/api.md GAME bölümündedir.

Tek yeni migration: V171__add_localization_and_metadata_provenance.sql.
V172–V190 kullanılmadı. Otomatik geçiş testi dolu V40 verisinin korunduğunu gösterir.
V171 daha sonra açık kullanıcı onayıyla DEV'e normal servis başlangıcında uygulandı;
ayrıntılar aşağıdaki DEV doğrulama bölümündedir.

Faz öncesi 186 olan Game Service test sayısı 208 oldu (22 yeni test). Tam Game
Service doğrulaması 208/208 başarılı, JaCoCo line %98,63; kanıt
logs/phase5-game-service-final.log. Bu servis kapsamlı runner sonucu
BACKEND TEST CHECK PASSED'dır. Kullanıcı daha sonra full pre-push kontrolünü manuel
çalıştırıp SAFE TO COMMIT ve SonarQube PASS aldığını bildirdi. Bu ek kullanıcı beyanı,
ajanın bu DEV dağıtımı sırasında full kontrolü yeniden çalıştırdığı anlamına gelmez.

SonarQube son turu: logs/phase5-sonar-final.log, SONAR SCAN PASSED.
Backend Reports, Backend Analysis, Quality Gate Backend ve Overall Targets Backend PASS.
Runner'ın tekrar çalıştırdığı tüm backend testleri: 225 test, 0 hata/başarısız/atlanmış.
Game Service bu turda da 208/208 başarılıdır. JaCoCo line: 1372 covered / 19 missed
(%98,63). Sonar'ın farklı kapsam/koşul metriği backend genelinde %90,3; yeni kodda
%91,5. Reliability/security/maintainability genel ve yeni kod rating'leri A;
duplicate density %0. İlk analizdeki 6 bakım bulgusu giderildi: servis anotasyonları,
regex sadeleştirme/önceden derleme, ayrı değişken bildirimi ve record metot adı.
Kalite kapısı gevşetilmedi veya bulgular bastırılmadı.
Son analizden sonra Sonar API ile tüm unresolved issue ve hotspot listeleri de
kontrol edildi: açık issue 0, hotspot 0.

## DEV dağıtımı ve API doğrulaması — 2026-10-21

Kullanıcı yalnız DEV Game Service imajının güncellenmesini ve V171'in normal
Flyway başlangıcıyla uygulanmasını açıkça onayladı. Güvenlik/ENV yapılandırması
değiştirilmedi. Başka servisin kodu veya konteyneri değiştirilmedi.

### İmaj ve konteyner

- `docker compose -p ltz-dev --env-file .env.dev -f docker-compose.yml build game-service`: başarılı.
- `up -d --no-deps --no-build game-service`: yalnız Game Service yeniden oluşturuldu.
- İmaj: `ltz/game-service:dev`, image ID `sha256:3d18432ca5ec44c678cd2c5d758188ec8922023fa7f56b1555af305e64f89183`.
- Konteyner: `ltz-dev-game-service-1`, ID `1de787377469566a9068232a991b528078d56fc91c68fd2ef89bbea374bcc47a`, durum running.
- Başlangıç: 21 Ekim 2026 18:13:13 (Europe/Istanbul); uygulama 5,727 saniyede başladı.
- Önceden çalışan diğer 20 konteynerin ID, StartedAt ve mount bilgileri aynı kaldı.
  PostgreSQL volume'ü korundu; silme/sıfırlama yapılmadı.

### Flyway ve veri koruması

Başlangıçta V31–V40 başarılıydı; V171 kolonları ve iki yeni tablo yoktu.
Mevcut `game_field_overrides_field_check` tanımı dosyadaki önceki değerlerle uyumluydu.
V171 veri silmez: nullable metadata dili, UNKNOWN varsayılanlı veri durumu ve iki boş
koleksiyon tablosu ekler; CHECK eski değerleri koruyarak LOCALIZATION ile genişler.
Yeni CASCADE ilişkiler yalnız yeni tablolara aittir; geçiş oyun silmez.

Normal uygulama başlangıcı 11 migration'ı doğruladı, V40'tan V171'e tek migration
uyguladı. Flyway son satırı: installed_rank=11, version=171,
script=V171__add_localization_and_metadata_provenance.sql, checksum=450374004,
success=true. Eski 10 Flyway satırı ve checksum'ları aynı kaldı.
Repair, baseline, reset veya geçmişe elle yazma yapılmadı.

DEV başlangıçta boş katalog içeriyordu: games=0, game_external_refs=0;
game_provider_sync_state=1. Önceki 17 uygulama tablosunun eski kolonları üzerinden
satır sayısı ve sıralı satır MD5 özetleri önce/sonra karşılaştırıldı: değişiklik yok.
İki yeni tablo boş; kaynak, doğrulama tarihi veya dil desteği uydurulmadı.
Bu DEV kontrolü dolu katalog korumasını kanıtlamaz; dolu V40 verisi ayrıca izole
LocalizationMigrationTest ile doğrulandı. Test amacıyla DEV'e kayıt eklenmedi.

### Canlı DEV API sonuçları

Doğrudan servis `http://127.0.0.1:5084` ve mevcut gateway
`http://127.0.0.1:5080` üzerinden toplam 24 kontrol başarılı:

- Her iki health endpoint'i: HTTP 200, status UP.
- GET catalog?q=portal, catalog/status, games, games/search?q=portal,
  games/popular: 200; eski sayfalama ve yanıt alanları aynı, katalog boş.
- POST games/batch: 200; items/unavailable sözleşmesi korunuyor.
- Var olmayan UUID için metadata/card/detail: beklenen 404 ProblemDetail.
- POST admin/identity/reassign ve split: anonim erişim iki yolda da 401.
- Önce/sonra aynı isteklerde durum kodları ve üst seviye yanıt alanları aynı kaldı.

**Canlı DEV'de doğrulanamayanlar:** oyun olmadığı için GAME-23'ün dolu metadata/detail
yanıtındaki metadataLanguage, languageDataState, languageSupport ve provenance alanları
canlı örnek üzerinde gösterilemedi. Geçerli DEV rol JWT'leri olmadığı için USER/developer/
moderator 403 ve ADMIN 409 kontrolleri canlı ortamda doğrulanmadı. Test anahtarları DEV'e
taşınmadı; permitAll/sabit token/başka güvenlik bypass'ı eklenmedi. Auth entegrasyonu
NOT VERIFIED olmaya devam ediyor.

### İzole doğrulama

Mevcut test JWT altyapısı, gerçek Spring Security zinciri ve geçici PostgreSQL ile
15 hedefli test yeniden çalıştırıldı: 15 PASS, 0 failure/error/skipped.
AdminGameIdentityControllerTest (6), GameIdentitySafetyTest (2),
LocalizationMetadataTest (6), LocalizationMigrationTest (1).

USER, GAME_DEVELOPER, MODERATOR için iki uç 403; anonim 401;
ADMIN gerçek reassign/split için 409 FOREIGN_REFERENCE_SAFETY_UNVERIFIED.
Eski eşleme, no-op, veri/audit korunması, rollback ve eşzamanlı kilit doğrulandı.
GAME-23 public metadata/detail alanları, UNKNOWN ayrımı, eski istekler,
gerçek doğrulama beyanı/kaynak ayrımı ve manuel kilit koruması geçti.
İlk Maven çağrısındaki PowerShell argüman ayrıştırma hatası düzeltilerek testler
çalıştırıldı; bu bir uygulama/test başarısızlığı değildi. Başarısız kalan kontrol yok.

### Git ve kanıtlar

Branch `feature/game-service`, HEAD `d1273b0`. Git çalışma alanı: 16 modified,
22 untracked dosya; staged dosya yok. Faz 5 kaynakları ve belgeleri henüz commit
edilmedi. Bu DEV işleminde production kodu/migration değiştirilmedi; bu rapor güncellendi.
`git diff --check` başarılı. Commit/push/merge/rebase/reset yapılmadı.

Yerel kanıtlar (logs Git dışında): phase5-dev-build.log, phase5-dev-restart.log,
phase5-dev-before.json, phase5-dev-after.json, phase5-dev-api-before.json,
phase5-dev-api-after.json, phase5-dev-isolated-tests.log.

## Sınırlar

Auth/Review/gerçek detay sağlayıcısı entegrasyonu bekler. GAME-04/05 puan davranışı
değişmez. Faz 6'ya geçilmez. Commit/push/merge yapılmaz.

Faz 6 hazırlığı: mevcut modeller, dil/kaynak sözleşmeleri ve ADMIN güvenlik sınırı
korunmuştur; bu fazın yerel değişiklikleri ayrı bir Faz 6 geliştirmesine engel
oluşturmamaktadır. GAME-11/14 gereksinimleri için ayrı analiz ve açık kullanıcı
onayı gerekir. Bekleyen sağlayıcı/kimlik koordinasyonu üretim hazırlığı olarak
tamamlanmış sayılmaz. Şu teslim yalnız Faz 5'in izinli yerel kapsamıdır.
