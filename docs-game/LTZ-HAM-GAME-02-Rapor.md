# LTZ-HAM-GAME-02 — Faz 2 uygulama ve doğrulama raporu

> **Tarihsel not (2026-10-21):** Bu raporda geçen V31–V40 numaraları, o fazın eski migration zincirindeki anlamıyla yazılmıştır. Aynı gün `ltz-dev` ortamı sıfırlandı ve DEV `game_db` yeni V31–V40 zinciriyle (10 migration) kuruldu; aktif eşleştirme [migrations.md](../docs/decisions/migrations.md) ve [yeniden yapılandırma raporundadır](LTZ-HAM-GAME-Migration-Yeniden-Yapilandirma.md). Bu raporlardaki V171–V190 ek aralığı ve "sıradaki V174" ifadeleri o günün kararıdır; ek aralık ve V174–V190 rezervasyonu sonradan kaldırılmıştır.

Tarih: 2026-10-21 · Sorumlu: Hamza · Servis: game-service

**Durum: GAME-19 ve GAME-22 uygulama/test kapsamında tamamlandı; GAME-17 kısmi. Faz 2 bütünü tamamlanmadı. Gerçek detay sağlayıcısı için veri kaynağı kararı ve entegrasyon onayı bekleniyor. Faz 3 ve Faz 4 sonradan kullanıcı onayıyla uygulandı; GAME-17 gerçek sağlayıcı entegrasyonu hâlâ bekliyor.**

## Güncel checklist

- [x] GAME-19 — İlk aktarım, artımlı güncelleme, kalıcı checkpoint ve zamanlayıcı akışı.
- [x] GAME-19 — Geçici hatada sınırlı retry/backoff; kalıcı hatada durma; açık resume komutu.
- [x] GAME-22 — Yerel okumanın sağlayıcı kesintisinden ayrılması.
- [x] GAME-22 — Detay TTL, STALE yanıtı, son başarılı verinin korunması ve yeniden deneme zamanı.
- [x] GAME-22 — Mevcut RestClient timeout/redirect ve Steam çağrı bütçesinin korunması; Retry-After desteği.
- [x] GAME-17 altyapısı — Sağlayıcıdan bağımsız detay sözleşmesi ve iç uygulama komutu.
- [x] GAME-17 altyapısı — Eşzamanlı isteklerde DB kilidiyle tekrar çağrı kontrolü; kısmi veri güncelleme.
- [x] V36 migration, API/DB/ER belgeleri ve otomatik testler.
- [x] Steam, IGDB, RAWG, FreeToGame ve Giant Bomb belge incelemesi.
- [ ] GAME-17 — Kullanıcı tarafından onaylanmış, izinli gerçek detay sağlayıcısı adaptörü.
- [ ] GAME-17 — Gerçek AppID eşlemeleri ve sağlayıcı alanlarının canlı doğrulaması.
- [ ] Detay kaynağının ticari kullanım/atıf koşullarının LTZ için kesinleşmesi ve uygulanması.
- [ ] Gerçek detay sağlayıcısıyla uçtan uca TTL/kota/kesinti doğrulaması.
- [ ] Faz 2'nin bütünü için kapanış (GAME-17). Faz 3 ve Faz 4 için onay sonradan verildi ve uygulandı.

Ana görev dosyasında GAME-17 açık bırakıldı. GAME-19/GAME-22 işaretleri uygulama ve otomatik test sonucudur; canlı sağlayıcıyla tam katalog yüklemesi veya üretim işletim onayı anlamına gelmez.

## GAME-19 — Otomatik senkronizasyon

`CatalogSchedulingConfiguration`, yalnız `game.catalog.sync.enabled=true` olduğunda etkinleşir. Varsayılan kapalıdır; yeni ENV eklenmedi. Uygulama argümanı `--game.catalog.sync.enabled=true` ile açıkça etkinleştirilebilir. Steam adaptörünün mevcut yapılandırması ayrıca tamamlanmış olmalıdır; anahtar yokken ağ isteği yapılmaz.

Başlangıç gecikmesi 30 saniye, turlar arası fixed delay 5 saniyedir. Her tur en fazla 1000 kayıtlık tek sayfa işler. IDLE ilk aktarımı başlatır; COMPLETE son başarılı tur başlangıcından altı saat sonra artımlı aktarımı başlatır. Zamanlayıcı gecikmesi toplam katalog tamamlama süresi garantisi değildir.

