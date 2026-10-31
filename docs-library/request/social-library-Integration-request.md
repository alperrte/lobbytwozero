# Social Service — Library Service Entegrasyon Talebi

Belge kimliği: `social-library-Integration-request` · Tarih: 30 Ekim 2026 (revizyon 2: karar yetkisi ve alternatifler)
Durum: **TALEP / ÖNERİ — henüz uygulanmadı.** Bu belge yalnız analiz ve gereksinim içerir; hiçbir servis kodu değiştirilmemiştir.

> **Karar yetkisi ilkesi.** Bu belgede belirtilen teknik yöntemler ve mimari seçenekler Library Service tarafından sunulan alternatiflerdir. Nihai teknik çözümü ilgili servisin sahibi kendi mevcut mimarisine, gereksinimlerine ve güvenlik standartlarına göre belirler. Library Service, kararlaştırılan güvenli entegrasyon sözleşmesine kendi tarafında uyarlanacaktır.

Okuma notu: Bu belge tek başına okunabilir. Aynı entegrasyonun diğer üç belgesi (`auth-…`, `user-…`, `recommendation-library-Integration-request.md`)
aynı klasördedir ve aynı **zorunlu gereksinimleri** ve aynı **başlangıç öneri tablolarını** taşır. Social kendi arkadaşlık ve engelleme API mimarisini belirler; Library'nin önerdiği
endpoint, DTO veya yetki biçimi **zorunlu değildir**. Mevcut Social API'leri ihtiyaçları karşılıyorsa yeni endpoint istenmez. Seçiminiz birden fazla servisi etkiliyorsa uygulamadan önce
ilgili sahiplerle (User: Kerem, Auth: Kerem) paylaşın ve sonucu Library sorumlusuna (Hamza) iletin.

Etiketler: **MEVCUT** = repo kodunda doğrulandı · **PROPOSED / ÖNERİ** = Library'nin sunduğu, bağlayıcı olmayan örnek; ilgili sahip onaylamadı ·
**ZORUNLU GEREKSİNİM (ZG)** = yöntemden bağımsız güvenlik/işlevsel şart · **EKİP KARARI (D-xx)** = sahipler arasında karara bağlanacak madde.

---

## A) Genel Bilgiler

| Alan | Değer |
| --- | --- |
| Talep eden servis | `library-service` (Hamza) — **dolaylı:** doğrudan tüketici `user-service`'tir (Library, kararı User'dan alır) |
| Hedef servis / sorumlu | `social-service` — **Umut** (arkadaşlık ve engelleme API mimarisinin sahibi) |
| Entegrasyonun amacı | User Service'in `FRIENDS` koleksiyon görünürlüğünü ve engel kuralını değerlendirebilmesi için Social'ın arkadaşlık/engel bilgisini **servisler arası, güvenli ve toplu** sunabilmesi. Veri Social'da kalır |
| İlgili Library görevleri | **LIB-11** (gizlilik; `FRIENDS` ve engel koşulları), **LIB-10** (dolaylı) |
| İlgili Social görevleri | SOC-07, SOC-09, SOC-12, **SOC-15** (servis sınırları ve entegrasyon) |
| Öncelik | **Öneri:** P1 — yalnız `FRIENDS` görünürlüğü ve engel kuralı için gerekir. `PRIVATE`/`PUBLIC`(engelsiz) kararlar Social'a ihtiyaç duymaz; User ve Library işleri Social beklenmeden başlayabilir. Sıra ve takvim servis sahiplerinin kararıdır |
| Bağımlılıklar | Auth: User→Social servis kimliği (Auth belgesi). User: tüketici (User belgesi). Karar maddeleri: **D-05, D-09** (bu belgede), D-10 (User) |

**Sonuç özeti (kısa):** Engel için mevcut `POST /internal/v1/blocks/check` işlevsel olarak yeterlidir; yalnız çağıran izin listesi (veya eşdeğer yetkilendirme) User'ı kapsamalıdır.
Arkadaşlık için mevcut hiçbir uç servis-servis kullanımına uygun değildir; küçük bir sorgu yeteneği gerekir — **biçimi Social'ın kararıdır** (D-09). Başka yeni özellik talep edilmez.

### A.1 Zorunlu gereksinimler (yöntemden bağımsız) — dört belgede aynıdır

Aşağıdakiler **teknik yöntem değil, güvenlik/işlevsel gereksinimdir.** Hangi çözüm seçilirse seçilsin karşılanmalıdır.
Yöntem (hangi claim, endpoint, scope adı, DTO) ise ilgili servis sahibinin kararıdır.

