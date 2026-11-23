# LTZ-HAM-LIB-01 — Library Service Faz 1 sonucu

Tarih: 30 Ekim 2026. Sorumlu: Hamza. Kapsam: LIB-01 → LIB-02 → LIB-12 → LIB-13 → LIB-15.

Son kontrol: 30 Ekim 2026, 17:03 (Europe/Istanbul). Gerçek ltz-dev E2E sonucu: **62/62 PASS**.
Pre-push ve SonarQube bu son kontrol turunda çalıştırılmadı; kullanıcı tarafından manuel yapılacaktır.

Faz 1: **5/5**. Genel: **5/18**. Faz 2–5 uygulanmadı.
Mevcut `docs-library/` klasörü kullanıldı; ikinci bir Library dokümantasyon klasörü açılmadı.
Kaynak görev Markdown dosyaları değiştirilmedi. Endpoint kataloğu zorunlu kayıt yeri olan
`docs/api.md` dosyasının yalnız LIBRARY bölümünde güncellendi.

## Uygulama

- LIB-01: Kanonik LTZ gameId ile koleksiyon kaydı oluşturulur; iki bayrak da false olabilir.
  Kayıt oyun satın alma/sahiplik kanıtı değildir.
- LIB-02: JWT sub ve gameId ile sınırlı fiziksel silme, koleksiyon/favori/bitirildi bilgilerini
  tek satır ve tek yerel transaction içinde kaldırır. Tekrar veya olmayan kayıtta 204.
- LIB-12: PostgreSQL `UNIQUE (user_id, game_id)` ve atomik `INSERT ... ON CONFLICT ... RETURNING`
  kullanılır. Uygulama ön kontrolüne dayanılmaz. Tekrar, kimliği/tarihleri/version değerini korur.
- LIB-13: Kullanıcı token'ı Library sınırında RS256/Auth JWKS, at+jwt türü, issuer, audience,
  zorunlu zamanlar, en çok 30 saniye saat toleransı ve kanonik UUID sub ile doğrulanır.
  Beş kanonik Auth rolünden biri gerekir. İstek gövdesindeki userId bilinmeyen alan olarak
  reddedilir; query/sahte kimlik başlıkları sahip seçemez. D4 servis token'ı kullanıcı
  uçlarında, kullanıcı token'ı hesap silme iç ucunda kabul edilmez.
- LIB-15: Favori/bitirildi PUT çağrısı eksik kaydı aynı DB işlemiyle oluşturur; yeni kayıtta
  yalnız hedef bayrak true olur. Mevcut diğer bayrak korunur. Gerçek değişiklik version ve
  updated_at ilerletir. Geri alma ve listeleme uçları eklenmedi.
- İlk kişisel tabloyla birlikte mevcut D4 hesap silme servisi tamamlandı: yalnız hedef hesabın
  library_entries satırları atomik/idempotent silinir; başka hesabın verisi korunur.

Game doğrulaması mevcut public `POST /api/v1/game/games/batch` sözleşmesiyle, RestClient ve
mevcut GAME_SERVICE_URL üzerinden yapılır. Kullanıcı token'ı aktarılmaz; otomatik retry yoktur.
Yalnız 200 ve tam/tutarlı istenen kimlik kabul edilir. unavailable için 404; timeout, HTTP
hatası, 429, yönlendirme veya bozuk/tutarsız yanıt için 503 döner ve yeni kayıt oluşmaz.
Game HTTP çağrısı transaction dışındadır; dağıtık atomiklik iddia edilmez.

## API kullanımı

Tüm kullanıcı yolları Gateway üzerinden `/api/v1/library` altında sunulur.
Servis yalnız Authorization Bearer kabul eder. Tarayıcı cookie→Bearer dönüşümü ve
cookie mutasyonlarında CSRF/Origin denetimi mevcut Gateway'e aittir.

Ekleme — ilk çağrı ve tekrar **200**:

```http
POST /api/v1/library/entries
Content-Type: application/json
```

```json
{ "gameId": "00000000-0000-4000-8000-000000000001" }
```

Örnek yanıt; kimlikler ve tarihler temsili, gerçek kullanıcı verisi değildir:

```json
{
  "id": "00000000-0000-4000-8000-000000000002",
  "gameId": "00000000-0000-4000-8000-000000000001",
  "inCollection": true,
  "favorite": false,
  "completed": false,
  "addedAt": "2026-10-30T12:00:00Z",
  "updatedAt": "2026-10-30T12:00:00Z",
  "version": 0
}
```

