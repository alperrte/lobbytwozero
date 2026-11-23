# IMPORTANT — community-service test container'ları (merge öncesi kaldırılacak)

> Bu belge bir ajanın okuyup uygulayabilmesi için yazıldı. Sahibi: Nisa. Kapsam: yalnız `backend/services/community-service`.
> **Merge öncesi, tüm COM görevleri bittikten sonra** buradaki "Kaldırma adımları" uygulanacak. Kullanıcı kaldırmayı açıkça isteyene kadar **kaldırma**; ayrıca bu belgeyi kaldırma sırasında silme, sonuna sonucu not et.

## 1. Neden eklendi

- Pre-push `backend`/`full` modları her serviste `mvn clean verify` çalıştırır ve profil/`.env` yüklemez. Veritabanı gerektiren testler (`contextLoads`, `@DataJpaTest`) `application.properties` varsayılanlarıyla (`ltz_dev` kullanıcısı, `POSTGRES_PASSWORD` yok) gerçek DB'ye bağlanmaya çalışınca `FATAL: password authentication failed for user "ltz_dev"` ile düşer.
- Çözüm: DB'ye ihtiyaç duyan community-service testleri Testcontainers ile **geçici bir PostgreSQL 17 container'ında** çalışır; dev DB'ye ve `.env`'e bağımlılık kalmaz. Docker'ın açık olması gerekir.
- Durum kanıtı: `.\pre-push\pre-push.cmd community-service` → `community-service PASS PASS 135 test %97,14`, `BACKEND TEST CHECK PASSED` (Testcontainers ile; Maven 3.9.16 + JDK 25).
- **Diğer servislere dokunulmadı.** Onların `contextLoads` testleri aynı şifre hatasıyla düşmeye devam eder (Kerem, Hamza, Umut, Alper ve ortak servisler); sahipleri karar verir. Bu belge yalnız community-service'i kapsar.

## 2. Eklenenlerin tam listesi (hepsi `backend/services/community-service/` altında)

