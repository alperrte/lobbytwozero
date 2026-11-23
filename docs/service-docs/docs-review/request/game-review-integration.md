# GAME–REVIEW entegrasyonu

Son güncelleme: 12 Kasım 2026 (Game cevabı sonrası). Bu belge yalnız **açık işleri ve beklentileri** içerir; tamamlanmış entegrasyonlar bilerek yazılmamıştır.

- Talep eden / hedef: `review-service` — Hamza ↔ `game-service` — Hamza.
- Kesin geliştirme emri değildir; Game ve Review kodu ayrı kapsamlarda değiştirilir.

## 1. Açık: canlı doğrulama

Game cevabı (12 Kasım 2026): Game tarafında eksik yok; doğrulama Review merge ve DEV dağıtımı sonrası birlikte yapılır. Puan okuma entegrasyonu çalışır durumdadır; **gerçek kullanıcı oturumuyla Review→Game (`batch` doğrulaması, kart okuma) ve Game→Review (puan projeksiyonu) canlı uçtan uca doğrulaması yapılmadı (NOT VERIFIED)**, üretim ENV dağıtımı da yapılmadı. Canlı doğrulama adımları Game'in cevabındaki §3.5 listesidir (kayıt, merkez listesi, puan/tavsiye/inceleme yazma, olmayan UUID → 404, Game kapalıyken yazma → 503 ve okuma ekranlarının açık kalması, 5 dk sonra projeksiyon, `UNRATED`/`UNAVAILABLE` ve 0 gösterilmemesi). Sonuçlar `REVIEW_SERVICE_PROGRESS.md`'ye tarihli yazılır.

## 2. Review frontend'in Game'den kullandıkları ve beklentileri

Review frontend'i Game'in **herkese açık, salt okunur** uçlarını Gateway üzerinden, çerezsiz (`credentials: "omit"`) çağırır; POST `batch`'te çerez gönderilirse Gateway durum değiştiren istek sayıp CSRF ister. **Tek istisna (12 Kasım 2026):** yaşı doğrulanmış kullanıcı merkezde "+18 oyunları göster"i işaretlerse `GET /games` ve `/games/search` `includeAdult=true` ile **çerezli** (`credentials: "include"`) gider; GET güvenli yöntem olduğundan Gateway CSRF istemez (canlı doğrulandı, aşağıya bakın).

| Kullanım | Game ucu | Okunan alanlar |
| --- | --- | --- |
| Oyun adı/kapağı (oyun, yazma, detay, istatistik, "incelemelerim") | `POST /api/v1/game/games/batch` `{gameIds:[≤50]}` | `items[].gameId`, `title`, `coverUrl`, `externalReferences[provider=STEAM]`; `unavailable[]` |
| Merkezde oyun listesi (aramasız) | `GET /api/v1/game/games?page&size&sort=POPULARITY&direction=DESC` | `items[].game.*`, `totalElements` |
| Merkezde oyun arama | `GET /api/v1/game/games/search?q&page&size` | aynı |

**Beklentiler**

0. **`batch` 429 ve kova (Game cevabı):** `batch` istemci başına jeton kovasıyla sınırlıdır (ani yük 100, sürekli 20 istek/sn); aşımda `429` + `Retry-After` + `RATE_LIMITED`. Review frontend'inin Gateway üzerinden gelen tüm tarayıcı trafiği (Library ile birlikte) tek kovayı paylaşır. Review frontend'i sayfa başına tek `batch` isteği atar; 429'da kartlar başlıksız ("Oyun") açılır, "tekrar dene" `Retry-After` dolana kadar kapalıdır ve inceleme/puan verisi gizlenmez (uygulandı: `RetryButton`). Review backend'i Game'i doğrudan çağırdığı için kendi kovasına sahiptir; `GameServiceClient` 429'u 503'e çevirir ve yeniden denemez. Kovanın tarayıcı trafiği için nasıl bölüneceği (Gateway'in istemci IP'sini güvenilir iletmesi ya da kova değerleri) Game ve Gateway sahibinin işidir; Review'ı bloklamaz.

