# LTZ-NIS-COM-03 — Gönderi Yorumları, Beğeni, Repost ve Oyun Forumları

Servis: `community-service` · Sorumlu: Nisa · Branch: `feature/community-service` · Faz: Faz 3
Durum: **FAZ 3 TAMAMLANDI — onay bekliyor (6 / 6 task)** · Faz 2 (COM-02) henüz commit edilmemiş değişikliklerle birlikte ilerledi

Nihai faz raporu. Faz 4 kullanıcı onayı olmadan başlatılmaz.

## Task durumu

| Task | Durum | Migration |
| --- | --- | --- |
| COM-06 — Gönderi beğenileri | Tamamlandı | V94 |
| COM-07 — Gönderi yorumları | Tamamlandı | V95 |
| COM-08 — Yorum düzenleme ve silme | Tamamlandı | V95 |
| COM-09 — Yorumlara yanıt verme | Tamamlandı | V95 |
| COM-19 — Oyun forumları / konuları | Tamamlandı | V96 |
| COM-20 — Repost | Tamamlandı | V97 |

Faz 2'de kısmi bırakılan **COM-05** (etkileşim sayaçları, `likedByMe`, yorum uçları) ve **COM-13** (`POPULAR` artık gerçek beğeni/yorum sayaçlarıyla çalışıyor) bu fazla **tamamlandı**.

## Alınan kararlar (kullanıcı onaylı)

- **Yanıt derinliği:** sınırsız iç içe. Yanıtlar özyinelemeli sorguyla değil, üst yorum başına ayrı uçtan (`/comments/{id}/replies`, keyset) okunur; bu yüzden derinlik sorgu maliyetini artırmaz.
- **Yanıtı olan yorum silinince:** soft delete, yanıtlar kalır; silinmiş yorum yer tutucu (içerik ve yazar yok) olarak listelenir, yanıtı olmayan silinmiş yorum listelenmez.
- **Forum yetkisi:** forum oluşturma/güncelleme/gizleme yalnız `MODERATOR`/`ADMIN` rolü; konuyu herkes açar. Rol JWT `roles` claim'inden okunur (**varsayım**, auth-service sözleşmesi bekleniyor; decoder fail-closed olduğundan şimdilik gerçek token kabul edilmez).
- **Repost biçimi:** opsiyonel kısa yorumlu repost; ayrı satır, yalnız referans (içerik kopyalanmaz), kullanıcı başına orijinal başına bir (silinmemiş) repost, repost'un reposta'ı doğrudan orijinale bağlanır.

## COM-06 — Beğeniler

- `PUT` / `DELETE /api/v1/community/posts/{postId}/like` (idempotent, `LikeStateResponse{postId, liked, likeCount}`).
- Mükerrer beğeni DB düzeyinde engellenir: `post_likes` PK `(post_id, user_id)` + `insert ... on conflict do nothing`. Sayaç yalnız satır gerçekten eklendiğinde/silindiğinde, aynı transaction'da `update posts set like_count = like_count ± 1` ile atomik güncellenir; artırma `status='VISIBLE'` koşulludur (yarışta gönderi gizlenirse işlem geri alınır).
- `Post.likeCount/commentCount` `updatable=false`: gönderi düzenlenirken bayat sayaç geri yazılıp ezilmez (test var).
- Akış/detayda `likedByMe` (sayfa için tek sorgu, `PostViewAssembler`; N+1 yok).

## COM-07 / COM-08 / COM-09 — Yorumlar

- `POST /posts/{postId}/comments` (yorum + `parentCommentId` ile yanıt), `GET /posts/{postId}/comments` (üst düzey, herkese açık), `GET /comments/{commentId}/replies`, `PATCH` / `DELETE /comments/{commentId}`.
- Yorum yalnız VISIBLE (ve forumu görünür) gönderiye yazılır; yanıt hedefi aynı gönderide ve VISIBLE olmalıdır. Düzenleme/silme yalnız yazara (başkası 403, VISIBLE dışı 404). `posts.comment_count` ekleme/silmeyle atomik güncellenir; eşzamanlı çifte silmede `@Version` ile yalnız biri başarılı olur.
- Keyset (eskiden yeniye) sayfalama (`TimeIdCursor`), yanıt sayıları sayfa başına tek sorgu.

## COM-19 — Forumlar

- `POST/GET /forums`, `GET/PATCH /forums/{forumId}`; konular `POST /posts` içindeki `forumId` ile açılır, `GET /posts?forumId=` ile listelenir; yorumlar mevcut yorum akışıyla.
- Oyun ilişkisi korunur: forum oyunuyla kataloğa karşı doğrulanır; `slug`/`gameId` sonradan değişmez; oyuna bağlı forumun konusu o oyunu miras alır, çelişen `gameId` 422, konunun oyun bağlantısı değiştirilemez/kaldırılamaz.
- Moderasyon: gizli forum ve konuları akış, arama, profil geçmişi, detay, beğeni, yorum ve repost kaynağından herkes için (sahibi dahil) çıkar; yeniden açılınca geri gelir. Veri silinmez.

## COM-20 — Repost

