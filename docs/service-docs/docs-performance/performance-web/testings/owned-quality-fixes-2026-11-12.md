# Hardware / Performance kalite düzeltmeleri

Tarih: 2026-11-12. Kullanıcı kapsamı yalnız `hardware-service` ve `performance-service` olarak daralttı. Auth/Mail/Community/Messaging veya ortak kalite altyapısına bu turda yapılan geçici değişiklikler geri alındı; main ile önceden gelmiş staged değişiklikler korundu. Commit/push veya merge tamamlama komutu çalıştırılmadı. Bu kayıt devam eden, henüz commit edilmemiş main birleştirmesinin üstündeki çalışmaya aittir.

## Başlangıç kanıtı ve kapsam

Sonar CE `48a42505-ec06-485d-a6f0-a644ccb6f187`: toplam 308 açık issue; yeni kod döneminde 304 issue. Gerçek kapının başarısız koşulu `new_violations=304`; coverage ve yeni duplication koşulları OK. Yeni kod dönemi PREVIOUS_VERSION / 2026-10-20 başlangıçlıdır; dört conflict dosyasının tek başına ürettiği borç değildir.

Bu analizde Hardware'a ait **71 issue**, Performance'a ait **0 issue** vardı. Global 10 BUG + 3 VULNERABILITY listesinden Hardware kapsamındaki 3 null akışı ve 2 SQL bulgusu bu çalışmaya dahildir. Auth cookie ipucunun güvenlik değerlendirmesi ve diğer servislerin açık bulguları kapsam dışıdır. Gerçek ENV/token içerikleri açılmadı veya değiştirilmedi; Sonar/pre-push betiklerinin önceden onaylanmış alt süreç kullanımı korundu.

## Yapılanlar

- Hardware katalog kaynak sorgularında dinamik placeholder birleştirmesi yerine sabit SQL + `NamedParameterJdbcTemplate` parametreleri; kimlikler sorguya değer olarak bağlanır.
- Cihaz JWT doğrulamasında audience ve token ID bir kez okunur, null/boş değerler fail-closed reddedilir; mevcut güven modeli ve kapsamlar değişmez.
- PC Part alan ve JSON anahtarları tekil sabitlere alındı; sayısal form faktörü null kontrolü eklendi. Kaynak `form_factor` ve çıktı `formFactor` anahtarları ayrı tutulur.
- Katalog aramasındaki kullanılmayan overload'lar kaldırıldı; seçim ve teknik koşullar ayrı iç record'larda toplanır. API filtre sözleşmesi ve query davranışı korunur. İki transactional list overload'u ortak private uygulamayı çağırır; proxy sınırındaki transaction ayarları korunur.
- Icecat grup/özellik/görsel ayrıştırması sınırları korunarak küçük metotlara ayrıldı; GTIN deseni önceden derlenir. XML indeks sayfalamasında tekrar eden `continue` akışı sadeleştirildi. Birim dönüşümü ve bilinmeyen değerlerin yokluğu korunur.
- Dataset satır okuma ve import transaction batch'i ayrıldı; karantina/rollback davranışı değişmez. Uzun ternary, kullanılmayan import/catch değişkenleri ve test lambda uyarıları temizlendi.
- HTTP gövde limiti sayaç artışı ve byte okuma açık adımlara ayrıldı. Yetkilendirme kararları küçük metotlarda toplanır; cookie/CSRF/rol/scope veya endpoint modelinde değişiklik yoktur.
- Import CLI logger kullanır. Yalnız Hardware'a ait `ops/hardware-catalog-import` özet filtresi, izinli CLI logger önekini kaldırarak eski makine özeti biçimini korur; başka logger veya ham ayrıntı çıktıdan geçirilmez.
- Icecat parser ve specification mapping testleri eklendi: hatalı/demo kaynak, kimlik/taksonomi uyuşmazlığı, zorunlu metin, grup/özellik limitleri, bilinmeyen alanlar, desteklenen birimler, aralık sınırları ve ilk gözlemin korunması.

Endpoint, migration, ENV, bağımlılık veya kalite eşiği değiştirilmedi. Performance'ta açık issue yoktu; son tekrar oranı için yapılan JWT metot ayrıştırması dışında uygulama akışı değiştirilmedi.

