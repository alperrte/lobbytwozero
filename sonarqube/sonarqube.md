# LobbyTwoZero SonarQube

SonarQube, uygulama PostgreSQL'inden ayrı `ltz-sonar` Compose projesinde çalışır.
Adres: `http://localhost:5199`. Veritabanı: `ltz_sonar`. Backend proje anahtarı:
`ltz-backend`. Frontend henüz bulunmadığı için frontend analizi uygulanmaz;
`.env.example` içindeki frontend anahtarı ileride kullanılmak üzere ayrılmıştır.

Kaynak projedeki sabit SonarQube image sürümü/digest'i ve PostgreSQL 17 kullanılır.
Sonar portu yalnızca loopback üzerinden yayınlanır. Scanner Node.js 22.12+,
Maven ve JDK 25 gerektirir; Maven'in mevcut JaCoCo ve Sonar plugin ayarlarını
`backend/pom.xml` üzerinden kullanır.

Mevcut `.env.dev` dosyasına eksik Sonar alanları ve rastgele bir yerel veritabanı
parolası eklendi. `SONAR_TOKEN` ilk kurulumdan sonra doldurulmalıdır. Yeni bir
profil gerekiyorsa `.env.example` üzerinden oluşturun; Sonar alanları şöyledir:

```dotenv
SONARQUBE_HOST_PORT=5199
SONAR_HOST_URL=http://localhost:5199
SONAR_TOKEN=
SONAR_BACKEND_PROJECT_KEY=ltz-backend
SONAR_DB_NAME=ltz_sonar
SONAR_DB_USER=sonar
SONAR_DB_PASSWORD=REPLACE_WITH_A_LOCAL_PASSWORD
```

Windows:

```powershell
.\sonarqube\sonar.cmd up dev
.\sonarqube\sonar.cmd status dev
.\sonarqube\sonar.cmd scan dev
.\sonarqube\sonar.cmd down dev
```

Linux/macOS:

```sh
sh ./sonarqube/sonar.sh up dev
sh ./sonarqube/sonar.sh scan dev
```

İlk başlatmadan sonra tarayıcıdan SonarQube'a girin. İlk kurulumda `admin` /
`admin` ile giriş yapıp parolayı değiştirin. `ltz-backend` projesini oluşturun
ve analiz yetkisi bulunan bir token üretin. Token'ı ignore edilen `.env.dev`
dosyasında veya terminalde `SONAR_TOKEN` değişkeninde saklayın.

Komutlar yalnızca seçilen `.env.dev` / `.env.prod` dosyasını okur; aktif `.env`
gerekmez ve değiştirilmez. Terminal değişkenleri önceliklidir. Profil dosyaları
komut olarak çalıştırılmaz. Portu değiştirirseniz `SONAR_HOST_URL` değerini de
güncelleyin. Scanner yalnızca yerel HTTP(S) Sonar adreslerini kabul eder.

`up` Sonar veritabanını ve sunucuyu başlatıp hazır olmasını bekler. `status`
container ve API durumunu gösterir. `down`, isim uyumluluğu için korunmuştur;
gerçekte `docker compose stop` çalıştırır, container ve volume'leri saklar.
Bu komutlar uygulama Compose projesine dokunmaz.

`scan` sırasıyla sunucu/token erişimini, Sonar way yeni kod koşullarını,
tüm modüllerin taze Maven test/JaCoCo raporlarını ve gerçek analiz task'ının
Quality Gate sonucunu kontrol eder. Ayrıca bütün kodda şu hedefleri zorunlu tutar:

- Coverage en az %80.
- Tekrarlanan satır yoğunluğu en fazla %3.
- Security, reliability ve maintainability rating A.

Yeni kod için Sonar way en az %80 coverage, en fazla %3 duplication ve sıfır
yeni issue (veya A rating koşulları) içermelidir. Atanmış gate'in yapılandırması
token yetkisi nedeniyle HTTP 403 dönerse `NOT VERIFIED` olarak raporlanır;
gerçek analiz gate sonucu ve bütün kod hedefleri yine zorunludur.

Eksik, eski, başarısız veya atlanmış test raporları kabul edilmez. Maven ya da
scanner hatasında başarılı analiz sonucu gösterilmez. Token komut argümanına
yazılmaz; scanner'a environment ile aktarılır ve script loglarında maskelenir.
Loglar `logs/sonarqube-*.log` altında, raporlar ilgili `target/` dizinlerindedir.

Backend testleri için veritabanı/test profili henüz tanımlı değilse önce mevcut
uygulama test yapılandırması tamamlanmalıdır; runner bu hatayı atlamaz.
SonarQube'u başlatmak ve gerçek analiz göndermek için yukarıdaki komutları
yerelde çalıştırın. Script kurulumu kendi başına container veya token oluşturmaz.
