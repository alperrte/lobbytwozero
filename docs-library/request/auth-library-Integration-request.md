# Auth Service — Library Service Entegrasyon Talebi

Belge kimliği: `auth-library-Integration-request` · Tarih: 30 Ekim 2026 (revizyon 2: karar yetkisi ve alternatifler)
Durum: **TALEP / ÖNERİ — henüz uygulanmadı.** Bu belge yalnız analiz ve gereksinim içerir; hiçbir servis kodu değiştirilmemiştir.

> **Karar yetkisi ilkesi.** Bu belgede belirtilen teknik yöntemler ve mimari seçenekler Library Service tarafından sunulan alternatiflerdir. Nihai teknik çözümü ilgili servisin sahibi kendi mevcut mimarisine, gereksinimlerine ve güvenlik standartlarına göre belirler. Library Service, kararlaştırılan güvenli entegrasyon sözleşmesine kendi tarafında uyarlanacaktır.

Okuma notu: Bu belge tek başına okunabilir. Aynı entegrasyonun diğer üç belgesi (`user-…`, `recommendation-…`, `social-library-Integration-request.md`)
aynı klasördedir ve aynı **zorunlu gereksinimleri** ve aynı **başlangıç öneri tablolarını** taşır. Auth kendi mimarisini belirler; Library'nin önerdiği
endpoint, token modeli, claim/scope adı veya DTO **zorunlu değildir**. Seçiminiz birden fazla servisi etkiliyorsa uygulamadan önce ilgili sahiplerle
(User: Kerem, Recommendation: Alper, Social: Umut) paylaşın ve sonucu Library sorumlusuna (Hamza) iletin.

Etiketler: **MEVCUT** = repo kodunda doğrulandı · **PROPOSED / ÖNERİ** = Library'nin sunduğu, bağlayıcı olmayan örnek; ilgili sahip onaylamadı ·
**ZORUNLU GEREKSİNİM (ZG)** = yöntemden bağımsız güvenlik/işlevsel şart · **EKİP KARARI (D-xx)** = sahipler arasında karara bağlanacak madde.

---

## A) Genel Bilgiler

| Alan | Değer |
| --- | --- |
| Talep eden servis | `library-service` (Hamza) |
| Hedef servis / sorumlu | `auth-service` — **Kerem** (kendi JWT/servis token mimarisinin sahibi) |
| Entegrasyonun amacı | Library'nin servisler arası okuma ve gizlilik akışları için, **kimin hangi kullanıcı bağlamında neye eriştiğini** güvenilir biçimde taşıyacak, kısa ömürlü servis kimliği/token'larının sağlanması |
| İlgili Library görevleri | **LIB-10** (profil entegrasyonu), **LIB-11** (gizlilik), **LIB-14** (Recommendation); doğrulama için LIB-18 |
| Öncelik | **P0 (öneri)** — diğer servislerin gerçek token'la ilerlemesi buna bağlı. Sıra ve takvimi servis sahipleri birlikte belirler |
| Bağımlılıklar | Auth işi User/Recommendation işlerinden bağımsız başlayabilir; onlar Auth'un seçtiği sözleşmeyi bekler. Karar maddeleri: **D-01, D-08, D-11, D-13** (bu belgede ayrıntılı), D-02…D-07, D-09, D-10, D-12 (ilgili belgelerde) |

Bu işte Auth'un **sorumluluğu dışında kalanlar:** kullanıcı-profil/gizlilik kararı (User'ın), koleksiyon verisi (Library'nin), arkadaşlık/engel (Social'ın).
Auth yalnız "kim, hangi servis olarak, hangi kullanıcı bağlamıyla, ne amaçla" bilgisini **imzalar** (veya eşdeğer bir mekanizmayla taşır).

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

Bu belgede Auth'u doğrudan ilgilendirenler: **ZG-3, ZG-4, ZG-5, ZG-9, ZG-11**.

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

Aşağıdakilerin tamamı kaynak kodunda doğrulandı (yalnız okuma). Gerçek ENV dosyaları okunmadı.

### B.1 Repository'de MEVCUT olanlar

| Parça | Kanıt | Not |
| --- | --- | --- |
| Servis token ucu | `ServiceTokenController` · `POST /internal/v1/service-tokens` | Gövde `ServiceTokenRequest(clientId, clientSecret, audience)`; yanıt `ServiceTokenResponse(accessToken, tokenType, expiresIn, scope)`; `Cache-Control: no-store` |
| İstemci kimlik doğrulama | `ServiceClientTokenService` | Sabit zamanlı özet karşılaştırması; bilinmeyen/devre dışı/yanlış anahtar tek `INVALID_CLIENT`; hedef izin listesinde yoksa `SERVICE_AUDIENCE_FORBIDDEN` |
| İstemci kaydı | `ServiceClientProperties` + `application.properties` (`ltz.auth.service-clients.clients.<ad>.*`) | `secret` boşsa istemci kapalı (fail-closed). Kayıtlı istemciler: `admin-service`, `social-service`, `community-service`, `messaging-service`, `lfp-service` |
| Açılış denetimi | `ServiceClientConfig` | Gizli anahtar ≥ 32 karakter; hedef anahtarı `^[a-z0-9-]{1,24}-service$`; hedef kapsamları **yalnız `ALLOWED_TARGET_SCOPES`** (`events:ingest`, `blocks:check`) |
| Token üretimi | `ServiceClientTokenIssuer` | `typ=svc+jwt`, `alg=RS256`, `kid`; `iss`, `sub=svc:<istemci>`, `aud=[tek hedef]`, `iat/nbf/exp` (TTL **60 sn**), `jti`, `scope` (boşlukla ayrık). **Başka claim taşımaz** |
| D4 hesap silme token'ı | `ServiceTokenIssuer` (TTL 30 sn, `scope=account:erase`, `account_id`, `sub=svc:auth-service`, kapsam deseni `^[a-z]+:[a-z]+$`) | Hedefe **ve hesaba bağlı** token için emsal; Library silme ucu bunu kullanır — **bozulmamalı** (ZG-9) |
| Kullanıcı token doğrulayıcı | `JwtConfig.jwtDecoder` + `SessionStateValidator` | `typ=at+jwt`, `aud=ltz-api`; oturum aktif mi, hesap `ACTIVE` mi, `ver` (token sürümü) güncel mi — veritabanından denetler. Auth içinde kullanıcı token'ını doğrulamak için hazır mekanizma |
| JWKS | `JwksController` · `GET /api/v1/auth/.well-known/jwks.json` | Geçerli + `additional-public-keys` (anahtar rotasyonu) |
| Access token ömrü | `ltz.auth.jwt.access-token-ttl=${JWT_ACCESS_TTL_SECONDS:60}s` | Varsayılan 60 sn (`docs/SECURITY.md` örnek değeri 300 sn der; ENV ile değişebilir) |
| Olay altyapısı | Auth outbox: `AccountStatusChanged`, `UserRegistered`, `AccountEmailChanged` | Hedef listesi `ltz.auth.events.targets` (varsayılan boş) |

