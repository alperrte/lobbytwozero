# LobbyTwoZero ops araçları

`ops/` klasörü Node dosyaları, bir manifest, rehber belgeler ve ENV profil geçiş betiklerinden oluşur:

| Dosya | Görev |
| --- | --- |
| `dev.mjs` | Tek yerel geliştirme aracı: kurulum, anahtar üretimi, ENV denetimi, e-posta, test hesapları, site adresi ve tüm e2e kabul testleri |
| `dev.test.mjs` | `dev.mjs` için tek ortak test dosyası (`node --test ops/dev.test.mjs`) |
| `hardware-catalog-import.mjs` | Hardware PC parça kataloğunu indirir ve içe aktarır (ayrı kalır) |
| `hardware-catalog-import.test.mjs` | Katalog içe aktarma betiğinin testi |
| `game-catalog-import.mjs` | Game oyun kataloğunu (tüm `game_db`) hazır dökümden dışa/içe aktarır; API anahtarı gerekmez |
| `game-catalog-import.test.mjs` | Game katalog betiğinin testi |
| `game-catalog.md` | Game katalog yükleme rehberi (ekip ve yapay zekâ ajanları için) |
| `game-catalog.manifest.json` | Dökümün SHA-256, boyut, Flyway sürümü, tablo satır sayıları ve (varsa) indirme adresi |
| `env.cmd` / `env.ps1` / `env.sh` | `dev` ve `prod` ENV profilleri arasında geçiş |

## dev.mjs komutları

```sh
node ops/dev.mjs                      # tek komutla kurulum: ön koşullar → anahtarlar → e-posta → servisler → hesaplar → frontend (5001)
node ops/dev.mjs stop | reset | status
node ops/dev.mjs mail on|off|status [--file <yol>]

node ops/dev.mjs keys [dosya…] [--rotate-totp] [--rotate-hmac] [--force]   # ENV anahtarlarını üretir/tamamlar
node ops/dev.mjs env-check [dosya…]                                        # Auth ENV kural denetimi (dosyaya dokunmaz)
node ops/dev.mjs accounts up|seed|list|totp <ad>                           # yığın + rol başına test hesapları
node ops/dev.mjs site-url <adres> [--production]                           # NEXT_PUBLIC_SITE_URL satırını doğrular ve yazdırır
node ops/dev.mjs help
```

Başlatma bayrakları: `--no-frontend --no-mail --no-build --no-services --no-catalog --skip-keys --dry-run --with=a,b --all --force`.

**Hardware kataloğu:** `--all` veya `--with=hardware-service` ile Hardware seçiliyse akış şöyledir: önce diğer servisler (Postgres dahil) kalkar → `hardware-catalog-import.mjs import <geçici-klasör> --standalone` çalışır → en son Hardware servisi başlar. Veri seti `%TEMP%/ltz-pc-part-dataset` klasörüne (yoksa) SHA-256 doğrulamalı indirilir. Katalog hatası kurulumu durdurmaz, uyarı yazılır. Atlamak için `--no-catalog`.
**Game kataloğu:** `--all` veya `--with=game-service` ile Game seçiliyse aynı sırayla `game-catalog-import.mjs import <geçici-klasör>` Game servisi başlamadan önce çalışır (depo kökündeki `game-catalog-dump/` klasörü; döküm yoksa manifestteki adresten indirilir). `game_db` zaten doluysa dokunulmaz; hata kurulumu durdurmaz. Atlamak için `--no-catalog`.
Başlatma akışı Node 24.7+ ve Docker ister. `dev.mjs` içindeki bölümler `// #region` işaretleriyle ayrılır; testler bölüm bazlı çalışır.

### e2e (gerçek-HTTP kabul testleri)

Yalnız geçici kaynaklar (geçici PostgreSQL, sahte JWKS) kullanılır; gerçek ENV okunmaz.

```sh
node ops/dev.mjs e2e hardware [--with-user]
node ops/dev.mjs e2e performance | auth | game-auth | game-community | game-review | library
node ops/dev.mjs e2e game-provider-pilot --allow-env-files [--itad]   # YALNIZ bu komut .env/.env.dev okur
```

### Eski betiklerin yeni karşılıkları

| Eski | Yeni |
| --- | --- |
| `dev-accounts.mjs` | `dev.mjs accounts` |
| `dev-mail.mjs` | `dev.mjs mail` |
| `generate-auth-dev-keys.mjs` | `dev.mjs keys` |
| `auth-env-check.mjs` | `dev.mjs env-check` |
| `frontend/site-url.mjs` | `dev.mjs site-url` |
| `auth-smoke.mjs` | `dev.mjs e2e auth` |
| `hardware-auth-e2e.mjs` | `dev.mjs e2e hardware` |
| `performance-auth-e2e.mjs` | `dev.mjs e2e performance` |
| `game-auth-e2e.mjs`, `game-community-e2e.mjs`, `game-review-e2e.mjs` | `dev.mjs e2e game-auth`, `game-community`, `game-review` |
| `library-phase1-e2e.mjs` | `dev.mjs e2e library` |
| `game-provider-pilot.mjs` | `dev.mjs e2e game-provider-pilot` |

