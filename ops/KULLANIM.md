# LTZ yerel kurulum kılavuzu (aşama aşama)

Bu kılavuz, `node ops/dev.mjs` tek komutunu **parçalara böler**: önce secret/ENV, sonra servisleri **sen Docker'dan** kaldırırsın, katalog import'unu ayrıca yaparsın. Böylece bellek yetmeyen servisler, yanlış ENV dosyası ve takılan istemler gibi sorunlardan kaçınırsın.

Genel kurallar:

- Komutlar repository kökünden çalıştırılır (PowerShell).
- Gereksinim: Node 24.7+, Docker Desktop açık.
- `.env`, `.env.dev` ve `frontend/.env.local` dosyalarını açıp içeriğini paylaşma; araçlar bunları senin yerine okur/yazar.
- Compose proje adı her zaman `ltz-dev`'dir ve her Docker komutunda `--env-file .env.dev --profile dev-mail` kullanılır. **Düz `docker compose up` kullanma:** `.env` dosyasını okur, mailpit'i başlatmaz ve tüm servisleri kaldırıp belleği aşar.

Kolaylık için bu oturumda bir kez tanımla:

```powershell
$C = @("compose","-p","ltz-dev","--env-file",".env.dev","--profile","dev-mail")
# kullanım: docker @C up -d --build postgres mailpit
```

---

## Aşama 1 — Secret ve ENV (üret + doğrula)

Docker gerekmez; hiçbir servis başlamaz.

```powershell
node ops/dev.mjs keys          # eksik anahtarları üretir (.env ve .env.dev), sonra kontrol sonucunu yazar
node ops/dev.mjs env-check     # dosyalara dokunmadan yalnız doğrular
```

Beklenen çıktı: her anahtar için `[OK  ]` (`AUTH_OTP_HMAC_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`, `AUTH_JWT_PRIVATE_KEY`, `MAIL_PAYLOAD_ENCRYPTION_KEY`) ve sonda "Hata yok". Bir anahtar `[HATA]` ise önce onu düzelt; sonraki aşamalara geçme.

