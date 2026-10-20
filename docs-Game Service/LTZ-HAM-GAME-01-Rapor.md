# LTZ-HAM-GAME-01 — Faz 1 teknik teslim raporu

Tarih: 2026-10-21 · Sorumlu: Hamza · Servis: game-service · Branch: feature/game-service

**Durum: Faz 1 tamamlandı — 6/6 (%100). Güncel not: Faz 2 kullanıcı onayıyla başlatıldı; durumu [Faz 2 raporunda](LTZ-HAM-GAME-02-Rapor.md) izlenir.**

## Görev durumu

- [x] GAME-08 — Oyun alt verileri
- [x] GAME-09 — Harici oyun veri sağlayıcıları
- [x] GAME-10 — Tutarlı oyun görüntüleme modeli
- [x] GAME-16 — Yerel Steam katalog indeksi
- [x] GAME-18 — Kanonik oyun kimliği ve mükerrer kayıt kontrolü
- [x] GAME-21 — Oyun türü ve katalog temizliği

## Uygulanan geliştirmeler

### GAME-08

Tür/kategori/etiket/platform kavramları `catalog_terms.kind` ile ayrılır; ortak sözlük ilişkileri DB kısıtlarıyla korunur. API dört ayrı dizi döner. Çoklu geliştirici/yayıncı, sıralı medya ve platform + minimum/önerilen seviyesinde sistem gereksinimleri saklanır. Sağlayıcının vermediği skaler alanlar null, koleksiyonlar boş kalır. Ayrı medya servisi oluşturulmadı.

### GAME-09

`GameProvider` portu frontend DTO'larından bağımsızdır. Steam adaptörü yalnız belgeli CATALOG ve INCREMENTAL_CATALOG yeteneklerini sunar. SEARCH, DETAILS ve ITEM_TYPE desteklenmiş gibi gösterilmez. Resmî GetAppList için `input_json`, sayfalama, artımlı sorgu, mevcut RestClient timeout/redirect ayarları, 1 MiB yanıt limiti ve alan/ilerleme doğrulaması uygulanır. Hatalar anahtar/URL/ham yanıt içermeyen kodlara çevrilir.

Tek instance için 1 çağrı/sn ve 50.000 çağrı/UTC günü ihtiyatlı bellek bütçesi vardır. Yeniden başlatma sayacı sıfırlar; dağıtık veya kalıcı sağlayıcı kota garantisi değildir. Otomatik retry eklenmedi.

### GAME-10

INTERNAL/EXTERNAL kökenleri aynı kart ve detay kabuğunu kullanır. Kartta tek kanonik gameId bulunur; dış kimlikler provider/externalId referanslarıdır. Köken, metadata kapsamı ve oyun türü ayrı alanlardır. EXTERNAL oyun yerelde saklandığında INTERNAL olmaz. Aktif olmayan oyunlar public kart/detay/metadata uçlarında 404 döner.

### GAME-16

`CatalogImportService.startInitial()` ve `startIncremental()` ayrı giriş noktalarıdır. `importNextPage(size)` tek sayfa işler; size en fazla 1000'dir. Sağlayıcı state satırı kilitlenir, kayıtlar ve cursor aynı transaction'da saklanır. Tekrar aktarım gameId'yi korur; aynı adlı farklı AppID'ler birleştirilmez. Artımlı sorgu son başarılı turun başlangıcından bir saniye örtüşmeyle yürür. Sağlayıcı hatası önceki sayfaları silmez; cursor ve güvenli hata durumu saklanır.

Önek araması yerel DB'den çalışır; sağlayıcı çağırmaz. Sonuçlar normalizedTitle + gameId ile kararlı sıralanır. Boyut 1–100, boş sorgu reddedilir ve joker karakterler kaçırılır. Kapsam/durum API'si toplam sağlayıcı evrenini bilmediğinde providerTotal=null döner; yüzde 100 kapsam iddiası yoktur.

### GAME-18

Provider/externalId benzersizliği DB seviyesindedir. Steam AppID pozitif, başında sıfır olmayan uint32 metni olarak tutulur; `0123` alias'ı reddedilir. `GameIdentityService.reassign/split` beklenen kaynak gameId, doğrulayan operatör ve kanıt gerektirir. Eşleme ve indeksleme aynı kalıcı kilit sınırını kullanır. Eski kanonik oyun fiziksel olarak silinmez; bağlı servis geçmişi korunur. Split doğrulanmamış zengin metadata'yı kopyalamaz.

