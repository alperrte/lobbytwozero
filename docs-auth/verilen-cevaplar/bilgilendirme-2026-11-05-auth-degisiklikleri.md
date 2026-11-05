# AUTH DEĞİŞİKLİKLERİ — BİLGİLENDİRME (2026-11-05)

**Sahip:** Kerem (auth-service) · **Kapsam:** Auth'taki yeni özellikler ve herkesten beklenenler · **Branch:** `feature/auth-service`

Bu dosya herkes içindir. Kendi servisinin özel notu, kendi klasöründeki `bilgilendirme-2026-11-05.md` dosyasındadır. Yapay zekâ asistanına vereceksen bu dosyanın ve kendi klasörünün yolunu ver ve "bunları oku, bana yapılacakları söyle" de. Auth'u yerelde çalıştırma, dev/prod farkı ve hata giderme için: [`../ekip-hizli-baslangic.md`](../ekip-hizli-baslangic.md).

---

## KISACA

| Konu | Sonuç |
|---|---|
| Servis token ve kullanıcı JWT sözleşmesi | **DEĞİŞMEDİ.** `aud`, `sub`, `scope`, `account_id`, `typ`, JWKS aynı. Derleme ve yansıma ile Auth sınıflarını kullanan testler (`ConsumerContractTest` kilidi) etkilenmez |
| Eski uçlar | `POST /api/v1/auth/oauth/steam/unlink` **kaldırıldı** (aşağıda). Başka uç kalkmadı |
| Yeni uçlar | Google bağlama, parola belirleme, zorunlu 2FA kurulumu (aşağıda) |
| Senden istenen | Yalnız silme ucu eksik 7 serviste ve ilgili birkaç servis/Frontend'de (aşağıdaki tablo) |

---

## NE DEĞİŞTİ

### 1. Google hesap bağlama (B10)
- `POST /api/v1/auth/oauth/google/link/start` (oturum + parola, TOTP açıksa kod) → `{ "redirectUrl" }`
- `GET /api/v1/auth/oauth/google/link/callback?code&state` → tarayıcı gezinmesi; sonuç Frontend adresine `#status=google_linked` veya `#error=KOD`
- Steam bağlama uçları aynı kalır (`steam/link/start`, `steam/link/callback`).
- **Karar:** bağlanan Steam/Google kimliği kullanıcı tarafından **kaldırılamaz.** `steam/unlink` uç kaldırıldı. Yanlış veya devredilmiş bağlantı yalnız yönetici (ticket) yoluyla kaldırılır; bu yol henüz yok (planlı).
- Olay: `ExternalIdentityLinked { accountId, provider: "STEAM" | "GOOGLE", providerSubject }`. `ExternalIdentityUnlinked` şu an **üretilmiyor.**

### 2. Steam bağlı hesapta zorunlu 2FA
- Steam kimliği bağlı hesap TOTP açık değilken **oturum alamaz.** Parola veya Steam girişi sonrası token yerine tek kullanımlık kurulum anahtarı döner:
  - `POST /api/v1/auth/login` yanıtı: `{ "status": "MFA_ENROLLMENT_REQUIRED", "challengeId", "challengeExpiresAt" }`
  - Harici giriş dönüşü: `#status=mfa_enrollment_required&challenge=<id>`
- `POST /api/v1/auth/login/2fa/enrollment/start` `{ challengeId }` → `{ secret, otpauthUri }`
- `POST /api/v1/auth/login/2fa/enrollment/complete` `{ challengeId, code }` → oturum çerezleri + `recoveryCodes` (yalnız bir kez)
- Steam bağlı hesapta TOTP kapatılamaz. Ayar üretimde açık, yerel dev'de kapalıdır (`ltz.auth.two-factor.require-for-steam-accounts`). Dev'de denemek için: `mvn`/IDE ile çalışıyorsan `LTZ_AUTH_TWOFACTOR_REQUIREFORSTEAMACCOUNTS=true`; Compose ile çalışıyorsan `application-dev.properties` satırını geçici olarak `true` yap (commit etme). Ayrıntı: `docs-auth/ekip-hizli-baslangic.md`.

