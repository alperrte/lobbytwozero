# LTZ-NIS-COM-04 — Topluluk Raporlama, Moderasyon ve Bildirim

Servis: `community-service` · Sorumlu: Nisa · Branch: `feature/community-service` · Faz: Faz 4
Durum: **FAZ 4 TAMAMLANDI — kullanıcı onayı bekliyor (4 / 4 task; alıcı servislerle uçtan uca doğrulama NOT VERIFIED)** · Faz 2–3 commit'i: `4d67a72`

Bu, community-service'in son fazıdır. Servisler arası entegrasyonun community tarafı tamamdır; admin-service, notification-service ve social-service tarafı (alıcı/üretici sözleşmeleri) bu repoda henüz yoktur, bu yüzden canlı teslim doğrulanamadı.

## Task durumu

| Task | Durum | Migration |
| --- | --- | --- |
| COM-16 — Gönderi ve yorum raporlama | Tamamlandı | V98 (outbox), V99 |
| COM-17 — Moderasyon entegrasyonu | Tamamlandı (admin ile canlı doğrulama NOT VERIFIED) | V98 (inbox) |
| COM-18 — Topluluk bildirimleri | Tamamlandı (notification ile canlı teslim NOT VERIFIED) | V98 (outbox) |
| COM-21 — Servis sınırları ve entegrasyon | Tamamlandı (social ile canlı doğrulama NOT VERIFIED) | V100 |

## Alınan kararlar (kullanıcı onaylı)

- **Gelen olaylar:** `POST /internal/events` (docs/architecture.md §6.2), **fail-closed**. Yalnız `SCOPE_internal.events` yetkisi taşıyan servis kimliği kabul edilir; servisler arası kimlik modeli ADR'si netleşene kadar hiçbir token bu yetkiyi taşımadığından uç fiilen kapalıdır. Bu bir **varsayım**dır ("servis JWT'si" seçeneği).
- **Engelleme:** iki yönlü gizle + etkileşimi engelle. Veri social-service'indir; community yalnız `UserBlocked` / `UserUnblocked` olaylarından beslenen yerel okuma yansımasını (`user_blocks`) tutar.
- **Olay teslimi:** olaylar iş verisiyle aynı transaction'da outbox'a yazılır; relay yazıldı ama **varsayılan KAPALI** (`ltz.outbox.relay.enabled=false`). Alıcı URL'leri (`ADMIN_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`) `.env.example`'da zaten vardı; **ENV değişikliği yapılmadı**.

## COM-16 — Raporlama

- `POST /api/v1/community/posts/{postId}/reports`, `POST /api/v1/community/comments/{commentId}/reports` (`reason` sabit listeden, `detail` ≤ 500 karakter).
- Kurallar: yalnız çağıranın görebildiği içerik raporlanır (silinmiş/gizli/gizli forum/engelli → 404); kendi içeriğin raporlanamaz (422); aynı içerik aynı kullanıcıdan bir kez (409, DB UNIQUE ile de korunur); kullanıcı başına saatte 20 rapor (429 + `Retry-After`).
- Rapor içerik referansı (`targetType`, `targetId`), içerik yazarı ve gerekçeyle `community_reports`'ta saklanır; **`ContentReported`** olayı aynı transaction'da outbox'a yazılır (içerik metni taşınmaz). Yanıtta yazar/diğer raporlayanlar görünmez.

## COM-17 — Moderasyon entegrasyonu

- `POST /internal/events` → `ModerationActionTaken` (yalnız `admin-service`). Eylemler: `HIDE`, `RESTORE`, `REMOVE`, `DISMISS`.
- Etki: gönderi/yorum VISIBLE ↔ HIDDEN; `REMOVE` soft delete (gönderi görselleri silinir, **geri alınamaz**; silinmiş içerik `RESTORE` ile geri gelmez). Yorum gizleme/geri açma/kaldırma `posts.comment_count`'u doğru tutar (yalnız sayılan yorum değişince). İlgili açık raporlar: `HIDE`/`REMOVE` → `RESOLVED`, `DISMISS` → `DISMISSED`.
- Idempotent: `inbox_events` ile `eventId` tekilleştirmesi, etkisiyle aynı transaction'da; üretici–olay türü eşleşmesi zorunlu (yanlış üretici 400); bozuk yük inbox'a "işlendi" diye yazılmaz; bilinmeyen olay türü başarıyla yok sayılır.

## COM-18 — Bildirimler

- Olaylar: **`CommentCreated`** (alıcılar: gönderi sahibi ve yanıtsa üst yorumun yazarı; aktör çıkarılır, tekrarlar tekilleştirilir), **`PostLiked`** (yalnız beğeni gerçekten eklendiğinde), **`PostReposted`** (orijinal sahibine). Kişi kendi içeriğine yaptığı etkileşim için olay üretilmez. Yükte yalnız kimlikler vardır (metin ve görünen ad yok).
- Relay (`OutboxRelay`/`OutboxDispatcher`): `for update skip locked` ile kiralama (çoklu örnekte çifte teslim yok), üstel geri çekilme (10 sn × 2ⁿ, en çok 1 saat), 10 denemeden sonra `FAILED`, kalıcı hatalarda (bilinmeyen tür/bozuk yük) anında `FAILED`, teslim edilmiş olaylar 14 gün sonra temizlenir. Teslim: `RestClient` ile `POST {ADMIN|NOTIFICATION}_SERVICE_URL/internal/events`; log'lara yük yazılmaz.

