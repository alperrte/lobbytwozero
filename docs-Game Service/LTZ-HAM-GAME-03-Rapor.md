# LTZ-HAM-GAME-03 — Faz 3 uygulama ve doğrulama raporu

Tarih: 2026-10-21 · Sorumlu: Hamza · Servis: game-service

**Durum: Onaylanan yerel katalog kapsamı uygulandı. Altı görev tamamlandı; GAME-04 ve GAME-05 LTZ puanı entegrasyonu nedeniyle kısmi. Faz 3 bütünü 8/8 tamamlandı değildir. GAME-17 gerçek detay sağlayıcısı için önceki onay sınırı korunuyor. Faz 4 başlatılmadı.**

## Görev sırası ve sohbet checklist'i

Görevler GAME-01 → GAME-02 → GAME-03 → GAME-04 → GAME-05 → GAME-06 → GAME-20 → GAME-25 sırasıyla ele alındı; her adımın sonucu sohbetten bildirildi.

- [x] GAME-01 — Birleşik yerel liste, gerçek toplam ve kararlı sayfalama.
- [x] GAME-02 — Ortak detay yanıtı; açıklama, tarih, dil, erken erişim ve mevcut ilişkili metadata; eksik veri korunur.
- [x] GAME-03 — Yerel önek araması, normalizasyon ve literal joker davranışı.
- [ ] GAME-04 — Tür/platform/dil/tarih/erken erişim/editör seçimi filtreleri tamamlandı; **LTZ puanı filtresi entegrasyon bekliyor**.
- [ ] GAME-05 — Ad/tarih/editör sırası sıralamaları tamamlandı; **LTZ puanı sıralaması entegrasyon bekliyor**.
- [x] GAME-06 — Hazırlanmış yerel popüler oyun okuması.
- [x] GAME-20 — Operatör/gerekçe doğrulamalı iç editör komutu ve detay yenileme önceliği.
- [x] GAME-25 — Büyük veri, eşzamanlı okuma, tür temizliği, eksik veri ve sağlayıcı kesintisi testleri; ölçülen p95.

İndirim/fiyat filtresi GAME-24'ün koşullarına bağlıdır ve uygulanmadı. Genel dil kodları vardır; arayüz/ses/altyazı ayrımı GAME-23 kapsamındadır.

## API ve görünürlük

Kullanıcının açık onayıyla yalnız şu public GET uçları eklendi:

- `/api/v1/game/games` — INTERNAL ve EXTERNAL birleşik katalog.
- `/api/v1/game/games/search` — q zorunlu yerel arama.
- `/api/v1/game/games/popular` — yerel editör listesi.

Hepsi yalnız ACTIVE + MAIN_GAME kayıtlarını gösterir. UNKNOWN/DLC/demo ve diğer türler genel keşfe girmez. Eski Steam indeks araması `/api/v1/game/catalog` kendi sözleşmesiyle korunur. Yeni yazma endpoint'i açılmadı; POST ve diğer metotlar deny-by-default/CSRF sınırında kalır. Genel uçlar kişisel veri veya operatör/gerekçe bilgisini döndürmez.

Mevcut detay ve metadata yanıtlarına `languages` ve nullable `earlyAccess` eklendi. Bilinmeyen erken erişim false sayılmaz; boş dil dizisi destek olmadığı iddiası değildir. Kaynak verisi bulunmadığında açıklama/tarih uydurulmaz; detay zenginleştirmesinin gerçek adaptörü GAME-17'de beklemeye devam eder. Genel liste veya detay GET sırasında sağlayıcıya çağrı yapılmaz.

## Sorgu sözleşmesi ve performans tasarımı

Sayfa 0–10000, boyut 1–100; varsayılan 0/20. Arama NFKC + whitespace + Locale.ROOT normalizasyonuyla başlık öneklerini eşler; `%`, `_`, `!` literal olarak kaçırılır. Boş q verilmesi reddedilir; q verilmemiş liste tüm uygun kayıtları kapsar. Ayrı canlı arama veya search-service eklenmedi.

