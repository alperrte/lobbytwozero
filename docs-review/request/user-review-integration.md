# USER–REVIEW entegrasyonu

Son güncelleme: 12 Kasım 2026 (User cevabı sonrası). Bu belge yalnız **açık işleri ve beklentileri** içerir. Kesin geliştirme emri değildir.

- Talep eden: `review-service` — Hamza. Hedef: `user-service` — Kerem. User kodu, migration'ı ve belgeleri değiştirilmedi.

## 1. User'ın cevabı (12 Kasım 2026)

**A — Profil sayacı (`ReviewSummaryChanged`): ONAYLANDI.** User alıcısı hazırdır ve davranış koddan doğrulandı:
- Eşit veya eski `occurredAt` bilerek yok sayılır; yalnız kesin yeni olay uygulanır (Review kesin artan zaman üretir — uyumlu).
- Aynı `eventId` tekrarı `inbox_events` ile zararsızdır.
- `userId` = `aggregateId` olmalı; `reviewCount` 0–10.000.000 aralığında olmalı, dışı kalıcı `INVALID_EVENT`'tir.
- `reviewCount` yalnız `VISIBLE` incelemeleri sayar (Review'ın kararı; gizlenen/arşivlenen sayılmaz — uygulandı).
- Backfill ve etkinleştirme Review tarafındadır. Secret için ENV onayı User sahibindedir; dev'de `ops/dev.mjs` secret'ı zaten üretir.
- Canlı Auth↔Review↔User uçtan uca yapılmadı (NOT VERIFIED); hesap silme sonrası gecikmiş olayın profili geri oluşturmadığı User tarafında test edilecek.

**B — Yazar görünümü: seçenek 2 kabul edildi; User'da toplu kart ucu zaten vardı** (`POST /api/v1/user/profiles/batch`). Review backend (`authorUserId`) ve frontend (yazar adı/avatar/profil bağlantısı) uygulandı (§3).

## 2. A için Review tarafı (UYGULANDI)

Review üreticisi, kullanıcı başına birleştirilmiş outbox (V59) ve teslim işçisi hazırdır (yerel sahte Auth/User sunucularıyla test edildi). Moderasyon kararları (`HIDE/ARCHIVE/RESTORE`) da yazarın sayacını günceller. **İlk etkinleştirme backfill'i uygulandı:** işçi açıldığında (yalnız `AUTH_REVIEW_SERVICE_CLIENT_SECRET` doluysa), outbox'tan önce yazılmış incelemeleri olan her kullanıcı için bekleyen olay açılır; idempotenttir, var olan satırlara dokunmaz; olay gönderim anındaki görünür sayıyı taşır.

**Açık (A):**
1. **ENV (YAPILDI, doğrulama açık):** `docker-compose.yml` review-service bloğuna `AUTH_REVIEW_SERVICE_CLIENT_SECRET` aktarımı eklendi (12 Kasım 2026, kullanıcı onayıyla; değer `.env.dev`te zaten vardı, Auth ile aynı değer). Review DEV'de yeniden oluşturuldu (12 Kasım 2026): 1092/1092 backfill olayı teslim edildi ve User `profile_counters.review_count` toplamı Review'daki görünür inceleme sayısıyla eşleşti (1344 / 903 kullanıcı). Eski madde: `AUTH_REVIEW_SERVICE_CLIENT_SECRET`'ın Review ortamına aktarımı (Review'ın compose/ENV işi; onay gerekir). Secret verilene kadar teslim kapalıdır.
2. **Canlı doğrulama [Tümü] — KISMEN YAPILDI:** gerçek Auth token'ıyla teslim ve sayaç eşleşmesi DEV'de doğrulandı; tekrar/eski zaman/yanlış kapsam ve hesap silme sonrası gecikmiş olay senaryoları canlıda denenmedi.

## 3. B — Yazar görünümü: seçenek 2 (UYGULANDI; canlı doğrulama açık)

**Karar (12 Kasım 2026, Hamza + User):** seçenek 2. Review yanıtları `authorUserId` (UUID) taşır; yazar adı/avatarı Review'dan gelmez, User toplu kart ucundan çözülür.

