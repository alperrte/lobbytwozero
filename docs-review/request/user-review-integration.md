# USER–REVIEW entegrasyon talebi

Son güncelleme: 12 Kasım 2026 (Review tarafı üretici uygulandıktan sonra).

## 1. Talep eden servis

`review-service` — Hamza. Bu belge Review'ın User'dan ihtiyaçlarını ve beklentilerini bildirir; User sahibine kesin geliştirme emri değildir. User kodu, migration'ı ve belgeleri bu çalışmada **değiştirilmedi**.

## 2. Hedef servis

`user-service` — Kerem. Nihai tasarım User sahibince belirlenir.

## 3. İlgili task

REV-10 (servis sınırları ve entegrasyon; Review→User olay üreticisi), REV-02/REV-06/REV-09 (inceleme görünümünde yazar bilgisi ihtiyacı). USER tarafında profil sayaçları ve herkese açık profil görünürlüğü ile ilişkilidir.

## 4. Talebin amacı

İki ayrı ihtiyaç vardır ve birbirine karıştırılmamalıdır:

- **A. Profil sayacı:** Kullanıcının profilinde görünen inceleme sayısının `ReviewSummaryChanged` olayıyla User'a aktarılması (Review → User).
- **B. Yazar görünümü:** İnceleme kartında yazarın görünen adı/avatarı ve profiline bağlantı. Review bugün ham yazar kimliği döndürmez; bu, gizlilik kararıyla birlikte User ile ortak çözülmelidir.

## 5. Repository üzerinde doğrulanan mevcut durum (12 Kasım 2026)

**A için**

- User, olayı kabul etmeye **hazırdır**: `EventType.REVIEW_SUMMARY_CHANGED("ReviewSummaryChanged", "review-service")`; `ProjectionEventHandler.reviewSummaryChanged` payload'dan `userId` (= `aggregateId` olmalı) ve `reviewCount` (negatif olmayan tam sayı) okur ve `profile_counters.review_count` alanına yazar. Alıcı `inbox_events` ile `eventId` tekilleştirmesi yapar; producer uyuşmazlığı hata, tanınmayan tür `IGNORED`'dır.
- User sırayı `occurredAt` ile korur: `upsertReviews` yalnız **kayıtlı zamandan kesin olarak daha yeni** olayı uygular (`review_changed_at < EXCLUDED.review_changed_at`). Eşit veya eski zamanlı olay sayacı değiştirmez.
- Auth çifti **mevcuttur** (`application.properties`): `ltz.auth.service-clients.clients.review-service.targets.user-service=events:ingest`; istemci secret'ı `${AUTH_REVIEW_SERVICE_CLIENT_SECRET:}`.
- Review üreticisi **uygulandı** (outbox V59 + teslim işçisi; yerel testler PASS) ve Auth/User yerel sahte sunucularla doğrulandı; **canlı Auth/User ile doğrulanmadı**. İşçi, `AUTH_REVIEW_SERVICE_CLIENT_SECRET` boşken hiç kurulmaz (Auth'a da User'a da istek atılmaz); secret'ın ortama eklenmesi **ENV değişikliğidir ve kullanıcı onayı** gerektirir. Bugün User'daki `review_count` bu yüzden Review'dan beslenmez; `PublicProfileService` değeri yoksa `UNAVAILABLE` gösterir (0 uydurmaz).

**B için**

- Review yanıtları (`ReviewResponse`, öne çıkanlar, `reviews/mine`) yazar kimliği, kullanıcı adı veya avatar **içermez**; yalnız `ownedByActor` bayrağı vardır. Review frontend'i bu yüzden yazar adı/avatarı/profil bağlantısı göstermez (uydurmaz).
- User'da tek kullanıcı için herkese açık profil (`GET /api/v1/user/profiles/{userId}`, gizlilik kurallarına tabi) ve ad araması (`GET /api/v1/user/search?q=`) vardır. **Toplu kimlikle profil kartı çözümleme yoktur**; bir listede 20–50 yazar için tek tek çağrı N+1 olur ve gizlilik kararı her çağrıda ayrıdır.

## 6. Eksik gereksinimler

