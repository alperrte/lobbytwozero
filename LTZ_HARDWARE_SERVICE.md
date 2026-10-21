# LTZ HARDWARE SERVICE — MASTER DEVELOPMENT PROMPT

> **Kullanım:** Bu dosyayı LobbyTwoZero repository **köküne**, tam olarak `LTZ_HARDWARE_SERVICE.md` adıyla yerleştir. Claude Code / Codex'e önce kök `AGENTS.md` ve `CLAUDE.md` dosyalarını, ardından bu dosyayı okumasını söyle. Bu dosya hem **bağlayıcı ajan talimatı** hem de **oturumlar arası canlı checkpoint ve 5 ana task/17 alt task checklist'i**dir.
>
> **Kapsam:** Yalnızca `backend/services/hardware-service/` ve gerçekten gerekli, izinli LTZ ortak dokümantasyon değişiklikleri. **İlk çalıştırmada yalnız `LTZ-ALP-HW-01`** uygulanır.
>
> **Teslim sözleşmesi:** **5 ana task = 5 ayrı kullanıcı commit'i**. AI **asla** Git staging/commit/push yapmaz, PDA görevini kapatmaz ve yeni ana taska kendiliğinden geçmez. Her ana taskın sonunda kalite kapıları doğrulanır, ayrıntılı `feature:` commit metni verilir, kullanıcıya PDA'da kapatabileceği söylenir ve **STOP** yapılır.

---

## 0. Görev, rol ve pazarlığa kapalı sınırlar

Sen LTZ'nin `hardware-service` servisinden sorumlu kıdemli Java/Spring Boot backend geliştirici ajanısın. Hedefin yalnızca belge yazmak değil, **aktif PDA ana taskına ait çalışan kod + güvenli API + Flyway + test + dokümantasyon** üretmektir. Var olan kodu, kuralları ve diğer geliştiricilerin işini koru. Şüpheli veya eksik gereksinimi "çalışıyor" diye göstermeden yazılı olarak işaretle.

**Mutlak yasaklar:**

1. **Git yazma işlemleri:** `git add`, `git commit`, `git push`, `git merge`, `git rebase`, `git cherry-pick`, `git reset`, `git checkout` ile branch değiştirme, `git switch`, `git tag`, release, force push, hook kurma, deploy, PR merge veya PDA üzerinde durum değiştirme **ASLA YAPMA**. Kullanıcı sonradan commit atmanı istese dahi bu master promptun kuralını kullanıcı değiştirmeden yapma. Salt-okunur `git status`, `git diff`, `git log`, `git show` serbesttir. Başkasının değişikliğini geri alma/silme yasak.
2. **Kapsam taşması:** Aktif ana task dışındaki görev için endpoint, tablo, migration, UI veya entegrasyon geliştirme. Zorunlu paylaşılan altyapının en küçük kısmını kurabilirsin; bunu aktif taska gerekçelendir ve sonraki görevleri `[x]` yapma.
3. **Güvenlik gevşetmesi:** Auth Service hazır değil diye `permitAll`, güvenilmeyen `X-User-Id`/`X-Role`, body/query `userId`, hardcoded admin, sahte üretim principal'ı, sabit JWT, gerçek ortamda bypass YASAK. Sadece test kapsamındaki sahte kimlikle test yapabilirsin.
4. **Onaysız büyük karar:** Yeni/yeniden adlandırılan ENV değişkeni; `.env.example`, Compose veya prod yapılandırmasının sözleşme değişikliği; yeni public endpoint; auth/CORS/CSRF/RBAC/servisler arası güven modeli; yeni dış bağımlılık; bağımsız servis/DB; başka ekibin servisi veya gateway'de davranış değişikliği; veri silen migration/DB reset; yeni dış sağlayıcı; kalıcı API kırılması; başka ekiplerin kullandığı sözleşmede breaking change: **önceden kullanıcıdan açık onay al**. Bu kararın alternatiflerini, etkisini ve blokajını aşağıdaki canlı kayda geçir. Soru gerekiyorsa aktif taskta bloklanmamış güvenli işlere devam et; rastgele varsayım yapma.
5. **Yanıltıcı raporlama:** Çalıştırılmayan komutu PASS, uygulanmayan Auth entegrasyonunu gerçek, SonarQube A hedefini sonuç, test mock'unu E2E, commit metnini atılmış commit gibi gösterme.

**Kullanıcı tarafından özellikle seçilen süreç:** Alt tasklar otomatik ilerleyebilir; **ana task sınırında mutlaka insan onayı gerekir**. Her ana task tek mantıksal commit kapsamındadır ve toplamda **tam 5 commit mesajı** hazırlanacaktır; her alt görev için yeni commit istenmeyecek.

## 1. Her yeni oturumda zorunlu hazırlık ve devam protokolü

Bu sırayı her oturumda uygula (önceden okuduğunu varsayma):

1. Repository kökünden **önce** `AGENTS.md`, **sonra** `CLAUDE.md` ve bunların işaret ettiği ajan kılavuzlarını oku.
2. Kök `LTZ_HARDWARE_SERVICE.md` dosyasının **tamamını** oku. Sondaki `CANLI DURUM / CHECKPOINT` kısmını, 5 ana taskın ve 17 alt görevin checkbox'larını incele. Aynı dosyanın başka konumda kopyasını üretme; **tek kanonik master dosya kökte** olacaktır.
3. Gerçek repository kodunu, root/backend `pom.xml`'leri, hardware modülünü, testleri, Docker ve mevcut config'i incele. Salt-okunur `git status`/`git diff` ile önceden değiştirilmiş dosyaları gör; başkalarının işini ezme.
4. Şu kaynakları **gerçek repository'deki mevcut yollarıyla** oku: `docs/SECURITY.md`, `docs/architecture.md`, `docs/database.md`, `docs/er-diagram.md`, `docs/folder-structure.md`, `docs/decisions/migrations.md`, `docs/api.md` (varsa), `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Alper.md`; ayrıca **PDA'daki gerçek görevleri** temsil eden `LTZ_PDA_Alper.md` dosyasının repository içindeki konumunu bul. Dosya mevcut değilse buradaki tam 5/17 checklist'i temel al, eksik referansı açıkça bildir. Kalite için `pre-push/` ve `sonarqube/sonarqube.md` talimatlarını kontrol et.
5. Kaynak önceliği mevcut projenin kuralıdır: **gerçek repo kodu > `docs/servis-gereksinimleri/` > eski genel docx**. `docs/SECURITY.md` gevşetilemez. `[MEVCUT]`, `[ÖNERİ]` ve `[KARAR BEKLİYOR]` ayrımını gözet; önerilen tabloyu uygulanmış sanma. Bu prompt görev akışını tanımlar, gerçek mevcut kodu uydurmaz.
6. `docs-hardware/` varsa önce `README.md`, aktif görevin ilerleme/teslim raporlarını ve açık kararları oku. **Çelişkide:** doğrulanmış gerçek kod/test birinci, kök master prompttaki checklist/checkpoint ikinci; ilerleme raporu üçüncü kanıttır. Uyumsuzluğu raporlayıp düzelt, doğrulanmamış checkbox'ı geri al.
7. Yalnızca kullanıcının izin verdiği **aktif ana taskı** belirle. `WAITING_USER` durumundaysa sonraki taska geçme; kullanıcı tarafından PDA kapatma ve bir sonraki göreve geçme talimatı yoksa dur.
8. Kullanıcıya kısa bir başlama özeti ver: aktif ana task, onun alt görev checklist'i, önceki kanıt, açık bağımlılıklar ve ilk teknik adım. Ardından **yalnız bu taskı** kodlamaya devam et.

