# Auth cevabı: hesap türleri, silme, e-posta, bağlantılar (2026-11-10)

Soru belgesi: `AUTH-SORULAR.md` (User/Frontend). Cevaplar **Auth kodu okunarak** verildi (`main` @ `e15b3fab`); canlı çalıştırılarak doğrulanmadı.
Etiketler: **Var / Yok / Kısmen**. "Doğrulanmadı" = kodda net göremedim.

## Özet

- Auth bugün **A/B/C ayrımı bilmiyor.** Hesap türü, kayıt yöntemi ve "güvenli hesap" bayrağı yok.
- Silme ve e-posta değiştirme kuralları **parolaya ve 2FA'ya** bağlı, hesap türüne değil. Sizin kurallarınız (B/C silemez, C e-posta değiştiremez) şu an **uygulanmıyor.**
- Kısıtlı işlemlerin sunucuda zorlanması (7.x) **yok**; bu güvenlik açığı olarak ele alınmalı.
- Bunlar ürün kararı + Auth geliştirmesi gerektirir; aşağıda her biri işaretli.

## 1. Hesap türünü ayırt etme

| # | Cevap | Not |
|---|---|---|
| 1.1 | **Yok** | `GET /me` = `{accountId, roles, authMethods, accessTokenExpiresAt}` (`CurrentAccountResponse`). Parola var/yok alanı yok. |
| 1.2 | **Yok** | Bağlı sağlayıcı listesi dönmüyor. `authMethods` hesabın değil, **o oturumun** giriş yöntemleridir (token claim'i). |
| 1.3 | **Yok** | TOTP açık bilgisi `/me`'de yok (yalnız oturumda ikinci faktör kullanıldıysa `authMethods`'a yansır). |
| 1.4 | **Yok** | Kayıt yöntemi saklanmıyor. `Account` tablosunda böyle bir alan yok (üç ayrı üretici metot var: normal, Google, Steam; sonuç kaydedilmiyor). Doğrudan Google ile kayıt olan ile sonradan bağlayanı ayırt etmek bugün mümkün değil. |
| 1.5 | **Yok** (kavram yok) | Tür olmadığı için "kalır" diyemiyorum. Parola belirleme hesabı değiştirmiyor ama türü de tutulmuyor. |
| 1.6 | **Yok** | `accountTier` yok. |

Öneri: kayıt yöntemini bir migration ile kalıcı alan yapmak (`registration_method`), `/me`'ye `passwordSet`, `providers[]`, `totpEnabled`, `registrationMethod` eklemek. Mevcut hesaplar için geriye dönük değer belirsiz (karar gerekir).

## 2. Hesap silme