**Kabul edilen bedel:** Yazarın UUID'si kimliği doğrulanmış her kullanıcıya görünür; aynı yazarın farklı oyunlardaki incelemeleri birbirine bağlanabilir. **Ad ve avatar profil gizlilik ayarına BAKILMADAN görünür** (User'ın düzeltmesi: kart yalnız hesap durumu ve engele bakar; `REVIEW_AUTHOR` yorum/UGC kuralı). Aydınlatma metnine yansıtılması gerekir (metin sahibi: ürün/hukuk); hazır madde taslağı: [REVIEW_KVKK_MADDE_ONERISI.md](../REVIEW_KVKK_MADDE_ONERISI.md). Sitedeki `/yasal/kvkk` sayfası henüz yayınlanmış metin içermediğinden maddeye sayfada yer verilmedi.

**Review'da UYGULANDI (backend):** `ReviewResponse.authorUserId`: oluşturma/okuma/düzenleme yanıtları, öne çıkanlar öğesinin `review`'ü, `reviews/mine`, `games/{gameId}/reviews/mine`. Moderatör uçlarında, rapor yanıtlarında ve denetim kayıtlarında yok; oy veren kimlikleri dönmez. `docs/api.md` işlendi.

**Review'da UYGULANDI (frontend):** User'ın kesin sözleşmesine göre (`docs/api.md` → "USER — Toplu profil kartı", IMPLEMENTED):
- `POST /api/v1/user/profiles/batch`, gövde `{ "userIds": [UUID] }` (1–50, yinelenenler tek); boş liste için çağrı yapılmaz. Sayfa başına **tek istek** (öne çıkanlar sayfasında başkalarına ait incelemelerin yazarları; detayda tek yazar). Kendi incelemende ad gösterilmez, istek atılmaz.
- Yanıt `{ items: [{ userId, username, displayName, avatar:{small,large,animated} }] }`. Görünmeyen (olmayan, yasaklı/devre dışı, her iki yönde engelli) hesap yanıtta YOKTUR; ayrım sızmaz. Kartı olmayan yazar için "Bir oyuncu" gösterilir, profil bağlantısı verilmez. Ad düz metin çizilir.
- CSRF zorunlu (`X-XSRF-TOKEN`); `403 CSRF_TOKEN_INVALID` olursa belirteç yenilenip YALNIZ BİR KEZ tekrarlanır. 429/Retry-After beklenmez (bu uçta kota yok).
- Avatar: `small` yoksa `large`; yalnız `/api/v1/user/media/<anahtar>` biçimi kabul edilir (başka kaynağa görsel isteği atılmaz).
- Profil bağlantısı: `/oyuncular/{userId}` (TR), `/players/{userId}` (EN).
- 401/413/5xx veya ağ hatasında inceleme listesi yazarsız çalışır; inceleme asla gizlenmez.

**Açık:** gerçek Gateway üzerinden birlikte canlı doğrulama (**NOT VERIFIED**; yukarıdakiler sözleşme ve fixture ile test edildi); aydınlatma metni.

## 4. Kimlik doğrulama ve hata beklentileri

- **A:** Auth `svc+jwt` (`aud=user-service`, `events:ingest`, `sub=svc:review-service`, ≤60 sn); kullanıcı token'ına fallback yok; 401/403 kalıcı hata sayılır (token yenilenir, uzun geri çekilme); 5xx/zaman aşımı üstel geri çekilmeyle yeniden denenir. Kullanıcı işlemleri teslimden etkilenmez.
- **B:** kullanıcı oturumu (çerez→Bearer, Gateway); User kart ucu kesintisinde Review listesi yazarsız çalışır. Review, User'ın görünürlük kararını taklit etmez.

## 5. Kabul kriterleri (açık işler için)

- Gerçek Auth token'ıyla Review→User olayı kabul edilir; aynı `eventId` tekrarı sayacı değiştirmez; eski/eşit `occurredAt` geri almaz; yanlış producer/audience/scope reddedilir; hesap silme sonrası gecikmiş olay silinmiş profili geri oluşturmaz.
- İnceleme oluşturma, silme ve moderasyon sonrası User profilindeki sayı doğru görünür; backfill sonrası eski kullanıcıların sayıları gelir.
- (B) 50 yazar tek çağrıyla çözülür; gizli/engelli/silinmiş yazar için ayrım sızmaz; Review listesi kart ucu kesintisinde çalışır.

## 6. Durum

**A — Review ve User hazır; açık: ENV onayı ve canlı doğrulama. B — Review (backend + frontend) hazır; açık: canlı Gateway doğrulaması ve aydınlatma metni.**
