# Auth Servis Sınırları ve Servisler Arası Sözleşmeler (AUTH-17)

> Tarih: 2026-10-23 · Durum: **sözleşmeler hazır; Auth tarafı D4 dahil uygulandı, çoğu karşı servis geliştirilmediği için PENDING.** Hiçbir entegrasyon
> "tamamlandı" sayılmaz; aşağıdaki tablo gerçek durumu gösterir.

## 1. Sahiplik ve sınırlar

| Servis | Sahip olduğu | Auth'tan beklentisi | Yasak |
| --- | --- | --- | --- |
| **auth** | parola, token, kimlik doğrulama, roller, hesap durumu, harici kimlik (Steam ID / Google sub), 2FA | — | Profil verisi tutmak, e-posta göndermek |
| **user** | profil, tercihler, bağlı hesap görünümü | `UserRegistered` (ACTIVE sonrası), `AccountDeleted` silme çağrısı | Auth DB'sine erişmek, parola/refresh token tutmak, Steam kimlik kanıtını kendi başına üretmek |
| **mail** | e-posta teslimi, SMTP, kuyruk | 3 port: doğrulama kodu, parola sıfırlama, hesap silme onayı | Doğrulama/sıfırlama token'ı üretmek, token'ı kalıcı düz metin tutmak veya loglamak |
| **admin** | yönetim orkestrasyonu, denetim | Auth yönetici uçları (rol/durum) | Auth DB'sine erişmek, durumu kendi DB'sinde değiştirmek |
| diğer servisler | kendi alan verisi | JWT doğrulaması (JWKS), silme çağrısı | Auth DB'sine erişmek |

Hiçbir servis Auth veritabanına doğrudan erişmez; yalnız yukarıdaki sözleşmeler kullanılır. Auth, diğer servislerin
veritabanına dokunmaz.

## 2. Sözleşmeler ve gerçek durum

| # | Sözleşme | Yön | Durum |
| --- | --- | --- | --- |
| 1 | JWT doğrulama: JWKS `GET /api/v1/auth/.well-known/jwks.json`; `iss`, `aud=ltz-api`, `typ=at+jwt`, RS256, `exp` | diğer servisler → Auth | Auth tarafı **IMPLEMENTED**; tüketici durumu (2026-10-24): **Game, Social ve Hardware uygun**, Hardware uygun (2026-10-26), Community geçici (her token reddedilir), diğerleri iskelet (bkz. `ekip-uyum-denetimi-2026-10-24.md`) |
| 2 | Doğrulama kodu e-postası (`VerificationCodeMailPort`) | Auth → Mail | Port + dev (Mailpit) **IMPLEMENTED**; Mail Service ucu **PENDING** |
| 3 | Parola sıfırlama e-postası (`PasswordResetMailPort`) | Auth → Mail | Aynı: port + dev **IMPLEMENTED**; Mail Service **PENDING** |
| 4 | Hesap silme onayı e-postası (`AccountDeletionMailPort`) | Auth → Mail | Aynı: port + dev **IMPLEMENTED**; Mail Service **PENDING** |
| 5 | `UserRegistered` (hesap ACTIVE olunca) | Auth → User | **PLANNED**; outbox altyapısı ve User Service yok |
| 6 | Hesap verisi silme `DELETE /internal/v1/accounts/{id}` (`AccountDataEraserPort`) | Auth → her veri sahibi servis | Auth tarafı (görev, geri çekilme, tamamlama, gerçek HTTP adaptörü + D4 servis token'ı) **IMPLEMENTED**; hedef uçlar: **Social, Game, Library, Review, Hardware hazır (main'de)**, kalan 11 servis **PENDING**; bekleme süresi 14 gün (`grace-period`), süre bitince Auth PII'si hemen anonimleşir → onaylamayan hedefin görevi PENDING kalır |
| 7 | Yönetici durum/rol değişikliği | Admin → Auth | Auth tarafı **IMPLEMENTED (2026-10-24)**: servis kimlikli iç uçlar (`POST /internal/v1/service-tokens` client credentials, `account:read|status:write|role:write` kapsamları, `aud=auth-service`); **admin-service tüketicisi yok → uçtan uca PENDING** (§3.4). Şimdilik kullanıcı token'lı `/api/v1/auth/admin/**` de çalışır |
| 8 | Durum değişikliği yayılımı (`AccountStatusChanged`) | Auth → servisler | **PENDING (D3)**; şimdilik en fazla access token ömrü (varsayılan 60 sn) gecikme |
| 9 | Gateway: çerez→Bearer + CSRF/Origin doğrulaması, CORS allowlist, kimlik başlığı temizliği, Auth hız sınırı | tarayıcı → Gateway → servisler | **IMPLEMENTED** (`api-gateway`); güvenilir proxy IP'si ayarlanabilir, ortak (çok örnekli) limitleyici **PENDING** |