### B.2 Library'nin bugünkü kabul sınırı (kodla doğrulandı)

`ServiceTokenDecoderConfiguration` + `LibraryInternalReadController` + `AccountErasureSecurityConfiguration`. Bu, **Library'nin şu an yazılı olan** kabul koşuludur;
Auth farklı bir sözleşme seçerse Library bu sınırı kararlaştırılan sözleşmeye göre **uyarlar** (A.3, A.4):

| Denetim | Bugünkü kural |
| --- | --- |
| İmza | RS256, Auth JWKS (`${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json`) |
| Başlık | `typ` **tam** `svc+jwt`; `kid` dolu |
| `iss` | `ltz-auth` |
| `aud` | **yalnız** `["library-service"]` |
| `sub` | `svc:` ile başlar; uç bazında tam eşleşme (profil uçları `svc:user-service`, sinyal ucu `svc:recommendation-service`) |
| Zaman | `iat`, `nbf`, `exp` zorunlu; `exp − iat ≤ 60 sn`; `iat ≤ şimdi + 30 sn`; `exp ≤ şimdi + 60 sn + 30 sn` |
| `jti` | dolu zorunlu |
| `scope` | `scope` claim'i (boşlukla ayrık) → `SCOPE_*`; profil uçları `library:profile:read`, sinyal ucu `library:recommendation:read`. `scp` ve `roles` okunmaz |
| `target_user_id` | string; yoldaki `{userId}`'nin **küçük harfli kanonik** UUID'siyle birebir eşit |
| `viewer_id` | yalnız profil uçlarında; **küçük harfli kanonik** UUID string |
| Ret | `401` (token geçersiz) veya `403` (scope/sub/target/viewer); veri ve gizlilik portu çağrılmaz |

Dikkat — büyük/küçük harf: Library `UUID.toString()` ile karşılaştırır. Bu, Library'nin bugünkü gerçekleşmesidir; Auth'un seçtiği biçime uyarlanabilir.

### B.3 EKSİK olanlar (gerçek entegrasyonu engelleyen noktalar)

| # | Eksik | Etki |
| --- | --- | --- |
| 1 | `ServiceTokenRequest` yalnız `clientId/clientSecret/audience` taşır; hedef kullanıcı ve görüntüleyen kullanıcı bağlamını taşıyacak bir mekanizma yok | Library'nin bugünkü zorunlu claim'leri hiçbir yolla üretilemez → LIB-10 ve LIB-14 bloklu |
| 2 | `ALLOWED_TARGET_SCOPES` yalnız `events:ingest`, `blocks:check` | Library/User/Social gizlilik okuma amaçları için kapsam tanımlanamaz |
| 3 | `user-service`, `recommendation-service`, `library-service` istemci kaydı yok | Hiçbiri token alamaz |
| 4 | Düz uç, hedefin **tüm** kapsamlarını tek token'a koyar; çağıran hedef kullanıcıyı belirtemez | Kullanıcıya bağlı yetki düz uçtan üretilemez |
| 5 | `docker-compose.yml` Auth ortamında 5 istemci anahtarı var; user/library/recommendation ortamlarında `AUTH_*_CLIENT_SECRET` yok | ENV/Compose değişikliği gerekir (**kullanıcı onayı şart**, §C.8) |

### B.4 Belgeler arası tutarsızlıklar (düzeltilmeli)

- `docs-auth/entegrasyon/library-service.md`: *"AUTH'TA BEKLEYEN — Yok"*; `docs-auth/entegrasyon/recommendation-service.md`: *"Auth'ta Recommendation için istemci kaydı yok, gerek de görünmüyor"*. Faz 4 bağımlılık raporu ve bu belge farklı bir ihtiyaç gösteriyor; Auth sahibi bir yöntem seçtiğinde bu iki belge güncellenmelidir.
- `docs-auth/servis-sozlesmeleri.md` §3.5 tablosu 6 (çağıran, hedef, kapsam) çifti listeler; seçilen çözüme göre yeni çiftler eklenecektir.
- `docs/api.md` AUTH bölümünde `service-tokens` kaydı vardır; seçilen yeni/değişen uç buraya yazılmalıdır (AGENTS.md §4.1).

---

## C) Beklenen Geliştirmeler

C bölümü iki türdür: **(1) Zorunlu çıktılar** — hangi yöntemle olursa olsun Auth'un sağlaması gereken yetenekler; **(2) Önerilen teknik yöntem** — Library'nin
örnek çözümü, bağlayıcı değil. Her alt başlıkta hangisi olduğu belirtilmiştir.

### C.1 Zorunlu çıktılar (yöntemden bağımsız)

Auth, seçeceği mekanizmayla şunları sağlamalıdır:

1. **Servis kimliği + hedef + amaç sınırı:** üç yeni çağıran kimliği (Library→User gizlilik kararı, User→Library profil okuma, Recommendation→Library öneri sinyali) ve User→Social ilişki sorguları için yetkilendirilmiş, kısa ömürlü, tek hedefe bağlı kimlik (ZG-3).
2. **Kullanıcı bağlamının güvenilir taşınması:** User'ın profil okumasında görüntüleyen kullanıcının kimliği, Auth'un doğruladığı kullanıcı bağlamından gelir (ZG-4); çağıran servis keyfi bir `viewer` belirleyemez.
3. **Hedef kullanıcı sınırı:** Recommendation (ve User) okumalarında yetki tek bir hedef kullanıcıya daraltılabilir olmalıdır (çalınan/yanlış yönlenen kimlik başka kullanıcıya genişletilemez).
4. **En az ayrıcalık:** `user-service` Recommendation amacıyla, `recommendation-service` profil amacıyla veri okuyamaz.
5. **Kullanıcı ↔ servis ayrımı ve D4 korunumu** (ZG-3, ZG-9); kullanıcı token'ı ikinci bir servise "oynatılabilir" hale getirilmez.
6. **Kısa ömür, anahtar rotasyonu, tekrar oynatma yüzeyinin sınırlanması** (D-08).
7. **Başarısızlıkta açık hata** (hangi istemci/hedef/amaç reddedildi, token üretilemedi) ve Library'nin tüketebileceği doğrulama (JWKS veya eşdeğer).
8. **Belgeleme:** `docs/api.md` AUTH kaydı ve `docs-auth` belgelerinin güncellenmesi; seçilen sözleşmenin A.4'e göre Library'ye bildirilmesi.

