# game-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Hamza.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- Windows'ta `core.autocrlf=true` ise SQL migration dosyaları CRLF kalır ve 8 migration testi kırmızı çıkar (kod hatası değil, Linux CI'da yok). `.gitattributes` (`*.sql text eol=lf`) bunu çözer; eski checkout'ta migration klasörünü silip `git checkout -- <klasör>` ile LF'e çevir.

## 10 Kasım 2026 — Katalog yaşam döngüsü: görünürlük, hız, Steam yedeği, tüm mağazalar, TR, +18 (kod hazır, DEV'e uygulanmadı)

- **Kapsam:** Steam durum kolonları (`games.free`, `coming_soon`, `adult`, `delisted_at`) V31'e, fiyat kaynağı kısıtı (ITAD+STEAM) V173'e taşındı (kullanıcı onaylı tek seferlik migration kuralı istisnası; yeni migration yok; checksum değişti); `SteamReleaseStatusService` (erken erişim, ücretsiz,
  yakında, +18, mağazadan kaldırılma iki ardışık gözlemle, Steam tür kayması koruması); `SteamTypeClassificationService` (IGDB'yi beklemeden `type=0` ana oyun,
  `type=4` DLC; terimler `SteamTermMapper` ile yalnız mevcut kodlara); `CatalogClassificationService` IGDB uzlaştırması (`IGDB_TYPE_CHECK`, IGDB kanıtı Steam
  türünün ve terimlerinin yerini alır); `DlcParentService` toplu Steam bağlama (100 DLC/istek) ve IGDB'siz mod; `CatalogImportService` `sync-interval` (dev 1 sa,
  canlı 15 dk); `SteamRequestBudget` önceliği (`ProviderPriority`: arka plan taramaları günlük payın %75'inde durur, sayfası açılan ve sıcak işler sürer);
  DLC fiyatları; ITAD'de tüm mağazalar (`capacity=5` kalktı, `ItadRequestLimiter` 5 dk'da ≈850 istek); TR/TRY pazarı; `includeAdult` filtresi; dump'ta fiyat
  verisi yok; `ops/game-freshness-report.mjs`.
- **Doğrulama:** backend modül testleri, ops testleri (`node --test`) ve frontend birim testleri PASS (sayılar son rapor mesajındadır). DEV dağıtımı, birleşik V176
  uygulaması ve Sonar taraması bu kayıtta YAPILMADI (ayrı onay gerekir).
- **IGDB ticari kullanım (kullanıcı işi):** ücretsiz kullanım yalnız ticari olmayan uygulamalar içindir; ticari lansman öncesi IGDB (Twitch) ile ortaklık/şartlar
  görüşülmelidir. Sistem IGDB'siz tam çalışır (Steam yedeği tür, terim, tarih, DLC ana oyun bağlama sağlar); canlıda ortaklık gelene kadar IGDB anahtarı boş
  bırakılabilir, kod değişikliği gerekmez. Resmî metin okunamadı; ikincil kaynaklara dayanır.
- **Türkiye (TRY):** ITAD TR için tüm teklifleri TL döndürür; Fanatical, Humble, GameBillet, IndieGala, DLGamer sabit kurla dönüştürülmüş (TRY/USD ≈50,2),
  Epic, GOG, GreenManGaming bölgesel fiyat verir. Steam Türkiye'de USD ile sattığından ITAD'in "Steam TRY" satırı gösterilmez ve Steam doğrudan fiyatı TR için
  alınmaz. Arayüzde şeffaflık notu görünür. ITAD atıf satırı eklenmez (ürün kararı).
- **Bilinen sınırlar:** "En düşük seçenek" kuralı bazı pazarlarda üst sürümü (Edition) gösterebilir. +18 onay kutusu yaş doğrulaması değildir (içerik/yaş politikası
  ayrı ürün/hukuk kararı). Mağazadan kaldırılmış oyun kaydı korunur; yeniden tanınırsa işaret kalkar. Sıcak olmayan oyunların release-status taraması Steam kotası %75'e
  ulaşınca durur (sıcak ve sayfası açılan oyunlar sürer).
- **Geri alma:** kaynağı STEAM olan tür ve terimler tek SQL ile geri alınır: `update games g set game_type='UNKNOWN', parent_game_id=null from game_metadata_provenance p
  where p.game_id=g.id and p.field='GAME_TYPE' and p.source='STEAM'` ve ilgili `STEAM_TYPE` işaretleri silinir; `ltz.game.catalog.steam-type.enabled=false` yedeği kapatır.

## 7 Kasım 2026 — Game Sonar düzeltmeleri

- Gerçek Sonar analizinde Game'in 42 açık bulgusu kapandı: bug, vulnerability ve
  code smell 0. Sonar coverage %91,5, duplication %1,9. Proje geneli kapı diğer
  servislerdeki 88 yeni bulgu nedeniyle FAIL; bu çalışma yalnız Game'i düzeltir.
- Maven `services/game-service verify`: 429/429 test, Checkstyle, PMD ve merkezi
  coverage kontrolü PASS. JaCoCo LINE %98,05; değişen executable satırlar %98,91.
  Sonar coverage ölçümü dalları da kapsadığından LINE oranıyla aynı değildir.