### 3. Parola belirleme (B11)
- `POST /api/v1/auth/password/set/request` `{ code?, recoveryCode? }` → `202`; hesabın doğrulanmış e-postasına 6 haneli kod gider (Mail `verification-codes` ucu, yeni uç yok)
- `POST /api/v1/auth/password/set` `{ emailCode, newPassword, newPasswordConfirmation }` → `204`; tüm oturumlar kapanır, çerezler silinir, yeni parolayla yeniden giriş yapılır
- Neden: yalnız Google/Steam ile açılmış hesap parolasızdı; `PASSWORD_REQUIRED (409)` alıyordu (cihaz bağlama, e-posta değiştirme, hesap silme iptali, Google/Steam bağlama). Parola belirleyince hepsi açılır.

### 4. Süreli hesap askısı (B8)
- `SUSPENDED` hesap isteğe bağlı bitiş tarihiyle verilebilir: `POST /api/v1/auth/admin/accounts/{id}/status` ve servis kimlikli `POST /internal/v1/accounts/{id}/status` gövdesine isteğe bağlı `"suspendedUntil": "ISO-8601"` (yalnız `SUSPENDED` hedefinde; şimdi + 1 dk ile şimdi + 365 gün arası, aksi `400 INVALID_SUSPENSION_END`). Süre dolunca Auth hesabı otomatik `ACTIVE` yapar, `AccountStatusChanged(ACTIVE)` olayı yayımlanır ve kullanıcıya e-posta gider. `BANNED` ve `DISABLED` süresizdir.
- Yönetici görünümü (`GET .../admin/accounts/{id}`) süreli askıdaki hesapta `suspendedUntil` döner. Verilmezse askı süresizdir (eskisi gibi elle açılır); mevcut istemciler değişmeden çalışır.

### 5. Anahtar rotasyonu (S9, yalnız Auth)
- TOTP şifreleme ve HMAC anahtarları döndürülebilir hale geldi (`docs-auth/anahtar-rotasyonu.md`). Diğer servisleri etkilemez.

Sözleşme ayrıntısı: `docs/api.md` ("Steam ve Google bağlama", "Zorunlu 2FA kurulumu", "Parola belirle").

---

## KİMDEN NE BEKLENİYOR

| Kim | İş | Durum |
|---|---|---|
| **Frontend** | Yeni durumları ve uçları bağla: `MFA_ENROLLMENT_REQUIRED` + `#status=mfa_enrollment_required`, kurulum uçları (QR göster, ilk kodu al, kurtarma kodlarını bir kez göster), parola belirleme ekranı, `google_linked`, `steam/unlink` düğmesini kaldır. `PASSWORD_REQUIRED` alan kullanıcıyı "parola belirle" ekranına yönlendir | AÇIK |
| **Admin, Content, Event, LFP, Messaging, Notification, Recommendation** | `DELETE /internal/v1/accounts/{accountId}` silme ucunu yaz (`docs-auth/servis-sozlesmeleri.md` §3.1). Yazılmadıkça Auth'ta görevin durumu PENDING kalır ve hesap silme hiçbir zaman tamamlanmaz. Verisi olmayan servis de ucu açıp `204` döner | AÇIK |
| **User** | `ExternalIdentityLinked` olayında `provider` artık GOOGLE da olabilir. `ExternalIdentityUnlinked` beklenmez. `AccountEmailChanged` olayını tüketme işi hâlâ açık | AÇIK |
| **Hardware** | Bilgi: cihaz bağlama parola ister; parolasız Google/Steam kullanıcısı artık parola belirleyip bağlayabilir | BİLGİ |
| **Mail** | Bilgi: parola belirleme kodu mevcut `verification-codes` ucunu kullanır; isteğe bağlı ayrı şablon | BİLGİ |
| **Alper (altyapı)** | Ortak parent pom'da bağımlılık yamaları ve katmanlı rate limit (ayrıntı `chore-security/` klasöründe) | AÇIK |
| **Community** | `AUTH_EVENT_TARGETS=community` açılmasını bekliyoruz; alıcı hazır olunca haber ver | BİLGİ |
| **Diğerleri** (Game, Library, Review, Social, Performance) | Yapılacak iş yok | KAPANDI |

---

## DOĞRULAMA

Auth tarafı: 528 test geçti, satır kapsamı %93,40, Checkstyle 0 ihlal, kapsam politikası geçti. Sağlayıcı davranışı (Google/Steam) yerel sahte sunucularla denendi; **gerçek Google/Steam ile uçtan uca denenmedi** (OAuth kimlik bilgileri gerekir).