1. **Geriye uyum:** Yukarıdaki alanlar (özellikle kanonik `gameId`, `title`, `coverUrl`, `unavailable`) kırıcı değişmemeli; değişecekse `docs/api.md` GAME kaydında duyurulmalı. `batch` üst sınırı 50'nin altına inmemeli.
2. **Kanonik kimlik:** Review yalnız kanonik LTZ `gameId` kullanır; Steam AppID kabul etmez.
3. **Kesinti ve eksik kart:** `unavailable` veya yanıtsız oyun için Review incelemeleri gizlemez; başlık yerine "Oyun" yazar ve "tekrar dene" sunar. Game'den "oyun yok" (404) ile "geçici kesinti" (5xx) ayrımı korunmalı.
4. **Review'ın Game'e çağrıları:** İnceleme, puan ve tavsiye **yazma** istekleri oyunun varlığını `POST /api/v1/game/games/batch` ile doğrular (transaction dışında, yeniden deneme yok): oyun `ACTIVE` değilse Review 404, Game erişilemezse 503. Okuma uçları Game'e bağlı değildir. `batch` ucunun bu amaçla düşük gecikmeli ve herkese açık kalması beklenir.
5. **Puan tutarlılığı:** Game listelerindeki LTZ puanı projeksiyondan gelir ve tarama periyodu kadar (varsayılan 5 dk) gecikebilir; Review ekranları canlı okur. Kısa süreli fark beklenen davranıştır (`ratingsSyncedAt` gecikmeyi gösterir). Game'de `UNAVAILABLE` ile `UNRATED` ayrımı korunmalı; 0 puan gösterilmemeli.

### +18 ve kaldırılmış oyunlar — KARAR VERİLDİ, UYGULANDI (12 Kasım 2026)
Yaş kapısı Game'de kuruldu: `includeAdult=true` yalnız Auth'un imzaladığı access token'daki boolean `adult_verified=true` iddiasıyla uygulanır; yetkisizse sessizce yok sayılır ve yanıtta `includeAdultApplied=false` döner (hata yok). Yalnız `GET /games` ve `/games/search` kapıdadır; `batch`, detay, `/games/popular`, Library ve Review okumaları etkilenmez. Kaldırılmış (`delisted`) oyunlar doğrulanmış kullanıcıda bile listede yoktur.

| # | Karar | Seçim |
| --- | --- | --- |
| R1 | Merkezde +18 arama | Evet: yalnız yaşı doğrulanmış kullanıcıya "+18 oyunları göster" kutusu |
| R2 | Doğrulanmamış kullanıcıya ipucu | Evet: "Yaşını doğrula" bağlantısı (`/profile/settings`) |
| R3 | Sonuç kartında rozet | Evet: `adult=true` ise "+18" |
| R4 | Kaldırılmış oyunlar | Aramada görünmez; doğrudan bağlantı ve "incelemelerim" çalışır |
| STEP_UP | `STEP_UP_REQUIRED` alan kullanıcı | Review'da ayrı hata yok; kutu yalnız `adultVerified === true` iken etkin, değilse bağlantı Auth'un yaş bölümüne gider |

**Review frontend'inde uygulananlar:** `gameLookup.search(..., includeAdult)` yalnız `true` iken `includeAdult=true` ekler ve yalnız o durumda `credentials: "include"` kullanır (`card`/`cards` `batch` hâlâ çerezsiz); `hubQueryFrom` `includeAdult`'ı yalnız tek ve tam olarak `"true"` değerinde kabul eder; `useAdultVerified()` (`useSession` + `me.adultVerified === true`); merkezde kutu, doğrulanmamışta açıklama ve bağlantı, `includeAdultApplied === false` ipucu ve "+18" rozeti. Gösterimde esas alınan **sunucu yanıtıdır** (`includeAdultApplied`), yerel `me` değil: doğrulama geri alınırsa iddia ≤60 sn gecikebilir. Doğrulanmamış kullanıcıda `includeAdult` sunucuya gönderilmez, URL'deki seçim korunur.

