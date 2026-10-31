# LTZ-HAM-LIB-04 — Library Service Faz 4 sonucu

Tarih: 30 Ekim 2026. Sorumlu: Hamza.
Kapsam: LIB-10 → LIB-11 → LIB-14 → LIB-18.

**Library tarafındaki hazırlık ve otomatik testler tamamlandı; gerçek entegrasyonlar PENDING.**
Faz 4 kabul ilerlemesi: **1/4**. Genel ilerleme: **14/18**.
LIB-10/11/14 tamamlandı işaretlenmedi. LIB-18'in otomatik güvenlik/veri bütünlüğü
testleri tamamlandı; bu, gerçek karşı servis E2E doğrulaması anlamına gelmez.

## LIB-10 — Profil sunumu: kısmi

Kullanıcı JWT'siyle erişilen iki Library yolu eklendi:

- GET /api/v1/library/users/{userId}/entries.
- GET /api/v1/library/users/{userId}/entries/statistics.

Görüntüleyen yalnız doğrulanmış JWT sub'dan alınır. Owner kendi koleksiyonunu okuyabilir.
Non-owner erişiminde ADMIN dahil her kanonik rol için açık görünürlük kararı zorunludur.
Anonim erişim sunulmaz. Query/header başka görüntüleyen seçemez.

User servisinin gelecekteki tüketimi için iç yollar hazırlandı:

- GET /internal/v1/library/users/{userId}/entries.
- GET /internal/v1/library/users/{userId}/entries/statistics.

Servis JWT'si mevcut RSA/JWKS/issuer/tek audience/tür/zaman doğrulayıcısından geçer;
ek olarak scope=library:profile:read, sub=svc:user-service, imzalı target_user_id
yol eşitliği ve kanonik viewer_id zorunludur. Owner eşitliği yalnız doğrulanmış
görüntüleyen bağlamında değerlendirilir. D4 account:erase yetkisi okuma yetkisi değildir.

Liste mevcut oyun özeti/metadata fallback DTO'sunu, sayaç mevcut istatistik DTO'sunu
kullanır. Sayfalama 0/20 varsayılan, page>=0 ve size=1..50; sıra addedAt DESC/id DESC.
Yeni başarılı yanıtlar Cache-Control: no-store taşır. Liste ve sayaç ayrı isteklerdir.

**Eksik:** Gerçek User tüketici adaptörü, görünürlük API'si ve Auth delegasyonu yoktur.

## LIB-11 — Sunucu taraflı gizlilik: kısmi

LibraryVisibilityPort, PROFILE ve RECOMMENDATION amaçları için ALLOW/DENY/UNAVAILABLE
kararı tanımlar. LibraryAccessService kararı bütün paylaşılan okumaların önünde uygular.
DB liste/toplam/sayaç sorgusu ve Game çağrısı izin kontrolünden sonra başlar.

- ALLOW: ilgili amaç ve doğrulanmış bağlam için okumaya izin verir.
- DENY: 403 ve genel ProblemDetail; koleksiyon/sayaç dönmez.
- UNAVAILABLE, null veya port istisnası: 503; özel hata detayı/yerel veri dönmez.

Üretimde tek adaptör UnavailableLibraryVisibility'dir ve her zaman UNAVAILABLE döner.
İzin veren üretim modu, ENV anahtarı veya varsayılan PUBLIC davranışı eklenmedi.
User'ın bulunmayan API'sine uydurma bir HTTP çağrısı yapılmaz. Tercihler Library DB'sine
kopyalanmaz. Karar cache'lenmez; her non-owner okuma ve her Recommendation isteği yeniden
karar gerektirir. Owner'ın mevcut kişisel uçları ve D4 hesap silme akışı korunur.

User gereksinimi bütün profilin gizlenmesini değil oyun geçmişi gibi alanların
görünürlüğünü tanımlar. Bu çalışmada profilin tamamına yeni gizlilik ayarı eklenmedi.

**Eksik:** Gerçek PRIVATE/PUBLIC/FRIENDS kararı ve varsa arkadaşlık/engelleme doğrulaması.
Üretimde non-owner profil erişimi şu anda 503 ile kapalıdır.

## LIB-14 — Recommendation veri sınırı: kısmi

GET /internal/v1/library/users/{userId}/recommendation-signals eklendi.

scope=library:recommendation:read, sub=svc:recommendation-service ve imzalı
target_user_id eşitliği gerekir. Ardından RECOMMENDATION amacıyla ayrı izin aranır.
Owner eşitliği bu izni atlatmaz; PUBLIC profil tercihi öneri kullanım izni sayılmaz.