## 3. Servis kimliği (D4) — sözleşme

> **Auth tarafı IMPLEMENTED (2026-10-23):** `ServiceTokenIssuer` (üretici) + `HttpAccountDataEraser` (silme çağrısı).
> Sözleşmenin sahibi Auth'tur; hedef servisler bu kurallara uyar, Auth hiçbir servise göre gevşetilmez.

Auth bir hedef servisi çağırırken **her çağrı için yeni**, kısa ömürlü bir JWT imzalar. Kurallar Auth kodunda sabittir; ENV ile
gevşetilemez. Taşıma: `Authorization: Bearer <token>`. İmza, kullanıcı token'larıyla aynı RS256 anahtarıdır (aynı JWKS).

| Alan | Auth'un ürettiği değer | Hedef servisin denetimi |
| --- | --- | --- |
| `typ` (başlık) | `svc+jwt` | **ZORUNLU** tam eşleşme; `at+jwt` (kullanıcı token'ı) iç uçta reddedilir |
| `alg`, `kid` | `RS256`, JWKS'teki anahtar | **ZORUNLU** yalnız RS256; imza JWKS ile |
| `iss` | `ltz-auth` (`JWT_ISSUER`) | **ZORUNLU** |
| `aud` | **tek değer** `<servis>-service` (ör. `social-service`, `game-service`; Compose servis ve modül adıyla aynı) | **ZORUNLU** kendi adın; `ltz-api` iç uçta kabul edilmez |
| `sub` | `svc:auth-service` | **ZORUNLU** silme ucunda **tam eşleşme** (yalnız `svc:` öneki yetmez: silme yetkisi yalnız Auth'tadır) |
| `scope` | `account:erase` | **ZORUNLU** ucun gerektirdiği kapsam (`SCOPE_account:erase`) |
| `account_id` | silinecek hesap (UUID) | **ZORUNLU** istek yolundaki `{accountId}` ile **eşit**; değilse `403` (çalınan token başka hesabı silemez) |
| `iat`, `nbf`, `exp` | ömür **30 sn** | **ZORUNLU** `exp - iat ≤ 60 sn`, saat sapması ≤ 30 sn |
| `jti` | her çağrıda yeni UUID | **ZORUNLU** var olmalı; tekrar oynatma kaydı önerilir (silme idempotent olduğundan zorunlu değil) |
| `roles`, `sid`, `ver`, `amr` | **taşınmaz** | Servis token'ından kullanıcı yetkisi türetilmez |

Auth'un kendi kullanıcı token doğrulayıcısı `svc+jwt`'yi reddeder; kullanıcı token'ı bekleyen her servis de reddetmelidir (`typ` ve
`aud` farklıdır). Kanıt: `ServiceTokenIssuerTest`, `AccountDeletionServiceErasureTest`; Social'ın gerçek doğrulayıcısıyla uyumluluk
2026-10-23'te denendi (kabul; `game-service` aud'u, kullanıcı token'ı olarak ve süre dolunca ret).

### 3.1 Hesap verisi silme ucu (her hedef servis)

`DELETE /internal/v1/accounts/{accountId}` — Auth bunu `<SERVİS>_SERVICE_URL` üzerinden doğrudan çağırır.

- Yalnız §3'teki servis token'ıyla çağrılabilir; **kimliksiz açılmaz**. `/internal/**` Gateway'e açılmaz (Gateway yalnız
  `/api/v1/<servis>/**` yönlendirir ve önek kırpmaz).
- Kişisel veriyi silmeyi/anonimleştirmeyi **bitirdiğinde** `204` (veya `200`) döner. Zaten silinmişse veya o hesaba ait veri yoksa
  da `204`. İdempotenttir: ikinci çağrı da `204`.
- **Onay sayılmayanlar:** `202 Accepted` ("sonra silerim" kabul edilmez), `3xx` (Auth yönlendirme izlemez; token başka adrese
  gitmez), `404` (uç yok/yanlış adres), `401`/`403`, `5xx`, 5 sn zaman aşımı, bağlantı hatası. Bu durumlarda görev `PENDING` kalır
  ve üstel geri çekilmeyle (1 dk → en çok 6 sa) yeniden denenir; hata kodu `last_error`'da görünür (ör. `SERVICE_ERASURE_HTTP_404`).