Cursor, watermark, işlenen kayıt sayısı ve retry durumu PostgreSQL'de saklanır. Servis yeniden başlasa da RUNNING tur sonraki sayfadan devam eder. Başarılı sayfa ve checkpoint aynı transaction'dadır. Sağlayıcı state satırı, kimlik eşleme ve katalog güncellemesiyle aynı DB kilit sınırını kullanır; birden çok worker aynı sayfayı eşzamanlı uygulamaz. Ağ çağrısı mevcut sayfa transaction'ı içinde olduğundan kilit süresi RestClient timeout'una bağlıdır; çok instance performansı ölçülmedi.

Geçici transport/5xx hatalarında 5, 10, 20, 40 ve 80 saniyelik artan beklemeyle en fazla beş otomatik tekrar vardır. RATE_LIMITED için en az 60 saniye beklenir; geçerli daha uzun Retry-After korunur. Bir günü aşan bekleme otomatik olarak kısaltılmaz; otomatik tekrar durur. Altıncı başarısızlık veya kalıcı hata, FAILED + null nextAttemptAt üretir. Sayfa başarısı ardışık hata sayacını sıfırlar.

`resumeFailed()` yalnız iç uygulama komutudur: cursor, querySince ve tamamlanan sayfaları koruyup hata bütçesini sıfırlar. HTTP yönetim endpoint'i eklenmedi. Anahtar/erişim/şema hatalarında sonsuz retry yapılmaz.

## GAME-17 ve GAME-22 — Detay altyapısı

`GameProvider.Details` açıklama, çıkış tarihi, geliştirici/yayıncı kümesi ve güvenilir sağlayıcı türü için doğrulanmış sınırlar sunar. DETAILS yeteneği varsayılan olarak desteklenmez. **Mevcut gerçek Steam adaptörü DETAILS sunmaz; gerçek zenginleştirme yapılmış gibi gösterilmez.**

`GameDetailRefreshService.refresh(gameId)` HTTP'ye açık olmayan uygulama komutudur. Aktif EXTERNAL kayıtlar için çalışır; INTERNAL kayıtları sağlayıcıyla ezmez. Aynı katalog kilidi ve oyun kilidiyle eşzamanlı tekrarlar sıralanır. Başarılı yenilemeden sonra gelen ikinci istek TTL nedeniyle yeni çağrı yapmaz.

Başarılı detay yanıtı 24 saat önbelleklenir. Eksik skaler alan ve boş şirket kümeleri önceki veriyi silmez; başlık, kanonik gameId ve origin değişmez. Tür yalnız ITEM_TYPE yeteneği varsa güncellenir. Sınırlı alanlar PARTIAL üretir, COMPLETE iddiası yoktur. Payload sınırları geçersizse veri uygulanmaz. Başarısız yenileme önceki açıklamayı/tarihi silmez ve TTL'yi ileri taşımaz.

Kalıcı hata veya tükenen retry bütçesi `resetFailure(gameId)` iç komutuyla sıfırlanabilir. İlk kez denenmiş ya da önceden başarıyla zenginleştirilmiş kayıtların süresi dolduğunda zamanlayıcı her tur en fazla bir detay adayı alır. Hiç talep edilmemiş tüm katalog için detay taraması yapılmaz. Gerçek sağlayıcı yokken aday taraması ve detay ağı çağrısı yoktur.

GET `/api/v1/game/games/{gameId}` her zaman yalnız yerel DB'yi okur; yazma veya otomatik yenileme kuyruğuna alma yapmaz. Ayrı iç komut/zamanlayıcı yenilerken STALE veri okunabilir. Tam bir kullanıcı tetiklemeli stale-while-revalidate akışı henüz yoktur; auth/komut giriş noktası ve gerçek sağlayıcı onayı bekler.

Yeni `freshness.status`: LOCAL, UNSUPPORTED, MISSING, FRESH veya STALE. `fetchedAt`, `expiresAt`, `nextAttemptAt`, `lastErrorCode` güncelliği ve hata durumunu açıklar. Bu son başarılı yanıtın güncelliğidir; tek tek korunmuş eski alanların güncelliği garanti edilmez. Fiyat verisi sunulmadığından eski fiyat güncelmiş gibi gösterilmez. Açıklama düz metindir; frontend'de HTML olarak render edilmemelidir.

## Veri ve API değişiklikleri