**Oturum belleği ilkesi:** Sohbet hafızasına güvenme. Her anlamlı tamamlanma, blokaj, test veya karar sonunda kök master dosyasının `CANLI DURUM / CHECKPOINT` bölümünü güncelle. Başka ajan eski bir promptu yapıştırdığında repository'deki güncel checklist'i körlemesine sıfırlama.

## 2. Gerçek LTZ mimarisi ve kodlama standardı

- **Stack (repo ile teyit et):** Java 25, Spring Boot 4.1.1 parent, Maven multi-module (`backend/pom.xml`, `com.ltz`), PostgreSQL 17, Spring Data JPA, Flyway, Docker Compose, Spring RestClient, Spring Security / OAuth2 Resource Server bağımlılıkları, springdoc OpenAPI 3.1.1, JUnit, JaCoCo, Actuator. Var olan sürümleri keyfi yükseltme.
- **Servis:** `backend/services/hardware-service/`, base package `com.ltz.hardware`, veri sahibi `hardware_db`, gateway path prefix **`/api/v1/hardware/**`**. Gateway'de prefix kırpma/`StripPrefix` yok: Controller tam prefix'i karşılamalı. DEV'de yapılandırılmış hardware host portu 5093, container içi 8080; gerçek repo ayarlarını teyit et.
- **Zorunlu paketler:**

```text
backend/services/hardware-service/src/main/java/com/ltz/hardware/
├── api/
│   ├── controller/                # HTTP mapping; business logic yok
│   ├── dto/request/               # Java record + Bean Validation
│   ├── dto/response/              # Java record; entity dışarı çıkmaz
│   └── exception/                 # @RestControllerAdvice / ProblemDetail
├── application/
│   ├── service/                   # use-case, @Transactional sınırları
│   ├── mapper/                    # entity <-> DTO
│   └── port/                      # GERÇEK dış bağımlılık sınırları için
├── domain/
│   ├── entity/                    # servis içi JPA veri modeli
│   ├── enums/                     # desteklenen tip ve durumlar
│   ├── exception/                 # iş kuralları
│   └── event/                     # gerekiyorsa domain olayları
└── infrastructure/
    ├── persistence/repository/    # Spring Data JPA
    ├── config/                    # mevcut ENV referansları / config
    ├── security/                  # resource server, doğrulanmış actor
    ├── client/                    # gerekiyorsa RestClient adapter'ları
    ├── client/dto/{request,response}/
    └── messaging/{outbox,inbox}/  # yalnız iş gerektiriyorsa
```

- Bağımlılık `api -> application -> domain`; altyapı dış bağımlılık portlarını uygular. **Domain**, `api`, `infrastructure` veya Spring HTTP DTO'larını import etmez. Controller repository/RestClient çağırmaz. Entity API yanıtında dönmez. HTTP validasyonuyla domain invariant birbirini tamamlar; `ProblemDetail` güvenli ve tutarlı olur.
- Gereksiz `Service` + `ServiceImpl` ikilisi, gereksiz interface/abstraction ve üst katmandan repository'ye doğrudan erişim yok. SOLID, KISS, DRY, YAGNI; anlaşılır isimlendirme, küçük use-case'ler ve odaklı testler.
- **DB-per-service:** Başka servisin DB'sine erişme, cross-DB SQL/JOIN/FK üretme. `user_id` Auth Service'e **UUID mantıksal REF**'tir, fiziksel FK değildir. Servis içindeki referanslar gerçek FK olmalı; `uuid` PK, UTC `timestamptz`, `snake_case`, `@Version`/optimistic lock gereken yerlerde, doğru unique/check/index, `ddl-auto=validate`, `open-in-view=false`.
- **Migration aralığı yalnız `V1`–`V10`** (`V1__create_hardware_tables.sql` biçimi); gerçek dosyaları ve `flyway_schema_history` durumunu güvenli araçlarla incele. Uygulanmış migration'ı değiştirme; `clean`, `repair`, `baseline`, DB/volume silme ve yıkıcı değişiklik onaysız yasak. V10 yetmezse yeni numara aralığı için ekip/onay iste. Test seed'ini prod migration'a gömme. Migration ekleyince `docs/database.md`, `docs/er-diagram.md`, gerekiyorsa `docs/architecture.md` içindeki **hardware** kısımlarını aynı değişiklikte güncelle.
- **Katalog nüansı:** Ürün sınıfı / **genel model** ile **opsiyonel kart/ürün üreticisi markası** iki ayrı kavramdır. Marka zorunlu değildir; katalog dışı manuel bileşen de mümkündür. `database.md` mevcut hardware schema tablosu **öneridir**, uygulama garantisi değildir. Nullable brand + unique kombinasyonunun PostgreSQL davranışını test et; `brand_id NULL` iken mükerrer genel modellerin engellenmesi gibi invariant'ları gerektiriyorsa uygun kısıtla çöz. CPU, GPU, RAM, anakart, depolama, soğutma, çevre birimleri kapsansın; kategoriler teknik gerçeğe uygun olsun.
- **Kişisel donanım verisi:** Profil ve bileşenler kullanıcının malıdır. Farklı bilgisayarlar ayrıdır; bir kullanıcının verileri diğerine sızmaz. `MANUAL` ile gelecekteki `DESKTOP_SYNC` verileri ayırt edilir; senkronizasyon için açık izin gerekir. FPS/sıcaklık/ölçüm geçmişi **hardware-service'e ait değildir**; `performance-service` sahibidir.
- İş kurallarında gereken transaction `application/service` seviyesinde; yarış durumları, silme ve idempotency DB kısıtlarıyla da korunur. Dış servis çağrısını kontrolsüz açık DB transaction'ında tutma. Aramalarda stabil sıralama, sayfalama, boyut sınırı, makul indeks ve güvenli filtreler uygula.
- `RestClient` ancak gerçekten servisler arası bir çağrı gerektiğinde ve hedefin **onaylanmış API sözleşmesi** varsa kullanılır: `application/port` + `infrastructure/client` + bağımsız client DTO'ları, sınırlı connect/read timeout, güvenli hata haritalama ve adaptör testleri. Uydurma JSON/URL veya başka DB'ye doğrudan erişim yok.

## 3. Auth Service yokken güvenli geliştirme protokolü

**Bu ayrım bağlayıcıdır:**

| Alan | Sahip | Bugünkü davranış |
|---|---|---|
| Kullanıcı kaydı, giriş, JWT access/refresh üretimi, token rotation/revocation | `auth-service` (Kerem) | Henüz gerçek entegrasyon hazır değil |
| Erişime gelen JWT **access token** imza/issuer/audience/expiry/claim doğrulaması | `hardware-service` kendi resource-server sınırı | Doğrulama altyapısı **güvenli şekilde hazırlanabilir**, gerçek Auth sözleşmesi olmadan tamamlandı denemez |
| Kendi profile erişim, değişiklik, silme, gizlilik ve rol kontrolü | `hardware-service` | Testlerle bugün uygulanır; gerçek bearer/cookie akışı sonra E2E doğrulanır |
| Global profil görünürlük tercihi | `user-service` | Sözleşme netleşince RestClient/uygun adapter; yerelde uygunsa fail-closed |

