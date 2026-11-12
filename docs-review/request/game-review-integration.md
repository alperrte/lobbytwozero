# GAME–REVIEW entegrasyonu

Son güncelleme: 12 Kasım 2026. Bu belge yalnız **açık işleri ve beklentileri** içerir; tamamlanmış entegrasyonlar bilerek yazılmamıştır.

- Talep eden / hedef: `review-service` — Hamza ↔ `game-service` — Hamza.
- Kesin geliştirme emri değildir; Game ve Review kodu ayrı kapsamlarda değiştirilir.

## 1. Açık: canlı doğrulama

Puan okuma entegrasyonu çalışır durumdadır; yalnız gerçek kullanıcı oturumuyla canlı uçtan uca doğrulama ve üretim ENV dağıtımı yapılmamıştır (NOT VERIFIED).

## 2. Review frontend'in Game'den kullandıkları ve beklentileri

Review frontend'i Game'in **herkese açık, salt okunur** uçlarını Gateway üzerinden, çerezsiz (`credentials: "omit"`) çağırır; çerez gönderilirse Gateway durum değiştiren istek sayıp CSRF ister.

| Kullanım | Game ucu | Okunan alanlar |
| --- | --- | --- |
| Oyun adı/kapağı (oyun, yazma, detay, istatistik, "incelemelerim") | `POST /api/v1/game/games/batch` `{gameIds:[≤50]}` | `items[].gameId`, `title`, `coverUrl`, `externalReferences[provider=STEAM]`; `unavailable[]` |
| Merkezde oyun listesi (aramasız) | `GET /api/v1/game/games?page&size&sort=POPULARITY&direction=DESC` | `items[].game.*`, `totalElements` |
| Merkezde oyun arama | `GET /api/v1/game/games/search?q&page&size` | aynı |

**Beklentiler**

1. **Geriye uyum:** Yukarıdaki alanlar (özellikle kanonik `gameId`, `title`, `coverUrl`, `unavailable`) kırıcı değişmemeli; değişecekse `docs/api.md` GAME kaydında duyurulmalı. `batch` üst sınırı 50'nin altına inmemeli.
2. **Kanonik kimlik:** Review yalnız kanonik LTZ `gameId` kullanır; Steam AppID kabul etmez.
3. **Kesinti ve eksik kart:** `unavailable` veya yanıtsız oyun için Review incelemeleri gizlemez; başlık yerine "Oyun" yazar ve "tekrar dene" sunar. Game'den "oyun yok" (404) ile "geçici kesinti" (5xx) ayrımı korunmalı.
4. **Review'ın Game'e çağrıları:** İnceleme, puan ve tavsiye **yazma** istekleri oyunun varlığını `POST /api/v1/game/games/batch` ile doğrular (transaction dışında, yeniden deneme yok): oyun `ACTIVE` değilse Review 404, Game erişilemezse 503. Okuma uçları Game'e bağlı değildir. `batch` ucunun bu amaçla düşük gecikmeli ve herkese açık kalması beklenir.
5. **Puan tutarlılığı:** Game listelerindeki LTZ puanı projeksiyondan gelir ve tarama periyodu kadar (varsayılan 5 dk) gecikebilir; Review ekranları canlı okur. Kısa süreli fark beklenen davranıştır (`ratingsSyncedAt` gecikmeyi gösterir). Game'de `UNAVAILABLE` ile `UNRATED` ayrımı korunmalı; 0 puan gösterilmemeli.

### Açık ürün sorusu — +18 ve kaldırılmış oyunlar
`GET /games` ve `/games/search` varsayılan olarak yetişkin içerikli (`adult=true`) ve mağazadan kaldırılmış (`delisted`) oyunları gizler; `batch` ve doğrudan bağlantı etkilenmez. Sonuç: bu oyunların Review sayfaları doğrudan bağlantıyla ve "incelemelerim" ile açılır, fakat Review merkezinin aramasında **bulunamaz**. Review frontend'i `includeAdult` göndermez. Merkezde bu oyunların nasıl aranacağı (kullanıcı tercihi/yaş kapısı) Game ve ürün sahibinin kararıdır.

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
3. **Katılımcı uçları (Gateway'e açılmaz):** `POST /internal/v1/game-identity/operations/{operationId}/prepare`, `…/finalize`, `GET …/operations/{operationId}`. Kalıcı operasyon/engel kaydı için yeni migration gerekir: Review aralığı V51–V60, **V58–V60 boş**; numara onayla seçilir.
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

## 4. Game frontend'inden Review'a bağlantı (öneri, AÇIK)

Review frontend'i bağımsız sayfalarla tamamlandı; Game frontend'ine dokunulmadı. Game sayfasının sahibine öneri:

1. Oyun detayından Review'a bağlantı: `/reviews/games/[gameId]` (TR `/incelemeler/oyun/[gameId]`). Review sayfaları oturum ister; oturumsuz kullanıcı girişe yönlenir ve dönüşte hedefe gelir.
2. LTZ puan gösteriminden Review istatistik sayfasına bağlantı (`/reviews/games/[gameId]/statistics`); iki kaynağın (Game projeksiyonu / Review canlı) gecikme notu §2.5 ile uyumlu anlatılmalı.
3. Game sayfasında inceleme listelenmeyecekse Review UI'ı kopyalanmamalı; tek kaynak Review sayfalarıdır.
4. Review sayfaları `noindex`'tir; Game metadata'sına inceleme/puan içeriği taşınmaz.