- Saklama istisnası (ör. append-only denetim kaydı) yalnız hukuki gerekçeyle ve kişiyi tanımlamayan biçimde olabilir; kararı servis
  sahibi ve Auth sahibi birlikte verir, servis belgesine yazılır. İstisna, `204`'ün "kişisel veri kalmadı" anlamını değiştirmez.

### 3.2 Hedef listesi (S2 kararı, 2026-10-23)

`ltz.auth.deletion.targets` = `user, mail, game, library, review, social, messaging, lfp, community, content, event, hardware,
recommendation, performance, admin, notification` (16). Her hedefin iç adresi `ltz.auth.deletion.service-urls.<servis>`
(`<SERVİS>_SERVICE_URL`, Gateway ile aynı değişken); adresi eksik ya da şema + host (+ port) dışında yol, kimlik bilgisi veya sorgu
içeren hedefte Auth **açılmaz**.

- `game` (kullanıcı REF'leri: editör, geliştirici yetkisi, içerik yazarı vb.) ve `admin` (denetim kaydı) listeye eklendi.
- Auth "uygulanamaz" beyanını **kendi tarafında kabul etmez**: verisi olmayan servis de ucu açıp `204` döner. Bir servisi listeden
  çıkarmak yalnız Auth sahibinin kararıdır.
- Admin → Auth yönündeki servis kimlikli iç uç (rol/durum) Auth tarafında **hazır** (§3.4); tüketici admin-service henüz yok.

### 3.3 Servis sahipleri için

Diğer servis sahiplerinin yapması gerekenler (JWT doğrulama ayrıntısı, roller, sahiplik, 401/403, iptal gecikmesi, silme ucu,
Game/Library/Review örnekleri, test listesi) tek yerde toplanmıştır:
[docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md](../docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md). Bu belge sözleşmenin kaynağıdır; rehber onu
tekrar tanımlamaz.

### 3.4 Servis → Auth çağrıları (client credentials) — admin-service için

> **Auth tarafı IMPLEMENTED (2026-10-24).** Tüketici admin-service henüz yok; sözleşme onun yazılmasından önce sabitlendi.

Auth'a iç çağrı yapacak servis, **kendi gizli anahtarıyla** Auth'tan kısa ömürlü bir `svc+jwt` alır; imzalayan yalnız Auth'tur (diğer servislerde anahtar
dağıtımı gerekmez). Akış:

1. `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` — `{ "clientId": "admin-service", "clientSecret": "<AUTH_ADMIN_SERVICE_CLIENT_SECRET>" }` → `{ accessToken, tokenType, expiresIn: 60, scope }`.
2. `Authorization: Bearer <accessToken>` ile iç uçlar: `GET /internal/v1/accounts/{id}`, `POST …/status`, `POST …/roles/grant|revoke` (gövdede `actorId`, `reason`).
3. Token 60 sn'dir; süresi dolmadan yenilenir. Önbelleğe uzun süre alınmaz.

| Alan | Değer | Auth'un denetimi |
| --- | --- | --- |
| `typ` (başlık) | `svc+jwt` | **ZORUNLU**; kullanıcı token'ı (`at+jwt`) bu uçlarda geçmez |
| `alg`, `kid` | `RS256`, Auth'un güncel anahtarı | **ZORUNLU** |
| `iss` | `ltz-auth` | **ZORUNLU** |
| `aud` | **yalnız** `auth-service` | **ZORUNLU**; `ltz-api` ve `<servis>-service` token'ları reddedilir |
| `sub` | `svc:<istemci>` (ör. `svc:admin-service`) | **ZORUNLU**: kayıtlı ve etkin istemci; `svc:auth-service` istemci olamaz |
| `scope` | `account:read`, `account:status:write`, `account:role:write` | **ZORUNLU**: uç kapsamı ister; kapsam istemcinin kayıtlı kapsamlarını aşamaz |
| `iat`, `nbf`, `exp`, `jti` | ömür 60 sn | **ZORUNLU**: `exp - iat ≤ 60 sn`, bitiş şimdiden ≤ 60 sn ileride, `iat` çıkarılarak aşılamaz |
| `roles`, `sid`, `ver`, `amr` | taşınmaz | Servis token'ından kullanıcı yetkisi türetilmez |

**Kullanıcı kimliği ayrı doğrulanır:** servis token'ı yönetici yetkisi taşımaz. Gövdedeki `actorId` Auth'ta yeniden denetlenir (hesap var, `ACTIVE`,
`ADMIN` rolünde); değilse `403 ACTING_ADMIN_INVALID`. İş kuralları (kendini değiştirme yasağı, durum geçişleri, ADMIN/MODERATOR için TOTP,
oturum iptali, denetim kaydı) kullanıcı uçlarıyla aynıdır; **Auth hiçbir kuralı servis çağrısı için gevşetmez.**