### C.2 Önerilen teknik yöntem (bağlayıcı değil): yeni istemci kayıtları ve kapsam kümeleri

Mevcut `ServiceClientConfig` üzerine örnek: düz hedef kapsamları (`ALLOWED_TARGET_SCOPES`) yeni değerlerle (`privacy:library:check`, `friendships:check`) genişler;
kullanıcıya/hedefe bağlı kapsamlar (`library:profile:read`, `library:recommendation:read`) için ayrı bir küme (`DELEGATED_TARGET_SCOPES`) **düz uçtan verilmez**.

```properties
# ÖRNEK — bağlayıcı değil; yapılandırma biçimi Auth sahibine aittir
# T3 — düz
ltz.auth.service-clients.clients.library-service.secret=${AUTH_LIBRARY_SERVICE_CLIENT_SECRET:}
ltz.auth.service-clients.clients.library-service.targets.user-service=privacy:library:check
# T4 — düz
ltz.auth.service-clients.clients.user-service.secret=${AUTH_USER_SERVICE_CLIENT_SECRET:}
ltz.auth.service-clients.clients.user-service.targets.social-service=blocks:check,friendships:check
# T1 / T2 — delegasyon (yeni yapılandırma kavramı; biçim Auth sahibine ait)
ltz.auth.service-clients.clients.user-service.delegations[0].audience=library-service
ltz.auth.service-clients.clients.user-service.delegations[0].scope=library:profile:read
ltz.auth.service-clients.clients.user-service.delegations[0].mode=VIEWER_BOUND
ltz.auth.service-clients.clients.recommendation-service.secret=${AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET:}
ltz.auth.service-clients.clients.recommendation-service.delegations[0].audience=library-service
ltz.auth.service-clients.clients.recommendation-service.delegations[0].scope=library:recommendation:read
ltz.auth.service-clients.clients.recommendation-service.delegations[0].mode=TARGET_BOUND
```

Önerilen açılış denetimleri (mevcut sertlikte): gizli anahtar ≥ 32 karakter, boşsa istemci kapalı; hedef geçerli `<servis>-service`; kapsam↔mod eşleşmesi sabit;
tanımsız/çift kayıt Auth'u açtırmaz; hata iletisinde gizli anahtar yok.

### C.3 Önerilen teknik yöntem (bağlayıcı değil): E1 delegasyon ucu — örnek

> Bu bölüm D-01 Seçenek A'nın örneğidir; D-01 kararı verilmeden uygulanmaz. Örnekler gerçek uygulama değildir.

```http
POST /internal/v1/service-tokens/delegated
Content-Type: application/json
```

```json
{
  "clientId": "user-service",
  "clientSecret": "<AUTH_USER_SERVICE_CLIENT_SECRET>",
  "audience": "library-service",
  "scope": "library:profile:read",
  "targetUserId": "3f2b8c1e-5a4d-4e9b-9c1a-7d6e5f4a3b21",
  "subjectToken": "<görüntüleyen kullanıcının at+jwt access token'ı>"
}
```

| Alan | Örnek kural |
| --- | --- |
| `audience`, `scope` | zorunlu, tek değer; istemcinin kayıtlı delegasyonlarında olmalı |
| `targetUserId` | zorunlu UUID; kanonik küçük harfe çevrilir |
| `subjectToken` | `VIEWER_BOUND` modda zorunlu, `TARGET_BOUND` modda yasak; `toString()` içinde maskelenir |
| Bilinmeyen alan | reddedilir; gövdede `viewerId` **yoktur**, olsa da kullanılmaz |

Yanıt `200`, `Cache-Control: no-store`, mevcut `ServiceTokenResponse` biçimi: `{ "accessToken": "…", "tokenType": "Bearer", "expiresIn": 30, "scope": "library:profile:read" }`.

İşleyiş (örnek; her adım başarısızsa sonrakiler çalışmaz): (1) istemci doğrulama (`INVALID_CLIENT` mevcut mantık); (2) (istemci, audience, scope) kayıtlı mı
(`SERVICE_AUDIENCE_FORBIDDEN` / yeni `SERVICE_SCOPE_FORBIDDEN`); (3) mod denetimi (`SUBJECT_TOKEN_REQUIRED` / `SUBJECT_TOKEN_NOT_ALLOWED`);
(4) `VIEWER_BOUND` ise `subjectToken` mevcut `jwtDecoder` ile (imza, `typ=at+jwt`, `aud=ltz-api`, oturum aktif, hesap `ACTIVE`, `ver` güncel) doğrulanır,
`viewer_id` = token `sub` (`SUBJECT_TOKEN_INVALID`); (5) `TARGET_BOUND` ise (D-11 kararına bağlı) hedef hesap denetimi; (6) imzalama.

Örnek üretilen token'lar:

```json
{
  "T1 payload (örnek)": {
    "iss": "ltz-auth", "sub": "svc:user-service", "aud": ["library-service"],
    "scope": "library:profile:read",
    "target_user_id": "3f2b8c1e-5a4d-4e9b-9c1a-7d6e5f4a3b21",
    "viewer_id": "9a7c2d10-1b3e-4f6a-8d5c-0e1f2a3b4c5d",
    "iat": 1761830000, "nbf": 1761830000, "exp": 1761830030, "jti": "c6a1f1b2-…"
  }
}
```

T2 (örnek): aynı yapı; `sub=svc:recommendation-service`, `scope=library:recommendation:read`, `viewer_id` yok.

Örnek hata kodları: `VALIDATION_FAILED` (400), `SUBJECT_TOKEN_REQUIRED` / `SUBJECT_TOKEN_NOT_ALLOWED` (400), `INVALID_CLIENT` (401), `SERVICE_AUDIENCE_FORBIDDEN` /
`SERVICE_SCOPE_FORBIDDEN` / `SUBJECT_TOKEN_INVALID` / `TARGET_USER_INVALID` (403), `RATE_LIMITED` (429, isteğe bağlı). Gövde `ProblemDetail` + `code` (mevcut Auth deseni).

Uygulama tuzakları (yöntem ne olursa olsun geçerli): D4 `ServiceTokenIssuer.SCOPE` deseni (`^[a-z]+:[a-z]+$`) üç bölümlü kapsamlarda geçmez; UUID'ler tutarlı biçimde yazılmalı;
düz uç delegasyon kapsamlarını vermemeli; `subjectToken` imzalı çıktıya taşınmamalı.

### C.4 Güvenlik gereksinimlerinin Auth'a yansıması (zorunlu kısım ZG ile, yöntem serbest)