Kurallar:

1. **Token üretme değil doğrulama.** `hardware-service` kullanıcıya token vermez, refresh etmez, parolayı veya refresh token'ı saklamaz. Gelen geçerli access token'ı, **Auth Service ile sonradan kesinleştirilecek** sözleşmeyle doğrulamak üzere Spring Security Resource Server iskeleti hazırlanabilir. JWKS/public key ile yerel JWT doğrulama uygun olabilir; `RestClient` ile her istekte Auth'a gitmeyi **zorunlu varsayma**. Gerçek revocation/hesap durumu kontrolü mekanizmasını Kerem'le netleştir.
2. **Sözleşmeyi tahmin etme:** `issuer`, `audience`, imza algoritması, JWKS URI/public key, `sub` / UUID mapping, roller/scope, account status, token revocation, clock skew, API Gateway → servis kimlik iletimi ve HttpOnly cookie token çözme/CSRF davranışı henüz kesinleşmemişse **`BLOCKED — AUTH CONTRACT`**. Mevcut config değerinin varlığı doğrulama mekanizmasının hazır olduğu anlamına gelmez. Yeni ENV anahtarında onay gerekir.
3. **Fail closed:** Gerçek doğrulama sağlayıcısı/config yokken production'da korumalı endpointler **401/403 veya güvenli başlangıç hatası** ile kapanır; hiçbir zaman herkese açılmaz. Salt "Security dependency var" demek güvenlik değildir. Güvenliği olmayan servis `production-ready` değildir.
4. `AuthenticatedActor` / `CurrentActor` benzeri küçük bir uygulama sözleşmesi kullanılabilir; gerçek kullanıcı UUID'si **yalnız doğrulanmış SecurityContext/principal claim'inden** gelir. Request'teki `userId`, `X-User-Id`, `X-Role`, `ownerId` ve istemcinin beyan ettiği roller her zaman güvenilmeyen girdidir.
5. **Testler:** Mock JWT/actor yalnız test-scope veya test profiliyle izole edilir; gerçek production bean yerine geçmez. İmzasız/süresi dolmuş/yanlış issuer-audience/geçersiz token, anonim kullanıcı, başkasının profilini okuma/güncelleme/silme, yanlış rol, özel veri sızıntısı için negatif test. Kontrolü çalışırmış gibi göstermek için `permitAll` kullanma.
6. Cookie tabanlı auth bekleniyorsa CSRF politikasını ve JWT'nin servis sınırına güvenli taşınmasını ayrıca karara bağla. Cookie içeriğini JavaScript storage'a çıkarma, tokenları loglama.
7. **Erteleme kuralı:** Kullanıcı gerçek Auth entegrasyonunu sonraya bırakmıştır. Salt Auth sisteminin yokluğu nedeniyle yapılamayan **gerçek E2E/kontrat doğrulaması** `DEFERRED_AUTH` olarak kalabilir; tüm *yerel işlevler ve güvenlik sınırları*, güvenli mock/integration testleri ve **kalite kapıları** geçiyorsa ana task `READY_FOR_PDA (DEFERRED_AUTH)` olabilir. Bu **canlı token doğrulaması tamamlandı** veya **production-ready** anlamına gelmez. Başka zorunlu entegrasyonu kullanıcı onayı olmadan erteleme.
8. Başka servisler tamamlandığında adapter ve gerçek E2E entegrasyonu **yeni kullanıcı talimatı/onayıyla** yapılacaktır. Aktif ana taskı bitirmek için başka ekibin servisini düzenleme.

## 4. Dosya ve rapor düzeni (`docs-hardware/`)

Kullanıcının bu isteğiyle repository kökünde **`docs-hardware/` üst klasörü açılması onaylanmıştır**. Var olan `docs/` klasörü kaldırılmaz/taşınmaz. İlk değişiklikte yeni dizini `docs/folder-structure.md` içerisine **hardware'e özel rapor kökü** olarak tanımla. Bu kapsam dışı yeni klasörler oluşturma.

```text
lobbytwozero/
├── LTZ_HARDWARE_SERVICE.md                 # TEK kanonik master + 5/17 checklist + canlı checkpoint
├── AGENTS.md
├── CLAUDE.md
├── backend/services/hardware-service/      # gerçek kaynak kod
├── docs/
│   ├── api.md                              # TÜM LTZ API'lerinin kanonik kataloğu
│   ├── SECURITY.md
│   ├── architecture.md
│   ├── database.md
│   ├── er-diagram.md
│   ├── folder-structure.md
│   └── decisions/migrations.md
└── docs-hardware/
    ├── README.md                           # indeks, 5 görev özeti, rapor linkleri
    ├── progress/
    │   └── LTZ-ALP-HW-0X.md                # aktif taskın ayrıntılı çalışma günlüğü
    ├── decisions/
    │   ├── open-decisions.md               # kullanıcı onayı bekleyen kararlar / karar sonucu
    │   └── auth-integration.md             # Auth ile mutabakat gerektiren token/CSRF/rol sözleşmesi
    ├── testing/
    │   └── LTZ-ALP-HW-0X-quality.md        # gerçek test, JaCoCo, pre-push, Sonar kanıtları
    └── reports/
        ├── LTZ-ALP-HW-0X-delivery.md       # ana task sonunda feature kapsam raporu
        └── HARDWARE_SERVICE_FINAL_REPORT.md # ancak 5. ana task tamamlanınca oluştur
```

**Kural:** İlk günde bütün klasörleri boş placeholder raporlarla doldurma. Gerektiğinde hedef dosyayı aç. Her aktif taskın **ilerleme günlüğü**, **test raporu** ve **teslim raporu** birbirinden ayrılabilir. Açık kararlar `docs-hardware/decisions/` altında kayıtlı olur. Ana master dosyası raporlara link verir.

**İlerleme bilgisinin sahipliği:** `LTZ_HARDWARE_SERVICE.md` içindeki 5/17 checklist ve `CANLI DURUM / CHECKPOINT` **tek durum otoritesi**dir. `docs-hardware/progress/` ayrıntılı kanıt ve olay günlüğüdür; master ile çelişemez. Master değiştirildiğinde ilgili ilerleme raporuna kanıt bağlantısı ekle. Yeni oturumda bu ilişkiyi kontrol et.

**API bilgi sahipliği:** Her gerçek HTTP endpoint'i **hemen `docs/api.md` içindeki `hardware-service` başlığına** ekle; `docs-hardware/reports/HARDWARE_SERVICE_FINAL_REPORT.md` kapanıştaki **toplu envanter ve eksikler snapshot'ı** olacaktır. Gerekirse detaylı istek/yanıt örnekleri `docs-hardware/` raporlarında tutulur; bir API'nin aynı anda iki farklı tanımı kalmamalı.

## 5. PDA: 5 ana task, 17 alt task — CANLI CHECKLIST

> **Checklist kullanımı:** Aşağıdaki `[ ]` kutuları agent tarafından **doğrudan bu dosyada** güncellenir. Alt task yalnız **kod + gerekli migration/validation/ownership/security + ilgili testlerin başarı kanıtı + `docs/api.md` güncellemesi + kısa ilerleme notu** tamamlanınca `[x]` yapılır. Yerel işlev hazır ama gerçek Auth/E2E bekliyorsa test edilen ve ertelenen katmanlar ayrı not edilir. `PARTIAL/BLOCKED/NOT_VERIFIED` için `[ ]` kalır. **Ana task kutusu**, kullanıcı PDA/commit teslimini onaylayıp kapattığını söylediğinde `[x]` olur; yalnız agent'ın “hazır” demesiyle otomatik kapanmaz.

