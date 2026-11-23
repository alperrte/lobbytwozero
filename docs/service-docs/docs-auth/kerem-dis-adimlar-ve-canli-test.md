# KEREM — DIŞ ADIMLAR VE CANLI TEST PLANI

**Yalnız Kerem için** (ekibe gönderilecek bir belge değildir) · **Güncelleme:** 2026-11-05 · Bu dalda (`feature/auth-service`) çalışan yapay zekâ asistanları da okuyabilir: burada kodla çözülemeyen, **elle yapılacak** işler ve gerçek sağlayıcılarla yapılacak denemeler yazılıdır. Sır/anahtar değeri yazılmaz.

---

## 1. PR #54 merge edilmeden önce (elle)

| # | İş | Neden | Durum |
| --- | --- | --- | --- |
| 1 | **Google Cloud Console → OAuth istemcisi (Web) → Yetkili yönlendirme URI'lerine ekle:** `<AUTH_PUBLIC_BASE_URL>/api/v1/auth/oauth/google/callback` (giriş) ve `<AUTH_PUBLIC_BASE_URL>/api/v1/auth/oauth/google/link/callback` (**yeni**, bağlama). Dev için `<AUTH_PUBLIC_BASE_URL>` = `http://localhost:5080`; prod'da https alan adı | Eklenmezse Google "redirect_uri_mismatch" döner, bağlama çalışmaz | AÇIK |
| 2 | OAuth onay ekranı "Testing" modundaysa **kendi Google hesabını "Test users"a ekle** | Aksi halde yalnız proje sahibi giriş yapabilir | AÇIK |
| 3 | Frontend sahibine mesajı gönder (aşağıda hazır metin, bölüm 3) | Yeni durumlar bağlanmazsa prod'da Steam'li kullanıcılar girişte takılır | AÇIK |
| 4 | CI sonucunu kontrol et (PR #54). Main'deki iki eski kırmızı (Backend Verify "Stale/empty application archive", `end-of-file-fixer`) Auth'tan bağımsızdır | Yeni kırmızı çıkarsa bana ait | BEKLİYOR |

## 2. Merge'den sonra / sıradaki (elle veya karar)

| İş | Not |
| --- | --- |
| Alper'e B13 (ortak parent pom) ve B1 (rate limit) mesajı | Metin hazır: `docs-auth/verilen-cevaplar/chore-security/bilgilendirme-2026-11-05.md`; sen ilet |
| Silme ucu eksik 7 servise hatırlatma (admin, content, event, lfp, messaging, notification, recommendation) | Her birinin klasöründe `bilgilendirme-2026-11-05.md` var |
| B9: saklama süresi için hukuk görüşü | 5651 (yer/erişim sağlayıcı kaydı 1–2 yıl, doğrulanmadı) ve KVKK; o zamana dek 365 gün |
| Üretim SMTP sağlayıcısı kararı | Mail ajanına verilen prompt'ta; karar sende |
| `AUTH_EVENT_TARGETS=community` | Nisa "alıcı hazır" deyince |
| Anahtar rotasyonu | İlk gerçek rotasyonu **önce dev'de** dene (bölüm 4.3) |

## 3. Frontend sahibine gönderilecek mesaj (hazır metin)

```text
Merhaba, Auth tarafında yeni uçlar ve durumlar var; bağlamazsak prod'da bazı kullanıcılar girişte takılır.
Ayrıntı: docs-auth/verilen-cevaplar/bilgilendirme-2026-11-05-auth-degisiklikleri.md ve docs/api.md.

1) [KALDIRILDI 2026-11-10: Steam için zorunlu 2FA yok; Steam girişi doğrudan oturum açar. Aşağıdaki `MFA_ENROLLMENT_REQUIRED` yalnız 2FA zorunlu roller (ADMIN/MODERATOR) için geçerlidir.] Eski metin: Steam bağlı hesaplarda giriş 2FA kurulumu isteyebilir:
   - POST /api/v1/auth/login yanıtı { status: "MFA_ENROLLMENT_REQUIRED", challengeId, challengeExpiresAt } olabilir;
     Steam dönüşü #status=mfa_enrollment_required&challenge=<id>. Çerez YAZILMAZ.
   - POST /api/v1/auth/login/2fa/enrollment/start { challengeId } -> { secret, otpauthUri } (QR göster)
   - POST /api/v1/auth/login/2fa/enrollment/complete { challengeId, code } -> oturum çerezleri + recoveryCodes
     (kurtarma kodlarını BİR KEZ göster, kaydettirmeden geçme).
2) Parolasız Google/Steam hesabı: 409 PASSWORD_REQUIRED alırsa "parola belirle" ekranına yönlendir:
   - POST /api/v1/auth/password/set/request { code?, recoveryCode? } -> 202 (e-postaya kod)
   - POST /api/v1/auth/password/set { emailCode, newPassword, newPasswordConfirmation } -> 204
     Başarıda tüm oturumlar kapanır; yeni parolayla yeniden giriş gerekir.
3) Google bağlama: POST /api/v1/auth/oauth/google/link/start (parola, TOTP açıksa kod) -> { redirectUrl };
   dönüş #status=google_linked veya #error=KOD. Steam bağlama aynı kalır.
4) Bağlanan Steam/Google kimliği KALDIRILAMAZ: steam/unlink ucu ve düğmesi kalkıyor.
5) Yönetici arayüzü varsa: hesap askısına isteğe bağlı bitiş tarihi (suspendedUntil) verilebilir.
```

---

## 4. Canlı test planı (gerçek Steam, gerçek Google, gerçek anahtar döndürme)

Ortak ön koşul: dev yığını ayakta (Auth + Gateway + Mailpit), `node ops/generate-auth-dev-keys.mjs` çalıştırılmış ve çıktısı `Hata yok`. Tarayıcı adımlarını **sen** yaparsın (kendi Steam/Google hesabınla); sonuçları bana yapıştırırsan doğrulamaya yardım ederim. Sır içeren değerleri (token, kod) sohbete yapıştırma.

### 4.1 Steam — **YAPILDI (2026-11-05, Kerem + Claude)**

Sonuç: yalıtılmış düzenekle (geçici Postgres + Mailpit + ayrı Auth, zorunlu 2FA açık) gerçek Steam'le kayıt → e-posta doğrulama → `mfa_enrollment_required` → TOTP kurulumu (10 kurtarma kodu) → `mfa_required` → giriş başarılı. Veritabanı: TOTP ACTIVE, kurtarma kodları 10, kurulum challenge'ı tüketildi. Aşağıdaki adımlar elle tekrar denemek içindir.

Steam, dönüşü tarayıcıya yaptırır ve doğrulamayı Auth'tan Steam'e giden sunucu isteğiyle yapar; `localhost` yeterlidir.

1. Tarayıcıda `http://localhost:5080/api/v1/auth/oauth/steam/start` aç → Steam girişi.
2. Dönüşte adres çubuğunda `…/auth/callback#status=registration_required&provider=steam&token=…` görürsün (Frontend çalışmıyorsa sayfa açılmaz, fragment yine de görünür). **Beklenen:** `registration_required`. Frontend açıksa yalnız **kullanıcı adı** formu gelir.
3. Kullanıcı adını yaz (ya da `POST /api/v1/auth/oauth/steam/complete` `{ "registrationToken": "<token>", "username": "…" }`, CSRF başlığıyla). **Beklenen:** oturum açılır (e-posta ve doğrulama adımı yoktur).
4. Adım 1'i tekrarla. **Beklenen:** `#status=authenticated` (dev'de Steam 2FA zorunluluğu kapalı).
5. [KALDIRILDI 2026-11-10: `require-for-steam-accounts` ayarı yok.] Zorunlu 2FA akışını görmek için ADMIN/MODERATOR hesabıyla giriş dene; eski beklenti: **Beklenen:** `#status=mfa_enrollment_required&challenge=…`; `/login/2fa/enrollment/start` → QR'ı authenticator'a okut → `/complete`.
6. Doğrula: `select account_id, provider from external_identities where provider = 'STEAM';` bir satır.