- **Keyfi kullanıcı seçimi yok (ZG-4):** görüntüleyen kimliği hiçbir zaman çağıranın serbest alanından gelmez. Hedef kullanıcı çağıran tarafından belirtilse bile yetki o tek hedefe kilitlenir; asıl erişim kararı Library+User'dadır.
- **En az ayrıcalık (ZG-3):** her delegasyon token'ında tek amaç; kayıt matrisi ve negatif test.
- **Kullanıcı ↔ servis token ayrımı (ZG-3):** `typ` ve `aud` farklı kalır; kullanıcı token'ı üçüncü bir servise taşınmaz.
- **D4 korunur (ZG-9):** `account:erase` akışı, `ServiceTokenIssuer`, Library silme ucu aynen çalışır; regresyon testi.
- **Önbellek:** Auth token önbelleklemez; istemciler kullanıcıya bağlı token'ı kullanıcılar arasında paylaşmaz (Social'ın `AuthServiceTokenClient` önbelleği yalnız `audience` anahtarlıdır; bu desen kullanıcıya bağlı token'lar için kopyalanmamalıdır).
- **Anahtar rotasyonu:** mevcut `additional-public-keys` korunur.
- **Kayıtlar:** başarısız kimlik doğrulama mevcut biçimde loglanır (anahtarsız); kullanıcı kimlikleri INFO'da yazılmaz; token/anahtar hiçbir yerde (ZG-9). İsteğe bağlı: kullanıcı kimliği içermeyen "token verildi" güvenlik olayı.
- **Hız sınırı (isteğe bağlı):** istemci başına dakikada üst sınır.

### C.5 Yapılandırma / ENV (kullanıcı onayı gerektirir — AGENTS.md §5)

