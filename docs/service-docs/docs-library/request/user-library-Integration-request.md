# User Service — Library Service Entegrasyon Talebi

Belge kimliği: `user-library-Integration-request` · Tarih: 30 Ekim 2026 (revizyon 2: karar yetkisi ve alternatifler)
Durum: **TALEP / ÖNERİ — henüz uygulanmadı.** Bu belge yalnız analiz ve gereksinim içerir; hiçbir servis kodu değiştirilmemiştir.

> **Karar yetkisi ilkesi.** Bu belgede belirtilen teknik yöntemler ve mimari seçenekler Library Service tarafından sunulan alternatiflerdir. Nihai teknik çözümü ilgili servisin sahibi kendi mevcut mimarisine, gereksinimlerine ve güvenlik standartlarına göre belirler. Library Service, kararlaştırılan güvenli entegrasyon sözleşmesine kendi tarafında uyarlanacaktır.

Okuma notu: Bu belge tek başına okunabilir. Aynı entegrasyonun diğer üç belgesi (`auth-…`, `recommendation-…`, `social-library-Integration-request.md`)
aynı klasördedir ve aynı **zorunlu gereksinimleri** ve aynı **başlangıç öneri tablolarını** taşır. User kendi profil/gizlilik mimarisini belirler; Library'nin önerdiği
endpoint, DTO, alan veya akış **zorunlu değildir**. Seçiminiz birden fazla servisi etkiliyorsa uygulamadan önce ilgili sahiplerle (Auth: Kerem, Recommendation: Alper,
Social: Umut) paylaşın ve sonucu Library sorumlusuna (Hamza) iletin.

Etiketler: **MEVCUT** = repo kodunda doğrulandı · **PROPOSED / ÖNERİ** = Library'nin sunduğu, bağlayıcı olmayan örnek; ilgili sahip onaylamadı ·
**ZORUNLU GEREKSİNİM (ZG)** = yöntemden bağımsız güvenlik/işlevsel şart · **EKİP KARARI (D-xx)** = sahipler arasında karara bağlanacak madde.

---

## A) Genel Bilgiler