### 4.2 Google

1. Bölüm 1'deki 1. ve 2. maddeler tamam olmalı. `.env.dev` içine `GOOGLE_CLIENT_ID` ve `GOOGLE_CLIENT_SECRET` yaz (Cloud Console'dan), Auth'u yeniden başlat.
2. Giriş: tarayıcıda `http://localhost:5080/api/v1/auth/oauth/google/start`. **Beklenen:** yeni hesap için `#status=authenticated` (e-posta Google tarafından doğrulanmış olmalı).
3. Bağlama (e-postayla açılmış bir hesaba): önce o hesapla giriş yap (parola), sonra `POST /api/v1/auth/oauth/google/link/start` `{ "password": "…" }` → dönen `redirectUrl`'i tarayıcıda aç → Google'da onayla. **Beklenen:** `#status=google_linked`.
4. Doğrula: `select account_id, provider from external_identities where provider = 'GOOGLE';` ve `select event_type, payload_value from auth_event_outbox order by created_at desc limit 3;` (`EXTERNAL_IDENTITY_LINKED`).
5. Aynı Google hesabını ikinci bir Auth hesabına bağlamayı dene. **Beklenen:** `#error=EXTERNAL_IDENTITY_TAKEN`.
6. `redirect_uri_mismatch` görürsen bölüm 1'deki 1. madde eksiktir.