### A. `ReviewSummaryChanged` (Review → User)

- **[User] Kabul onayı (açık):** Review `occurredAt`'ı kullanıcı başına kesin artan üretir ve `reviewCount` olarak yalnız `VISIBLE` incelemeleri sayar; aynı `eventId` yeniden denemelerde sabittir. User'ın eşit zamanlı olayı bilerek yok saydığının, `eventId` tekrarının güvenli olduğunun ve `reviewCount` üst sınırının teyidi gerekir. Zarf: `eventId`, `eventType=ReviewSummaryChanged`, `eventVersion=1`, `producer=review-service`, `aggregateId=userId`, `occurredAt`, `payload{userId,reviewCount}` — `POST {USER_SERVICE_URL}/internal/events`.
- **[Review] Etkinleştirme (açık):** (1) secret için ENV onayı; (2) ilk etkinleştirmede mevcut kullanıcıların sayaçları için **backfill** (outbox yalnız değişiklikleri izler; etkinleştirmeden önce yazılmış incelemeler için olay üretilmemiştir) — gerekirse tek seferlik bir yeniden-yayın aracı; (3) gizlenen/arşivlenen inceleme sayılacak mı (REV-15 kararı; bugün yalnız `VISIBLE`).
- **[Auth]** Yeni çift gerekmez (mevcut).

### B. Yazar görünümü (karar gerekir — User ve Review birlikte)

Review'ın bunu tek başına çözmesi **uygun değildir**: yazar kimliğini her görüntüleyene vermek, User'daki profil/gizlilik ayarlarını (gizli profil, engelleme, yalnız arkadaşlar) atlatabilir.

- **[Review + User] Karar:** Review yanıtına yazar kimliği (`authorUserId`) eklenmeli mi? Önerilen güvenli yön: Review ham kimliği yalnız User'ın görünürlük kararı verdiği bir alan olarak taşımaz; aşağıdaki seçeneklerden biri seçilir:
  1. **Opak yazar belirteci (önerilen):** Review `authorRef` gibi kullanıcıya bağlı, geri çevrilemez ve kullanıcılar arası eşlenemeyen bir değer verir; User yalnız kendi ilişkisi/gizlilik kararıyla çözer. Bu seçenek User'da yeni bir çözümleme sözleşmesi ister.
  2. **Ham `userId` + User toplu kart ucu:** Review `authorUserId` döndürür; User toplu ve gizlilik duyarlı bir kart ucu sunar (aşağıda). Engelleme/gizlilik kararı User'da verilir; Review'a sızmaz.
  3. **Yazarsız (bugünkü durum):** Anonim inceleme kartları.
- **[User] Toplu görünür kart ucu (seçenek 2 ise):** `POST /api/v1/user/profiles/cards` benzeri; `{userIds: UUID[≤50]}` → her biri için görünür `{userId, displayName, username, avatarUrl}` veya "görünmez" (gizli/engelli/silinmiş için ayrım sızdırmayan aynı yanıt). Kimliği doğrulanmış çağıran bağlamıyla (kullanıcı oturumu) çalışır; Gateway üzerinden; N+1 olmadan. Önbellek/gizlilik güncellemesi gecikmesi belgelenmeli.
- **[Review]** Hangi seçenek seçilirse seçilsin: silinmiş hesabın incelemeleri Review'da zaten fiziksel silinir; yazar çözümleme başarısız olursa kart yazarsız gösterilir (inceleme içeriği gizlenmez).
- **[Frontend]** Karar sonrası inceleme kartı/detayı yazar adı, avatar ve (görünürse) profil bağlantısı için hazırdır; bugün bu alanlar ve bağlantı yoktur ve uydurulmaz.

## 7. Önerilen sözleşme — kesinleşmedi

**A.** `ReviewSummaryChanged` v1 zarfı: `{ eventId, eventType:"ReviewSummaryChanged", eventVersion:1, producer:"review-service", aggregateId:<userId>, occurredAt:<UTC>, payload:{ userId:<aynı UUID>, reviewCount:<int ≥ 0> } }`. Teslim: User'ın mevcut olay alma ucu (sözleşmesi [docs/api.md → USER](../../docs/api.md)); kalıcı ACK ve tekrar davranışı User'ın `inbox_events` tekilleştirmesine uyar. Payload'a inceleme metni, oyun adı, e-posta veya başka kişisel veri girmez.

