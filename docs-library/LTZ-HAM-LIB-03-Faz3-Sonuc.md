# LTZ-HAM-LIB-03 — Library Service Faz 3 sonucu

Tarih: 30 Ekim 2026. Sorumlu: Hamza.
Kapsam ve uygulama sırası: LIB-05 → LIB-06 → LIB-07 → LIB-08 → LIB-09 → LIB-16.

Faz 3: **6/6**. Genel ilerleme: **13/18**.
Maven `clean verify`: **78 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**.
JaCoCo LINE: **342 covered / 4 missed, %98,84**. Checkstyle/PMD: **0 ihlal**.
ltz-dev HTTP kontrolleri: **36/36 PASS**. Pozitif canlı kullanıcı E2E: **NOT VERIFIED**.

## Uygulanan davranışlar

- **LIB-05:** `GET /api/v1/library/entries`, JWT sub sahibinin bütün koleksiyonunu
  favori ve bitirildi bayraklarıyla sunar. Satır ve oyun özeti ayrı response DTO'larıdır;
  JPA entity dışarı verilmez. Eksik oyun özeti koleksiyon kaydını silmez.
- **LIB-06:** `favorite` ve `completed` isteğe bağlı boolean filtreleridir. Yoksa ilgili
  bayrak filtrelenmez; false açıkça işaretsiz kayıtları seçer. İkisi birlikte AND uygulanır.
  SQL parametreleri bağlanır; her sorgu JWT sahibine sınırlıdır.
- **LIB-07:** `sort=ADDED_AT|TITLE`, `direction=ASC|DESC`. Varsayılan ADDED_AT/DESC.
  Tarih sırası DB'de, aynı yönlü id ikincil anahtarıyla hesaplanır. Ad sırası bütün
  filtrelenmiş kayıtlar zenginleştirildikten sonra, sayfalama öncesinde hesaplanır.
  Ad anahtarı Unicode NFKC ve Locale.ROOT küçük harf dönüşümüdür. Eşit adlarda id ASC;
  eksik adlar her iki yönde de sonda, kendi aralarında id ASC yer alır.
- **LIB-08:** `page>=0`, `size=1..50`; varsayılanlar 0 ve 20. Yanıtta page, size ve
  filtrelenmiş totalElements bulunur. Sonuç dışı sayfa 200/boş items döner ve Game çağrısı
  yapmaz. Offset long hesaplanır. Tarih sıralamasında liste ve toplam aynı REPEATABLE_READ
  transaction'ından okunur. Ad sıralamasında tek yerel sorgunun sonuç kümesi kullanılır.
  Faz 2 favori/bitirildi liste uçlarının düz DTO sözleşmesi korunur.
- **LIB-09:** `GET /api/v1/library/entries/statistics`, tek owner SQL aggregate ile
  totalGames/favoriteGames/completedGames döner. İki bayrak true olan kayıt toplamda bir,
  iki alt sayımda da birer kez sayılır. İki bayrak false olan kayıt toplamda kalır.
  İdempotent tekrarlar sayıyı artırmaz; liste filtreleri bu sayımları etkilemez.
  İstatistik için Game HTTP çağrısı yapılmaz.
- **LIB-16:** Mevcut public `POST /api/v1/game/games/batch` sözleşmesi RestClient ile
  kullanılır. Kimlikler tekilleştirilir; istek başına en fazla 50 kimlik gönderilir.
  ADDED_AT ile 50 oyunluk sayfa tek batch gerektirir. TITLE için bütün filtrelenmiş
  küme 50'lik batch'lere ayrılır. Kullanıcı token'ı aktarılmaz; otomatik retry yoktur.

## API ve hata toleransı

İki yeni uç Gateway üzerinden `/api/v1/library/entries` ve
`/api/v1/library/entries/statistics` yollarındadır. Kullanıcı JWT'si, mevcut kanonik rol
ve sahiplik denetimleri geçerlidir. Query/header userId başka kullanıcı seçemez.
Geçersiz parametreler 400, kimlik yok/geçersiz ise 401, rol yetersizse 403 döner.
Hatalar mevcut ProblemDetail sözleşmesini kullanır.

