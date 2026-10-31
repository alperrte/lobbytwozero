# LTZ-HAM-LIB-02 — Favori ve Bitirildi İşlemleri

Tarih: 30 Ekim 2026. Sorumlu: Hamza. Uygulama sırası: LIB-03 → LIB-04.

**Faz 2: 2/2 · Genel: 7/18.** Kabul kriterleri Library backend otomatik entegrasyon
testleriyle doğrulandı. Çalışan ltz-dev'de pozitif kullanıcı oturumuyla tam E2E
ayrıca **NOT VERIFIED**; aşağıda sınırı açıklanmıştır. Faz 3'e geçilmedi.

## Kapsam ve korunmuş alanlar

Yalnız `backend/services/library-service/`, bu `docs-library/` klasörü ve
`docs/api.md` içindeki LIBRARY bölümü değiştirildi. Diğer backend/frontend dosyaları,
ortak Gateway, Compose, kök Maven POM, CI/CD ve ortak güvenlik yapılandırması değiştirilmedi.
Yeni ENV veya bağımlılık eklenmedi. Pre-push ve SonarQube çalıştırılmadı; kullanıcı manueldir.
Mevcut gereksinim kaynakları ve Faz 1 sonuç raporu değiştirilmedi.

Başka serviste yeni hesap/oyun, fixture, DB yazımı veya şema değişikliği yapılmadı.
Önceki `ops/library-phase1-e2e.mjs` bu turda çalıştırılmadı; mevcut katı veri sınırlarıyla
uyumlu olarak yalnız mevcut servislerin read-only public API'leri ve çalışan konteynerleri kullanıldı.
Uygulama yalnız `ltz-dev-library-service-1` üzerinde yenilendi; diğer servisler durdurulmadı.

## Kabul kriterleri ve davranış

### LIB-03 — Favori oyunlar

- Mevcut `PUT /api/v1/library/entries/{gameId}/favorite` korunur. Geçerli oyunda
  eksik koleksiyon kaydı otomatik açılır; completed false olur. Mevcut diğer bayrak korunur.
- `DELETE /api/v1/library/entries/{gameId}/favorite` yalnız favorite işaretini kaldırır.
  Koleksiyon, completed, id ve addedAt korunur. Gerçek değişiklik updatedAt/version ilerletir.
- Zaten false veya olmayan kayıt ve tekrar 204; yeni kayıt ya da değişiklik oluşturmaz.
- `GET /api/v1/library/entries/favorites` yalnız JWT sub sahibinin favorite=true kayıtlarını döndürür.
- Tekrar favorileme aynı koleksiyon id'si üzerinde çalışır; mükerrer satır oluşmaz.

### LIB-04 — Bitirilen oyunlar

- Mevcut `PUT /api/v1/library/entries/{gameId}/completed` korunur. Eksik kayıt otomatik
  açılır; yeni kayıtta favorite false olur. Mevcut favorite değeri korunur.
- `DELETE /api/v1/library/entries/{gameId}/completed` yalnız completed işaretini kaldırır;
  favorite/koleksiyon korunur. İki bayrak false olsa da koleksiyon satırı silinmez.
- Zaten false/olmayan kayıt ve tekrarda 204; yeniden işaretleme aynı koleksiyon id'sini kullanır.
- `GET /api/v1/library/entries/completed` yalnız sahibinin completed=true kayıtlarını döndürür.
  İki bayrak true ise oyun iki listede de yer alır.

## API sözleşmesi

Yeni yollar:

- `DELETE /api/v1/library/entries/{gameId}/favorite` → 204, gövdesiz.
- `GET /api/v1/library/entries/favorites?page=0&size=20` → 200.
- `DELETE /api/v1/library/entries/{gameId}/completed` → 204, gövdesiz.
- `GET /api/v1/library/entries/completed?page=0&size=20` → 200.

GET/DELETE gövdesi yoktur. JWT/rol/sahiplik modeli Faz 1 ile aynıdır; kullanıcı yalnız
doğrulanmış sub'dan alınır. userId query/header başkasını seçtirmez. ADMIN başka sahip adına erişemez.
Cookie oturumunda CSRF/Origin denetimi mevcut Gateway'dedir; Library yalnız Bearer kabul eder.
Hatalar: 400 geçersiz UUID/page/size, 401 kimlik yok/geçersiz JWT, 403 rol/CSRF,
500 beklenmeyen hata; ProblemDetail kullanılır.

Liste parametreleri: page minimum 0, varsayılan 0; size 1–50, varsayılan 20.
Sıra sabit `addedAt DESC, id DESC`; eşit tarihlerde sonuç kararlıdır. Offset hesabı long'dur;
çok büyük page taşma üretmez. Boş veya toplamı aşan sayfada items boş, totalElements gerçek toplamdır.

