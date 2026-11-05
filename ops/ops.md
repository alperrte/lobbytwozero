# LobbyTwoZero ENV profilleri

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
node ops/generate-auth-dev-keys.mjs              # .env ve .env.dev (hangisi varsa); hiçbiri yoksa .env.dev'i .env.example'dan oluşturur
node ops/generate-auth-dev-keys.mjs .env.local   # yalnız verilen dosya(lar)
```

Yeni klonda sıra: bu komut, sonra `.\ops\env.cmd use dev` (POSIX: `sh ./ops/env.sh use dev`). Komut güvenle tekrar
çalıştırılır: dolu satıra dokunmaz, eksik bir şey yoksa dosyayı değiştirmez, anahtar değerlerini ekrana yazmaz.

Komut **kendisi doldurur:** Auth, servis ve Mail anahtarları (`AUTH_*`, `MAIL_PAYLOAD_ENCRYPTION_KEY`), `.env.example`'da
olup dosyada hiç bulunmayan ayarlar, SMTP/bağlantı satırları (boş), `AUTH_EVENT_TARGETS=user`.
Komut **doldurmaz, siz verirsiniz** (dış sağlayıcı bilgisi; boş kalması dev'de normaldir): `STEAM_WEB_API_KEY`,
`IGDB_CLIENT_ID/SECRET`, `ITAD_API_KEY`, `GOOGLE_CLIENT_ID/SECRET`, `SONAR_TOKEN`, gerçek SMTP sağlayıcı bilgileri.
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

`node ops/generate-auth-dev-keys.mjs` tek komutla `.env`/`.env.dev` dosyalarını tamamlar (eksik ayarları `.env.example`'dan ekler, boş anahtarları üretir, var olan satırlara dokunmaz) ve sonunda Auth'un açılış kurallarını ve Mail Service'in şifreleme anahtarını (`MAIL_PAYLOAD_ENCRYPTION_KEY`, Base64, tam 32 bayt) kontrol eder; Mail anahtarı hatalıysa `[FAIL]` yanında "Mail Service açılmaz" yazar (Auth etkilenmez). Yalnız raporu görmek için `node ops/auth-env-check.mjs` (dosyaya dokunmaz, değer yazmaz). Mail Service'i adım adım ayağa kaldırmak ve sık hatalar için: `docs-auth/ekip-hizli-baslangic.md` §1.1 ve §3. Ayrıntı: `docs-auth/ekip-hizli-baslangic.md`.