- `DELETE /api/v1/library/entries/{gameId}` → 204, gövdesiz; tekrar da 204.
- `PUT /api/v1/library/entries/{gameId}/favorite` → 200, gövde gerekmez; yeni kayıtta true/false.
- `PUT /api/v1/library/entries/{gameId}/completed` → 200, gövde gerekmez; yeni kayıtta false/true.
- Mevcut `DELETE /internal/v1/accounts/{accountId}` → 204; yalnız Auth D4 servis token'ı.

Doğrulama hatası 400; kimlik yok/geçersiz 401; rol yok 403; oyun kullanılamıyor 404;
Game doğrulanamıyor 503. Hatalar ProblemDetail'dır. Silme dış katalog erişimi gerektirmez.
İç hesap silme yolu Gateway'e yönlendirilmez.

## Veri ve dosyalar

Tek migration: `backend/services/library-service/src/main/resources/db/migration/V41__create_library_entries.sql`.
V41–V50 Library aralığı kontrol edildi; mevcut klasörde yalnız .gitkeep bulunduğundan V41 seçildi.
Mevcut migration değiştirilmedi. library_entries: UUID PK ve REF kimlikler,
timestamptz tarihler, version bigint, NOT NULL bayraklar, koleksiyon CHECK'i ve kullanıcı/oyun UNIQUE'i.
Başka servis DB'sine FK veya sorgu eklenmedi. Hibernate ddl-auto=validate korunuyor.

Servis kökü: `backend/services/library-service/`.

- `api/controller/LibraryController`, request/response record'ları ve mevcut exception handler.
- `application/port/GameCatalogPort`, LibraryService, transaction sınırını tutan LibraryEntryWriter,
  gerçek temizleme yapan AccountErasureService.
- `domain/entity/LibraryEntry`, LibraryAction ve güvenli domain istisnaları.
- `infrastructure/client/GameServiceClient`, dış request/response DTO'ları,
  persistence/repository/LibraryEntryRepository, kullanıcı JWT doğrulayıcısı ve mevcut güvenlik zinciri.
- application.properties mevcut JWT_AUDIENCE değişkenini tüketir. Yeni ENV yoktur.
  Son kontrol başlangıcında mevcut olan test kapsamındaki Testcontainers bağımlılığı ve
  LibraryApiTest test DB izolasyonu korundu; pom.xml ve docs-security notu bu turda değiştirilmedi.
- Testler: LibraryApiTest, GameServiceClientTest, ProductionAuthSupport ve uyarlanan AccountErasureTest;
  mevcut decoder yaşam döngüsü/sözleşme testleri korunmuştur.

## Doğrulama kanıtı

Son Maven `clean verify`: **45 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**.
JaCoCo: **210 covered / 4 missed, %98,13 LINE**.
Merkezi progressive coverage kapısı PASS; değişen satırlar **115/116, %99,14**.
Manifest/baseline veya kural bastırması değiştirilmedi. Maven Checkstyle/PMD raporlarında
ihlal **0**; git diff --check başarılı. Ayrı pre-push/lint-ratchet veya SonarQube bu turda
çalıştırılmadı. Kullanıcının manuel kontrolleri Codex geliştirme eksikliği sayılmadı.

Test kapsamı:

- Gerçek PostgreSQL 17, Flyway ve Hibernate validate; başlangıç bayrakları, tekrar ve sahiplik.
- 16 eşzamanlı POST tek satır; doğrudan yinelenen INSERT gerçek DB constraint hatası.
- 24 eşzamanlı POST/favori/bitirildi tek satır, işaret kaybı yok.
- İki kullanıcı izolasyonu, bayraklarıyla birlikte silme, D4 hesap temizleme ve tekrar.
- Gerçek RSA/JWKS filtreleriyle JWT türü/imzası/issuer/audience/zaman/UUID/rol negatifleri.
- Gerçek Auth AccessTokenIssuer/ServiceTokenIssuer ve Gateway cookie/CSRF filtre kaynakları,
  yalnız geçici test çıktısına derlenerek sözleşme uyumu sınandı; başka servisin kaynağı değişmedi.
- Game HTTP adaptörü MockRestServiceServer ile sınandı. Katalog çağrısında yerel transaction açık değil.
- Test temizliği yanlışlıkla library_db seçilirse assert ile durur. Yalnız özel test DB/rolü veya
  CI'ın `library-service`/`ltz_test` çifti ve CI test işareti kabul edilir.

Son Maven komutu; yalnız test rolü kullanıldı, parola CI profilindeki sentetik test değeridir:

```powershell
mvn -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml `
  -f backend/pom.xml -pl services/library-service -Pci `
  '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5542/library-phase1-test' `
  '-Dspring.datasource.username=ltz_library_phase1_test' clean verify