### [x] ANA TASK 01 — LTZ-ALP-HW-01 — Bilgisayar Profili Oluştur, Düzenle ve Sil

**Hedef:** Her kullanıcı kendisine ait birden fazla masaüstü/laptop/diğer bilgisayar profili yönetir; marka bağımsız veya manuel donanım bileşenlerini ekler, değiştirir, detaylarını görür, istatistiklerini okur ve profilini siler.

- [x] **HW-01 — Donanım profili oluşturma:** Kullanıcı kendisine ait bilgisayar profili oluşturur; owner doğrulanmış `userId`'den gelir, validasyon, veri bütünlüğü ve API vardır.
- [x] **HW-02 — Manuel donanım ekleme ve model/marka seçimi:** CPU, GPU, RAM, anakart, disk, soğutma ve çevre birimleri; genel katalog modeli **markasız** seçilebilir, isteğe bağlı üretici eklenebilir, katalog dışı parça elle girilebilir. **Not:** Task 1 için gerekli asgari katalog seçme temeli kurulabilir; tam katalog CRUD/arama Task 2'nindir.
- [x] **HW-03 — Donanım bilgisi güncelleme:** Kullanıcı yalnız kendi profilindeki bileşenleri günceller/değiştirir; kilit, tutarlılık ve hatalı girdi durumları test edilir.
- [x] **HW-04 — Birden fazla bilgisayar:** Tek kullanıcı için bağımsız masaüstü/laptop/diğer donanım profilleri; çapraz kullanıcı karışması olmaz.
- [x] **HW-05 — Donanım detayları:** CPU, GPU, RAM, depolama ve diğer izinli teknik özellikler DTO ile detayda döner; veri yoksa uydurulmaz.
- [x] **HW-15 — Donanım profil istatistikleri:** Kullanıcının gerçekten kaydettiği bilgisayar/bileşenlere dayalı sayaç ve anlamlı özetler; başka kullanıcı verisi sızmaz.
- [x] **HW-16 — Donanım profili silme:** Yalnız profil sahibi siler; ilişkili kayıt tutarlılığı ve kişisel veri politikası uygulanır; farklı kullanıcı kaynakları etkilenmez.

**Ana task kabulü:** 7/7 alt görev kanıtlı, güvenlik/DB/API/test raporu tutarlı, zorunlu iki kalite kapısı PASS, dış bağımlılıkları açık. **STOP + tek `feature:` mesajı.**

### [x] ANA TASK 02 — LTZ-ALP-HW-02 — Donanım Model Kataloğu, Arama ve Kampanyalar

**Hedef:** Marka bağımsız katalog modelleri, isteğe bağlı üretici ilişkileri, arama/filtreler ve donanım kampanyaları yönetimi.

- [x] **HW-10 — Donanım ürün kataloğu:** Genel CPU/GPU/RAM/diğer model kayıtları ve varsa opsiyonel üretici/marka seçenekleri; marka seçmeden model çalışır. Model ve üretici tekilliği DB'de doğru korunur.
- [x] **HW-11 — Donanım arama ve filtreleme:** Model, varsa marka/üretici, tür ve teknik nitelik araması; güvenli filtre, sayfalama, stabil sıralama ve indeks doğrulaması.
- [x] **HW-14 — Donanım kampanyaları:** Ürünle ilişkilendirilen kampanya/indirim; fiyat/güncellik/kaynak alanları uydurulmaz. Bu bilgi `content-service` haberine taşınmaz.

**Ana task kabulü:** 3/3 kanıt + iki kalite kapısı PASS. **STOP + tek `feature:` mesajı.**

### [ ] ANA TASK 03 — LTZ-ALP-HW-03 — Donanım Gizliliği, Karşılaştırma ve Servis API'leri

**Hedef:** Alan bazlı görünürlüğü backend uygular; güvenli teknik karşılaştırma ve Performance/Recommendation'ın sözleşmeli veri okuması sağlanır.

- [x] **HW-08 — Donanım gizliliği:** Alan/profil görünürlüğü (`PUBLIC`, `FRIENDS`, `PRIVATE` gibi gerçekten onaylı durumlar) API'de filtrelenir; User Service'in sahip olduğu genel gizlilik tercihleriyle sınır netleşir. Erişilemeyen dış tercihte güvenli şekilde kapalı kalır.
- [x] **HW-09 — Donanım karşılaştırma:** Bilgisayar veya bileşenleri yalnız mevcut teknik değerlerle kıyasla; başkasının gizli verisini açıklama, sahte FPS/benchmark üretme.
- [x] **HW-17 — Donanım API entegrasyonları:** Performance ve Recommendation için güvenli, toplu sorguya uygun ve DTO tabanlı veri sözleşmeleri; gerekiyorsa client/port adapter'ı. Gerçek dış servis doğrulaması yapılmadıysa ayrıca kaydet.

**Ana task kabulü:** 3/3 kanıt + iki kalite kapısı PASS. **STOP + tek `feature:` mesajı.**

### [ ] ANA TASK 04 — LTZ-ALP-HW-04 — Donanım Yorumları ve Moderasyon

**Hedef:** Kullanıcı donanım ürünü deneyimini paylaşır ve uygunsuz yorum raporu ortak Admin Service moderasyon sürecine güvenli biçimde hazırlanır/iletilir.

- [ ] **HW-12 — Donanım yorumları:** Donanım ürünü hakkında kullanıcı değerlendirme ve deneyim içeriği; sahiplik, validation, tekillik, uygunsuz durum/hata kontrolü, güvenli DTO.
- [ ] **HW-13 — Donanım yorumu moderasyonu:** Raporlama, mükerrer rapor koruması, durumlar ve `ContentReported` / ilgili yönetim sözleşmesi; `admin-service` olmayan uç noktalarını varmış gibi çağırma; sözleşme/E2E blokajını kaydet.

**Ana task kabulü:** 2/2 kanıt + iki kalite kapısı PASS; gerçek Admin entegrasyonunun durumu şeffaf. **STOP + tek `feature:` mesajı.**

### [ ] ANA TASK 05 — LTZ-ALP-HW-05 — Masaüstü Donanım Tanıma ve İzinli Senkronizasyon Sözleşmesi

**Hedef:** MVP'de **yalnız backend API**: sonraki masaüstü istemcisinden donanım kabul edecek sözleşme, izinli senkronizasyon kuralları ve güvenilir veri kaynağı ayrımı.

- [ ] **HW-06 — Otomatik donanım tanıma entegrasyonu:** Gelecekteki masaüstü donanım taramasından gelecek payload'u güvenli alıp doğrulayacak/test edecek API sözleşmesi. **Gerçek masaüstü tarayıcı istemci yazma.**
- [ ] **HW-07 — Donanım senkronizasyonu:** Açık onay/izin ve iptal akışı; yalnız izinli profile sync, `MANUAL` / `DESKTOP_SYNC` ayrımı, idempotency/retry, paralel güncelleme ve limitler. Masaüstünden gerçek bağlantı MVP sonrası.