İşlem kaydı operatör REF, eski/yeni gameId, referans, kanıt ve UTC zamanı içerir. Audit tablosunda UPDATE/DELETE trigger ile reddedilir. Eşleme/ayırma HTTP üzerinden açılmadı; JWT/rol kullanan yönetim giriş noktaları Faz 4 kapsamındadır. Faz 1 komutları uygulama servisi sınırındadır.

### GAME-21

MAIN_GAME, DLC, DEMO, SOUNDTRACK, SOFTWARE, VIDEO, PACKAGE, HARDWARE ve UNKNOWN ayrılır. Sınıflandırıcı ada bakmaz; ancak adaptörün gerçekten sunduğu ITEM_TYPE yeteneğini kullanır. Varsayılan indeks araması yalnız MAIN_GAME döndürür. `includeUnclassified=true` bilinçli olarak UNKNOWN ekler; bilinen oyun dışı türleri eklemez. Kartlar gameType alanını taşır; durum yanıtında ana oyun ve belirsiz referans sayıları ayrı verilir.

**Steam sınırlaması:** Doğrulanan GetAppList yanıtında kayıt başına kesin tür alanı yoktur. `include_games` filtresi demo/soundtrack ayrımı için tek başına kanıt kabul edilmez. Bu nedenle Steam hafif indeks kayıtları UNKNOWN kalır ve varsayılan ana oyun sonuçlarına girmez; açık unclassified seçeneğiyle aranabilir. Güvenilir detay kaynağı olmadan tür veya metadata uydurulmadı.

## Flyway migrationları

- V31__create_game_metadata.sql
- V32__add_game_view_and_external_references.sql
- V33__create_local_catalog_index.sql
- V34__audit_verified_game_identity_changes.sql
- V35__classify_catalog_item_types.sql

Önceden uygulanmış migration değiştirilmedi. Yalnız game_db şeması kullanılır; servisler arası FK veya başka DB sorgusu yoktur. UUID kimlikler, timestamptz zamanlar, varchar + CHECK durumları ve değiştirilebilir entity'lerde version kullanılır. Şema Hibernate `validate` ile doğrulandı. V36–V40 aralığı henüz kullanılmadı.

## API ve yapılandırma

Kayıtların tamamı `docs/api.md` GAME bölümündedir:

- GET /api/v1/game/{gameId}/metadata
- GET /api/v1/game/{gameId}/card
- GET /api/v1/game/games/{gameId}
- GET /api/v1/game/catalog?q=...&page=0&size=20&includeUnclassified=false
- GET /api/v1/game/catalog/status

Kullanıcının onayıyla yalnız katalog okumaları Public'tir. Diğer yollar deny-by-default; CSRF korunur ve hata yanıtları ProblemDetail biçimindedir. İndeksleme/eşleme HTTP'ye açılmadı. Gateway prefix'i değiştirilmez.

`.env.example` içinde boş STEAM_WEB_API_KEY ve varsayılan STEAM_CATALOG_ENABLED=false bulunur; Compose bunları yalnız Game Service'e aktarır. Etkin olup anahtar eksikse uygulama fail-fast eder. Etkinleştirme otomatik toplu aktarım başlatmaz. Gerçek `.env` ve `.env.dev` dosyaları değiştirilmedi. Gerçek anahtar koda, teste, dosyaya veya rapora kaydedilmedi.

Manuel aktarım sayfaları bütçeye uygun aralıklarla çağrılmalıdır (en az 1 saniye). Bir hata FAILED durumunu üretirse kalıcı checkpoint korunur; otomatik yeniden deneme/zamanlanmış devam Faz 2 kapsamıdır.

## Canlı Steam doğrulaması

2026-10-21: Kullanıcının verdiği anahtar gizli stdin üzerinden yalnız geçici kontrol sürecine aktarıldı. Partner host 403 döndü. Public hosta aktarım için otomatik onay incelemesi önce hedefe özgü izin istedi; o istek gönderilmedi. Kullanıcının açık onayından sonra `https://api.steampowered.com/IStoreService/GetAppList/v1/` iki kayıtlık sorguya 200 döndürdü. Adaptör erişimi doğrulanan bu public hostu kullanır.

Yanıt alanları apps/have_more_results/last_appid; kayıt alanları appid/name/last_modified/price_change_number olarak doğrulandı. Anahtar `x-webapi-key` header'ında taşındı; URL veya çıktıya konmadı. Tam katalog indirilmedi, geliştiricinin game_db veritabanına aktarım yapılmadı.

Resmî kaynaklar:

- [GetAppList sözleşmesi](https://partner.steamgames.com/doc/webapi/IStoreService?l=english)
- [Anahtar ve header desteği](https://partner.steamgames.com/doc/webapi_overview/auth)
- [Public/partner host kuralları](https://partner.steamgames.com/doc/webapi_overview)
- [Steam Web API kullanım koşulları](https://steamcommunity.com/dev/apiterms)

## Doğrulama sonuçları

`pre-push.cmd game-service`: **65 test, 0 hata, 0 atlanan; JaCoCo satır kapsamı %98,16; BACKEND TEST CHECK PASSED.**

Testler geçici Testcontainers PostgreSQL 17 üzerinde V31–V35 migrationlarını ve JPA validate'i çalıştırır; geliştiricinin veritabanına dokunmaz. Kapsam: ilişkiler ve opsiyonel veri; ortak DTO ve görünürlük; 200/400/404/401/403; sağlayıcı hata/şema/kota sözleşmeleri; sayfalı/idempotent/artımlı aktarım; eşzamanlı sayfalar; kısmi hata; kimlik eşleme/ayırma; DB tekilliği ve audit immutability; bütün katalog türleri ve UNKNOWN filtreleme.

- `docker ... compose --env-file .env.dev config --quiet`: geçti; ENV değerleri yazdırılmadı.
- `git diff --check`: geçti.
- İlk geliştirme sırasında artımlı derlemede bir VerifyError görüldü; temiz derleme ve nihai clean verify geçti.
- SonarQube çalıştırılmadı; A notu alındığı iddia edilmez.
- Büyük veri hacmi/p95 yük ölçümü GAME-25 kapsamındadır ve yapılmadı.
- Frontend değişmedi; frontend testleri çalıştırılmadı.

`docs/database.md`, `docs/er-diagram.md` ve API kataloğu mevcut uygulamaya göre güncellendi. Kaynak görev Markdown dosyası değiştirilmedi. Commit/push yapılmadı.

## Sonraki faz

LTZ-HAM-GAME-02 — Oyun Detayı Zenginleştirme ve Otomatik Senkronizasyon: GAME-17 → GAME-19 → GAME-22 (3 alt task).

**2026-10-21 güncellemesi:** Kullanıcı Faz 2'yi onayladı. GAME-17 gerçek sağlayıcı entegrasyonu için ayrıca karar/onay bekleniyor; ayrıntılar [Faz 2 raporunda](LTZ-HAM-GAME-02-Rapor.md). Faz 3 için onay yok.

## 2026-10-21 — Tam pre-push kontrolü düzeltmesi

Kullanıcının tam kalite kontrolünde PostgreSQL/servisler çalışmıyordu. İskelet servis testleri `localhost:5542 connection refused` ile durdu; HTTP kontrolleri 18 backend modülünün çalışmadığını bildirdi. Ayrıca `full dev` Maven alt sürecine seçilen profilin DB kimlik bilgilerini aktarmıyordu.

Pre-push artık Compose profilini değerleri loglamadan çözer; yalnız DB kullanıcı/parolası ve DB adlarını Maven sürecine aktarır. Host bağlantısı yalnız 127.0.0.1 üzerindeki yayımlanmış PostgreSQL portuna yapılır. Docker'ın container içi adres/port ortamı değiştirilmez; ENV dosyalarına yazılmaz. Parola test çıktısında maskelenir. Ortam farkı, dış host reddi, eksik profil ve yanlış başarı durumları regresyon testleriyle kontrol edildi.

32 Node regresyon testi geçti. PostgreSQL başlatıldıktan sonra ilk tam kontrolde 82 backend testi ve build geçti; toplam satır kapsamı %87,93 oldu. O sırada backend imajlarının ilk derlemesi sürdüğünden ilk turda yalnız HTTP health başarısız kaldı. Servis kaynakları veya migrationlar bu düzeltmede değiştirilmedi.

Nihai doğrulama: `pre-push.cmd full dev` çıkış kodu 0 ve **SAFE TO COMMIT**. Git/Secret, Backend Tests, Backend Build, Backend Coverage, Docker Configuration ve Backend HTTP Health satırlarının tamamı PASS. 82 backend testi, toplam satır kapsamı %87,93 ve 18/18 HTTP health başarılıdır. 32 pre-push Node regresyon testi de geçti. Geliştirme servisleri çalışır bırakıldı. Son tam kontrol logu: `logs/pre-push-2026-10-20T21-30-42-756Z-41428-5ee5496f.log` (dosya adı UTC zamanını kullanır).
