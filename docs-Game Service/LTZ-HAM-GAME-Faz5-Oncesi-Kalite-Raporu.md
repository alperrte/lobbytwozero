# Game Service — Faz 5 öncesi kalite ve yarış düzeltmeleri

> **Tarihsel not (2026-10-21):** Bu raporda geçen V31–V40 ve V171–V173 numaraları ve DEV geçmişi, eski 13 dosyalı migration zincirine aittir. Aynı gün `ltz-dev` ortamı sıfırlandı ve DEV `game_db` yeni V31–V40 zinciriyle (10 migration) kuruldu; aktif eşleştirme [migrations.md](../docs/decisions/migrations.md) ve [yeniden yapılandırma raporundadır](LTZ-HAM-GAME-Migration-Yeniden-Yapilandirma.md). Bu raporlardaki V171–V190 ek aralığı ve "sıradaki V174" ifadeleri o günün kararıdır; ek aralık ve V174–V190 rezervasyonu sonradan kaldırılmıştır. Aşağıdaki sonuçlar o tarihteki teslim durumunu gösterir.

Tarih: 2026-10-21 · Sorumlu: Hamza · Branch: feature/game-service

## Kapsam

Faz 5 başlatılmadı. GAME-23/24/26, gerçek Auth/Review/detay sağlayıcısı entegrasyonu,
yeni migration, yeni ENV ve başka servislerde kod değişikliği yapılmadı.
V31–V40 migration dosyaları değiştirilmedi. Commit/push/merge yapılmadı.

## İlk FAIL sonucunun kök nedenleri

Kanıt: `logs/pre-push-2026-10-21T13-43-55-628Z-24536-b1a18150.log`.

- Gerçek tam kontrol komutu `pre-push/pre-push.cmd full dev`; `quality.mjs`
  Compose DEV bağlantı ayarlarını yalnız test alt sürecine aktarır ve 18 Maven
  modülünde `clean verify` çalıştırır. Git hook otomatik kurulu değildir
  (`.git/hooks/pre-push.sample` yalnız örnektir).
- Test context'leri `Connection to 127.0.0.1:5542 refused` ile açılmamış.
  Flyway bağlantı kuramıyor; bu log bir migration SQL veya assertion hatası kanıtı değil.
- Backend Build FAIL, normal `verify` içindeki test hatalarının sonucudur.
  Coverage adımı başarılı, taze test raporu olmadığı için çalıştırılmamış;
  ilk özet gerçek bir düşük kapsam yüzdesi göstermiyor.
- HTTP health adımı `Cannot inspect running services` ile durmuş. Bu mesaj
  tek başına uygulamanın DOWN olduğunu göstermez. Bu oturumda kısıtlı araç
  ortamının Docker pipe erişimi de reddedildi; yükseltilmiş erişimle konteynerler görüldü.
- Önceki inceleme oturumunun bağımlılık çözümleme hatası (`Permission denied: getsockopt`)
  bu kullanıcının tam kontrol logundaki PostgreSQL hatasından ayrıdır.
- JDK 25.0.1, Maven 3.9.16, Spring Boot parent 4.1.1 doğrulandı.
  Bağımlılık/plugin sürümü veya kalite eşiği değiştirilmedi.

Tekrarını önlemek için tam kontrolden önce Docker Engine erişimi, DEV PostgreSQL
ve 18 servis doğrulanmalıdır. `full dev` konteynerleri kendisi başlatmaz.
Kaynak güncellendiğinde mevcut imajın da güncellendiği ayrıca kontrol edilmelidir.
Bu oturumda test/analiz sahibi artık çalışmayan PID 34192'ye ait eski kalite kilidi,
süreç listesi ve kullanıcı teyidi kontrol edildikten sonra kaldırıldı; çalışan
test veya analiz kesilmedi. Ortam sorunu test silerek veya eşiği gevşeterek giderilmedi.

## Yetki iptali / içerik yazma yarışı

Önceki kod yetkiyi oyun kilidinden önce okuyordu. İptal transaction'ı oyun kilidini
tutup yetkiyi iptal ederken yazma bu kilitte bekleyebiliyor; iptal commit edilince
eski kontrol sonucuyla içerik yazabiliyordu.

