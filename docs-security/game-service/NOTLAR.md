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
- **Kilit çakışması (deadlock) düzeltildi:** Postgres günlüğünde katalog işlerinin oyun satırı kilidi (`lockCatalogGames`, eskiden `for update`) ile fiyat yenilemesinin `game_price_refresh_state` yazımı çakışıyordu: fiyat satırı `games`'e yabancı anahtarla bağlı olduğundan yazarken oyun satırında anahtar paylaşım kilidi tutar. Kilit `for no key update` yapıldı (anahtar paylaşımıyla çakışmaz; oyun güncellemeleri zaten bu kilidi alır). `CatalogLockCompatibilityTest` eski kilitle kırmızı, yenisiyle yeşildir. Katalog taraması (`SteamGameProvider.catalog`) saniyede-1 bütçe beklemesini diğer işler gibi yeniden dener.
- **Gateway gecikmesi (Game dışı):** Game servisine doğrudan liste isteği ≈135 ms (p50), api-gateway üzerinden ≈350 ms; aradaki ≈220 ms gateway katmanındadır ve Game'in sahibi olmadığı bir servistedir.
- **Liste sorguları (11 Kasım 2026, 264 bin oyun, fiyat dolumu bittikten sonra ölçüldü):** karışık yükte (sayfalama, sıralama, arama, tür, indirim) Game servisine doğrudan p50 110 ms / p95 139 ms; api-gateway üzerinden p50 329 ms / p95 358 ms (gateway payı ≈220 ms, Game dışı). Tür filtresi önceden 500-650 ms'ydi: parametreli `(:bayrak = false or exists(...))` kalıbı ve `EXISTS` yazımı planlayıcıyı 168 bin tek tek aramalı iç içe döngüye düşürüyordu. `GameDiscoveryRepository` artık yalnız etkin filtrelerin sabit parçalarını birleştirir ve tür/platform/dil/indirim filtrelerini `IN (alt sorgu)` ile yazar (hash yarı-birleşimi, tür ≈80-120 ms, indirim ≈65 ms). İndeks gerekmedi (kapsayan indeks denendi, kazanç yok).
- **ITAD kotası ve kademe payı (DEV gözlemi, 11 Kasım 2026):** DEV anahtarı ekip tarafından paylaşılır; kota yetmeyince ITAD 429 + `Retry-After` döner ve servis tüm pazarlar için o süre kadar yeni istek atmaz (`PRICE_LOCAL_THROTTLE`, hata sayılmaz). Aday sıralaması sıkı öncelikliyken sıcak kademe (≈198 bin çift, 20 dk) tüm kapasiteyi yiyip ılık ve soğuk oyunları aç bırakıyordu; adaylar artık kademeler arasında paylaştırılır (sıcak %40, ılık %30, soğuk %30). Kota yetmezse gerçek yenileme aralıkları hedeften uzar; canlıda ITAD 429 sayısı ve tazelik raporu izlenir, gerekirse önce soğuk süre uzatılır, sonra pazar sayısı azaltılır (yapılandırma).
- **GAME-24 fiyat tazeliği (12 Kasım 2026, S1–S7):** Oyun sayfasındaki "fiyatın güncelliği doldu" mesajının kaynağı yenilemenin yetişememesiydi (DEV'de pazar başına ≈125 bin oyun bayat; ITAD ≈160-220 istek/5 dk'da 429 verir; saatte ≈1,25 milyon oyun-fiyatı gerekir). Yapılanlar: (S1) 429 ve istek telemetrisi, `ops/game-freshness-report.mjs` kırılımı; (S2) arayüzde bayat fiyat "güncel olmayabilir" etiketiyle gösterilir; (S3) 429/yerel kısıtlama/ITAD bloğu oyunu `FAILED` yazmaz, zehirli parti ikiye bölünerek ayıklanır, `next_attempt_at` boş kalmaz; (S4) `ItadRateProperties` ile ayarlanabilir, uyarlamalı (AIMD) limitleyici, DEV limiti 150/30, `floor` 90; (S5) düşen sayfa-açılışı ipucu en çok iki kez yeniden kuyruğa alınır; (S6) eşlemesiz AppID 24 saat önbellekte, `NO_OFFER` aralığı 6 katı, boş kademe payı devredilir; (S7) arka plan yenilemesi talebe göre (ABD, TR + okunan en çok 3 pazar; V176 `game_price_market_demand`). **Bilinen sınır:** ITAD eşlemeleri yalnız bellekte tutulur; her yeniden başlatma ≈1300 `lookup` isteğini yeniden harcar (S8 adayı: eşlemeyi veritabanında kalıcı tutmak). Etkin olmayan pazarlarda STALE beklenen davranıştır. Paylaşılan DEV anahtarında başka makinenin 429'u bizim limitimizi haksız yere düşürmez (küçük gözlemde 429 limiti değiştirmez), yalnız `Retry-After` bloğu geçerli olur.
- **Yetişkin içerik (+18) kapısı (12 Kasım 2026):** `includeAdult=true` yalnız Auth'un imzaladığı access token'daki boolean `adult_verified=true` iddiasıyla uygulanır (`AdultAccess` + `AdultAccessArgumentResolver`, merkezi güvenlik katmanı; controller JWT okumaz). Kapalı varsayılan: anonim, iddiasız, `false` ve boolean olmayan değer yetkisiz sayılır ve sessizce yok sayılır (`includeAdultApplied=false`, hata yok). Yalnız `/games` ve `/games/search` kapıdadır; popüler liste, detay, `batch`, Library ve Review okumaları etkilenmez. İddia adı `adult_verified` değişirse Auth `TokenClaims.ADULT_VERIFIED`, bu sınıftaki sabit ve `docs/api.md` birlikte değişir. Geri almada ≤60 sn gecikme kabul edilmiştir (`ver` artırılmaz). `includeAdult` istenen yanıtlarda `Cache-Control: private, no-store` + `Vary`. **Dağıtım uyarısı:** Auth iddiayı yazmadan bu Game sürümü dağıtılırsa +18 kutusu herkes için boş döner. **Canlı doğrulandı (12 Kasım 2026, yerel ltz-dev, Gateway :5080):** anonim `includeAdult=true` → `200`, `includeAdultApplied=false`; `includeAdult` yanıtında `cache-control: no-store, private` ve `vary: Authorization, Cookie` Gateway'den değişmeden geçiyor; çerezli (`ltz_access`) GET Gateway'de CSRF istemiyor (sahte token Game'e ulaşıp `401` alıyor, aynı çerezle POST `403 CSRF_TOKEN_INVALID`). **Girişli uçtan uca canlı doğrulandı (12 Kasım 2026, yerel ltz-dev, gerçek Auth + Gateway + Game, kullanıcı onayıyla `.env.dev`'den yalnız dev hesap parolası kullanıldı):** (1) tier A hesap yaş beyanında `403 STEP_UP_REQUIRED` aldı; (2) TOTP ile güvenilir (tier B) yapılan hesap `POST /account/age-verification` ile `204` aldı; (3) token yenilenmeden `includeAdultApplied=false` kaldı (≤60 sn gecikme beklenen davranış); (4) `session/refresh` sonrası token'da `adult_verified: true` göründü, `/me` `adultVerified=true`, `GET /games?includeAdult=true` `includeAdultApplied=true` döndü ve toplam 189.153 → 199.537 oldu (10.384 yetişkin oyun); ilk 100 kayıtta `adult=true` görüldü; (5) `includeAdult` gönderilmeyince yetişkin içerik gelmedi; `/games/popular` kapı dışı kaldı; (6) `DELETE /account/age-verification` ve yenilemeden sonra claim kalktı ve `includeAdultApplied=false` döndü. Hesap sonunda eski haline döndürüldü (TOTP kapatıldı, aktif yaş doğrulaması yok). **Yönetici geri alma (Auth V67, canlı doğrulandı 12 Kasım 2026, `main`'de):** yönetici `POST /api/v1/auth/admin/accounts/{id}/age-verification/revoke` ile geri alınca eski token ≤60 sn yetkili kalır, `session/refresh` sonrası claim kalkar ve `includeAdultApplied=false` döner; kullanıcı yeniden beyan etmeye çalışırsa `403 AGE_VERIFICATION_BLOCKED`, sıradan kullanıcı yönetici ucuna `403`; `lift-block` sonrası yeniden beyan `204` ve claim yeniden `true` olur. Game için değişen bir şey yoktur: claim yine yalnız doğrulanmışken boolean `true` gelir. Review frontend'i `main`'e girdi (PR #126). **Kapsam dışı kaldı:** tarayıcı arayüzü (kutu, ipucu) elle denenmedi.
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