Liste yanıtı:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "metadataPartial": false
}
```

Her item içinde `entry` mevcut LibraryEntryResponse alanlarını, `game` ise gameId,
title, coverUrl, origin, metadataCompleteness, gameType ve status alanlarını taşır.

- `AVAILABLE`: Game ilgili kimliği items içinde doğrulamıştır. Opsiyonel metadata null olabilir.
- `UNAVAILABLE`: Game ilgili kimliği unavailable içinde bildirmiştir. Mevcut sözleşme
  arşivli, birleştirilmiş ve bulunamayan oyun nedenlerini ayırmaz.
- `TEMPORARILY_UNAVAILABLE`: Timeout, 429, HTTP hatası veya bozuk/çelişkili batch yanıtı.
  Aynı liste isteğinde sonraki batch'ler çağrılmaz. Önceki başarılı batch'ler korunur.

Yanıttaki items/unavailable bölümleri istenen kimlikleri tam ve ayrık kapsamalıdır;
null, yinelenen, yabancı, çakışan veya atlanan kimlikler geçici hata sayılır.
Boş/256 karakterden uzun adlar, geçersiz/HTTPS olmayan/2048 karakterden uzun görsel
URL'leri ve tanınmayan opsiyonel enum değerleri null yapılır. Ad uydurulmaz.
Game hatasında liste yerel verilerle 200 döner; koleksiyon kaydı, bayraklar ve tarihler korunur.

metadataPartial, ADDED_AT için dönen sayfadaki; TITLE için bütün filtrelenmiş kümedeki
kullanılamayan özet veya eksik ad üzerinden hesaplanır. Opsiyonel görselin tek başına
eksik olması bu alanı true yapmaz. Boş/sonuç dışı sayfada metadataPartial=false döner.
Game HTTP çağrıları yerel DB transaction'ı kapandıktan sonra yapılır.

## Test kanıtı

Son `clean verify`: **78 test PASS**, failure/error/skipped **0**.
JaCoCo: **%98,8439 LINE**; merkezi coverage kapısı PASS.
Değişen satırlar: **247/248, %99,60**. Checkstyle ve PMD raporlarında **0 ihlal**.
Manifest/baseline veya kural bastırması değiştirilmedi.

- LibraryApiTest içindeki CollectionTests: owner izolasyonu, boş koleksiyon, gerçek
  PostgreSQL/Flyway/Hibernate validate, dokuz filtre kombinasyonu, AND/false davranışı,
  global ad sırası, normalize eşit adlar, eksik adların sırası ve iki yönlü tarih sırası.
- Varsayılan/sınır/geçersiz parametreler, yüksek offset, sayfalar arası tekrar/atlama,
  eşzamanlı insert sırasında liste/toplam snapshot tutarlılığı ve Faz 2 DTO uyumluluğu.
- İstatistikte çakışan bayraklar, idempotent tekrar, geri alma/silme, boş küme ve başka owner.
- 60 kayıtlı API senaryosu: tarih sayfasında tek batch; ad sıralamasında iki batch ve
  bütün küme üzerinden doğru sayfa. Game çağrısı sırasında yerel transaction açık değildir.
- GameServiceClientTest: 50 kimlikte tek HTTP, kimlik bölümlerinin tamlığı/tutarlılığı,
  opsiyonel metadata temizliği, hata yanıtları ve mevcut mutasyon doğrulamasının korunması.
- LibraryGameSummariesTest: 121 kimlikte 50/50/21, tekrarları tekilleştirme, boş giriş,
  ilk/ikinci batch hatasında çağrıların kesilmesi ve önceki başarılı özetlerin korunması.
- Faz 1/2 mutasyon, JWT/JWKS ve D4 testleri aynı verify içinde başarılıdır.

```powershell
mvn -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml `
  -f backend/pom.xml -pl services/library-service -Pci `
  '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5542/library-phase1-test' `
  '-Dspring.datasource.username=ltz_library_phase1_test' clean verify
```

