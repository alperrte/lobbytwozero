# LTZ Ekip Notu: Auth, API Gateway ve Servislerin Birlikte Çalışması

> Tarih: 2026-10-21 · Hazırlayan: Kerem (auth, user, mail) · Dal: `feature/auth-service`
> Bu belge **kısa ve basit** anlatır, ayrıntı için ilgili kanonik belgeye bağlanır. Yeni kural icat etmez;
> `docs/SECURITY.md`, `docs-auth/servis-sozlesmeleri.md`, `docs/api.md` ve gerçek koda uyar.

## 0. Bu belgeyi nasıl kullanacaksınız?

1. **Herkes §1–§3'ü bir kez okur** (5 dakika). Auth'un ve Gateway'in ne yaptığını anlatır.
2. **Kendi bölümünüzü** §5'ten bulun (hangi servis kimin: `AGENTS.md` §1).
3. §7'deki **"Claude'a yapıştır" bloğunu** servisinizin `CLAUDE.md` dosyasına ya da Claude hafızasına bir kez ekleyin. Böylece Claude her
   oturumda Auth kurallarını bilir. Ortak blok (7.1) herkes için, sahibe özel blok (7.2–7.7) yalnız ilgili kişi için.
4. Auth'a bağlanırken teknik ayrıntı gerekirse tek kaynak: **[`docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`](AUTH_SERVICE_ENTEGRASYON_REHBERI.md)**.

Durum işaretleri: **VAR** = kodda var ve test edildi · **SÖZLEŞME** = karar yazıldı, kod yok · **EKSİK** = henüz yok/karar yok.

---

## 1. Resim: kim kiminle konuşuyor?

```mermaid
flowchart LR
  B[Tarayıcı / Next.js :5001] -->|credentials: include| GW[API Gateway :5080]
  GW -->|/api/v1/auth/**| AUTH[Auth Service]
  GW -->|/api/v1/&lt;servis&gt;/** + Authorization: Bearer| S[Diğer 16 servis]
  AUTH --> ADB[(auth_db)]
  S -. JWKS ile token doğrular .-> AUTH
  AUTH -. e-posta .-> MAIL[Mail Service - EKSİK]
  AUTH -. hesap silme çağrısı .-> S
```

Üç cümleyle:

- **Auth** kimlik, parola, oturum, rol, 2FA ve hesap durumunun **tek sahibi**. Başka servis Auth'un veritabanına girmez.
- **Tarayıcı** token'ı JavaScript ile görmez. Auth, token'ı **HttpOnly çerezde** tutar. Tarayıcı her isteğe çerezi kendisi ekler.
- **Gateway** çerezi okuyup servislere **`Authorization: Bearer <token>`** olarak iletir. **Her servis token'ı kendisi doğrular** (Gateway'e güvenmez).

### 1.1 Neden "çerezi Bearer'a çeviriyoruz"?