git diff --check
```

Maven çıktısı: `logs/library-phase1-final-verify.log`. JaCoCo/Surefire raporları servis target klasöründedir.
Test veritabanı/rolü iş sonunda kaldırıldığı için tekrar koşmadan önce aynı test DB'si kurulmalıdır.

## ltz-dev üzerinde çalıştırma

Kullanıcının açık yönlendirmesiyle nihai çalışma ortamı **ltz-dev** oldu.
Başlangıçta açılan `ltz-library-phase1-test` PostgreSQL konteyneri/ağı kaldırıldı.
Son Maven testleri host JDK25 ile, **ltz-dev-postgres-1 içindeki ayrı Library test DB'sine** bağlanarak koştu.
Uygulamanın kendisi **ltz-dev-library-service-1** içinde çalışıyor.

- Mevcut PostgreSQL, Auth, Game, Gateway ve Library konteynerleri başlangıçta durmuştu; başlatıldı.
- Standart Library Dockerfile ile `ltz/library-service:dev` başarıyla derlendi.
- Yalnız Library `docker compose -p ltz-dev up -d --no-deps --no-build library-service` ile yenilendi.
- Çalışan Library konteynerinin image ID'si son etiketin image ID'siyle eşleşti.
- Gerçek library_db Flyway history: V41 success=true. PK/CHECK/UNIQUE doğrulandı; 0 koleksiyon kaydı.
- Gerçek Library/Gateway HTTP kontrolleri **15/15 PASS**: health 200, anonim ekle/sil/işaretle 401,
  geçersiz Bearer ve sahte kullanıcı başlığı 401, Gateway eksik CSRF cookie mutasyonu 403.
- Auth/Game/Gateway/Library health 200. Gerçek Game public batch: 200, olmayan kimlik unavailable.
- Library konteynerinden Auth JWKS Docker ağı erişimi: HTTP 200.
- ENV dosyaları değiştirilmedi; volume/veritabanı geçmişi sıfırlanmadı. V41 normal Flyway akışıyla uygulandı.

## Son kod incelemesi ve düzeltme

Tüm Library controller, DTO, application service, repository, JWT ve D4 güvenlik kodu,
Game RestClient adaptörü, V41 ve otomatik testler yeniden okundu. LIB-01/02/12/13/15
kabul kriterleriyle eşleştirildi. Başka servisin backend/frontend dosyası değiştirilmedi;
hiçbir frontend dosyasına dokunulmadı. Diğer servislerin DB'sine Library kodundan erişim yoktur.

Bir eşzamanlı zaman damgası hatası bulundu. PostgreSQL CURRENT_TIMESTAMP transaction
başlangıcına sabittir. Daha erken başlayan transaction, başka transaction'ın sonradan
oluşturduğu kaydı favorilerken updated_at değerini added_at öncesine çekebiliyordu.
`anEarlierTransactionMustNotBackdateAnEntryCreatedByAnotherTransaction` testi düzeltmeden önce
**1 failure** ile bu hatayı kanıtladı; çıktı `logs/library-phase1-timestamp-regression.log`.
Yalnız upsert'in gerçek bayrak değişikliği dalında `clock_timestamp()` kullanıldı.
No-op tekrarlar tarih/version değiştirmemeye devam ediyor. Düzeltmeden sonra regresyon ve
tüm 45 test geçti. Şema değişmedi; yeni migration yok, V41 değiştirilmedi.

Bu turda değiştirilen/eklenen dosyalar:

- `backend/services/library-service/src/main/java/com/ltz/library/infrastructure/persistence/repository/LibraryEntryRepository.java`
  — tek SQL zaman ifadesi.
- `backend/services/library-service/src/test/java/com/ltz/library/api/controller/LibraryApiTest.java`
  — deterministik eşzamanlı timestamp regresyon testi.
- `ops/library-phase1-e2e.mjs` — yalnız ltz-dev'e sınırlandırılmış, sırları bellekte tutan gerçek HTTP testi.
- `docs/api.md` — yalnız LIBRARY test kanıtı/güncellik açıklaması.
- Bu rapor, `LTZ-HAM-LIB-01-PR-Aciklamasi.md` ve `LTZ-HAM-LIB-01-E2E-Sonuc.md`.

## Gerçek E2E: Auth → Gateway → Library → Game → PostgreSQL

Komut: `node ops/library-phase1-e2e.mjs`. Sonuç: **exit 0, 62/62 kontrol PASS**.
Her isteğin beklenen/gerçekleşen HTTP kodu ve DB kanıtı
[ayrı E2E raporunda](LTZ-HAM-LIB-01-E2E-Sonuc.md) yazılıdır.

- İki sentetik test hesabı mevcut Auth API'sinden kaydedildi (202), Mailpit koduyla
  doğrulandı (204) ve gerçek login ile oturum açtı (200). JWT Auth tarafından üretildi;
  test anahtarı/imza taklidi kullanılmadı. Cookie/CSRF ve açık Bearer akışları gerçek Gateway'den geçti.
- Katalog boş olduğu için yalnız Hamza'nın sahip olduğu game_db içinde işaretli tek bir
  test oyunu oluşturuldu; mevcut Game batch API'si bu kanonik UUID'yi 200 ile döndürdü.
  Library istemcisi aynı çalışan Game Service'e başvurdu. Game kaynak kodu/migration'ı değişmedi.
- Ekleme/tekrar 200; DB satırı 1 ve entry id sabit. İlk bayraklar false/false.
- Favori/bitirildi 200; DB'de iki bayrak true, önceki favori korundu.
- Başkasının userId alanını gövdeye eklemek 400; başka kullanıcı adına yol 403.
  B'nin silmesi 204 olsa da yalnız B'yi hedefledi; A'nın satırı ve bayrakları değişmedi.
- B'nin otomatik favorisi yalnız B için yeni kayıt açtı; completed false kaldı.
  Eksik kayda completed PUT'u da favorite false ile çalıştı.
- Silme/tekrar 204; DB'de koleksiyon ve iki bayrakla ilişkili satır 0.
- Geçersiz gameId POST/PUT 404, geçersiz JWT 401, cookie ile eksik CSRF 403.
- Game kısa süre durduruldu; geçerli oyunla POST/PUT 503 döndü, DB'ye satır yazılmadı.
  Game finally içinde geri açıldı; health 200 ve yeniden ekleme 200 doğrulandı.

Temizlik: yalnız test kullanıcılarının ilgili Library satırları ve işaretli test Game kaydı
kaldırıldı. Test e-postaları yalnız ilgili mesaj ID'leriyle Mailpit'ten temizlendi; tüm mailbox
silme kullanılmadı ([Mailpit API kaynağı](https://mailpit.axllent.org/docs/api-v1/)).
İki test hesabı Auth'un request/confirm API'lerinden 202/202 ile silme sürecine alındı.
**Auth'un 14 günlük geri alma süresi korunuyor: hesaplar hemen fiziksel silinmiş sayılmıyor.**
Auth DB'sine SQL yazılmadı, süre kısaltılmadı. Test kullanıcı oturumları resmi silme onayıyla
iptal edilir; parola, token, cookie ve OTP değerleri dosyaya/loga yazılmadı.
Mevcut veritabanları veya Docker volume'ları sıfırlanmadı. Son Library satır sayısı 0.

## Açık konular ve sonraki faz

- Codex'in Faz 1 otomatik ve gerçek E2E doğrulamalarında açık engel kalmadı.
  Faz 2'ye teknik geçiş engeli saptanmadı; uygulama için kullanıcının açık onayı gerekir.
- **Pre-push ve SonarQube kullanıcı tarafından manuel gerçekleştirilecektir.**
  Bu turda bu kontroller çalıştırılmadı, yapılandırmaları veya altyapıları değiştirilmedi.
  Maven clean verify içindeki normal JaCoCo/merkezi coverage aşaması tamamlandı.
- Frontend değişmedi; diğer servislerin backend/frontend kodlarına dokunulmadı.
- İki servis arasında transaction yoktur: doğrulama sonrası katalog değişimi atomik engellenemez.
  Aynı kayıt için DB işlemleri sıralanır; silmeden sonra gelen yeni işaretleme yeniden kayıt oluşturabilir.
- Auth iptal/hesap durumu yayılımı mevcut access token ömrü + en çok 30 saniye toleransla sınırlıdır;
  Library'ye yeni introspection veya uydurma servis sözleşmesi eklenmedi.
- Game Service'e ek endpoint talebi gerekmedi. User/Recommendation entegrasyonu sonraki fazlardadır.

## Güncel checklist

Faz 1: **5/5** · Genel: **5/18**. Eksik Faz 1 kabul doğrulaması yoktur.

### Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü

- [x] LIB-01 — Kişisel oyun kütüphanesi
- [x] LIB-02 — Koleksiyondan oyun kaldırma
- [x] LIB-12 — Mükerrer işlem kontrolü
- [x] LIB-13 — Kimlik ve oyun doğrulama
- [x] LIB-15 — Otomatik koleksiyon kaydı

### Faz 2 — Favori ve Bitirildi İşlemleri

- [ ] LIB-03 — Favori oyunlar
- [ ] LIB-04 — Bitirilen oyunlar

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

Faz 1 geliştirme ve doğrulama işlemleri tamamlandı. Pre-push ve SonarQube kontrolleri kullanıcı tarafından manuel gerçekleştirilecektir. Faz 2'ye geçmem için onayını bekliyorum.