**Gizli anahtar:** `AUTH_ADMIN_SERVICE_CLIENT_SECRET`; en az 32 karakter, yerelde `node ops/generate-auth-dev-keys.mjs` üretir. **Boşsa istemci devre
dışıdır** (fail-closed). Zayıf anahtar veya bilinmeyen kapsam Auth'u açtırmaz. Prod için ayrı üretilir ve sır yöneticisinde tutulur. Admin servisinin
ortamına aynı değer verilir. Hesap kilidi yoktur (iç ağdaki biri gerçek servisi kilitleyemesin); yanıt tek hata verir (`401 INVALID_CLIENT`).

**Admin servisine düşenler:** (1) yukarıdaki iki adımı bir HTTP istemcisiyle yapmak, (2) kendi kullanıcısının ADMIN olduğunu kendi token doğrulamasıyla
bilip `actorId` olarak göndermek, (3) gizli anahtarı koda/log'a yazmamak. Başka bir şey gerekmez.

### 3.5 Servisler arası olay girişi (`events:ingest`) — hedefe bağlı servis token'ı

> **Auth tarafı kodlandı (2026-10-28), main'e PR ile girer.** Alıcı uçlar (ör. Community `POST /internal/events`) alıcı servislerin işidir.

Servis, aynı token ucundan (`POST /internal/v1/service-tokens`) gövdeye `audience` ekleyerek **başka bir servise** gidecek token alır. Kimseye varsayılan
imtiyaz yoktur: her istemci yalnız kendisine tanımlı (hedef, kapsam) çiftleri için token alır, listede olmayan çağrı `403 SERVICE_AUDIENCE_FORBIDDEN`
olur (kimlik doğrulaması önce yapılır; yanlış gizli anahtar yine `401 INVALID_CLIENT`).

| # | Çağıran (`sub`) | Hedef (`aud`) | Kapsam | Neden |
| --- | --- | --- | --- | --- |
| 1 | `svc:admin-service` | `community-service` | `events:ingest` | Moderasyon kararını (`ModerationActionTaken`) içeriğe uygulatmak |
| 2 | `svc:social-service` | `community-service` | `events:ingest` | Engelleme yansıması (`UserBlocked`, `UserUnblocked`) |
| 3 | `svc:community-service` | `admin-service` | `events:ingest` | Rapor olayı (`ContentReported`) admin kuyruğuna |
| 4 | `svc:community-service` | `notification-service` | `events:ingest` | Etkileşim bildirimleri |
| 5 | `svc:messaging-service` | `social-service` | `blocks:check` | Mesaj öncesi engel sorgusu |
| 6 | `svc:lfp-service` | `social-service` | `blocks:check` | Eşleşme öncesi engel sorgusu |
| 7 | `svc:social-service` | `user-service` | `privacy:read`, `events:ingest` | SOC-12 arkadaş listesi görünürlüğü; `FriendshipChanged`, `UserBlocked`, `UserUnblocked` |
| 8 | `svc:library-service` | `user-service` | `privacy:read`, `events:ingest` | LIB-11 koleksiyon gizliliği; `LibrarySummaryChanged` |
| 9 | `svc:hardware-service` | `user-service` | `privacy:read` | HW-08 `HardwareVisibilityPolicy` |
| 10 | `svc:review-service` | `user-service` | `events:ingest` | `ReviewSummaryChanged` |
| 11 | `svc:recommendation-service` | `library-service` | `signals:read` (**hesaba bağlı**) | Tek hesabın öneri sinyali (LIB-14) |

Satır 5 ve 6 için gizli anahtarlar `AUTH_MESSAGING_SERVICE_CLIENT_SECRET` ve `AUTH_LFP_SERVICE_CLIENT_SECRET` (yeni, boşsa istemci kapalı).
Satır 7–11 için (2026-10-30, boşsa kapalı): social satır 2'deki `AUTH_SOCIAL_SERVICE_CLIENT_SECRET`'i kullanır; ayrıca `AUTH_LIBRARY_SERVICE_CLIENT_SECRET`,
`AUTH_HARDWARE_SERVICE_CLIENT_SECRET`, `AUTH_REVIEW_SERVICE_CLIENT_SECRET`, `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET`.
**User Service istemci değildir:** kimseyi senkron çağırmaz, Social/Library/Review verisi olayla gelir. `privacy:read` kullanıcıya bağlı değildir;
görüntüleyen ve sahip istek gövdesindedir, çağıranın kendi doğruladığı beyandır. Kararı User verir, Auth vermez.