## Hardware kataloğu içe aktarma

```sh
node ops/hardware-catalog-import.mjs download <dataset-klasörü>   # sabitlenmiş revizyonu indirir, SHA-256 doğrular
node ops/hardware-catalog-import.mjs dry-run <dataset-klasörü>    # ltz-dev Hardware konteynerinde deneme (yazmaz)
node ops/hardware-catalog-import.mjs import <dataset-klasörü>     # içe aktarır (--execute)
node ops/hardware-catalog-import.mjs import <dataset-klasörü> --standalone   # çalışan konteyner GEREKMEZ (tek seferlik konteyner)
```

`--standalone` olmadan yalnız `ltz-dev` Compose yığınındaki çalışan Hardware konteyneri kullanılır; `--standalone` aynı imajdan `compose run --rm --no-deps` ile tek seferlik konteyner açar (`dev.mjs` bunu Hardware başlamadan önce çağırır). Her iki durumda gerçek ENV dosyası okunmaz. Test: `node --test ops/hardware-catalog-import.test.mjs`.

## Game kataloğu içe aktarma

Oyunlar, terimler, açıklamalar (dile göre), DLC ilişkisi, dil desteği, medya, fiyatlar ve sağlayıcı senkron durumu **tek veritabanı dökümü** olarak taşınır; Steam/IGDB/ITAD anahtarı ve yeniden çekme gerekmez. Döküm tüm `game_db` şemasını (Flyway geçmişi dahil) taşır, hesap kimliği içeren tablolar boştur.

```sh
node ops/game-catalog-import.mjs export [klasör]            # (dökümü üreten kişi) ltz-dev Postgres'inden döküm + manifest yazar
node ops/game-catalog-import.mjs download [klasör]          # manifestteki adresten indirir, SHA-256 doğrular
node ops/game-catalog-import.mjs verify [klasör]            # dosya bütünlüğü ve şema uyumu (veritabanına dokunmaz)
node ops/game-catalog-import.mjs import [klasör]            # boş game_db'ye yükler; doluysa atlar
node ops/game-catalog-import.mjs import [klasör] --replace  # dolu game_db'nin ÜZERİNE yazar
```

- `export`: hesap verisi içerebilecek tablo (geri bildirim, yetki, denetim kaydı vb.) doluysa reddedilir. Manifestteki `url` alanı korunur.
- `import`: yalnız `ltz-dev` Postgres konteynerini kullanır, Game servisi çalışıyorsa durur, dökümün Flyway sürümü depodaki en yeni Game migration'ından yeniyse reddeder, yükleme tek işlemdir ve sonunda tablo satır sayıları manifestle karşılaştırılır. Gerçek ENV dosyası okunmaz.
- Klasör verilmezse `game-catalog-dump/` (depo kökü) kullanılır; `*.dump` Git'e girmez. Alıcı dosyayı bu klasöre kopyalar (dosya adı değişmez).
- Paylaşım: döküm (~180 MB) Git'e girmez. Üreten kişi dosyayı GitHub Release varlığı olarak yükler ve `ops/game-catalog.manifest.json` içindeki `url` alanına adresi yazar; `download` yalnız `github.com` ve GitHub varlık sunucularından, SHA-256 doğrulamasıyla indirir. Veri IGDB ve Steam kaynaklıdır; herkese açık paylaşmadan önce sağlayıcı kullanım şartlarını kontrol edin.
- Test: `node --test ops/game-catalog-import.test.mjs`.
- Ekip arkadaşları ve yapay zekâ ajanları için adım adım rehber: [game-catalog.md](game-catalog.md).

## ENV profilleri (env.cmd / env.ps1 / env.sh)

Windows PowerShell / CMD ve Linux/macOS için aynı profil seçimi sunulur.
Araçlar yalnızca yerel `.env` dosyasını seçilen profilin kopyası olarak yazar;
Docker veya uygulama başlatmaz.

```powershell
.\ops\env.cmd status
.\ops\env.cmd use dev
.\ops\env.cmd use prod
# PowerShell doğrudan da kullanılabilir:
.\ops\env.ps1 status
```

```sh
sh ./ops/env.sh status
sh ./ops/env.sh use dev
sh ./ops/env.sh use prod
```

## ENV'i tek komutla doldur

```sh
node ops/dev.mjs keys              # .env ve .env.dev (hangisi varsa); hiçbiri yoksa .env.dev'i .env.example'dan oluşturur
node ops/dev.mjs keys .env.local   # yalnız verilen dosya(lar)
```