Tür (GENRE), platform, kesin dil kodu, erken erişim, dahil tarih sınırları ve editör listesinde bulunma filtreleri AND ile birleşir. Bilinmeyen alan o filtreye eşleşmez; filtresiz sorguda kayıt kalır. Tür/kategori/etiket kavramları karıştırılmaz. Tarih aralığı ve girdiler sunucuda doğrulanır.

TITLE, RELEASE_DATE ve POPULARITY alan/yön allowlist'ine sahiptir. Tüm sıralamalar gameId ASC ile bağlanır. Null tarih ve editör sırası her iki yönde sonda kalır. POPULARITY DESC küçük editör sırasını önce getirir. Sabit veri kümesinde sayfalar tutarlıdır; farklı HTTP istekleri arasındaki veri değişikliklerinde snapshot devamlılığı garantisi yoktur.

Her istek count, sayfa ve kart ilişkilerini tek REPEATABLE_READ transaction görünümünde okur. Dolu sayfada dört SQL okuması vardır: count; sayfa/kapak; toplu dış referanslar; toplu diller. Kart başına JPA lazy load veya servis HTTP çağrısı yoktur. Sorgular derleme zamanı sabitidir: filtreler `cast(:x as …) is null or …` biçiminde isteğe bağlı koşullardır, sıralama sabit enum'a göre hazır sorgulardan seçilir ve kullanıcı değerleri yalnız named parameter ile bağlanır. SQL metni çalışma zamanında birleştirilmez (Sonar `java:S2077` bulgusu bu yolla giderildi).

Review Service'in API kataloğunda puan sözleşmesi bulunmadığından review_db okunmadı; tahmini puan, boş veriyi sıfır sayma veya sahte API kontratı eklenmedi. `minRating` ve `sort=RATING`, 400 ProblemDetail + `RATING_INTEGRATION_PENDING` döner. Bu bağımlılık kapanmadan GAME-04/05 işaretlenmez.

## Editör listesi ve ön hazırlama

`EditorialCatalogService.replace(orderedIds, actorId, evidence)` iç uygulama komutudur. En fazla 100 benzersiz, mevcut, ACTIVE MAIN_GAME kimliği; operatör REF ve 8–1000 karakterlik gerekçe gerekir. Boş liste açık temizleme isteğidir. Önce tüm seçim doğrulanır; hatalı komut önceki listeyi silmez. Sağlayıcı state kilidi ve oyun kilitleriyle liste aynı transaction'da değiştirilir; sıra tekilliği deferred DB constraint ile korunur.

Komut kendi başına kullanıcı kimliğini doğrulayan bir HTTP/auth katmanı değildir. Faz 4'ün yetkili yönetim girişi doğrulanmış aktörü sağlayacaktır. Son seçim operatörü/gerekçesi saklanır; tam değişiklik geçmişi/audit özelliği iddia edilmez.

Popülerlik ölçütü **EDITORIAL**'dır; `editorialRank` ve `popularityUpdatedAt` açıkça döner. Bu Steam trendi veya LTZ etkileşim skoru değildir. Gerçek geliştirici DB'sine oyun veya editör seed'i yüklenmedi; liste yönetilene kadar boş olabilir.

Editör seçilen EXTERNAL oyunlar, desteklenen detay sağlayıcısı bulunduğunda yenileme adaylarında önceliklidir. Mevcut Steam adaptörü DETAILS sunmadığı için bu öncelik gerçek detay çağrısı başlatmaz. Yeni sağlayıcı entegrasyonu yapılmadı.

## Migration ve belgeler

Yeni `V37__add_catalog_discovery_metadata.sql`: nullable erken erişim; game_languages ve dil indeksi; editör sırası/seçim zamanı/operatör REF/gerekçe; ACTIVE MAIN_GAME başlık/tarih indeksleri ve kapak seçimi indeksi. V31–V36 değiştirilmedi. Game Service aralığında V38–V40 kalır.

`docs/api.md`, `docs/database.md`, `docs/er-diagram.md` ve ana faz checklist'i güncellendi. Başka servis, yeni ENV, bağımlılık veya frontend değişikliği yoktur. Gerçek `.env` dosyalarına yazılmadı.

