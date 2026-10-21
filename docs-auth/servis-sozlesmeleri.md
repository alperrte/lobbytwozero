# Auth Servis Sınırları ve Servisler Arası Sözleşmeler (AUTH-17)

> Tarih: 2026-10-21 · Durum: **sözleşmeler hazır, çoğu karşı servis geliştirilmediği için PENDING.** Hiçbir entegrasyon
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
| 6 | Hesap verisi silme `DELETE /internal/v1/accounts/{id}` (`AccountDataEraserPort`) | Auth → her veri sahibi servis | Auth tarafı (görev, geri çekilme, tamamlama) **IMPLEMENTED**; hedef uçlar ve gerçek adaptör **PENDING** → görevler PENDING kalır |
| 7 | Yönetici durum/rol değişikliği | Admin → Auth | Şimdilik kullanıcı token'lı `/api/v1/auth/admin/**`; servis kimlikli `/internal` uç **PENDING (D4)** |
| 8 | Durum değişikliği yayılımı (`AccountStatusChanged`) | Auth → servisler | **PENDING (D3)**; şimdilik en fazla access token ömrü (5 dk) gecikme |

## 3. Servis kimliği (D4) — sözleşme

Auth'un imzaladığı kısa ömürlü JWT: `iss=ltz-auth`, `sub=svc:<çağıran>`, `aud=<tek hedef servis>`, `scope=<eylem>`
(ör. `account:erase`), `typ=svc+jwt` (kullanıcı token'ıyla karıştırılamaz), `exp ≤ 60 sn`, `jti`. Hedef servis imzayı JWKS ile,
`aud/scope/typ`'ı kendisi doğrular; kullanıcı cookie'sine veya `X-User-Id` benzeri başlıklara dayanmaz. İç uçlar
kimliksiz açılmaz. **Üretici kod yazılmadı** (kullanılmayan kod ve yakında gelecek repository geneli Security güncellemesiyle
çakışma riski nedeniyle); sözleşme onaylanınca eklenecek.

## 4. Silme koordinasyonunun özeti

`DELETION_REQUESTED` → her servis için görev (`account_deletion_tasks`) → worker görevi kiralar (SKIP LOCKED) → çağrı işlem
dışında → 2xx ise `CONFIRMED`, hata ise üstel geri çekilme → hepsi `CONFIRMED` olunca tek işlemde Auth verisi silinir ve hesap
anonimleşip `DELETED` olur. Kısmi başarısızlıkta hesap `DELETION_REQUESTED` kalır, kişisel veri korunur ve hiçbir yerde
"silindi" denmez. Hedef listesi `ltz.auth.deletion.targets` ile yönetilir; yeni veri sahibi servis eklenince listeye girer.