`GameAccessPolicy`: ön kontrol (yetkisiz kaynak davranışını korumak için) korunur;
oyun kilidi alındıktan sonra aktif yetki yeniden okunur. İptal de aynı oyun kilidini
aldığından READ COMMITTED altında iptal önce commit edilmişse yazma reddedilir;
yazma kilidi önce almışsa iptal yazma transaction'ı bitene kadar bekler.
Bu gerekçe servislerin mevcut READ COMMITTED transaction davranışı içindir;
farklı izolasyonla çağıran yeni akışlar ayrıca değerlendirilmelidir.

`DeveloperRevocationRaceTest` gerçek, commit edilen PostgreSQL transaction'ları kullanır.
`pg_stat_activity` üzerinde Lock beklemesi gözlenir; gözlem transaction'ının istatistik
snapshot'ı `pg_stat_clear_snapshot()` ile yenilenir. İptal-önce ve yazma-önce sıraları
ayrı sınanır. İlk test düzeninde snapshot önbelleği nedeniyle bekleme görülmedi;
bu test gözlem hatası düzeltildi, doğrulama kaldırılmadı.
Sonar ilk turda testin `Thread.sleep` kullanımını S2925 olarak bildirdi. Mevcut
Spring test bağımlılığının zaten sağladığı Awaitility ile aynı thread'de koşul
beklenir (`pollInSameThread`); yeni bağımlılık eklenmedi ve gerçek Lock gözlemi korunur.

Eski üretim kodu + yeni regresyon testinde iptal-önce testi FAIL verdi
(`expected true but was false`); düzeltilmiş kodda iki test PASS.
Kanıt: `logs/game-service-race-before-fix.log`, `logs/game-service-race-targeted.log`.
Eski kod denemesi sonunda düzeltmeler `finally` ile geri yüklendi.

## Rate limit maxKeys yarışı

Eski kodun boyut kontrolü ve yeni anahtar eklemesi atomik değildi. İlk 32 anahtarlı
koşuda aşım üretilmedi. Aynı hash'e sahip farklı String anahtarlarıyla 64 iş parçacıklı
ek denemede eski kod `maxKeys=1` iken **17 kayıt** oluşturdu (ilk tur).
Deneme yalnız geçici klasörde eski sınıfın bağımsız derlemesiyle yapıldı.
Düzeltilmiş sınıf aynı bağımsız denemede 200 turda sınırı aşmadı.

`tryAcquire` ve `trackedKeys` aynı nesne monitörünü kullanır. Kabul, dolu/boşta
kovaların temizliği ve jeton alma tek kritik bölgededir. Bu, hem anahtar sınırını
hem seçilen kovanın jeton alınmadan temizlenmemesini sağlar. Mevcut refill,
Retry-After ve ortak taşma kovası davranışı korunur. Yeni bağımlılık yoktur.

Regresyonlar: 32 farklı anahtar + toplam taşma bütçesi; 64 eş-hash anahtar,
16 tur, tek kayıt sınırı + tek ortak taşma kovası. Mevcut aynı-anahtar kapasite,
refill ve temizleme testleri korunur. Dağıtık/replika başına toplam limit bu görevin
kapsamı değildir; sınırlayıcı süreç içidir.

## Önceki fazlardan kalan Sonar bakım bulguları

Yeni kod filtresiyle sıfır bulgu görülmesi, tüm projede sıfır bulgu anlamına
gelmiyordu. Tam proje sorgusunda Game Service'in önceki kodunda 17 code smell
bulundu; bug, vulnerability veya hotspot yoktu. Bunlar görev kapsamındaki
kalite bulguları olarak giderildi:

- S1845: `CatalogImportService` sabiti `PROVIDER_CODE` oldu; enjekte edilen
  `provider` alanıyla ad çakışması giderildi.
- S6809: `CatalogSearchService` iki public transaction girişinden ortak private
  sorgu metoduna gider; transaction'lı public metodun sınıf içi çağrısı kaldırıldı.
  Her iki public girişin read-only transaction sınırı korunur.
- S3776: `SteamGameProvider` HTTP durum doğrulaması küçük bir metoda ayrıldı;
  hata kodları, Retry-After ve payload sınırı değişmedi.
