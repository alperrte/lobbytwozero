# Game kataloğunu yerel Docker'a yükleme

Bu belge **hem ekip arkadaşları hem de onların yapay zekâ asistanları** içindir. Game servisinin hazır oyun veritabanını (yaklaşık 264 bin kayıt; listede varsayılan olarak ≈189 bin ana oyun görünür, yetişkin içerikli oyunlar "+18 oyunları göster" ile açılır) kendi bilgisayarındaki Docker'a yükler. Kataloğun kendisi için Steam, IGDB veya ITAD API anahtarı gerekmez ve oyunlar sağlayıcılardan yeniden çekilmez. **Fiyatların güncel görünmesi için ise ITAD anahtarı gerekir**, çünkü dump'taki fiyatlar en geç 48 saat sonra gizlenir ([Fiyatlar güncel kalsın](#fiyatlar-güncel-kalsın-itad-anahtarı)).

**Nasıl okunur**

| Kimsin? | Oku |
|---|---|
| İnsan, kendi başına kuracaksın | [Özet](#özet) → [Adım adım kurulum (insanlar için)](#adım-adım-kurulum-insanlar-için) |
| Yapay zekâ asistanı, kullanıcı "Game kataloğunu kur" dedi | [Özet](#özet) → [Yapay zekâ için prosedür](#yapay-zekâ-için-prosedür) → [Yapay zekâ için kurallar](#yapay-zekâ-için-kurallar) |
| Daha önce kurmuştun, ekip yeni dump verdi | [Daha önce kurduysan: yeni dump nasıl yüklenir?](#daha-önce-kurduysan-yeni-dump-nasıl-yüklenir) |
| Oyun sayfasında fiyat yerine "Fiyatın güncelliği doldu" yazıyor | [Fiyatlar güncel kalsın: ITAD anahtarı](#fiyatlar-güncel-kalsın-itad-anahtarı) |
| Bir şey ters gitti | [Sorun giderme](#sorun-giderme) |

## Özet

Her iki kitle için de aynı gerçekler:

| Konu | Değer |
|---|---|
| Ne yüklenir | Tüm `game_db` (Flyway geçmişi dahil): oyunlar, terimler, dile göre açıklamalar, DLC ilişkisi, dil desteği, medya. **Fiyat tablolarının verisi yoktur** (şema vardır); fiyatları servis kendisi doldurur |
| Dump dosyası | `ltz-game-catalog.dump` (193.117.854 bayt, adı değişmez). Git'te **yoktur**; ekipten elden alınır |
| Dump'ın konulacağı yer | Depo kökündeki **`game-catalog-dump/`** klasörü (`git pull` ile gelir; gelmezse komut açar) |
| Kurulum komutu | `node ops/dev.mjs --all` (repo kökünden) |
| Gereksinimler | Node 24.7+, Docker Desktop açık, Docker'a en az 12 GB bellek, Hardware kataloğu için internet |
| Doğrulama | `node ops/game-catalog-import.mjs verify` (veritabanına dokunmaz) |
| Başarı işareti | `GAME_CATALOG_IMPORT_RESULT {"games":264509,"flyway":175,...}` ve `✓ Game kataloğu hazır` |

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
   GAME_CATALOG_IMPORT_RESULT {"games":264509,"flyway":175,...}
   ✓ Game kataloğu hazır
   ```

   Hardware için de `✓ Hardware kataloğu hazır` çıkar. Her servis için ✓ görünür. `⚠` ile başlayan bir uyarı varsa [Sorun giderme](#sorun-giderme) bölümüne bak.
6. **Siteyi aç.** Tarayıcıda `http://localhost:5001/tr/giris`. Test hesapları: `user@ltz.com`, `creator@ltz.com`, `developer@ltz.com`, `moderator@ltz.com`, `admin@ltz.com`; parola `.env.dev` içindeki `DEV_ACCOUNTS_PASSWORD` değeridir (dosyayı kimseyle paylaşma).

Servisleri durdurmak için `node ops/dev.mjs stop` (veri kalır).

## Daha önce kurduysan: yeni dump nasıl yüklenir?

Ekip yeni bir dump dağıttığında (örneğin yeni fiyat pazarları eklendiğinde) **ne yapacağın daha önce kurup kurmadığına bağlıdır.** Aşağıdaki akış ikisi için de aynıdır; hangi yolda olduğunu komutun çıktısı söyler.

**Neden ayrı bir adım var?** Dump dosyası yalnız kaynaktır. Veriler senin Docker'ındaki PostgreSQL'dedir. Eski dosyayı silip yenisini koymak veritabanını değiştirmez. Kurulum aracı, `game_db` zaten doluysa hiçbir şeye dokunmaz ve eski veriyle devam edersin.

| Durum | Ne yaparsın |
|---|---|
| **Hiç kurmadın** (ilk kurulum) | Dump'ı `game-catalog-dump/` klasörüne koy, `node ops/dev.mjs --all` çalıştır. Başka adım yok. |
| **Daha önce kurdun** (eski dump yüklü) | Yeni dump'ı klasöre koy, `node ops/dev.mjs --all` çalıştır; çıktı "zaten dolu" derse aşağıdaki 3 komutla değiştir. |

**Adım adım (herkes için)**

1. `git pull` yap. Güncel `ops/game-catalog.manifest.json` gelir.
2. Elindeki eski `ltz-game-catalog.dump` dosyasını sil ve ekipten aldığın **yeni** dosyayı `game-catalog-dump/` klasörüne koy. Adını değiştirme.
3. Kontrol et (veritabanına dokunmaz):

   ```powershell
   node ops/game-catalog-import.mjs verify
   ```

   `Dump bütünlüğü doğrulanamadı` çıkarsa elindeki dosya hâlâ eskidir ya da eksik inmiştir; yeni dosyayı yeniden al.
4. Kurulumu çalıştır:

   ```powershell
   node ops/dev.mjs --all
   ```

5. Çıktıya bak:
   - `GAME_CATALOG_IMPORT_RESULT {...}` ve `✓ Game kataloğu hazır` → **bitti**, ilk kurulumdu.
   - `GAME_CATALOG_IMPORT_SKIPPED game_db zaten dolu` → eski katalog yüklüydü, yeni dump uygulanmadı. 6. adıma geç.
6. Eski kataloğu yenisiyle değiştir. Üç komut sırayla çalışır:

   ```powershell
   docker stop ltz-dev-game-service-1
   node ops/game-catalog-import.mjs import --replace
   docker start ltz-dev-game-service-1
   ```

   Sonunda `GAME_CATALOG_IMPORT_RESULT {"games":<oyun sayısı>,...,"replaced":true}` görünmelidir (oyun sayısı manifestteki `tables.games` ile aynı olmalı). Game servisi açılışta kendini hazırlar; fiyatlar dump'ta olmadığı için ITAD anahtarın varsa 13 pazarın (Türkiye dahil) fiyatları saatler içinde dolar.

   **Servis açılmıyor ve günlükte `Migration checksum mismatch` ya da `Validate failed` yazıyorsa:** `git pull` ile gelen kod, veritabanındaki eski migration kayıtlarıyla uyuşmuyor demektir (sende dump'ın eski bir sürümü yüklü). Bu bir veri kaybı değildir. Çözüm tam olarak bu 6. adımdır: **yeni dump'ı** `--replace` ile yükle (servis kapalıyken), sonra servisi başlat. Eski dump'la tekrar denersen aynı hata döner; `verify` komutu (3. adım) dosyanın yeni olduğunu doğrular.

**`--replace` ne siler?** Yalnız `game_db` veritabanının içeriğini: oyun kataloğunu ve senin yerel Game test kayıtlarını (geri bildirim, geliştirici yetkisi vb.). Hesapların, diğer servislerin veritabanları (Auth, Hardware, Community vb.) ve Hardware kataloğu **etkilenmez**. İşlem tek bir işlemde yapılır; hata olursa eski veri olduğu gibi kalır.

**Bu adımlar işe yaramazsa:** Docker'da `ltz-dev-postgres-1` açık olmalıdır (`docker ps`). Kapalıysa 4. adımdaki komut onu açar; sonra 6. adımı uygula. Diğer hatalar için [Sorun giderme](#sorun-giderme).

## Fiyatlar güncel kalsın: ITAD anahtarı

**Kısaca:** Katalog (oyunlar, açıklamalar, görseller, DLC ilişkisi) dump'tan gelir ve günlerce geçerli kalır. **Fiyatlar dump'ta yoktur:** fiyat tablolarının verisi dump'a bilerek konmaz (ömürleri dolunca gizlenirler ve dağıtım anında bayattır; veritabanının yaklaşık yarısıdır). Servis açılınca fiyatları kendisi doldurur. Her fiyat kaydının bir geçerlilik süresi vardır (sıcak oyunlarda 1 saat, ılıkta 6 saat, soğukta 24 saat). Süre dolunca oyun sayfasında fiyat yerine *"Fiyatın güncelliği doldu; güncel fiyat olarak gösterilmiyor."* yazar. Eski fiyat bilerek güncelmiş gibi gösterilmez.

Bu yüzden dump'ı yükledikten sonra fiyatlar ilk saatlerde boştur. Çözüm, Game servisinin fiyatları kendisinin yenilemesidir. Bunun için **tek şey gerekir: ITAD anahtarı.**

| Durum | Ne görürsün |
|---|---|
| ITAD anahtarı **yok** | Fiyat yok (yalnız **sayfasını açtığın oyunun Steam fiyatı**, Steam anahtarın varsa, kota sınırları içinde arka planda hemen alınır). Diğer mağazaların fiyatları için ITAD anahtarı gerekir. Bu bir hata değildir. |
| ITAD anahtarı **var** | Game servisi fiyatları ITAD'den yeniler. Sıra: önce son 90 gün içinde çıkmış veya 30 gün içinde çıkacak oyunlar (sıcak, 20 dk aralıkla), sonra son 2 yılın oyunları (ılık, 2 sa), en son eski oyunlar (soğuk, 8 sa). **Her oyunda ITAD'in döndürdüğü tüm mağazalar** (ABD'de oyun başına ortalama ≈1,85 teklif) gösterilir; DLC'lerin fiyatı da yenilenir. İlk dolum ITAD hız sınırı nedeniyle (5 dakikada ≈850 istek) birkaç saat sürer. Sen bir şey yapmazsın. |

**Türkiye (TRY) pazarı:** ITAD Türkiye için tüm teklifleri TL döndürür. Fanatical, Humble, GameBillet, IndieGala ve DLGamer fiyatı sabit kurla TL'ye çevirir (TRY/USD ≈50,2); Epic, GOG ve GreenManGaming bölgesel fiyat verir. Steam Türkiye'de ABD doları ile sattığından Steam satırı Türkiye pazarında gösterilmez; Türkçe arayüzde varsayılan pazar Türkiye'dir ve sayfada bu durum bir notla açıklanır.

**Fiyatın kaynağı ve tazeliği:** "Steam" mağaza satırı doğrudan Steam'in kendi mağaza fiyatından gelir (ITAD'in Steam satırının yerini alır; bağlantı düz Steam sayfasıdır). Steam fiyatı sıcak oyunlarda (yalnız canlı profilde), ITAD'in teklif veremediği oyunlarda ve sayfası açılan oyunlarda alınır; diğer mağazalar ITAD'dedir. Ücretsiz oyunda fiyat yerine "Ücretsiz" rozeti ve mesajı, henüz çıkmamış oyunda "Yakında" rozeti ve mesajı görünür; yetişkin içerikli (Steam tanımlayıcı 3/4) oyunlar varsayılan listelerde gizlidir, "+18 oyunları göster" onay kutusuyla açılır. Bu bilgiler Steam'den (anahtarınla) gelir ve dump'a gerek duymaz.

**Anahtar nasıl eklenir**

1. Kendi ITAD anahtarını al: ITAD'e giriş yapıp [isthereanydeal.com/apps](https://isthereanydeal.com/apps/) sayfasında uygulama kaydet; anahtar kayıtta üretilir. ITAD'in API koşulları geçerlidir (özel kullanım serbest; ticari kullanım yalnız uygulama herkese açıksa). Ekip ortak anahtar kullanmaya karar verirse anahtarı Game servisinin sahibinden al. Anahtarı sohbete, koda, belgeye veya commit'e **yazma**; yalnız kendi bilgisayarındaki dosyaya gider.
2. Repo kökündeki **`.env.dev`** dosyasını aç (Git'e girmez, yalnız sende durur). `ITAD_API_KEY=` satırı varsa değerini yaz, yoksa dosyanın sonuna ekle:

   ```text
   ITAD_API_KEY=<aldığın anahtar>
   ```

3. Kaydet ve kurulum komutunu yeniden çalıştır. Game servisi yeni anahtarla kendiliğinden yeniden başlar:

   ```powershell
   node ops/dev.mjs --all
   ```

   (Yalnız Game için: `node ops/dev.mjs --with=game-service`.) Katalog doluysa içe aktarma "zaten dolu" diye atlanır; bu normaldir, dump'a dokunulmaz.
4. Birkaç dakika sonra `http://localhost:5001/tr/oyunlar` sayfasında yeni çıkmış bir oyun aç. Onun fiyatı yeniden görünmelidir. Eski bir oyunun fiyatı sırası gelene kadar (saatler) "güncelliği doldu" kalabilir.

**Bilmen gerekenler**

- Fiyat yenileme **varsayılan olarak açıktır**; başka bir ayar gerekmez. Anahtar boşsa servis ITAD'e hiç istek atmaz.
- `ITAD_API_KEY` yalnız fiyatları yeniler. Yeni oyun, DLC ve erken erişim bilgisi Steam'den gelir (`STEAM_WEB_API_KEY` ve `STEAM_CATALOG_ENABLED=true`, `GAME_CATALOG_SYNC_ENABLED=true`; ekipte bunlar zaten açık), IGDB zenginleştirmesi ayrı anahtar ister.
- ITAD'in hız sınırı uygulama başına 5 dakikada 1000 istektir (doğrulanmış e-posta ile). Game servisi kendi içinde 5 dakikada en çok ≈850 istek atar (istemci tarafı hız sınırlayıcı), böylece kendi kaynaklı 429 oluşmaz. **İki kişi aynı anahtarla aynı anda yetişmeye çalışırsa** sınır aşılır; servis durup kendiliğinden yeniden dener, fiyatlar geç yetişir ama bozulmaz. Herkesin kendi anahtarı olması bu yüzden daha iyidir.
- Servis kapalıyken fiyat yenilenmez. Bilgisayarı kapatıp uzun süre sonra açarsan fiyatlar önce "güncelliği doldu" olur, servis açıldıktan sonra yeniden dolar.
- Yapay zekâ asistanı kullanıyorsan anahtarı sen elle yaz. Asistan `.env.dev` dosyasını okumaz ve değiştirmez.

## Dump politikası: ne için, ne zaman yenilenir?

**Dump bir tazelik aracı değil, ilk kurulum aracıdır.** Oyun satırları sıfırdan birkaç saatte oluşur ama zenginleştirme DEV'de yaklaşık iki gün sürdü; her geliştiricinin bunu kendi API kotasıyla tekrarlaması istenmez. **Fiyat verisi dump'a girmez** (şema girer): fiyatlar zaten ömürleri dolunca gizlenir ve her geliştirici kendi ITAD anahtarıyla bunları saatler içinde doldurur. Dump'ı yükledikten sonra servis kendini yetiştirir ve **sürekli güncel kalır; yeni oyun, DLC, fiyat veya durum için yeni dump gerekmez:**

| Veri | Kim günceller | Nasıl (dev / canlı) |
|---|---|---|
| Yeni oyun ve DLC keşfi | Steam artımlı tarama (`ltz.game.catalog.sync-interval`) | 1 sa / 15 dk; yalnız değişenleri ister |
| Yeni oyunun/DLC'nin türü, terimi, çıkış tarihi | Steam mağaza türü yedeği (`type=0` oyun, `type=4` DLC) | dakikalar; IGDB'yi beklemez. IGDB sonra tanırsa tür, meta veri ve terimler IGDB'ye geçer |
| DLC → ana oyun bağlama | Steam `related_items.parent_appid` (100 DLC/istek) + IGDB | ana oyun sınıflanınca ≤6 sa |
| Erken erişim, ücretsiz, yakında, +18, mağazadan kaldırılma | Steam çıkış durumu taraması | sıcak 1 sa / 15 dk; diğerleri 6 sa / 4 sa |
| Detay (açıklama, tarih, şirket, dil, kapak) | IGDB toplu yenileme (anahtar varsa) | sıcak 1 sa / 15 dk; diğerleri 6 sa / 2 sa |
| IGDB kanıtı olmayan kaydı yeniden sorma | IGDB sınıflandırma | yeni 6 sa / 1 sa; eski 24 sa / 12 sa |
| Fiyat (tüm mağazalar, DLC dahil, TR dahil) | ITAD + Steam | sıcak 20 dk / ılık 2 sa / soğuk 8 sa (dev ve canlı); sayfası açılan oyun hemen |

- **Düzenli yeni dump gerekmez.** Yeni dump yalnız büyük şema/veri değişiminde ve yeni kurulacak kişilerin ilk açılışı hızlansın istenirse alınır. Eski dump + yeni migration çalışır: kurulum aracı yalnız dump depodan DAHA YENİ şemadaysa reddeder; Game servisi açılışta eksik migration'ları uygular. **İstisna:** V31 ve V173 tablo tanımları değiştiği için (Steam durum kolonları, fiyat kaynağı), bu değişiklikten ÖNCEKİ bir dump yüklemiş olanlar yeni dump'ı `--replace` ile yüklemelidir (aşağıdaki `Migration checksum mismatch` notu).
- **Dump'ı yükledikten sonra** fiyatlar boştur; servis çalıştıkça (ve ITAD anahtarı varsa) saatler içinde dolar. Oyunlar, açıklamalar ve görseller dump'la hemen görünür (yerel veritabanından okunur).
- **Canlıda** dump sürekli işletmede gerekmez (kalıcı veritabanı, 15 dakikalık artımlı tarama + yenileme işleri). İlk açılışta canlı veritabanı bir kez dump'tan (`pg_restore`) doldurulur ve servisler canlı anahtarlarla `prod` profilinde (`SPRING_PROFILES_ACTIVE=prod`) birkaç saat "ısınır"; canlıdaki "dump" = günlük `pg_dump` yedeğidir. Kullanıcılar veriyi yerel veritabanından okur, dış servisi beklemez.
- **IGDB isteğe bağlıdır:** IGDB anahtarı olmadan da sistem tam çalışır (Steam yedeği tür, terim, tarih ve DLC bağlama sağlar). IGDB'nin ücretsiz kullanımı yalnız ticari olmayan uygulamalar içindir; ticari lansmandan önce IGDB (Twitch) ile ortaklık görüşülmelidir. Ortaklık gelene kadar canlıda anahtar boş bırakılabilir.
- **Profiller:** varsayılan `application.properties` geliştirme makineleri içindir (ortak Steam anahtarı günde 100.000 çağrıyla sınırlı olduğundan makine başına 20.000 bütçe, süreler daha uzun). Canlı değerler `application-prod.properties` içindedir (Steam bütçesi 90.000); yeni ENV değişkeni gerekmez. Arka plan taramaları günlük Steam kotasının %75'inde durur; sayfası açılan ve sıcak oyunlar sınırın sonuna kadar çalışır.

### Sistem kendini nasıl güncel tutar, nasıl denetlenir?

`node ops/game-freshness-report.mjs` (salt okunur, ENV okumaz; `--json` seçeneği vardır) şunları raporlar ve eşik aşılırsa uyarı yazıp 1 koduyla çıkar: sınıflanmamış kayıt payı, ana oyun/DLC/listede görünen sayısı, +18 ve mağazadan kaldırılmış sayıları (kaldırılmış payı >%2 ise toplu yanlış delist uyarısı), +18/ücretsiz/yakında bilgisinin doluluk payı, son başarılı Steam turunun yaşı, ABD pazarında güncel fiyatlı oyun payı ve son saatteki hız sınırı günlük satırları. Birleşik V176 uygulanmadan önce kolon eksikliği hatası verir (beklenen).

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

- Beklenen: `GAME_CATALOG_VERIFY_RESULT {"games":264509,"flyway":175}`.
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
- Başarı: `GAME_CATALOG_IMPORT_RESULT {"games":264509,"flyway":175,...}` ve `✓ Game kataloğu hazır`.
- `GAME_CATALOG_IMPORT_SKIPPED game_db zaten dolu (...)`: veritabanında daha önce yüklenmiş bir katalog var, hiçbir şeye dokunulmadı. Kurulum bozulmadı, ama **yeni dump uygulanmadı**. Kullanıcıya şunu sor: "Game veritabanında zaten bir katalog var. Yeni dump ile değiştirmemi ister misin? Bu işlem `game_db` içeriğini siler (hesaplar ve diğer servisler etkilenmez)." Kullanıcı açıkça "evet" derse [Daha önce kurduysan](#daha-önce-kurduysan-yeni-dump-nasıl-yüklenir) bölümünün 6. adımını uygula. Cevap vermezse veya "hayır" derse dokunma.
- Başarısızlık: `⚠ Game kataloğu ...` uyarısı varsa katalog yüklenmemiştir. Başarılı sayma; nedeni [Sorun giderme](#sorun-giderme) tablosundan bul.
- Game servisi için ✓ (ya da "healthy") görünmeli.

**6. Kullanıcıya raporla**
Şunları kısaca yaz: hangi komutu çalıştırdın, `GAME_CATALOG_*` satırları, servislerin durumu, varsa uyarılar. Parola, anahtar veya ortam değişkeni değeri yazma. Frontend'i kendisinin başlatması gerektiğini ve test hesaplarının parolasının `.env.dev` içinde `DEV_ACCOUNTS_PASSWORD` olarak durduğunu (değerini yazmadan) hatırlat.

## Yapay zekâ için kurallar

Bu depoda çalışan ajanlar `AGENTS.md` kurallarına uyar. Bu belgeye özel olarak:

- **Asla, kullanıcı açıkça istemeden:** `import --replace` (yalnız yukarıdaki SKIPPED sorusuna kullanıcı "evet" derse), `node ops/dev.mjs reset`, `docker compose ... down -v`, dump'ı Git'e ekleme, `git commit/push`, `export`. `--replace` mevcut oyun verisini siler.
- `.env`, `.env.dev`, `frontend/.env.local` dosyalarını okuma, yazdırma veya kopyalama. Katalog araçları bunlara ihtiyaç duymaz.
- Kullanıcı fiyatların görünmediğini söylerse nedeni [Fiyatlar güncel kalsın](#fiyatlar-güncel-kalsın-itad-anahtarı) bölümünden açıkla. ITAD anahtarını kullanıcı kendisi `.env.dev` dosyasına yazar; asistan anahtar istemez, dosyaya yazmaz, başka dosyaya veya sohbete kopyalamaz.
- `ltz-game-catalog.dump` dosyasını Git'e ekleme (`*.dump` zaten `.gitignore`'dadır), başka yere yükleme veya paylaşma. Dağıtım kullanıcının kararıdır.
- `ops/game-catalog.manifest.json` içindeki `sha256`, `bytes` ve `tables` alanlarını elle değiştirme. Bu değerler dump'tan `export` ile üretilir; elle değişirse `verify` ve `import` dosyayı reddeder.
- Dump'ı yeniden üretmek (`export`) yalnız dump sahibinin işidir. Yeni dump yeni bir SHA-256 üretir ve dağıtılan dosyayı geçersiz kılar.
- Kullanıcıdan komut çıktısı istenirse yalnız `GAME_CATALOG_*` satırlarını ve hata mesajını iste; ortam değişkeni veya parola içerebilecek çıktıyı isteme.
- Game, Library ve Review dışındaki servislerin kodunu bu iş için değiştirme.

## Ne yüklenir?

Beklenen içerik (doğrulama için):

| Veri | Beklenen sayı |
|---|---:|
| Oyun (`games`) | 264.509 |
| Terim bağlantısı (`game_terms`) | 1.693.604 |
| Dile göre açıklama (`game_localized_descriptions`) | 26.411 |
| Ana oyuna bağlı DLC (`parent_game_id` dolu) | 56446 (DLC'lerin yaklaşık %80'i; kalanı servis çalıştıkça bağlanır) |
| Fiyat teklifi (`game_price_quotes`) | 0 (fiyat verisi dump'a girmez; şema vardır, servis 13 pazarın fiyatlarını kendisi doldurur) |
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
| `Dump bütünlüğü doğrulanamadı` | Dosya eksik inmiş, bozulmuş, sonradan değiştirilmiş **ya da `git pull` ile yeni manifest geldi ama sende hâlâ eski dump var** | Ekipten güncel dosyayı al; boyutu `ops/game-catalog.manifest.json` içindeki `bytes` ile aynı olmalı (şu an 190.320.977). Eski dosyayı silip yenisini koy |
| `GAME_CATALOG_IMPORT_SKIPPED game_db zaten dolu` | Daha önce katalog yüklenmişti; yeni dump uygulanmadı | [Daha önce kurduysan](#daha-önce-kurduysan-yeni-dump-nasıl-yüklenir) bölümünün 6. adımı |
| Game servisi açılmıyor; günlükte `Migration checksum mismatch` / `Validate failed` | `git pull` ile gelen kod, veritabanındaki eski dump'ın migration kayıtlarıyla uyuşmuyor (veri kaybı yok) | Yeni dump'ı al ve aynı bölümün 6. adımındaki `--replace` ile yükle |
| `Dump V... şemasıyla alınmış, bu depodaki en yeni Game migration V...` | Depo dalın eskimiş | `git pull` |
| `Game servisi çalışıyor; önce durdurun` | Game konteyneri açık (`import` elle çalıştırıldı) | `node ops/dev.mjs stop`, sonra `node ops/dev.mjs --all` ile yeniden başlat (katalog Game'den önce yüklenir) |
| `Yalnız ltz-dev postgres konteyneri kullanılabilir` | Postgres başka bir Compose projesiyle açılmış | `node ops/dev.mjs` ile `ltz-dev` yığınını başlat |
| `... GB ister, Docker'a ... GB ayrılmış` | Docker belleği yetersiz | Docker'a daha fazla bellek ver (en az 12 GB) veya yalnız Game için `--with=game-service` kullan |
| Oyunlar var ama yeni oyun gelmiyor | Steam anahtarı boş ya da `.env.dev` içinde `STEAM_CATALOG_ENABLED` ve `GAME_CATALOG_SYNC_ENABLED` `true` değil; katalog senkronu çalışmaz | Üçünü de kontrol et (`STEAM_WEB_API_KEY` dolu, iki bayrak `true`), sonra `node ops/dev.mjs --all` ile yeniden başlat. Anahtarı ekip liderinden al |
| Oyun sayfasında fiyat yok, "Fiyatın güncelliği doldu" yazıyor | Dump'taki fiyatların süresi doldu ve ITAD anahtarı olmadığı için yenilenmiyor | [Fiyatlar güncel kalsın: ITAD anahtarı](#fiyatlar-güncel-kalsın-itad-anahtarı) |
| ITAD anahtarını ekledim ama fiyat gelmiyor | Servis yeni anahtarla yeniden başlamadı, anahtar hatalı ya da ITAD hız sınırı | `node ops/dev.mjs --all` ile yeniden başlat; `docker logs ltz-dev-game-service-1` içinde `PRICE_` ile başlayan kodlara bak (`PRICE_RATE_LIMITED` geçicidir, kendiliğinden yeniden dener) |

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