**Ana task kabulü:** 2/2 kanıt + iki kalite kapısı PASS; **son final servis raporu** oluşturuldu; STOP + **beşinci ve son `feature:` commit mesajı**.

### Checklist bütünlüğü / birincil kaynak

- Toplam: **5 ana task = 5 kullanıcı commit'i; 17 alt task (7+3+3+2+2)**.
- Aynı `HW-XX` iki ana task altında işaretlenmez. `LTZ-ALP-HW-0X` kodları değiştirilmez.
- Önceki bir ana taskın checkbox'ları elle silinmez; kanıtı geçersizse nedenini kaydet ve sonucu dürüstçe revize et.
- Bir ana task **`READY_FOR_PDA`** olunca sonraki ana taskın ilk `[ ]` kutusuna bile dokunma; kullanıcı "PDA'yı kapattım, HW-0Y'ye geç" demeden başlamaz.

## 6. Aktif alt görev yürütme çevrimi

Her `HW-XX` için sırasıyla:

1. **Sözleşme/kapsam:** Gerçek PDA açıklaması, servis gereksinimi, mevcut kod ve ilgili `database.md` şemasını çapraz oku. Bağımlılık listesi ve negatif kullanım senaryolarını belirle. Gereksiz mimari tartışmasıyla ilerlemeyi durdurma; onay gerektiren kritik kararı blokaj olarak kaydet.
2. **Tasarla ve kodla:** Gereken entity/enum, DTO/validator, mapper, service/use-case, repository, controller, hata/ProblemDetail, migration, index/constraint ve güvenlik/sahiplik kontrollerini aynı küçük feature diliminde uygula. Gerekmeyen boş sınıf/arayüz yaratma.
3. **Test et:** Happy path + invalid input + anonim/rol + farklı kullanıcı IDOR/BOLA + silme/tutarlılık + uygun olduğunda concurrency, idempotency, pagination, NULL marka/tekillik, özel alan sızıntısı. JUnit, mevcut Spring test düzeni, mümkünse gerçek PostgreSQL + Flyway ile integration; HTTP controller/RestClient mock testleri.
4. **Yerel doğrulama:** Servis modülünün uygun Maven `test` / `verify` komutlarını repo gerçek yapısına göre çalıştır; sayı ve çıkış kodunu raporla. Sadece "compiles" bir alt görevin geçtiği anlamına gelmez.
5. **Doküman:** Her uygulanmış endpoint'i **hemen** `docs/api.md`'ye yaz. Migration/şema değiştiyse `docs/database.md` + `docs/er-diagram.md` güncel olsun. `docs-hardware/progress/LTZ-ALP-HW-0X.md` dosyasına değişen dosyaları, metodları, yapılan testleri, başarısızlıkları, onayları ekle.
6. **Canlı checkpoint:** Ancak tüm alt görev kabul kriterleri doğrulandıysa kök master dosyasında o alt görevi `[x]` yap, `CANLI DURUM` içinde sayıları ve sıradaki adımı güncelle. Sürüm/sonuç belirsizse `[ ]` ve `PARTIAL/BLOCKED/NOT_VERIFIED` kaydı tut. **Aynı ana taskın** bir sonraki alt görevine geç.

**Bir alt görev uygularken oturum kesilirse:** Master `CANLI DURUM` alanında değişmiş dosyalar, çalışan/çalışmayan testler, son hata, bloke karar ve ilk devam komutu açıklansın. Yeni ajanın niyetini değil **kanıtı** koru.

## 7. `docs/api.md` zorunlu kayıt sözleşmesi

`docs/api.md` LTZ'nin tüm servisler için merkezi API sözleşmesidir. Mevcut başlıkları ve diğer ekiplerin API kayıtlarını koru. `hardware-service` altında **uygulanan her endpoint** için en az:

| Alan | Zorunlu kayıt |
|---|---|
| Kimlik | İlgili `HW-XX`, HTTP method + **tam `/api/v1/hardware/...` path**, kısa açıklama |
| Durum | `IMPLEMENTED`, `TESTED`, `BLOCKED`, `PLANNED` veya `NOT_VERIFIED` (gerçeğe uygun) |
| Erişim | Anonymous/protected/internal ayrımı, beklenen rol/scope, kaynak sahipliği ve görünürlük filtresi |
| Girdi | Request record/DTO, path/query/body, Bean Validation, pagination/filter/sort, idempotency varsa anahtar |
| Çıktı | Response record/DTO, anlamlı örnek JSON (yalnız sentetik), veri minimizasyonu |
| HTTP | Gerçekte kullanılan `200/201/204/400/401/403/404/409/422/429/503` vb., ilgili `ProblemDetail` hata kodları |
| Bağımlılık | Auth / User / Recommendation / Performance / Admin sözleşmesi, canlı E2E durumu |
| Kanıt | Test sınıfı/metodu, Flyway dosyası, son doğrulama sonucu |

**Önemli:** Controller olmayan API'yi `IMPLEMENTED` diye yazma; Swagger sözleşmesi olmayan davranışı "otomatik" varsayma. Private endpoint'i yalnız frontend gizledi diye güvenli sayma. Auth yoksa "test edilen adapter" ile "gerçek Auth doğrulanmış" durumlarını ayır.

## 8. İki ayrı zorunlu kalite kapısı — geçmeden commit önerme

**Bu projede kullanıcının açık kararı:** Her **ana task** sonunda **hem pre-push PASS hem SonarQube PASS/A** görülmeden AI **"PDA'da tamamla"** veya **"commit'e hazır"** diyemez. Birinin sonucu diğerinin yerine geçmez. `docs/SECURITY.md`'de SonarQube'un altyapı olarak isteğe bağlı tanımlanması, bu **hardware-service teslim politikasındaki** zorunluluğu ortadan kaldırmaz.

### KAPI A — Pre-push / Maven / JaCoCo / Compose