Temsili boş liste yanıtı:

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0 }
```

items mevcut LibraryEntryResponse biçimindedir: id, gameId, inCollection, favorite,
completed, addedAt, updatedAt, version. userId, oyun adı, görsel veya tahmini metadata dönmez.

Bu iki listenin zorunlu kayıt sınırı/sabit sırası, genel kütüphane filtreleme/sıralama
özelliği değildir. LIB-05/06/07/08/09/16 uygulanmadı ve tamamlandı işaretlenmedi.
Oyun kartı zenginleştirmesi ve genel sayfalama sözleşmesi Faz 3 kapsamında kalır.

## Veri bütünlüğü ve mimari

- Bayrak kaldırma tek atomik, user_id/game_id ile sınırlı UPDATE'tir. Yalnız hedef bayrak
  true ise çalışır. Diğer bayrağı okumaya/yazmaya gerek yoktur; kayıp güncelleme üretilmez.
- SQL kolon seçimi yalnız LibraryFlag sabit allowlist'inden yapılır; istemci metni SQL'e girmez.
- PUT işlemleri mevcut benzersiz upsert ve Game doğrulamasını kullanır. DELETE ve GET dış
  Game çağrısı gerektirmez: kayıt oluşturmazlar ve katalog kesintisinde de yerel durumu yönetirler.
- Reader, kayıtlar ve toplam için iki SQL sorgusunu aynı read-only REPEATABLE_READ
  transaction içinde yürütür. Araya başka yazma girse de listede 1 kayıt/toplam 2 gibi tutarsızlık oluşmaz.
- Controller → LibraryService → reader/writer → repository akışı korunur. Ortak entity→DTO
  dönüşümü iki kullanım için application/mapper altında tutulur; entity API'ye dönmez.
- Şema/entity değişmedi. Yeni migration yoktur; uygulanmış V41 dosyası değiştirilmedi.

## Değişen dosyalar

- `api/controller/LibraryController.java`: dört GET/DELETE yolu ve page/size validation.
- `api/dto/response/LibraryEntryPageResponse.java`: liste DTO'su.
- `application/service/LibraryService.java`: favori/bitirildi liste ve geri alma akışları.
- `application/service/LibraryEntryWriter.java`: atomik bayrak kaldırma.
- `application/service/LibraryEntryReader.java`: tutarlı liste/toplam transaction'ı.
- `application/mapper/LibraryEntryMapper.java`: ortak entry response dönüşümü.
- `domain/enums/LibraryFlag.java`: güvenilir kolon allowlist'i.
- `infrastructure/persistence/repository/LibraryEntryRepository.java`: sınırlı owner sorguları ve UPDATE.
- `infrastructure/security/AccountErasureSecurityConfiguration.java`: yalnız yeni Library yolları için mevcut JWT/rol kuralları.
- `src/test/java/com/ltz/library/api/controller/LibraryApiTest.java`: kabul, negatif, yarış ve snapshot testleri.
- `docs/api.md`: yalnız LIBRARY sözleşmeleri.
- `docs-library/`: bu rapor, Faz 2 PR açıklaması ve yerel doğrulama çıktıları.

Java yolları `backend/services/library-service/src/main/java/com/ltz/library/` altındadır;
test yolu ayrıca belirtilmiştir. Başlangıç çalışma alanı temizdi.

## Otomatik doğrulama

Başlangıç clean verify: **45 PASS**. LIB-03 ara API testi: **23 PASS**.
Son clean verify: **58 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**.

JaCoCo LINE: **246 covered / 4 missed = %98,40**.
Merkezi coverage PASS; değişen satır kapısı **151/152 = %99,34** (kapının kendi Git karşılaştırma kapsamı).
Maven Checkstyle/PMD raporlarında **0 ihlal**. git diff --check başarılı.
Bağımsız pre-push/lint-ratchet veya SonarQube betiği çalıştırılmadı; Maven'in kendi JaCoCo kapısı korundu.

Yeni testlerin kapsadığı durumlar:

- İki bayrak ve koleksiyon korunması; iki bayrak false olsa da kayıt kalması.
- Olmayan kayıt/tekrar kaldırmada no-op; tarih/version korunması; yeniden işaretlemede aynı id.
- Favori/bitirildi listelerinin birbirinden bağımsızlığı ve aynı oyunun iki listede bulunması.
- İki kullanıcı izolasyonu, sahte userId, rol ve token türü; 401/403/400.
- Boş/sınır dışı sayfa, eşit timestamp'te stabil id sırası, maksimum size ve yüksek offset.
- Her bayrak için 16 eşzamanlı aç/kapat isteğinde tek satır ve diğer bayrak korunması.
- Liste ile count arasına gerçek başka transaction yazması sokularak snapshot tutarlılığı.
- Gerçek Auth üretim issuer kaynakları ve Gateway cookie/CSRF filtreleriyle yeni yolların testi;
  cookie'den Bearer dönüşümü, eksik CSRF'de 403, doğru CSRF'de GET 200/DELETE 204.
- Game HTTP adaptörü mevcut ayrı MockRestServiceServer testleriyle korunur; liste/geri alma
  testlerinde GameCatalogPort çağrısı olmadığından N+1 yoktur.

Testler host JDK25'te, ltz-dev PostgreSQL konteynerindeki yalnız Library'ye ait ayrılmış
`library-phase1-test` veritabanında koştu. Test guard'ı gerçek library_db temizliğini reddeder.
Bu isim önceki güvenli fixture sözleşmesidir; Faz 2'de de kullanıldı. Test DB/rolü iş sonunda kaldırıldı.

```powershell
mvn -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml `
  -f backend/pom.xml -pl services/library-service -Pci `
  '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5542/library-phase1-test' `
  '-Dspring.datasource.username=ltz_library_phase1_test' clean verify
```