Bu görevde ENV'e **dokunmayın**; öneri: `.env.example` ve `docker-compose.yml`'de `auth-service` ortamına seçilen istemci anahtarları (örnek adlar A.2'de), ilgili servislerin ortamına yalnız
**kendi** anahtarı. `ops/generate-auth-dev-keys.mjs` mevcut üretici betiktir. Anahtarlar boşsa istemci kapalı kalır: geçiş sürecinde güvenli varsayılan (tüketiciler fail-closed davranır).

### C.6 `docs/api.md` kaydı (zorunlu)

AUTH bölümüne seçilen yeni/değişen uçlar şablonla yazılır: `Durum`, `Tüketici servisler`, `Yetkilendirme`, `Request Body`, `Response`, `Hata Durumları`, `Bağlantı`, `Test Durumu`.
Test yoksa `NOT IMPLEMENTED`; gerçek servislerle denenmediyse `NOT VERIFIED`.

### C.7 Mimari kararlar — ayrıntılı alternatifler (Auth'un yetkisinde: D-01, D-08, D-11, D-13)

Aşağıdakiler Library'nin sunduğu **alternatif analizleridir.** Nihai karar Auth sahibindedir; Auth burada listelenmeyen, ZG'yi karşılayan başka bir çözüm de seçebilir.
Uygulanabilirlik etiketleri: **U1** = mevcut mimariyle uyumlu · **U2** = ek geliştirme gerektirir · **U3** = güvenlik açısından riskli · **U4** = mevcut güvenlik gereksinimlerini (ZG) karşılamıyor.

#### D-01 — Servis token delegasyonu · Sorumlu: Auth Service

**Çözülen sorun.** User'ın profil okuması ve Recommendation'ın sinyal okuması Library'de kullanıcıya bağlı yetki ister: "hangi kullanıcı adına, hangi kullanıcının verisi, hangi amaçla". Bugün token yalnız çağıran servisi tanıtır (ZG-3), görüntüleyen/hedef kullanıcıyı taşımaz (ZG-4).

**Mevcut durum.** `POST /internal/v1/service-tokens` (client credentials, `audience`); issuer hiçbir kullanıcı claim'i eklemez; D4 `ServiceTokenIssuer` hesaba bağlı (`account_id`) token için emsal; `jwtDecoder` + `SessionStateValidator` kullanıcı token'ını (oturum/hesap/`ver`) doğrulayabilir.

**Seçenekler**

| | A — Yeni delegasyon ucu | B — Mevcut ucun genişletilmesi | C — Token exchange (RFC 8693 tarzı) | D — Servisin kullanıcı bağlamını imzalaması | E — Delegasyon yok, bağlam gövdede |
| --- | --- | --- | --- | --- | --- |
| **Teknik açıklama** | Ayrı uç, ayrı DTO (`targetUserId`, `subjectToken`, `scope`); mod matrisi | `ServiceTokenRequest`'e opsiyonel `scope/targetUserId/subjectToken` | Standart `grant_type=…token-exchange`, `subject_token`, `audience`, `scope`; çıktı kısıtlı token | User, "viewer şudur" beyanını kendi anahtarıyla imzalar; Library ikisini doğrular | Token yalnız servis kimliği; viewer/target istek gövdesinde |
| **Uygulanabilirlik** | U1+U2 | U1+U2 | U2 (yeni uç + farklı içerik türü) | U3 (Auth tek imzalayıcı ilkesini bozar; anahtar dağıtımı) | U4 (ZG-4 yalnız çağırana güvenle) |
| **Avantaj** | Mevcut sözleşme ve 5 istemci bozulmaz; denetimi/testi net | Tek uç, istemci kodu tekrar kullanılır | Standart, genişleyebilir (gelecekte başka amaçlar) | Auth'a iş yok | En az iş |
| **Dezavantaj** | İkinci uç, bakım yüzeyi | Koşullu doğrulama karmaşası, geriye dönük uyumluluk riski | Daha büyük yüzey, mevcut JSON desenlerinden sapma | Compromise edilen User her kullanıcı adına imzalayabilir; çifte güven | Ele geçirilen/hatalı servis herhangi bir `viewer` bildirir |
| **Güvenlik/gizlilik** | `viewer` doğrulanmış `subjectToken` `sub`'ından (ZG-4 ✓) | Aynı (A ile) | Aynı + standart denetimler | ZG-4 zayıf | ZG-4 ✗ |
| **Performans/bakım** | Kullanıcı başına +1 Auth çağrısı (token kısa ömürlü) | Aynı | Aynı | Auth çağrısı yok | Auth çağrısı yok |
| **Diğer servislere etki** | User/Rec yeni istemci yazar | Aynı; mevcut istemciler etkilenmemeli | Aynı + form kodlama | User'da imza altyapısı | Tüketicilerde basit |
| **Library'de uyarlama** | Bugünkü `target_user_id`/`viewer_id` claim'leri korunursa **yok** | Aynı | Claim adları (`act`, `sub` vb.) farklıysa `LibraryInternalReadController.bound/viewer` ve yetki eşlemesi uyarlanır | Yeni doğrulama katmanı | Library'nin kabul sınırı baştan değişir (önerilmez) |

Kullanıcı bağlamı nasıl taşınır / yetkisiz işlem nasıl engellenir: A/B/C'de çağıran, görüntüleyen kullanıcının **kendi access token'ını** Auth'a verir; Auth oturum/hesap/`ver` doğrulamasıyla `viewer_id`'yi kendisi belirler. Hedef kullanıcı tek hedefe kilitlenir; ömür kısadır; tek `aud`. D'de güven User'dadır; E'de hiçbir doğrulanabilir bağ yoktur.

Auth'un yeni bir endpoint oluşturması **zorunlu değildir** (B ve C seçilebilir; Auth ayrıca kendi mimarisine uygun başka bir mekanizma önerebilir).

**Library önerisi (yalnız tercih):** A. Gerekçe: mevcut beş istemcinin sözleşmesi hiç değişmez.

#### D-08 — Delegasyon token geçerlilik süresi · Sorumlu: Auth Service

**Çözülen sorun.** Kullanıcıya/hedefe bağlı token çalınırsa veya yanlış yönlenirse kullanım penceresinin daraltılması.

**Mevcut durum.** Düz servis token'ı 60 sn; D4 token'ı 30 sn; kullanıcı access token'ı varsayılan 60 sn. **Library sınırı:** `exp − iat ≤ 60 sn`, `iat ≤ şimdi + 30 sn`, `exp ≤ şimdi + 60 sn + 30 sn` saat toleransı; 60 sn üstü reddedilir.

| Seçenek | Avantaj | Dezavantaj | Notlar |
| --- | --- | --- | --- |
| 15 sn | En dar tekrar oynatma penceresi | Çok sayfalı toplamada (Recommendation 20 sayfaya kadar) ve yavaş ağda yenileme sıklığı artar; 5 sn okuma zaman aşımı + saat sapmasıyla payı küçük; Auth yükü artar | Library'de değişiklik gerekmez |
| 30 sn | D4 ile aynı politika; çoğu çağrı akışına yeter | Orta düzey | Library'de değişiklik gerekmez |
| 60 sn | Mevcut düz token ile aynı; en az Auth yükü | En geniş pencere (+30 sn tolerans = fiilen 90 sn kabul) | Library üst sınırı tam burası |
| Farklı (mevcut politikayla uyumlu, örn. access token ömrü) | Tek politika | Access TTL ENV ile değiştirilebilir (60→300): ömür sınırı Library'nin 60 sn tavanını aşabilir | > 60 olursa Library sınırı değişir — **önerilmez** |
| Ek koruma (seçeneklerle birleşir) | Tek kullanımlık `jti` (hedef serviste tekrar oynatma kaydı) | Hedefte durum tutmak gerekir; salt okuma uçlarında kazanım sınırlı | Library'de `jti` kayıt deposu eklemek gerekir |

Güvenlik: kısa ömür, çalınan token'ın değerini azaltır; ancak asıl koruma tek `aud` + tek hedef + tek amaçtır. Performans: token üretimi (imzalama + Auth DB oturum kontrolü) kullanıcı başına tek çağrıdır; yük düşüktür.
**Library önerisi (yalnız tercih):** 30 sn. Library etkisi: ≤ 60 sn seçilirse yok.

#### D-11 — Delegasyon token'ında hedef hesap kontrolü · Sorumlu: Auth Service

**Çözülen sorun.** Hedef kullanıcı kimliği çağıran tarafından belirtildiğinde (özellikle sunulan `subjectToken` olmayan modda) rastgele UUID'ler için token basılmasının ve gereksiz hedef serviste çalışma yapılmasının önlenmesi.

**Mevcut durum.** Auth hesap tablosuna sahiptir (`AccountRepository`, `isActive`); Library hesap bilmez; User kendi profil kaydını bilir; D-06 ile User, olmayan owner için `DENY` döner.

| Seçenek | Avantaj | Dezavantaj | Güvenlik/yük |
| --- | --- | --- | --- |
| A — Auth, token üretiminde hedef hesabı doğrular (var + `ACTIVE`) | Keyfi UUID için token yok; tek DB sorgusu; mevcut `AccountRepository` | Token üretimini hesap durumuna bağlar; çağıran "var/yok" bilgisini öğrenir (`TARGET_USER_INVALID`) — enumerasyon sinyali | +1 indeksli sorgu |
| B — Hedef servis (User/Library) yapar | Auth sade | Library hesap bilmez; yalnız User'ın karar ucu (profil var mı) etkili — D-06 ile zaten `DENY` | Auth'ta ek yük yok; geç fark edilir |
| C — İki aşamalı (A + User kararı) | Savunma derinliği | Çifte iş | En sağlam; biraz daha yük |
| D — Hiç ek denetim yok | En basit | Boş yere token; kötüye kullanım yüzeyi | Karar yine User/Library'de güvenli (ZG-2) |

Mevcut Auth mekanizması: `SessionStateValidator`/`AccountRepository` aynı süreçte olduğundan A en ucuz yöntemdir. `VIEWER_BOUND` modunda viewer hesabı zaten `subjectToken` doğrulamasıyla denetlenir.
**Library önerisi (yalnız tercih):** Belirli bir tercih bulunmuyor; C ile D arasında Auth'un bakış açısı belirleyici. Library etkisi: yok (Library yine her istekte User kararına bağlı).

#### D-13 — Kullanıcı JWT'sinin servisler arasında aktarılması · Sorumlu: Auth Service (tüketici: Recommendation)

**Çözülen sorun.** Recommendation (veya User) kullanıcı adına Library verisi okumak ister; kullanıcı yetkisinin başka servise nasıl taşınacağı.

**Mevcut durum.** Kullanıcı access token'ı `aud=ltz-api` (tüm servisler ortak kabul eder), `typ=at+jwt`, roller içerir. Library'nin kişisel uçları (`/api/v1/library/entries…`) bu token'ı kabul eder ve **yazma uçları dahil** kullanıcının tüm yetkilerini tanır; Recommendation izni (ZG-6) kişisel uçlarda aranmaz.

| Seçenek | Açıklama | Uygulanabilirlik | Avantaj | Dezavantaj | Güvenlik |
| --- | --- | --- | --- | --- | --- |
| A — Kullanıcı JWT'sini doğrudan aktarma | Recommendation, kullanıcı token'ını Library kişisel uçlarına iletir | Teknik olarak **bugün çalışır**; **U3/U4** | Ek geliştirme yok | `aud=ltz-api` tüm servislerde geçer (tekrar oynatma); kullanıcının **tüm** hakları (silme/ekleme dahil) devredilir; amaç/izin ayrımı yok (ZG-6); arka plan işleri çalışmaz; Library kişisel ucu fazla veri döner (ZG-8) | **Güvenli kabul edilmez** |
| B — Servise/amaca özel yetkilendirilmiş token | Auth, tek hedef/amaç/kullanıcı için kısa ömürlü servis token'ı verir (T2 gibi) | U1+U2 (D-01 ile aynı mekanizma) | Dar yetki, `aud` bağlı, arka planda çalışır | Auth'ta mekanizma gerekir | ZG-3/4 ✓; hedef kullanıcıyı çağıran beyan eder (kullanıcı hazır değil) |
| C — Token exchange (kullanıcı token'ı → kısıtlı servis token'ı) | Recommendation, kullanıcının token'ını Auth'a verir; Auth `aud`/`scope`/hedefi kısıtlı yeni token üretir | U2 | Kullanıcının **o anda var olduğunu** kanıtlar; ham JWT Library'ye gitmez | Yalnız kullanıcı kaynaklı akışlarda; arka plan için B gerekir | En güçlü bağ; token ikinci servise gitmez |
| D — Kullanıcıya bağlı olmayan servis token'ı | Yalnız servis kimliği; hedef yolda | U4 (ZG-4 zayıf) | En basit | Ele geçirilen Recommendation her kullanıcı için okuyabilir (izin kontrolü User'da olsa da hedef bağı yok) | Önerilmez |