**Doğrulama:** birim testleri (`review-api`, `review-schema`, `review-hub`) ve Playwright (`review.spec.ts` 16/16, iki yeni +18 senaryosu) geçti. Game tarafında girişli uçtan uca canlı doğrulama yapıldı (yaş beyanı → refresh → `adult_verified: true` → `includeAdultApplied=true`; geri alma sonrası claim kalktı). Çerezli GET'in Gateway'de CSRF istememesi canlı doğrulandı. **NOT VERIFIED:** Review merkezinin tarayıcıda gerçek oturumla elle denenmesi.

## 3. GAME-26 kimlik taşıma/ayırma — Game'in Review'dan beklediği (AÇIK)

Kaynak: [GAME-26 sözleşme taslağı](../../docs-Game%20Service/GAME-26-Servis-Sahipleri-Entegrasyon-Sozlesmesi.md) (7 Kasım 2026, **ÖNERİ / PLANNED**) ve [0002 kararı](../../docs/decisions/0002-oyun-dis-kimlik-duzeltme.md). İş kuralı onaylı: yalnız **referanssız** oyunlarda taşıma/ayırma. Review beş katılımcıdan biridir.

**Review tarafı: NOT IMPLEMENTED.** Aşağıdaki, Review'ın sorumluluk analizidir; kodlama için ayrıca kullanıcı onayı gerekir.

### 3.1 Review'ın referansları

| Tablo | İlişki | Not |
| --- | --- | --- |
| `game_ratings` (V51) | `game_id` | kullanıcı/oyun başına tek satır |
| `reviews` (V52) | `game_id` | `VISIBLE` ve `DELETED`; silinmiş satır `body=NULL` ama `game_id`'yi saklar |
| `game_recommendations` (V53) | `game_id` | kullanıcı/oyun başına tek satır |
| `review_likes` (V55) | doğrudan yok | `reviews` üzerinden; ayrı sayılmaz |

Türetilmiş önbellek yoktur. Game'in puan projeksiyonu Game'dedir; taşımadan sonra Game yeniden tarar.

### 3.2 Review'ın yapması gerekenler (kabul edilirse)

