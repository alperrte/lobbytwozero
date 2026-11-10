# Auth cevabı: hesap türleri, silme, e-posta, bağlantılar (2026-11-10, güncel)

Soru belgesi: `AUTH-SORULAR.md` (User/Frontend). Bu belge ilk sürümün (kod okuma, "yok" cevapları) yerine geçer: istenen kurallar **Auth'ta uygulandı** ve otomatik testlerle doğrulandı (`AccountTierTest` 23 test, Auth toplam 604 test yeşil). Canlı (gerçek servis) doğrulaması ayrıca belirtilenler dışında yapılmadı.
Etiketler: **Var / Yok / Değişti**. Ayrıntılı uç sözleşmesi: `docs/api.md`.

## Özet

- **Tür saklanmaz, hesaplanır** (kayıt yöntemi + TOTP + bağlı sağlayıcı):
  - **C**: doğrudan Google veya Steam ile kayıt.
  - **B**: normal kayıt ve (TOTP açık veya Google/Steam bağlı).
  - **A**: normal kayıt, ikisi de yok. `trusted = tür != A`.
- `GET /me` ve access token (`tier`, `trusted` claim) türü taşır. Tür token yenilemede en geç ~60 sn gecikmeyle güncellenir; `/me` ve silme kontrolü anlık hesaplar.
- B ve C kendi başına silemez; **yöneticiye talep açar**, yönetici onaylarsa standart silme süreci başlar.
- Google ile kayıt olan hesap e-posta değiştiremez. Steam ile kayıt olan (e-postasız) hesap ilk adresini ekleyebilir (bilinçli sapma: aksi halde hiç e-posta ekleyemezdi).
- **Steam için zorunlu 2FA kaldırıldı** (karar: Steam ana referans). Zorunlu 2FA yalnız ADMIN/MODERATOR.
- Geriye dönük doldurma yapılmadı (yalnız yerel 5 rol hesabı var, hepsi `PASSWORD` kayıtlı).
- Yönetici arayüzü User/Frontend ekibinindir; Auth yalnız backend uçlarını sağlar.

## 1. Hesap türünü ayırt etme

| # | Cevap | Not |
|---|---|---|
| 1.1 | **Var** | `/me`: `passwordSet` |
| 1.2 | **Var** | `/me`: `providers[]` (hesabın bağlı sağlayıcıları, ör. `["GOOGLE"]`). `authMethods` hâlâ oturumun giriş yöntemidir. |
| 1.3 | **Var** | `/me`: `totpEnabled` |
| 1.4 | **Var** | `/me`: `registrationMethod` (`PASSWORD` / `GOOGLE` / `STEAM`), `accounts.registration_method` (V65), değişmez. |
| 1.5 | **Var** | Parola belirlemek veya sağlayıcı bağlamak kayıt yöntemini değiştirmez; C, C kalır. |
| 1.6 | **Var** | `/me`: `accountTier` (`A`/`B`/`C`), `trusted`. Aynı iki değer token claim'inde (`tier`, `trusted`). |

## 2. Hesap silme

| # | Cevap | Not |
|---|---|---|
| 2.1 | **Var (yalnız A)** | `POST /account/deletion/request` yalnız A türü içindir. |
| 2.2 | **Var** | B: `403 DELETION_REQUIRES_SUPPORT`. |
| 2.3 | **Var** | C: aynı kod (parola/yakın giriş aranmadan önce türe bakılır). |
| 2.4 | **Var** | Kod: `DELETION_REQUIRES_SUPPORT` (403). Yeni destek kodları: `DELETION_SELF_SERVICE_AVAILABLE` 409 (A talep açmaya çalışırsa), `DELETION_SUPPORT_REQUEST_NOT_FOUND` 404, `DELETION_SUPPORT_ALREADY_DECIDED` 409. |
| 2.5 | **Var** | `POST /account/deletion/support-request` (`{message?}`, ≤500) → `202`; hesap başına tek bekleyen talep (ikinci istek aynısını döner). `GET` aynı yol: en son talep (`200`) ya da `204`. |
| 2.6 | **Var** | `GET /admin/account-deletion-requests?limit=` (bekleyenler, en eski önce; ROLE_ADMIN). Eski `account-deletions/pending` hâlâ takılı silme görevleridir, ayrı şeydir. |
| 2.7 | **Kısmen** | `POST /admin/account-deletion-requests/{id}/approve` ve `/reject` (`{message?}`) var. Onaylanmış silmeyi yönetici iptal etme ucu **yok**; kullanıcı mevcut `deletion/cancel` ile (parola + TOTP) vazgeçer. |
| 2.8 | Değişmedi | `deletion/cancel`: kimlik + parola (+ TOTP); parolasız hesap iptal edemez. |
| 2.9 | Değişmedi | A için: parola; parolasızsa yakın giriş; TOTP açıksa kod/kurtarma kodu. |
| 2.10 | Değişmedi | `DELETION_REQUESTED` hesap giriş yapamaz (`ACCOUNT_DELETION_PENDING`). Yönetici onayında oturumlar kapanır. |
| 2.11–2.13 | Değişmedi | Outbox cascade, eraser çağrıları, üstel bekleme aynen. |