| Kod | Zorunlu gereksinim |
| --- | --- |
| **ZG-1** | Yetkisiz bir kullanıcı veya servis, başka bir kullanıcının özel koleksiyonuna (liste, sayaç, favori/bitirildi bilgisi) erişememelidir; kontrol sunucu tarafındadır, yalnızca frontend'e bırakılmaz. |
| **ZG-2** | Kimlik, yetki veya gizlilik/izin kararı doğrulanamadığında erişim açılmamalıdır (fail-closed): hata, zaman aşımı, bozuk veya eşleşmeyen yanıt hiçbir koşulda izin sayılmaz. |
| **ZG-3** | Kullanıcı kimliği (kullanıcı token'ı) ile servis kimliği ayrı doğrulanır; biri diğerinin yerine geçemez. Çağıran servis kimliği doğrulanır ve yalnız kendisine tanınan işleme yetkilidir (en az ayrıcalık). |
| **ZG-4** | Verileri görüntüleyen kullanıcının kimliği, istemcinin/çağıran servisin doğrulanmamış beyanından (body, query, header) değil, doğrulanmış kullanıcı bağlamından türetilir. Hiçbir servis keyfi bir kullanıcı adına erişim elde edemez. |
| **ZG-5** | Hiçbir rol (ADMIN, MODERATOR dahil) gizlilik kararını atlayamaz. |
| **ZG-6** | Öneri amacıyla işleme, diğer kullanıcılara görünürlükten ayrı bir amaçtır ve kullanıcının geri alınabilir iznine dayanır; izin dayanağının biçimi servis sahibinin kararıdır. |
| **ZG-7** | Erişim reddi veya izin doğrulama hatası başarı ya da "boş koleksiyon" olarak yorumlanmaz; reddedilme, geçici erişilemezlik ve gerçekten boş veri birbirinden ayrılır. |
| **ZG-8** | Veri sahipliği korunur: servisler birbirinin veritabanına erişmez; Library verisi başka servise kalıcı kopyalanmaz; tüketici yalnız amacı için gereken alanları alır. |
| **ZG-9** | Token, gizli anahtar ve kişisel veri listeleri loglanmaz; mevcut D4 hesap silme yetkisi (`account:erase`) okuma yetkisi olarak kullanılamaz ve bozulmaz. |
| **ZG-10** | Gizli, engelli veya var olmayan kullanıcı dış taraflara ayrıştırılabilir biçimde (yanıt gövdesi/süre sınıfı) farklı davranmaz; kullanıcı varlığı gereksiz açıklanmaz. |
| **ZG-11** | İzin geri alma ve görünürlük değişikliğinin etkili olma süresi servis sahibince açıkça tanımlanır, belgelenir ve Library'ye bildirilir; tanımsız süreyle eski izin kullanılmaz. |

Bu belgede Social'ı doğrudan ilgilendirenler: **ZG-2, ZG-3, ZG-7, ZG-8, ZG-9, ZG-10**.

### A.2 Library tarafından önerilen başlangıç entegrasyon sözleşmesi (T1–T4, E1–E5) — dört belgede aynıdır

> Aşağıdaki tablolar **Library'nin önerdiği başlangıç sözleşmesidir.** Auth veya diğer servis sahiplerince onaylanmış/kesinleşmiş bir sözleşme **değildir**.
> Scope, claim, endpoint ve DTO adları örnektir; bir servis farklı bir çözüm seçerse dört belgedeki ilgili satırlar birlikte güncellenir (bkz. A.3).

Önerilen token türleri (hepsi `typ=svc+jwt`, RS256, `iss=ltz-auth`, tek `aud`, ≤ 60 sn):

| Kod | Çağıran (`sub`) | Hedef (`aud`) | `scope` | Edinme yolu | Ek claim'ler |
| --- | --- | --- | --- | --- | --- |
| **T1** | `svc:user-service` | `library-service` | `library:profile:read` | **E1** delegasyon ucu, mod `VIEWER_BOUND` | `target_user_id`, `viewer_id` |
| **T2** | `svc:recommendation-service` | `library-service` | `library:recommendation:read` | **E1**, mod `TARGET_BOUND` | `target_user_id` (viewer_id **yok**) |
| **T3** | `svc:library-service` | `user-service` | `privacy:library:check` | MEVCUT `POST /internal/v1/service-tokens` (düz client credentials) | — |
| **T4** | `svc:user-service` | `social-service` | `blocks:check friendships:check` | MEVCUT düz uç | — |

Önerilen uçlar:

| Kod | Servis | Uç | Durum |
| --- | --- | --- | --- |
| **E1** | Auth | `POST /internal/v1/service-tokens/delegated` | **PROPOSED** — Library önerisi, Auth onayı yok |
| **E2** | User | `POST /internal/v1/users/{ownerId}/library-access-check` | **PROPOSED** — Library önerisi, User onayı yok |
| **E3** | Social | `POST /internal/v1/blocks/check` | MEVCUT uç; çağıran izin listesinin genişletilmesi **öneridir** (Social onayı yok) |
| **E4** | Social | `POST /internal/v1/friendships/check` | **PROPOSED** — Library önerisi, Social onayı yok |
| **E5** | Library | `GET /internal/v1/library/users/{userId}/entries`, `…/entries/statistics`, `…/recommendation-signals` | MEVCUT (Library) |

İstemci gizli anahtar değişkenleri (öneri, mevcut adlandırma desenine uyar): `AUTH_USER_SERVICE_CLIENT_SECRET`, `AUTH_LIBRARY_SERVICE_CLIENT_SECRET`,
`AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET`.

Social'ın doğrudan ilgilendiği: **T4, E3, E4**. Library ve Recommendation Social'ı çağırmaz.

### A.3 Alternatif seçilirse hangi sözleşmeler yeniden kararlaştırılır — dört belgede aynıdır

| Seçilen karar | Yeniden kararlaştırılacak sözleşmeler | Etkilenen belgeler | Library'de olası uyarlama (Hamza) |
| --- | --- | --- | --- |
| D-01, D-13 (delegasyon/token modeli) | T1, T2 (claim/scope/aud adları, edinme yolu), E1 | Auth, User, Recommendation | `LibraryInternalReadController` claim okuma, `AccountErasureSecurityConfiguration` yetki eşlemesi, test token'ları |
| D-08 (token ömrü) | T1–T4 ömür sınırı | Auth, tüm tüketiciler | Yok (≤ 60 sn kabul); > 60 sn istenirse Library doğrulayıcı sınırı değişir (önerilmez) |
| D-11 (hedef hesap denetimi) | E1 hata kodları, E2 owner yoksa davranış | Auth, User | Yok (Library zaten User kararına bağlı) |
| D-02 (profilde veri) | E5 kullanım biçimi, T1 gerekliliği | User, Auth | Yok (iki yol da Library'de mevcut) |
| D-03, D-04, D-06 (görünürlük/izin/hesap durumu) | E2 gövde/yanıt/hata anlamı | User, Library, Recommendation | `LibraryVisibilityPort` HTTP adaptörü eşlemesi; izin amacı farklı granülerlikteyse `recommendation-signals` alan süzme |
| D-05, D-09, D-10 (engel/arkadaşlık/ortak mantık) | E2 iç akışı, E3/E4, T4 | User, Social, Auth | Yok (Library Social'ı çağırmaz); E2 şekli değişirse adaptör |
| D-07, D-12 (izin geri alma, kısmi veri) | Recommendation'ın veri tutma/sonuç modeli; isteğe bağlı olay sözleşmesi | User, Recommendation | Yok; Library tarafında karar önbelleği istenirse eklenir |

### A.4 Seçilen sözleşmenin Library'ye bildirilmesi (teslim şablonu) — dört belgede aynıdır

Servis sahibi kararını verdiğinde Library sorumlusuna (Hamza) şunları iletir; Library kendi tarafında buna uyarlanır, **Library kodunu servis sahibi değiştirmez**:

1. Seçilen yaklaşım ve gerekçesi (öneri/alternatif/özgün çözüm), karar tarihi ve sahibi.
2. Uç(lar): yöntem, yol, yetkilendirme (token tipi, `aud`, `scope`/eşdeğeri, zorunlu claim'ler), ömür.
3. İstek/yanıt şeması (alan adları, tipler, örnek JSON), `Cache-Control` ve yeniden deneme beklentisi.
4. Hata kodları ve **Library'nin her biri için nasıl davranması gerektiği** (izin / ret / geçici erişilemez).
5. Zaman aşımı/bütçe önerileri; izin iptalinin etkili olma süresi (ZG-11).
6. Diğer servis sahipleriyle uzlaşma kaydı (hangi belgeler güncellendi).
7. Test kanıtı (sahte sunucu mu, gerçek servis mi; gerçek E2E yapılmadıysa açıkça "NOT VERIFIED").

---

## B) Mevcut Durum

### B.1 Social'da MEVCUT olanlar (kodla doğrulandı)

| Parça | Kanıt | Not |
| --- | --- | --- |
| Engel sorgusu | `BlocksCheckController` · `POST /internal/v1/blocks/check` · `BlockCheckService` | Gövde `{userId, otherUserIds[1..100]}`; yanıt `{results:[{userId, blocked}]}`; **iki yön** birlikte değerlendirilir; yön/zaman sızdırılmaz; yinelenenler tek sonuç; kendi kimliği `blocked=false` |
| Yetki | `SecurityConfig`: `POST /internal/v1/blocks/check` → `SCOPE_blocks:check`; `BlocksCheckController`: `sub` ∈ `TokenClaims.BLOCKS_CHECK_SUBJECTS = {svc:messaging-service, svc:lfp-service}` | Kapsam doğru olsa da başka servis kimliği `403`. **`svc:user-service` listede yok** |
| Servis token doğrulama | `JwtConfig` + `SecurityConfig.serviceTokens` | `typ=svc+jwt`, RS256/JWKS, `iss`, `aud` ∋ `social-service`, `jti`, ≤ 60 sn; kullanıcı token'ı iç uçta geçmez |
| Arkadaşlık modeli | `Friendship` / `FriendshipId(userLowId, userHighId)` · V121 | Çift **tek satır**; "arkadaş" satırın diğer tarafı. Engel konunca arkadaşlık sonlanır (`BlockService.block` → `endFriendship`), bekleyen istekler kapanır |
| Arkadaşlık okuma | `GET /api/v1/social/friends` (yalnız çağıranın kendi listesi), `GET /api/v1/social/users/{userId}/friends` (SOC-12) | Kullanıcı token'ıyla; ikincisi görünürlük kuralına (User tercihi, engel) bağlı |
| Gelen port | `FriendsVisibilityPort` ← `UnavailableFriendsVisibility` | Social, kendi SOC-12 kararı için User'dan **tercih** bekliyor; **Library işiyle ilgisiz**, bu belgenin ön koşulu değil |
| Giden istemci | `AuthServiceTokenClient` | Social'ın kendi giden çağrıları (community olayları); bu işte kullanılmaz |
| Migration | V121–V124 (aralık V121–V130) | Bu işte migration gerekmeyebilir (seçilen yönteme bağlı) |
| Test | `BlocksCheckTest` (13), `AccountErasureTest` (+4); modül 218 test | `docs/api.md` SOCIAL: engel sorgusu kaydı `IMPLEMENTED`, tüketiciler `messaging`, `lfp` |

### B.2 Mevcut uçlar User ihtiyacını karşılıyor mu? — analiz

| İhtiyaç | Mevcut uç | Karşılıyor mu? | Gerekçe |
| --- | --- | --- | --- |
| "Viewer ile owner arasında (herhangi yönde) engel var mı?" | `POST /internal/v1/blocks/check` | **İşlevsel olarak evet** | `userId=viewer`, `otherUserIds=[owner]`. Tek eksik: çağıran yetkisi (`svc:user-service` listede değil) ve Auth'ta ilgili servis kimliği |
| "Viewer, owner'ın arkadaşı mı?" | `GET /api/v1/social/users/{ownerId}/friends` | **Hayır** | Kullanıcı token'ı ister (servis çağıramaz); sonuç User'ın `friends_visibility` tercihine bağlı (karar için kararı kullanma döngüsü); sayfalı listeden çıkarım gereksiz veri |
| (aynı) | `GET /api/v1/social/friends` | **Hayır** | Yalnız token sahibinin listesi; servis, viewer adına çağıramaz (kullanıcı token'ı iletmek ZG-3/4 sorunu) |
| (aynı) | `blocks/check` yanıtını genişletmek | **Tartışmalı** | `messaging`/`lfp` sözleşmesini etkiler ve onlara arkadaşlık bilgisi açar (en az ayrıcalık) — D-09 B seçeneği |

Sonuç: **Engel kısmı mevcut uçla karşılanır; arkadaşlık kısmı için bir sorgu yeteneği eksiktir.** Kullanıcı arayüzüne dönük hiçbir Social ucu değişmez.

### B.3 EKSİK olanlar (gerçek entegrasyonu engelleyen noktalar)

1. `BLOCKS_CHECK_SUBJECTS` içinde `svc:user-service` yok → User, mevcut uçta yetkisiz kalır (yetkilendirme biçimi Social'ın kararıdır).
2. Servis kimliğiyle sorulabilen **arkadaşlık sorgusu yok.**
3. Auth'ta `user-service` → `social-service` istemci kaydı ve ilgili kapsam yok (Auth belgesi).
4. `docs/api.md` SOCIAL'da engel sorgusunun "Tüketici servisler" alanı yalnız `messaging`, `lfp`.

---

## C) Beklenen Geliştirmeler

C bölümü iki türdür: **(1) Zorunlu çıktılar** — hangi yöntemle olursa olsun Social'ın sağlaması gerekenler; **(2) Önerilen teknik yöntem** — Library'nin örnek çözümü, bağlayıcı değil.

### C.1 Zorunlu çıktılar (yöntemden bağımsız)

1. User'ın, servis kimliğiyle **toplu** olarak "iki kullanıcı arasında engel var mı" ve "iki kullanıcı arkadaş mı" bilgisini alabilmesi; yetki, çağıran servise ve amaca özgü olmalıdır (ZG-3).
2. Sorgular **salt okuma**dır; sonuç yalnız gereken bilgiyi taşır (kimlik + boolean); tarih, neden, engel yönü, sayaç gibi ek veri yoktur (ZG-8, ZG-10).
3. Social **hiçbir hata yolunda** "ilişki yok/engel yok" izlenimi vermez; hata hata olarak döner (ZG-2, ZG-7). Kısmi sonuç yoktur.
4. Olmayan, engelli veya arkadaş olmayan kullanıcılar sonuç gövdesinde ayrıştırılamaz (arkadaşlık sorgusunda "arkadaş değil" tek sonuçtur) (ZG-10).
5. Arkadaşlık/engel verisi Social'da kalır; User kopyalamaz (ZG-8). Kullanıcı arayüzüne dönük mevcut uçlar, D4 silme akışı ve olaylar değişmez.
6. Tutarlılık: engel varken arkadaşlık "var" olarak raporlanmaz (mevcut iş kuralı: engel arkadaşlığı sonlandırır).
7. Log: token, kimlik listeleri ve sonuçlar INFO'da yazılmaz (ZG-9).
8. `docs/api.md` SOCIAL güncellemesi ve seçilen sözleşmenin A.4'e göre Library'ye (ve User'a) bildirilmesi.

### C.2 Önerilen teknik yöntem (bağlayıcı değil): E3 — çağıran izin listesi

Mevcut `blocks/check`'te `TokenClaims.BLOCKS_CHECK_SUBJECTS` → `{svc:messaging-service, svc:lfp-service, svc:user-service}`. Gövde, yanıt, kapsam ve limitler **değişmez**; diğer servis kimlikleri (örn. `svc:library-service`) kapsam taşısa da `403`.
Kullanım örneği: `{ "userId": "<viewerId>", "otherUserIds": ["<ownerId>"] }` → `{ "results": [ { "userId": "<ownerId>", "blocked": false } ] }`.
`docs/api.md` kaydı: "Tüketici servisler" += `user-service`.
*Alternatif:* `user-service` için ayrı bir kapsam/uç veya Social'ın yetkilendirme modeline uyan başka bir yöntem (Social karar verir).

### C.3 Önerilen teknik yöntem (bağlayıcı değil): E4 — arkadaşlık sorgusu (örnek)

> D-09 Seçenek A'nın örneğidir; Social farklı bir uç/biçim seçebilir. Örnekler gerçek uygulama değildir.

```http
POST /internal/v1/friendships/check
Authorization: Bearer <T4>
Content-Type: application/json
```

Örnek yetkilendirme (E3 ile aynı desen, **ayrı kapsam**): servis token'ı (`typ=svc+jwt`, RS256/JWKS, `iss`, `aud` ∋ `social-service`, `jti`, ≤ 60 sn) · `scope` içinde `friendships:check` ·
`sub` tam `svc:user-service`. Kullanıcı token'ı bu uçta geçmez; `/internal/**` Gateway'e açılmaz.

```json
{ "userId": "9a7c2d10-1b3e-4f6a-8d5c-0e1f2a3b4c5d", "otherUserIds": ["3f2b8c1e-5a4d-4e9b-9c1a-7d6e5f4a3b21"] }
```

```json
{ "results": [ { "userId": "3f2b8c1e-5a4d-4e9b-9c1a-7d6e5f4a3b21", "friends": true } ] }
```

Örnek semantik: `friends=true` ⇔ kabul edilmiş arkadaşlık var (simetrik); aralarında herhangi yönde engel varsa `friends=false`; kendi kimliği/olmayan kullanıcı → `false`; yinelenen kimlik tek sonuç;
1–100 kimlik; mevcut 8 KB gövde sınırı; bilinmeyen alan reddedilir; yanıtta `since`, istek geçmişi, engel yönü yok. Hata: `400` (geçersiz gövde), `401` (token), `403` (kapsam/çağıran), `413`, `500` —
**hata asla `friends=false` olarak maskelenmez**; çağıran bunu "bilinmiyor" sayar (ZG-2).
Uygulama deseni (öneri): `BlockCheckService` benzeri salt-okuma servisi + `FriendshipRepository`'ye tek toplu sorgu; engel için mevcut `UserBlockRepository.findBlockedAmong/findBlockersAmong`.

### C.4 Güvenlik ve gizlilik gereksinimlerinin Social'a yansıması

- **En az ayrıcalık (ZG-3):** engel ve arkadaşlık bilgileri ayrı yetkilerle korunmalı; `messaging`/`lfp` arkadaşlık bilgisini görememeli (seçilen yönteme göre kapsam, uç veya izin listesi).
- **Sızıntı yok (ZG-10):** yanıtlarda yön, zaman, neden, sayaç yok; hata gövdesi gönderilen değerleri yansıtmaz.
- **Kötüye kullanım:** en çok 100 kimlik/istek (mevcut desen); isteğe bağlı çağıran başına hız sınırı.
- **Kapsam sınırı:** `FriendsVisibilityPort` gerçek adaptörü (SOC-12, User'dan tercih okuma) **bu işin parçası değildir.** Bilgi: User→Social karar zinciri ile Social→User tercih okuması döngü oluşturmamalıdır (User'ın tercih okuma ucu Social'ı çağırmaz).
- Yeni arkadaşlık listesi ucu, görünürlük ayarı, profil veya engel özelliği **istenmez.**

### C.5 `docs/api.md` kaydı ve yapılandırma

SOCIAL bölümünde seçilen değişiklikler şablonla yazılır (`Durum`, `Sorumlu: Umut`, `Tüketici servisler: user-service`, `Yetkilendirme`, `Request Body`, `Response`, `Hata Durumları`, `Bağlantı`, `Test Durumu`).
Kod+test var ama gerçek User/Auth ile denenmediyse bağlantı `NOT VERIFIED`. Yalnız Social bölümünü düzenleyin. Social'a **yeni ENV gerekmez** (gelen çağrılar JWKS ile doğrulanır); istemci gizli anahtarı User ve Auth ortamlarına aittir.

### C.6 Mimari kararlar — ayrıntılı alternatifler (Social'ın yetkisinde: D-05 [User ile birlikte], D-09)

Aşağıdakiler Library'nin sunduğu **alternatif analizleridir.** Nihai karar Social sahibindedir; ZG'yi karşılayan başka bir çözüm de seçilebilir.
Uygulanabilirlik etiketleri: **U1** mevcut mimariyle uyumlu · **U2** ek geliştirme gerektirir · **U3** güvenlik açısından riskli · **U4** ZG'yi karşılamıyor.

#### D-09 — Arkadaşlık kontrol endpointi · Sorumlu: Social Service

**Çözülen sorun.** `FRIENDS` görünürlüğünü değerlendirmek için User'ın "bu iki kullanıcı arkadaş mı?" sorusunu servis kimliğiyle ve toplu sorabilmesi gerekir.
**Mevcut durum.** Servis kimliğiyle sorulabilen arkadaşlık sorgusu yok (B.2). `blocks/check` bir emsaldir: toplu, iki yönlü, yalnız boolean. Arkadaşlık tek satırlık çift (`FriendshipId`) olarak saklanır.

| Seçenek | Açıklama · uygulanabilirlik | Avantaj | Dezavantaj | Güvenlik/gizlilik · performans/bakım | Mevcut tüketicilere etki · Library |
| --- | --- | --- | --- | --- | --- |
| **A — Ayrı `friendships/check` ucu** (U2) | `blocks/check` ile simetrik gövde; ayrı yetki | En az ayrıcalık; mevcut tüketiciler etkilenmez; basit semantik | İki uç, FRIENDS kararı için iki çağrı (engel + arkadaşlık); aralarında anlık görüntü farkı | Ayrı kapsam/çağıran listesi; tek toplu sorgu, yük düşük | Messaging/LFP: etkilenmez. Library: yok |
| **B — `blocks/check` yanıtını genişletmek** (`friends` alanı) (U1+U2) | Mevcut uca alan ekle | Tek çağrı, tek anlık görüntü; yeni uç yok | Messaging/LFP'ye arkadaşlık bilgisi açılır (ZG-3 en az ayrıcalık ihlali, çağıran bazlı alan gizleme gerekir); sözleşme değişimi | Alan gizleme karmaşası; geriye dönük uyumluluk riski | Messaging/LFP sözleşmesi etkilenir. Library: yok |
| **C — Birleşik ilişki ucu** (`relationships/check` → `{friends, blocked}`) (U2) | Yeni uç, iki bilgi | Tek çağrı ve tutarlı anlık görüntü; FRIENDS kararı kolaylaşır | `blocks/check` ile semantik tekrarı; iki bilgi tek yetkide birleşir (ayrıntılı yetki gerekebilir) | Kapsam ayrımı gerekir; tek sorguda iki tablo | Mevcut `blocks/check` değişmez. Library: yok |
| **D — Mevcut kullanıcı uçlarını servis adına kullanmak** (U3/U4) | Kullanıcı token'ını User'dan Social'a taşımak veya `users/{id}/friends` ile çıkarım | Ek geliştirme yok | Kullanıcı token'ı taşıma riski (ZG-3/4); sonuç User tercihine bağlı döngü; gereksiz kişisel liste yükü | Güvenli kabul edilmez | — |
| **E — Social'ın mimarisine uygun başka güvenli sorgu** (örn. tek çift için `GET`, olay tabanlı kopya vb.) | Social kararı | — | Kopya veri (ZG-8) veya bayatlık riski içeriyorsa uygun değil | — | Library: yok |

Mevcut tüketicilerin etkilenmesi: yalnız B'de (messaging/lfp'nin gördüğü sözleşme değişir). En az ayrıcalık açısından A ve C ayrı yetkiyle iyi çalışır; B ek önlem ister.
**Library önerisi (yalnız tercih):** A. Gerekçe: mevcut iki tüketicinin sözleşmesi hiç değişmez. Library etkisi: yok (Library Social'ı çağırmaz; etki User'ın adaptöründe).

#### D-05 — Engellenen kullanıcıların koleksiyon erişimi · Sorumlu: User Service ve Social Service (Social perspektifi)

Karar ayrıntısı **User belgesi C.9'dadır**; Social açısından özet:

| Seçenek | Social'a etkisi |
| --- | --- |
| A — Engel PUBLIC ve FRIENDS için de uygulanır | `blocks/check` her non-owner kararda çağrılır (yük artar); Social erişilemezse PUBLIC de geçici erişilemez olur |
| B — Yalnız FRIENDS'te | Social yalnız FRIENDS yolunda çağrılır; engellenen kişi PUBLIC'i görür (SOC-12 ile tutarsız) |
| C — Mevcut platform politikası | Social'ın engel semantiği (iki yön) belirleyicidir; politika değişirse bu değişir |
| D — Yönlü engel | `blocks/check` yön sızdırmaz (ZG-10); yön gerekirse yeni sözleşme gerekir |

Social'ın sorumluluğu: engel semantiğini (iki yön, yön sızdırmama, arkadaşlığı sonlandırma) açık tutmak ve değişiklikte tüketicilere bildirmek.
**Library önerisi (yalnız tercih):** A/C. Nihai karar User ve Social sahiplerinde. Library etkisi: yok.

---

## D) Sıralı Geliştirme Checklisti

Bu belge Library'nin orijinal gereksinim dokümanıdır: aşağıdaki kutular `[ ]` olarak kalır ve servis sahibi tarafından değiştirilmez.
Tamamlanma durumu, kabul kriteri **gerçekten** sağlandığında servis sahibinin kendi sonuç raporunda ve çalışma sonunda sunduğu checklistte `[x]` olarak izlenir.
Kabul kriterleri **ZG-x** ile yazılmıştır; "Örnek yöntemde" ifadesi C bölümündeki öneriyi gösterir, başka yöntem seçildiyse eşdeğer kriter uygulanır ve A.4'e göre bildirilir.

- [ ] **SOC-LIB-01 — Mimari kararların verilmesi ve kayda geçirilmesi.** D-09 (ve D-05 Social tarafı) için Social kararı; User ve Auth sahipleriyle paylaşım.
  *Kabul:* karar ve gerekçe yazıldı; A.3'e göre etkilenen sözleşmeler listelendi.
- [ ] **SOC-LIB-02 — Engel sorgusunun User tarafından yetkili kullanımı.** User'ın mevcut engel sorgusunu kullanabilmesi için çağıran yetkisi.
  *Kabul (ZG-3):* `svc:user-service` (veya seçilen eşdeğeri) sorgulayabilir; diğer servis kimlikleri (örn. `svc:library-service`, `svc:recommendation-service`) kapsam taşısa da reddedilir; mevcut messaging/lfp testleri aynen yeşil; `account:erase` token'ı yetmez. *Örnek yöntemde:* C.2.
- [ ] **SOC-LIB-03 — Arkadaşlık sorgu yeteneği.** Seçilen D-09 yöntemiyle servis kimliğine açık, toplu, salt-okuma sorgu.
  *Kabul (ZG-2, ZG-8, ZG-10):* simetri, engel→`false`, kendi kimliği, olmayan kullanıcı, yinelenen kimlik, ≤ 100, sıra korunumu; yanıtta fazla alan yok; hata `false`'a maskelenmez. *Örnek yöntemde:* C.3.
- [ ] **SOC-LIB-04 — Yetki ayrımı.**
  *Kabul (ZG-3):* engel yetkisi arkadaşlık sorgusuna yetmez ve tersi (ayrı yetkilendirme seçildiyse); `messaging` arkadaşlık bilgisini alamaz; kullanıcı token'ı iki sorguda da reddedilir.
- [ ] **SOC-LIB-05 — Belgeler.** `docs/api.md` SOCIAL (C.5), `docs-security/social-service/NOTLAR.md` (varsa not).
- [ ] **SOC-LIB-06 — Gerçek Auth/User ile doğrulama (User hazır olunca).** Gerçek servis kimliğiyle çağrı; sonuç `docs/api.md`'ye gerçeği yansıtacak biçimde işlenir.
- [ ] **SOC-LIB-07 — Sözleşmenin Library/User'a bildirilmesi.** A.4 şablonuyla Hamza ve Kerem'e teslim.

---

## E) Test Senaryoları

Senaryolar **gereksinim temellidir**; ayrıntılı alan adları seçilen yönteme göre uyarlanır.

**Unit**
- Arkadaşlık sorgu mantığı: simetri (A→B ve B→A), engelli çift `false`, kendi kimliği, olmayan kullanıcı, yinelenen girdi, sıra korunumu, 100 sınırı.
- Yetkilendirme sabitleri/izin listeleri.

**Integration (gerçek PostgreSQL, gerçek RSA/JWKS — mevcut `BlocksCheckTest` altyapısı)**
- Engel sorgusu: User kabul; diğer servisler ret; iki yönlü engel; toplu sorgu.
- Arkadaşlık: arkadaş / arkadaş değil / bekleyen istek (`false`) / reddedilen istek (`false`) / engel sonrası (`false`) / arkadaşlıktan çıkarma sonrası (`false`).
- Arkadaşlık kabulünden hemen sonraki sorgu `true` (önbellek yok).
- Gövde sınırı (`413`), 101 kimlik (`400`), boş liste (`400`), bilinmeyen alan (`400`), kanonik olmayan UUID (`400`).

**Güvenlik / negatif**
- Token yok / `at+jwt` / yanlış `aud` / yanlış `typ` / süresi dolmuş / ömür > 60 sn / `jti` yok → `401`.
- Yetki çaprazlaması: engel yetkili token'la arkadaşlık sorgusu ve tersi (ayrı yetki seçildiyse); `account:erase` ile ikisi → `403`.
- Servis kimliği: yetki doğru ama çağıran yanlış (`svc:library-service`, `svc:messaging-service` arkadaşlık sorgusunda) → `403`.
- Yanıt incelemesi: `since`, engel yönü, neden, sayaç yok; hata yanıtı değeri yansıtmaz.
- Engel varken `friends=true` **asla** dönmez.

**Gerçek servisler arası E2E (Auth + User hazır olunca; Hamza ile)**
1. User, gerçek servis kimliğiyle engel ve arkadaşlık sorgularını çağırır → `200`.
2. `FRIENDS` owner, viewer arkadaş → Library `200`; arkadaşlık sonlandırılınca sonraki istekte `403`.
3. Viewer owner'ı engelleyince (D-05'e göre) PUBLIC bile `403`.
4. Social kapalıyken ilgili kararlar geçici erişilemez (`503`); `PRIVATE` `403`.

---

## F) Tamamlanma Kriterleri

Social işi **tamamlandı** sayılır, ancak ve ancak:

1. SOC-LIB-01…05 ve 07 kabul kriterleri sağlanmış (servis sahibinin sonuç raporundaki checklistte `[x]`), Social testleri yeşil (mevcut 218 test bozulmamış), `docs/api.md` gerçeği yansıtır.
2. Gerçek Auth servis kimliğiyle gerçek User, engel ve arkadaşlık sorgularından `200` alır; yetki çaprazlama negatifleri E2E veya eşdeğer entegrasyon testiyle gösterilmiştir.
3. Social'ın hiçbir hata yolunda "ilişki yok/engel yok" yanıtı üretmediği testle kanıtlıdır.
4. Seçilen sözleşme A.4'e göre Library ve User'a bildirilmiştir; gerçek E2E yapılmayan kısım `NOT VERIFIED` yazılmıştır.

Library tarafında (Hamza) doğrulanacak: `FRIENDS` ve engel koşullarında Library'nin bu bilgiyi **yalnız User kararı üzerinden** görmesi (Library Social'ı hiç çağırmaz) ve Social/User kesintisinde ret/geçici erişilemez ile kapalı kalması.
**LIB-11 bu E2E'den sonra işaretlenir; 15/18 şimdilik değişmez.**

Diğer servis sahiplerinden beklenen: Auth (User→Social servis kimliği), User (karar akışı ve Social istemcisi).

---

## G) Yapay Zekâ Geliştirme Talimatları

Bu bölümü, yapay zekâ geliştirme aracınıza **olduğu gibi** verin.

```text
Sen LTZ (LobbyTwoZero) reposunda social-service için çalışıyorsun. Sahip: Umut.
Görev: docs-library/request/social-library-Integration-request.md belgesindeki ihtiyaçları, Social'ın KENDİ mimarisine uygun biçimde karşılamak.

Çalışma sırası ve kurallar:
1. ÖNCE kendi servisinin (social-service) mevcut mimarisini incele: BlocksCheckController, BlockCheckService, SecurityConfig, TokenClaims, InternalApiPaths, Friendship/UserBlock
   modeli ve repository'leri, docs/api.md SOCIAL, docs-security/social-service/README.md ve NOTLAR.md. AGENTS.md ve CLAUDE.md zorunlu okuma sırasını uygula.
   Yeni Checkstyle/PMD ihlali ekleme (mevcut test metot adı borcunu büyütme).
2. Kendi gereksinimlerini (SOC-07/09/12/15) ve mevcut güvenlik sözleşmelerini değerlendir. Mevcut API'ler ihtiyaçları karşılıyorsa yeni endpoint ekleme.
3. Belgedeki T1–T4 / E1–E5 tabloları ve C.2–C.3 örnekleri Library'nin ÖNERDİĞİ başlangıç sözleşmesidir; ZORUNLU DEĞİLDİR. Önerilen endpoint, kapsam adı, DTO ve yetki biçimini
   kabul etmek zorunda değilsin. Belgedeki ZG-1…ZG-11 zorunlu gereksinimlerdir; bunları karşılamak zorundasın.
4. D-09 (ve D-05 Social tarafı) için belgedeki alternatifleri değerlendir; istersen önerilenlerden birini, istersen ZG'yi karşılayan kendi çözümünü seç. Seçimi ve gerekçesini yaz.
   Güvenli olmayan (U3/U4) bir seçeneği seçme.
5. Çok servisli etkisi olan sözleşmeyi (uç biçimi, yetki modeli, çağıran izin listesi) UYGULAMADAN ÖNCE User ve Auth sahibi (Kerem) ile Library sorumlusu Hamza ile koordine et.
   Mevcut tüketicilerin (messaging, lfp) sözleşmesini bozma. Belirsizlik veya çelişki varsa UYDURMA: dur, kullanıcıya sor.
6. Yalnız backend/services/social-service kodunu, testlerini, docs/api.md SOCIAL bölümünü ve docs-security/social-service/NOTLAR.md dosyasını değiştir. Migration gerekiyorsa yalnız V121–V130
   ve docs/decisions/migrations.md kuralları. auth-service, user-service, library-service, recommendation-service, frontend, gateway, Maven/CI dosyalarına DOKUNMA.
   Library'nin gerekli uyarlamasını KENDİN YAPMA; seçtiğin sözleşmeyi A.4 şablonuyla Library sorumlusuna bildir.
   docs-library/ altındaki hiçbir Library belgesini (bu belge dahil) DEĞİŞTİRME.
7. Mevcut arkadaşlık/engel kullanıcı uçlarını, D4 silme akışını ve outbox olaylarını değiştirme. Arkadaşlık/engel verisini başka servise taşıma.
8. .env, .env.dev, *.bak, frontend/.env.local dosyalarını OKUMA. ENV/Compose değişikliği için kullanıcı onayı al.
9. Güvenlik: hiçbir hata yolunda "ilişki yok/engel yok" üretme (hata 5xx'tir); kısmi sonuç yok; yanıta fazladan alan (since, neden, yön) koyma; yetkiler çaprazlanamasın;
   kimlik listeleri/token loglanmasın. Seçtiğin yaklaşımın ZG-1…ZG-11'i karşıladığını testlerle doğrula (belgenin E bölümü; gerçek PostgreSQL ve gerçek RSA/JWKS).
10. Pre-push ve SonarQube ÇALIŞTIRMA; bunları kullanıcı elle yapacak. Yalnız hedefli Maven testlerini çalıştır ve sonucu dürüstçe raporla.
11. git commit / git push YAPMA. İş bitince AGENTS.md §7 formatında yalnız commit mesajı öner (atıf satırı ekleme).
12. Bu belgenin (docs-library/request/) D bölümündeki checklisti DEĞİŞTİRME; belge Library'nin orijinal gereksinim ve sözleşme dokümanıdır ve `[ ]` olarak kalır.
    Tamamlanan görevleri kendi servisinin sonuç raporunda ve çalışma sonunda sunduğun checklistte [x] olarak işaretle. Gerçek Auth/User ile denenmeyen bağlantıyı docs/api.md'de NOT VERIFIED yaz; gerçek E2E yapılmadıysa entegrasyonu
    tamamlanmış gibi raporlama. Çalıştırılamayan veya başarısız adımı gizleme.
```

---

## H) Alternatifler ve Library Service Önerileri

Bu bölüm yalnız Social'ın sorumluluğundaki kararların özetidir (ayrıntı: C.6). Buradaki "Önerilen" alanları **yalnızca Library'nin tercihidir**; zorunlu değildir ve
Social sahibinin bunu seçmesi beklenmez. Güvenli ve işlevsel gereksinimleri (ZG) karşılayan her alternatif kabul edilebilir.

### D-05 — Engellenen Kullanıcıların Koleksiyon Erişimi

**Seçenekler:**
- Seçenek A: Engel PUBLIC ve FRIENDS görünürlüklerinde uygulanır.
- Seçenek B: Engel yalnız FRIENDS'te değerlendirilir.
- Seçenek C: Mevcut platform engelleme politikasının aynen kullanılması.
- Seçenek D: Yönlü engel (yalnız owner'ın engellediği viewer).

**Önerilen:** Seçenek A/C (Library'nin tercihi; yalnızca öneridir).

**Öneri gerekçesi:** SOC-12 ve Hardware politikasıyla tutarlıdır; engellenen kişinin PUBLIC koleksiyonu görmesi beklenmedik bir gizlilik ihlalidir.

**Nihai karar yetkisi:** İlgili servis sahipleri (Social — Umut ve User — Kerem).

**Library Service'e etkisi:** Yok; Library Social'ı çağırmaz ve yalnız User kararını görür. Yön bilgisi gerekirse (D) Social'ın sözleşmesi ve ZG-10 yeniden değerlendirilir.

### D-09 — Arkadaşlık Kontrol Endpointi

**Seçenekler:**
- Seçenek A: Ayrı bir arkadaşlık kontrol endpointi.
- Seçenek B: Mevcut engel kontrol endpointinin yanıtının genişletilmesi.
- Seçenek C: Birleşik ilişki kontrol endpointi (arkadaşlık + engel).
- Seçenek D: Mevcut kullanıcı uçlarının servis adına kullanılması (U3/U4 — önerilmez).
- Seçenek E: Mevcut Social mimarisine uygun başka bir güvenli sorgulama yöntemi.

**Önerilen:** Seçenek A (Library'nin tercihi; yalnızca öneridir).

**Öneri gerekçesi:** Mevcut iki tüketicinin (messaging, lfp) sözleşmesi hiç değişmez ve en az ayrıcalık ilkesi korunur.

**Nihai karar yetkisi:** İlgili servis sahibi (Social — Umut).

**Library Service'e etkisi:** Yok; Library Social'ı çağırmaz. Etki User'ın adaptöründedir ve User belgesindeki sözleşme buna göre güncellenir.