DTO yalnız items[{gameId,favorite,completed}], page, size ve totalElements içerir.
Yerel kayıt id'si, kullanıcı id'si, tarihler/version ve oyun metadata'sı dönmez.
favorite OR completed satırları seçilir; bir oyun iki bayrağı taşısa da bir kez döner.
İşaretsiz kayıtlar ve diğer owner verileri dışlanır. Liste/toplam aynı REPEATABLE_READ
transaction'ında okunur; deterministik addedAt DESC/id DESC ve long offset kullanılır.
Game veya başka servisin DB'sine erişim yoktur.

**Eksik:** Auth istemci/target/scope desteği, öneri kullanımının izin dayanağı ve gerçek
Recommendation tüketici adaptörü. Üretimde izin adaptörü 503 ile erişimi kapatır.

## LIB-18 — Otomatik test teslimi: tamamlandı

PrivacyTests, mevcut LibraryApiTest içinde yeni bir nested test grubu olarak eklendi.
Testler gerçek PostgreSQL, V41/Flyway/Hibernate validate ve gerçek RSA imzası/JWKS ile
çalışır. User görünürlük portu test içinde mock'tur; izinli cevaplar gerçek User kararı değildir.
Yerel test JWT'leri gerçek Auth token edinme akışının çalıştığını kanıtlamaz.

Eklenen senaryolar:

- Owner okuması, non-owner izin/ret, ADMIN'in gizliliği atlayamaması.
- Gizli/dolu ve bilinmeyen/boş hedefte aynı ret ayrıntısı; sayaç veya kayıt sızmaması.
- UNAVAILABLE/null/istisnada DB/Game çağrısı olmadan 503.
- Query viewerId'nin kimliği değiştirememesi; izinli okumada yalnız hedef owner'ın verisi.
- Anonim/rolsüz kullanıcı, kullanıcı token'ının iç uçta ve servis token'ının kullanıcı
  ucunda reddi; D4 kapsamının okuma için yetersizliği.
- Yanlış svc sub/scope/hedef, eksik veya kanonik olmayan viewer; yanlış issuer/audience,
  eksik jti ve aşırı token ömrünün reddi.
- İzinli profil iç liste/sayaç yanıtları, bütün yeni sayfalı yolların parametre sınırları.
- Recommendation alan azaltma, OR filtresi, iki sayfa, boş sonuç/yüksek offset,
  başka owner dışlama, izin iptali ve ulaşılamama sonrası eski iznin kullanılmaması.
- 32 eşzamanlı HTTP ekle/favori/bitirildi/sil işlemi; kullanıcı/oyun tekilliği,
  son silme ve diğer owner verisinin korunması.
- LibraryAccessServiceTest gerçek üretim adaptörünün non-owner ve Recommendation
  erişimine izin vermediğini, yalnız kişisel profil okumasının korunmasını doğrular.

Faz 1/2/3 testleri aynı koşuda geçti; mevcut gerçek UNIQUE constraint ve eşzamanlı
upsert/işaretleme testleri korundu. Gerçek karşı servis entegrasyonu halen PENDING.

## Maven kanıtı

Son `clean verify`: **90 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**.
JaCoCo LINE: **402 covered / 4 missed, %99,01**.
Merkezi coverage kapısı PASS; değişen satırlar **308/309, %99,68**.
Checkstyle/PMD raporlarında **0 ihlal**. git diff --check başarılı.
İlk koşuda test beklentisinin Türkçe karakter kodlaması hatası düzeltildi; son koşu başarılıdır.

```powershell
mvn -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml `
  -f backend/pom.xml -pl services/library-service -Pci `
  '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5542/library-phase1-test' `
  '-Dspring.datasource.username=ltz_library_phase1_test' clean verify