- Yeni migration: `V36__add_catalog_retry_and_detail_freshness.sql`. V31–V35 değiştirilmedi.
- `game_provider_sync_state`: failure_count ve next_attempt_at.
- `games`: description, release_date ve details_* güncellik/retry alanları.
- Mevcut detay GET yanıtına nullable açıklama/tarih ve freshness eklendi.
- Mevcut katalog status GET yanıtına failureCount ve nextAttemptAt eklendi.
- `docs/api.md`, `docs/database.md`, `docs/er-diagram.md` güncellendi. Başka servis kaydı/şeması değiştirilmedi.
- Yeni servis, bağımlılık, public endpoint, ENV veya gerçek anahtar eklenmedi.

## Sağlayıcı kararı

[Araştırma ve öneri](LTZ-HAM-GAME-02-Saglayici-Arastirmasi.md) ayrı belgede kayıtlıdır. IGDB yalnız detay kaynağı olarak önerilmiştir; kullanıcı entegrasyon onayı henüz yoktur. Steam katalog indeksi ve gameId değişmez. Kesin eşleme kanıtı, kaynak/atıf izlenebilirliği ve izin koşulları onaydan sonra uygulanacaktır. Test sağlayıcısının başarısı gerçek IGDB/Steam detay entegrasyonu sayılmaz.

## Test sonuçları

`pre-push.cmd game-service`: **83 test, 0 hata, 0 atlanan; JaCoCo satır kapsamı %98,50; BACKEND TEST CHECK PASSED.** Önceki 65 teste ek olarak 18 test senaryosu eklendi. Geçici Testcontainers PostgreSQL 17 üzerinde V31–V36 ve Hibernate validate geçti. Geliştirici Game DB'sinde veri aktarımı/migration çalıştırılmadı.

Doğrulananlar: otomatik ilk/artımlı turlar, altı saat aralığı, kalıcı cursor'dan devam, retry bekleme/bütçe, kalıcı hatada durma ve resume; detay TTL/STALE, eşzamanlı istek tekilleştirme, kesintide mevcut verinin korunması, eksik alan davranışı, INTERNAL/aktif olmayan oyun sınırı, salt okunur HTTP GET, desteklenmeyen yetenek, Retry-After ve opt-in scheduler koşulu.

İlk ek HTTP testi yanlış Spring test paketi nedeniyle derlenmedi; repo ile aynı MockMvc kurulumu kullanılarak düzeltildi. Nihai temiz build/test geçti. İlk uygulama turu 75 test ve %94,66 kapsamla, genişletilmiş ilk tur 83 test ve %98,49 kapsamla geçmişti; yukarıdaki değer Sonar düzeltmeleri sonrasındaki nihai pakettir.

`sonar.cmd scan dev`: **SONAR SCAN PASSED**. SonarQube Access, Backend Reports, Backend Analysis, Quality Gate Backend ve Overall Targets Backend satırlarının tamamı PASS. Tüm backend temiz build'inde 100 test ve ağırlıklı yerel satır kapsamı %91,13 elde edildi. Sonar'ın kendi kapsam hesabı %88,1; yeni kod kapsamı %93,1; tekrar oranı %0; security/reliability/maintainability A. JaCoCo ile Sonar kapsam hesapları aynı metrik gibi sunulmadı.

İlk Sonar taraması 11 yeni bulgu, ikinci tarama kalan bir karmaşıklık bulgusu nedeniyle `SONAR SCAN FAILED` döndü. Transaction sınırı korunarak private yardımcı metotlar ayrıldı, retry ayrıştırması ve test okunabilirliği düzeltildi. Kurallar veya eşikler gevşetilmedi; nihai üçüncü analiz ve ardından servis pre-push geçti.

Sonar logu: `logs/sonarqube-scan-2026-10-20T22-14-48-656Z-49044-51b3ee05.log` (dosya adı UTC). Tam `full dev`/HTTP health ve frontend kontrolleri bu fazda çalıştırılmadı. Çalışan Game Service imajı yeniden kurulmadı; geliştirme DB'sine katalog yüklenmedi. Frontend değişmedi. Büyük katalog yük/p95 ölçümü ve çok instance kota garantisi yapılmadı; Steam bütçesi halen instance içidir ve yeniden başlatmada sıfırlanır. Bunlar tamamlandı sayılmaz. `git diff --check` geçti.

Nihai servis pre-push logu: `logs/pre-push-2026-10-20T22-17-41-825Z-40596-63a80ee8.log`.

## Sonraki adım

IGDB önerisi için kullanıcı kararı beklenir. Onay gelmeden gerçek adaptör/ENV/atıf entegrasyonu ve Faz 3 başlatılmaz. Commit veya push yapılmadı.
