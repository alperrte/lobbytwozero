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
| 1 | JWT doğrulama: JWKS `GET /api/v1/auth/.well-known/jwks.json`; `iss`, `aud=ltz-api`, `typ=at+jwt`, RS256, `exp` | diğer servisler → Auth | Auth tarafı **IMPLEMENTED**; tüketici servislerde doğrulama yok (Security güncellemesi bekleniyor) |
| 2 | Doğrulama kodu e-postası (`VerificationCodeMailPort`) | Auth → Mail | Port + dev (Mailpit) **IMPLEMENTED**; Mail Service ucu **PENDING** |
| 3 | Parola sıfırlama e-postası (`PasswordResetMailPort`) | Auth → Mail | Aynı: port + dev **IMPLEMENTED**; Mail Service **PENDING** |
| 4 | Hesap silme onayı e-postası (`AccountDeletionMailPort`) | Auth → Mail | Aynı: port + dev **IMPLEMENTED**; Mail Service **PENDING** |
| 5 | `UserRegistered` (hesap ACTIVE olunca) | Auth → User | **PLANNED**; outbox altyapısı ve User Service yok |
| 6 | Hesap verisi silme `DELETE /internal/v1/accounts/{id}` (`AccountDataEraserPort`) | Auth → her veri sahibi servis | Auth tarafı (görev, geri çekilme, tamamlama, gerçek HTTP adaptörü + D4 servis token'ı) **IMPLEMENTED**; hedef uçlar servis sahiplerinde **PENDING** (Social kendi dalında hazır) → onaylamayan hedefin görevi PENDING kalır |
| 7 | Yönetici durum/rol değişikliği | Admin → Auth | Şimdilik kullanıcı token'lı `/api/v1/auth/admin/**`; Auth'ta servis kimlikli `/internal` uç **PENDING** (§3.2) |
| 8 | Durum değişikliği yayılımı (`AccountStatusChanged`) | Auth → servisler | **PENDING (D3)**; şimdilik en fazla access token ömrü (5 dk) gecikme |
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
- Admin → Auth yönündeki servis kimlikli iç uç (rol/durum) **PENDING**: Auth'ta henüz `/internal` uç yoktur.

### 3.3 Servis sahipleri için

Diğer servis sahiplerinin yapması gerekenler (JWT doğrulama ayrıntısı, roller, sahiplik, 401/403, iptal gecikmesi, silme ucu,
Game/Library/Review örnekleri, test listesi) tek yerde toplanmıştır:
[docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md](../docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md). Bu belge sözleşmenin kaynağıdır; rehber onu
tekrar tanımlamaz.

## 4. Silme koordinasyonunun özeti

`DELETION_REQUESTED` → her servis için görev (`account_deletion_tasks`) → worker görevi kiralar (SKIP LOCKED) → çağrı işlem
dışında, her çağrıda yeni servis token'ıyla → `200`/`204` ise `CONFIRMED`, aksi halde üstel geri çekilme → hepsi `CONFIRMED` olunca
tek işlemde Auth verisi silinir ve hesap anonimleşip `DELETED` olur. Kısmi başarısızlıkta hesap `DELETION_REQUESTED` kalır, kişisel
veri korunur ve hiçbir yerde "silindi" denmez. Hedef listesi §3.2'dedir; yeni veri sahibi servis eklenince listeye girer.
