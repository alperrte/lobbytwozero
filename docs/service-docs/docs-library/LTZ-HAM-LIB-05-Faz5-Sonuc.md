# LTZ-HAM-LIB-05 — Arşivlenen oyunlarda kütüphane dayanıklılığı

Tarih: 30 Ekim 2026. Sorumlu: Hamza. Kapsam: LIB-17.

Faz 5: **1/1**. Genel ilerleme: **15/18**.
Faz 4'te gerçek entegrasyon bekleyen LIB-10, LIB-11 ve LIB-14 açık kalır.

## Mevcut durum ve tamamlanan eksik

Faz 3 listelemesi Game unavailable/hata yanıtında koleksiyon satırını koruyordu;
ancak son bilinen oyun özetini kalıcı saklamıyordu. docs/api.md GAME batch sözleşmesi
LIB-17 için Library snapshot kullanımını, database/ER belgeleri game_snapshots tablosunu
öngörüyordu. Bu fazda mevcut public batch akışıyla beslenen Library snapshot deposu eklendi.
Yeni servis API'si veya event üreticisi varsayılmadı; diğer servisler değiştirilmedi.

## Davranış

- Başarılı ve tutarlı Game batch yanıtındaki geçerli, boş olmayan title içeren kartlar
  Library'nin game_snapshots tablosuna kaydedilir. Saklanan metadata client'ın mevcut
  alan/URL doğrulamasından geçmiştir. Gözlem zamanı UTC'dir.
- Snapshot kullanıcıdan bağımsız public metadata'dır: gameId, title, coverUrl, origin,
  metadataCompleteness, gameType, son başarılı status, refreshedAt ve version.
  Kullanıcı kimliği, token, kişisel bayrak veya kullanıcı tarihi içermez.
- Güncel Game yanıtında kimlik unavailable ise kayıt ve kişisel geçmiş korunur;
  son bilinen snapshot varsa bu metadata açıkça LAST_KNOWN olarak döner.
- Timeout/429/HTTP hatası/bozuk veya çelişkili yanıtta status TEMPORARILY_UNAVAILABLE
  olur. Mevcut snapshot silinmez veya hatalı veriyle ezilmez.
- Game kartı erişilebilir fakat title eksik/geçersiz ise önceki kullanılabilir snapshot
  korunur ve fallback olarak sunulur. Status AVAILABLE kalır; metadata kaynağı LAST_KNOWN'dır.
- Hiç snapshot yoksa title/görsel gibi alanlar null, kaynak NONE ve metadataRefreshedAt
  null olur. Sahte oyun adı üretilmez. Güncel geçerli kartın opsiyonel görseli yoksa
  bu kart LIVE/null görselle sunulabilir; her eksik opsiyonel alan için eski veri birleştirilmez.
- Tekrar erişilebilir geçerli kart geldiğinde LIVE metadata döner ve snapshot yenilenir.
  Eski/eşit gözlem zamanı daha yeni snapshot'ı geri alamaz. Çoklu yazımlar UUID sırasıyla
  batch edilir; game_id PK ve atomik upsert tekilliği korur.

Mevcut koleksiyon satırının id/userId/gameId, bayraklar, addedAt/updatedAt ve version
alanları bu okuma/yenilemelerde değişmez. Snapshot aktif oyun doğrulamasını atlatmaz:
POST ekleme ve favori/bitirildi PUT halen Game doğrulaması ister; unavailable için 404,
doğrulama hatasında 503 döner. İşaret geri alma ve açık koleksiyon silme dış katalog
gerektirmez. Kullanıcının açık silme işlemi ve D4 hesap temizliği kişisel satırları
kaldırabilir; ortak public snapshot ve diğer owner'ın koleksiyonu korunur.

## API değişikliği

Yeni endpoint yoktur. Mevcut koleksiyon/profil liste DTO'sundaki game nesnesine iki
ek alan eklendi; eski alanların adları ve endpointler korundu:

- metadataSource: LIVE, LAST_KNOWN veya NONE.
- metadataRefreshedAt: LIVE için batch gözlem zamanı; LAST_KNOWN için saklanan özgün
  snapshot zamanı; NONE için null. Sağlayıcının son güncelleme zamanı olduğu iddia edilmez.