Tekrar oynatma / yetki sınırı karşılaştırması: A'da token Library'den başka servise de oynatılabilir ve tüm yetkileri taşır; B/C'de `aud` tek, `scope` tek, hedef tek, ömür kısa.
**Library önerisi (yalnız tercih):** B (arka plan işlerini de kapsar); kullanıcı kaynaklı akışta C ile birleştirilebilir. A önerilmez. Library etkisi: B/C seçilirse claim adlarına göre D-01 ile aynı uyarlama.

### C.8 Library'nin bu işte Auth'tan **talep etmedikleri**

Mevcut `service-tokens` imzasının değiştirilmesi, mevcut beş istemcinin davranış değişikliği, kullanıcı token'ı sözleşmesi (`at+jwt`) değişikliği, D4 silme akışında değişiklik ve Library'nin kendi kodunu Auth tarafından değiştirmesi talep edilmez.

---

## D) Sıralı Geliştirme Checklisti

Sıra bağımlılığa göre önerilir. Bu belge Library'nin orijinal gereksinim dokümanıdır: aşağıdaki kutular `[ ]` olarak kalır ve servis sahibi tarafından değiştirilmez.
Tamamlanma durumu, kabul kriteri **gerçekten** sağlandığında servis sahibinin kendi sonuç raporunda ve çalışma sonunda sunduğu checklistte `[x]` olarak izlenir. Kabul kriterleri **ZG-x** ile yazılmıştır;
"Örnek yöntemde" ifadesi A.2/C.3'teki öneriyi gösterir, başka bir yöntem seçildiyse eşdeğer kriter uygulanır ve A.4'e göre Library'ye bildirilir.

- [ ] **AUTH-LIB-01 — Mimari kararların verilmesi ve kayda geçirilmesi.** D-01, D-08, D-11, D-13 için Auth kararı; çok servisli etkisi olanlar User/Recommendation/Social sahipleriyle paylaşılır.
  *Kabul:* karar gerekçesiyle yazıldı; A.3'e göre etkilenen sözleşmeler ve sahipleri listelendi.
- [ ] **AUTH-LIB-02 — Düz servis kimlikleri.** Library→User gizlilik kararı ve User→Social ilişki sorgusu için istemci kayıtları ve kapsam tanımı.
  *Kabul (ZG-3):* doğru anahtarla token alınır; yanlış hedef reddedilir; anahtar boşken istemci kapalı. *Örnek yöntemde:* T3, T4; `privacy:library:check`, `friendships:check` allowlist'e eklenir.
- [ ] **AUTH-LIB-03 — Kullanıcıya/hedefe bağlı yetki mekanizması.** Seçilen D-01 yöntemiyle User profil okuması ve Recommendation sinyal okuması için.
  *Kabul (ZG-3, ZG-4, ZG-5):* görüntüleyen kimliği doğrulanmış kullanıcı bağlamından gelir; çağıran keyfi kullanıcı seçemez; süresi dolmuş/oturumu kapanmış/`ver`'i değişmiş/hesabı ACTIVE olmayan kullanıcı bağlamı reddedilir; servis↔kullanıcı token ayrımı korunur.
- [ ] **AUTH-LIB-04 — En az ayrıcalık matrisi.** `user-service` Recommendation amacıyla, `recommendation-service` profil amacıyla veri alamaz.
  *Kabul (ZG-3):* çapraz amaç istekleri reddedilir (negatif test).
- [ ] **AUTH-LIB-05 — Token ömrü ve hedef denetimi.** D-08, D-11 sonuçlarının uygulanması.
  *Kabul (ZG-11 ile uyumlu):* seçilen ömür Library sınırını (≤ 60 sn) aşmıyorsa Library'de değişiklik yoktur; aşıyorsa Library'ye bildirilir.
- [ ] **AUTH-LIB-06 — Hata sözleşmesi.** Seçilen yöntem için hata kodları ve `ProblemDetail`.
  *Kabul (ZG-9):* hiçbir yanıt/log token veya anahtar yansıtmaz; hatalar tüketicinin ayrıştırabileceği biçimde belgelenir.
- [ ] **AUTH-LIB-07 — Library kabul sınırı uyumu.** Üretilen token'lar Library'nin (uyarlanmış) doğrulayıcısını geçer.
  *Kabul:* sözleşme testi: Auth'un gerçek token'ı Library doğrulama mantığıyla yazılmış testte kabul edilir; yanlış varyantlar reddedilir. Sözleşme farklıysa önce Library uyarlaması (Hamza) yapılır.
- [ ] **AUTH-LIB-08 — D4 regresyonu (ZG-9).** `account:erase` akışı, `ServiceTokenIssuer`, silme görev kuyruğu aynen çalışır.
- [ ] **AUTH-LIB-09 — Belgeler.** `docs/api.md` AUTH kaydı, `docs-auth/servis-sozlesmeleri.md` §3.5 tablosu, `docs-auth/entegrasyon/library-service.md` ve `recommendation-service.md`.
- [ ] **AUTH-LIB-10 — ENV/Compose önerisi (uygulamadan).** C.5 için diff önerisi; uygulama kullanıcı onayıyla.
- [ ] **AUTH-LIB-11 — Sözleşmenin Library'ye bildirilmesi.** A.4 şablonuyla Hamza'ya teslim.

---

## E) Test Senaryoları

Senaryolar **gereksinim temellidir**; ayrıntılı alan adları seçilen yönteme göre uyarlanır.