**B.** Toplu kart ucu önerisi yukarıdadır; alan adları ve yol User sahibine aittir.

## 8. Kimlik doğrulama ve yetkilendirme

- **A:** Auth `svc+jwt`: `iss=ltz-auth`, tek `aud=user-service`, `sub=svc:review-service`, `scope` içinde `events:ingest`, ≤60 sn. Token Auth `POST /internal/v1/service-tokens` ile `clientId=review-service`, gizli anahtar bellekte; gerçek secret belgeye/loga yazılmaz. Kullanıcı token'ına fallback yoktur; 401/403'te kontrolsüz retry yoktur.
- **B:** Kullanıcı oturumu (cookie→Bearer, Gateway). Review, User'ın görünürlük kararını **taklit etmez**; iç ağdan kimlik çözüp frontend'e yaymaz.

## 9. Hata ve güvenilirlik davranışı

- Outbox → at-least-once; aynı `eventId` tekrarı User'da `DUPLICATE`, zararsız. Timeout sonrası aynı `eventId` ile sınırlı retry; 4xx (şema/yetki/producer) kalıcı hata olarak görünür operasyonel duruma düşer.
- User kesintisi Review kullanıcı işlemlerini (puan, inceleme, beğeni) **engellemez**; sayaç gecikmeli tutarlıdır.
- User'daki sayaç bilinmiyorsa `UNAVAILABLE` gösterilir, 0 uydurulmaz (mevcut davranış korunmalı).
- B için: User toplu uç kesintisinde Review listesi yazarsız yine görünür.

## 10. Kabul kriterleri

- Gerçek Auth token'ıyla Review→User olayı kabul edilir; aynı `eventId` tekrarı sayacı değiştirmez; eski/eşit `occurredAt` olayı geri almaz; yanlış producer/audience/scope/kullanıcı token'ı reddedilir.
- İnceleme oluşturma ve silme sonrası User profilindeki sayı doğru görünür; hesap silme sonrası eski olay silinmiş profili geri oluşturmaz.
- (B seçilirse) gizli/engelli/silinmiş yazar için kart ucu ayrım sızdırmaz; 50 yazar tek çağrıyla çözülür; Review listesi User kesintisinde çalışır.
- Canlı Auth↔Review↔User uçtan uca doğrulaması ayrıca yapılmalıdır (bugün NOT VERIFIED).

## 11. Bağımlılık ve etki

REV-10 tam kapanışı Review→User üreticisine ve diğer sözleşmelere bağlıdır. A, kullanıcı işlemlerinin kabulünü engellemez. B kararı verilene kadar Review arayüzü yazarsız kalır; bu eksik raporlanmıştır, hiçbir backend/frontend davranışı varmış gibi gösterilmez.

## 12. Durum

**A — ÜRETİCİ UYGULANDI (Review, secret boşken kapalı), ALICI HAZIR (User), AUTH ÇİFTİ MEVCUT.** Açık: ENV onayı, backfill, canlı doğrulama ve User'ın teyidi.
**B — KARAR BEKLİYOR (User + Review).** Hiçbir kod yazılmadı.

## 13. Karar bekleyen konular

1. Yazar görünümü için seçenek (opak belirteç / ham `userId` + toplu kart / yazarsız) ve gizlilik kuralları (gizli profil, engelleme, "yalnız arkadaşlar").
2. User toplu kart ucunun varlığı, yolu ve yanıt biçimi.
3. User'ın eşit zamanlı `ReviewSummaryChanged` olayı yok sayma davranışının teyidi (Review kesin artan zaman üretir).
4. `reviewCount` anlamı (yalnız `VISIBLE`; REV-15 sonrası gizlenenler).
5. `AUTH_REVIEW_SERVICE_CLIENT_SECRET` dağıtımı için ENV onayı (kullanıcı) ve ilk etkinleştirmede backfill kararı.