```

Host Maven, ltz-dev PostgreSQL içindeki ayrı Library test DB/rolüne bağlandı; CI
profilindeki sentetik test parolası kullanıldı. library_db test temizliğine dahil edilmedi.
Test DB/rolü sahiplik doğrulaması ardından kaldırıldı; kalan DB/rol sayıları 0.
Tekrar koşmak için izole DB/rol kurulmalıdır.
Ham çıktılar Library target/verification altındadır; docs-library içine log eklenmedi.
Pre-push ve SonarQube çalıştırılmadı.

## ltz-dev doğrulaması

Standart Library Dockerfile ile ltz/library-service:dev derlendi; yalnız Library,
`docker compose -p ltz-dev up -d --no-deps --no-build library-service` ile yenilendi.
Çalışan konteynerin image ID'si derlenen etiketle eşleşiyor.

**32/32 HTTP PASS:**

- Library/Gateway yüzeylerinde iki yeni public profil yolu için anonim, geçersiz
  Bearer, sahte kimlik/ADMIN başlığı ve yalnız cookie ile erişim 401.
- Üç iç yolda doğrudan anonim, geçersiz Bearer ve sahte servis başlığıyla erişim 401.
- Aynı üç iç yol Gateway üzerinden 404; Gateway yapılandırması değiştirilmedi.
- Library, Gateway, mevcut Auth ve Game health 200.
- 401 yanıtlarının ProblemDetail status alanı doğrulandı.

Canlı imajda geçerli kullanıcı/servis token'ıyla pozitif akış veya User kararı sınanmadı;
test token'ları canlı ortama gönderilmedi. Gerçek entegrasyon E2E NOT VERIFIED.
library_db Flyway history yalnız V41 success=true; koleksiyon sayısı 0.

## Çalışma sınırı ve kalan doğrulama

Yeni migration, bağımlılık veya ENV eklenmedi; V41 ve mevcut DB geçmişi korundu.
Değişiklikler yalnız Library backend, docs-library Markdown raporları ve docs/api.md
LIBRARY bölümündedir. Diğer servislerin kaynak/şema/verileri, frontend ve ortak
Compose/Gateway/Maven/CI yapılandırmaları değiştirilmedi.

Gerçek User görünürlük kararı, Auth delegasyon token edinimi ve User/Recommendation
tüketici E2E akışları **PENDING / NOT VERIFIED**. Test portu başarısı bu eksikleri kapatmaz.
Kalan sözleşmeler ve sorumlular ayrı Entegrasyon-Bagimliliklari raporundadır.
Gelecekteki gerçek adaptör için izin revizyonu ile devam eden HTTP yanıtı arasında
dağıtık atomiklik vaat edilmez; her yeni istek güncel karar istemelidir.

## Değişen dosyalar

- LibraryProfileController, LibraryInternalReadController: beş GET yolu.
- LibrarySignalResponse/LibrarySignalPageResponse: azaltılmış öneri girdisi DTO'ları.
- LibraryVisibilityPort, LibraryAccessService, LibrarySharedReadService:
  amaç bazlı erişim kontrolü ve izin sonrası okuma akışı.
- LibraryAccessPurpose/LibraryAccessDecision ve LibraryPrivacyUnavailableException.
- UnavailableLibraryVisibility: kapalı üretim adaptörü.
- LibraryEntryReader/LibraryEntryRepository: sayfalı sinyal listesi ve aynı snapshot toplamı.
- Library'nin güvenlik zinciri ve exception handler: yeni kapsamlar, roller, genel 403/503.
- LibraryApiTest.PrivacyTests ve LibraryAccessServiceTest.
- docs/api.md LIBRARY bölümü ve üç Faz 4 Markdown raporu.

## Güncel checklist

Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü

- [x] LIB-01 — Kişisel oyun kütüphanesi
- [x] LIB-02 — Koleksiyondan oyun kaldırma
- [x] LIB-12 — Mükerrer işlem kontrolü
- [x] LIB-13 — Kimlik ve oyun doğrulama
- [x] LIB-15 — Otomatik koleksiyon kaydı

Faz 2 — Favori ve Bitirildi İşlemleri

- [x] LIB-03 — Favori oyunlar
- [x] LIB-04 — Bitirilen oyunlar

Faz 3 — Kütüphane Listeleme, Filtreleme ve İstatistik

- [x] LIB-05 — Kütüphane listeleme
- [x] LIB-06 — Kütüphane filtreleme
- [x] LIB-07 — Kütüphane sıralama
- [x] LIB-08 — Sayfalama
- [x] LIB-09 — Kütüphane istatistikleri
- [x] LIB-16 — Toplu oyun özeti entegrasyonu

Faz 4 — Gizlilik ve Servis Entegrasyonları

- [ ] LIB-10 — Profil entegrasyonu (Library hazır; gerçek tüketici/delegasyon bekleniyor)
- [ ] LIB-11 — Gizlilik kontrolleri (kapalı varsayılan hazır; gerçek User kararı bekleniyor)
- [ ] LIB-14 — Recommendation entegrasyonu (Library hazır; gerçek izin/tüketici bekleniyor)
- [x] LIB-18 — Yetki, gizlilik ve tutarlılık otomatik testleri

Faz 5 — Arşivlenen Oyunlarda Dayanıklılık

- [ ] LIB-17 — Katalog değişikliklerine dayanıklılık

Faz 4'teki Library hazırlığı sonunda duruldu. Faz 5 için ayrıca açık kullanıcı onayı gerekir.