**Unit**
- İstek doğrulama (bilinmeyen alan, biçim), gizli değerlerin `toString()`/log maskelemesi.
- İstemci-hedef-amaç-mod matrisi (seçilen yapılandırma biçimine göre).
- Token üretici: tek `aud`, amaç sınırı, kullanıcı/hedef bağ claim'leri, kısa ömür, benzersiz `jti`, rol/oturum bilgisinin taşınmaması.

**Integration (gerçek PostgreSQL, gerçek RSA/JWKS)**
- Pozitif akışlar ve JWKS ile imza doğrulama.
- Kullanıcı bağlamı doğrulaması: oturum kapatma, tüm oturumlardan çıkış (`ver` artışı), hesap askıya alma/yasaklama sonrası delegasyon isteği reddedilir (ZG-2, ZG-4).
- Düz uç davranış değişmezliği: mevcut beş istemcinin testleri yeşil.

**Güvenlik / negatif**
- Yanlış anahtar → tek tip `INVALID_CLIENT`.
- Yanlış amaç: `user-service` ile öneri amacı, `recommendation-service` ile profil amacı reddedilir (ZG-3).
- Düz uçtan delegasyon amaçlı kapsam alınamaz.
- Kullanıcı bağlamı olarak servis token'ı veya yanlış `aud` taşıyan token verilirse reddedilir.
- Aynı kullanıcı bağlamıyla farklı hedef isteği: görüntüleyen ve hedef birbirinden türetilmez.
- İstek gövdesinde görüntüleyen/rol/oturum alanı enjeksiyonu reddedilir veya yok sayılır (ZG-4).
- Log temizliği (ZG-9); D4 token'ıyla Library silme ucu hâlâ başarılı.

**Gerçek servisler arası E2E (tüm taraflar hazır olunca; Hamza ile)**
1. Library→User karar çağrısı gerçek servis token'ıyla.
2. User, kullanıcı bağlamlı token'la Library profil ucunu çağırır → `200`; başka kullanıcı adına imzalatma girişimi başarısız.
3. Recommendation, hedefe bağlı token'la sinyal ucunu çağırır → izin varsa `200`, yoksa `403`.
4. Süresi dolmuş token Library'de `401`.

---

## F) Tamamlanma Kriterleri

Auth işi **tamamlandı** sayılır, ancak ve ancak:

1. AUTH-LIB-01…09 ve 11 kabul kriterleri sağlanmış (servis sahibinin sonuç raporundaki checklistte `[x]`); ilgili testler yeşil; `docs/api.md` ve `docs-auth` belgeleri güncel.
2. Seçilen yöntemle Auth'un ürettiği **gerçek** token'lar, (gerekirse uyarlanmış) Library iç yollarında kabul edilir; yanlış kimlik/amaç/hedef/kullanıcı varyantları reddedilir.
3. Mevcut Auth testleri ve D4 silme akışı bozulmamıştır (ZG-9).
4. Seçilen sözleşme A.4'e göre Library'ye bildirilmiştir; gerçek servislerle denenmeyen kısım `NOT VERIFIED` olarak açıkça yazılmıştır.

Library tarafında (Hamza) doğrulanacak: kararlaştırılan sözleşmeye göre doğrulayıcı/claim uyarlaması, T3'ü alan HTTP görünürlük adaptörü, Auth erişilemezken kapalı kalma (ZG-2). Library ilerlemesi (15/18)
bu belgenin teslimiyle değişmez; LIB-10/11/14 ancak tüm tarafların işi bitip birlikte E2E doğrulanınca işaretlenir.

Diğer servis sahiplerinden beklenen: User (karar ucu + istemciler), Recommendation (istemci), Social (ilişki sorguları).

---

## G) Yapay Zekâ Geliştirme Talimatları

Bu bölümü, yapay zekâ geliştirme aracınıza **olduğu gibi** verin.

```text
Sen LTZ (LobbyTwoZero) reposunda auth-service için çalışıyorsun. Sahip: Kerem.
Görev: docs-library/request/auth-library-Integration-request.md belgesindeki ihtiyaçları, Auth'un KENDİ mimarisine uygun biçimde karşılamak.

Çalışma sırası ve kurallar:
1. ÖNCE kendi servisinin (auth-service) mevcut mimarisini incele: ServiceTokenController, ServiceClientTokenService, ServiceClientTokenIssuer,
   ServiceClientProperties/Config, ServiceTokenIssuer (D4), JwtConfig/SessionStateValidator, docs/api.md AUTH, docs-auth/*, docs-security/auth-service/README.md ve NOTLAR.md.
   AGENTS.md ve CLAUDE.md zorunlu okuma sırasını uygula. Yeni Checkstyle/PMD ihlali ekleme.
2. Kendi gereksinimlerini ve mevcut güvenlik sözleşmelerini (docs/SECURITY.md, docs-auth/servis-sozlesmeleri.md) değerlendir.
3. Belgedeki T1–T4 / E1–E5 tabloları Library'nin ÖNERDİĞİ başlangıç sözleşmesidir; ZORUNLU DEĞİLDİR. Önerilen endpoint, token modeli, scope/claim adı ve DTO'yu
   kabul etmek zorunda değilsin. Belgedeki ZG-1…ZG-11 zorunlu gereksinimlerdir; bunları karşılamak zorundasın.
4. D-01, D-08, D-11, D-13 için belgedeki alternatifleri değerlendir; istersen önerilenlerden birini, istersen ZG'yi karşılayan kendi çözümünü seç.
   Seçimi ve gerekçesini yaz. Güvenli olmayan (U3/U4) bir seçeneği seçme.
5. Çok servisli etkisi olan sözleşmeyi (token biçimi, claim/scope adları, edinme yolu) UYGULAMADAN ÖNCE ilgili sahiplerle (User: Kerem, Recommendation: Alper,
   Social: Umut) ve Library sorumlusu Hamza ile koordine et. Belirsizlik veya çelişki varsa UYDURMA: dur, kullanıcıya sor.
6. Yalnız backend/services/auth-service kodunu, testlerini, gerekirse auth migration aralığını (V61–V70), docs/api.md AUTH bölümünü ve docs-auth belgelerini değiştir.
   library-service, user-service, recommendation-service, social-service, frontend, gateway, Maven/CI dosyalarına DOKUNMA.
   Library'nin gerekli uyarlamasını KENDİN YAPMA; seçtiğin sözleşmeyi A.4 şablonuyla Library sorumlusuna bildir.
   docs-library/ altındaki hiçbir Library belgesini (bu belge dahil) DEĞİŞTİRME.
7. Mevcut mimariyi koru: mevcut POST /internal/v1/service-tokens sözleşmesini ve mevcut beş istemcinin davranışını, D4 (account:erase) yolunu bozma.
8. .env, .env.dev, *.bak, frontend/.env.local dosyalarını OKUMA. ENV/Compose değişikliği için kullanıcı onayı al; önce .env.example önerisi sun.
9. Seçtiğin yaklaşımın Library'nin güvenlik ve işlevsel ihtiyaçlarını (ZG-1…ZG-11) karşıladığını testlerle doğrula (belgenin E bölümü; gerçek PostgreSQL ve gerçek RSA/JWKS).
10. Pre-push ve SonarQube ÇALIŞTIRMA; bunları kullanıcı elle yapacak. Yalnız hedefli Maven testlerini çalıştır ve sonucu dürüstçe raporla.
11. git commit / git push YAPMA. İş bitince AGENTS.md §7 formatında yalnız commit mesajı öner (atıf satırı ekleme).
12. Bu belgenin (docs-library/request/) D bölümündeki checklisti DEĞİŞTİRME; belge Library'nin orijinal gereksinim ve sözleşme dokümanıdır ve `[ ]` olarak kalır.
    Tamamlanan görevleri kendi servisinin sonuç raporunda ve çalışma sonunda sunduğun checklistte [x] olarak işaretle.
    Gerçek E2E yapılmadıysa entegrasyonu tamamlanmış gibi raporlama; docs/api.md'de NOT VERIFIED yaz.
    Çalıştırılamayan veya başarısız adımı gizleme.
```