| Alan | Değer |
| --- | --- |
| Talep eden servis | `library-service` (Hamza) |
| Hedef servis / sorumlu | `user-service` — **Kerem** (profil ve gizlilik yönetiminin sahibi) |
| Entegrasyonun amacı | (1) Kullanıcının koleksiyon görünürlük tercihini **User'da tutup** Library'nin sunucu tarafında uygulayabileceği güvenli bir **erişim kararı** olarak sunmak; (2) Recommendation'ın kullanıcı verisini kullanma izni için ayrı bir karar dayanağı sağlamak; (3) profil akışında Library verisini yetkili şekilde tüketmek (D-02'ye bağlı) |
| İlgili Library görevleri | **LIB-11** (gizlilik — ana bağımlılık), **LIB-10** (profil entegrasyonu), **LIB-14** (Recommendation izin dayanağı) |
| İlgili User görevleri | **USER-06** (gizlilik ayarları), **USER-12** (profil özetleri), USER-13 (servis sınırları), USER-09 (onboarding) |
| Öncelik | **Öneri:** P0 erişim kararı (LIB-11'in tek engeli); P1 Library'yi profilde tüketme (LIB-10); P1 öneri izni. Sıra ve takvim servis sahiplerinin kararıdır |
| Bağımlılıklar | Auth: kullanıcıya bağlı/servis token mekanizması (Auth belgesi). Social: `FRIENDS` ve engel için ilişki sorguları (Social belgesi). Karar maddeleri: **D-02…D-07, D-10** (bu belgede ayrıntılı), D-01/D-08/D-11/D-13 (Auth), D-09 (Social), D-12 (Recommendation) |

User'ın **sorumluluğu dışında kalanlar:** koleksiyon/favori/bitirildi verisini kendi DB'sine kopyalamak (Library sahibi), arkadaşlık/engel verisi (Social), kimlik doğrulama (Auth).
**Profilin tamamını gizleyen yeni bir özellik talep edilmez** (USER-05/06: yalnız alan bazlı görünürlük; mevcut sözleşme korunur).

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

Bu belgede User'ı doğrudan ilgilendirenler: **ZG-1, ZG-2, ZG-4, ZG-5, ZG-6, ZG-7, ZG-10, ZG-11**.

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

### B.1 User Service'te MEVCUT olanlar

| Konu | Durum |
| --- | --- |
| Kaynak kodu | `backend/services/user-service/src/main/java/com/ltz/user/UserServiceApplication.java` **tek Java dosyasıdır**: controller, port, istemci, entity, migration **yok** |
| `docs/api.md` USER bölümü | *"Kayıt yok."* — USER-06/USER-12 ucu kayıtlı değil |
| Gateway | `/api/v1/user/**` → `user-service` yönlendirmesi tanımlı (`/internal/**` yönlendirilmez) |
| Veri modeli (tasarım) | `docs/database.md` §3.2 ve `docs/er-diagram.md`: `privacy_settings(user_id PK/FK, library_visibility, hardware_visibility, friends_visibility, performance_visibility, activity_visibility)`, değerler **PUBLIC / FRIENDS / PRIVATE**. Tasarım var, **Flyway migration ve entity yok** |
| Migration aralığı | `docs/decisions/migrations.md`: User **V71–V80**; klasörde henüz hiç migration yok |
| Auth bağları | Hesap silme ucu (`DELETE /internal/v1/accounts/{id}`) ve `UserRegistered` olay alıcısı henüz yok (`docs-auth/entegrasyon/user-service.md`); Auth tarafında `AccountStatusChanged` olayı mevcut (varsayılan hedef listesi boş) |
| İzin emsali | Hardware'de `hardware_sync_consents(granted_at, revoked_at)` tablosu var (HW-07); User'da benzer bir izin tablosu yok |

### B.2 Library'nin bugünkü beklentisi (kodla doğrulandı)

Bu, Library'nin **şu an yazılı olan** davranışıdır; User farklı bir sözleşme seçerse Library buna **uyarlanır** (A.3, A.4):

`LibraryVisibilityPort.decide(owner, viewer, purpose)` → `ALLOW | DENY | UNAVAILABLE`, `purpose ∈ {PROFILE, RECOMMENDATION}`:

- `LibraryAccessService.require`: `purpose == PROFILE` ve `owner == viewer` ise **User'a sormadan** geçer. Aksi her durumda port çağrılır.
- **RECOMMENDATION** için Library her zaman `viewer = owner` ile sorar; bu eşitlik izin sayılmaz (kullanım izni ayrıca aranır).
- `DENY` → Library `403` (genel ProblemDetail); `UNAVAILABLE`, `null` veya port istisnası → `503`; veri, sayaç, Game çağrısı **yapılmaz**.
- **ADMIN/MODERATOR dahil hiçbir rol gizliliği atlayamaz** (ZG-5) — User kararı da rol bakmadan verilmelidir.
- Karar **cache'lenmez**; her non-owner okuma ve her Recommendation isteği yeni karar ister.
- Üretimdeki tek adaptör `UnavailableLibraryVisibility`: her zaman `UNAVAILABLE`. Bugün başka kullanıcının koleksiyonu **hiç açılmıyor**.
- Library public uçları (kullanıcı JWT'si): `GET /api/v1/library/users/{userId}/entries` ve `…/entries/statistics` (Gateway üzerinden). İç uçlar: aynı yolların `/internal/v1/library/users/{userId}/…` karşılıkları.

Library iç ucunun yanıtı (E5, `LibraryCollectionPageResponse`):

```json
{
  "items": [
    {
      "entry": { "id": "UUID", "gameId": "UUID", "inCollection": true, "favorite": true, "completed": false,
                 "addedAt": "2026-10-30T10:00:00Z", "updatedAt": "2026-10-30T10:00:00Z", "version": 1 },
      "game":  { "gameId": "UUID", "title": "string|null", "coverUrl": "string|null", "origin": "string|null",
                 "metadataCompleteness": "string|null", "gameType": "string|null",
                 "status": "AVAILABLE | UNAVAILABLE | TEMPORARILY_UNAVAILABLE",
                 "metadataSource": "LIVE | LAST_KNOWN | NONE", "metadataRefreshedAt": "ISO-8601|null" }
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "metadataPartial": false
}
```

İstatistik: `{ "totalGames": 0, "favoriteGames": 0, "completedGames": 0 }`. Her ikisi `Cache-Control: no-store`; sayfalama `page ≥ 0`, `size 1..50`.
Liste ve sayaç **ayrı isteklerdir**; aralarında ortak snapshot veya dağıtık kilit vaat edilmez.

### B.3 EKSİK olanlar (gerçek entegrasyonu engelleyen noktalar)

1. Görünürlük karar mekanizması ve onu besleyecek `privacy_settings` altyapısı yok → LIB-11 kapalı kalıyor, non-owner koleksiyon erişimi `503`.
2. `privacy_settings` tasarımında **Recommendation için ayrı kullanım izni yok** (yalnız 5 görünürlük kolonu). Faz 4 raporu, PUBLIC'in tek başına öneri izni sayılmamasını şart koşuyor (ZG-6).
3. User'ın Auth'tan token alma, Library/Social iç çağrı istemcileri yok.
4. `FRIENDS` ve engel kararı için Social tarafında servis kimliğiyle sorulabilen sorgular: engel için mevcut uç var ama `svc:user-service` çağıran izin listesinde **değil**; arkadaşlık sorgusu **hiç yok** (Social belgesi).
5. Gelen servis çağrılarını doğrulayan servis-token doğrulayıcısı yok.

### B.4 Tutarsızlıklar / dikkat

- Social `FriendsVisibilityPort` ve Hardware `HardwareVisibilityPolicy` da **User'dan benzer bir karar/tercih bekliyor** (`UnavailableFriendsVisibility`; Hardware adaptörü sözleşme yokken deny ediyor). Üç tüketici üç ayrı gizlilik sözlüğü kurarsa kural tutarsızlığı çıkar (bkz. D-10).
- `docs-auth/entegrasyon/library-service.md` gizlilik bağımlılığının User'da olduğunu söylüyor — doğru; aynı belge Auth tarafında bekleyen iş yok diyor, bu Auth belgesinde ele alınıyor.

---

## C) Beklenen Geliştirmeler

C bölümü iki türdür: **(1) Zorunlu çıktılar** — hangi yöntemle olursa olsun User'ın sağlaması gerekenler; **(2) Önerilen teknik yöntem** — Library'nin örnek çözümü, bağlayıcı değil.

### C.1 Zorunlu çıktılar (yöntemden bağımsız)

User, seçeceği mimariyle şunları sağlamalıdır:

1. Library'nin sunucu tarafında uygulayabileceği **erişim kararı** için güvenli bir yol: "bu görüntüleyen, bu kullanıcının koleksiyonunu görebilir mi?" Sonuç; izin, ret ve **geçici belirsizlik** olarak ayrılabilmelidir (ZG-2, ZG-7).
2. Karar; sahip tercihine, engel/arkadaşlık kurallarına ve hesap durumuna göre verilir; **rol karar girdisi değildir** (ZG-5); bilinmeyen/eksik durumda varsayılan izin yoktur (ZG-2).
3. Görüntüleyen kimliği Library'nin doğrulanmış bağlamından gelir; User bunu kendi başına doğrulayamıyorsa çağıran servisin kimliğine ve sözleşmeye dayanır, istemci beyanına değil (ZG-4).
4. **Öneri kullanımı için ayrı, geri alınabilir izin dayanağı** ve bunun bir karar olarak sunulması (ZG-6, ZG-11).
5. Görünürlük/izin değişikliğinin **etkili olma süresinin** tanımlanması ve bildirilmesi (ZG-11).
6. Gizli/engelli/olmayan kullanıcıların dış tarafa ayrıştırılamaması (ZG-10).
7. Library verisinin `user_db`'ye kopyalanmaması (ZG-8); Library'den veri alınıyorsa başarısızlıkların boş liste gibi gösterilmemesi (ZG-7).
8. `docs/api.md` USER, `docs/database.md` §3.2, `docs/er-diagram.md` güncellemeleri ve seçilen sözleşmenin A.4'e göre Library'ye bildirilmesi.

### C.2 Önerilen teknik yöntem (bağlayıcı değil): veri modeli

`privacy_settings` (tasarımda mevcut) için Library'nin bağımlı olduğu iki alan örneği:

| Alan | Tip | Anlam |
| --- | --- | --- |
| `library_visibility` (MEVCUT tasarım) | `varchar(16) NOT NULL`, CHECK ∈ {`PUBLIC`,`FRIENDS`,`PRIVATE`} | Başka kullanıcıların **koleksiyon/oyun geçmişini** görme kapsamı. Yalnız bu alanı etkiler; profilin tamamını gizlemez |
| `recommendation_library_consent` (**ÖRNEK**, D-04'e bağlı) | `boolean NOT NULL` | Kullanıcının, favori/bitirdiği oyun bilgisinin kendisine öneri üretmek için işlenmesine izni. Görünürlükten bağımsızdır |

```sql
-- ÖRNEK yön gösterimi (migration değil; asıl migration USER-01/06 işiyle V71+ ile yazılır)
library_visibility             varchar(16) NOT NULL DEFAULT 'PRIVATE'
                               CONSTRAINT ck_privacy_library_visibility CHECK (library_visibility IN ('PUBLIC','FRIENDS','PRIVATE')),
recommendation_library_consent boolean     NOT NULL DEFAULT false,
version                        bigint      NOT NULL DEFAULT 0
```

Bağımsız ilkeler: satır, profil oluşurken aynı işlemde oluşur; yalnız sahibi değiştirir (`{userId}` token `sub`'ından; ADMIN başkasının tercihini okumaz/değiştirmez); değişiklik anında karar yoluna yansır;
`docs/database.md` §3.2 ve `docs/er-diagram.md` aynı görevde güncellenir (AGENTS.md §2).

### C.3 Önerilen teknik yöntem (bağlayıcı değil): E2 erişim kararı ucu — örnek

> Bu uç **User tarafından seçilmiş bir sözleşme değildir**; Library'nin örneğidir. User farklı bir uç, token modeli veya yanıt biçimi seçebilir (D-10 dahil); A.4'e göre bildirim yeterlidir.

```http
POST /internal/v1/users/{ownerId}/library-access-check
Authorization: Bearer <T3>
Content-Type: application/json
```

Örnek yetkilendirme (fail-closed; Library'nin `ServiceTokenDecoderConfiguration` kuralıyla aynı çizgide): RS256/JWKS · `typ=svc+jwt` · `aud` tam `["user-service"]` ·
`sub` tam `svc:library-service` · `scope` içinde `privacy:library:check` · `jti`, `iat/nbf/exp` zorunlu, `exp − iat ≤ 60 sn`. Kullanıcı token'ı bu uçta geçmez; `/internal/**` Gateway'e açılmaz.
Referans uygulamalar (yalnız okuma): `library-service/.../ServiceTokenDecoderConfiguration.java`, `social-service/.../JwtConfig.java`.

İstek / yanıt örneği:

```json
{ "viewerId": "9a7c2d10-1b3e-4f6a-8d5c-0e1f2a3b4c5d", "purpose": "PROFILE" }
```

```json
{
  "ownerId":  "3f2b8c1e-5a4d-4e9b-9c1a-7d6e5f4a3b21",
  "viewerId": "9a7c2d10-1b3e-4f6a-8d5c-0e1f2a3b4c5d",
  "purpose":  "PROFILE",
  "decision": "ALLOW"
}
```

Örnek kurallar: `purpose ∈ {PROFILE, RECOMMENDATION}`; `RECOMMENDATION` için `viewerId = ownerId`; `decision` yalnız `ALLOW|DENY` (belirsizlik `503` ile); yanıt kimlikleri/amaç istekle eşleşir
(Library eşleşmeyen yanıtı geçici erişilemez sayar); yanıtta tercih değeri/neden/engel bilgisi yok; olmayan owner için `404` değil `200 DENY` (ZG-10); `Cache-Control: no-store`.

| HTTP | Örnek `code` | Anlam | Library eşlemesi (mevcut plan) |
| --- | --- | --- | --- |
| 400 | `VALIDATION_FAILED` / `VIEWER_MUST_BE_OWNER` | Biçim, bilinmeyen alan | geçici erişilemez |
| 401 / 403 | `UNAUTHENTICATED` / `FORBIDDEN` | Token/`sub`/scope hatası | geçici erişilemez |
| 503 | `DECISION_UNAVAILABLE` | Gereken bağımlılık (Social, DB) yanıt vermedi | geçici erişilemez |

### C.4 Önerilen teknik yöntem (bağlayıcı değil): örnek karar algoritması

**`purpose = PROFILE`** (her adım kısa devre)

| # | Koşul | Sonuç |
| --- | --- | --- |
| 1 | Owner için `privacy_settings` satırı yok (profil yok/silinmiş/bekleyen silme) | `DENY` (D-03, D-06) |
| 2 | `viewerId == ownerId` | `ALLOW` |
| 3 | `library_visibility = PRIVATE` | `DENY` (Social'a sorulmaz) |
| 4 | Aralarında engel (herhangi yönde) | `DENY` (D-05) |
| 5 | `library_visibility = PUBLIC` | `ALLOW` |
| 6 | `library_visibility = FRIENDS` ve viewer owner'ın arkadaşı | `ALLOW`, aksi `DENY` |

Adım 4 veya 6'da gereken bağımlılık (Social) yanıt vermezse **`DENY` değil, geçici erişilemez** (örnekte `503`). Her durumda: rol karar girdisi değil, header/query'den kimlik yok, her çağrı taze okur,
bilinmeyen enum/`null` → `DENY` veya geçici erişilemez, **varsayılan `ALLOW` yoktur** (ZG-2, ZG-5).

**`purpose = RECOMMENDATION`**: `viewerId ≠ ownerId` → geçersiz istek; satır yoksa `DENY`; öneri izni açıksa `ALLOW`, değilse `DENY`. Social çağrılmaz ve `library_visibility` okunmaz (ZG-6).

### C.5 User → Social çağrıları (örnek, D-05/D-09'a bağlı)

Önerilen başlangıç: engel için E3, arkadaşlık için E4 (T4 token'ıyla; Social belgesi). Token düz (kullanıcıya bağlı değil) olduğundan hedefe göre kısa süreli önbelleklenebilir
(Social'ın `AuthServiceTokenClient` deseni: 60 sn, bitimden 15 sn önce yenile). Zaman aşımı önerisi: bağlantı 1 sn, okuma 1 sn; toplam karar bütçesi ≈ 2 sn. Yönlendirme izlenmez
(servis varsayılanı `spring.http.clients.redirects=dont-follow`). **Döngü uyarısı:** Social, arkadaş listesi görünürlüğü için User'dan tercih okuyacağından, o tercih okuma ucu
Social'ı çağırmamalıdır (D-10).

### C.6 User → Library tüketimi (D-02'ye bağlı; örnek akış Seçenek A)

1. Gelen kullanıcı isteğinin Bearer token'ı (Gateway çerezden üretir) **yalnız Auth'a** iletilir; Library veya Social'a asla.
2. Auth'tan (D-01 sonucu mekanizmayla) görüntüleyen ve hedef kullanıcıya bağlı token alınır (örnekte T1/E1).
3. `GET {LIBRARY_SERVICE_URL}/internal/v1/library/users/{ownerId}/entries?page=&size=` ve/veya `…/entries/statistics`.
4. Her (viewer, owner) çifti için yeni token; **kullanıcılar/hedefler arasında paylaşılmaz veya önbelleklenmez**.

Library yanıtının profil yanıtına eşlenmesi (**zorunlu ilke ZG-7:** başarısızlık asla boş koleksiyon olarak gösterilmez; durum adları örnektir):

| Library yanıtı | Profil bölümü durumu (örnek) |
| --- | --- |
| `200` | görünür + veri |
| `403` | gizli (neden ayrıştırılmaz) |
| `503`, zaman aşımı, bağlantı hatası | geçici erişilemez (eski veri gösterilmez) |
| `401` | token reddedildi → bir kez yeni token, sürerse geçici erişilemez + alarm |
| `400`/`404` | sözleşme hatası → geçici erişilemez + alarm |

Library verisi `user_db`'ye kopyalanmaz/önbelleklenmez (ZG-8). Liste ve sayaç ayrı isteklerdir.

### C.7 Kullanıcıya dönük gizlilik ayarları (USER-06) — Library'nin şartları

Uç biçimi USER-06 sahibine aittir. Library için gereken **işlevler:** (1) `libraryVisibility` ∈ {PUBLIC, FRIENDS, PRIVATE} ve (D-04'e göre) öneri izni okunur/yazılır, (2) yalnız sahibi değiştirir (IDOR), (3) geçersiz değer reddedilir,
(4) değişiklik karar yoluna **tanımlı sürede** yansır (ZG-11), (5) yanıtlar önbelleklenmez, (6) izin geri alma sonrası bir sonraki karar ret/ilgili sonuç döner. Gateway yolu: `/api/v1/user/**`.

### C.8 Yapılandırma, ENV ve belgeler

ENV'e dokunmayın (AGENTS.md §5); öneri: User ortamında `AUTH_SERVICE_URL`, `SOCIAL_SERVICE_URL`, `LIBRARY_SERVICE_URL` (Compose ortak bloğunda mevcut, doğrulayın) ve seçilen Auth sözleşmesine göre yeni istemci anahtarı
(örnek ad `AUTH_USER_SERVICE_CLIENT_SECRET`; boşsa Social/Library çağrıları geçici erişilemez → güvenli varsayılan). Library ortamına gereken `USER_SERVICE_URL`/istemci anahtarı **Hamza** tarafından önerilir.
`docs/api.md` USER bölümüne seçilen uçlar şablonla yazılır (kod yoksa `PLANNED`; uç var ama gerçek Library/Social/Auth ile denenmediyse `NOT VERIFIED`).

### C.9 Mimari kararlar — ayrıntılı alternatifler (User'ın yetkisinde: D-02, D-03, D-04, D-05, D-06, D-07, D-10)

Aşağıdakiler Library'nin sunduğu **alternatif analizleridir.** Nihai karar ilgili servis sahibindedir (D-05 User+Social, D-07 User+Recommendation); User, ZG'yi karşılayan başka bir çözüm de seçebilir.
Uygulanabilirlik etiketleri: **U1** mevcut mimariyle uyumlu · **U2** ek geliştirme gerektirir · **U3** güvenlik açısından riskli · **U4** mevcut güvenlik gereksinimlerini (ZG) karşılamıyor.

#### D-02 — Profilde Library verilerinin getirilmesi · Sorumlu: User Service

**Çözülen sorun.** Oyuncu profili (USER-05/USER-12) koleksiyon özetini göstermek ister; verinin sahibi Library, görünürlük kararının sahibi User.
**Mevcut durum.** Library hem kullanıcı JWT'siyle çalışan public rotalara hem servis kimliğiyle çalışan iç rotalara sahip (ikisi de uygulanmış, gizlilik portu üretimde kapalı). User'da kod yok. USER-12 "diğer servislerden gelen özetleri gösterme" der; LIB-10 "User Service'in profil akışına … koleksiyon verisi sunulur" der.

| Seçenek | Avantaj | Dezavantaj | Güvenlik/gizlilik · performans/bakım | Diğer servisler · Library uyarlaması |
| --- | --- | --- | --- | --- |
| **A — User, Library'den alıp profil yanıtına ekler** (U1+U2) | Frontend tek çağrı; User kendi kararını önceden kullanabilir; USER-12/LIB-10 metnine birebir | User→Library çalışma zamanı bağımlılığı; Library kesintisinde profil bölümü bozulur (bölüm bazlı durumla çözülür); kullanıcıya bağlı token gerekir | ZG-4 için görüntüleyen bağlamı taşınmalı; veri kopyalanmaz (ZG-8); +1 hop gecikmesi, arama sonuçlarında N+1 riski (yalnız profil sayfasında kullanın) | Auth: delegasyon (D-01) şart. **Library: yok** (iç uçlar hazır) |
| **B — Frontend, Library'yi doğrudan çağırır** (U1) | Delegasyon gerekmez; hatalar izole; Library public rotası hazır; hop yok | Frontend iki servisi birleştirir (kısmi render); User profil yanıtında koleksiyon yok — LIB-10 ifadesi "User profil akışı" frontend bileşimi olarak yorumlanmalı (Hamza+Kerem uzlaşır) | Karar yine LIB-11 (User kararı) ile verilir; viewer doğrudan kullanıcı JWT'sinden gelir (ZG-4 ✓) | Auth: delegasyon gerekmez. **Library: yok** |
| **C — Karma: User yalnız "görünür mü" ipucu verir, veriyi frontend çeker** (U2) | Boş istek azalır | İki kaynakta ayrı karar riski (User ipucu ≠ Library kararı) | İpucu bilgi sızdırabilir (ZG-10); bakım iki yerde | Library kararı belirleyicidir; **Library: yok** |
| **D — Olay tabanlı kopya (Library özet olayı → User özet tablosu)** (U3/U4 bugün) | Okuma hızlı, runtime bağımlılık yok | Kalıcı kopya (ZG-8 ihlali); izin iptalinde bayat veri; broker kararı bekliyor | Gizlilik ve tazelik riski yüksek | **Library: olay yayıncısı eklenmeli** (yeni özellik) — önerilmez |

**Library önerisi (yalnız tercih):** Belirli bir tercih bulunmuyor. Nihai yöntem ilgili servis sahibi tarafından belirlenecek. (Not: A seçilirse LIB-10 kabulü açısından en doğrudan karşılıktır; B seçilirse Auth delegasyonu gereksizleşir.)

#### D-03 — Koleksiyon görünürlüğünün varsayılan değeri · Sorumlu: User Service

**Çözülen sorun.** Yeni kullanıcı ve ayarı hiç yapmamış kullanıcı için koleksiyon kimlere açık? Satır yoksa ne olur?
**Mevcut durum.** `library_visibility` tasarımda var (PUBLIC/FRIENDS/PRIVATE); varsayılan tanımsız. Emsal: Hardware `visibility` varsayılanı `PRIVATE`. USER-05: profil herkese açık; yalnız alanlar gizlenebilir.

| Seçenek | Avantaj | Dezavantaj | Güvenlik/gizlilik · bakım | Library uyarlaması |
| --- | --- | --- | --- | --- |
| **A — Varsayılan `PRIVATE`** (U1) | Varsayılan gizlilik (privacy by default); Hardware ile tutarlı; yanlışlıkla ifşa yok | Sosyal keşif azalır; kullanıcılar ayarı açmayı bilmeli | En güvenli | Yok (daha fazla `DENY` → daha fazla 403) |
| **B — Varsayılan `FRIENDS`** (U1) | Sosyal ürün hissi; arkadaşlarla paylaşım | Arkadaş listesi ve engel bağımlılığı (Social) her okumada devrede; kullanıcı fark etmeden paylaşır | Orta | Yok |
| **C — Varsayılan `PUBLIC`** (U1) | Maksimum keşfedilebilirlik | Bilinçsiz ifşa; yeni kullanıcıda geri alınamaz sızıntı | En riskli | Yok |
| **D — Onboarding'de açık seçim** (U2, USER-09) | Bilinçli onay; denetlenebilir | Seçim yapılana kadar bir ara değer gerekir; atlanırsa yine varsayılan lazım | Seçim anına kadar `PRIVATE` ile birleşirse güvenli | Yok; `UNSET` gibi üçüncü durum kullanılırsa karar `DENY`'a eşlenmeli |

**Satır bulunmadığında:** (1) `DENY` — fail-closed (ZG-2), önerilir; (2) varsayılan değeri uygulamak — yalnız satırın "henüz oluşturulmadı" değil "bu varsayılandı" olduğu garantiliyse; (3) okuma yolunda tembel satır yaratmak — okuma yolunda yazma yaptığından önerilmez.
**Library önerisi (yalnız tercih):** A (+ D ile birleşebilir); satır yoksa `DENY`. Library etkisi: yok.

#### D-04 — Recommendation veri kullanım izni · Sorumlu: User Service (tüketici: Recommendation)

**Çözülen sorun.** Kullanıcının verisini **diğer kullanıcılara göstermek** (görünürlük = hedef kitle kontrolü) ile **sistemin öneri üretmek için işlemesi** (kullanım amacı kontrolü) farklı kavramlardır. PUBLIC bir koleksiyon "öneri sistemi bunu profilleme için kullanabilir" demek değildir; PRIVATE bir koleksiyon sahibinin kendisine öneri almasını engellememelidir. ZG-6 bu ayrımı şart koşar.
**Mevcut durum.** `privacy_settings`'te yalnız 5 görünürlük kolonu. Genel bir rıza/izin altyapısı yok; emsal: `hardware_sync_consents(granted_at, revoked_at)` (HW-07), `linked_accounts.consent_at`.

| Seçenek | Avantaj | Dezavantaj | Güvenlik/gizlilik · bakım | Diğer servisler · Library |
| --- | --- | --- | --- | --- |
| **A — `privacy_settings`'e ayrı alan** (örn. boolean/enum) (U1+U2) | Basit, tek tablo; karar ucuna doğrudan | Zaman damgası/gerekçe tutmaz; yeni amaç = yeni kolon | İzin iptali anlıktır; denetim izi sınırlı | Recommendation: karar Library üzerinden. Library: yok |
| **B — Genel izin tablosu** (`consents(user_id, purpose, granted_at, revoked_at, version)`) (U2) | Amaç genişletilebilir (donanım/performans kişiselleştirmesi); denetim izi; Hardware emsaliyle uyumlu | Daha fazla geliştirme; sorgu/işlem yükü | Mevzuat (amaç sınırlaması, ispat) için güçlü | Library: yok (karar yine ALLOW/DENY) |
| **C — Mevcut rıza mekanizmasını kullanmak** | — | User'da bugün **mevcut bir rıza mekanizması yok** (yalnız bağlı hesap rızası) | Uygulanabilir değil | — |
| **D — Profil görünürlüğüne dayandırmak** (örn. PUBLIC ⇒ izinli) (U4) | Ek geliştirme yok | Amaç ayrımı yok; PUBLIC sahibi öneri profillemesine rıza vermiş sayılır; PRIVATE sahibi kendi önerisinden dışlanır | ZG-6'yı karşılamaz; geri alma anlamsız | — |
| **E — Varsayılan değer politikası** (opt-in `false` / opt-out `true`) (A/B ile birleşir) | Opt-in: gizlilik ve mevzuat uyumu; opt-out: ürün değeri | Opt-in: ilk öneriler zayıf; opt-out: açık rıza değil | Opt-in daha güvenli | Library: yok |

Granülerlik varyantı: tek izin ("koleksiyon") yerine ayrı "favoriler" ve "bitirilenler" izni; bu durumda Library `recommendation-signals`'ı izne göre süzmeli (Library uyarlaması gerekir).
**Library önerisi (yalnız tercih):** A (veya genişlemesi gerekiyorsa B); varsayılan kapalı (opt-in); D önerilmez. Library etkisi: tek izin ise yok; granüler izin seçilirse alan süzme.

#### D-05 — Engellenen kullanıcıların koleksiyon erişimi · Sorumlu: User Service ve Social Service

**Çözülen sorun.** Birbirini engelleyen kullanıcılar koleksiyonu görebilir mi?
**Mevcut durum.** Social `blocks/check` engeli **iki yönlü** değerlendirir; SOC-12'de engelli taraf arkadaş listesini göremez; Hardware politikası "izin için engel olmaması" şartıdır. Yani platform politikası bugün: **engel varsa görünürlük kapalı.**

| Seçenek | Avantaj | Dezavantaj | Güvenlik/gizlilik · performans | Diğer servisler · Library |
| --- | --- | --- | --- | --- |
| **A — Engel PUBLIC ve FRIENDS için de uygulanır** (U1) | Platform tutarlılığı; kullanıcı beklentisi (engelle = görünmez); taciz azalır | PUBLIC yolunda da Social bağımlılığı: Social kesintisi `PUBLIC`'i geçici erişilemez yapar (ZG-2) | En güçlü | Social: E3 çağıran listesi. Library: yok |
| **B — Engel yalnız FRIENDS'te değerlendirilir** (U1) | PUBLIC yolu Social'sız hızlı/dayanıklı | Engellenen kişi PUBLIC koleksiyonu görür; SOC-12/Hardware ile **tutarsız** | Zayıf | Library: yok |
| **C — Platform engelleme politikasını olduğu gibi kullanmak** (U1) | Tek doğruluk kaynağı; politika değişince otomatik uyum | Politika bugün A ile aynı; ileride değişirse davranış değişir (belgelenmeli) | Politikaya bağlı | Library: yok |
| **D — Yönlü engel** (yalnız owner viewer'ı engellediyse reddet) (U2) | Viewer'ın owner'ı engellemesi görünürlüğü etkilemez | SOC-12 ve E3 semantiğinden sapma; iki yönün anlamı tartışmalı | Orta | E3 yönsüz; Social yön bilgisi vermemeli (sızıntı) |

Dayanıklılık notu: A'da Social erişilemezken `PUBLIC` için iki seçenek vardır — geçici erişilemez (ZG-2'ye uygun) veya engeli yok sayıp izin vermek (**ZG-2 ihlali**); yalnız ilki kabul edilebilir.
**Library önerisi (yalnız tercih):** A/C (mevcut platform politikası). Nihai karar User ve Social sahiplerinde. Library etkisi: yok.

#### D-06 — Profil bulunmaması veya hesabın pasif olması · Sorumlu: User Service

**Çözülen sorun.** Owner'ın profili yok, silinmeye bekliyor veya hesap pasif (askıda/yasaklı) ise koleksiyon erişimi.
**Mevcut durum.** User yalnız kendi profil kaydını bilir; Auth hesap durumunu `AccountStatusChanged` olayıyla yayınlayabilir (varsayılan hedef yok); silme koordinasyonu `account:erase` ile yürür.

| Seçenek | Avantaj | Dezavantaj | Güvenlik/gizlilik | Library uyarlaması |
| --- | --- | --- | --- | --- |
| **A — Her durumda tek tip `DENY`** (U1) | Basit; varlık sızmaz (ZG-10) | Pasif hesabın "pasif" olduğu ayrıştırılamaz (istenen davranış) | En güvenli | Yok |
| **B — Yaşam döngüsüne göre ayrı davranış** (örn. aktif→normal, pasif→DENY, silme bekleyen→DENY, bilinmiyor→geçici erişilemez) (U2) | Bilinmeyen durumu `DENY`'dan ayırır; operasyonel görünürlük | Durum bilgisi User'da yoksa Auth olayı veya sorgu gerekir; ek bağımlılık | Dışarı sızmadığı sürece güvenli | Yok (dış davranış aynı kalmalı) |
| **C — Farklı HTTP durumları** (`404`/`403`/`410`) (U4) | Hata ayıklama kolaylığı | Kullanıcı varlığı/durumu sızar (ZG-10); Library `404`'ü bugün "geçici erişilemez" sayar | Zayıf | Adaptör eşlemesi değişir (önerilmez) |
| **D — Auth hesap durumunu doğrudan sorgulamak** (U2) | Taze durum | User↔Auth çalışma zamanı bağımlılığı; Auth'ta okuma yetkisi tanımlı değil | Orta | Yok |

**Library önerisi (yalnız tercih):** A (tek tip `DENY`; ileride B). Library etkisi: yok.

#### D-07 — Kullanıcı izninin geri alınması · Sorumlu: User Service ve Recommendation Service

**Çözülen sorun.** Kullanıcı öneri iznini veya görünürlüğü geri aldığında eski izinle veri kullanımı sürmemeli (ZG-11).
**Mevcut durum.** Library her istekte yeni karar ister, karar önbelleklemez. Recommendation'ın kendi tarafında kalıcı/önbellek verisi henüz tasarlanmadı. Olay altyapısı: Auth outbox var; platform genelinde broker kararı bekliyor.

| Seçenek | Etkili olma süresi | Avantaj | Dezavantaj | Veri gizliliği · sistem yükü | Library uyarlaması |
| --- | --- | --- | --- | --- | --- |
| **A — Her istekte güncel izin kontrolü** (U1) | Bir sonraki istek | Basit; olay altyapısı gerekmez | Recommendation'da zaten saklanmış türetilmiş veri bir sonraki istek gelene kadar durur | İyi (okuma anında); her istekte +1 karar çağrısı | Yok (bugünkü model) |
| **B — Olay tabanlı bildirim** (`izin değişti` → Recommendation temizler) (U2) | Olay gecikmesi (sn–dk) | İstek beklemeden temizlik | Broker/outbox gerekir (karar bekliyor); en az bir kez iletim; kaçan olay riski | Temizlik hızlı; ek altyapı | Yok |
| **C — A + B birlikte** (U2) | min(A, B) | Hem anlık hem proaktif temizlik; olay kaçsa da A güvence | Çift mekanizma bakımı | En iyi | Yok |
| **D — Sınırlı önbellek + geçersizleştirme** (karar veya sinyal önbelleği, TTL + olay/ret ile silme) (U2) | ≤ TTL | Yük azalır | **Library karar önbelleği şu an yok** (eklenmesi Library değişikliğidir); bayat izin riski | Gizlilik için TTL kısa tutulmalı | **Library:** karar önbelleği + geçersizleştirme eklenir |

**Zorunlu gereksinim (yöntemden bağımsız):** Kullanıcı verdiği izni geri alabilmeli; geri alınan izin tanımlı bir süre içinde etkili olmalı (ZG-11); izin doğrulanamadığında erişim verilmemeli (ZG-2) ve eski izin sınırsız süreyle kullanılmamalıdır.
**Yöntem (servis sahiplerinin mimari kararı):** her istekte kontrol, olay tabanlı bildirim, ikisinin birlikte kullanımı, sınırlı önbellek veya eşdeğer güvenli başka bir mekanizma. Seçilen yöntem ZG-1…ZG-11'i karşılamalıdır; yukarıdaki seçeneklerin hiçbiri zorunlu değildir.

**Library önerisi (yalnız tercih):** Seçenek A (her istekte güncel izin kontrolü). İsteğe bağlı olarak Seçenek B (olay tabanlı bildirim) ile desteklenebilir. Nihai uygulama yöntemi User Service ve Recommendation Service sahipleri tarafından belirlenir.
Library etkisi: A/B/C için yok; D seçilirse karar önbelleği.

#### D-10 — Ortak gizlilik karar mimarisi · Sorumlu: User Service

**Çözülen sorun.** Library (koleksiyon), Social (arkadaş listesi) ve Hardware (donanım) User'dan benzer kararlar bekliyor; çelişen kurallar (örn. bir yerde engel PUBLIC'i keser, başka yerde kesmez) kullanıcı gizliliğini bozar.
**Mevcut durum.** Üç tüketici de üretimde kapalı/`UNAVAILABLE`/deny adaptörleriyle bekliyor; User tarafında hiçbir karar kodu yok.

| Seçenek | Avantaj | Dezavantaj | Güvenlik/bakım | Tüketicilere etki |
| --- | --- | --- | --- | --- |
| **A — Tek ortak `PrivacyDecision` servisi** (tüm alanlar) (U2) | Kural tek yerde; tutarlılık | Alan farklarını (örn. öneri izni vs görünürlük) tek modele sığdırmak zor | Çelişki riski düşük | Hepsi aynı sözlükle |
| **B — Her alan için ayrı karar mekanizması** (U1) | Alan bağımsız ilerler | Kural kopyası; zamanla ayrışma | Çelişki riski yüksek | Her tüketici ayrı sözleşme |
| **C — Ortak çekirdek kurallar + alan bazlı adaptörler** (U2) | Kural tek yerde, uç şekli alana göre | Biraz daha tasarım | Dengeli | Uç şekilleri ayrı kalabilir |
| **D — Tek genel uç** (`resource`, `purpose` parametreli) (U2) | Tüketici tarafı basit | Geniş yüzey; en az ayrıcalık için kapsam bazlı ayrım gerekir | Kapsam ayrımı şart | Tüketiciler `resource` gönderir |

Çelişkiyi önlemek için ortak sözleşme noktaları (yöntemden bağımsız): aynı görünürlük sözlüğü (PUBLIC/FRIENDS/PRIVATE), aynı engel semantiği (D-05), aynı fail-closed davranış (belirsiz→ret veya geçici erişilemez), aynı kimlik/rol ilkeleri (ZG-4/5), ortak karar test vektörleri (tercih × ilişki × amaç matrisi).
Döngü yasağı: Social'ın tercih okuma ucu Social'ı çağırmamalı; Library/Hardware karar ucu Social'ı çağırabilir.
**Library önerisi (yalnız tercih):** Belirli bir tercih bulunmuyor. Nihai yöntem ilgili servis sahibi tarafından belirlenecek. Library etkisi: D seçilirse adaptör `resource=LIBRARY` gönderir; diğerlerinde yok.

---

## D) Sıralı Geliştirme Checklisti

Sıra bağımlılığa göre önerilir. Bu belge Library'nin orijinal gereksinim dokümanıdır: aşağıdaki kutular `[ ]` olarak kalır ve servis sahibi tarafından değiştirilmez.
Tamamlanma durumu, kabul kriteri **gerçekten** sağlandığında servis sahibinin kendi sonuç raporunda ve çalışma sonunda sunduğu checklistte `[x]` olarak izlenir. Kabul kriterleri **ZG-x** ile yazılmıştır; "Örnek yöntemde" ifadesi C bölümündeki öneriyi gösterir, başka yöntem seçildiyse eşdeğer kriter uygulanır ve A.4'e göre Library'ye bildirilir.

- [ ] **USER-LIB-01 — Mimari kararların verilmesi ve kayda geçirilmesi.** D-02…D-07, D-10 için User kararı; çok servisli olanlar (D-05, D-07) Social/Recommendation sahipleriyle paylaşılır.
  *Kabul:* karar ve gerekçe yazıldı; A.3'e göre etkilenen sözleşmeler listelendi.
- [ ] **USER-LIB-02 — Gizlilik veri modeli.** Seçilen D-03/D-04 yöntemine göre görünürlük ve öneri izni saklaması; migration (V71–V80), entity.
  *Kabul (ZG-2, ZG-6, ZG-11):* geçersiz değer DB'de reddedilir; kayıt yokken karar güvenli (ret); başkasının ayarı okunamaz/yazılamaz. *Örnek yöntemde:* `library_visibility` CHECK + `recommendation_library_consent`.
- [ ] **USER-LIB-03 — Gelen servis kimliği doğrulaması.** Library'nin karar isteklerini doğrulayan servis-token doğrulayıcı.
  *Kabul (ZG-3):* yanlış tür/hedef/çağıran/kapsam/süre reddedilir; kullanıcı token'ı geçmez. *Örnek yöntemde:* T3 kuralları (C.3).
- [ ] **USER-LIB-04 — Erişim kararı (Social'sız kısım).** PRIVATE/PUBLIC ve öneri amacı kararları.
  *Kabul (ZG-1, ZG-2, ZG-5, ZG-6, ZG-10):* rol karar girdisi değil; olmayan/gizli owner dışarıdan ayrıştırılamaz; varsayılan izin yok. *Örnek yöntemde:* C.4 tablosunun Social gerektirmeyen satırları.
- [ ] **USER-LIB-05 — Social entegrasyonu (engel/arkadaşlık).** D-05/D-09 sonuçlarına göre.
  *Kabul (ZG-2):* engelli viewer reddedilir (D-05 kararına göre); arkadaş olmayan `FRIENDS` için reddedilir; Social kapalıyken ilgili kararlar geçici erişilemez (izin değil); `PRIVATE` Social'dan bağımsız ret.
  *Bağımlılık:* Auth istemci kaydı, Social sorgu sözleşmesi.
- [ ] **USER-LIB-06 — Kullanıcıya dönük gizlilik ayarları (USER-06).**
  *Kabul (ZG-11):* değişiklik tanımlı sürede karar yoluna yansır; izin geri alma sonrası ret.
- [ ] **USER-LIB-07 — Library tüketimi (D-02 Seçenek A/C seçildiyse).** Kullanıcıya bağlı token ve iç uç çağrıları; durum eşlemesi (C.6).
  *Kabul (ZG-4, ZG-7, ZG-8):* hiçbir başarısızlık boş liste olarak görünmez; kullanıcı token'ı yalnız Auth'a gider; Library verisi `user_db`'de yok. D-02 Seçenek B seçildiyse bu madde "uygulanamaz" işaretlenir ve gerekçesi yazılır.
- [ ] **USER-LIB-08 — Profil özeti (USER-12).**
  *Kabul (ZG-10):* gizli/engelli/olmayan hedef dışarıdan ayrıştırılamaz.
- [ ] **USER-LIB-09 — Belgeler.** `docs/api.md` USER, `docs/database.md` §3.2, `docs/er-diagram.md`, `docs-security/user-service/NOTLAR.md`.
- [ ] **USER-LIB-10 — ENV/Compose önerisi (uygulamadan).** C.8; uygulama kullanıcı onayıyla.
- [ ] **USER-LIB-11 — Sözleşmenin Library'ye bildirilmesi.** A.4 şablonuyla Hamza'ya teslim.

---

## E) Test Senaryoları

Senaryolar **gereksinim temellidir**; ayrıntılı alan adları seçilen yönteme göre uyarlanır.

**Unit**
- Karar mantığı (saf): tercih × ilişki × amaç matrisinin her hücresi; bilinmeyen/`null` değer → ret; rol girdisi karar değiştirmez.
- DTO/istek doğrulaması: kanonik olmayan UUID, bilinmeyen alan, amaç/viewer tutarsızlığı.
- Durum eşleme (Library yanıtı → profil bölümü durumu).

**Integration (gerçek PostgreSQL, gerçek RSA/JWKS; Social/Auth/Library için sahte sunucu)**
- Veri modeli kısıtları ve varsayılanlar; migration üzerinde `validate`.
- Karar ucu: tüm yanıt sınıfları, kimlik yansıması doğruluğu, önbellek yok, log'da token/anahtar yok.
- Tercih değişince aynı çağrının sonucu tanımlı sürede değişir.
- Social sahte: engel var / arkadaş değil / 5xx / zaman aşımı / bozuk gövde / yönlendirme → geçici erişilemez veya ret beklentileri.

**Güvenlik / negatif**
- Token yok, kullanıcı token'ı, yanlış hedef/çağıran/kapsam, süresi dolmuş, ömrü fazla, `jti` yok → reddedilir.
- ADMIN/MODERATOR kimliği viewer olarak gönderilse de gizlilik atlanmaz (ZG-5).
- IDOR: başkasının tercihini okuma/yazma denemeleri.
- Kullanıcı enumerasyonu: olmayan, PRIVATE, engelli owner için gövde ve süre sınıfı aynı (ZG-10).
- Header/query ile görüntüleyen kimliği enjeksiyonu etkisiz (ZG-4).

**Gerçek servisler arası E2E (Hamza ile; Auth+Social+Library hazır olunca)**
1. Library HTTP görünürlük adaptörü ↔ User karar ucu (gerçek servis token'ı).
2. PUBLIC owner + oturumlu viewer → Library `200`. PRIVATE / engelli / olmayan owner → `403` aynı gövde; User kapalıyken `503`.
3. FRIENDS: arkadaş `200`; arkadaşlık sonlanınca sonraki istek `403`.
4. Tercih `PUBLIC → PRIVATE` → tanımlı sürede `403`.
5. (D-02 A seçildiyse) User profil isteği → kullanıcıya bağlı token → Library iç uç `200`; gizli koleksiyonda `403` → profil bölümü gizli.
6. Öneri izni açık/kapalı → Library `recommendation-signals` `200/403` (Recommendation belgesiyle).

---

## F) Tamamlanma Kriterleri

User işi **tamamlandı** sayılır, ancak ve ancak:

1. USER-LIB-01…10 kabul kriterleri sağlanmış (servis sahibinin sonuç raporundaki checklistte `[x]`; uygulanamaz olanlar gerekçeyle), testler yeşil, `docs/api.md` gerçeği yansıtır.
2. Seçilen karar mekanizması **gerçek Auth token'ıyla gerçek Library'den** (Library adaptörü hazır olduğunda) izin/ret/geçici erişilemez davranışını E2E'de kanıtlar.
3. Social ve User kesintilerinde Library'nin veri sızdırmadığı (ret/geçici erişilemez) E2E'de gösterilmiştir (ZG-2).
4. Library verisi User DB'sinde kopyalanmamıştır; kullanıcı token'ı yalnız Auth'a gönderilmiştir (ZG-8).
5. Seçilen sözleşme A.4'e göre Library'ye bildirilmiştir; gerçek E2E yapılmayan kısım `NOT VERIFIED` yazılmıştır.

Library tarafında (Hamza) doğrulanacak: kararlaştırılan sözleşmeye göre `LibraryVisibilityPort` HTTP adaptörü (kimlik yansıması doğrulaması, 5xx/zaman aşımı/yönlendirme → geçici erişilemez),
`PrivacyTests`'in gerçek User ile tekrarı. **LIB-10 ve LIB-11 ancak bu E2E'den sonra işaretlenir; 15/18 şimdilik değişmez.**

Diğer servis sahiplerinden beklenen: Auth (kullanıcıya bağlı/servis token mekanizması), Social (ilişki sorguları), Recommendation (kendi tüketici tarafı; User kararını yalnız Library üzerinden dolaylı kullanır).

---

## G) Yapay Zekâ Geliştirme Talimatları

Bu bölümü, yapay zekâ geliştirme aracınıza **olduğu gibi** verin.

```text
Sen LTZ (LobbyTwoZero) reposunda user-service için çalışıyorsun. Sahip: Kerem.
Görev: docs-library/request/user-library-Integration-request.md belgesindeki ihtiyaçları, User Service'in KENDİ mimarisine uygun biçimde karşılamak.

Çalışma sırası ve kurallar:
1. ÖNCE kendi servisinin (user-service) mevcut mimarisini incele: kaynak kod (şu an yalnız UserServiceApplication), docs/database.md §3.2, docs/er-diagram.md,
   docs/api.md USER, docs/folder-structure.md, docs/decisions/migrations.md (User: V71–V80), docs-security/user-service/README.md ve NOTLAR.md.
   AGENTS.md ve CLAUDE.md zorunlu okuma sırasını uygula. Yeni Checkstyle/PMD ihlali ekleme.
2. Kendi gereksinimlerini (USER-05/06/09/12/13) ve mevcut güvenlik sözleşmelerini değerlendir.
3. Belgedeki T1–T4 / E1–E5 tabloları ve C.2–C.6 örnekleri Library'nin ÖNERDİĞİ başlangıç sözleşmesidir; ZORUNLU DEĞİLDİR. Önerilen endpoint, DTO, alan adı, token modeli ve
   karar algoritmasını kabul etmek zorunda değilsin. Belgedeki ZG-1…ZG-11 zorunlu gereksinimlerdir; bunları karşılamak zorundasın.
4. D-02, D-03, D-04, D-05, D-06, D-07, D-10 için belgedeki alternatifleri değerlendir; istersen önerilenlerden birini, istersen ZG'yi karşılayan kendi çözümünü seç.
   Seçimi ve gerekçesini yaz. Güvenli olmayan (U3/U4) bir seçeneği seçme.
5. Çok servisli etkisi olan sözleşmeyi (karar ucu şekli, izin alanı, Social sorguları, token modeli) UYGULAMADAN ÖNCE Auth (Kerem), Social (Umut), Recommendation (Alper) sahipleri
   ve Library sorumlusu Hamza ile koordine et. Belirsizlik veya çelişki varsa UYDURMA: dur, kullanıcıya sor.
6. Yalnız backend/services/user-service kodunu, testlerini, migration'larını (yalnız V71–V80), docs/api.md USER bölümünü, docs/database.md ve docs/er-diagram.md içindeki
   user_db/privacy_settings kısımlarını ve docs-security/user-service/NOTLAR.md dosyasını değiştir. auth-service, library-service, social-service, recommendation-service,
   frontend, gateway, Maven/CI dosyalarına DOKUNMA. Library'nin gerekli uyarlamasını KENDİN YAPMA; seçtiğin sözleşmeyi A.4 şablonuyla Library sorumlusuna bildir.
   docs-library/ altındaki hiçbir Library belgesini (bu belge dahil) DEĞİŞTİRME.
7. Mevcut mimariyi koru (api/application/domain/infrastructure). Ham JPA entity paylaşma; başka servisin DB'sine erişme; Library/Social verisini user_db'ye kopyalama.
8. .env, .env.dev, *.bak, frontend/.env.local dosyalarını OKUMA. ENV/Compose değişikliği için kullanıcı onayı al; önce .env.example önerisi sun.
9. Güvenlik: hiçbir yolda varsayılan izin yok; rol karar girdisi değil; kullanıcı token'ı yalnız Auth'a gider; servis-token doğrulayıcı sıkı olmalı; hata yanıtları ProblemDetail,
   girdi yansıtılmaz; token/anahtar loglanmaz. Seçtiğin yaklaşımın ZG-1…ZG-11'i karşıladığını testlerle doğrula (belgenin E bölümü; gerçek PostgreSQL ve gerçek RSA/JWKS).
10. Pre-push ve SonarQube ÇALIŞTIRMA; bunları kullanıcı elle yapacak. Yalnız hedefli Maven testlerini çalıştır ve sonucu dürüstçe raporla.
11. git commit / git push YAPMA. İş bitince AGENTS.md §7 formatında yalnız commit mesajı öner (atıf satırı ekleme).
12. Bu belgenin (docs-library/request/) D bölümündeki checklisti DEĞİŞTİRME; belge Library'nin orijinal gereksinim ve sözleşme dokümanıdır ve `[ ]` olarak kalır.
    Tamamlanan görevleri kendi servisinin sonuç raporunda ve çalışma sonunda sunduğun checklistte [x] olarak işaretle. Sahte sunucuyla yapılan test "gerçek entegrasyon doğrulandı" anlamına gelmez; gerçek E2E yapılmadıysa
    entegrasyonu tamamlanmış gibi raporlama, docs/api.md'de NOT VERIFIED yaz. Çalıştırılamayan veya başarısız adımı gizleme.
```

---

## H) Alternatifler ve Library Service Önerileri

Bu bölüm yalnız User'ın sorumluluğundaki kararların özetidir (ayrıntı: C.9). Buradaki "Önerilen" alanları **yalnızca Library'nin tercihidir**; zorunlu değildir ve
User sahibinin bunu seçmesi beklenmez. Güvenli ve işlevsel gereksinimleri (ZG) karşılayan her alternatif kabul edilebilir.

### D-02 — Profilde Library Verilerinin Getirilmesi

**Seçenekler:**
- Seçenek A: User Service, Library'den veriyi alıp profil yanıtına ekler.
- Seçenek B: Frontend Library'yi doğrudan çağırır.
- Seçenek C: Karma — User yalnız ipucu verir, veriyi frontend çeker.
- Seçenek D: Olay tabanlı kopya (U3/U4 — önerilmez).
- Seçenek E: User'ın mimarisine uygun başka bir birleştirme yaklaşımı.

**Önerilen:** Belirli bir tercih bulunmuyor. Nihai yöntem ilgili servis sahibi tarafından belirlenecek.

**Öneri gerekçesi:** A, LIB-10/USER-12 metnine en doğrudan karşılıktır; B delegasyon ihtiyacını ortadan kaldırır. Seçim User'ın akış ve bağımlılık tercihine bağlıdır.

**Nihai karar yetkisi:** İlgili servis sahibi (User — Kerem).

**Library Service'e etkisi:** A ve B için Library'de değişiklik yok (iç ve public uçlar hazır). D seçilirse Library'ye olay yayıncısı eklenir. B seçilirse LIB-10 kabulünün yorumu Hamza ile netleştirilir.

### D-03 — Koleksiyon Görünürlüğünün Varsayılan Değeri

**Seçenekler:**
- Seçenek A: `PRIVATE` varsayılanı.
- Seçenek B: `FRIENDS` varsayılanı.
- Seçenek C: `PUBLIC` varsayılanı (en riskli).
- Seçenek D: Kullanıcının onboarding'de açık tercih yapması.
- Seçenek E: Kayıt bulunmadığında: ret / varsayılan uygulama / tembel kayıt (ret önerilir).

**Önerilen:** Seçenek A (D ile birleşebilir); kayıt yoksa ret. Bu yalnızca Library'nin tercihidir.

**Öneri gerekçesi:** Varsayılan gizlilik; Hardware varsayılanıyla tutarlı; yanlışlıkla ifşa riski yok.

**Nihai karar yetkisi:** İlgili servis sahibi (User — Kerem).

**Library Service'e etkisi:** Yok; Library yalnız izin/ret/geçici erişilemez görür. Üçüncü bir `UNSET` durumu eklenirse karar tarafında ret'e eşlenmelidir.

### D-04 — Recommendation Veri Kullanım İzni

**Seçenekler:**
- Seçenek A: `privacy_settings`'e ayrı izin alanı.
- Seçenek B: Genel izin/rıza tablosu (amaç, verilme/geri alma zamanı).
- Seçenek C: Mevcut rıza mekanizması (bugün yok — uygulanamaz).
- Seçenek D: Profil görünürlüğüne dayandırma (ZG-6'yı karşılamaz, önerilmez).
- Seçenek E: Opt-in / opt-out varsayılan politikası (A/B ile birleşir).

**Önerilen:** Seçenek A (genişlemesi gerekiyorsa B), varsayılan kapalı. Bu yalnızca Library'nin tercihidir.

**Öneri gerekçesi:** Görünürlük (hedef kitle) ile işleme amacı ayrı kavramlardır; basit ve hızlı bir ayrım sağlar.

**Nihai karar yetkisi:** İlgili servis sahibi (User — Kerem); tüketici Recommendation (Alper) ile uzlaşılır.

**Library Service'e etkisi:** Tek izin ise yok. Granüler izin (örn. yalnız favoriler) seçilirse Library `recommendation-signals` yanıtını izne göre süzer.

### D-05 — Engellenen Kullanıcıların Koleksiyon Erişimi

**Seçenekler:**
- Seçenek A: Engel PUBLIC ve FRIENDS görünürlüklerinde uygulanır.
- Seçenek B: Engel yalnız FRIENDS'te değerlendirilir.
- Seçenek C: Mevcut platform engelleme politikasının aynen kullanılması.
- Seçenek D: Yönlü engel (yalnız owner'ın engellediği viewer).

**Önerilen:** Seçenek A/C (Library'nin tercihi; yalnızca öneridir).

**Öneri gerekçesi:** Social SOC-12 ve Hardware politikasıyla tutarlıdır; engellenen kişinin PUBLIC koleksiyonu görmesi beklenmedik gizlilik ihlalidir.

**Nihai karar yetkisi:** İlgili servis sahipleri (User — Kerem ve Social — Umut).

**Library Service'e etkisi:** Yok; Library Social'ı çağırmaz ve yalnız User kararını görür.

### D-06 — Profil Bulunmaması veya Hesabın Pasif Olması

**Seçenekler:**
- Seçenek A: Her durumda tek tip ret.
- Seçenek B: Yaşam döngüsüne göre ayrı iç davranış (dış davranış aynı).
- Seçenek C: Farklı HTTP durumları (varlık sızıntısı — önerilmez).
- Seçenek D: Auth hesap durumunun doğrudan sorgulanması.

**Önerilen:** Seçenek A (ileride B). Bu yalnızca Library'nin tercihidir.

**Öneri gerekçesi:** Kullanıcı varlığı ve durumu dışarıya sızmaz; ek bağımlılık gerektirmez.

**Nihai karar yetkisi:** İlgili servis sahibi (User — Kerem).

**Library Service'e etkisi:** Yok. Farklı HTTP durumları seçilirse Library adaptörünün eşlemesi (özellikle `404`) güncellenir.

### D-07 — Kullanıcı İzninin Geri Alınması

**Seçenekler:**
- Seçenek A: Her istekte güncel izin kontrolü.
- Seçenek B: Olay tabanlı bildirim (broker/outbox gerektirir).
- Seçenek C: A + B birlikte.
- Seçenek D: Sınırlı önbellek + geçersizleştirme.

**Önerilen:** Seçenek A (her istekte güncel izin kontrolü). İsteğe bağlı olarak Seçenek B (olay tabanlı bildirim) ile desteklenebilir. Nihai uygulama yöntemi User Service ve Recommendation Service sahipleri tarafından belirlenir. Bu yalnızca Library'nin tercihidir.

**Öneri gerekçesi:** Altyapı gerektirmeden izin iptalini bir sonraki istekte etkili kılar; olay mekanizması hazır olunca proaktif temizlik eklenebilir. Zorunlu olan, iptalin tanımlı sürede etkili olması ve doğrulanamayan iznin erişim açmamasıdır (ZG-2, ZG-11); bunun hangi yöntemle sağlanacağı servis sahiplerinin kararıdır.

**Nihai karar yetkisi:** İlgili servis sahipleri (User — Kerem ve Recommendation — Alper).

**Library Service'e etkisi:** A/B/C için yok. D seçilirse Library'ye karar önbelleği ve geçersizleştirme eklenir (bugün yok).

### D-10 — Ortak Gizlilik Karar Mimarisi

**Seçenekler:**
- Seçenek A: User içinde tek ortak gizlilik karar servisi.
- Seçenek B: Her alan için ayrı karar mekanizması.
- Seçenek C: Ortak çekirdek kurallar + alan bazlı adaptörler.
- Seçenek D: Parametreli tek genel uç.
- Seçenek E: User mimarisine uygun başka bir yapı.

**Önerilen:** Belirli bir tercih bulunmuyor. Nihai yöntem ilgili servis sahibi tarafından belirlenecek.

**Öneri gerekçesi:** Bu karar User'ın iç mimarisidir; Library için önemli olan, Library/Social/Hardware kararlarının aynı sözlük ve engel/fail-closed kurallarını paylaşmasıdır.

**Nihai karar yetkisi:** İlgili servis sahibi (User — Kerem).

**Library Service'e etkisi:** D seçilirse Library adaptörü kaynak parametresini (`LIBRARY`) gönderir; diğer seçeneklerde yok.
