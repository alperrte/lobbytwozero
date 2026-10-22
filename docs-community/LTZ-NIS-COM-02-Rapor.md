# LTZ-NIS-COM-02 — Topluluk Akışı, Arama ve Profil Geçmişi

Servis: `community-service` · Sorumlu: Nisa · Branch: `feature/community-service` · Faz: Faz 2
Durum: **FAZ 2 TAMAMLANDI — onay bekliyor (4 tam + 2 kısmi / 6)** · Faz 1 commit'i: `3837c47`

Nihai faz raporu. Faz 3 kullanıcı onayı olmadan başlatılmaz.

## Task durumu

| Task | Durum |
| --- | --- |
| COM-04 — Gönderi listeleme | Tamamlandı |
| COM-05 — Gönderi detayları | **Kısmi** (yorum/etkileşim Faz 3) |
| COM-11 — Gönderi arama | Tamamlandı |
| COM-12 — Gönderi filtreleme | Tamamlandı |
| COM-13 — Gönderi sıralama | **Kısmi** (popülerlik sayaçları Faz 3) |
| COM-15 — Kullanıcı gönderi geçmişi | Tamamlandı |

## Alınan kararlar (kullanıcı onaylı)

- **Popülerlik / sayaçlar:** `posts` tablosuna `like_count` ve `comment_count` kolonları eklenecek (varsayılan 0) ve `popularity_score = like_count + comment_count` hesaplanacak; `sort=POPULAR` bunu kullanacak. Sayaçları Faz 3 (COM-06/07) doldurur; o zamana kadar POPULAR fiilen yenilik sırasına düşer.
- **Kategori:** sabit liste `GENERAL, GUIDE, QUESTION, SCREENSHOT`; oluşturmada **opsiyonel**, verilmezse `GENERAL`; düzenlemede değiştirilebilir. (Önceki cevap "kategori yok" idi; kullanıcı düzeltti.) `posts.category` varchar + CHECK; filtre ve oluşturma/düzenleme isteği COM-12'de eklenecek.
- **Okuma erişimi:** akış, detay, arama ve profil geçmişi (GET) **misafire açık**; yalnız `VISIBLE` gönderiler. Yazma uçları kimlik ister.
- **Tamamlanma:** COM-05 (yorumlar/etkileşimler) ve COM-13 (popülerlik) Faz 3 verisine bağlı olduğundan Faz 2'de **kısmi** işaretlenir ve Faz 3 sonunda kapatılır.
- Migration: Faz 2 şeması tek dosyada (`V93__add_post_feed_and_search_support.sql`, commit edilene kadar genişler) — V91–V100 aralığını korumak için. Kalan: V94–V100.

## COM-04 — Teknik özet

- Endpoint: `GET /api/v1/community/posts?limit&cursor` → `PostPageResponse{items, nextCursor}` (`docs/api.md` kaydı eklendi). Public; yalnız `VISIBLE`.
- **Keyset sayfalama** (`created_at desc, id desc`), `limit+1` okuma, COUNT sorgusu yok; opak, sürümlü (`v1`) imleç (`PostCursor`); bozuk imleç 400.
- Sayfadaki görsel bilgileri tek sorguda (kapalı projeksiyon `PostImageMeta`, içerik baytı yüklenmez) → N+1 yok.
- Sorgu `JpaSpecificationExecutor` + `PostSpecifications` ile parametreli; sıralama alanları sabit (SQL birleştirme yok). `PostSearchCriteria` (application/port) kalıcılık katmanından bağımsız kriter kaydı.
- Güvenlik: yalnız `GET /api/v1/community/posts` anonim açıldı; POST/PATCH/PUT/DELETE ve diğer GET'ler kimlik ister.
- Migration: `V93__add_post_feed_and_search_support.sql` → `ix_posts_visible_created_at` (kısmi indeks, `status='VISIBLE'`).
- Yazar bilgisi yalnız `authorId` (user-service sözleşmesi yok).

Test: toplam 160 test geçti (yeni: `PostCursorTest` 5, `PostFeedServiceIntegrationTest` 9, `PostControllerTest` +5); JaCoCo satır kapsamı %97,92. `.\pre-push\pre-push.cmd community-service` → `BACKEND TEST CHECK PASSED`.

## COM-05 — Teknik özet (KISMİ)