## COM-21 — Servis sınırları ve engelleme

- Veri sahipliği: gönderi, yorum, beğeni, repost, forum ve rapor verileri yalnız `community_db`'dedir; başka servisin DB'sine erişim yoktur. Dış veriler (oyun doğrulaması) RestClient ile, engelleme bilgisi olay yansıması ile alınır.
- Engelleme etkisi (iki yön): akış, arama, profil geçmişi, gönderi detayı, yorum/yanıt listeleri (engelli yazarın yorumları listelenmez), beğeni/yorum/yanıt/repost/rapor (404, varlık sızdırılmaz), repost kaynağı (engelli yazarın orijinali `available:false`). Misafir ve ilgisiz kullanıcılar etkilenmez.

## Migration (V98–V100)

- `V98__create_outbox_and_inbox_tables.sql`, `V99__create_community_reports_table.sql`, `V100__create_user_blocks_table.sql`. **Community aralığı (V91–V100) tamamen doldu**; bu serviste yeni migration için ekip kararı gerekir.

## API değişiklikleri (`docs/api.md`)

- Yeni kayıtlar: Gönderiyi Raporla, Yorumu Raporla, Servisler Arası Olay Girişi (`/internal/events`, Internal; community'nin yayınladığı olay sözleşmeleri de burada).
- Güncellenen kayıtlar: Akış, Kullanıcı Gönderi Geçmişi, Gönderi Detayı, Beğen, Yorum Yaz, Yorum/Yanıt Listeleri, Repostla (engelleme kuralları, outbox olayları).
- Geriye uyumluluk: mevcut yanıt alanları değişmedi.

## Doğrulama

- `.\pre-push\pre-push.cmd community-service`: 391 test, 0 hata, `BACKEND TEST CHECK PASSED`, JaCoCo %98,87.
- `.\sonarqube\sonar.cmd scan dev`: `SONAR SCAN PASSED`, Quality Gate PASSED, açık sorun 0, yeni kod coverage ≥ %97, tekrar %0, güvenlik/güvenilirlik/bakım A. (Taramada çıkan bulgular — S5778 lambdaları, S1192 sabitleri, S6213 kısıtlı tanımlayıcı, S6916, S5838/S5853, S8745 vb. — giderildi.)
- Gerçek PostgreSQL entegrasyon testleri: outbox'ın iş verisiyle aynı transaction'da yazılması/geri alınması, kiralama/geri çekilme/FAILED/temizlik, inbox tekilleştirme, moderasyon etkileri ve sayaçlar, engelleme etkileri, rapor kuralları. `RestClient` teslimi `MockRestServiceServer` ile.

## Bilinen sınırlar / NOT IMPLEMENTED / NOT VERIFIED

- **Alıcı/üretici sözleşmeleri yok:** admin-service, notification-service ve social-service API kayıtları `docs/api.md`'de boş; olay adları/yükleri community tarafında tanımlandı (docs/architecture.md §6.3 kataloğuyla uyumlu, `PostReposted` katalogda yok — eklenmesi önerilir). Canlı teslim ve alım NOT VERIFIED.
- **Servisler arası kimlik modeli:** `/internal/events` için `SCOPE_internal.events` varsayımı ve relay'in istekte kimlik başlığı göndermemesi ADR bekliyor; relay bu yüzden kapalı. Gateway'in `/internal/**` yolunu route etmediği gateway sahibiyle teyit edilmeli.
- **Olay sırası garanti edilmez:** `UserBlocked`/`UserUnblocked` farklı sırada gelirse son işlenen kazanır (tombstone yok). social-service olayları sıralı üretmeli ya da periyodik uzlaştırma eklenmeli.
- **AccountDeleted / AccountStatusChanged** (auth olayları) ve kişisel veri silme/anonimleştirme: bu fazın kapsamı dışında, NOT IMPLEMENTED.
- **Rapor kuyruğu okuma ucu** (admin'in moderasyon kuyruğunu community'den çekmesi) yoktur; admin `ContentReported` olayını bekler. Moderatörün raporlanmış gizli içeriği görmesi için ayrı bir iç uç gerekebilir.
- Oran sınırları (IP bazlı), yorum/beğeni/repost spam kontrolü: NOT IMPLEMENTED (yalnız rapor sayısı sınırlıdır).
- JWT `roles`/servis `scope` claim adları auth-service ile teyit edilmedi (NOT VERIFIED); Game Service canlı doğrulaması hâlâ yok.
- `replyCount` ve `commentCount` engelleme filtresinden etkilenmez (engelli yazarın yorumu sayılır, listelenmez).

## Sonraki adım

community-service Faz 1–4 ile tamamlandı. Önerilen sıradaki işler: (1) alıcı/üretici servis sahipleriyle olay sözleşmelerinin hizalanması ve `docs/decisions/` altında servisler arası kimlik ADR'si, (2) relay'in açılması ve uçtan uca testi, (3) Nisa'nın diğer servisleri (content-service, event-service) için ilgili görev dosyası.
