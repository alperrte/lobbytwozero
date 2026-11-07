# game-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Hamza.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- Windows'ta `core.autocrlf=true` ise SQL migration dosyaları CRLF kalır ve 8 migration testi kırmızı çıkar (kod hatası değil, Linux CI'da yok). `.gitattributes` (`*.sql text eol=lf`) bunu çözer; eski checkout'ta migration klasörünü silip `git checkout -- <klasör>` ile LF'e çevir.

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