Yeni klonda sıra: bu komut, sonra `.\ops\env.cmd use dev` (POSIX: `sh ./ops/env.sh use dev`). Komut güvenle tekrar
çalıştırılır: dolu satıra dokunmaz, eksik bir şey yoksa dosyayı değiştirmez, anahtar değerlerini ekrana yazmaz.

Komut **kendisi doldurur:** Auth, servis ve Mail anahtarları (`AUTH_*`, `MAIL_PAYLOAD_ENCRYPTION_KEY`), `.env.example`'da
olup dosyada hiç bulunmayan ayarlar, SMTP/bağlantı satırları (boş), `AUTH_EVENT_TARGETS=user`.
Komut **doldurmaz, siz verirsiniz** (dış sağlayıcı bilgisi; boş kalması dev'de normaldir): `STEAM_WEB_API_KEY`,
`IGDB_CLIENT_ID/SECRET`, `ITAD_API_KEY`, `GIPHY_API_KEY` (messaging GIF; boşsa GIF 503), `GOOGLE_CLIENT_ID/SECRET`, `SONAR_TOKEN`, gerçek SMTP sağlayıcı bilgileri.
`POSTGRES_PASSWORD` gibi `CHANGE_ME…` örnek parolalara da dokunmaz (var olan veritabanı hacmiyle uyuşmazlık çıkarır);
gerekirse elle değiştirin. Mail Service'i çalıştırmak için: `docs-auth/ekip-hizli-baslangic.md` §1.1.

Kaynak dosyalar repository kökündeki `.env.dev` ve `.env.prod` dosyalarıdır.
Eksik profili `.env.example` üzerinden oluşturun; var olan profili üzerine
yazmayın. Örnek parolaları değiştirin. Production profili hazırlanırken
`APP_ENV`, `SPRING_PROFILES_ACTIVE`, `COMPOSE_PROJECT_NAME`, adresler ve parolalar
da hedef ortama göre düzenlenmelidir. Uyarlama sırasında `.env.dev` içindeki
mevcut değerlere dokunulmadan eksik Sonar alanları ve rastgele bir yerel Sonar
veritabanı parolası eklendi. Otomatik production profili oluşturulmaz.

Seçim `.env` dosyasının ilk satırına `# LTZ-ACTIVE-PROFILE=dev` veya `prod`
ekler. Önceki LTZ marker'ı ile uyumludur. `status`, profilin adını ve aktif
kopyanın kaynak profille aynı olup olmadığını, değerleri göstermeden bildirir.

Aktif `.env` elle değiştirilmişse veya marker'ı yoksa profil değiştirme reddedilir.
Değişiklikleri önce ilgili profil dosyasına taşıyın. Bilerek değiştirmek için:

```powershell
.\ops\env.cmd use dev -Force
```

```sh
sh ./ops/env.sh use dev --force
```

Zorlanan değiştirmede önce `.env.bak` oluşturulur; önceki backup varsa üzerine
yazılır. `.env`, profiller, backup ve geçici ENV dosyaları Git tarafından ignore
edilir; `.env.example` paylaşılabilir şablondur.

Aktif profili seçtikten sonra Compose yığınını başlatmak için:

```powershell
docker compose up -d
```

Compose PostgreSQL, API Gateway ve uygulama servislerinin tamamını içerir; yalnız ihtiyacınız olanı da
başlatabilirsiniz (örn. `docker compose up -d postgres auth-service api-gateway`).
Çalışan uygulamaların ENV değişikliklerini alması için yeniden başlatılması
gerekir. Pre-push ve Sonar komutları ise açıkça verilen profil dosyasını kullanır;
öncesinde `use` çalıştırmak zorunlu değildir.

İlgili rehberler: [pre-push](../pre-push/pre-push.md),
[SonarQube](../sonarqube/sonarqube.md).

## Auth ENV kontrolü

`node ops/dev.mjs keys` tek komutla `.env`/`.env.dev` dosyalarını tamamlar (eksik ayarları `.env.example`'dan ekler, boş anahtarları üretir, var olan satırlara dokunmaz) ve sonunda Auth'un açılış kurallarını ve Mail Service'in şifreleme anahtarını (`MAIL_PAYLOAD_ENCRYPTION_KEY`, Base64, tam 32 bayt) kontrol eder; Mail anahtarı hatalıysa `[FAIL]` yanında "Mail Service açılmaz" yazar (Auth etkilenmez). Yalnız raporu görmek için `node ops/dev.mjs env-check` (dosyaya dokunmaz, değer yazmaz). Mail Service'i adım adım ayağa kaldırmak ve sık hatalar için: `docs-auth/ekip-hizli-baslangic.md` §1.1 ve §3. Ayrıntı: `docs-auth/ekip-hizli-baslangic.md`.
