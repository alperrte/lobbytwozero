# LTZ-NIS-COM-01 — Topluluk Gönderisi Oluştur/Düzenle/Sil ve Görsel Ekle

Servis: `community-service` · Sorumlu: Nisa · Branch: `feature/community-service` · Faz: Faz 1
Durum: **TAMAMLANDI (5/5 — %100)** · Faz 2 için kullanıcı onayı: **YOK, bekleniyor**

## Task durumu

| Task | Durum |
| --- | --- |
| COM-01 — Topluluk gönderisi oluşturma | Tamamlandı |
| COM-02 — Gönderi düzenleme | Tamamlandı |
| COM-03 — Gönderi silme | Tamamlandı |
| COM-10 — Gönderi görselleri | Tamamlandı |
| COM-14 — Oyuna bağlı gönderiler | Tamamlandı (game-service ile canlı doğrulama yapılamadı, bkz. sınırlar) |

## Alınan kararlar ve varsayımlar

- **Kimlik modeli (kullanıcı onaylı):** gönderi sahibi doğrulanmış JWT `sub` claim'inden alınır (`CurrentUserPort` → `SecurityContextCurrentUser`). Repoda JWT doğrulama anahtarı ENV'si olmadığı için `JwtDecoder` **fail-closed** çalışır: hiçbir gerçek token kabul edilmez (401). Yeni ENV eklenmedi. Auth-service sözleşmesi (algoritma, JWKS/secret, `sub` = accountId) netleşince yalnız `SecurityConfig.jwtDecoder()` bean'i değişir. `sub` = kullanıcı UUID'si varsayımı doğrulanmadı.
- **Görsel depolama (kullanıcı onaylı):** bayt içerik `community_db.post_images.content` (bytea). Ayrı media-service, ENV, volume veya dış bağımlılık yok. Repoda bu karar `[KARAR BEKLİYOR]` idi; resmî ADR (`docs/decisions/NNNN-...`) olarak kaydedilmesi **önerilir**, yazılmadı.
- **Dokümanlar** PDA'daki `docs-Community Service/` yerine mevcut `docs-community/` klasöründe (kullanıcı onaylı).
- **Varsayımlar (PDA'da yok):** gönderi gövdesi düz metin, 1–5000 karakter; görsel başına 2 MiB, gönderi başına 4 görsel, 6000 px kenar / 12 milyon piksel; görsel okuma için kimlik doğrulama gerekir (misafir erişimi Faz 2 görünürlük kuralıyla belirlenecek).
- **CSRF varsayılan olarak açık** (Sonar S4502 nedeniyle `disable()` kullanılmadı): resource server yapılandırması yalnız `Authorization: Bearer` başlıklı istekleri CSRF'den muaf tutar; kimlik göndermeyen yazma isteği CSRF filtresine takılsa da 401 döner (`ProblemDetailSecurityHandler`), Bearer dışı `Authorization` başlığı 403 alır. Çerez tabanlı oturum kararlaştırılırsa tasarım yeniden değerlendirilmeli.
- **Migration bütçesi (V91–V100, 10 numara):** `posts.game_id` kolonu numara tasarrufu için V91'de nullable açıldı; API'ye COM-14'te bağlandı. V91 henüz paylaşılmadığı için COM-03 sırasında `ck_posts_deleted_at` aynı dosyaya eklendi; paylaşıldıktan sonra değişiklik yeni numarayla yapılmalıdır. Faz 2–4 için kalan: V93–V100 (8 numara); Faz 3 (yorum, beğeni, forum, repost) ve Faz 4 (rapor, outbox/inbox) bu aralığa sığdırılmalıdır.

## API (`docs/api.md` COMMUNITY başlığında kayıtlı)

| Endpoint | Task | Yetki |
| --- | --- | --- |
| `POST /api/v1/community/posts` (opsiyonel `gameId`) | COM-01, COM-14 | Authenticated |
| `PATCH /api/v1/community/posts/{postId}` | COM-02 | Owner |
| `DELETE /api/v1/community/posts/{postId}` | COM-03 | Owner |
| `POST /api/v1/community/posts/{postId}/images` (multipart) | COM-10 | Owner |
| `GET /api/v1/community/posts/{postId}/images/{imageId}` | COM-10 | Authenticated (görünürlük kuralıyla) |
| `DELETE /api/v1/community/posts/{postId}/images/{imageId}` | COM-10 | Owner |
| `PUT /api/v1/community/posts/{postId}/game` | COM-14 | Owner |
| `DELETE /api/v1/community/posts/{postId}/game` | COM-14 | Owner |

Görsel silme ve oyun bağlantısını kaldırma PDA'da açık yazılı değil; ilgili task'ların (içerik yönetimi) doğal parçası olarak eklendi.

## Teknik özet

- **Katmanlar:** `PostController`/`PostImageController` → `PostService`/`PostImageService` (+ `PostAccess`, `ImageSanitizer`) → `PostRepository`/`PostImageRepository`; `PostMapper`; `Post`/`PostImage` entity (UUIDv7, `@Version`, UTC `Instant`); `GameCatalogPort` ← `GameServiceClient`.
- **Güvenlik:** deny-by-default `SecurityFilterChain` (yalnız actuator health/info anonim), stateless, 401/403 `ProblemDetail`; `userId` yalnız token'dan; bilinmeyen JSON alanları reddedilir (mass assignment).
- **Hata modeli:** `GlobalExceptionHandler` → `ProblemDetail`; doğrulama hatasında alan adı + mesaj, girdi değeri yok; 400/401/403/404/409/413/415/422/503/500.
- **Sahiplik/görünürlük (`PostAccess`):** silinmiş → herkese 404; sahibi değil + VISIBLE → 403; sahibi değil + VISIBLE dışı → 404 (gizli içerik sızmaz); sahibi + VISIBLE dışı düzenleme → 409. Eşzamanlılık: `@Version` optimistic lock (409) ve görsel/silme işlemlerinde `posts` satır kilidi.
- **Silme (COM-03):** soft delete (`status=DELETED`, `deleted_at`); satır fiziksel silinmez, görsel baytları aynı işlemde silinir. Yorum/beğeni/repost tabloları henüz olmadığından bu tutarlılığın çapraz davranışı **test edilemedi**; Faz 3 için kural: okuma sorguları DELETED gönderiye ait kayıtları hariç tutar, FK'ler `ON DELETE RESTRICT`.
- **Görsel doğrulama (`ImageSanitizer`):** istemcinin dosya adı/Content-Type'ı yok sayılır; gerçek biçim `ImageIO` ile belirlenir (yalnız JPEG/PNG); boyut/kenar/piksel sınırı çözmeden önce; görsel yeniden kodlanır (EXIF/konum, PNG metin parçaları, JPEG yorumları ve sonradan eklenmiş veri atılır); yanıt başlıkları `nosniff`, `Content-Security-Policy: default-src 'none'; sandbox`, `Cache-Control: no-store`, sunucu doğrulamalı `Content-Type`.
- **Oyun bağlantısı (COM-14):** `gameId` game-service `POST /api/v1/game/games/batch` ile (N+1 yok, tek kimlik, anonim uç) doğrulanır; çağrı transaction dışı (havuz=3), yeniden deneme yok, hata halinde fail-closed (503). Kimlik katalogda yoksa 422. game_db'ye doğrudan erişim yok.
- **Bağımlılık:** test kapsamında `spring-boot-starter-webmvc-test` (Boot yönetimli).
- **JPEG kodlama:** Sonar S2093 nedeniyle `ImageIO.write` kullanılıyor (JDK varsayılan kalite ~0,75); önceki sürümdeki elle 0,9 kalite ayarı kaldırıldı.

## Veritabanı / migration

- `V91__create_posts_table.sql`: `posts` (id, author_id, game_id, body, status, created_at, updated_at, deleted_at, version); CHECK: status, body uzunluğu, `ck_posts_deleted_at`; `ix_posts_author_id_created_at`. Cross-service FK yok.
- `V92__create_post_images_table.sql`: `post_images` (bytea içerik, mime, boyut, ölçü, sıra); FK `fk_post_images_post` `ON DELETE RESTRICT`; UQ (post_id, sort_order); CHECK: mime (jpeg/png), boyut ≤ 2 MiB, ölçü > 0, sıra 0–3.
- Konfigürasyon (ENV değil): `spring.servlet.multipart.max-file-size=2MB`, `max-request-size=2560KB`, `ltz.clients.game-service.url=${GAME_SERVICE_URL:http://game-service:8080}` (`GAME_SERVICE_URL` zaten tüm servislere geçiriliyor).
- Güncellenen resmî belgeler: `docs/api.md` (COMMUNITY), `docs/database.md` (posts/post_images satırları), `docs/er-diagram.md` (post_images).

## Testler

- `mvnw clean verify` (JDK 25.0.4, geçici PostgreSQL 17 container'ı; dev DB ve `.env` kullanılmadı): **135 test, 0 hata, BUILD SUCCESS**.
- Dağılım: PostControllerTest 34, PostImageControllerTest 20, PostServiceTest 25, PostImageServiceTest 13, ImageSanitizerTest 12, PostRepositoryTest 11, PostImageRepositoryTest 11, GameServiceClientTest 6, PostTest 2, contextLoads 1.
- JaCoCo satır kapsamı: **%97,14 (340/350)** (Sonar düzeltmeleri sonrası 138+ testte pre-push %94,4; Sonar tüm backend'de %89,2), hedef ≥ %80.
- Kapsanan senaryolar: sahip token'dan alınır; anonim/doğrulanamayan token/UUID olmayan `sub` → 401; başkasının gönderisi/görseli → 403/404 (IDOR); silinmiş/HIDDEN davranışı; optimistic lock; boş/uzun/kontrol karakterli/bilinmeyen alanlı istek; JPEG/PNG kabulü, GIF/metin dosyası/bozuk/aşırı büyük/piksel bombası reddi; EXIF/metadata/polyglot temizliği; 4 görsel sınırı (uygulama + DB); gerçek PostgreSQL üzerinde Flyway + `ddl-auto=validate` + CHECK/FK/UQ; game-service istemcisi (başarı, `unavailable`, 5xx, 429, bağlantı hatası, bozuk yanıt, bilinmeyen alan).
- `.\pre-push\pre-push.cmd service` **çalıştırılmadı**: ortam profili (`ops/env.* use`) ve gerçek `.env` gerektirir; ajan kuralı gereği kullanıcı onayı olmadan çalıştırılmaz. Önerilen: commit öncesi kullanıcı çalıştırsın.

## SonarQube (DEV, `.\sonarqube\sonar.cmd scan dev`)

SON DURUM: **SONAR SCAN PASSED** — 159 test, Quality Gate PASSED; Security/Reliability/Maintainability A; coverage %89,2; tekrar %0; açık hotspot 0. İlk taramada Security D (S4502 CSRF kapalı) ve Reliability C (S6218 `byte[]` taşıyan `record`'lar) çıktı; CSRF varsayılana alındı, `record`'lara `equals/hashCode/toString` yazıldı, kalan küçük bulgular (S2093, S6878, S5778, S5853, S5838, S5976, S1075, S7467, S1128) temizlendi.

## Bilinen sınırlar ve açık konular

1. **Gerçek JWT ile uçtan uca akış doğrulanmadı** — decoder fail-closed; auth-service sözleşmesi (anahtar, `sub`, hesap durumu/rol claim'leri) bekleniyor. SUSPENDED/BANNED hesapların gönderi oluşturmasını engelleme henüz yok.
2. **game-service ile canlı doğrulama yapılamadı** — yerel game-service container'ı batch ucunda 401 döndü (o imaj, Hamza'nın `feature/game-service` branch'indeki GAME-15 anonim batch ucunu içermiyor). İstemci, o branch'teki belgelenmiş sözleşmeye göre `MockRestServiceServer` ile test edildi; sözleşme main'e girip imaj yenilenince doğrulanmalı. Hamza'ya bildirilmesi gereken bağımlılık: Community → `POST /api/v1/game/games/batch` (tüketici olarak kayda eklenmeli).
3. **WebP desteklenmiyor** (JDK ImageIO çözemiyor; üçüncü taraf kütüphane onay gerektirir).
4. Container düzeyinde multipart sınırı (`spring.servlet.multipart.*`) gerçek Tomcat'te test edilmedi; yalnız servis düzeyi 2 MiB kontrolü testli.
5. Tarayıcı `<img>` Authorization başlığı gönderemez; görsel gösterimi için çerez/imzalı URL modeli auth sözleşmesine bağlı.
6. Oran sınırı / spam kontrolü (gönderi ve görsel yükleme, SECURITY.md §12) **NOT IMPLEMENTED**.
7. Yorum/beğeni/repost ile silme tutarlılığı Faz 3'te doğrulanacak; moderatör/admin istisnaları Faz 4'te (COM-17).
8. Kalıcı kişisel veri silme/anonimleştirme (AccountDeleted event'i) kapsam dışı (Faz 4/COM-21).
9. Görsel depolama için resmî ADR önerisi.
10. Çalışma ağacında benim yapmadığım değişiklikler var (`docs/decisions/migrations.md` Game Service ek aralığı, `frontend/package-lock.json`); commit'e dahil edilmemeli/sahibine sorulmalı.

## Faz durumu

Faz 1'in beş alt task'ı uygulandı, test edildi ve kabul kriterleri karşılandı; açık konular yukarıda. Faz 2 (LTZ-NIS-COM-02, 6 alt task) için **onay bekleniyor**.