---

## H) Alternatifler ve Library Service Önerileri

Bu bölüm yalnız Auth'un sorumluluğundaki kararların özetidir (ayrıntı: C.7). Buradaki "Önerilen" alanları **yalnızca Library'nin tercihidir**; zorunlu değildir ve
Auth sahibinin bunu seçmesi beklenmez. Güvenli ve işlevsel gereksinimleri (ZG) karşılayan her alternatif kabul edilebilir.

### D-01 — Servis Token Delegasyonu

**Seçenekler:**
- Seçenek A: Yeni bir delegasyon endpointi (kullanıcı bağlamlı ve hedefe bağlı token).
- Seçenek B: Mevcut `service-tokens` endpointinin opsiyonel alanlarla genişletilmesi.
- Seçenek C: Token exchange (RFC 8693 tarzı) yaklaşımı.
- Seçenek D: Auth'un kullanıcı bağlamını imzalamadığı, servisin imzaladığı model (U3, önerilmez).
- Seçenek E: Delegasyonsuz, bağlamın gövdede taşınması (U4, ZG-4'ü karşılamaz).
- Seçenek F: Auth'un mimarisine uygun, ZG'yi karşılayan başka bir mekanizma.

**Önerilen:** Seçenek A (Library'nin tercihi; yalnızca öneridir).

**Öneri gerekçesi:** Mevcut beş istemcinin sözleşmesini ve testlerini hiç etkilemez; denetimi ve testi ayrıdır.

**Nihai karar yetkisi:** İlgili servis sahibi (Auth — Kerem).

**Library Service'e etkisi:** Bugünkü `target_user_id`/`viewer_id` claim adları ve scope adları korunursa Library'de değişiklik yok. Farklı claim/scope adı veya token biçimi seçilirse
`LibraryInternalReadController` ile güvenlik yapılandırması ve test token'ları uyarlanır; E seçilirse Library kabul sınırı baştan yeniden tasarlanır.

### D-08 — Delegasyon Token Geçerlilik Süresi

**Seçenekler:**
- Seçenek A: 15 saniye.
- Seçenek B: 30 saniye.
- Seçenek C: 60 saniye (Library'nin kabul tavanı).
- Seçenek D: Mevcut Auth politikasıyla uyumlu farklı bir süre (60 sn'yi aşarsa Library sınırı değişir).
- Seçenek E: Yukarıdakilerden biri + tek kullanımlık `jti` koruması.

**Önerilen:** Seçenek B (Library'nin tercihi; yalnızca öneridir).

**Öneri gerekçesi:** D4 token politikasıyla aynı; çok sayfalı okumalarda yenileme sıklığı makul, tekrar oynatma penceresi dar.

**Nihai karar yetkisi:** İlgili servis sahibi (Auth — Kerem).

**Library Service'e etkisi:** ≤ 60 sn seçilirse yok; > 60 sn seçilirse Library doğrulayıcı üst sınırı değişir; `jti` tekrar oynatma kaydı seçilirse Library'ye kayıt deposu eklenir.

### D-11 — Delegasyon Tokenında Hedef Hesap Kontrolü

**Seçenekler:**
- Seçenek A: Auth, token üretiminde hedef hesabın varlığını ve aktifliğini doğrular.
- Seçenek B: Doğrulama hedef servis tarafında (User kararı) yapılır.
- Seçenek C: İki aşamalı doğrulama (Auth + User).
- Seçenek D: Ek hesap denetimi yok.
- Seçenek E: Auth'un mevcut hesap doğrulama mekanizmalarıyla uyumlu başka bir yöntem.

**Önerilen:** Belirli bir tercih bulunmuyor. Nihai yöntem ilgili servis sahibi tarafından belirlenecek.

**Öneri gerekçesi:** Library her durumda User kararına bağlıdır; bu seçim Auth'un servis bağımlılığı ve yük tercihine bağlıdır.

**Nihai karar yetkisi:** İlgili servis sahibi (Auth — Kerem).

**Library Service'e etkisi:** Yok; Library hesap bilgisi tutmaz.

### D-13 — Kullanıcı JWT'sinin Servisler Arasında Aktarılması

**Seçenekler:**
- Seçenek A: Kullanıcı JWT'sinin doğrudan aktarılması (U3/U4 — güvenli kabul edilmez).
- Seçenek B: Servise/amaca özel yetkilendirilmiş token.
- Seçenek C: Token exchange/delegasyon ile kullanıcı token'ından kısıtlı token.
- Seçenek D: Kullanıcıya bağlı olmayan servis token'ı (ZG-4 zayıf, önerilmez).
- Seçenek E: Auth mimarisindeki diğer uygun mekanizmalar.

**Önerilen:** Seçenek B; kullanıcı kaynaklı akışlarda C ile birleştirilebilir (Library'nin tercihi; yalnızca öneridir).

**Öneri gerekçesi:** `aud`, kapsam ve hedef kullanıcı daraltılır, arka plan işlerini de kapsar, ham kullanıcı JWT'si ikinci servise gitmez.

**Nihai karar yetkisi:** İlgili servis sahibi (Auth — Kerem); tüketici Recommendation (Alper) ile uzlaşılır.

**Library Service'e etkisi:** B/C için D-01 ile aynı claim/scope uyarlaması; A seçilirse (önerilmez) Library kişisel uçlarının öneri amacıyla kullanılması ZG-6 nedeniyle yeniden değerlendirilir.