1. **Korunacak yazma yolları:** `RatingWriter` (UPSERT), `RecommendationWriter` (UPSERT), `ReviewWriter.create`. PATCH/DELETE/beğeni yeni oyun ilişkisi doğurmaz; hesap silme referansı yalnız azaltır.
2. **Ortak kilit:** `AccountWriteLock` örüntüsüyle oyun UUID'si için transaction kapsamlı advisory kilit; prepare ve üç yazıcı aynı kilidi UUID sırasıyla alır; engelli oyunda yazma 409.
3. **Katılımcı uçları (Gateway'e açılmaz):** `POST /internal/v1/game-identity/operations/{operationId}/prepare`, `…/finalize`, `GET …/operations/{operationId}`. Kalıcı operasyon/engel kaydı için yeni migration gerekir: **Review'ın V51–V60 aralığında boş numara kalmadı** (V58 rapor, V59 outbox, V60 moderasyon kullanıldı). Numara uydurulmaz; ekip kararı gerekir (AGENTS §4).
4. **Kimlik:** `svc+jwt`, `aud=review-service`, `sub=svc:game-service`, `scope` içinde `game-identity:prepare|finalize|read`. `rating-summaries:read` bunlara yetki vermez, tersi de.
5. **Karar sorgusu:** Belirsizlikte `GET {GAME_SERVICE_URL}/internal/v1/game-identity/operations/{operationId}/decision`; token için `AUTH_REVIEW_SERVICE_CLIENT_SECRET` (**ENV onayı gerekir**). PENDING/404/erişilemezse engel tutulur.
6. **Hata kodları:** `409 GAME_REFERENCES_EXIST`, `GAME_IDENTITY_OPERATION_CONFLICT`, `GAME_IDENTITY_DECISION_CONFLICT`, `GAME_IDENTITY_OPERATION_IN_PROGRESS`; `503 GAME_IDENTITY_COORDINATION_UNAVAILABLE`. **Not:** Review hata yanıtları artık makine-okur `code` alanı taşıyabilir (`ReviewException(status, code, message)`; bugün yalnız rapor hatalarında: `REPORT_ALREADY_EXISTS`, `REPORT_OWN_REVIEW`). Review'ın **diğer** 409'ları (ör. "bu oyun için zaten incelemen var") hâlâ `code` taşımaz; GAME-26 hataları eklenirken `GAME_IDENTITY_OPERATION_IN_PROGRESS` 409'u bu mekanizmayla ayrıştırılacak. Review frontend'i `ApiHttpError.code` ile ayırmaya hazırdır (bugün oluşturma 409'unu "zaten incelemen var" diye yorumlar, bu yüzden kod gelmeden GAME-26 409'u yanlış yorumlanır).

### 3.3 Review için açık kararlar
1. **`DELETED` incelemeler referans mı?** Sözleşme "soft-delete kayıt yok kabul edilmez" der. Öneri: sayılır (güvenli taraf); sonuç: yalnız silinmiş incelemesi olan oyun taşınamaz. Alternatif: taşıma sırasında silinmiş satırın `game_id`'sini de taşımak. Ürün kararı.
2. **Hesap silme ile kilit sırası** deterministik tanımlanmalı; silme referansı azalttığı için engel altında çalışmaya devam edip etmeyeceği testle kanıtlanmalı.
3. **Saklama:** terminal operasyon/tombstone kayıtlarının silinme politikası.

### 3.4 Diğer taraflardan beklenenler
- **[Game]** Koordinatör (kalıcı operasyon, önce ABORTED, finalize teslim kuyruğu, kurtarma), `…/decision` ucu ve ADMIN akışı. Review hazır olana kadar `FOREIGN_REFERENCE_SAFETY_UNVERIFIED` koruması kapalı kalmalı; eksik/bilinmeyen katılımcı `0 referans` sayılmaz.
- **[Auth]** `game-identity:prepare|finalize|read|decision:read` Auth'ta **uygulanmış ve testlidir** (Auth cevabı, 12 Kasım 2026); `docs/api.md`'deki "taslak" ifadesi eskidir ve Auth tarafından düzeltilecek. Review bu kapsamlara bağımlı kod yazabilir (ENV onayı ve kullanıcı kararı sonrası).

### 3.5 Kabul kriterleri (Review payı)
Prepare ile eşzamanlı puan/tavsiye/inceleme yazımı birlikte başarı veremez; tekrarlı/ters sıralı prepare–finalize; önce abort sonra gecikmiş prepare; restart sonrası kalıcı engel; PENDING/404 karar sorgusunda engelin açılmaması; yanlış subject/audience/scope ve kullanıcı token'ı reddi; gerçek Auth+Game ile ortak kabul. Bugün hiçbiri PASS değildir.

## 4. Game frontend'inden Review'a bağlantı

**Yapıldı (12 Kasım 2026, Review PR #110 main'e girdikten sonra):** Game detay sayfasında "İncelemeleri gör" / "See reviews" bağlantısı (`Game.reviewsLink`) `/reviews/games/[gameId]` rotasına gider (`docs/api.md` GAME bölümüne not düşüldü). Rota adları sözleşmedir ve korunmalıdır: `/reviews/games/[gameId]` (TR `/incelemeler/oyun/[gameId]`) ve `/reviews/games/[gameId]/statistics`.

**Açık:**
1. LTZ puan gösteriminden Review istatistik sayfasına bağlantı (`/reviews/games/[gameId]/statistics`). Game detayında LTZ puanı gösterilmediği için şimdilik uygulanamaz; kartlardaki puana bağlantı vermek ürün kararıdır. İki kaynağın (Game projeksiyonu / Review canlı) gecikme notu §2.5 ile uyumlu anlatılmalıdır.
2. Canlı doğrulamada oturumsuz kullanıcının girişe yönlenip dönüşte hedefe gelmesi denenecek (§1).
