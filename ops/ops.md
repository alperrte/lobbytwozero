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

auth-dev-keys ile env doldurmak için aşağıdaki komutları kullanacağız

//   node ops/generate-auth-dev-keys.mjs            # .env ve .env.dev (varsa)
//   node ops/generate-auth-dev-keys.mjs .env.local # belirli dosya(lar)

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

Aktif profili seçtikten sonra mevcut PostgreSQL Compose servisini başlatmak için:

```powershell
docker compose up -d
```

Mevcut Compose yalnızca PostgreSQL içerir; uygulama servislerini başlatmaz.
Çalışan uygulamaların ENV değişikliklerini alması için yeniden başlatılması
gerekir. Pre-push ve Sonar komutları ise açıkça verilen profil dosyasını kullanır;
öncesinde `use` çalıştırmak zorunlu değildir.

İlgili rehberler: [pre-push](../pre-push/pre-push.md),
[SonarQube](../sonarqube/sonarqube.md).