status, Game'e bu istekteki erişilebilirliği; metadataSource, gösterilen metadata'nın
kaynağını anlatır. LAST_KNOWN her durumda metadataPartial=true yapar. Tarih sıralaması
yalnız sayfayı, TITLE sıralaması bütün filtrelenmiş kümeyi değerlendirir.
Snapshot adı TITLE global sırasına katılır; hiç bilinmeyen adlar iki yönde de sonda kalır.
Bu alanlar Library dışındaki frontend dosyalarında uygulanmadı.

## Migration ve transaction sınırları

Yeni dosya: V42__create_game_snapshots.sql. Library V41–V50 aralığı ve mevcut klasör
kontrol edildi; yalnız V41 vardı. Mevcut V41 değiştirilmedi.

UUID PK, timestamptz UTC, version bigint, NOT NULL ve enum/check kısıtları kullanıldı.
Snapshot status=AVAILABLE yalnız son başarılı gözlemi anlatır; güncel ARCHIVED durumunu
gösterdiği iddia edilmez. Başka servis veya kişisel satır tablosuna FK yoktur.
Hibernate ddl-auto=validate korunur; GameSnapshot entity şemayla doğrulanır.

Game HTTP çağrısı yerel transaction dışında kalır. Snapshot okuma ve batch upsert ayrı
kısa transaction'lardır; koleksiyon okumasının transaction'ını uzatmaz. 50 kimlik sınırı,
ilk geçici hatadan sonra kalan HTTP batch'lerini durdurma ve otomatik retry yapmama korunur.
Atlanan batch'lerde de yerel snapshot fallback'i uygulanır. Yeni ENV veya bağımlılık yoktur.

## Otomatik doğrulama

Başlangıç clean verify: **90 PASS**. Son clean verify: **100 test, 0 failure, 0 error,
0 skipped; BUILD SUCCESS**. JaCoCo LINE **434 covered / 4 missed, %99,09**.
Merkezi coverage kapısı PASS; değişen satırlar **340/341, %99,71**.
Checkstyle/PMD raporlarında **0 ihlal**. Kalite manifest/baseline veya kural bastırması değiştirilmedi.

CatalogResilienceTests, gerçek PostgreSQL/Flyway/Hibernate ve RSA/JWKS API sınırlarıyla,
gerçek GameServiceClient'ı MockRestServiceServer'a bağlayarak şu senaryoları sınar:

- Available → unavailable → timeout → eksik title → yeniden available; bütün kişisel
  satır alanlarının aynı kalması, snapshot'ın yalnız geçerli metadata ile yenilenmesi.
- İlk kez görülen unavailable oyunda NONE/null fallback ve kişisel bayrakların korunması.
- Çelişkili/yabancı gameId içeren batch'in snapshot'ı zehirleyememesi; snapshot varken
  unavailable oyun için ekleme/favori/bitirildi PUT işlemlerinin yine reddi.
- Katalog çağrısı yapmadan işaret geri alma, sayaç güncellemesi, idempotent kaldırma ve
  başka owner verisinin korunması.
- Daha eski gözlem ve eksik title ile son bilinen metadata'nın ezilememesi.
- 16 eşzamanlı snapshot yenilemesinde tek satır ve en yeni gözlemin korunması.
- Fallback adlarının bütün kümede ASC/DESC sıralanması ve snapshot'sız oyunun sonda kalması.
- Gerçek Flyway V41→V42 yükseltmesinde önceden oluşturulmuş koleksiyon satırının bütün
  alanlarıyla korunması; boş snapshot tablosu ve version CHECK doğrulaması.
- D4 hesap temizliğinde yalnız hedef kişisel satırın silinmesi, ortak public snapshot
  ve diğer owner'ın kaydının aynı kalması.

LibraryGameSummariesTest ayrıca 121 kimlikte ilk HTTP hatasından sonra atlanan
50/50/21 batch'lerin tümünde LAST_KNOWN fallback'ini, özgün snapshot zamanını ve tek HTTP
denemesini doğrular. Faz 1–4 testleri aynı koşuda başarılıdır.

Bu testler Game Service'te gerçek oyun arşivlendiği anlamına gelmez. Game HTTP yanıtları
simüledir; diğer servislerde arşivleme, metadata silme veya fixture oluşturma yapılmadı.