- Mevcut eşzamanlılık testi admin başlığının katalog tarafından ezilebildiğini
  yakaladı. Katalog, manuel alan bilgisini okumadan önce eşlenmiş oyun satırlarını
  kimlik sırasıyla kilitler; 13 eşzamanlılık testi PASS.
- Review puan istemcisinin yolu `ltz.game.rating.summary-path` üzerinden verilir;
  varsayılan `/internal/v1/games/rating-summaries` korunur. Özel yolun bearer
  yetkilendirmesi ve sayfalama parametrelerini koruduğu test edilir.
- Canlı pilotlar `ITGameProviderLivePilot` ve `ITGamePriceLivePilot` adlarıyla
  yalnız açık pilot komutunda çalışır; standart Surefire testlerine dahil olmaz.
  Pilot runner testleri 3/3 PASS. Pre-push bu doğrulamada çalıştırılmadı.

## 7 Kasım 2026 — sağlayıcı ve web kabulü

- Gerçek IGDB pilotu normal test setinden bilerek ayrıdır (sınıf adı
  `ITGameProviderLivePilot`); `node ops/game-provider-pilot.mjs --allow-env-files`
  yalnız kullanıcı o oturumda `.env` / `.env.dev` okumasını onayladıysa çalıştırılır.
  Sadece sağlayıcı değişkenleri ayrıştırılır; secret çıktı/argv/dosyaya yazılmaz.
  Test geçici PostgreSQL kullanır; gerçek DEV/prod DB'ye bağlanmaz.
- Gerçek Auth/Gateway/Game: `node ops/game-auth-e2e.mjs`, yalnız sentetik hesaplar,
  geçici PostgreSQL/Mailpit, üretilen anahtarlar ve temizlenen süreçler. 8/8 PASS;
  HTTP CookieJar kullanır, tarayıcı Secure-cookie/HTTPS deploy kabulü değildir.
- ITAD anahtarı izin verilen iki ENV'de de yok; canlı ITAD NOT VERIFIED. Şablon ve
  Compose aktarımı önceden onaylıdır; gerçek ENV bu görevde değiştirilmez.
- Web yazmaları mevcut CSRF sözleşmesini kullanır; otomatik POST tekrarı yoktur.
  409 taslağı korur; mutation sonucu belirsizken yeniden göndermeden önce durum
  kontrol edilir. Rol/sahiplik için backend denetimi esastır.

- Son Game kapısı: 427/427, LINE %97,96, değişen %90 (9/10), yeni kalite ihlali 0,
  Trivy HIGH/CRITICAL 0; BACKEND TEST CHECK PASSED. Frontend lint/typecheck PASS,
  395 PASS/1 mevcut SKIP, Chromium 5/5 PASS, frontend Trivy PASS.
  Kanıtlar: `logs/game-remaining-verify.log`, `logs/game-remaining-browser-final.log`,
  `logs/game-remaining-unit.log`, `logs/game-web-dependencies.log`.

## 7 Kasım 2026 — ITAD canlı fiyat pilotu güncellemesi

- Önceki anahtar eksikliği giderildi: kullanıcının açık yazma talimatıyla yalnız
  .env.dev içine ITAD_API_KEY eklendi. Dosya ignore edilir; secret raporlanmadı.
- `node ops/game-provider-pilot.mjs --allow-env-files --itad` gerçek ITAD adaptörü,
  geçici PostgreSQL, güvenlik filtreleri etkin fiyat API'si ve TTL tekrar turuyla PASS.
  Diğer sağlayıcı anahtarları bu sürece aktarılmaz; normal testler dış ağa çıkmaz.
- Gerçek sözleşme düzeltmeleri: `/lookup/id/shop/61/v1`, resmî `https://itad.link`
  host'u ve TR/TRY varsayılanı. Affiliate URL korunur; benzer host/userinfo/HTTP
  reddedilir. Yanlış para birimi halen hata; kur dönüşümü yoktur.
- Anahtar erişimi ve yerel pilot, ticari kullanım onayı veya DEV/prod dağıtım kabulü değildir.

- ITAD düzeltmeleri sonrası son merkezi Game koşusu: 428/428, LINE %97,96,
  değişen satırlar %94,12 (16/17), baseline/yeni ihlal/Trivy PASS;
  BACKEND TEST CHECK PASSED (`logs/game-itad-verify.log`). Frontend lint/typecheck
  PASS, 395 PASS/1 mevcut SKIP; sağlayıcı çalıştırıcı testleri 3/3 PASS.

## 7 Kasım 2026 — DEV runtime kabulü

Kullanıcının açık başlatma talimatıyla bütün LTZ DEV Compose servisleri açıldı:
20 konteyner çalışıyor, 18/18 Java health UP. Güncel Game JAR hash'i ve ITAD
anahtarının .env.dev ile eşleşmesi gizli değer yazdırılmadan doğrulandı. Gerçek
Gateway katalog/durum okuması 200; fiyat okuması TR/TRY için UNAVAILABLE: mevcut
2 aktif oyunun hiçbirinde Steam MAIN_GAME referansı yok (aday ve teklif sayısı 0).
Gerçek fiyat kabulü önceki geçici DB ITAD pilotunda PASS; tekrar edilmedi.
Mevcut volume'lar korundu; kalıcı test kaydı veya manuel DB onarımı yapılmadı.
Kanıt: `logs/ltz-game-dev-check.log`, `logs/ltz-dev-runtime-health.json`.