Testler ltz-dev PostgreSQL içindeki ayrı Library test DB/rolünü kullandı; test parolası
CI profilindeki sentetik değerdir. Test DB/rolü çalışma sonunda kaldırıldı; tekrar koşmak
için aynı güvenli test ortamı kurulmalıdır. Kalıcı library_db test temizliğine dahil edilmedi.
Ham çıktılar Library `target/verification/` altında; docs-library içine log konulmadı.
Pre-push ve SonarQube çalıştırılmadı; manuel kontrol planı korunuyor.

## ltz-dev doğrulaması

Standart Library Dockerfile ile `ltz/library-service:dev` derlendi. Yalnız Library,
`docker compose -p ltz-dev up -d --no-deps --no-build library-service` ile yenilendi.
Çalışan konteyner image ID'si derlenen imajla eşleşiyor.

**36/36 HTTP PASS:** Library/Gateway health 200; iki yüzeyde collection/statistics/
favorites/completed GET uçlarının her biri için anonim, geçersiz Bearer, sahte owner
başlığı ve geçersiz cookie 401; ayrıca mevcut Game/Auth health 200.
Bu canlı kontroller rol sahibi oturumla pozitif uçtan uca doğrulama yerine geçmez.
Pozitif canlı kullanıcı E2E **NOT VERIFIED**: kullanılabilir test oturumu sağlanmadı;
başka servislerde hesap/katalog fixture'ı veya kalıcı veri oluşturulmadı.
Pozitif JWT/sahiplik/liste davranışları otomatik PostgreSQL entegrasyon testlerinde sınandı.

library_db üzerinde V41 success=true ve koleksiyon sayısı 0 doğrulandı.
Yeni migration, şema, ENV veya bağımlılık eklenmedi. V41 ve Flyway geçmişi korundu.
Diğer servislerin kaynakları, şemaları, kalıcı verileri, ortak yapılandırmalar ve frontend
dosyaları değiştirilmedi. API kataloğunda yalnız LIBRARY bölümü güncellendi.

## Dosyalar

- API: LibraryCollectionController; LibraryCollectionItemResponse,
  LibraryCollectionPageResponse, LibraryGameResponse, LibraryStatisticsResponse.
- Application: LibraryCollectionService, LibraryGameSummaries, LibraryEntryReader;
  GameCatalogPort ve GameSummary.
- Domain: CollectionSort, SortDirection, GameSummaryStatus.
- Infrastructure: LibraryEntryRepository, GameServiceClient, GameBatchResponse;
  yeni GET yollarını mevcut rol denetimine alan Library güvenlik zinciri.
- Test: LibraryApiTest.CollectionTests, GameServiceClientTest, LibraryGameSummariesTest.
- Dokümantasyon: docs/api.md LIBRARY bölümü; bu rapor ve Faz 3 PR açıklaması.

## Maliyet ve doğrulama sınırları

TITLE için K filtrelenmiş kayıt belleğe alınır: O(K) bellek, O(K log K) sıralama ve
en fazla ceil(K/50) sıralı batch HTTP çağrısı. İlk geçici hata sonraki çağrıları durdurur.
ADDED_AT yalnız sayfayı zenginleştirir. Büyük koleksiyon için performans SLA ölçümü yapılmadı.
Yerel DB snapshot'ı ile farklı Game batch yanıtları arasında dağıtık snapshot yoktur.
Ayrı sayfa istekleri arasında veri değişirse sayfalar değişebilir. Metadata kalıcı snapshot
veya cache olarak saklanmaz. Arşivli oyunda yerel kaydı korumak, Faz 5'in bütün kabul
kriterlerinin tamamlandığı anlamına gelmez.

## Güncel görev listesi

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

- [ ] LIB-10 — Profil entegrasyonu
- [ ] LIB-11 — Gizlilik kontrolleri
- [ ] LIB-14 — Recommendation entegrasyonu
- [ ] LIB-18 — Yetki, gizlilik ve tutarlılık testleri

Faz 5 — Arşivlenen Oyunlarda Dayanıklılık

- [ ] LIB-17 — Katalog değişikliklerine dayanıklılık

Faz 3 sonunda duruldu. Faz 4 için açık kullanıcı onayı bekleniyor.
