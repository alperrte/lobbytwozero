# Recommendation Service — Library Service Entegrasyon Talebi

Belge kimliği: `recommendation-library-Integration-request` · Tarih: 30 Ekim 2026 (revizyon 2: karar yetkisi ve alternatifler)
Durum: **TALEP / ÖNERİ — henüz uygulanmadı.** Bu belge yalnız analiz ve gereksinim içerir; hiçbir servis kodu değiştirilmemiştir.

> **Karar yetkisi ilkesi.** Bu belgede belirtilen teknik yöntemler ve mimari seçenekler Library Service tarafından sunulan alternatiflerdir. Nihai teknik çözümü ilgili servisin sahibi kendi mevcut mimarisine, gereksinimlerine ve güvenlik standartlarına göre belirler. Library Service, kararlaştırılan güvenli entegrasyon sözleşmesine kendi tarafında uyarlanacaktır.

Okuma notu: Bu belge tek başına okunabilir. Aynı entegrasyonun diğer üç belgesi (`auth-…`, `user-…`, `social-library-Integration-request.md`)
aynı klasördedir ve aynı **zorunlu gereksinimleri** ve aynı **başlangıç öneri tablolarını** taşır. Recommendation kendi veri tüketim ve öneri üretim mimarisini belirler;
Library'nin önerdiği istemci yapısı, sonuç tipi, sayfalama algoritması veya sınırlar **zorunlu değildir**. Seçiminiz birden fazla servisi etkiliyorsa uygulamadan önce ilgili
sahiplerle (Auth: Kerem, User: Kerem, Social: Umut) paylaşın ve sonucu Library sorumlusuna (Hamza) iletin.

Etiketler: **MEVCUT** = repo kodunda doğrulandı · **PROPOSED / ÖNERİ** = Library'nin sunduğu, bağlayıcı olmayan örnek; ilgili sahip onaylamadı ·
**ZORUNLU GEREKSİNİM (ZG)** = yöntemden bağımsız güvenlik/işlevsel şart · **EKİP KARARI (D-xx)** = sahipler arasında karara bağlanacak madde.

---

## A) Genel Bilgiler

| Alan | Değer |
| --- | --- |
| Talep eden servis | `library-service` (Hamza) |
| Hedef servis / sorumlu | `recommendation-service` — **Alper** (öneri üretim ve veri tüketim mimarisinin sahibi) |
| Entegrasyonun amacı | Recommendation'ın, kullanıcının **izin verdiği** favori ve bitirdiği oyun bilgisini Library'den güvenli, sözleşmeli ve veri-azaltılmış biçimde alması (Library veri sahibi kalır) |
| İlgili Library görevi | **LIB-14** (Recommendation entegrasyonu); doğrulama için LIB-18 |
| İlgili Recommendation görevleri | **REC-02** (favorilere dayalı), **REC-05** (tamamlanan oyunlar), REC-01, REC-17 (öneri nedenleri), REC-18 (geçmiş), REC-20 |
| Öncelik | **Öneri:** P1 — Auth'un token mekanizması ve User'ın öneri izni hazır olunca; istemci/sonuç tipi/test işleri sahte sunucuyla hemen başlayabilir. Sıra ve takvim servis sahiplerinin kararıdır |
| Bağımlılıklar | **Auth:** Recommendation'ın Library'ye erişim token mekanizması (Auth belgesi). **User:** öneri kullanım izni (User belgesi). Karar maddeleri: **D-07, D-12** (bu belgede), D-04 ve D-13 (tüketici olarak etkilenir; sorumlular User ve Auth) |

