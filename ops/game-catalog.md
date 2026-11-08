# Game kataloğunu yerel Docker'a yükleme

Bu belge **hem ekip arkadaşları hem de onların yapay zekâ asistanları** içindir. Game servisinin hazır oyun veritabanını (yaklaşık 264 bin oyun) kendi bilgisayarındaki Docker'a yükler. Steam, IGDB veya ITAD API anahtarı gerekmez ve oyunlar sağlayıcılardan yeniden çekilmez.

**Nasıl okunur**

| Kimsin? | Oku |
|---|---|
| İnsan, kendi başına kuracaksın | [Özet](#özet) → [Adım adım kurulum (insanlar için)](#adım-adım-kurulum-insanlar-için) |
| Yapay zekâ asistanı, kullanıcı "Game kataloğunu kur" dedi | [Özet](#özet) → [Yapay zekâ için prosedür](#yapay-zekâ-için-prosedür) → [Yapay zekâ için kurallar](#yapay-zekâ-için-kurallar) |
| Bir şey ters gitti | [Sorun giderme](#sorun-giderme) |

## Özet

Her iki kitle için de aynı gerçekler:

| Konu | Değer |
|---|---|
| Ne yüklenir | Tüm `game_db` (Flyway geçmişi dahil): oyunlar, terimler, dile göre açıklamalar, DLC ilişkisi, dil desteği, medya, fiyatlar |
| Dump dosyası | `ltz-game-catalog.dump` (182.603.160 bayt, adı değişmez). Git'te **yoktur**; ekipten elden alınır |
| Dump'ın konulacağı yer | Depo kökündeki **`game-catalog-dump/`** klasörü (`git pull` ile gelir; gelmezse komut açar) |
| Kurulum komutu | `node ops/dev.mjs --all` (repo kökünden) |
| Gereksinimler | Node 24.7+, Docker Desktop açık, Docker'a en az 12 GB bellek, Hardware kataloğu için internet |
| Doğrulama | `node ops/game-catalog-import.mjs verify` (veritabanına dokunmaz) |
| Başarı işareti | `GAME_CATALOG_IMPORT_RESULT {"games":263671,"flyway":175,...}` ve `✓ Game kataloğu hazır` |

`--all` tüm servisleri kaldırır. Game kataloğu Game servisi, Hardware kataloğu Hardware servisi başlamadan **önce** yüklenir. Bellek yetmiyorsa yalnız Game için `node ops/dev.mjs --with=game-service` kullanılabilir. `--no-catalog` katalog yüklemelerini atlar.

## Adım adım kurulum (insanlar için)

Terminalde yazacağın tek şey iki komuttur; dosyayı klasöre koymak sürükle-bırakla yapılır. Yapay zekâ asistanı kullanıyorsan ona yalnız **"Game kataloğunu kur."** de; aşağıdaki "Yapay zekâ için prosedür"ü o uygular ve sana tek soru sorar (dump dosyası bilgisayarında nerede).

1. **Hazırlık.** Docker Desktop'ı aç. Repo klasöründe terminal aç (VS Code'da *Terminal → New Terminal*). Node 24.7 veya üstü kurulu olmalı.
2. **Güncelle.** Terminale yaz:

   ```powershell
   git pull
   ```

   Bu komut betikleri ve **`game-catalog-dump/` klasörünü** getirir. Klasör otomatik oluşur; bir sebepten gelmezse 4. adımdaki komut onu kendisi açar, elle oluşturman gerekmez.
3. **Dump dosyasını koy (4. adımdan ÖNCE).** Dosya Git'te **yoktur**, ekipten (OneDrive, Drive, USB vb.) alınır. Dosya Gezgini'nde `ltz-game-catalog.dump` dosyasını repodaki `game-catalog-dump` klasörüne sürükleyip bırak. Dosyanın adını değiştirme. Terminal gerekmez. **Sıra önemli:** katalog, Game servisi başlamadan önce klasörden yüklenir. Dosya o anda yoksa katalog atlanır ve Game boş başlar (düzeltmesi için [Sorun giderme](#sorun-giderme)).
4. **Kurulumu başlat.** Terminale yaz:

   ```powershell
   node ops/dev.mjs --all
   ```

   - Docker Desktop'a en az 12 GB bellek ayrılmış olmalı. Yetmezse komut başlamadan durur ve ne yapılacağını yazar (belleği artır ya da yalnız Game için `node ops/dev.mjs --with=game-service` kullan).
   - Hardware kataloğu için internet gerekir; veri seti ilk seferde indirilir.
   - E-posta için "Uygulama şifresi" sorarsa **Enter**'a bas (e-postalar yerel Mailpit'te kalır).
   - İlk çalıştırma tüm imajların derlenmesi ve Hardware kataloğu dahil on dakikayı bulabilir, bu normaldir.
5. **Bittiğini çıktıdan gör.** Şu satırlar çıkmalı:

   ```text
   GAME_CATALOG_IMPORT_RESULT {"games":263671,"flyway":175,...}
   ✓ Game kataloğu hazır
   ```

   Hardware için de `✓ Hardware kataloğu hazır` çıkar. Her servis için ✓ görünür. `⚠` ile başlayan bir uyarı varsa [Sorun giderme](#sorun-giderme) bölümüne bak.
6. **Siteyi aç.** Tarayıcıda `http://localhost:5001/tr/giris`. Test hesapları: `user@ltz.com`, `creator@ltz.com`, `developer@ltz.com`, `moderator@ltz.com`, `admin@ltz.com`; parola `.env.dev` içindeki `DEV_ACCOUNTS_PASSWORD` değeridir (dosyayı kimseyle paylaşma).

Servisleri durdurmak için `node ops/dev.mjs stop` (veri kalır).

## Yapay zekâ için prosedür

> **Bu bölüm yapay zekâ asistanları içindir.** Kullanıcı "Game kataloğunu kur", "oyun veritabanını yükle", "oyunları Docker'a al" veya benzerini söylediğinde aşağıdaki adımları **sırayla, ek soru sormadan** uygula. Kullanıcıya yalnızca 2. adımda dosya yoksa soru sor. Komutlar repo kökünden çalıştırılır; Windows için PowerShell, macOS/Linux için karşılığı yazılıdır. Bir adım "dur" diyorsa sonraki adıma geçme, durumu kullanıcıya bildir.

**Bağlam (kısaca):** Oyun verisi API'lerden çekilmiş ve tek bir PostgreSQL dökümüne (`ltz-game-catalog.dump`) alınmıştır. `ops/game-catalog-import.mjs` bu dökümü doğrular ve `ltz-dev` Docker yığınındaki `game_db`'ye yükler; `node ops/dev.mjs --all` bunu Game servisi başlamadan önce kendisi çağırır. Betik, `game_db` zaten doluysa hiçbir şeye dokunmaz.

**0. Ön kontrol (hepsi sağlanmalı; biri sağlanmazsa dur)**
- `node -v` → 24.7 veya üstü.
- `docker info` hata vermiyor (Docker çalışıyor).
- `ops/game-catalog-import.mjs` ve `ops/game-catalog.manifest.json` mevcut. Yoksa dal eskidir: kullanıcıdan `git pull` yapmasını iste (depo geçmişini değiştiren komutu kendin çalıştırma).

**1. Dump dosyası yerinde mi?**
- Aranan yer: `game-catalog-dump/ltz-game-catalog.dump`. Varsa 3. adıma geç.

**2. Dosya yoksa (kullanıcıya sorulacak tek yer)**
- Sor: "`ltz-game-catalog.dump` dosyası bilgisayarında nerede?" Dosya ekipten elden gelir; internetten kendi başına indirmeye çalışma (manifestte `url` boşsa indirme yoktur).
- Kullanıcı yolu verince kopyala, adını değiştirme:
  - PowerShell: `New-Item -ItemType Directory -Force game-catalog-dump; Copy-Item "<verilen yol>" game-catalog-dump\ltz-game-catalog.dump`
  - macOS/Linux: `mkdir -p game-catalog-dump && cp "<verilen yol>" game-catalog-dump/ltz-game-catalog.dump`

**3. Dosyayı doğrula**

```sh
node ops/game-catalog-import.mjs verify
```

- Beklenen: `GAME_CATALOG_VERIFY_RESULT {"games":263671,"flyway":175}`.
- Hata verirse **dur**: [Sorun giderme](#sorun-giderme) tablosundan nedeni bul ve kullanıcıya söyle (dosyayı yeniden alması gerekebilir). Veritabanına henüz dokunulmadı.

**4. Kur**

```sh
node ops/dev.mjs --all --no-mail --no-frontend
```

- `--no-mail` şifre istemini, `--no-frontend` sonsuza kadar çalışan geliştirme sunucusunu engeller; komut kendiliğinden biter. Frontend'i kullanıcı kendisi `cd frontend && npx next dev -H localhost -p 5001` ile başlatır.
- Docker belleği en az 12 GB gerekir; yetmezse komut başlamadan kendisi durur. `--force` ekleme, kullanıcıya söyle. Kullanıcı daha az servis istiyorsa veya bellek yetmiyorsa `--all` yerine `--with=game-service` kullan.
- Hardware kataloğu için internet gerekir (veri seti ilk seferde indirilir).
- Komut uzun sürer (ilk çalıştırmada tüm imajların derlenmesi ve Hardware kataloğu dahil on dakikayı bulabilir). Arka planda çalıştır ve bitmesini bekle; sürekli yoklama yapma.
- `.env.dev` dosyasını **okuma, açma, yazdırma**. Gerekli anahtarları komut kendisi üretir.

**5. Başarıyı kontrol et**
Katalog adımı hata verse bile `dev.mjs` kurulumu sürdürür ve çıkış kodu 0 olabilir; bu yüzden **çıktıyı oku**:
- Başarı: `GAME_CATALOG_IMPORT_RESULT {"games":263671,"flyway":175,...}` ve `✓ Game kataloğu hazır`, **ya da** `GAME_CATALOG_IMPORT_SKIPPED game_db zaten dolu (...)` (veritabanı zaten doluydu, hiçbir şeye dokunulmadı; bu da başarıdır).
- Başarısızlık: `⚠ Game kataloğu ...` uyarısı varsa katalog yüklenmemiştir. Başarılı sayma; nedeni [Sorun giderme](#sorun-giderme) tablosundan bul.
- Game servisi için ✓ (ya da "healthy") görünmeli.

**6. Kullanıcıya raporla**
Şunları kısaca yaz: hangi komutu çalıştırdın, `GAME_CATALOG_*` satırları, servislerin durumu, varsa uyarılar. Parola, anahtar veya ortam değişkeni değeri yazma. Frontend'i kendisinin başlatması gerektiğini ve test hesaplarının parolasının `.env.dev` içinde `DEV_ACCOUNTS_PASSWORD` olarak durduğunu (değerini yazmadan) hatırlat.

## Yapay zekâ için kurallar

Bu depoda çalışan ajanlar `AGENTS.md` kurallarına uyar. Bu belgeye özel olarak:

- **Asla, kullanıcı açıkça istemeden:** `import --replace`, `node ops/dev.mjs reset`, `docker compose ... down -v`, dump'ı Git'e ekleme, `git commit/push`, `export`. `--replace` mevcut oyun verisini siler.
- `.env`, `.env.dev`, `frontend/.env.local` dosyalarını okuma, yazdırma veya kopyalama. Katalog araçları bunlara ihtiyaç duymaz.
- `ltz-game-catalog.dump` dosyasını Git'e ekleme (`*.dump` zaten `.gitignore`'dadır), başka yere yükleme veya paylaşma. Dağıtım kullanıcının kararıdır.
- `ops/game-catalog.manifest.json` içindeki `sha256`, `bytes` ve `tables` alanlarını elle değiştirme. Bu değerler dump'tan `export` ile üretilir; elle değişirse `verify` ve `import` dosyayı reddeder.
- Dump'ı yeniden üretmek (`export`) yalnız dump sahibinin işidir. Yeni dump yeni bir SHA-256 üretir ve dağıtılan dosyayı geçersiz kılar.
- Kullanıcıdan komut çıktısı istenirse yalnız `GAME_CATALOG_*` satırlarını ve hata mesajını iste; ortam değişkeni veya parola içerebilecek çıktıyı isteme.
- Game, Library ve Review dışındaki servislerin kodunu bu iş için değiştirme.

## Ne yüklenir?

Beklenen içerik (doğrulama için):

| Veri | Beklenen sayı |
|---|---:|
| Oyun (`games`) | 263.671 |
| Terim bağlantısı (`game_terms`) | 997.454 |
| Dile göre açıklama (`game_localized_descriptions`) | 19.226 |
| Ana oyuna bağlı DLC (`parent_game_id` dolu) | 17.481 |
| Flyway sürümü | 175 |

Katalog kullanıcı verisi taşımaz: hesap kimliği içerebilecek tablolar (geri bildirim, yetki, denetim kaydı vb.) boştur. Kendi test hesapların `node ops/dev.mjs` ile ayrıca oluşturulur.

## Komutlar

Klasör verilmezse `game-catalog-dump/` kullanılır.

| Komut | Ne yapar | Veritabanına yazar mı? |
|---|---|---|
| `node ops/game-catalog-import.mjs verify` | Dosyanın boyutunu ve SHA-256'sını `ops/game-catalog.manifest.json` ile karşılaştırır, şema uyumunu denetler | Hayır |
| `node ops/game-catalog-import.mjs import` | Boş `game_db`'ye yükler; doluysa atlar | Evet (yalnız boşsa) |
| `node ops/game-catalog-import.mjs import --replace` | Dolu `game_db`'nin **üzerine yazar** | Evet, mevcut veri silinir |
| `node ops/game-catalog-import.mjs download` | Manifestte `url` varsa dosyayı indirir (şu an `url` boştur) | Hayır |
| `node ops/game-catalog-import.mjs export [klasör]` | Çalışan DEV Postgres'inden yeni dump üretir (yalnız dump'ı hazırlayan kişi) | Hayır |
| `node ops/dev.mjs ... --no-catalog` | Katalog yüklemelerini (Game ve Hardware) atlar | Hayır |

`import`; yalnız `ltz-dev` Postgres konteynerini kullanır, Game servisi çalışırken çalışmaz, yüklemeyi tek işlemde yapar ve sonunda tablo satır sayılarını manifestle karşılaştırır. Betik hata durumunda çıkış kodu 1, başarıda ve "zaten dolu" atlamasında 0 verir. Gerçek ENV dosyası okunmaz.

## Sorun giderme

| Belirti | Neden | Çözüm |
|---|---|---|
| `Dump dosyası bulunamadı: ...` ve `Game kataloğu indirilemedi; atlandı` (Game boş başladı) | Dosya `game-catalog-dump/` içinde değildi, manifestte `url` yok | Dosyayı `game-catalog-dump/` klasörüne koy, sonra `node ops/dev.mjs stop` ve `node ops/dev.mjs --all` çalıştır (Game boş olduğu için katalog bu sefer yüklenir) |
| `Dump bütünlüğü doğrulanamadı` | Dosya eksik inmiş, bozulmuş veya sonradan değiştirilmiş | Dosyayı yeniden al; boyutu 182.603.160 bayt olmalı |
| `Dump V... şemasıyla alınmış, bu depodaki en yeni Game migration V...` | Depo dalın eskimiş | `git pull` |
| `Game servisi çalışıyor; önce durdurun` | Game konteyneri açık (`import` elle çalıştırıldı) | `node ops/dev.mjs stop`, sonra `node ops/dev.mjs --all` ile yeniden başlat (katalog Game'den önce yüklenir) |
| `Yalnız ltz-dev postgres konteyneri kullanılabilir` | Postgres başka bir Compose projesiyle açılmış | `node ops/dev.mjs` ile `ltz-dev` yığınını başlat |
| `... GB ister, Docker'a ... GB ayrılmış` | Docker belleği yetersiz | Docker'a daha fazla bellek ver (en az 12 GB) veya yalnız Game için `--with=game-service` kullan |
| Oyunlar var ama yeni oyun gelmiyor | API anahtarı yok, sağlayıcı senkronu çalışmaz | Beklenen davranış; anahtar eklemek için ekip lideriyle konuş |

`node ops/dev.mjs reset` tüm yerel veriyi (Game kataloğu dahil) siler. Dump dosyası klasörde durduğu sürece `node ops/dev.mjs --all` kataloğu yeniden yükler.

## Dosyalar

| Dosya | Görev |
|---|---|
| `ops/game-catalog-import.mjs` | Dışa aktarma, indirme, doğrulama ve içe aktarma betiği |
| `ops/game-catalog-import.test.mjs` | Betiğin testi: `node --test ops/game-catalog-import.test.mjs` |
| `ops/game-catalog.manifest.json` | Dump'ın SHA-256, boyut, Flyway sürümü, tablo satır sayıları, (varsa) indirme adresi |
| `ops/dev.mjs` | `--all` ve `--with=game-service` ile kataloğu Game başlamadan önce yükler |
| `game-catalog-dump/` | Dump dosyasının konulduğu klasör (içeriği Git'e girmez) |

Hardware PC parça kataloğu için ayrı bir araç vardır: `ops/hardware-catalog-import.mjs` ([ops.md](ops.md)).