İlk Sonar tekrarında Hardware'ın önceki 71 issue'su kapandı; 2 yeni S2629 logger değerlendirme uyarısı çıktı. Hardware A/A/A, bug/vulnerability 0/0, Sonar coverage %90,4, duplication %1,7. Performance A/A/A ve issue 0; Sonar coverage %92,8, duplication %3,2. Global toplam 239 issue'ya düştü; global B/C/A, coverage %91,7 ve duplication %3,2 nedeniyle kapı FAIL kaldı. Kanıt: CE `6c1b6cda-b271-4c5e-b6a5-25502e3b0be1`, `quality-fixes-sonar-first-analysis.json`, `quality-fixes-sonar.log`.

Son iki nokta için Hardware logger argümanları lazy supplier olarak değerlendirilir. Performance JWT shape doğrulaması bağlam, zaman ve servis kimliği koşullarına ayrıldı: kullanıcı UUID kuralı, servis tek audience/jti kuralı, 60 sn ömür ve 30 sn tolerans aynen korunur. Bu, Hardware ile kopyalanmış uzun doğrulama bloğunu azaltır; yeni ortak modül veya güvenlik modeli kurulmaz. Kullanıcı/servis ayrımı, eksik claim, lifetime/skew ve raw-claim dönüşümü için Performance regresyonları eklendi. Nihai pre-push/Sonar tekrarı tamamlandı; sonuç aşağıdadır.

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| İzole Hardware Maven testleri | 581 PASS, failure/error/skip 0 |
| İzole Performance Maven testleri | 138 PASS, failure/error/skip 0 |
| Hardware merkezi coverage | %93,59 toplam; değişen satırlar 809/940 = %86,06, PASS |
| Performance merkezi coverage | %97,10, PASS; değişen production Java yok |
| İki modül Maven build | PASS |
| İki modül Checkstyle / PMD | 0 yeni ihlal, PASS |
| İki modül runtime Trivy | HIGH/CRITICAL 0, PASS |
| Hardware import Node regresyonu | 10/10 PASS |
| Gerçek `pre-push.cmd hardware-service dev` — son logger düzeltmesi dahil | BACKEND TEST CHECK PASSED, exit 0; 581 test / %93,59 |
| Gerçek `pre-push.cmd performance-service dev` — son JWT ayrıştırması dahil | BACKEND TEST CHECK PASSED, exit 0; 148 test / %97,18 |
| Gerçek Sonar tekrarı | İki servis ölçümleri hedefte, issue 0; global proje Quality Gate FAIL, exit 1; aşağıdaki kapsam ayrımına bakın |
| Global Gitleaks worktree | FINDING; Auth fixture satırları kapsam dışında, kural gevşetilmedi |
| Değişen/yeni Hardware/Performance kaynakları ve import betikleri Gitleaks | Son koşu 30 dosya, PASS; untracked üç yeni Java testi de dahil |

İzole runner'ın gerçek Maven/test/coverage/quality/dependency akışı kullanıldı. Windows'ta Docker CLI eklenti keşfi için yalnız Docker alt sürecine mevcut APPDATA/LOCALAPPDATA/ProgramData/ProgramFiles dizin yolları aktarıldı. Mockito self-attach sorunu için mevcut 5.23.0 test ajanı ASCII geçici dizinden Maven test alt süreçlerine başlangıçta yüklendi; production ayarı veya POM değiştirilmedi. Geçici PostgreSQL sentetik kimlikle ve tmpfs ile kuruldu, runner tarafından kapatıldı; DEV DB veya volume reseti yapılmadı.

İlk denemelerin Docker eklenti keşfi, sabit ad çakışması, Mockito ve değişen satır coverage hataları PASS sayılmadı. Son başarılı izole log: `test-results/performance-web/quality-fixes-owned-backend-final.log`. Önceki başarısız denemeler aynı klasörde ayrı loglardadır. Production import veya sağlayıcı HTTP çağrısı çalıştırılmadı.

## Kapsam dışındaki kapılar

Auth test fixture'ları ve global Secret Check mantığı kullanıcı talimatıyla değiştirilmedi. Bu nedenle genel pre-push'un Secret Check satırı yeşil kabul edilmez. Servis bazlı pre-push başarısı, `SAFE TO COMMIT` veya global Sonar Quality Gate başarısı anlamına gelmez.

