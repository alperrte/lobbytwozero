# AUTH — EKİP HIZLI BAŞLANGIÇ

**Sahip:** Kerem (auth-service) · **Güncelleme:** 2026-11-06

Bu sayfa, Auth'u yerelde hızlı ve güvenli çalıştırmak isteyen herkes (ve yapay zekâ asistanları) içindir. Amaç: geliştirmeyi **yavaşlatmadan** güvenliği **gevşetmemek.** Kısa cümlelerle yazıldı; yapay zekâya "bu dosyayı oku, bana adımları söyle" diyebilirsin.

---

## 0. Sıfırdan çalıştır (yeni klon: TEK KOMUT)

Gerekenler: Git, Docker Desktop ve **Node 24.7+** (betikler yerleşik `crypto.argon2` kullanır; eski Node'da betik anlaşılır bir mesajla durur).

```text
node ops/dev.mjs
```

Bu komut sırayla şunları yapar (her adım tekrar çalıştırmaya güvenlidir):

1. **Ön koşullar:** Node sürümü ve Docker'ın çalıştığı denetlenir.
2. **Anahtarlar:** `.env.dev` yoksa oluşturulur, JWT/OTP/TOTP/servis anahtarları üretilir, dolu satıra dokunulmaz.
3. **E-posta:** YALNIZ Google **uygulama şifresini** sorar (şifre yazarken görünmez; Gmail adresi ekibin ortak test hesabı olarak hazırdır). Boş bırakırsan e-postalar **Mailpit**'e düşer (`http://localhost:8025`, gerçek mail gitmez) ve kurulum devam eder. Şifre depoda/betikte **yoktur**; ekipte güvenli kanaldan (şifre yöneticisi) paylaşılır ve yalnız kendi `.env.dev`'ine yazılır. Etkileşimsiz kurulum için `DEV_MAIL_PASSWORD` ortam değişkeni (başka bir Gmail hesabı için `DEV_MAIL_USER`).
4. **Servisler ve hesaplar:** Postgres, Mailpit, Mail, Auth, User ve Gateway derlenip kaldırılır, sağlık beklenir, her role bir test hesabı açılır (bkz. §1.0). İlk seferde imajlar derlendiği için birkaç dakika sürer.
5. **Frontend:** `node_modules` eksik ya da eskiyse `npm ci`, sonra `http://localhost:5001` (**port 5001 şarttır**: Gateway CORS varsayılanı yalnız onu kabul eder; README'deki `npm run dev` 3000 açar ve girişte CORS hatası verir). Zaten çalışıyorsa dokunulmaz.

Sonra `http://localhost:5001/tr/giris` adresinde `user@ltz.com` (ya da §1.0'daki diğer hesaplar) ile gir. Parola `.env.dev`'deki `DEV_ACCOUNTS_PASSWORD`'dür.

Diğer komutlar: `node ops/dev.mjs stop` (servisleri durdurur, veri kalır), `node ops/dev.mjs reset` (servisleri **ve tüm yerel veriyi** siler, onay ister), `node ops/dev.mjs status`, `node ops/dev.mjs mail on|off|status` (gerçek e-postayı sonradan aç/kapat). Seçenekler: `--no-frontend`, `--no-mail`, `--no-build`, `--no-services` (yalnız ayarlar), `--skip-keys`, `--dry-run` (ne yapacağını yazar, çalıştırmaz).

> `ops/dev.mjs` mevcut betikleri sırayla çağırır (`generate-auth-dev-keys.mjs`, `dev-accounts.mjs`, `dev-mail.mjs`); onları tek tek de çalıştırabilirsin, aşağıdaki bölümler her birini anlatır.

## 1. Auth'u yerelde ayağa kaldır (tek komut)

```text
node ops/generate-auth-dev-keys.mjs
```

Bu **tek komut** yeterli; ayrıca başka betik çalıştırmana gerek yok. Her çalıştırmada sırayla:

1. `.env`/`.env.dev` yoksa `.env.example`'dan `.env.dev` oluşturur.
2. `.env.example`'da olup dosyanda **hiç bulunmayan** ayarları açıklamalarıyla sona ekler (yeni gelen ayarlar böyle otomatik gelir).
3. Boş anahtarları (JWT, HMAC, TOTP, servis sırları) üretir.
4. Sonunda Auth'un açılış kurallarını kontrol edip raporlar.

Güvenle tekrar çalıştırabilirsin: **var olan satırlara dokunmaz** (dolu değeri ezmez, sırayı bozmaz), eksik yoksa hiçbir şeyi değiştirmez, üretilen değerleri ekrana yazmaz.

Sonra: `.\ops\env.cmd use dev` (Linux/macOS: `sh ./ops/env.sh use dev`) ve Compose'u olağan şekilde başlat.

- Raporda `[FAIL]` varsa Auth açılmaz (komut çıkış kodu 1 verir); yanındaki `->` satırı çözümü söyler. `[WARN]` açılışı durdurmaz ama bir kolaylığı kaçırırsın. Dosyaya dokunmadan yalnız raporu görmek istersen: `node ops/auth-env-check.mjs`.
- `COMPOSE_PROFILES=dev-mail` olmalı (örnek dosyada hazır gelir): doğrulama ve parola kodları **Mailpit**'e düşer (arayüz: `http://127.0.0.1:8025`). Gerçek e-posta gitmez.
- **2026-11-10: Auth migration'ları yeniden 4 dosyaya (V61–V64) birleştirildi.** V65–V70'i daha önce uygulamış yerel `auth_db` açılışta Flyway hatası verir ("Detected applied migration not resolved locally" / checksum mismatch). Yerel veri olduğu için şemayı bir kez sıfırla: `docs-auth/migration-yazim-kurallari.md` → "Yerel `auth_db`'nizi bir kez sıfırlayın". Eski bir yerel veritabanında "checksum mismatch" görürsen aşağıdaki tabloya da bak.

## 1.0 Her role bir test hesabı (yalnız yerel dev)

> **Bu bölüm bilerek kural dışıdır ve yalnız yerel geliştirme içindir.** Hesaplar kayıt akışından (e-posta doğrulaması, parola kuralları) geçirilmeden **doğrudan veritabanına yazılır** (`auth_db` ve `user_db`). Staging/production'da bu yol yoktur.

`node ops/dev-accounts.mjs up` yığını kaldırır (Postgres, Mailpit, Mail, Auth, User, Gateway), sağlığı bekler ve hesapları açar. Hesap istemezsen `--no-seed`, yeniden derlemeden kaldırmak için `--no-build`.

| E-posta | Rol | Giriş |
| --- | --- | --- |
| `user@ltz.com` | PLAYER | parola |
| `creator@ltz.com` | CONTENT_CREATOR | parola |
| `developer@ltz.com` | GAME_DEVELOPER | parola |
| `moderator@ltz.com` | MODERATOR | parola + 2FA kodu |
| `admin@ltz.com` | ADMIN | parola + 2FA kodu |

- Parola ve alan adı `.env.dev`'deki `DEV_ACCOUNTS_PASSWORD` / `DEV_ACCOUNTS_DOMAIN` satırlarındadır. Satırlar yoksa `node ops/generate-auth-dev-keys.mjs` ekler (örnek dosyadan gelir). Parola depodaki koda gömülü değildir.
- **En kolay dev yolu:** `.env.dev`'e `AUTH_2FA_REQUIRED_ROLES=` (boş değer, Auth bunu **yalnız dev/test profilinde** kabul eder) ekleyip Auth'u yeniden başlat. O zaman `moderator` ve `admin` yalnız parolayla girer, betik hiç 2FA kurmaz ve hiçbir e-posta çıkmaz. Satır yoksa Auth varsayılanı geçerlidir: `moderator` ve `admin` için 2FA zorunlu, kod şu komutla alınır: `node ops/dev-accounts.mjs totp admin` (kod 30 sn geçerlidir, aynı kod ikinci kez kabul edilmez). 2FA sırrı **makine düzeyinde** `~/.ltz/dev-accounts.json` dosyasındadır (depoya girmez): aynı bilgisayardaki tüm klasörler (git worktree'leri dahil) aynı Docker veritabanını paylaştığı için aynı dosyayı görür. Başka makineye taşınmaz; sırrı olmayan bir 2FA hesabıyla karşılaşırsa betik o dev hesabının 2FA'sını sıfırlayıp yeniden kurar.
- Hesapları elle yenilemek ya da listelemek: `node ops/dev-accounts.mjs` / `node ops/dev-accounts.mjs list` (parolayı `.env.dev`'den kendisi okur; o dosyadan yalnız `DEV_ACCOUNTS_*` iki satırını okur). Tekrar çalıştırmak güvenlidir.
- **Neden doğrudan veritabanı?** Auth parola kuralı (en az 8 karakter + büyük/küçük harf, rakam, özel karakter) gereği kısa `ltz1234` gibi bir parola API'den geçmez, ADMIN/MODERATOR rolü de normalde yalnız TOTP'si etkin hesaba verilir. Betik bu kuralları dev için atlayarak şunları doğrudan yazar: `auth_db`: hesap (doğrulanmış, parola özeti), PLAYER rolü, durum geçmişi, gerekirse CONTENT_CREATOR / GAME_DEVELOPER / MODERATOR / ADMIN rolü; `user_db`: her hesap için `user_profiles` satırı (kullanıcı adı kopyasıyla). Bu yüzden **e-posta, SMTP, Mailpit ya da Mail Service gerekmez**; gerçek SMTP açıkken de hesaplar açılır. Hesaplar açıldıktan sonra betik gerçek giriş yapıp `/auth/me` ve `/user/me` ile doğrular.
- **Tek istisna, gerçek SMTP açıkken:** `AUTH_2FA_REQUIRED_ROLES` boş değilse ve admin/moderator için 2FA kurulması gerekiyorsa, Auth "2FA etkinleştirildi" bildirim e-postası gönderir. Gerçek SMTP ve gerçek alan adı (ör. `ltz.com`) varsa bu mail başkasına gider; betik bunu algılayıp **hiçbir şeye dokunmadan durur** ve çözümü yazar (yukarıdaki boş `AUTH_2FA_REQUIRED_ROLES=` satırı, ya da `DEV_ACCOUNTS_DOMAIN=ltz.test`).
- **Güvenlik kapıları:** betik yalnız `localhost` adreslerine ve `ltz-dev-*` Postgres kapsayıcısına çalışır, Auth'un profili `dev`/`test` değilse ya da okunamıyorsa durur, `NODE_ENV=production` iken reddeder. **Bu parola ve hesaplar staging/production'a asla taşınmaz;** `.env.prod`'a `DEV_ACCOUNTS_*` satırı eklenmez.

## 1.1 Mail Service'i yerelde ayağa kaldır (adım adım)

**Önce şunu bil:** Mail Service'i yalnız Auth çağırır; tarayıcıdan ya da başka servisten erişilmez. Varsayılan dev ayarında Auth e-postayı Mail Service'e **hiç göndermez**, doğrudan Mailpit'e yazar (`AUTH_MAIL_ADAPTER=dev-smtp`). Mail Service'i denemek istemiyorsan aşağıdakilere gerek yok.

**Mail Service üzerinden e-posta denemek için:**

1. ✅ **Anahtarları üret:** `node ops/generate-auth-dev-keys.mjs`. Sonda `MAIL_PAYLOAD_ENCRYPTION_KEY: geçerli` ve `Hata yok` yazmalı. Bu anahtar boşsa Mail Service **bilerek açılmaz**.
2. ✅ **Ortamı seç:** `.\ops\env.cmd use dev` (Linux/macOS: `sh ./ops/env.sh use dev`).
3. ✅ **Auth'a Mail Service'i söyle:** `.env.dev` içinde `AUTH_MAIL_ADAPTER=mail-service` (satırın başındaki `#` kalkacak). `MAIL_SERVICE_URL` örnek dosyada hazır gelir.
4. ✅ **Mailpit'i aç:** `COMPOSE_PROFILES=dev-mail` olmalı (örnek dosyada hazır). Mailpit, Mail Service'in dev'de e-postayı bıraktığı kutudur; kapalıysa gönderim bağlantı hatası verir.
5. ✅ **Başlat:** `docker compose up -d --build mail-service auth-service`. Mailpit profille birlikte kalkar; kalkmadıysa `docker compose --profile dev-mail up -d mailpit`.
6. ✅ **Sağlığı kontrol et:** `http://127.0.0.1:5083/actuator/health` (port `MAIL_SERVICE_HOST_PORT`, örnekte 5083) `UP` dönmeli.
7. ✅ **E-postayı gör:** Auth'tan bir kod iste (kayıt ya da parola sıfırlama). Mesaj `http://127.0.0.1:8025` adresinde (Mailpit) görünür. Kuyruk her ~5 sn işlenir; hemen gelmezse 10–15 sn bekle.

**SMTP satırları (`MAIL_SMTP_HOST` vb.) dev'de BOŞ kalır.** Boşken Mail Service Mailpit'i kullanır. Gerçek sağlayıcı seçilince (prod) doldurulur; o zaman TLS ve `MAIL_FROM_ADDRESS` ile `MAIL_ALLOWED_LINK_HOSTS` de zorunludur. Dev'de bunlara dokunma.

**Takılırsan** bize şunu gönder (anahtar, parola ya da kod değerlerini asla gönderme):

- `docker compose logs mail-service --tail 50`
- `node ops/auth-env-check.mjs` çıktısı (değer içermez)

## 2. Dev'de kolay, prod'da zorunlu

| Konu | Yerel dev | Production |
| --- | --- | --- |
| E-posta | Mailpit (kodlar tarayıcıda görünür) | Yalnız `mail-service`; yoksa Auth **açılmaz** |
| Steam bağlı hesapta 2FA kurulumu | **Kaldırıldı (2026-11-10)**: Steam için zorunlu 2FA yok | aynı |
| ADMIN için 2FA | `AUTH_2FA_REQUIRED_ROLES` ile gevşetilebilir (yalnız dev/test) | Zorunlu; gevşetilirse Auth açılmaz |
| Anahtarlar | `generate-auth-dev-keys.mjs` ile yerel üretim | Ayrı üretilir, sır yöneticisinde; dev anahtarı **kullanılmaz** |
| İç ağ adresleri | `http` olabilir | `https` zorunlu |
| Parola kuralı, token ömrü (60 sn), CSRF, çerez öznitelikleri | Aynı | Aynı |

Yani gevşetilebilenler sınırlı, adı konmuş ve yalnız dev/test profilinde: başka bir güvenlik ayarını "geçici" kapatma. Steam için zorunlu 2FA ayarı artık yoktur (2026-11-10).

## 3. "Auth açılmıyor" / "bir şey çalışmıyor" tablosu

| Belirti | Neden | Çözüm |
| --- | --- | --- |
| Auth açılışta `AUTH_OTP_HMAC_KEY` / `AUTH_TOTP_ENCRYPTION_KEY` hatası | Anahtar boş, kısa veya Base64 değil | `node ops/generate-auth-dev-keys.mjs` |
| Açılışta `previous-encryption-keys` hatası | Anahtar rotasyon listesi bozuk | `node ops/auth-env-check.mjs` hangi ayar olduğunu söyler |
| `prod` profilinde açılmıyor | `AUTH_MAIL_ADAPTER` `mail-service` değil, ya da güvenlik ayarı gevşetilmiş | Hata mesajı hangi ayarı söyler; prod'da gevşetilemez |
| Kod/bağlantı e-postası gelmiyor | `dev-mail` Compose profili kapalı | `COMPOSE_PROFILES=dev-mail` |
| Mail Service açılmıyor, logda `payload-key must be Base64` / `32 bytes` | `MAIL_PAYLOAD_ENCRYPTION_KEY` boş ya da bozuk | `node ops/generate-auth-dev-keys.mjs`, sonra `docker compose up -d --build mail-service` |
| Mail Service açık ama e-posta gitmiyor, logda SMTP bağlantı hatası | Mailpit kapalı (SMTP boşken dev'de `mailpit:1025` kullanılır) | `docker compose --profile dev-mail up -d mailpit`; mesajlar kuyrukta bekler ve yeniden denenir |
| Mailpit'te e-posta yok, Mail Service'e istek de gitmiyor | `AUTH_MAIL_ADAPTER` `dev-smtp` (Auth Mailpit'e doğrudan yazıyordur) ya da Auth yeniden başlatılmadı | `.env.dev`'de `AUTH_MAIL_ADAPTER=mail-service`, sonra Auth'u yeniden başlat |
| `409 PASSWORD_REQUIRED` | Hesap Google/Steam ile açılmış, parolası yok | `POST /api/v1/auth/password/set/request`, sonra `/password/set` |
| Girişte `MFA_ENROLLMENT_REQUIRED` | 2FA zorunlu rol (ADMIN/MODERATOR) hesabında 2FA henüz kurulu değil (Steam için artık zorunlu değil) | `/login/2fa/enrollment/start`, sonra `/complete` |
| `403` ve hesap durumu | Hesap askıda/yasaklı; süreli askı kendiliğinden açılır | Yönetici uçlarından durum; ayrıntı `docs/api.md` |
| Veritabanı "checksum mismatch" | Uygulanmış bir migration dosyası değiştirilmiş | Yerel DB'yi sıfırla (`docs-auth/migration-yazim-kurallari.md`); uygulanmış migration'a asla dokunma |
| Google ile bağlama çalışmıyor | Dönüş adresi Google Console'da yok | `…/api/v1/auth/oauth/google/link/callback` adresini ekle |

## 4. Yapma listesi (kısa)

- Anahtarı, parolayı, token'ı koda, commit'e, loga veya sohbete yazma. `.env*` Git'te değildir; öyle kalsın.
- Production anahtarını yerele, yerel anahtarı production'a kopyalama.
- Test geçsin diye CSRF, token ömrü, parola kuralı veya yetki kontrolünü kapatma. Hızlı denemek için yukarıdaki **izinli** dev gevşetmeleri yeter.
- Uygulanmış migration dosyasını değiştirme; yeni sürüm yaz (Auth'ta sıradaki boş sürüm V66; aralık V70'te biter).
- Auth'a yeni bir uç, durum veya hata kodu gerekiyorsa Auth'ta değişiklik isteme: `docs-auth/verilen-cevaplar/<servis>/` içine talep yaz.

## 5. Komut özeti

| Ne yapıyor | Komut |
| --- | --- |
| ENV'i tamamla (eksik ayarları ekle, anahtarları üret, sonunda kontrol et) | `node ops/generate-auth-dev-keys.mjs` |
| Yalnız raporu gör (dosyaya dokunmaz) | `node ops/auth-env-check.mjs` |
| TOTP şifreleme anahtarını döndür | `node ops/generate-auth-dev-keys.mjs --rotate-totp` |
| Doğrulama (HMAC) anahtarını döndür | `node ops/generate-auth-dev-keys.mjs --rotate-hmac` |
| Aktif profili seç / göster | `.\ops\env.cmd use dev` · `.\ops\env.cmd status` |
| Auth + Gateway uçtan uca duman testi | `node ops/auth-smoke.mjs` (Docker ve güncel jar'lar gerekir) |

Anahtar rotasyonunun ayrıntısı: [`anahtar-rotasyonu.md`](anahtar-rotasyonu.md). Yeni özelliklerin listesi: [`verilen-cevaplar/bilgilendirme-2026-11-05-auth-degisiklikleri.md`](verilen-cevaplar/bilgilendirme-2026-11-05-auth-degisiklikleri.md).