```powershell
mvn -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml `
  -f backend/pom.xml -pl services/library-service -Pci `
  '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5542/library-phase1-test' `
  '-Dspring.datasource.username=ltz_library_phase1_test' clean verify
```

Host Maven, ltz-dev PostgreSQL içindeki ayrı Library test DB/rolünü kullandı. Testler
kalıcı library_db'yi temizlemez. Ham çıktılar Library target/verification altındadır;
bu fazda docs-library içine yalnız Markdown raporları eklendi. Test DB/rolü sahiplik
doğrulaması sonrası kaldırıldı; kalan DB ve rol sayıları 0. Tekrar koşmadan önce izole
test DB/rolü kurulmalıdır. Pre-push ve SonarQube çalıştırılmadı.

## ltz-dev doğrulaması

Standart Library Dockerfile ile ltz/library-service:dev derlendi; yalnız Library,
`docker compose -p ltz-dev up -d --no-deps --no-build library-service` ile yenilendi.
Çalışan konteynerin image ID'si son etiketle eşleşti.

library_db üzerinde V41 ve V42 success=true. V41 değiştirilmedi; repair/baseline,
volume sıfırlama veya mevcut tablo temizliği yapılmadı. Yenileme öncesi koleksiyon
sayısı 0'dı; sonrasında 0 kaldı. Yeni public snapshot tablosunda 0 satır ve 0 FK doğrulandı.
Flyway/Hibernate doğrulaması ve uygulama açılışı başarılıdır.

**34/34 HTTP PASS:** Library/Gateway profil listesi/sayacında anonim, geçersiz Bearer,
sahte owner/ADMIN başlığı ve yalnız cookie 401; üç iç yolda anonim/geçersiz Bearer/sahte
servis başlığı 401; Gateway üzerinden bu üç iç yol 404. Library/Gateway/Auth/Game health
200. Gerçek Game ve Gateway public batch API'si rastgele bulunmayan kimlik için 200 ve
aynı unavailable sözleşmesini döndürdü. Diğer servislerde kayıt veya arşivleme yapılmadı.
Bu kontroller oturum sahibi pozitif snapshot/arşivleme E2E'sinin yerine geçmez.

## Sınırlar

Mevcut Game batch API'si missing/ARCHIVED/MERGED nedenlerini ayırmaz ve yeni kanonik
kimlik sağlamaz. Library otomatik kimlik taşıması/birleştirme yapmaz; eski gameId ile
kişisel geçmişi korur. GameCatalogChanged event tüketimi eklenmedi; gerçek event
sözleşmesi hazır değildir. Snapshot mevcut public batch yanıtlarıyla beslenir.

Snapshot görüntüleme fallback'idir; erişilebilirlik doğrulamasının veya gizlilik izninin
yerine geçmez. Yeni paylaşılan/profil okumaları Faz 4 erişim kapısından geçmeye devam eder.
Ortak snapshot'a erişim için ayrı public endpoint açılmadı. Dağıtık atomiklik veya Game
sağlayıcısının sürüm sırası garantisi yoktur; refreshedAt Library'nin gözlem zamanıdır.
Snapshot için otomatik TTL/temizlik politikası eklenmedi. Gerçek Game arşivleme ve
oturum sahibi pozitif canlı E2E **NOT VERIFIED**.

## Dosyalar

- V42__create_game_snapshots.sql ve GameSnapshot entity.
- GameSnapshotRepository/GameSnapshotService: sınırlı toplu okuma, atomik upsert ve transaction sınırları.
- GameMetadataSource ve genişletilmiş LibraryGameResponse.
- LibraryGameSummaries/LibraryCollectionService: snapshot fallback ve kısmi metadata hesabı.
- LibraryApiTest.CatalogResilienceTests ve LibraryGameSummariesTest.
- docs/api.md yalnız LIBRARY bölümü; Faz 5 sonuç raporu ve ayrı PR açıklaması.

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

- [ ] LIB-10 — Profil entegrasyonu
- [ ] LIB-11 — Gizlilik kontrolleri
- [ ] LIB-14 — Recommendation entegrasyonu
- [x] LIB-18 — Yetki, gizlilik ve tutarlılık otomatik testleri

Faz 5 — Arşivlenen Oyunlarda Dayanıklılık

- [x] LIB-17 — Katalog değişikliklerine dayanıklılık