### 4.3 Anahtar rotasyonu — **YAPILDI (2026-11-05, otomatik, yalıtılmış)**

Sonuç: gerçek Auth jar'ı + gerçek PostgreSQL ile dört farklı anahtar ayarı denendi, **16/16 kontrol geçti**: önceki anahtar verilmezse eski TOTP ve kurtarma kodu bozulur (kontrol deneyi); önceki anahtarlarla eski kayıtlar çalışır ve `key_version` 1'den 2'ye geçer; eski TOTP anahtarı kaldırılınca yeniden şifrelenmiş kayıt yalnız yeni anahtarla çözülür; eski HMAC ile özetli kurtarma kodları geçer ve tek kullanımlıktır. Aşağıdaki adımlar kendi dev yığınında elle tekrar etmek içindir.

Amaç: eski sürümdeki TOTP kaydı ve kurtarma kodu rotasyondan sonra çalışsın.

1. Provadan **önce** bir hesapta TOTP'yi etkinleştir (`POST /api/v1/auth/2fa/totp/setup` + `/activate`) ve bir kurtarma kodunu **kullanmadan** sakla.
2. Kontrol: `select key_version, count(*) from totp_credentials group by 1;` → hepsi `1`.
3. `node ops/generate-auth-dev-keys.mjs --rotate-totp --rotate-hmac` (anahtar değerleri yazılmaz) ve ardından çıkışta `Hata yok`.
4. Auth'u yeniden oluştur: `docker compose up -d auth-service` (Compose ENV'i yeniden okur).
5. Aynı hesapla TOTP koduyla giriş yap. **Beklenen:** başarılı. Kontrol: `select key_version, count(*) from totp_credentials group by 1;` → o hesap `2` oldu.
6. Başka bir oturumda **kurtarma koduyla** giriş yap. **Beklenen:** başarılı (eski HMAC anahtarıyla özetlenmişti, önceki anahtar sayesinde geçer).
7. **Geri alma yoktur:** giriş yapan hesaplar 2. sürüme geçti; eski `.env.dev`'i geri koyarsan o kayıtlar çözülemez. Bu yüzden provayı yalnız deneme hesaplarıyla yap ve provadan önce `.env.dev`'in yedeğini al (yalnız tamamen başarısız bir provada, hiç giriş yapılmadan geri koymak güvenlidir). Sorun çıkarsa yeni dosyayı koru ve çıktıyı bana göster.

SQL için yardımcı (parola yazmadan, konteyner içinden):

```text
docker exec -i ltz-dev-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d auth_db' < sorgu.sql
```

---

## 5. Yerel veritabanı notu (2026-11-05 kontrolü)

Bu makinedeki `auth_db`: `flyway_schema_history` V61–V65 hepsi başarılı, tek hesap. **Checksum uyuşmazlığı yok, sıfırlama gerekmedi.** V66–V69 yeni Auth sürümü açılınca kendiliğinden uygulanır. Başka bir makinede "checksum mismatch" görülürse yalnız o yerel veritabanı sıfırlanır (`docs-auth/migration-yazim-kurallari.md`).