- S7467 / S5411: kullanılmayan catch değişkenleri ve nullable Boolean ifadesi düzeltildi.
- S5778: kimlik/sağlayıcı/yapılandırma/persistence testlerinde istek ve kimlik
  hazırlıkları exception lambda'sının dışına alındı; her lambda yalnız sınanan
  işlemi çağırır. Assertion veya negatif test kaldırılmadı.

Bu sınırlı düzenlemeler raporlanan bulgular içindir; bağımlılık güncellemesi,
sağlayıcı entegrasyonu veya yeni özellik eklenmedi.

## Çalışan DEV ortamı

İlk canlı gözlemde 18 health ucu UP idi; fakat doğrudan ve Gateway üzerinden
public katalog isteği 401 döndü. Game Service startup logu yalnız 5 migration ve
şema V35 gösteriyordu: çalışan imaj Faz 3–4 kaynak kodundan gerideydi.
Health UP, güncel özellik sürümünün çalıştığını tek başına kanıtlamaz.

Kullanıcı yalnız DEV Game Service imajını yeniden derleyip başlatmayı ve mevcut
V36–V40 migration'larının DEV game_db'ye uygulanmasını ayrıca onayladı.
Komut: `docker compose --env-file .env.dev -f docker-compose.yml up -d --build --no-deps game-service`.
Veritabanı veya volume silme/sıfırlama yoktur; diğer servisler yeniden başlatılmaz.
Dockerfile'ın mevcut paketleme adımı test atlar; bu imaj build'i test kapısı yerine
kullanılmadı, testler ayrıca normal `clean verify` ile doğrulanır.

## Migration tahsisi ve dokümantasyon

Merkezi kaynak `docs/decisions/migrations.md`: ek **V171–V190**, 2026-10-21
kullanıcı onayıyla yalnız Game Service'e ayrıldı. V1–V170 tahsisleri değişmez.
Bu belge ortak branch'e aktarılmalı; diğer ekip üyeleri V171–V190 kullanmamalıdır.
Tahsis Faz 5 geliştirme onayı değildir; yeni SQL dosyası oluşturulmadı.

Game Service faz takip belgesi, Faz 3–4 raporları ve bekleyen işler belgesindeki
eski Faz 3/4 onay ifadeleri / migration kararı güncellendi. Auth belgesindeki
`image-hosts` / `video-hosts` / `demo-hosts`, gerçek `*-sources` adlarına düzeltildi.
`docs/api.md` GAME bölümüne içerik yazmaları/iptal transaction sırası ve testi eklendi.
Ortak SECURITY/architecture belgelerindeki eski genel durum ifadeleri bu görevde
değiştirilmedi; kapsamlı ortak güncelleme ayrı onay bekler.

## Güncel kalite sonuçları

İlk düzeltme sonrası Game Service: **PASS**, 185 test, 0 hata/atlanan,
%98,47 satır kapsamı; Maven normal verify ve paketleme PASS.
Kanıt: `logs/game-service-hardening-final.log`.
Nihai koşular eş-hash stres testini ve eski 17 bakım bulgusunun düzeltmelerini de içerir.

- Tüm backend test / build / coverage: **PASS**, 203 test, toplam %95,92 satır kapsamı.
  Game Service 186 test, %98,48 satır kapsamı. Normal `clean verify` paketleme ve
  JaCoCo raporları başarılı; hata ve atlanan test yok.
- Tam pre-push / HTTP health: **PASS**, `SAFE TO COMMIT`; 18/18 servis HTTP health PASS.
  Kanıt: `logs/game-service-hardening-full-complete.log` (nihai kaynak ağacı ve son DEV imajı).
  `logs/game-service-hardening-full.log` ve `logs/game-service-hardening-full-final.log`
  ara koşulardır; nihai sonuç yerine kullanılmaz.