| # | Dosya | Ne eklendi |
| --- | --- | --- |
| 1 | `pom.xml` | `org.testcontainers:testcontainers-postgresql:2.0.5` bağımlılığı, `<scope>test</scope>` (sürüm açıkça yazılı) |
| 2 | `pom.xml` | `maven-surefire-plugin` yapılandırması: `environmentVariables` içinde `TESTCONTAINERS_RYUK_DISABLED=true` (`<build><plugins>` içinde, `spring-boot-maven-plugin`'den önce) |
| 3 | `src/test/java/com/ltz/community/infrastructure/persistence/PostgreSqlIntegrationTest.java` | Soyut taban sınıf. **Ortamda `POSTGRES_PASSWORD` varsa (pre-push/Sonar'ın geçici DB'si) Testcontainers hiç başlatılmaz** ve o DB kullanılır; yoksa statik `PostgreSQLContainer("postgres:17-alpine")` (db/kullanıcı/şifre: `community_test`) başlatılıp `@DynamicPropertySource` ile `spring.datasource.*` ezilir (IPv4 `127.0.0.1`, `connectTimeout=60`, Hikari `connection-timeout=120000`), JVM kapanışında container durdurulur |
| 4 | `.../CommunityServiceApplicationTests.java` | `extends PostgreSqlIntegrationTest` + ilgili import |
| 5 | `.../infrastructure/persistence/PostRepositoryTest.java` | `extends PostgreSqlIntegrationTest` |
| 6 | `.../infrastructure/persistence/PostImageRepositoryTest.java` | `extends PostgreSqlIntegrationTest` |

`pom.xml`'deki **`spring-boot-starter-webmvc-test`** bağımlılığı Testcontainers'a ait **değildir**; MockMvc (`@WebMvcTest`) testleri için gerekir. **Kaldırılmaz.**

**Neden env öncelikli:** pre-push/Sonar zaten güvenilir bir geçici PostgreSQL veriyor; yanında ikinci bir Testcontainers açmak yoğun makinede (çok sayıda container) bağlantı zaman aşımlarıyla testleri aralıklı düşürdü (3 koşunun 2'si). Env DB kullanılınca 3/3 geçti. Bu yüzden pre-push/Sonar altında Testcontainers fiilen **kullanılmıyor**; yalnız IDE/doğrudan Maven için yedek.

## 3. Kaldırma adımları (merge öncesi)

Kullanıcı "test container'larını kaldır" dediğinde sırayla:

1. `PostgreSqlIntegrationTest.java` dosyasını sil.
2. 4, 5, 6 numaralı üç test sınıfından `extends PostgreSqlIntegrationTest` ifadesini kaldır; `CommunityServiceApplicationTests.java` içindeki `import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;` satırını da sil. `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` ve `@SpringBootTest` ek açıklamaları **kalır**.
3. `pom.xml`'den `testcontainers-postgresql` `<dependency>` bloğunu ve `maven-surefire-plugin` `<plugin>` bloğunu (TESTCONTAINERS_RYUK_DISABLED) sil. `spring-boot-starter-webmvc-test`'e dokunma.
4. `grep -ri testcontainers backend/services/community-service` boş dönmeli (`target/` hariç).
5. `docs/` altında Testcontainers'a atıf eklenmişse (şu an eklenmedi) temizle.

### Güncelleme (pre-push artık kendi geçici veritabanını açıyor)

Testcontainers eklendikten sonra `pre-push/test-database.mjs` eklendi: `backend`, servis adı ve `full`
modları Maven'den önce geçici bir PostgreSQL 17 başlatıp `POSTGRES_*` değişkenlerini Maven'e veriyor
(ayrıntı: `pre-push/pre-push.md` "Geçici test veritabanı"). Bu yüzden **pre-push üzerinden çalıştırırken
community-service Testcontainers'ı kaldırmak artık testleri düşürmez** (şifre hatası oluşmaz). Aşağıdaki
"sonuç" maddeleri hâlâ şu durumlar için geçerlidir: pre-push **dışında** (IDE, doğrudan `mvnw`)
çalıştırma ve `POSTGRES_PASSWORD`'ün ortamda tanımlı olduğu / `LTZ_TEST_DB=off` durumları. Kaldırmadan
sonra mutlaka `.\pre-push\pre-push.cmd community-service` ile 135 testin geçtiğini doğrula.

### Kaldırmanın sonucu (kullanıcıya mutlaka bildir)

Kaldırınca `contextLoads`, `PostRepositoryTest` ve `PostImageRepositoryTest` (toplam 1 + 11 + 11 = 23 test) tekrar `application.properties` üzerinden **ortamdaki gerçek PostgreSQL'e** bağlanır. Bu durumda:

- Pre-push `community-service` / `backend` modu, `POSTGRES_PASSWORD` vb. ortam değişkenleri tanımlı değilse **bu testlerde düşer** (diğer servislerde görülen hatanın aynısı); `Backend Tests/Coverage FAIL` ve `NOT SAFE TO COMMIT` çıkar.
- Çalıştırmak için ortam profili yüklü olmalıdır (`.\ops\env.cmd use dev` — bunu yalnız kullanıcı çalıştırır; ajan `.env*` dosyalarını **okumaz/kopyalamaz**) ya da DB değişkenleri elle verilmelidir.
- Testler `@DataJpaTest` içinde olduğundan veriyi geri alır, ama `contextLoads` ve Flyway, bağlandıkları DB'ye `V91`/`V92` migration'larını **uygular**. Dev `community_db` üzerinde bunun etkisini kullanıcıya hatırlat.
- Bu yüzden kaldırmadan önce kullanıcıya şunu sor: ekip, DB testleri için ortak bir yöntem (ör. pre-push'un geçici PostgreSQL başlatması) kararlaştırdı mı? Karar yoksa kaldırma önerilmez; kaldırma yalnız kullanıcı açıkça istiyorsa yapılır.

## 4. Doğrulama komutları

```powershell
# Maven artık PATH'te (C:\Users\nscmc\tools\apache-maven-3.9.16\bin). Yeni terminal aç.
mvn.cmd -version                                   # "Java version: 25..." görülmeli
.\pre-push\pre-push.cmd community-service          # Testcontainers varken: BACKEND TEST CHECK PASSED (135 test, ~%97)
```

Kaldırmadan sonra aynı komut, ortam değişkenleri yoksa DB testlerinde düşer (bkz. §3). Kaldırma sonrası beklenen test sayısı **aynı** (135) kalmalıdır; sayı düşerse yanlış dosya silinmiştir.

## 5. Bilmen gerekenler

- Testcontainers çalışırken Docker Desktop açık olmalı; ilk çalıştırmada `postgres:17-alpine` imajı yoksa indirilir (zaten dev Compose'ta kullanılıyor).
- `TESTCONTAINERS_RYUK_DISABLED=true`: Ryuk (temizlik container'ı) kapalı; container'ı taban sınıftaki shutdown hook durdurur. Zorla kapatılan bir test koşusunda `postgres:17-alpine` container'ı açık kalabilir: `docker ps` ile kontrol edip `docker rm -f <id>`.
- Bu oturumda doğrulama amacıyla elle açılan geçici `ltz-community-test-pg` container'ı Testcontainers'a ait değildir; Testcontainers sonrası gerekmez. `docker ps -a` ile kalıp kalmadığına bak, kaldıysa `docker rm -f ltz-community-test-pg`.
- Çalışma ağacında community-service dışı, bu işe ait olmayan değişiklikler vardır (`docs/decisions/migrations.md` Game Service ek aralığı, `frontend/package-lock.json`); commit'e dahil etme.
- Commit/push **yapılmaz** (AGENTS.md §7); kaldırma sonrası yalnız commit mesajı öner.