**Hesaba bağlı kapsam (2026-10-30, `signals:read`):** İstek gövdesinde `accountId` zorunludur; token `account_id` claim'iyle o tek hesaba bağlanır.
Auth hesabın var ve `ACTIVE` olduğunu denetler (değilse `403 SERVICE_TARGET_ACCOUNT_UNAVAILABLE`). Hesaba bağlı olmayan kapsamda `accountId` verilemez,
bağlı kapsamda eksik olamaz (`400 SERVICE_ACCOUNT_BINDING_INVALID`). Bir hedefin kapsamları ya hep hesaba bağlıdır ya hiç (açılış denetimi).
**Sınır:** `account_id` çağıranın beyanıdır; Auth kullanıcı iznini ve hedefin meşruiyetini kanıtlamaz. Bağ, çalınan token'ı tek hesapla ve
60 sn ile sınırlar. İzin kararı hedef servisindir (Library her istekte User'a sorar, kararsızlıkta veri açmaz).

**Kullanıcı adına çağrı (LIB-10, 2026-10-30 kararı):** Auth, görüntüleyen kullanıcıyı taşıyan delegasyon/exchange token'ı **üretmez** (servis
token'ından kullanıcı yetkisi türetilmez). Kullanıcı isteğiyle gelen çağrıda servis, o kullanıcının kendi `at+jwt`'sini hedefe iletir; hedef
`viewer`'ı yalnız `sub`'dan okur. İletilen token saklanmaz, loglanmaz, arka plan işinde kullanılmaz.

**Auth olayları (outbox, 2026-10-29):** `AccountStatusChanged`, `UserRegistered`, `AccountEmailChanged` aynı işlemde `auth_events` tablosuna yazılır; arka plan işçisi hedeflere `eventId/eventType/eventVersion/occurredAt/producer/aggregateId/payload` zarfıyla HTTP ile iletir (en az bir kez; alıcı `eventId` ile tekilleştirir). `AccountStatusChanged` yalnız yönetici durum değişikliğinde çıkar; silme isteği, iptal ve silme tamamlanması olay üretmez (bunlar silme koordinasyonuyla `account:erase` çağrısı üzerinden yürür). Hedef listesi `ltz.auth.events.targets` (ortam değişkeni `AUTH_EVENT_TARGETS`, virgülle ayrılmış servis adları), varsayılan **boş** (olay gönderilmez); prod'da hedef adresleri `https` olmalı.

**Cihaz token'ı (2026-10-29):** `typ=dev+jwt`, `aud=hardware-service`, `sub=<deviceId>`, `scope=hardware:sync:write`; uç sözleşmesi `docs/api.md`.

Token: `typ=svc+jwt`, RS256 (JWKS aynı), `iss=ltz-auth`, **`aud=<hedef>`** (yalnız o serviste geçer, Auth'un iç uçlarında geçmez), `sub=svc:<istemci>`,
`scope=events:ingest`, 60 sn. Alıcı doğrulaması alıcıya aittir: `aud` kendi adı, `typ`, `scope`, ayrıca olay türü ↔ `sub` eşleşmesi (örn.
`ModerationActionTaken` yalnız `svc:admin-service` ile). Gizli anahtarlar: `AUTH_ADMIN_SERVICE_CLIENT_SECRET`, `AUTH_SOCIAL_SERVICE_CLIENT_SECRET`,
`AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` (her biri ayrı, en az 32 karakter, boşsa istemci kapalı). Yeni (çağıran, hedef) çifti eklemek Auth'ta
yetkili bir değişikliktir (`application.properties` + test + bu tablo). Satır 5 (Auth → community, `AccountStatusChanged`) olay tanımı gelince.

## 4. Silme koordinasyonunun özeti

`DELETION_REQUESTED` → her servis için görev (`account_deletion_tasks`) → worker görevi kiralar (SKIP LOCKED) → çağrı işlem
dışında, her çağrıda yeni servis token'ıyla → `200`/`204` ise `CONFIRMED`, aksi halde üstel geri çekilme → hepsi `CONFIRMED` olunca
tek işlemde Auth'taki hesap kaydı, güvenlik olayları, silme görevleri ve silme talebi **tamamen silinir** (proje sahibi kararı 2026-10-28:
silinen kullanıcının kaydı tutulmaz; kimlik içermeyen tek iz bir günlük satırıdır). Kısmi başarısızlıkta hesap `DELETION_REQUESTED` kalır
(kimlik bekleme süresi bitince anonim), kayıtlar silme tamamlanana kadar durur ve hiçbir yerde "silindi" denmez. Hedef listesi §3.2'dedir; yeni veri sahibi servis eklenince listeye girer.
