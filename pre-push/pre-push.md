# LobbyTwoZero yerel kalite kontrolü

Bu yapı qr-management-system projesindeki manuel pre-push menüsünden uyarlandı.
Git hook otomatik kurulmaz; commit veya push yapmaz. Node.js 22.12+, Maven ve
Maven'in kullandığı JDK 25 gereklidir. Ek npm paketi kurulması gerekmez.

Windows'ta repository kökünden:

```powershell
.\pre-push\pre-push.cmd
.\pre-push\pre-push.cmd auth-service
.\pre-push\pre-push.cmd backend
.\pre-push\pre-push.cmd full dev
```

Linux/macOS'ta aynı argümanlarla:

```sh
sh ./pre-push/pre-push.sh full dev
```

Argümansız komut etkileşimli menüyü açar. Servisler `backend/pom.xml` içinden
okunur: mevcut 17 iş servisi ve API Gateway, toplam 18 modül. Yeni modüller
parent POM'a eklendiğinde menüye de gelir. `--help` seçenekleri gösterir.
`dev` ve `prod` kısayolları `full dev` / `full prod` anlamına gelir.

`backend` tüm modüllerde; servis adı yalnızca seçilen modülde Maven
`clean verify` çalıştırır. Her modülde çalışmış, başarılı ve atlanmamış testler,
taze Surefire/JaCoCo raporları ve en az %80 satır coverage aranır. Bu modların
sonucu yalnızca backend test kapsamına aittir.

`full` şu kontrolleri çalıştırır:

- Git çalışma alanı ve staged whitespace kontrolü; takip edilen/staged ENV,
  anahtar ve bilinen gizli dosya yollarının kontrolü. Bu dosya adı kontrolüdür;
  bütün kaynak içeriğinde veya Git geçmişinde secret taraması değildir. Ek depo hijyeni (`pre-push/hygiene-checks.mjs`): izlenen dosyalarda özel anahtar başlığı ve uzun sır atamaları (değer yazdırılmaz), `.env` dosyalarının Git ve `.dockerignore` tarafından yok sayılması, Compose portlarının 127.0.0.1'e bağlı olması, anahtar üretici script güvenlik testi ve (kuruluysa) gitleaks ile tüm geçmiş taraması.
- Tüm backend için Maven `clean verify`, test raporları ve ağırlıklı toplam
  satır coverage en az %80. Host Maven testleri, seçilen `.env.<profil>` içindeki
  `POSTGRES_USER/PASSWORD/HOST_PORT` ve `DB_*` değerlerini yalnız alt süreç ortamına
  alır (`POSTGRES_HOST=127.0.0.1`); terminalde açıkça tanımlı değerler önceliklidir.
  Parola loga yazılmaz. Profil okunamazsa uyarı verilir ve PostgreSQL gerektiren
  servis testleri görünür şekilde FAIL olur.
- Seçilen `.env.dev` veya `.env.prod` ile `docker-compose.yml` doğrulaması.
  Docker Compose gerekir; yapılandırma kontrolü container başlatmaz.
- Compose içinde tanımlı backend servisleri varsa seçilen Compose projesinde
  çalıştıkları ve `/actuator/health` yanıtlarının HTTP 200 / `UP` olduğu kontrolü.

Mevcut Compose PostgreSQL ve 18 backend modülünü içerir. `full dev` öncesinde
geliştirme servislerini başlatın:

```powershell
docker compose --env-file .env.dev up -d --build
.\pre-push\pre-push.cmd full dev
```

PostgreSQL çalışmıyorsa iskelet servislerin context testleri DB bağlantısında;
backend servisleri çalışmıyorsa HTTP health kontrolü başarısız olur.
`full` testleri başlatmadan önce seçilen Compose profilinden yalnız DB kimlik
bilgilerini ve DB adlarını alır. Host üzerindeki Maven için bağlantı adresini
`127.0.0.1` ve PostgreSQL'in yayımlanan host portuna çevirir. Bu değerler yalnız
Maven alt sürecine verilir; Docker'ın container içi adres/port ayarları ve ENV
dosyaları değiştirilmez. DB parolası test çıktısında maskelenir. PostgreSQL
portunun yalnız `127.0.0.1` üzerinde yayımlanması zorunludur.

Bu kalite kapısında frontend kontrolleri ve SonarQube ayrı kapsamda tutulur.

`SAFE TO COMMIT` yalnızca tabloda gösterilen uygulanabilir kontrollerin geçtiğini
belirtir. Uygulamanın çalıştığını veya production hazırlığını garanti etmez.
Başarısız/eksik test, coverage veya Docker kontrolü `NOT SAFE TO COMMIT` ve
çıkış kodu 1 üretir. SonarQube bağımsızdır: [SonarQube rehberi](../sonarqube/sonarqube.md).

`full` için ENV profili gerekir; [ENV rehberine](../ops/ops.md) bakın.
Backend-only komutları profili değiştirmez veya profil dosyasını uygulamaya
yüklemez; testlerin kendi Spring test yapılandırmasını kullanır. `prod` seçimi
deploy yapmaz.

Her çalışma `logs/pre-push-*.log` dosyasına kaydedilir. Aynı anda test ve Sonar
raporlarının silinmesini önlemek için `test-results/quality.lock` kullanılır.
İşlem zorla sonlandırıldıysa, hiçbir test/analiz çalışmadığını doğruladıktan
sonra bu kilit dosyasını elle kaldırabilirsiniz.

Script regresyon testleri:

```powershell
node --test pre-push/menu.test.mjs pre-push/quality.test.mjs pre-push/runtime-checks.test.mjs sonarqube/sonar.test.mjs ops/env.test.mjs ops/generate-auth-dev-keys.test.mjs pre-push/hygiene-checks.test.mjs pre-push/backend-env.test.mjs
```

Bu testler komut yönlendirmesini, hata durumlarını ve rapor kabul kurallarını
doğrular; gerçek Maven testlerinin ve Sonar analizinin yerine geçmez.