Onay davranışı: yönetici onayı standart silmeyi başlatır (bekleme süresi, servis görevleri; kullanıcıdan e-posta onayı beklenmez). Ret hesabı değiştirmez; kullanıcı yeniden talep açabilir. Onay anında hesap aktif değilse `409 TARGET_NOT_ACTIVE`; ADMIN hesabı silinemez (`ADMIN_DELETION_FORBIDDEN`). Güvenlik olayları: `DELETION_SUPPORT_REQUESTED/APPROVED/REJECTED`.

## 3. E-posta değiştirme

| # | Cevap | Not |
|---|---|---|
| 3.1–3.2 | Değişmedi | Parola (+ TOTP); e-postasız hesapta yalnız TOTP/kurtarma kodu. |
| 3.3 | **Kısmen** | **Google ile kayıt olan** hesap reddedilir. **Steam ile kayıt olan** reddedilmez (e-postasız olduğu için ilk adresi ekleyebilmeli). |
| 3.4 | **Var** | Google C parola belirlese de kilit sürer (kayıt yöntemine bakılır). |
| 3.5 | **Var** | `403 EMAIL_LOCKED_BY_PROVIDER` |
| 3.6 | Doğrulanmadı | Sağlayıcı e-postası farklıysa çakışma ayrıca denenmedi. |

## 4. Google / Steam bağlama ve kaldırma

Değişmedi: bağlama parola ister (parolasız hesap `PASSWORD_REQUIRED`), kaldırma ucu bilinçli yok (`USER-AUTH-REQ-05`), aynı sağlayıcıdan ikinci kimlik reddedilir. Sağlayıcı bağlamak A'yı B yapar.

## 5. Parola belirleme

Değişmedi (`/password/set/request` + `/password/set`, `PASSWORD_ALREADY_SET`). Türü değiştirmez (kayıt yöntemi sabit). Steam'deki zorunlu TOTP kontrolü kaldırıldı.

## 6. 2FA

| # | Cevap | Not |
|---|---|---|
| 6.1 | Var | `2fa/totp/setup` + `activate`. |
| 6.2 | **Değişti** | `disable`: parolası olan hesapta **parola + (TOTP veya kurtarma kodu)**; parolasız hesapta yalnız kod. Yanlış/eksik parola `401 INVALID_CREDENTIALS`. Kod-hata yolu aynen. |
| 6.4 | **Var** | Güvencesi kalmayan normal hesap (TOTP kapandı, bağlı sağlayıcı yok) **A'ya düşer**; bağlı sağlayıcı varsa B kalır; C, C kalır. |
| 6.5 | **Değişti** | Steam C dahil **kimse için zorunlu değil**; Steam girişi doğrudan oturum açar. Zorunlu yalnız ADMIN/MODERATOR (kapatamazlar: `TWO_FACTOR_REQUIRED_FOR_ROLE`). |
| 6.6 | Var | İsteğe bağlı açılabilir. |

Bilinen kabul edilmiş açık: B hesap parola + TOTP ile 2FA'yı kapatıp A'ya düşebilir ve sonra kendi silebilir.

## 7. Kısıtlı işlemler

| # | Cevap | Not |
|---|---|---|
| 7.1 | **Var** | Token claim'leri: `tier` (`A`/`B`/`C`), `trusted` (bool). |
| 7.2 | Var | Access token 60 sn; tür değişimi en geç sonraki yenilemede token'a yansır. |
| 7.3 | **Kısmen** | `403 STEP_UP_REQUIRED` kodu Auth'ta tanımlı. **Kaynak servisler** (Library, Review vb.) `trusted=false` için bunu kendileri döndürür; Auth hangi işlemin kısıtlı olduğuna karışmaz. |
| 7.4 | Sizde | Hangi işlemin kısıtlı olacağı ürün kararıdır. |

## 8. Oturum ve cihaz

Değişmedi. Yönetici onaylı silme başlayınca hesabın tüm oturumları kapanır.

## 9. Dev ve test

| # | Cevap | Not |
|---|---|---|
| 9.1 | Kısmen | `ops/dev.mjs accounts` rol başına 5 hesap açar (hepsi `PASSWORD` kayıtlı, A). B/C için hesap açılmadı; gerekirse kayıt + TOTP etkinleştirme veya Google/Steam ile giriş. |
| 9.2 | Doğrulanmadı | Dev'de Google/Steam için mock sağlayıcı olup olmadığına bakılmadı. |
| 9.3 | **Var** | `AccountTierTest`: tür matrisi, claim'ler, silme/destek talebi/onay/ret, e-posta kilidi, 2FA kapatma. |

## Frontend ve User için notlar

- Ekranları `/me` alanlarına göre çizin: `accountTier`, `trusted`, `passwordSet`, `providers`, `totpEnabled`.
- B/C için "Hesabı sil" yerine "silme talebi" ekranı (`support-request`); durum için `GET` ile en son talep.
- Yönetici talep listesi/onay/ret arayüzü sizin tarafta; uçlar hazır.
- Dev ortamda eski `auth_db` kullanıyorsanız migration birleştirmesi nedeniyle bir kez sıfırlamak gerekir (`docs-auth/migration-yazim-kurallari.md`); `user_db`'deki rol hesabı profilleri de temizlenmeli veya `node ops/dev.mjs reset` kullanılmalı.