Recommendation'ın **sorumluluğu dışında kalanlar:** Library DB'sine veya tablolarına erişmek, kullanıcının koleksiyonunu "profil gibi" okumak, Game metadata'sını Library'den almak
(Game kendi servisinden), izin kararı vermek (User'ın; Library uygular).

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

Bu belgede Recommendation'ı doğrudan ilgilendirenler: **ZG-2, ZG-3, ZG-4, ZG-6, ZG-7, ZG-8, ZG-9, ZG-11**.

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

Recommendation'ın doğrudan ilgilendiği: **T2, E1, E5 (`recommendation-signals`)**. User'ın izin kararına (E2) **Library** başvurur; Recommendation E2'yi çağırmaz.

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

### B.1 Recommendation Service'te MEVCUT olanlar

| Konu | Durum |
| --- | --- |
| Kaynak kodu | `backend/services/recommendation-service/src/main/java/com/ltz/recommendation/RecommendationServiceApplication.java` **tek Java dosyasıdır**; controller, port, istemci, entity, migration **yok** |
| `docs/api.md` RECOMMENDATION | *"Kayıt yok."* |
| Gateway | `/api/v1/recommendation/**` → `recommendation-service` tanımlı |
| Migration aralığı | Recommendation **V11–V20** (`docs/decisions/migrations.md`) |
| Servis ayarları | `spring.http.clients.redirects=dont-follow`, bağlantı 3 sn / okuma 5 sn varsayılanı mevcut |
| Gereksinim | `LTZ_Servis_Icerikleri_Alper.md`: `library-service` → "Favoriler ve tamamlanan oyunlar"; "başka servislerin veritabanlarına doğrudan erişilmez"; "kullanıcı gizliliği … dikkate alınır" |

### B.2 Library'nin bugünkü sunumu (E5 `recommendation-signals`) — kodla doğrulandı

Bu, Library'nin **şu an yazılı olan** davranışıdır; Recommendation/Auth/User farklı bir sözleşme seçerse Library buna **uyarlanır** (A.3, A.4):

```http
GET {LIBRARY_SERVICE_URL}/internal/v1/library/users/{userId}/recommendation-signals?page=0&size=20
Authorization: Bearer <servis token'ı>
```

| Denetim | Bugünkü kural |
| --- | --- |
| Token | RS256/JWKS, `typ=svc+jwt`, `iss=ltz-auth`, **`aud` tam `["library-service"]`**, `kid`, `jti`, `iat/nbf/exp`, `exp − iat ≤ 60 sn` |
| Kimlik | `sub` **tam** `svc:recommendation-service` |
| Kapsam | `scope` içinde `library:recommendation:read` |
| Hedef | `target_user_id` claim'i, yoldaki `{userId}`'nin **küçük harfli kanonik** UUID'siyle birebir aynı |
| İzin | Library, ardından User'dan `RECOMMENDATION` amacıyla (viewer = owner) **ayrı karar** ister. Owner eşitliği izin sayılmaz; PUBLIC profil tercihi izin sayılmaz |
| Parametre | `page ≥ 0` (varsayılan 0), `size 1..50` (varsayılan 20); sınır dışı → `400` |
| Sıra / filtre | `favorite OR completed` olan satırlar, `addedAt DESC, id DESC`; bir oyun iki bayrağı taşısa da **bir kez** döner |
| Tutarlılık | Tek sayfa isteğinde liste ve `totalElements` aynı `REPEATABLE_READ` işleminde; **sayfalar arasında** tutarlılık vaat edilmez |

Yanıt `200` (`Cache-Control: no-store`) — veri azaltılmış DTO:

```json
{
  "items": [
    { "gameId": "5d8f7a52-0c1b-4b7e-9f3a-2e6d4c8b1a90", "favorite": true,  "completed": false },
    { "gameId": "a1b2c3d4-0000-4000-8000-0123456789ab", "favorite": false, "completed": true }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2
}
```

Yanıtta **yoktur:** yerel kayıt `id`'si, `userId`, tarihler, `version`, oyun adı/görseli/metadata, `inCollection`. Oyun bilgisi gerekirse Game Service'in kendi (public) toplu API'sinden alınır.

Hata yanıtları `ProblemDetail` (`status`, `title`, `detail`; **`code` alanı yoktur** — Library'nin mevcut gövde biçimi budur, sözleşmeye `code` bağlamayın):

| HTTP | Anlam |
| --- | --- |
| 400 | Geçersiz UUID/`page`/`size` |
| 401 | Token yok/geçersiz (imza, `typ`, `aud`, `iss`, zaman, `jti`) |
| 403 | Kapsam/`sub`/`target_user_id` hatası **veya** kullanıcı öneri kullanımına izin vermiyor (ayrıştırılamaz) |
| 503 | İzin kararı alınamıyor (User kapalı/yanıtsız) |

### B.3 EKSİK olanlar (gerçek entegrasyonu engelleyen noktalar)

1. Recommendation'ın Library'ye erişmesini sağlayacak, kullanıcıya/hedefe bağlı bir token mekanizması yok (Auth belgesi). Mevcut `service-tokens` ucu bugünkü Library kabul sınırının istediği hedef-kullanıcı bağını üretemez; bugün alınabilecek hiçbir token Library'de geçmez.
2. User'da öneri kullanım izni kararı yok (User belgesi). Bugün Library üretimde tüm RECOMMENDATION isteklerine `503` verir.
3. Recommendation'da istemci, port, DTO, hata sınıflaması, test yok.
4. `docs-auth/entegrasyon/recommendation-service.md` "Auth'ta Recommendation için istemci kaydı yok, gerek de görünmüyor" diyor — Faz 4 ihtiyacıyla çelişir; Auth sahibi bir yöntem seçtiğinde güncellenmelidir.

---

## C) Beklenen Geliştirmeler

C bölümü iki türdür: **(1) Zorunlu çıktılar** — hangi yöntemle olursa olsun Recommendation'ın sağlaması gerekenler; **(2) Önerilen teknik yöntem** — Library'nin örnek çözümü, bağlayıcı değil.

### C.1 Zorunlu çıktılar (yöntemden bağımsız)

1. Library verisi yalnız **sözleşmeli iç API** üzerinden, **hedef kullanıcıya bağlı servis yetkisiyle** alınır; Library DB'sine veya kişisel/public uçlarına bağımlılık yoktur (ZG-3, ZG-4, ZG-8).
2. `Available` (veri geldi — boş olabilir), `Denied` (izin yok) ve `Unavailable` (geçici/sözleşme/kimlik sorunu) sonuçları **ayrı** ele alınır; `Denied` ve `Unavailable` hiçbir yolda boş koleksiyon gibi davranmaz (ZG-7).
3. Hata veya izin reddinde Library kaynaklı kişiselleştirme **yapılmaz**; kullanıcı yanıltılmaz (ZG-2, ZG-7).
4. Ham Library sinyalleri izinsiz kalıcı saklanmaz; izin geri alındığında kullanımı sınırlı ve tanımlı sürede durur (ZG-6, ZG-11) — D-07.
5. Kullanıcıya dönük akışlarda hedef kullanıcı yalnız doğrulanmış kullanıcı bağlamından (token `sub`) gelir (ZG-4).
6. Token, gizli anahtar ve oyun kimlik listeleri loglanmaz (ZG-9).
7. Eksik/kısmi veri durumunda ne yapılacağı **önceden tanımlı** bir sonuç modeliyle belirlenir (D-12).
8. `docs/api.md` RECOMMENDATION güncellemesi ve seçilen sözleşmenin A.4'e göre Library'ye bildirilmesi.

### C.2 Önerilen teknik yöntem (bağlayıcı değil): token edinme (T2)

> Auth bu mekanizmayı farklı seçebilir (D-01/D-13); aşağıdaki E1 yalnız örnektir.

```http
POST {AUTH_SERVICE_URL}/internal/v1/service-tokens/delegated
Content-Type: application/json
```

```json
{
  "clientId": "recommendation-service",
  "clientSecret": "<AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET>",
  "audience": "library-service",
  "scope": "library:recommendation:read",
  "targetUserId": "3f2b8c1e-5a4d-4e9b-9c1a-7d6e5f4a3b21"
}
```

Yanıt (örnek): `{ "accessToken": "…", "tokenType": "Bearer", "expiresIn": 30, "scope": "library:recommendation:read" }`.

Yöntemden bağımsız istemci kuralları: kullanıcının token'ı Library'ye iletilmez (D-13); bellek içi önbellek anahtarı **(audience, scope/amaç, hedef kullanıcı)** olmalı ve kullanıcılar arasında paylaşılmamalıdır
(Social'ın `AuthServiceTokenClient` önbelleği yalnız `audience` anahtarlıdır; bu desen kullanıcıya bağlı token'lar için kopyalanmamalıdır); token alınamazsa sonuç `Unavailable`'dır.

### C.3 Önerilen teknik yöntem (bağlayıcı değil): Library istemcisi ve sonuç modeli

Mevcut katman düzeni (`docs/folder-structure.md`; Library'deki `GameCatalogPort` ↔ `infrastructure/client/GameServiceClient` deseni örnektir):

```java
// ÖRNEK — application/port/LibrarySignalsPort.java
public interface LibrarySignalsPort {
    LibrarySignalsResult fetch(UUID userId);
}

// ÖRNEK sonuç modeli (D-12 sonrası tutarlı biçim; ayrıntı C.7)
public sealed interface LibrarySignalsResult {
    record Available(List<LibrarySignal> signals, Coverage coverage) implements LibrarySignalsResult { }
    record Denied() implements LibrarySignalsResult { }                  // izin yok / yetki reddi (403)
    record Unavailable(Reason reason) implements LibrarySignalsResult { } // geçici, sözleşme, kimlik, EKSİK VERİ
    sealed interface Coverage { record Complete() implements Coverage { } record Bounded(int limit) implements Coverage { } }
    enum Reason { AUTH, TIMEOUT, SERVER_ERROR, CONTRACT, INCOMPLETE }
}
public record LibrarySignal(UUID gameId, boolean favorite, boolean completed) { }
```

İstemci: RestClient, yönlendirme izlenmez; bağlantı ≈ 1 sn, okuma ≈ 2 sn önerilir (servis varsayılanı 3/5 sn üst sınır olarak uygundur); yol yalnız `/internal/v1/library/users/{userId}/recommendation-signals`;
`userId` küçük harf kanonik UUID; **public `/api/v1/library/**` çağrılmaz.**

### C.4 Hata sınıflaması (zorunlu ilke ZG-7; durum adları örnektir)

| Library yanıtı | Sonuç sınıfı | Davranış |
| --- | --- | --- |
| `200` | Available | Sinyalleri kullan (kapsam: C.7) |
| `200`, `items=[]`, `totalElements=0` | Available (boş) | Kullanıcının işaretli oyunu yok; kişiselleştirme library sinyali olmadan sürer. **Yalnız HTTP 200 ile** |
| `403` | **Denied** | Boş koleksiyon **değildir**. Library kaynaklı kişiselleştirme yapılmaz; kullanıcıya genel durum; D-07 temizliği; yeniden deneme yok (izin açılana kadar) |
| `401` | Unavailable (kimlik) | Bir kez yeni token alıp dene; sürerse Unavailable + alarm |
| `503`, `5xx`, zaman aşımı, bağlantı hatası, `3xx` | Unavailable (geçici) | Sınırlı geri çekilmeli yeniden deneme, sonra kişiselleştirmesiz akış |
| `400`, `404`, bozuk JSON/şema | Unavailable (sözleşme) | Yeniden deneme yok; alarm |
| Token alınamadı | Unavailable (kimlik) | Aynı |

Zorunlu yasaklar: `403`'ü veya hataları **asla** boş sonuca çevirmeyin; `Denied` ile `Unavailable`'ı aynı kovaya koymayın; `403`'te yeniden deneme yapmayın; hata gövdesini kullanıcıya iletmeyin.

### C.5 İzin geri alma ve veri tutma (D-07'nin Recommendation tarafı)

- Ham sinyaller (`gameId/favorite/completed` listeleri) kalıcı saklanmaz; istek yaşam döngüsü bellek içidir. Önbellek tasarlanırsa TTL ve geçersizleştirme (`Denied` görülünce silme, varsa olay) tanımlanır ve ZG-11 uyarınca etkili olma süresi yazılır.
- `Denied` görüldüğünde o kullanıcı için varsa önbellek/türetilmiş özellik silinir; yeni öneriler library sinyali olmadan üretilir.
- Kalıcı öneri geçmişi (REC-18) ve neden metinleri (REC-17) library kaynaklı sinyale dayanıyorsa, izin geri alındıktan sonra bu nedenler gösterilmez (genel neden metni) ve yeniden üretimde izin yeniden doğrulanır. Hangi alanların kalıcı olduğu Recommendation'ın tasarımıdır.
- Günlükler: oyun kimlik listeleri INFO'da yazılmaz; yalnız sayı, süre, sonuç sınıfı.

### C.6 Kimlik ve yetkilendirme (Recommendation'ın gelen tarafı)

Kullanıcıya dönük öneri uçları kullanıcı JWT'sini (`typ=at+jwt`, `aud=ltz-api`, JWKS) kendisi doğrular; hedef kullanıcı **yalnız token `sub`'ından** gelir (ZG-4, IDOR). Arka plan/toplu üretimde hedef yalnız Recommendation'ın tanıdığı kullanıcılar için kullanılır;
Library her kullanıcı için izni ayrıca sorar, izin yoksa `Denied` döner ve atlanır. Servis token'ı kullanıcı yetkisi taşımaz.

### C.7 Mimari kararlar — ayrıntılı alternatifler (Recommendation'ın yetkisinde: D-07, D-12)

Aşağıdakiler Library'nin sunduğu **alternatif analizleridir.** Nihai karar Recommendation sahibindedir (D-07: User ile birlikte); ZG'yi karşılayan başka bir çözüm de seçilebilir.
Uygulanabilirlik etiketleri: **U1** mevcut mimariyle uyumlu · **U2** ek geliştirme gerektirir · **U3** güvenlik açısından riskli · **U4** ZG'yi karşılamıyor.

#### D-12 — Kısmi Library verisinin kullanılması · Sorumlu: Recommendation Service

**Çözülen sorun.** Library sinyalleri sayfa sayfa gelir (en çok 50/sayfa); tamamının alınması mümkün olmayabilir (hata, sayfalar arası kayma, kasıtlı üst sınır). Eksik veriyle yapılan kişiselleştirme sessizce yanıltıcı olabilir.

**Mevcut durum.** Library sıralaması `addedAt DESC, id DESC` (deterministik); sayfalar arası tutarlılık vaadi yok; `totalElements` yalnız ipucu. Library sınırı `size ≤ 50`.

**Önceki taslaktaki çelişki ve çözümü.** Önceki revizyon C.3'te, `maxPages` dolunca sonucun `Available (truncated)` sayılabileceğini; aynı belgede ise "kısmi veri yok" kuralını yazıyordu. İki ifade
**iki farklı durumu** karıştırıyordu: (a) **kasıtlı, tanımlı sınırlandırma** — "en yeni N sinyal" (Library sırası deterministik olduğundan N'inci sinyale kadar olan küme tanımlıdır) ve
(b) **hata kaynaklı eksiklik** — bir sayfa başarısız oldu, yarım küme elde kaldı. Taslak (a)'yı da `Available` saydığı için (b) kuralıyla çelişir görünüyordu ve `Available`, kapsamı gizliyordu.
Çözüm: sonuç tipinde **kapsam (`Complete` | `Bounded(limit)`) açıkça taşınır**; (b) hiçbir seçenekte `Available` olmaz.

Ortak değişmez kurallar (her seçenekte): `Denied` ve izin doğrulama hatası başarı ya da boş koleksiyon sayılmaz (ZG-7); hata kaynaklı eksiklik `Available` olamaz.

| Seçenek | Açıklama · uygulanabilirlik | Avantaj | Dezavantaj | Güvenlik/gizlilik · bakım | Tutarlı sonuç modeli | Library uyarlaması |
| --- | --- | --- | --- | --- | --- | --- |
| **A — Eksik veride öneriyi durdur** (U1) | Tüm sayfalar alınamazsa kişiselleştirme yok (üst sınıra takılan büyük koleksiyonlar dahil, sınır pratikte yeterince yüksek seçilir ya da sınır yok) | En basit ve en doğru; yanıltıcı sonuç yok | Çok büyük koleksiyonlar zaman/yük sorunu; geçici hatada kişiselleştirme düşer | Daha az veri tutulur | `Available(Complete)` · `Denied` · `Unavailable(INCOMPLETE/…)` | Yok |
| **B — Açıkça tanımlı, sınırlı küme** (U2) | "En yeni N sinyal" sözleşmesi; algoritma bunu bilerek tasarlanır (örn. yakınlık ağırlıklı) | Yük öngörülebilir; büyük koleksiyonlarda da çalışır | Eski favoriler görülmez; algoritma sınırlı kümeyle doğrulanmalı | Veri azaltma (ZG-8) açısından olumlu | `Available(Complete)` **veya** `Available(Bounded(N))` · `Denied` · `Unavailable`. **Hata kaynaklı eksiklik = `Unavailable(INCOMPLETE)`** | Yok |
| **C — Sayfalama/işlem sınırlarını yeniden düzenle** (U2) | Akış halinde (sayfa sayfa) toplulaştırma, bellekte tam liste tutmama; gerekirse Library'den sayfa boyutu/`updatedSince`/imleç gibi yetenekler istenmesi | Bellek/süre iyileşir; artımlı güncelleme mümkün | Sayfalar arası tutarlılık yine garanti değil; Library yeni özellik gerektirebilir | Daha az ham veri tutulur | A veya B ile aynı model | **Library:** imleç/`updatedSince`/daha büyük sayfa gibi yetenekler eklenebilir (yeni özellik; talep halinde) |
| **D — Library sinyallerine hiç dayanmayan genel öneriye düş** (U1) | Herhangi bir `Denied`/`Unavailable` → kişiselleştirmesiz öneri | Kullanıcı her zaman bir sonuç alır | Kalite düşer; kullanıcı bunun nedenini anlamayabilir | `Denied` yanıtında temizlik (D-07) şart | A/B'nin üstüne ek **kişiselleştirme modu**: `LIBRARY_BASED(Complete/Bounded)` · `GENERAL(DENIED)` · `GENERAL(UNAVAILABLE)` — hiçbiri "kullanıcının koleksiyonu boş" olarak sunulmaz | Yok |

Seçeneklerin birbirini dışlamadığına dikkat: tipik bir uygulama A veya B'yi D ile birleştirir (eksik/izinsiz durumda genel öneri). Erişim reddi veya izin doğrulama hatası hiçbir seçenekte başarı ya da boş koleksiyon değildir.
**Library önerisi (yalnız tercih):** Belirli bir tercih bulunmuyor. Nihai yöntem ilgili servis sahibi tarafından belirlenecek. (Not: B seçilirse sonuç tipinde kapsamın taşınması ve hata kaynaklı eksikliğin `Unavailable` sayılması çelişkiyi ortadan kaldırır.)

#### D-07 — Kullanıcı izninin geri alınması · Sorumlu: User Service ve Recommendation Service

Ayrıntılı seçenek analizi (A: her istekte kontrol, B: olay, C: ikisi, D: sınırlı önbellek) **User belgesinde C.9'dadır**; özet burada:

| Seçenek | Etkili olma süresi | Recommendation'a etkisi |
| --- | --- | --- |
| A — Her istekte güncel kontrol | Bir sonraki istek | Her üretimde Library'ye gidilir; `Denied` ile temizlik |
| B — Olay tabanlı bildirim | Olay gecikmesi | Olay alıcısı (inbox) gerekir; broker kararı bekliyor |
| C — A + B | min(A, B) | İki mekanizma bakımı |
| D — Sınırlı önbellek | ≤ TTL | Recommendation tarafı önbelleği + geçersizleştirme kuralı yazılır |

Recommendation'ın kendi sorumluluğu: hangi türetilmiş verinin tutulduğu, `Denied` sonrası temizlik, önbellek varsa TTL ve etkili olma süresinin ZG-11'e göre yazılması.
**Zorunlu gereksinim (yöntemden bağımsız):** Kullanıcı verdiği izni geri alabilmeli; geri alınan izin tanımlı bir süre içinde etkili olmalı (ZG-11); izin doğrulanamadığında erişim verilmemeli (ZG-2) ve eski izin sınırsız süreyle kullanılmamalıdır.
**Yöntem (servis sahiplerinin mimari kararı):** her istekte kontrol, olay tabanlı bildirim, ikisinin birlikte kullanımı, sınırlı önbellek veya eşdeğer güvenli başka bir mekanizma. Seçilen yöntem ZG-1…ZG-11'i karşılamalıdır; yukarıdaki seçeneklerin hiçbiri zorunlu değildir.

**Library önerisi (yalnız tercih):** Seçenek A (her istekte güncel izin kontrolü). İsteğe bağlı olarak Seçenek B (olay tabanlı bildirim) ile desteklenebilir. Nihai uygulama yöntemi User Service ve Recommendation Service sahipleri tarafından belirlenir.

#### Tüketici olarak etkilenilen kararlar (sorumlu başka servis; ayrıntı ilgili belgede)

- **D-04 (izin dayanağı; User):** Öneri izni görünürlükten ayrıdır (ZG-6). Recommendation izni kendisi tutmaz; Library `403` ile bildirir. Granüler izin seçilirse hangi sinyal türlerinin alınabileceği değişir.
- **D-13 (kullanıcı JWT'sinin aktarımı; Auth):** Kullanıcı JWT'sinin doğrudan Library'ye aktarılması güvenli kabul edilmez; ayrıntı Auth belgesi C.7'de. Recommendation seçilen mekanizmanın istemcisini yazar.

### C.8 Yapılandırma, ENV ve belgeler

- **ENV (kullanıcı onayı gerektirir — AGENTS.md §5):** Bu görevde ENV'e dokunmayın. Öneri: `recommendation-service` ortamında `AUTH_SERVICE_URL`, `LIBRARY_SERVICE_URL` (Compose ortak bloğunda mevcut, doğrulayın) ve seçilen Auth sözleşmesine göre yeni istemci anahtarı
  (örnek ad `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET`; boşsa istemci kapalı → Library sinyalleri `Unavailable`, akış kişiselleştirmesiz sürer). Servise özgü ayarlar (`base-url`, sayfa/sinyal üst sınırı, zaman aşımları) tek bir yapılandırma önekinde toplanır (örn. `ltz.recommendation.library.*`).
- **`docs/api.md`:** RECOMMENDATION bölümüne bu işle oluşan/değişen her uç ve iç tüketimin bağlantı notu şablonla yazılır (`Durum`, `Tüketici servisler`, `Yetkilendirme`, `Response`, `Hata Durumları`, `Bağlantı`, `Test Durumu`);
  gerçek Library ile denenmediyse `NOT VERIFIED`. Yalnız kendi bölümünüzü düzenleyin.
- **Hesap silme (kapsam dışı hatırlatma):** Recommendation kişisel veri tutmaya başladığında D4 silme ucu zorunludur (`docs-auth/entegrasyon/recommendation-service.md`); Library sinyalinden türetilmiş kalıcı veri varsa silmeye dahildir.

---

## D) Sıralı Geliştirme Checklisti

Bu belge Library'nin orijinal gereksinim dokümanıdır: aşağıdaki kutular `[ ]` olarak kalır ve servis sahibi tarafından değiştirilmez.
Tamamlanma durumu, kabul kriteri **gerçekten** sağlandığında servis sahibinin kendi sonuç raporunda ve çalışma sonunda sunduğu checklistte `[x]` olarak izlenir.
Kabul kriterleri **ZG-x** ile yazılmıştır; "Örnek yöntemde" ifadesi C bölümündeki öneriyi gösterir, başka yöntem seçildiyse eşdeğer kriter uygulanır ve A.4'e göre Library'ye bildirilir.

- [ ] **REC-LIB-01 — Mimari kararların verilmesi ve kayda geçirilmesi.** D-07, D-12 için Recommendation kararı; D-04/D-13 sonuçlarının izlenmesi.
  *Kabul:* karar ve gerekçe yazıldı; A.3'e göre etkilenen sözleşmeler listelendi.
- [ ] **REC-LIB-02 — Sonuç modeli ve istemci portu.** `Available / Denied / Unavailable` ayrımı, kapsam (D-12 sonucu).
  *Kabul (ZG-7):* üç sınıf ayrı tiplerle ayrılır; eksik veri hiçbir yolda `Available` olmaz. *Örnek yöntemde:* C.3 tipleri.
- [ ] **REC-LIB-03 — Token edinme istemcisi.** Auth'un seçtiği mekanizmayla kullanıcıya bağlı token.
  *Kabul (ZG-3, ZG-4, ZG-9):* iki kullanıcı için aynı token kullanılmaz; secret/token logda ve `toString()`'te yok; anahtar yoksa `Unavailable`. *Bağımlılık:* Auth kararı; öncesinde sahte Auth sunucusu.
- [ ] **REC-LIB-04 — Library HTTP adaptörü.** Yönlendirme yok, zaman aşımları, yalnız iç yol.
  *Kabul (ZG-2, ZG-7):* C.4 tablosunun her satırı testle doğrulanır; `403` hiçbir koşulda `Available(boş)` olmaz.
- [ ] **REC-LIB-05 — Sayfalama/toplama ve eksik veri politikası (D-12).**
  *Kabul:* seçilen politika testle doğrulanır; hata kaynaklı eksiklik `Unavailable`; yinelenen kayıtlar güvenle birleşir; sınır davranışı belgeli.
- [ ] **REC-LIB-06 — Öneri üretimi entegrasyonu.** `Available/Denied/Unavailable` ayrı sonuçlar üretir (REC-02/REC-05 girdisi).
  *Kabul (ZG-7):* hiçbiri "boş koleksiyon" gibi davranmaz; kişiselleştirmesiz akış çalışır.
- [ ] **REC-LIB-07 — İzin geri alma ve veri tutma (D-07).**
  *Kabul (ZG-6, ZG-11):* ham sinyaller kalıcı değil; `Denied` sonrası temizlik; etkili olma süresi yazılı; log'da oyun listesi yok.
- [ ] **REC-LIB-08 — Kimlik/IDOR.**
  *Kabul (ZG-4):* başka `userId` parametresiyle öneri çekilemez (negatif test).
- [ ] **REC-LIB-09 — Dayanıklılık.** Zaman aşımı, sınırlı yeniden deneme, devre kesici.
  *Kabul:* `400/403/404` yeniden denenmez; `401` en çok bir kez.
- [ ] **REC-LIB-10 — Belgeler.** `docs/api.md` RECOMMENDATION, `docs-security/recommendation-service/NOTLAR.md`; migration gerekirse yalnız V11–V20.
- [ ] **REC-LIB-11 — ENV/Compose önerisi (uygulamadan).** C.8; uygulama kullanıcı onayıyla.
- [ ] **REC-LIB-12 — Sözleşmenin Library'ye bildirilmesi.** A.4 şablonuyla Hamza'ya teslim.

---

## E) Test Senaryoları

Senaryolar **gereksinim temellidir**; ayrıntılı alan adları seçilen yönteme göre uyarlanır.

**Unit**
- DTO eşleme (yalnız gereken alanlar; fazlası saklanmaz), kanonik UUID yolu.
- Sayfalama/toplama: durma koşulları, sınır davranışı, birleştirme, hata kaynaklı eksikliğin `Unavailable` olması, boş koleksiyon.
- Hata sınıflaması tablosu (C.4) — her satır ayrı test.
- Token önbelleği: kullanıcı başına izolasyon, süre dolumu.

**Integration (sahte HTTP sunucu / MockRestServiceServer; Auth ve Library için)**
- Auth sahte: başarı/401/403/5xx; Library sahte: çok sayfalı 200, 400, 401→yeni token→200, 401→401, 403, 503, 5xx, zaman aşımı, 3xx, bozuk JSON.
- Yeniden deneme: yalnız geçici hatalar sınırlı; `403/400` için deneme sayısı 1.

**Güvenlik / negatif**
- `403`'ün boş koleksiyona dönüşmediği (en kritik test, ZG-7).
- A ve B kullanıcıları için eşzamanlı çağrıda token karışmaması.
- Kullanıcı isteğiyle başka `userId` seçilemez (ZG-4).
- Library DB'sine/public uçlarına bağımlılık yok; Library'den Game metadata'sı istenmiyor (ZG-8).
- Secret/token/oyun listesi loglarda yok (ZG-9).

**Gerçek servisler arası E2E (Auth + User + Library hazır olunca; Hamza ile)**
1. Öneri izni açık → Library `200`, sinyaller geldi.
2. İzin kapalı → `403` → `Denied`; öneri kişiselleştirmesiz; ham sinyal saklanmadı.
3. İzin açıkken kapatma → tanımlı sürede `Denied`; önbellek/türetilmiş veri temizlendi.
4. User kapalı → Library `503` → `Unavailable`; yeniden denemeler sınırlı; izin varsayılmadı.
5. Süresi dolmuş/yanlış hedefli token → Library `401/403`.

---

## F) Tamamlanma Kriterleri

Recommendation işi **tamamlandı** sayılır, ancak ve ancak:

1. REC-LIB-01…11 kabul kriterleri sağlanmış (servis sahibinin sonuç raporundaki checklistte `[x]`), testler yeşil; `docs/api.md` gerçeği yansıtır.
2. Gerçek Auth'tan alınan **gerçek token** ile gerçek Library `recommendation-signals` ucu, izin açıkken `200`, kapalıyken `403`, User kapalıyken `503` döndürüp Recommendation'ın bunları sırasıyla
   `Available / Denied / Unavailable` ayırdığı E2E'de gösterilmiştir.
3. `403` hiçbir yolda boş koleksiyon olarak yorumlanmamıştır; izin geri alma sonrası ham sinyal ve türetilmiş veri tanımlı sürede temizlenmektedir.
4. Library DB'sine veya public Library uçlarına bağımlılık yoktur.
5. Seçilen sözleşme A.4'e göre Library'ye bildirilmiştir; gerçek E2E yapılmayan kısım `NOT VERIFIED` yazılmıştır.

Library tarafında (Hamza) doğrulanacak: RECOMMENDATION kararının User'dan gerçek alınması, kararlaştırılan token sözleşmesine uyarlama, `LIB-14` kabulü. **LIB-14 ancak bu E2E'den sonra işaretlenir; 15/18 şimdilik değişmez.**

Diğer servis sahiplerinden beklenen: Auth (token mekanizması), User (öneri izni + karar), Library (hazır; uyarlama Hamza'da).

---

## G) Yapay Zekâ Geliştirme Talimatları

Bu bölümü, yapay zekâ geliştirme aracınıza **olduğu gibi** verin.

```text
Sen LTZ (LobbyTwoZero) reposunda recommendation-service için çalışıyorsun. Sahip: Alper.
Görev: docs-library/request/recommendation-library-Integration-request.md belgesindeki ihtiyaçları, Recommendation'ın KENDİ mimarisine uygun biçimde karşılamak.

Çalışma sırası ve kurallar:
1. ÖNCE kendi servisinin (recommendation-service) mevcut mimarisini incele: kaynak kod (şu an yalnız RecommendationServiceApplication), docs/api.md RECOMMENDATION,
   docs/folder-structure.md, docs/decisions/migrations.md (Recommendation: V11–V20), docs-security/recommendation-service/README.md ve NOTLAR.md,
   docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Alper.md. AGENTS.md ve CLAUDE.md zorunlu okuma sırasını uygula. Yeni Checkstyle/PMD ihlali ekleme.
2. Kendi gereksinimlerini (REC-01/02/05/17/18/20) ve mevcut güvenlik sözleşmelerini değerlendir.
3. Belgedeki T1–T4 / E1–E5 tabloları ve C.2–C.4 örnekleri Library'nin ÖNERDİĞİ başlangıç sözleşmesidir; ZORUNLU DEĞİLDİR. Önerilen istemci yapısı, sonuç tipi, sayfalama algoritması,
   token alma biçimi ve sınırları kabul etmek zorunda değilsin. Belgedeki ZG-1…ZG-11 zorunlu gereksinimlerdir; bunları karşılamak zorundasın.
4. D-07 ve D-12 için belgedeki alternatifleri değerlendir; istersen önerilenlerden birini, istersen ZG'yi karşılayan kendi çözümünü seç. Seçimi ve gerekçesini yaz.
   Güvenli olmayan (U3/U4) bir seçeneği seçme. D-04 (User) ve D-13 (Auth) kararlarının sonucunu izle ve istemcini ona göre yaz.
5. Çok servisli etkisi olan sözleşmeyi (token edinme biçimi, izin davranışı, olay sözleşmesi) UYGULAMADAN ÖNCE Auth (Kerem), User (Kerem) sahipleri ve Library sorumlusu Hamza ile
   koordine et. Belirsizlik veya çelişki varsa UYDURMA: dur, kullanıcıya sor.
6. Yalnız backend/services/recommendation-service kodunu, testlerini, migration'larını (yalnız V11–V20), docs/api.md RECOMMENDATION bölümünü ve
   docs-security/recommendation-service/NOTLAR.md dosyasını değiştir. auth-service, user-service, library-service, social-service, frontend, gateway, Maven/CI dosyalarına DOKUNMA.
   Library'nin gerekli uyarlamasını KENDİN YAPMA; seçtiğin sözleşmeyi A.4 şablonuyla Library sorumlusuna bildir.
   docs-library/ altındaki hiçbir Library belgesini (bu belge dahil) DEĞİŞTİRME.
7. Mevcut mimariyi koru (api/application/domain/infrastructure; port application/port'ta, HTTP adaptörü infrastructure/client'ta). Library DB'sine veya Library'nin public
   /api/v1/library/** uçlarına bağlanma; başka servisin DB'sine erişme.
8. .env, .env.dev, *.bak, frontend/.env.local dosyalarını OKUMA. ENV/Compose değişikliği için kullanıcı onayı al; önce .env.example önerisi sun.
9. Doğruluk ve güvenlik: 403'ü ASLA boş koleksiyona çevirme; hata kaynaklı eksik Library verisiyle "tam" sonuç üretme; token'ı kullanıcılar arasında paylaşma; hedef kullanıcıyı yalnız
   doğrulanmış kullanıcı bağlamından al; secret/token/oyun listesi loglama; ham library sinyallerini izinsiz kalıcı saklama. Seçtiğin yaklaşımın ZG-1…ZG-11'i karşıladığını testlerle doğrula
   (belgenin E bölümü).
10. Pre-push ve SonarQube ÇALIŞTIRMA; bunları kullanıcı elle yapacak. Yalnız hedefli Maven testlerini çalıştır ve sonucu dürüstçe raporla.
11. git commit / git push YAPMA. İş bitince AGENTS.md §7 formatında yalnız commit mesajı öner (atıf satırı ekleme).
12. Bu belgenin (docs-library/request/) D bölümündeki checklisti DEĞİŞTİRME; belge Library'nin orijinal gereksinim ve sözleşme dokümanıdır ve `[ ]` olarak kalır.
    Tamamlanan görevleri kendi servisinin sonuç raporunda ve çalışma sonunda sunduğun checklistte [x] olarak işaretle. Sahte sunucuyla yapılan test "gerçek entegrasyon doğrulandı" anlamına gelmez; gerçek E2E yapılmadıysa entegrasyonu
    tamamlanmış gibi raporlama, docs/api.md'de NOT VERIFIED yaz. Çalıştırılamayan veya başarısız adımı gizleme.
```

---

## H) Alternatifler ve Library Service Önerileri

Bu bölüm yalnız Recommendation'ın sorumluluğundaki kararların özetidir (ayrıntı: C.7). Buradaki "Önerilen" alanları **yalnızca Library'nin tercihidir**; zorunlu değildir ve
Recommendation sahibinin bunu seçmesi beklenmez. Güvenli ve işlevsel gereksinimleri (ZG) karşılayan her alternatif kabul edilebilir.

### D-07 — Kullanıcı İzninin Geri Alınması

**Seçenekler:**
- Seçenek A: Her istekte güncel izin kontrolü.
- Seçenek B: Olay tabanlı bildirim.
- Seçenek C: Güncel kontrol ve olay tabanlı bildirimin birlikte kullanılması.
- Seçenek D: Sınırlı önbellek ve geçersizleştirme stratejisi.

**Önerilen:** Seçenek A (her istekte güncel izin kontrolü). İsteğe bağlı olarak Seçenek B (olay tabanlı bildirim) ile desteklenebilir. Nihai uygulama yöntemi User Service ve Recommendation Service sahipleri tarafından belirlenir. Bu yalnızca Library'nin tercihidir.

**Öneri gerekçesi:** Altyapı gerektirmeden iptali bir sonraki istekte etkili kılar; olay mekanizması (broker kararı bekliyor) hazır olunca proaktif temizlik eklenebilir. Zorunlu olan, iptalin tanımlı sürede etkili olması ve doğrulanamayan iznin erişim açmamasıdır (ZG-2, ZG-11); bunun hangi yöntemle sağlanacağı servis sahiplerinin kararıdır.

**Nihai karar yetkisi:** İlgili servis sahipleri (Recommendation — Alper ve User — Kerem).

**Library Service'e etkisi:** A/B/C için yok. D seçilirse (Library tarafında karar önbelleği) Library'ye önbellek ve geçersizleştirme eklenir; Recommendation tarafı önbelleği Library'yi etkilemez.

### D-12 — Kısmi Library Verisinin Kullanılması

**Seçenekler:**
- Seçenek A: Eksik veride öneri üretiminin durdurulması.
- Seçenek B: Açıkça tanımlanmış ve sınırlandırılmış veri kümesiyle öneri (kapsam sonuç tipinde taşınır; hata kaynaklı eksiklik başarı sayılmaz).
- Seçenek C: Sayfalama/işlem sınırlarının yeniden düzenlenmesi (gerekirse Library'den yeni yetenek talebi).
- Seçenek D: Library sinyalleri kullanılmadan genel önerilere dönülmesi (A/B ile birleşebilir).

**Önerilen:** Belirli bir tercih bulunmuyor. Nihai yöntem ilgili servis sahibi tarafından belirlenecek.

**Öneri gerekçesi:** Seçim, Recommendation algoritmasının sınırlı kümeyle çalışıp çalışamamasına ve yük/kalite dengesine bağlıdır. Hangi seçenek seçilirse seçilsin erişim reddi veya izin hatası başarı/boş koleksiyon sayılmamalıdır.

**Nihai karar yetkisi:** İlgili servis sahibi (Recommendation — Alper).

**Library Service'e etkisi:** A, B, D için yok. C seçilirse Library'ye imleç, `updatedSince` veya daha büyük sayfa gibi yetenekler eklenmesi talep edilebilir (yeni özellik; Library sorumlusuyla planlanır).

*Tüketici olarak etkilenilen kararlar (sorumlu başka servis):* D-04 (User) ve D-13 (Auth); ayrıntı ilgili belgelerdedir.