- `POST /posts/{postId}/reposts`. Yanıt `PostViewResponse` + `repostOf` (orijinal gönderi kimliği, sahibi, içeriği, görselleri) ile **açık atıf** taşır; orijinalin içeriği, sahibi ve zaman damgaları değişmez (test var).
- Erişilemeyen (silinmiş/gizli/gizli forumdaki) gönderi repostlanamaz (404). Orijinal sonradan erişilemez olursa `repostOf = {postId, available:false}` döner; içerik ve sahip sızmaz, yeniden açılınca atıf geri gelir. Görünürlük her okumada, sayfa için toplu sorguyla hesaplanır.
- Repost düzenlenemez (409), görsel/oyun bağlanamaz; yalnız silinebilir. Mükerrer repost 409 (+ `ux_posts_one_repost_per_user` UNIQUE indeksi).

## Migration (V94–V97)

- `V94__create_post_likes_table.sql`, `V95__create_comments_table.sql`, `V96__create_forums_and_link_posts.sql`, `V97__add_post_reposts.sql` (hiçbiri commit edilmedi; V93 de henüz commit'lenmedi).
- **Kalan numaralar: V98–V100 (3).** Faz 4 en az reports, outbox ve inbox gerektirir; aralık tam dolar. Daha fazlası gerekirse ekip kararı gerekir.
- `posts.body` artık NULL olabilir; `ck_posts_body_length` repost'u kapsayacak şekilde yeniden tanımlandı (yeni migration ile; uygulanmış dosya değiştirilmedi).

## API değişiklikleri (`docs/api.md`)

- 12 yeni kayıt: Gönderiyi Beğen, Beğeniyi Geri Al, Yorum Yaz/Yanıtla, Yorumları Listele, Yanıtları Listele, Yorumu Düzenle, Yorumu Sil, Forum Oluştur/Listele/Detay/Güncelle, Gönderiyi Repostla.
- Güncellenen kayıtlar: Gönderi Oluştur (`forumId`), Akış/Kullanıcı Geçmişi (`forumId` filtresi, `forumId`/`likedByMe`/`repostOf`, `POPULAR` tamamlandı), Detay (kısmi → tamam), Düzenle/Oyun Bağla/Kaldır (repost 409, forum oyun uyuşmazlığı 422), Görsel Ekle (repost 409).
- Geriye uyumluluk: mevcut yanıtlara yalnız yeni alanlar eklendi (`forumId`, `likedByMe`, `repostOf`); kaldırılan/tipi değişen alan yok. `PostResponse.body` ve `PostViewResponse.body` repost'ta null olabilir.

## Doğrulama

- `.\pre-push\pre-push.cmd community-service`: 323 test, 0 hata, `BACKEND TEST CHECK PASSED`, JaCoCo %98,75.
- `.\sonarqube\sonar.cmd scan dev`: `SONAR SCAN PASSED`, Quality Gate PASSED, açık sorun 0; yeni kod coverage %97,7, tekrar %0, güvenlik/güvenilirlik/bakım A. (İlk taramada çıkan 32 bulgu — S5778 lambdaları, S1192 sabitleri, S3776 karmaşıklık, S8786 regex, S7467 — giderildi.)
- Gerçek PostgreSQL üzerinde eşzamanlılık testleri: 12 farklı kullanıcıdan eşzamanlı beğeni/yorum doğru sayıldı; aynı kullanıcıdan 10 eşzamanlı beğeni tek kayıt; aynı yorumun 6 eşzamanlı silinmesinde tek başarı.

## Bilinen sınırlar / NOT IMPLEMENTED / NOT VERIFIED

- **Oran sınırı ve spam kontrolü** (yorum/beğeni/repost için aynı içeriğin tekrarı, kullanıcı+IP limiti): NOT IMPLEMENTED (SECURITY.md §5/§12).
- **JWT `roles` claim'i** ve JWT uçtan uca: NOT VERIFIED (auth-service sözleşmesi bekleniyor); forum yönetimi gerçek bir token'la denenemedi.
- **Game Service** canlı doğrulaması: NOT VERIFIED (forum oluşturmada da aynı batch ucu kullanılıyor).
- **Bildirimler** (beğeni/yorum/repost → notification-service) ve **moderasyon denetim kaydı** (admin-service): Faz 4 (COM-17/18/21).
- **Engelleme/gizlilik** (social-service), **yazar adı/avatarı** (user-service): henüz yok.
- Gizlenmiş (`HIDDEN`) gönderinin sahibi, gizli gönderisinin orijinal olduğu bir repost'ta kaynağı `available:false` görür (sahip istisnası yok; bilinçli basitlik).
- Repost sayacı (`repostCount`) yok; gerekirse ayrı karar.
- Yorum listelerinde silinmiş yorum yer tutucusu, yalnız doğrudan yanıtı varsa listelenir (yanıtlarının hepsi silinmişse de yer tutucu kalır).
- JWT'ye bağlı rol adı (`MODERATOR`/`ADMIN`) ve claim adı (`roles`) auth-service ile teyit edilmeli.

## Sonraki faz

**LTZ-NIS-COM-04 — Topluluk Raporlama, Moderasyon ve Bildirim (Faz 4):** COM-16, COM-17, COM-18, COM-21 (rapor oluşturma, admin-service ile moderasyon sonuçlandırma, bildirim, servis sınırları/engelleme/görünürlük). Migration: V98–V100 (3).