Çerez güvenli (XSS çalamaz) ama servisler Bearer bekler. Çerezle gelen isteği Bearer'a çevirince servislerdeki CSRF koruması devre dışı kalır
(Bearer isteği CSRF'den muaf sayılır). Bu yüzden **CSRF kontrolünü Gateway yapar**: durum değiştiren (POST/PUT/PATCH/DELETE) çerezli istekte
`X-XSRF-TOKEN` başlığı `XSRF-TOKEN` çerezine eşit olmalı, `Origin` izinli olmalı. Aksi halde istek servisinize **hiç ulaşmaz** (403).

### 1.2 Gateway'in şu an yaptıkları (VAR, `api-gateway`)

| Özellik | Kural |
| --- | --- |
| CORS | Yalnız `CORS_ALLOWED_ORIGINS` listesindeki tam origin'ler; `*` yasak; `credentials=true` |
| Çerez → Bearer | `/api/v1/**` (Auth hariç) isteğinde `Authorization` yoksa ve erişim çerezi varsa Bearer eklenir |
| CSRF + Origin | Çerezle gelen POST/PUT/PATCH/DELETE'de zorunlu; hata `403` + `CSRF_TOKEN_INVALID` / `ORIGIN_NOT_ALLOWED` |
| Başlık temizliği | İstemciden gelen `X-User-*`, `X-Account-*`, `X-Auth-*`, `X-Internal-*`, `X-Role(s)` silinir; `Forwarded`/`X-Forwarded-*` yalnız `GATEWAY_TRUSTED_PROXIES` ile güvenilen bağlantıdan korunur |
| Hız sınırı | `/api/v1/auth/**` için IP başına dakikada 120 (ayarlanır, JWKS muaf); aşımda `429` + `Retry-After` |

Gateway **yapmaz**: JWT doğrulaması, rol kontrolü, sahiplik kontrolü. Bunlar servislerinizin işi.

---

## 2. Herkes için 10 kural

1. Token'ı **kendiniz doğrulayın**: JWKS `GET /api/v1/auth/.well-known/jwks.json`, yalnız **RS256**, `iss=ltz-auth`, `aud` içinde `ltz-api`, başlık `typ=at+jwt`, `exp`.
2. Kullanıcı kimliği **yalnız `sub`** claim'inden gelir (hesap UUID'si). Gövdeden, sorgudan veya `X-User-Id` gibi başlıktan gelen kimliğe **güvenmeyin**.
3. Rol **`roles`** claim'inden gelir (öneksiz: `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`). Başka rol yoktur.
4. **Rol "ne yapabilir" der, "hangi kayda dokunabilir" demez.** Sahiplik kontrolü sizin veritabanınızda: `kayıt.owner_id == sub`.
5. Varsayılan **reddet**: her endpoint ya açıkça herkese açık ya da açıkça rol/kimlik ister.
6. **401** = kimlik yok/geçersiz, **403** = kimlik var ama yetki yok, **404** = yok ya da güvenli gizleme. Gövde `ProblemDetail`, içinde token/SQL/stack trace yok.
7. Başkasının kaydı için 404 veya 403 verin, **kayıt varmış gibi ayrıntı sızdırmayın**; servisinizde tutarlı olun.
8. Kullanıcı hesabı **askıya alınır/yasaklanırsa** token en fazla **5 dakika** (access token ömrü) sizde geçerli görünür. Bunu bilerek tasarlayın.
9. Hesap silinirse Auth size **`DELETE /internal/v1/accounts/{accountId}`** çağırır. Kişisel veriyi silin/anonimleştirin, **idempotent** olun (ikinci çağrı da 2xx). Servis kimliği (D4) gelene kadar bu ucu Gateway'e açmayın.
10. Auth DB'sine, başka servisin DB'sine **asla** doğrudan erişmeyin. Sır, token, parola loglamayın, koda yazmayın.

---

## 3. İstemci (frontend) Auth ile nasıl konuşur?

Frontend `frontend/src/shared/lib/api/client.ts` içindeki `apiRequest` ile konuşur ve `credentials: "include"` kullanır. Adımlar:

| Adım | İstek | Not |
| --- | --- | --- |
| 1. CSRF çerezi al | `GET /api/v1/auth/csrf` → `204` | Çerez adı `__Host-XSRF-TOKEN` (güvenli ortam) veya `XSRF-TOKEN` (dev'de Secure kapalıysa). JavaScript okuyabilir |
| 2. Kayıt | `POST /api/v1/auth/register` → `202` | Çerezsiz, CSRF gerekmez. E-posta/kullanıcı adı varsa da aynı yanıt |
| 3. E-posta doğrula | `POST /api/v1/auth/email-verification/confirm` | `{email, code, password}` (kayıt parolası da gerekir) |
| 4. Giriş | `POST /api/v1/auth/login` + `X-XSRF-TOKEN: <çerez değeri>` | `status: AUTHENTICATED` → çerezler yazıldı. `MFA_REQUIRED` → `challengeId` ile `POST /login/2fa` |
| 5. Servis çağrısı | `fetch(url, {credentials:"include"})` | Çerez otomatik gider. **POST/PUT/PATCH/DELETE'e `X-XSRF-TOKEN` ekleyin** |
| 6. 401 gelirse | `POST /api/v1/auth/session/refresh` (+ `X-XSRF-TOKEN`) | Başarılıysa isteği **bir kez** tekrarlayın; değilse giriş sayfası |
| 7. Çıkış | `POST /api/v1/auth/session/logout` (+ `X-XSRF-TOKEN`) | Çerezler silinir |

Google/Steam girişi: tarayıcıyı `GET /api/v1/auth/oauth/google/start` (veya `steam/start`) adresine yönlendirin. Sonuç **`FRONTEND_PUBLIC_URL/auth/callback`** adresine
**URL fragment'i** ile döner: `#status=authenticated`, `#status=mfa_required&challenge=…`, `#status=registration_required&token=…` (yalnız Steam) veya
`#status=email_verification_required&proof=…` (yalnız Steam). Tam sözleşme: `docs/api.md` → AUTH.

Hata biçimi (`ProblemDetail`): `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `429` (+ `Retry-After`). Gateway'in kendi hataları: `403 CSRF_TOKEN_INVALID`,
`403 ORIGIN_NOT_ALLOWED`, `429 RATE_LIMITED`.

> **Önemli (EKSİK, frontend):** Mevcut `apiRequest` **`X-XSRF-TOKEN` başlığını eklemiyor** ve `features/auth` boş. Auth'a POST atan her çağrı bu haliyle `403` alır.
> Önerilen küçük ek (frontend sahibi uygular, ayrı PR):
>
> ```ts
> const XSRF_COOKIES = ["__Host-XSRF-TOKEN", "XSRF-TOKEN"];
> function xsrfToken(): string | undefined {
>   for (const name of XSRF_COOKIES) {
>     const hit = document.cookie.split("; ").find((c) => c.startsWith(name + "="));
>     if (hit) return decodeURIComponent(hit.slice(name.length + 1));
>   }
> }
> // apiRequest içinde, GET/HEAD dışındaki isteklerde:
> //   headers.set("X-XSRF-TOKEN", xsrfToken() ?? "")   (yoksa önce GET /api/v1/auth/csrf çağrılır)
> ```

---

## 4. Eksikler listesi (kim, ne, ne kadar acil)

Öncelik: **K** kritik (prod'u engeller) · **Y** yüksek · **O** orta · **D** düşük. Ayrıntı: `docs-auth/acik-guvenlik-borclari.md`.

| # | Eksik | Sahibi | Öncelik | Not |
| --- | --- | --- | --- | --- |
| 1 | **Mail Service yok** (doğrulama, sıfırlama, silme onayı, bildirim e-postaları) | Kerem (mail) | **K** | Auth'ta `prod` profili bu yüzden bilerek açılmıyor. Dev'de Mailpit ile çalışır |
| 2 | **Servislerde JWT doğrulaması yok** | Her servis sahibi | **K** | Rehberdeki Spring örneği kullanılır; ortak Security modülü gelince onunla değişir |
| 3 | **Hedef servislerde silme ucu yok** (`DELETE /internal/v1/accounts/{id}`) | Her veri sahibi servis | **Y** | Yoksa hesap `DELETED` olmaz, kişisel veri Auth'ta kalır |
| 4 | **Servis kimliği (D4) yok** (`svc+jwt`) | Kerem + ortak Security | **Y** | Admin→Auth ve Auth→servis iç çağrıları buna bağlı |
| 5 | **Frontend CSRF başlığı ve auth sayfaları yok** | Frontend sahibi | **Y** | §3'teki not |
| 6 | **Silme hedef listesinde `game` ve `admin` yok** | Hamza + Kerem | **Y** | `accountId` içeren veri tutuyorlarsa listeye eklenmeli (S2) |
| 7 | **Bağımlılık yamaları (Tomcat/Jackson CVE) yalnız auth-service'te** | Parent POM sahibi | **Y** | Gateway dahil diğer 17 modül eski sürümde (B13) |
| 8 | **Durum değişikliği yayılımı yok** (`AccountStatusChanged`, D3) | Kerem + altyapı | O | Şimdilik en fazla 5 dk gecikme |
| 9 | **İlk ADMIN oluşturma yolu yok** | Proje sahibi | O | Şimdilik elle SQL ile rol verilir (B7) |
| 10 | **Cihaz kimliği yok** (PERF-01 masaüstü cihaz eşleştirme; `SECURITY.md`: cihaz token'ı kullanıcı token'ı değildir) | Alper + Kerem | O | Auth'ta bu yetenek yok, tasarım kararı gerekir |
| 11 | **`UserRegistered` olayı yok** (Auth → User) | Kerem | O | Outbox/User Service yok |
| 12 | Gerçek Google/Steam ile uçtan uca deneme yok | Kerem | O | Client id/secret gerekli (B14) |
| 13 | Çok örnekli Gateway için ortak hız sınırı (Redis vb.) | Altyapı | O | Şimdi örnek başına, bellekte |
| 14 | Anahtar rotasyonu yok (JWT, TOTP, HMAC) | Kerem | O | S9 |
| 15 | Parola politikası: en az 12, kara liste yok | Ürün + Kerem | O | S10 (NIST: ≥15 + kara liste) |
| 16 | Hesap bağlama/ayırma, BANNED kaldırma, süreli askı, "parola ekle" yok | Ürün + Kerem | D | B8, B10, B11 |
| 17 | Güvenlik olayı saklama süresi ve silme sonrası geri alma kararı yok | Hukuk/ürün | D | B9, B17, S1 |
| 18 | Servis başına DB rolü yok (hepsi `ltz_dev`) ve prod dağıtımı (sır yöneticisi, prod compose) tasarlanmadı | Altyapı | **Y** | Prod öncesi |
| 19 | Yetkisiz erişim negatif testleri (`SECURITY.md` hedefi 20/20) servislerde yok | Her servis sahibi | O | Rehberdeki test listesi |

---

## 5. Servis servis notlar

Sahip tablosu `AGENTS.md` §1'dedir. "Silme hedefi" = hesap silinince Auth bu servisi arar (Auth ayarı `ltz.auth.deletion.targets`).

### 5.1 Altyapı ve Auth tarafı (Kerem)

| Servis | Durum | Notlar |
| --- | --- | --- |
| **auth-service** | VAR (dev) | Kayıt, e-posta doğrulama, giriş, 2FA, Steam/Google, JWT+JWKS, rotasyon, parola, rol/durum yönetimi, silme koordinasyonu. 249 test. `prod` Mail bekliyor |
| **api-gateway** | VAR (§1.2) | Hız sınırı bellekte; `GATEWAY_TRUSTED_PROXIES` yük dengeleyici arkasında ayarlanır |
| **user-service** | İSKELET | Profil sahibi. Auth'tan `UserRegistered` bekler (EKSİK, #11). Parola/refresh token **tutmaz**. Steam/Discord bağlantısı (USER-07) bu servisin; Auth'un Steam **girişi**yle karışmasın, Steam kimlik kanıtını user-service **üretmez**. Silme hedefi |
| **mail-service** | İSKELET | Auth 3 port ile çağırır: doğrulama kodu, parola sıfırlama, silme onayı (+ MAIL-03 bildirimleri). Token **üretmez**, loglamaz, düz metin saklamaz. Silme hedefi |

### 5.2 Hamza: game, library, review

| Servis | Auth'tan beklenen | Yapılacaklar |
| --- | --- | --- |
| **game-service** | GAME-07 yönetim: `ADMIN`. GAME-12 geliştirici: `GAME_DEVELOPER` rolü | Katalog okuma herkese açıksa açıkça `permitAll`. **Oyun bazlı geliştirici sahipliği Game DB'sinde** (Auth oyun yetkisi taşımaz). Silme hedefi **değil**; `accountId` tutuyorsa (sahiplik, audit) Kerem'e bildirin (#6) |
| **library-service** | `sub` | `userId = sub`, gövdede `userId` alanı yok. `(userId, gameId)` benzersiz. Başkasının koleksiyonu 404/403. Silme hedefi |
| **review-service** | `sub`, silme/moderasyon için `MODERATOR`/`ADMIN` | Başkası adına inceleme yok; düzenle/sil `owner == sub`. Moderatör işlemi audit'li. Silme hedefi: içerik/puan için silme-anonimleştirme kuralını belirleyin |

### 5.3 Alper: hardware, recommendation, performance

| Servis | Auth'tan beklenen | Yapılacaklar |
| --- | --- | --- |
| **hardware-service** | `sub`; yorum moderasyonu admin-service'e | Kendi donanım profilini silme (HW-16) `owner == sub`. Silme hedefi |
| **recommendation-service** | `sub` | Kullanıcıya özel öneri verisi varsa silme ucu. Silme hedefi |
| **performance-service** | `sub`; **masaüstü cihaz kimliği EKSİK** (#10) | Kullanıcı verisini silme (PERF-20). Cihaz token'ı kullanıcı oturum token'ı **değildir**: tasarım Kerem ile konuşulur. Silme hedefi |

### 5.4 Nisa: community, content, event

| Servis | Auth'tan beklenen | Yapılacaklar |
| --- | --- | --- |
| **community-service** | `sub`; moderasyon kararları `admin-service`'ten | Gönderi/yorum yalnız sahibi düzenler-siler (COM-03/08). Silme hedefi |
| **content-service** | Yayın yetkisi: `ADMIN` veya `CONTENT_CREATOR` (CONT-05/17); kaldırma: `MODERATOR` | Normal `PLAYER` haber yayımlayamaz. Rol kontrolü **sunucuda**. Silme hedefi |
| **event-service** | `sub` | Katılım/ceza kaydı `userId` ile; mükerrer katılım DB kısıtıyla. Silme hedefi |

### 5.5 Umut: social, messaging, lfp

| Servis | Auth'tan beklenen | Yapılacaklar |
| --- | --- | --- |
| **social-service** | `sub` | Arkadaşlık, davet kodu, engelleme `userId` ile. Silme hedefi |
| **messaging-service** | `sub` | Sohbet erişimi: yalnız üyeler (MSG-14). Engelleme social'dan (MSG-15). Silme hedefi |
| **lfp-service** | `sub` | İlanı yalnız sahibi günceller (LFP-18). Silme hedefi |

### 5.6 Ortak: admin, notification

| Servis | Notlar |
| --- | --- |
| **admin-service** | Auth'un `/api/v1/auth/admin/**` uçlarını **kullanıcı token'ıyla** çağırır (yalnız `ADMIN`). Servis kimlikli iç uç D4 gelince (#4). Moderasyon kararları ilgili servislere gider. Denetim kaydında `accountId` tutuyorsa silme listesi kararı (#6) |
| **notification-service** | Silme hedefi. E-posta bildirimi Mail Service'ten, uygulama içi bildirim bu serviste kalır |

### 5.7 Frontend

`apiRequest` + `credentials: "include"` doğru. EKSİK: `X-XSRF-TOKEN` (§3), `features/auth` ekranları (giriş, kayıt, 2FA, `/auth/callback`, parola sıfırlama `#token=…`, hesap silme). Token'ı `localStorage`/`sessionStorage`'a **yazmayın** (SECURITY.md §3).

---

## 6. İletişim kuralları (kim kime, nasıl yazar?)

| Durum | Ne yapılır |
| --- | --- |
| Yeni **rol** gerekiyor | Kerem'e iletin. Rol eklemek Auth'ta migration (V65+) ve `docs/api.md` güncellemesidir; tek başınıza rol uydurmayın |
| Auth **claim**/endpoint değişikliği isteği | Önce `docs-auth/servis-sozlesmeleri.md` + `docs/api.md` AUTH kaydı değişir, sonra kod. Kerem onaylar |
| Başka servisin API'si | `docs/api.md` içindeki o servisin bölümü okunur; değişiklik sahibine bildirilir |
| Silme ucu hazır | Kerem'e bildirin: Auth ayarına eklenir, görev kuyruğu otomatik çalışır |
| Servis kimliği (D4) | Ortak Security güncellemesiyle birlikte kararlaştırılır; o zamana kadar iç uçları Gateway'e açmayın |
| ENV anahtarı ekleme/değiştirme | Onay ister (`docs/SECURITY.md` §1); önce `.env.example` önerisi |
| Migration | Yalnız kendi aralığınız; uygulanmış dosya değişmez (`docs/decisions/migrations.md`). Auth: V61–V64 donuk, yeni V65+ |
| Şüphe | "Bu bilgiyi token'dan mı alıyorum?" Cevap hayırsa durun ve sorun |

---

## 7. "Claude'a yapıştır" blokları

Her blok **o kişinin Claude'una bir kez** verilir: servis kökündeki `CLAUDE.md`'ye eklenir ya da Claude'a *"şu kuralları hafızana kaydet"* diye yapıştırılır.
Teknik ayrıntı için bloklar `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` dosyasına işaret eder.

### 7.1 Ortak blok (herkes)

```markdown
## Auth / Gateway entegrasyon kuralları (LTZ)
- Kimlik doğrulama: Auth Service JWT (RS256) üretir. Servisim token'ı JWKS ile KENDİSİ doğrular:
  GET <AUTH_SERVICE_URL>/api/v1/auth/.well-known/jwks.json; yalnız RS256; iss=ltz-auth; aud içinde ltz-api; JWT başlığı typ=at+jwt; exp zorunlu.
- userId yalnız `sub` claim'inden alınır. Gövde/sorgu/`X-User-Id`/`X-Role` başlığına ASLA güvenme. Gateway bu başlıkları zaten siler.
- Roller `roles` claim'inde (öneksiz): PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN. Başka rol uydurma. Spring'de ROLE_ öneki ile eşle.
- Rol ≠ sahiplik. Kayıt sahipliğini kendi DB'mde `owner_id == sub` ile kontrol et (IDOR/BOLA). Başkasının kaydı için 404 veya 403 (tutarlı).
- Varsayılan reddet. 401 = kimlik yok/geçersiz, 403 = yetki yok. Hata gövdesi ProblemDetail; token/SQL/stack trace yok.
- Tarayıcı çerezle gelir; Gateway çerezi `Authorization: Bearer` yapar ve çerezli POST/PUT/PATCH/DELETE'de CSRF+Origin doğrular. Ben yalnız Bearer görürüm.
- Hesap askıya alınsa bile token en fazla 5 dk geçerli görünür (D3 yok). Buna göre tasarla; durum claim'i yok, `AccountStatusChanged` olayı yok, uydurma.
- Hesap silme: Auth `DELETE /internal/v1/accounts/{accountId}` çağırır (idempotent, 2xx = tamamlandı). Servis kimliği (D4) hazır olana kadar bu ucu Gateway'e açma.
- Auth DB'sine ve başka servisin DB'sine doğrudan erişme. Sır/token/parola loglama, koda veya belgeye yazma.
- Olmayan şeyi varsayma: Mail Service, servis kimliği (D4), cihaz kimliği, JWT claim'lerinde e-posta/kullanıcı adı/durum YOK. Önce docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md'ye bak.
- Yeni rol/claim/endpoint ihtiyacı → Kerem'e iletilir; docs-auth/servis-sozlesmeleri.md ve docs/api.md önce güncellenir.
- Test: token yok/bozuk/süresi dolmuş → 401; alg=none ve HS256 → 401; yanlış iss/aud/typ → 401; yetersiz rol → 403; başkasının kaydı → 404/403; sahte X-User-Id yok sayılır.
```

### 7.2 Hamza (game, library, review)

```markdown
## Hamza'ya özel Auth notları
- game: GAME-07 yönetim endpoint'leri hasRole('ADMIN') (gerekirse ADMIN + GAME_DEVELOPER). Katalog okuma herkese açıksa açıkça permitAll yaz.
- game: GAME-12 geliştirici sahipliği (hangi oyun hangi geliştiricinin) KENDİ DB'mde; Auth oyun bazlı yetki taşımaz. GAME_DEVELOPER yalnız "geliştirici hesabı" demektir.
- game: Auth silme hedef listesinde `game` YOK. accountId tutan veri varsa (sahiplik, audit) Kerem'e bildir, listeye eklenir.
- library: userId = sub; istek gövdesinde userId alanı tanımlama; (userId, gameId) benzersiz; gameId'yi Game Service ile doğrula. Silme hedefi: DELETE /internal/v1/accounts/{id} yaz.
- review: başkası adına inceleme yok; düzenle/sil owner == sub; moderatör silmesi hasAnyRole('MODERATOR','ADMIN') + audit. Silme hedefi; içerik silme ile puan silme kuralını belirle.
- Game bekleyen task notu: "Auth hazır olunca yalnız yapılandırma ve claim dönüştürücü değişir" → jwks-uri/issuer/audience yapılandırması + `roles` claim'ini ROLE_ yetkilerine çeviren converter; gerçek Auth token'ıyla entegrasyon testi yaz.
```

### 7.3 Alper (hardware, recommendation, performance)

```markdown
## Alper'e özel Auth notları
- hardware: kendi donanım profilini silme/düzenleme owner == sub. Yorum moderasyonu admin-service'e gider (HW-13). Silme hedefi: DELETE /internal/v1/accounts/{id}.
- recommendation: kullanıcıya özel veri için sub; silme hedefi.
- performance: PERF-01 masaüstü cihaz eşleştirme için Auth'ta cihaz kimliği YOK ve SECURITY.md cihaz token'ını kullanıcı oturum token'ından ayırır. Kullanıcı JWT'sini cihaz kimliği gibi kullanma; tasarımı Kerem'le konuş.
- performance: PERF-20 / PWEB-10 kullanıcı verisi silme + hesap silme çağrısı (silme hedefi).
```

### 7.4 Nisa (community, content, event)

```markdown
## Nisa'ya özel Auth notları
- community: gönderi/yorum yalnız sahibi düzenler-siler (owner == sub); moderasyon kararları admin-service'ten gelir. Silme hedefi.
- content: yayımlama yetkisi ADMIN veya CONTENT_CREATOR rolü (CONT-05/17), yayından kaldırma MODERATOR/ADMIN. Normal PLAYER yayımlayamaz; kontrol sunucuda. Silme hedefi.
- event: katılım/ceza kayıtları userId = sub ile; mükerrer katılım DB benzersizlik kısıtıyla. Silme hedefi.
```

### 7.5 Umut (social, messaging, lfp)

```markdown
## Umut'a özel Auth notları
- social: arkadaşlık/davet/engelleme userId = sub ile; başka kullanıcı adına işlem yok. Silme hedefi.
- messaging: sohbet erişimi yalnız üyelere (MSG-14), engelleme social-service'ten (MSG-15); userId = sub. Silme hedefi.
- lfp: ilanı yalnız sahibi günceller/siler (owner == sub). Silme hedefi.
```

### 7.6 Kerem (auth, user, mail) ve ortak servisler

```markdown
## Kerem'e özel notlar
- auth: Migration V61–V64 DONUK, yeni şema V65+. Yeni rol/claim/endpoint önce docs-auth/servis-sozlesmeleri.md + docs/api.md'de.
- auth: prod profili Mail Service adaptörü gelene kadar açılmaz (bilinçli). Prod anahtarları yeni üretilir (openssl genrsa 3072 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0).
- user: parola/refresh token tutma; UserRegistered olayı henüz yok (PLANNED). Steam/Discord bağlantısı user-service işi, Steam kimlik kanıtını sen üretme.
- mail: 3 port sözleşmesi (doğrulama kodu, sıfırlama, silme onayı) + MAIL-03; token üretme/loglama yok.
- admin: Auth admin uçları kullanıcı token'ı + ADMIN rolüyle; servis kimliği (D4) gelince iç uca geçilir.
```

### 7.7 Frontend sahibi

```markdown
## Frontend Auth notları
- Her istek `credentials: "include"`. Token'ı localStorage/sessionStorage/IndexedDB'ye YAZMA.
- Durum değiştiren her istekte `X-XSRF-TOKEN` = `__Host-XSRF-TOKEN` (yoksa `XSRF-TOKEN`) çerez değeri. Çerez yoksa önce GET /api/v1/auth/csrf.
- 401 → bir kez POST /api/v1/auth/session/refresh, başarılıysa isteği tekrarla; değilse giriş sayfası. 403 CSRF_TOKEN_INVALID → csrf çerezini yenile.
- OAuth sonucu FRONTEND_PUBLIC_URL/auth/callback#status=... fragment'iyle gelir (api.md AUTH). Parola sıfırlama bağlantısı #token=... fragment'i taşır; token'ı reset gövdesinde gönder.
- Hata gövdesi ProblemDetail: 401 UNAUTHENTICATED, 403 FORBIDDEN, 429 + Retry-After.
```

---

## 8. Sık yaşanan sorunlar

| Belirti | Sebep | Çözüm |
| --- | --- | --- |
| Auth'a POST atınca `403 CSRF_TOKEN_INVALID` | `X-XSRF-TOKEN` başlığı yok / çerezle eşleşmiyor | §3, önce `GET /api/v1/auth/csrf` |
| Tarayıcıda CORS hatası | Origin `CORS_ALLOWED_ORIGINS` listesinde değil | `.env`'de listeye ekleyin (tam origin, `*` yok) |
| Servisim 401 veriyor ama giriş yapılmış | Gateway'den değil doğrudan çağırıyorsunuz (çerez Bearer'a çevrilmedi) | Gateway üzerinden çağırın veya `Authorization: Bearer` verin |
| Token geçerli ama `aud` hatası | `JWT_AUDIENCE` farklı | Auth ile aynı değer (`ltz-api`) |
| Auth `prod` profilinde açılmıyor | Mail Service adaptörü yok (bilinçli) | Bekleyen iş #1 |
| Çok istekte `429` | Gateway Auth hız sınırı | `Retry-After` kadar bekleyin; dev'de `GATEWAY_AUTH_RATE_LIMIT_PER_MINUTE` artırılır |
| Eski token'lar bir anda geçersiz | Auth JWT anahtarı değişti | Yeniden giriş yapın |