| # | Cevap | Not |
|---|---|---|
| 2.1 | **Var** | `POST /api/v1/auth/account/deletion/request`: parola (+ TOTP açıksa ikinci faktör) ister. |
| 2.2 | **Yok** | B hesabı reddedilmiyor; 2FA/bağlı sağlayıcı silmeyi engellemiyor (yalnız ikinci faktörü ister). |
| 2.3 | **Yok** | C (parolasız) reddedilmiyor. Parola yerine **son 10 dk içinde giriş** (`recent-login-window=PT10M`) ister; yoksa `REAUTHENTICATION_REQUIRED` 403. |
| 2.4 | **Yok** | Böyle bir kod yok. Mevcut ilgili kodlar: `CURRENT_PASSWORD_INVALID` 400, `REAUTHENTICATION_REQUIRED` 403, `ACCOUNT_EMAIL_REQUIRED` 409, `ADMIN_DELETION_FORBIDDEN` 409. Silme isteğinde `PASSWORD_REQUIRED` **dönmüyor**. |
| 2.5 | **Yok** | Talep/onay akışı yok. Silme kullanıcı onayıyla (e-postadaki bağlantı) ilerler. |
| 2.6 | **Yok** | `GET /admin/account-deletions/pending` insan talepleri değil, **takılı silme görevlerini** (hangi servise silme çağrısı ulaşmadı) listeler. Yönetici onay/ret ucu yok. |
| 2.7 | **Yok** | Yönetici iptal ucu yok. İptal (`deletion/cancel`) kimlik + parola ister. |
| 2.8 | **Var** | `POST /account/deletion/cancel`: e-posta/kullanıcı adı + parola (+ TOTP). Parolasız hesap iptal **edemez** (`INVALID_CREDENTIALS`). |
| 2.9 | **Belgeli** | Parola; parolasızsa son 10 dk girişi; TOTP açıksa kod veya kurtarma kodu. Alanlar: `password`, `code`, `recoveryCode`. |
| 2.10 | **Kısmen** | `DELETION_REQUESTED` hesap **giriş yapamaz**: `ACCOUNT_DELETION_PENDING` 403 döner, token verilmez. Tek yol açık iptal ucudur. Başlatılırken tüm oturum ve cihazlar iptal edilir. |
| 2.11 | **Var** | Outbox tablosu hesaba `ON DELETE CASCADE` ile bağlı (`V64__account_deletion_and_event_outbox.sql`); hesap satırı silinince bekleyen olaylar da gider. Tek açık nokta: silme anında zaten HTTP yolunda olan olay User'a ulaşabilir ve yalnız `userId`'li boş satır açabilir (kişisel veri yok). Ayrıntı: `USER-AUTH-REQ-03` (kapalı). |
| 2.12 | **Var** | `HttpAccountDataEraser` her hedefe (`user` dahil) `DELETE /internal/v1/accounts/{id}` gönderir; yalnız `200`/`204` başarı sayılır. |
| 2.13 | **Var** | Üstel bekleme: ilk 1 dk, tavan 6 saat, 10 denemede "takılı" işaretlenir ve admin listesine düşer (`AUTH_DELETION_*`). |

## 3. E-posta değiştirme

| # | Cevap | Not |
|---|---|---|
| 3.1 | **Var** | `POST /email/change` + `/email/change/confirm`: mevcut parola (+ TOTP). |
| 3.2 | **Var** | Aynı akış. |
| 3.3 | **Yok** | C reddedilmiyor. Parolasız hesap **yalnız TOTP/kurtarma koduyla** yeniden doğrulanır; 2FA yoksa `PASSWORD_REQUIRED` 409. |
| 3.4 | **Yok (parola ile açılıyor)** | C parola belirleyince `email/change` çalışır. Sizin "kalıcı yasak" kuralınız uygulanmıyor; önce 1.4 gerekir. |
| 3.5 | **Yok** | `EMAIL_LOCKED_BY_PROVIDER` benzeri kod yok. |
| 3.6 | **Doğrulanmadı** | Sağlayıcı e-postası ile hesap e-postası farklıysa çakışma çıkarmıyor gibi; test yazılmadı. |

Not: e-posta değişince tüm oturumlar ve cihazlar iptal edilir.

## 4. Google / Steam bağlama ve kaldırma

| # | Cevap | Not |
|---|---|---|
| 4.1 | **Var** | `POST /oauth/google/link/start`, `/oauth/steam/link/start` (oturumlu). |
| 4.2 | **Var** | Bağlama **parola ister**; parolası olmayan hesap `PASSWORD_REQUIRED` 409 alır. TOTP açıksa ikinci faktör de. |
| 4.3 | **Yok (doğru)** | Kaldırma ucu yok; bu 2026-11-05 kararıdır (`USER-AUTH-REQ-05`: kullanıcı kaldıramaz, yalnız yönetici yoluyla ve planlı). `LAST_LOGIN_METHOD`, `EXTERNAL_IDENTITY_NOT_LINKED` kodları tanımlı ama kullanılmıyor. |
| 4.4 | **Farklı** | Kimlik başka hesapta: `EXTERNAL_IDENTITY_TAKEN` 409. Bu hesapta aynı sağlayıcıdan zaten var: `EXTERNAL_IDENTITY_ALREADY_LINKED` 409. |
| 4.5 | **Yok** | Aynı sağlayıcıdan ikinci kimlik reddedilir (`EXTERNAL_IDENTITY_ALREADY_LINKED`). |
| 4.6 | **Kısmen** | Teknik engel yok ama C önce parola belirlemeli (4.2). Ürün kararı sizde. |

## 5. Parola belirleme