Yerel ham çıktılar (Git ignore kapsamındaki loglar):

- `LTZ-HAM-LIB-02-Baslangic-Maven.log`
- `LTZ-HAM-LIB-02-LIB03-Test.log`
- `LTZ-HAM-LIB-02-Maven-Sonuc.log`
- `LTZ-HAM-LIB-02-Docker-Build.log`
- `LTZ-HAM-LIB-02-HTTP-Kontrol.log`

## Çalışan ltz-dev doğrulaması ve sınırlar

Standart Library Dockerfile ile `ltz/library-service:dev` derlendi. Yalnız mevcut Library
konteyneri `--no-deps --no-build` ile yenilendi; Compose dosyası değiştirilmedi.
Çalışan image ID ile etiket aynı. Library/Gateway health 200; Auth public JWKS ve Game public katalog 200.
library_db Flyway history yalnız V41 success=true; Hibernate validate başarılı; Library satır sayısı 0.

Gerçek HTTP **32/32 PASS**:

- Library/Gateway health ve mevcut Auth/Game read-only public uçları: 200.
- Her iki listeye anonim, bozuk Bearer, sahte kimlik başlığı veya geçersiz cookie: 401.
- Her iki işaret kaldırma yoluna anonim veya bozuk Bearer: 401.
- Cookie ile CSRF'siz DELETE: doğrudan Library 401, gerçek Gateway 403.
- Tüm hata yanıtlarında application/problem+json doğrulandı.

**Pozitif gerçek Auth oturumu → canlı Gateway → Library → Game E2E: NOT VERIFIED.**
Mevcut public katalogda 0 oyun var ve bu tur için hazır doğrulanmış test oturumu sağlanmadı.
Kullanıcının yeni sınırları gereği Auth hesabı/Game fixture verisi oluşturulmadı, diğer servis
kalıcı verisi veya güvenlik ayarı değiştirilmedi. Otomatik üretim kaynak sözleşme testleri,
çalışan servisler arası pozitif E2E gibi sunulmadı. Bu canlı kabul akışı için kullanıcının
sağlayacağı mevcut test oturumu ve mevcut geçerli gameId gerekir; gizli değerler belgeye alınmaz.

Teknik kapsamda bilinen test başarısızlığı yoktur. Başka serviste endpoint/kod değişikliği
gerekmiyor. Genel listeleme/oyun özeti ve gizlilik entegrasyonu kendi sonraki fazlarındadır.
Faz 3'e geçiş için açık kullanıcı onayı beklenir. Pre-push/SonarQube kullanıcı tarafından manuel yapılır.

## Güncel checklist

### Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü

- [x] LIB-01 — Kişisel oyun kütüphanesi
- [x] LIB-02 — Koleksiyondan oyun kaldırma
- [x] LIB-12 — Mükerrer işlem kontrolü
- [x] LIB-13 — Kimlik ve oyun doğrulama
- [x] LIB-15 — Otomatik koleksiyon kaydı

### Faz 2 — Favori ve Bitirildi İşlemleri

- [x] LIB-03 — Favori oyunlar
- [x] LIB-04 — Bitirilen oyunlar

### Faz 3 — Kütüphane Listeleme, Filtreleme ve İstatistik

- [ ] LIB-05 — Kütüphane listeleme
- [ ] LIB-06 — Kütüphane filtreleme
- [ ] LIB-07 — Kütüphane sıralama
- [ ] LIB-08 — Sayfalama
- [ ] LIB-09 — Kütüphane istatistikleri
- [ ] LIB-16 — Toplu oyun özeti entegrasyonu

### Faz 4 — Gizlilik ve Servis Entegrasyonları

- [ ] LIB-10 — Profil entegrasyonu
- [ ] LIB-11 — Gizlilik kontrolleri
- [ ] LIB-14 — Recommendation entegrasyonu
- [ ] LIB-18 — Yetki, gizlilik ve tutarlılık testleri

### Faz 5 — Arşivlenen Oyunlarda Dayanıklılık

- [ ] LIB-17 — Katalog değişikliklerine dayanıklılık

Faz 2 tamamlandı. Faz 3'e geçmem için onayını bekliyorum.