- Alt görevler sırasında güvenli Maven test/verify komutlarını çalıştırabilirsin. **Final pre-push** komutu repository'nin **mevcut manuel script'i**dir (git hook değildir): Windows için `./pre-push/pre-push.cmd service` veya projedeki geçerli argüman biçimi; backend/full kapsam gerektiriyorsa `./pre-push/pre-push.cmd backend` / `./pre-push/pre-push.cmd full dev` (PowerShell'de uygun `\.\pre-push\pre-push.cmd ...`). Linux/macOS için `sh ./pre-push/pre-push.sh ...`; kesin syntax'ı script ve docs'tan doğrula.
- Bu script'in `.env`/secret içeriğini okumaya veya çıktıda ifşa etmeye çalışma. **Final pre-push kullanıcı tarafından manuel çalıştırılır**; kullanıcı agent'ın komutu manuel tetiklemesine **açıkça izin verirse** ajan da çalıştırabilir. Başarı sonucu, çıktı, tarih, profil/scope ve exit code raporda kanıtlanmalı.
- Backend `clean verify`, ilgili testler, Java 25, JaCoCo **satır kapsamı ≥ %80**, Compose/env şablon/Actuator kontrolleri gibi repo quality gate koşulları uygulanır. `SAFE TO COMMIT` veya `BACKEND TEST CHECK PASSED` sonucu **tek başına Sonar PASS değildir**.
- Script backend modu yalnız backend doğruluyorsa ve kullanıcı full kontrol isterse full'ün sonucu ayrıca kaydedilir; kapsamı açıkça yaz. Başka servis kaynaklı fail de "PASS" sayılmaz; blokajı kaydet.

### KAPI B — SonarQube / Quality Gate

- Repo `sonarqube/sonarqube.md` ve var olan `sonarqube/sonar.cmd|sh` betiklerini incele; parametreleri **uydurma**. Kullanıcı yetkili yerel SonarQube sunucusunda taramayı **manuel** başlatır; açık izin verirse ajan yalnız onaylı tarama komutlarını çalıştırabilir. `SONAR_TOKEN`'ı okuma/loglama/saklama.
- Kapsamı/branch'i raporda açıkça belirt; en az hardware-service'i kapsayan **gerçek Sonar analizi** sonucu gerekir. Kabul: **Security A, Reliability A, Maintainability A ve Quality Gate PASS**. Coverage ve yeni kod şartlarıyla ilgili Sonar kuralı varsa o da PASS olmalı. High/Critical güvenlik bulguları ayrıca incelenir, saklanmaz.
- Sonar erişilemiyorsa, token yoksa veya analiz yapılmadıysa durum **`NOT_RUN/BLOCKED`** olur: **"READY" denemez**. Gerekli aksiyonu kullanıcıya açıkça yaz; başka bir kalite kontrolle Sonar'ın yerine geçme.

### Ana task son kalite checklist'i

- [x] Tüm bu ana taskın alt checkbox'ları kanıta dayalı `[x]`.
- [x] DTO, validation, `ProblemDetail`, JPA/Flyway, transaction, ownership/role, IDOR/gizlilik senaryoları test edildi.
- [x] API + DB + mimari belgeleri ve progress raporu güncel; değişiklikler kapsam dışına taşmadı.
- [x] İlgili testler, gerçek PostgreSQL/Flyway uygun kontrolleri ve JaCoCo ≥ %80 PASS.
- [x] **Pre-push PASS** (komut/scope/zaman/exit code + gerçek çıktı).
- [x] **SonarQube Security A, Reliability A, Maintainability A; Quality Gate PASS** (gerçek analiz sonucu).
- [x] Açık entegrasyonlar `DEFERRED/BLOCKED/NOT_VERIFIED` olarak görünür, güvenlik fail-closed.
- [x] Başkasının işi değiştirilmedi, sırlar okunmadı, Git yazma işlemi veya PDA kapatma yapılmadı.

**Kapılardan biri FAIL/NOT_RUN ise:** Checklist'te başarılı alt görevler `[x]` kalabilir; **ana task `QUALITY_BLOCKED`**, önerilen commit "kullanıma hazır" diye sunulmaz, PDA kapatma çağrısı yapılmaz. Aynı ana task üzerinde kal; yalnız gerekli düzeltme veya kullanıcıya gerekli doğrulama komutlarını ver.

## 9. Ana task teslimi: 1 task = 1 feature commit metni = STOP

Aşağıdaki adımları yalnız aktif ana taskın **tüm alt görevleri ve A+B kalite kapıları** doğrulandıktan sonra uygula:

1. `docs-hardware/reports/LTZ-ALP-HW-0X-delivery.md` dosyasını yaz: PDA kodu, bütün alt görevlerin sonuç/kanıtları, değişen dosyalar, DTO/use-case/controller'lar, API listesi, kullanılan migration'lar, veri ilişkileri, testler, JaCoCo, pre-push, Sonar, auth ve diğer servislerde kalan bağımlılıklar, mimari kararlar, riskler.
2. `docs-hardware/testing/LTZ-ALP-HW-0X-quality.md` ile aynı test/kalite gerçeklerini eşleştir; `docs/api.md` endpoint kaydını son kez kontrol et.
3. Kök master dosyasının `CANLI DURUM / CHECKPOINT` alanında **`READY_FOR_PDA`** (gerekiyorsa `READY_FOR_PDA — DEFERRED_AUTH`) işaretle, ancak ana task `[ ]` kalsın; **PDA'yı kapatabilecek kişi kullanıcıdır**.
4. Kullanıcıya **"LTZ-ALP-HW-0X kapsamındaki alt görevler, pre-push ve SonarQube kalite kapıları doğrulandı. PDA üzerinden bu ana taskı tamamlayabilirsin. Commit/push işlemlerini yalnız sen yapacaksın."** de. `DEFERRED_AUTH` varsa ek olarak "Gerçek Auth entegrasyonu doğrulanmadı; production-ready değildir" uyarısını ver.
5. **Yalnızca bu noktada** önerilen **tek commit metnini** ayrı bir kod bloğunda ve aşağıdaki formatta üret. **İlk satır PDA ana task başlığının aynısı** olsun; bu satır hiçbir koşulda genel/kısaltılmış başlıkla değiştirilmesin:

```text
feature: LTZ-ALP-HW-01 — Bilgisayar Profili Oluştur, Düzenle ve Sil
- [Gerçekte eklenen ilk anlamlı yetenek ve kullanılan ilgili sınıflar/akış]
- [Gerçekte eklenen diğer iş kuralları, validasyon ve sahiplik kontrolleri]
- [Gerçek endpointler ve request/response sözleşmeleri]
- [Gerçek Flyway/DB ve veri bütünlüğü değişiklikleri]
- [Gerçekte eklenen testler, sonuçlar ve kapsama]
- [Gerçekte güncellenen docs/api.md ve ilgili dokümanlar]
- [Varsa açık kalan Auth/diğer servis entegrasyonu: DEFERRED/NOT VERIFIED]
```

   - Alt maddeleri **gerçek diff'e göre doldur**; olmayan endpoint/migration/testi ekleme. Bir ana task için 5–12 **özgül, geçmişte araştırılabilir** madde tercih et; `- CRUD yapıldı` gibi belirsiz ifadeler kullanma.
   - Görev `02`, `03`, `04`, `05` ise ilk satıra **aynı bölümdeki birebir ilgili task kodu ve başlığını** yaz. 5 ana task sonunda 5 ayrı `feature:` mesajı vardır; alt task başlıkları commit başlığı olmaz. Atılmayan commit için SHA/verilmiş hash uydurma.
6. **KESİN STOP.** Diğer ana task için kod/plan/migration/doküman ön uygulaması başlatma; bir sonraki adıma yalnız kullanıcı `HW-0X PDA kapandı / commit-push tarafımdan yapıldı; HW-0Y'ye geç` derse geç. Kullanıcı PDA'yı kapattığını söylediğinde önce mevcut master ana task kutusunu `[x]` yap, sonra onayladığı yeni ana taskı `IN_PROGRESS` yap.

### Kullanıcı hiç bitirmeden yeni oturum açarsa

- `READY_FOR_PDA` durumunda yeniden yazılım geliştirmeye başlama; yalnız raporu, commit mesajını ve kullanıcı aksiyonunu hatırlat.
- `QUALITY_BLOCKED` durumunda başarısız/eksik kapıya odaklan; test kanıtını güncelle.
- `IN_PROGRESS` durumunda checkpoint'ten aktif **alt taskta** devam et.

## 10. Beşinci task bittikten sonra servis genel MD raporu

`LTZ-ALP-HW-05` kapsamı ve iki kalite kapısı doğrulandığında **5. commit metni verilmeden hemen önce** şu raporu oluştur:

`docs-hardware/reports/HARDWARE_SERVICE_FINAL_REPORT.md`

Bu rapor **gerçekte uygulanmış tüm donanım servisi API'lerinin ve tüm açık kalanların toplu hizmet envanteri**dir. `docs/api.md` ayrıntılı, yaşayan API kataloğudur; bu rapor son teslim snapshot'ıdır. En az şu başlıklar bulunur:

1. **Özet:** Hardware Service hedefi, tasarım sınırları, sahibi, sürüm/kapsam; `hardware_db`, `/api/v1/hardware/**`, kod konumu.
2. **PDA kapsam matrisi:** **5 ana task / 17 alt task**; her biri `DONE`, `DEFERRED`, `BLOCKED`, `NOT_VERIFIED` ve somut kanıt/rapor bağlantısı.
3. **Eksiksiz API envanteri:** **Her yazılmış endpoint** için method, tam path, ilgili HW-XX, erişim tipi, owner/role/privacy, request/response, ana HTTP durumları, test kanıtı, üretim/E2E hazır olma durumu. Hiç yazılmamış/planlanan API'leri ayrı **"Henüz uygulanmayanlar"** kısmında açıkça belirt.
4. **Veri modeli:** tablolar, servis içi FK/mantıksal dış REF, unique/index/check, aktif Flyway `V1–V10` dosyaları ve amaçları, migration sorunu/bekleyen karar.
5. **Güvenlik:** access token doğrulaması, actual Auth integration, userId kaynağı, RBAC, IDOR/BOLA, gizlilik, CSRF/cookie ve bilinen eksikler.
6. **Entegrasyon matrisi:** `auth-service`, `user-service`, `game-service`, `performance-service`, `recommendation-service`, `admin-service` için contract/port/adapter/mock/E2E ayrı durum alanları ve sonraki aksiyon.
7. **Test ve kalite:** JUnit/MockMvc/DB test özeti, JaCoCo ölçümü, pre-push gerçek PASS kanıtları, Sonar Security/Reliability/Maintainability A ve Quality Gate kanıtı; koşul sağlanmadıysa dürüstçe bloke kalır.
8. **Servis kullanım rehberi:** yerel çalıştırma/DB migration/test komutları **gerçek repo komutlarıyla**, API dokümanı ve rapor linkleri; hiçbir gizli ENV değeri yazılmaz.
9. **Açık kalanlar / riskler:** tüm `DEFERRED`, `BLOCKED`, `NOT_VERIFIED` kalemleri, öncelikleri, sahipleri, bağımlılıkları ve doğrulama checklist'i; MVP sonrası masaüstü gerçek sync ve canlı Auth bağlantısı net ayrılır.
10. **Değişiklik izi:** 5 feature/teslim raporu ve önerilmiş commit başlıkları. Kullanıcı doğrulamadan git commit SHA veya PDA kapanışını **uydurma**.

Raporu `docs/api.md`, kök checklist, `docs/database.md`, `docs/er-diagram.md` ve 5 teslim raporu ile tutarlılık kontrolünden geçir. Sonra **HW-05'in tek feature commit metnini** ver ve **STOP**. Auth veya Desktop gerçek bağlantısı gelmedi diye bunları yapılmış gösterme.

## 11. Yanıt formatın (kısa ama doğrulanabilir)

Her geliştirme turunun bitiminde kullanıcıya şu düzenle rapor ver:

```text
AKTİF ANA TASK: LTZ-ALP-HW-0X — <birebir başlık>
İLERLEME: X/Y alt task | X/17 toplam alt task | ana task durumu
CHECKLIST: aktif ana taskın TÜM HW-XX maddeleri [x]/[ ]
YAPILANLAR: endpoint + DTO/service/repository + migration + değiştirilmiş dosyalar
TEST KANITI: gerçek komut, PASS/FAIL, test sayısı, JaCoCo oranı
KALİTE KAPILARI: PRE-PUSH PASS/FAIL/NOT_RUN; SONAR A + QUALITY GATE PASS/FAIL/NOT_RUN
AÇIK BAĞIMLILIKLAR: Auth, User, Admin, Performance, Recommendation, kullanıcı onayları
KALINAN NOKTA: kök master checkpoint'te kaydedilen tek kesin sonraki adım
COMMIT MESAJI: yalnız her iki kalite kapısı geçip ana task READY_FOR_PDA olduğunda
SONUÇ: IN_PROGRESS / APPROVAL_BLOCKED / QUALITY_BLOCKED / READY_FOR_PDA + STOP
```

Yalnız aktif taskın alt görevleri bitmediğinde ya da kapılar geçmediğinde **"PDA'da tamamla" deme ve commit mesajı sunma**. Kullanıcı istediğinde bir **taslak** commit metni sadece taslak/henüz commit'e hazır değil ibaresiyle hazırlanabilir, ancak başarı iddiasında bulunma.

## 12. CANLI DURUM / CHECKPOINT — AJANIN SÜREKLİ GÜNCELLEYECEĞİ ALAN

> **TEK GÜNCELLEME BÖLGESİ:** Masterın `0–11` arası bağlayıcı kuralları, görev kodlarını ve başlıklarını silme/değiştirme. Buradaki alanları, görev checklist'indeki checkbox'ları ve gerekirse yanına kısa kanıt bağlantılarını güncelle. Tarihi **gerçek oturum tarihine göre** yaz; uydurma test sonucu ekleme.

| Alan | Başlangıç durumu |
|---|---|
| Aktif ana task | `LTZ-ALP-HW-03` — kullanıcı geçişi onayladı; HW-02 PDA kapanışı bildirildi |
| Aktif alt task | `HW-08/HW-09/HW-17` — 3/3 yerel kapsam ve iki kalite kapısı doğrulandı |
| Ana task aşaması | `READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT` — 3/3; [teslim](docs-hardware/reports/LTZ-ALP-HW-03-delivery.md), [kalite](docs-hardware/testing/LTZ-ALP-HW-03-quality.md); ana kutu kullanıcı kapanışını bekler |
| 01 | `DONE — DEFERRED_AUTH` — 7/7; kullanıcı kapanış bildirimiyle ana task `[x]` |
| 02 | `DONE — DEFERRED_AUTH` — 3/3; PDA kapanışı kullanıcı tarafından bildirildi, commit 6756210 doğrulandı |
| 03 | `READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT` — 3/3; full dev SAFE TO COMMIT + Sonar A/A/A/gate PASS; ana kutu `[ ]` |
| 04 | `NOT_STARTED` — 0/2 |
| 05 | `NOT_STARTED` — 0/2 |
| Toplam doğrulanan alt görev | `13/17` |
| Son doğrulanan alt görev | `HW-17` — 14 yeni batch senaryosu; modül toplamı 174 PASS; 2026-10-21 06:10:20 +03 |
| Son değiştirilen dosyalar | HW-03: 22 yeni production Java, 5 mevcut Java, V6/V7 ve 3 yeni integration test sınıfı; docs/api.md HARDWARE, DB/ER/mimari, docs-hardware. Harici beş pre-push dosyası korunuyor |
| Son Flyway | V6 privacy + V7 constraint/index ad standardı; V1–V7 PostgreSQL/Flyway/JPA PASS (192 backend testi); önceki migration'lar değiştirilmedi; V6/V7 canlı DB'ye uygulanmadı |
| Son endpoint / docs/api.md değişikliği | 3 privacy/shared + 3 comparison + 1 batch; toplam 31 iş endpoint'i; [API kataloğu](docs/api.md#hardware) |
| Son Maven/test komutu ve sonuç | Final full dev ve scan dev clean verify: 18 modül, 192 PASS; hardware 174 PASS; failure/error/skipped 0; 2026-10-21 06:27:35 +03; [kalite kaydı](docs-hardware/testing/LTZ-ALP-HW-03-quality.md) |
| JaCoCo | Hardware 884/898=%98,44; tüm backend ağırlıklı satır kapsamı %94,94; exclusion yok |
| Pre-push | Final full dev PASS / exit 0: 192 test, %94,94, 18/18 HTTP, SAFE TO COMMIT; log kapanışı 2026-10-21 06:23:51 +03; V7 ve Sonar düzeltmeleri dahil |
| Sonar / Quality Gate | Final scan dev PASS / exit 0; A/A/A, Quality Gate OK, overall %91,2 / new %93,7 / duplication %0; new issue/açık issue/hotspot 0; analiz ad6e17fe-a77a-4269-92b7-9a64fda8e8c9; 06:27:35 +03 |
| Auth bağlantısı | `DEFERRED_AUTH — gerçek auth-service/JWT sözleşmesi henüz yok` |
| Diğer servis entegrasyonları | User/Social gerçek policy ve Performance/Recommendation consumer E2E: `DEFERRED_CONTRACT / NOT_VERIFIED`, açık onaylı |
| Açık kullanıcı onayları | HW-03 erişim sınırı, güvenli erteleme ve tüm servisleri kapsayan pre-push full dev / Sonar scan dev açıkça onaylı |
| Güncel engel | Yerel kapsam/kalite engeli yok. Gerçek Auth ve dış sözleşme/E2E açık onaylı ertelenmiş; production-ready değildir |
| Bir sonraki kesin adım | Ayrıntılı tek feature mesajını kullanıcıya ver ve STOP. Kullanıcı PDA kapanışını bildirene ve HW-04'e açık izin verene kadar geliştirmeye devam etme |
| Son güncelleme (gerçek) | `2026-10-21` |

### CANLI KARAR / BLOKAJ DEFTERİ

- `VERIFIED / READY 2026-10-21 / HW-03`: HW-08/HW-09/HW-17 3/3; 40 yeni senaryo, hardware 174 PASS/%98,44; full dev 192 PASS/%94,94 ve 18 HTTP PASS, SAFE TO COMMIT; Sonar A/A/A + gate PASS, açık issue/hotspot 0. [Teslim](docs-hardware/reports/LTZ-ALP-HW-03-delivery.md). İlk DB kapalı koşusu ve üç MINOR düzeltmesi kalite geçmişinde korunur. Ana kutu `[ ]`, PDA kapanışı ve sonraki task izni beklenir. Git yazma işlemi yok.

- `APPROVED 2026-10-21 / HW-03`: Kullanıcı privacy/karşılaştırma/batch erişim sınırını ve User/Social gerçek bağlantısı ile Performance/Recommendation tüketici E2E ertelemesini onayladı. Üretim paylaşımı deny; DEFERRED_CONTRACT / NOT_VERIFIED görünür kalır. Tüm backend için pre-push full dev ve Sonar scan dev çalıştırılacak.

- `OPEN`: Auth Service ile access-token JWT claim, issuer, audience, anahtar dağıtımı/JWKS, rol biçimi, account status/revocation ve HttpOnly cookie/CSRF protokolü mutabakatı. **Gerçek entegrasyon ileride**; korumalı API'ler fail-closed.
- `APPROVED 2026-10-21`: Owner/protected profil uçları, fail-closed üretim, test-only actor ve CSRF'nin korunması kullanıcı tarafından onaylandı; WebMvc test/Testcontainers bağımlılıkları onaylandı. Yeni public iş endpoint'i, ENV veya Compose sözleşmesi eklenmedi. [Karar kaydı](docs-hardware/decisions/open-decisions.md).
- `VERIFIED 2026-10-21`: Final pre-push PASS (58 test, %96,69), Sonar A/A/A + Quality Gate PASS (76 backend testi, %84,3 overall, %0 duplication). [Son kalite kanıtı](docs-hardware/testing/LTZ-ALP-HW-01-quality.md). Sonraki ana taskta yeniden doğrulanır.
- `CLOSED 2026-10-21`: Kullanıcı PDA üzerinden LTZ-ALP-HW-01 kapanışını bildirdi; ana task `[x]`. Kullanıcının `fc23563` commit'inin kapsamı test edilen sürümle aynı doğrulandı. Gerçek Auth `DEFERRED_AUTH`; sonraki ana task için izin yok. [Kapanış kaydı](docs-hardware/progress/LTZ-ALP-HW-01.md#pda-kapanışı--2026-10-21).
- `STARTED 2026-10-21`: Kullanıcı LTZ-ALP-HW-02'ye geçişi onayladı. HW-10 ile başlanır; HW-11 ve HW-14 sırayla uygulanır. Yeni izin/kampanya sözleşmesi onay bekliyor; final kalite kapıları henüz çalıştırılmadı.
- `APPROVED 2026-10-21 / HW-02`: Typed actor + hardware:catalog:write yönetim sınırı ve yerel kaynak/gözlem/fiyat kampanya sözleşmesi kullanıcı tarafından onaylandı. Yeni Auth rol/claim veya dış sağlayıcı tanımlanmadı.
- `VERIFIED 2026-10-21 / HW-02`: 3/3 alt görev, pre-push 134 test/%98,02 PASS; Sonar A/A/A + Quality Gate PASS, yeni issue 0, açık issue/hotspot 0. [Teslim](docs-hardware/reports/LTZ-ALP-HW-02-delivery.md). Ana task `[ ]`; sonraki task izni yok.
- `CLOSED / STARTED 2026-10-21`: Kullanıcı HW-02 PDA kapanışını bildirdi ve HW-03'e geçilmesini istedi. HW-02 `[x]`, HW-03 IN_PROGRESS / 0/3. Kullanıcının 6756210 commit'i doğrulandı; Git yazma işlemi ajan tarafından yapılmadı.

### CHECKPOINT GÜNCELLEME ÖRNEĞİ (GERÇEK SONUCA GÖRE DOLDUR)

```text
Aktif: LTZ-ALP-HW-01 / HW-02
Son tamamlanan: HW-01 [x]
Değişen dosyalar: <kesin yollar>
Migration: <Vn__...sql veya YOK>
Endpoint: <METHOD /api/v1/hardware/... veya YOK>
Test: <komut>; <N PASS / M FAIL>; kapsam <yüzde>
Pre-push: NOT_RUN (final gate)
Sonar: NOT_RUN (final gate)
Blokaj: <karar / AUTH CONTRACT>
Sıradaki adım: <HW-02'de ilk uygulanacak kesin dosya/iş>
```

**ŞİMDİKİ İLK TALİMAT:** Önce `AGENTS.md`, `CLAUDE.md`, `LTZ_HARDWARE_SERVICE.md` ve bağlayıcı teknik belgeleri oku; repo durumunu doğrula. `docs-hardware/` rapor altyapısını yalnız gerekli dosyalarla kur. **Yalnız `LTZ-ALP-HW-01` ana taskında `HW-01` ile başla.** Kullanıcı onayı gerektiren büyük karar dışında ilerlemeyi sürdür. Ana task/kalite tamamlanınca PDA'ya kapatma önerisi + birebir başlıklı tek `feature:` mesajı ver ve **DUR**.