Toplam duplication %3,2'nin önemli kısmı başka servislerin güvenlik/istemci kodlarındaki kopyalardır. Başka servise kod taşıma, yeni ortak Maven modülü, exclusion veya Sonar dönem/kalite kapısı değişikliği yapılmadı.

## Nihai Sonar ve kapanış

12 Kasım 2026 13:16 TSİ: gerçek analiz sunucuda işlendi. CE task **`2a57074b-ae96-4f06-9da9-95b3eeca5168`**, SCM revision `22ef905df00239942e48fff627178f6ab5f56b0e`; analiz henüz commit edilmemiş birleşmiş kaynakları kapsar. Sunucudaki bütün açık issue sayfaları alınıp iki servis dosya yolu ile filtrelendi; ilk 50 bulguyla sınırlı bir örneklem değildir.

| Servis | Security / Reliability / Maintainability | BUG | VULNERABILITY | CODE_SMELL | Sonar coverage | Duplication |
| --- | --- | --- | --- | --- | --- | --- |
| hardware-service | **A / A / A** | **0** | **0** | **0** | **%90,4** | **%1,4** |
| performance-service | **A / A / A** | **0** | **0** | **0** | **%93,6** | **%2,7** |

Hardware'ın başlangıçtaki 71 issue'su ve ara analizdeki 2 logger uyarısı kapandı. Performance duplication %3,2 → %2,7; issue 0. Sonar coverage, önceki tablodaki JaCoCo LINE yüzdesiyle aynı metrik değildir; iki ölçüm de ayrı raporlanır ve hedefin üstündedir. Tek bir backend projesi olduğundan servis başına ayrı sunucu Quality Gate'i varmış gibi PASS iddia edilmez: burada doğrulananlar gerçek servis metrikleri ve sıfır açık issue'dur.

Sonar runner'ın taze tam backend test turu: **4369 test, 0 failure/error/skip**, Maven build ve merkezi coverage PASS; toplam LINE **%95,52**. Son scanner raporu yüklendi/işlendi; global gate başarısız olduğu için Maven scanner ve runner exit 1 döndü. Yeni kod koşulları: coverage %91,9 OK, duplication %1,64418 OK, **233 yeni issue ERROR**. Global toplam **237 açık issue'nun tamamı iki servis kapsamı dışında**. Global B/C/A, Sonar coverage %91,8, duplication %3,2; genel hedefler FAIL.

Kanıtlar:

- `test-results/performance-web/quality-fixes-sonar-after.json`: son sunucu servis metrikleri ve boş issue listeleri, UTC 10:16:43.
- `test-results/performance-web/quality-fixes-sonar-gate.json`: gerçek global gate koşulları ve tam test raporu.
- `test-results/performance-web/quality-fixes-sonar-final.log` ve `logs/sonarqube-owned-quality-2026-11-12T09-55-00-897Z-59488-20e4b922.log`: gerçek runner çıktısı.
- `test-results/performance-web/quality-fixes-pre-push-{hardware,performance}-final.log`: son servis pre-push exit 0 sonuçları.
- `test-results/performance-web/quality-fixes-owned-secrets-final.log`: kapsamdaki 30 dosya Gitleaks PASS.

**Kullanıcının verdiği Hardware/Performance kalite kapsamı tamamlandı. Global merge hazırlığı NOT READY**: başka servislerin Sonar borcu, Auth fixture secret bulguları ve PWEB-08/09'un Recommendation sözleşmesi eksikliği çözülmüş sayılmaz. Bu kapsamda yeni servis entegrasyonu yazılmadı. Kullanıcıya entegrasyon eksikleri yalnız mevcut sözleşmeler/kod üzerinden açıklandı. Commit/push/PR/merge yapılmadı; kullanıcı merge commit'ini kendisi tamamlayacaktır.

Son Git denetimi PASS: unmerged kayıt yok, index/worktree whitespace exit 0, beklenmeyen kapsam dışı source diff'i yok. HEAD `22ef905d`, MERGE_HEAD = origin/main `d2f31fe9`; merge commit'i hâlâ bekliyor. Üç yeni Java testi ve bu kalite raporu stage edilmedi. Kanıt: `test-results/performance-web/quality-fixes-final-git.json`. [PR başlığı/açıklaması](../reports/pr-description.md) ve master checklist güncel sonuçlarla yenilendi.