| # | Cevap | Not |
|---|---|---|
| 5.1 | **Var** | `POST /password/set/request` (e-posta kodu) + `/password/set`. |
| 5.2 | **Doğru** | Türü değiştirmez, çünkü tür kavramı yok (bkz. 1.4). |
| 5.3 | **Var** | `PASSWORD_ALREADY_SET` 409. |
| 5.4 | **Doğrulanmadı** | `forgot`/`reset` e-posta üzerinden çalışır; parolasız hesap için ayrıca denenmedi. E-postasız Steam hesabında akış olmaz. |

## 6. 2FA

| # | Cevap | Not |
|---|---|---|
| 6.1 | **Var** | `2fa/totp/setup` + `activate`. |
| 6.2 | **Farklı** | `disable`: **parola + (TOTP kodu veya kurtarma kodu)** ister. "Yalnız kurtarma koduyla" değil. Parolasız hesap kapatamaz (`INVALID_CREDENTIALS`). |
| 6.3 | **Var** | Kurulumda kurtarma kodları üretilir (`RecoveryCodesResponse`). Bir kez gösterilir: **doğrulanmadı** (yanıt şekline bakılmadı). |
| 6.4 | **Yok** | Tür kavramı olmadığı için "düşme" de yok. |
| 6.5 | **Soruluyor (Steam)** | Zorunlu 2FA rolleri (admin/moderatör) için; ayrıca **Steam hesapları için de zorunlu** (`isRequiredForSteamAccount`, `TWO_FACTOR_ENROLLMENT_REQUIRED`). Google ile kayıt olan C'ye sorulmuyor, Steam C'ye soruluyor. Bu sizin "C'ye 2FA sorulmaz" kuralıyla çelişir. |
| 6.6 | **Var** | Google C hesabı isteğe bağlı açabilir. |

## 7. Kısıtlı işlemler

| # | Cevap | Not |
|---|---|---|
| 7.1 | **Yok** | Token claim'leri: `sid`, `token_version`, `roles`, `auth_methods` (oturum). "Güvenli hesap" bayrağı yok. |
| 7.2 | **Kısmen** | Access token ömrü 60 sn (`JWT_ACCESS_TTL_SECONDS`). Claim eklenirse en geç sonraki yenilemede güncel olur. |
| 7.3 | **Yok** | Ortak `STEP_UP_REQUIRED` kodu tanımlı değil. |
| 7.4 | **Bekliyor** | Karar sizin. |

## 8. Oturum ve cihaz

| # | Cevap | Not |
|---|---|---|
| 8.1 | **Var** (kodla tam doğrulanmadı) | `logout-all` oturumdaki hesap için çalışır. |
| 8.2 | **Var** | Parola değişimi ve sıfırlama tüm oturum ve cihazları iptal eder. E-posta değişimi ve silme başlatma da. |
| 8.3 | **Kullanıcı** | `POST/GET /devices`, `DELETE /devices/{id}` oturumlu kullanıcı içindir; `POST /devices/token` cihaz anahtarıyla masaüstü içindir. |

## 9. Dev ve test

| # | Cevap | Not |
|---|---|---|
| 9.1 | **Yok** | `ops/dev.mjs accounts` rol başına 5 hesap açar (user, creator, developer, moderator, admin). A/B/C yok. |
| 9.2 | **Doğrulanmadı** | Dev'de Google/Steam için mock sağlayıcı olup olmadığına bakmadım. |
| 9.3 | **Yok** | A/B/C × silme/e-posta/bağlantı testleri yok (kavram yok). |

## Frontend'e etkisi

- **Yazılabilir bugün:** parola/TOTP/e-posta/silme ekranları, Auth'un **mevcut** kurallarıyla (parola ve 2FA'ya göre).
- **Bekleyen (Auth geliştirmesi gerekir):** 1.x (tür alanları), 2.2–2.7 ve 3.3–3.5 (tür bazlı ret + kodlar), 7.x (sunucuda zorlama).
- Önce şu ürün kararları gerekir: kayıt yöntemi geriye dönük nasıl atanacak; Steam C'ye 2FA sorulacak mı (bugün zorunlu); silme talebi insan onayıyla mı yürüyecek; 2FA kapatma "yalnız kurtarma koduyla" mı kalacak.