- SonarQube: **PASS**, `SONAR SCAN PASSED`; Quality Gate, analiz, erişim,
  taze backend raporları ve genel hedefler PASS. Son runner turu **203 backend testi**
  (Game Service **186**, 0 hata/atlanan), toplam JaCoCo **%95,92**, Game Service **%98,48**.
  Sonar'ın ayrı kapsam metriği tüm kodda **%90,0**, yeni kodda **%91,4**;
  duplication %0, güvenlik/güvenilirlik/bakım notları A. Yerel JaCoCo toplayıcısı
  ve Sonar kapsam metriği ayrı sonuçlardır, birbirinin yerine yazılmadı.
  İlk API sorgusu yeni kodda **0 issue, 0 hotspot** gösterdi. Tüm kod sorgusu
  önceki fazlardan kalan **17 bakım bulgusu** gösterdi (bug/vulnerability/hotspot yok).
  Bu ayrım açıkça kontrol edildi; 17 bakım bulgusu giderildikten sonraki
  tam kod sorgusu **0 issue, 0 hotspot** döndü. Güncel bug, vulnerability,
  code smell ve hotspot sayısı sıfırdır.
  İlk turdaki tek S2925 bakım bulgusu kodla giderildi; bastırma, false-positive
  işareti, kalite kapısı/coverage eşiği veya güvenlik gevşetmesi yapılmadı.
  Kanıt: `logs/game-service-hardening-sonar.log` (ilk FAIL),
  `logs/game-service-hardening-sonar-final.log` (ara PASS),
  `logs/game-service-hardening-sonar-complete.log` (17 eski bulgu da giderildikten sonraki nihai PASS).
- Güncel DEV imajı ve public katalog: **PASS**. İmaj yeniden oluşturuldu;
  mevcut V36–V40 başarıyla uygulandı, şema V40. Game Service :5084 ve Gateway :5080
  üzerinden hem health hem public `/api/v1/game/games?size=1` HTTP 200 verdi.
  JSON batch her iki portta 200, anonim admin erişimi 401, JSON olmayan batch 403 verdi.
  Kanıt: `logs/game-service-hardening-dev-image.log`,
  `logs/game-service-hardening-dev-image-final.log` ve çalışma anındaki startup/HTTP doğrulaması.
  Son imajda 10 migration doğrulandı, şema V40 ve yeni uygulanacak migration yok.

Son yerel keşif regresyon ölçümü: 100.000 kayıt, PostgreSQL 17 Testcontainers,
Java 25, havuz 3; liste p95 34,02 ms, arama 10,35 ms, birleşik filtre 148,20 ms,
eşzamanlı arama 18,37 ms. Testteki 300 ms hedefi PASS; üretim garantisi değildir.
Kanıt: `backend/services/game-service/target/catalog-discovery-performance.json`
(üretilmiş dosya; sonraki clean çalıştırmasında yenilenir).

## Açık kalanlar

Gerçek Auth claim/token entegrasyonu, Review toplu puan API'si ve GAME-04/05/15,
GAME-17 izinli detay kaynağı, Gateway güvenilir istemci IP iletimi, diğer uçlarda
rate limit ve çok replikalı doğrulama önceki gibi açıktır. Bunlar bu görevde
tamamlanmış sayılmaz. Faz 5 için ayrıca açık kullanıcı onayı gerekir.

## Değiştirilen dosyalar

- Üretim: `application/service/GameAccessPolicy.java`,
  `infrastructure/security/TokenBucketRateLimiter.java` (Game Service içinde).
- Sonar bakım düzeltmeleri: `application/service/CatalogImportService.java`,
  `application/service/CatalogSearchService.java`, `infrastructure/client/SteamGameProvider.java`.
- Test: yeni `api/controller/DeveloperRevocationRaceTest.java`,
  genişletilen `infrastructure/security/TokenBucketRateLimiterTest.java`.
- Exception test düzenlemeleri: `GameIdentityServiceTest.java`, `SteamGameProviderTest.java`,
  `SteamProviderConfigurationTest.java`, `GameMetadataPersistenceTest.java`.
- Merkezi tahsis: `docs/decisions/migrations.md`.
- API: yalnız `docs/api.md` içindeki GAME bölümü.
- Game Service belgeleri: Faz 1/2/3 raporlarındaki eski onay ifadeleri,
  Faz 4 raporu, Auth entegrasyonu, sıralı faz takip belgesi ve bekleyen işler belgesi.
- Yeni rapor: bu dosya. Yerel kanıt logları `logs/` altındadır; commit edilmez.

Kalite scriptleri, Maven/POM, Docker/Compose dosyaları, ENV dosyaları,
diğer servislerin kaynak kodu ve mevcut migration SQL dosyaları değiştirilmedi.