Anahtarları yenilemek gerekirse: `node ops/dev.mjs keys --rotate-totp` / `--rotate-hmac` (mevcut TOTP kayıtları geçersiz olur; yalnız dev'de).

**E-posta (isteğe bağlı):**

```powershell
node ops/dev.mjs mail status   # Mailpit mi gerçek SMTP mi, şifre dolu mu
node ops/dev.mjs mail on       # gerçek e-posta: Google uygulama şifresi sorar (yazarken görünmez)
node ops/dev.mjs mail off      # Mailpit'e dön (gerçek mail gitmez)
```

Gerçek e-posta için gereken, hesabın normal parolası değil, 16 harfli **Google uygulama şifresidir**. Gerek yoksa Mailpit'te kal (varsayılan). Uygulama şifresini sohbete ya da repoya yazma.

---

## Aşama 2 — Servisleri Docker'dan kaldır

Önce bellek payını düşün: Docker'a ayrılan bellek, servis başına yaklaşık 550 MB (Java) + Postgres 150 MB + Mailpit 30 MB kadar olmalı, üzerine %15 pay bırak. 7,8 GB için çekirdek + yaklaşık 8 ek servis sığar. Tümünü (~9,8 GB) kaldırmak için Docker'a en az 12 GB gerekir.

**Çekirdek (giriş, kayıt, profil, e-posta):**

```powershell
docker @C up -d --build postgres mailpit mail-service auth-service user-service api-gateway
docker @C ps
```

**Ek servisler** (ihtiyacın kadar; ör. Hardware hariç iş için community):

```powershell
docker @C up -d --build community-service
```

Sağlık kontrolü: `docker @C ps` çıktısında hepsi `healthy` olana kadar bekle. Bir servis düşerse: `docker @C logs <servis> --tail 50`.

Durdurmak için:

```powershell
docker @C down              # konteynerleri durdurur; veritabanı verisi kalır
# docker @C down -v         # DİKKAT: tüm yerel veriyi siler
```

---

## Aşama 3 — Test hesapları

Çekirdek servisler `healthy` olduktan sonra:

```powershell
node ops/dev.mjs accounts          # her role bir test hesabı oluşturur/günceller ve girişi doğrular
node ops/dev.mjs accounts list     # hesaplar ve roller
node ops/dev.mjs accounts totp admin   # admin/moderator için anlık 2FA kodu
```

Hesaplar: `user@`, `creator@`, `developer@`, `moderator@`, `admin@` + alan adı (`DEV_ACCOUNTS_DOMAIN`, varsayılan `ltz.com`). Yalnız yerel geliştirme içindir; hesaplar doğrudan veritabanına yazılır.

---

## Aşama 4 — Hardware kataloğu içe aktarma

Katalog, Hardware servisi **başlamadan önce** aktarılır. Postgres ayakta olmalı (Aşama 2).

```powershell
# 1) Hardware imajını derle (içe aktarma bu imajdan tek seferlik konteyner açar)
docker @C build hardware-service

# 2) Veri setini indir (yoksa; SHA-256 doğrulanır, internet gerekir)
node ops/hardware-catalog-import.mjs download "$env:TEMP\ltz-pc-part-dataset"

# 3) İçe aktar (Hardware konteyneri gerekmez)
node ops/hardware-catalog-import.mjs import "$env:TEMP\ltz-pc-part-dataset" --standalone

# 4) Şimdi Hardware'i başlat
docker @C up -d hardware-service
```

Hata alırsan: veri seti eskiyse `$env:TEMP\ltz-pc-part-dataset` klasörünü silip 2. adımdan tekrarla. Hardware'i katalogsuz başlatıp sonradan da aktarabilirsin; 3. adım Hardware açıkken de çalışır.

---

## Aşama 4b — Game kataloğu içe aktarma (isteğe bağlı)

Oyun verisi (oyunlar, terimler, açıklamalar, DLC ilişkisi) hazır dökümden yüklenir; Steam/IGDB anahtarı gerekmez. Postgres ayakta olmalı, Game servisi **başlamadan önce** yapılır.

```powershell
# 1) Ekipten aldığın ltz-game-catalog.dump dosyasını depo kökündeki game-catalog-dump klasörüne kopyala (adı değişmesin)
# 2) İsteğe bağlı: bütünlük ve şema uyumu
node ops/game-catalog-import.mjs verify

# 3) Boş game_db'ye yükle (doluysa atlar; üzerine yazmak için --replace)
node ops/game-catalog-import.mjs import

# 4) Game'i başlat
docker @C up -d game-service
```

`node ops/dev.mjs --with=game-service` (veya `--all`) bunu Game'den önce kendisi yapar (`--no-catalog` ile atlanır). Klasör `game-catalog-dump` yoksa betik oluşturur; `*.dump` Git'e girmez. Daha önce kurduysan ve yeni dump aldıysan dosyayı değiştirmek yetmez; adımlar için [game-catalog.md](game-catalog.md) içindeki "Daha önce kurduysan" bölümüne bak.

---

## Aşama 5 — Frontend

```powershell
cd frontend
npm ci                                  # yalnız ilk kurulumda ya da package-lock.json değişince
npx next dev -H localhost -p 5001
```

Adres: `http://localhost:5001`. Giriş için Aşama 3'teki hesapları kullan.

---

## Aşama 6 — Doğrulama

```powershell
node ops/dev.mjs status        # e-posta modu, frontend (5001) ve ltz-dev konteynerleri
node ops/dev.mjs e2e auth      # gerçek-HTTP Auth kabul testi (geçici kaynaklar kullanır, gerçek ENV okumaz)
```

---

## Sık sorunlar

| Belirti | Neden | Çözüm |
| --- | --- | --- |
| `dev.mjs` "Uygulama şifresi" isteminde bekliyor | 3/5 e-posta adımı girdi bekliyor | Enter (Mailpit) ya da `--no-mail`; bu kılavuzu kullanırsan `dev.mjs` başlatma akışı gerekmez |
| "N servis X GB ister, Docker'a Y GB ayrılmış" | Bellek yetersiz | Daha az servis kaldır ya da `~/.wslconfig` içinde `memory=` artır, `wsl --shutdown`, Docker'ı yeniden başlat. Fiziksel RAM'in en az 4 GB'ı Windows'a kalmalı |
| Servis sürekli yeniden başlıyor | Yanlış ENV dosyası ya da bellek | `docker @C logs <servis> --tail 50`; komutta `--env-file .env.dev` olduğundan emin ol |
| Hardware'de parça listesi boş | Katalog aktarılmadı | Aşama 4'ü çalıştır |
| `docker compose up` sonrası bazı servisler öldü | Düz komut tüm servisleri kaldırır | `docker @C down`, sonra Aşama 2 |

Bu kılavuz `ops.md` içindeki komut referansını tamamlar; `dev.mjs` davranışı değiştirilmedi.