## Test ve ölçüm

Son `.\pre-push\pre-push.cmd game-service` kontrolü: **94 test, 0 hata/atlanan, %98,61 JaCoCo satır kapsamı ve BACKEND TEST CHECK PASSED.**

GAME-25 ölçümü: Windows 11, Java 25.0.1, Testcontainers PostgreSQL 17, maksimum 3 bağlantı. 100.000 sentetik oyun; INTERNAL/EXTERNAL, ana oyun/UNKNOWN/DLC ve ACTIVE/ARCHIVED dağılımı; dış referanslar, 200.000 terim ilişkisi ve 100.000 dil kaydı. Gerçek kullanıcı verisi yoktur.

Her tekil senaryoda beş ısınma çağrısından sonra 40 örnek; eşzamanlı aramada dört işçiyle 100 örnek alındı. Servis çağrısı süresi transaction ve bağlantı beklemesini içerir; HTTP/Gateway/ağ gecikmesi dahil değildir. Sonuçlar:

- Genel liste p95: **43,92 ms**.
- Yerel önek araması p95: **11,57 ms**.
- Birleşik filtre + tarih sıralaması p95: **175,39 ms**.
- Dört işçiyle eşzamanlı arama p95: **17,55 ms**.

Bu yerel çalışmada 300 ms başlangıç hedefi karşılandı. Üretim, soğuk önbellek, farklı donanım veya tüm sorgu kombinasyonları için garanti değildir. Birleşik filtre ölçümü hedefe diğer senaryolardan yakındır (175,39 ms); üretim dağılımıyla yeniden ölçülmelidir. Ölçüm testi hedef tutmadığında sonucu gizlemez; `targetMet` alanını raporlar. Başarı iddiası bu çalışmanın gerçek değerlerine dayanır.

Otomatik ölçüm dosyası `backend/services/game-service/target/catalog-discovery-performance.json` altında üretilir; clean ile yeniden oluşturulur. Gerçek sağlayıcısı kesinti döndüren test portunun çağrı sayısı yerel sorgularda sıfır doğrulandı. İlk beş sayfada tekrar kimlik olmadığı da kontrol edildi. Mevcut katalog import testleri kısmi hata/checkpoint ve idempotent devamı; diğer testler metadata/tür/görünürlük ve timeout davranışını doğrulamaya devam eder.

Hedefli GAME-02 artımlı derlemesinde bytecode VerifyError görüldü; temiz derleme ve sonraki tam pre-push geçti. Test atlanmadı, kapsam hariç tutması eklenmedi.

**SonarQube:** İlk Faz 3 taraması kalite kapısında başarısız oldu (yeni kodda güvenlik puanı C): `GameDiscoveryRepository` içinde dinamik birleştirilmiş SQL için iki `java:S2077` ve `DiscoveryQuery` içinde iki `java:S4165` (gereksiz atama) bulgusu. SQL sabit sorgulara dönüştürüldü, gereksiz atama kaldırıldı; test atlanmadı, kapsam hariç tutması ve bastırma (`NOSONAR`) eklenmedi. Yeniden tarama: **SONAR SCAN PASSED** (erişim, raporlar, analiz, kalite kapısı ve genel hedefler PASS). Sorgu değişikliğinden sonra performans ölçümü yenilendi; yukarıdaki değerler son ölçümdür. Tam `full dev`/HTTP health ve frontend testleri bu fazda çalıştırılmadı (frontend değişikliği yok). Geliştirme Game DB'sine migration/seed aktarımı veya çalışan imajın yeniden kurulumu yapılmadı.

## Açık işler ve durma sınırı

- Review Service toplu puan sözleşmesi ve entegrasyonu: GAME-04/05 açık.
- Gerçek detay sağlayıcısı seçimi/entegrasyonu: GAME-17 için önceki karar/onay bekleniyor.
- Yetkili editör/yönetim HTTP akışı: Faz 4; bu görevde yapılmadı.
- Faz 4'e geçiş için kullanıcı onayı beklenir. Commit veya push yapılmadı.