- Endpoint: `GET /api/v1/community/posts/{postId}` → `PostViewResponse` (public; misafir VISIBLE görür, sahibi kendi HIDDEN gönderisini de görür; silinmiş/başkasının gizli gönderisi 404). `docs/api.md` kaydı **IN PROGRESS** olarak eklendi.
- **Tamamlananlar:** içerik, yazar (`authorId`), tarih, oyun bağlantısı, görseller ve etkileşim **sayaçları** (`likeCount`, `commentCount`).
- **Eksik (Faz 3'e bağlı, kullanıcı kararı: kısmi işaretle):** gönderiye ait **yorumların** ve beğeni/repost **etkileşim ayrıntılarının** görüntülenmesi. Yorum listesi COM-07'de ayrı uç olarak gelecek; sayaçlar şimdilik hep 0. COM-05, Faz 3 sonunda gerçek veriyle doğrulanıp kapatılacak.
- Yazarın görünen adı/avatarı yok (user-service sözleşmesi yok); yalnız `authorId`.
- **Misafir görsel okuma:** akış/detay misafire açıldığı için `GET /posts/{id}/images/{imageId}` de VISIBLE gönderiler için misafire açıldı (aksi halde misafirde görseller kırık görünürdü). Bu, "misafir okuyabilsin" kararının sonucu olarak yapıldı; istenmezse geri alınabilir.
- Migration: `V93` içine `like_count`, `comment_count` (varsayılan 0, `>= 0` CHECK) eklendi.
- Kod: `CurrentUserPort.findUserId()` (misafir için boş), `PostFeedService.getPost`, `Post.likeCount/commentCount`.

Test: 173 test geçti, JaCoCo %98,20 (yeni: `SecurityContextCurrentUserTest` 4, detay/görünürlük entegrasyon testleri, controller detay testleri).

## COM-11 — Teknik özet

- Parametre: `GET /api/v1/community/posts?q=...` (aynı akış ucu; `docs/api.md` güncellendi). Servis içi arama, ayrı search-service yok.
- Doğrulama: `q` kırpılır; boş = arama yok; 1 karakter 400 (`InvalidPostQueryException`); en çok 100 karakter, kontrol karakteri (NUL dahil) 400.
- Sorgu: `lower(body) like :pattern escape '\'`; desen Java tarafında ASCII-güvenli küçük harfe çevrilir ve `\`, `%`, `_` kaçırılır (`PostSpecifications.containsPattern`), değer parametre olarak bağlanır (SQL birleştirme yok). Arama, `VISIBLE` filtresi ve keyset imleciyle birlikte çalışır (gizli/silinmiş gönderi aranamaz).
- Migration: `V93` içine `create extension if not exists pg_trgm` ve `ix_posts_body_trgm` (`gin (lower(body) gin_trgm_ops)`). Eklenti süper kullanıcı/DB sahibi yetkisi gerektirir (dev/test'te uygun; production için DB sahibine bildirilmeli).
- **Sınırlama:** ASCII dışı harflerin büyük/küçük dönüşümü DB locale'ine bağlı; dev/test PostgreSQL alpine imajı `C` locale'inde olduğundan büyük `Ç` ↔ küçük `ç` eşleşmez (küçük harfli Türkçe karakterler birebir eşleşir). Kalıcı çözüm için DB'nin `C.UTF-8`/ICU collation'ı veya normalize edilmiş arama kolonu önerilir; karar ekibe aittir.
- Anonim arama için oran sınırı yok (NOT IMPLEMENTED); en az 2 karakter ve limit üst sınırı kötüye kullanımı sınırlar.

Test: 186 test geçti, JaCoCo %98,25 (yeni: `PostSpecificationsTest`, 9 arama entegrasyon testi, 2 controller testi).

## COM-12 — Teknik özet

- Filtreler (`GET /api/v1/community/posts`): `gameId`, `category`, `from` (dahil), `to` (hariç) — `q` ve sayfalama ile birlikte AND olarak uygulanır; `from >= to` 400. Hepsi parametreli Criteria sorgusu (SQL birleştirme yok).
- **Kategori (kullanıcı kararı):** sabit liste `GENERAL, GUIDE, QUESTION, SCREENSHOT`; `posts.category` varchar(32) + CHECK, varsayılan GENERAL. **Mevcut yazma sözleşmesi genişledi (geriye uyumlu):** `POST /posts` ve `PATCH /posts/{id}` isteğine opsiyonel `category`; tüm yanıtlara `category` eklendi. PATCH'te `category` verilmezse korunur.
- Enum sertliği: `PostCategory` JSON'dan yalnız tam büyük harfli adla kabul edilir (`@JsonCreator`); sıra numarası (`1`), boş metin ve küçük harf 400 verir. (Jackson 3'te `fail-on-numbers-for-enums` özelliği `spring.jackson.deserialization` altında geçerli değildi; uygulama özelliği yerine enum seviyesinde çözüldü.) Sorgu parametresi enum'u da büyük/küçük harfe duyarlıdır.
- `gameId` filtresi game-service'i çağırmaz (yalnız yerel eşleşme).
- Migration: `V93` içine `category` kolonu + CHECK ve kısmi indeksler (`ix_posts_visible_category_created_at`, `ix_posts_visible_game_id_created_at`).
- Not: `JpaSpecificationExecutor` eklenmesiyle `PostRepository.delete(any())` testlerde belirsizleşti; `delete(any(Post.class))` olarak düzeltildi.

Test: 201 test geçti, JaCoCo %98,37 (yeni: 6 filtre entegrasyon testi, kategori/DTO/enum testleri, controller filtre testleri).

## COM-13 — Teknik özet (KISMİ)

- Parametre: `sort=NEWEST (varsayılan) | OLDEST | POPULAR` (allowlist enum; SQL'e kullanıcı metni girmez). Tüm sıralamalarda `id` ile deterministik eşitlik bozma; keyset koşulu sıralamaya göre üretilir (`PostSpecifications.after`).
- **Popülerlik (kullanıcı kararı):** skor `like_count + comment_count`, eşitlikte yenilik. Veritabanında üretilmiş kolon `popularity_score` (V93) ve kısmi indeks `ix_posts_visible_popularity`; JPA'da `insertable=false, updatable=false`.
- İmleç `v2`: sıralama adı ve skor taşır; başka sıralama için üretilmiş imleç 400.
- **Kısmi çünkü:** sayaçlar (beğeni/yorum) Faz 3'te dolacak; şimdilik tüm skorlar 0 ve `POPULAR` fiilen `NEWEST` gibi davranır. Mantığın kendisi (skor, eşitlikte yenilik, sayfalama) sayaçlar test içinde doğrudan SQL ile güncellenerek **gerçek PostgreSQL üzerinde** doğrulandı; gerçek beğeni/yorum akışıyla doğrulama Faz 3'te yapılacak.
- Sınırlama: skor sayfalar arasında değişirse (Faz 3 sonrası) değişken anahtarlı keyset'in bilinen sınırı olarak sayfa sınırında atlama/tekrar olabilir (belgelendi).
- Migration: `V93` içine `popularity_score` (generated) ve `ix_posts_visible_popularity`.

Test: 212 test geçti, JaCoCo %98,27 (yeni: sıralama/popülerlik entegrasyon testleri, `PostCursorTest` v2, controller `sort` testleri).

## COM-15 — Teknik özet

- Endpoint: `GET /api/v1/community/users/{userId}/posts` (public; `docs/api.md` kaydı eklendi). Akışla aynı `PostFeedRequest` (q, gameId, category, from, to, sort, limit, cursor) ve aynı sorgu hattı (`authorId` kriteri eklendi); yeni sorgu mantığı yok.
- **Erişime açık gönderiler:** misafir ve başka kullanıcılar yalnız `VISIBLE`; kullanıcı kendi profilinde `VISIBLE + HIDDEN` görür (HIDDEN öğeler `status` ile işaretli). `DELETED` hiç listelenmez. "Kendi profili" yalnız doğrulanmış kimlikten (`findUserId`) belirlenir; istemci girdisi yetki kaynağı değildir.
- Bilinmeyen kullanıcı → boş sayfa (varlık sızdırılmaz). Engelleme/profil gizliliği kuralları Faz 4/COM-21'e bırakıldı (belgelendi).
- Güvenlik: yalnız `GET /api/v1/community/users/*/posts` anonim açıldı; `/users/*` ve `/users/*/posts/*` ile yazma yöntemleri kimlik ister (testli).

Test: 222 test geçti, JaCoCo %98,48 (yeni: `UserPostControllerTest` 4, profil geçmişi entegrasyon testleri 6).

## Migration

- `V93__add_post_feed_and_search_support.sql` (tek dosya, henüz commit edilmedi): akış/kategori/oyun/popülerlik indeksleri, `pg_trgm` + `ix_posts_body_trgm`, `category`, `like_count`, `comment_count`, üretilmiş `popularity_score`. Community aralığında kalan numaralar: V94–V100 (7).

## Doğrulama (nihai)

- `.\pre-push\pre-push.cmd community-service`: 222 test, 0 hata, `BACKEND TEST CHECK PASSED`, JaCoCo %98,48.
- `.\sonarqube\sonar.cmd scan dev`: `SONAR SCAN PASSED`, Quality Gate PASSED; güvenlik/güvenilirlik/bakım A, yeni kod coverage %97,1, tekrar %0.
- Sonar'ın bulduğu 6 ihlal (S1192 ×2, S5853, S5778 ×3) giderildi: `PostSpecifications` alan adı sabitleri, test assertion zinciri, lambda dışına alınan id'ler.

## Bilinen sınırlar (Faz 2)

- COM-05: yorum/etkileşim sayıları Faz 3'te (COM-06/07) dolacak; sayaçlar şimdilik hep 0.
- COM-13: `POPULAR` şu an fiilen yeniliğe düşer; mekanik yalnız SQL ile verilen sayaçlarla test edildi.
- Anonim akış için oran sınırı yok (NOT IMPLEMENTED).
- Yazar adı/avatarı yok (user-service sözleşmesi bekleniyor).
- Engellenen kullanıcıların gönderilerinin gizlenmesi (social-service) COM-21/Faz 4 kapsamındadır; akışta uygulanmıyor.
- Aramada büyük/küçük harf katlama DB locale'ine bağlı; dev/test (C locale) ASCII dışı harflerde katlamaz.
- JWT uçtan uca ve Game Service canlı doğrulaması hâlâ NOT VERIFIED (auth/game sözleşmeleri bekleniyor).
