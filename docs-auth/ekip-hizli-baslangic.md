# AUTH — EKİP HIZLI BAŞLANGIÇ

**Sahip:** Kerem (auth-service) · **Güncelleme:** 2026-11-05

Bu sayfa, Auth'u yerelde hızlı ve güvenli çalıştırmak isteyen herkes (ve yapay zekâ asistanları) içindir. Amaç: geliştirmeyi **yavaşlatmadan** güvenliği **gevşetmemek.** Kısa cümlelerle yazıldı; yapay zekâya "bu dosyayı oku, bana adımları söyle" diyebilirsin.

---

## 1. Auth'u yerelde ayağa kaldır (4 komut)

```text
1. .env.example dosyasını .env.dev olarak kopyala         (bir kez)
2. node ops/generate-auth-dev-keys.mjs                     anahtarları üretir; dolu olana dokunmaz, ekrana değer yazmaz
3. node ops/auth-env-check.mjs                             Auth'un AÇILIŞTA reddedeceği hatayı Docker'dan önce yakalar
4. .\ops\env.cmd use dev   (Linux/macOS: sh ./ops/env.sh use dev)   sonra Compose'u olağan şekilde başlat
```

- `COMPOSE_PROFILES=dev-mail` olmalı: doğrulama ve parola kodları **Mailpit**'e düşer (arayüz: `http://127.0.0.1:8025`). Gerçek e-posta gitmez.
- `node ops/auth-env-check.mjs` çıktısında `[FAIL]` varsa Auth açılmaz; yanındaki `->` satırı çözümü söyler. `[WARN]` açılışı durdurmaz ama bir kolaylığı kaçırırsın.
- V61–V65'i zaten uygulamış yerel veritabanı yeni sürümlerle **kendiliğinden ileri taşınır** (V66–V69 yeni dosyalardır, eskilere dokunulmadı); sıfırlamana gerek yok. Eski bir yerel veritabanında "checksum mismatch" görürsen aşağıdaki tabloya bak.

## 2. Dev'de kolay, prod'da zorunlu

| Konu | Yerel dev | Production |
| --- | --- | --- |
| E-posta | Mailpit (kodlar tarayıcıda görünür) | Yalnız `mail-service`; yoksa Auth **açılmaz** |
| Steam bağlı hesapta 2FA kurulumu | **Kapalı** (hızlı deneme) | **Açık**; kapatılırsa Auth açılmaz |
| ADMIN için 2FA | `AUTH_2FA_REQUIRED_ROLES` ile gevşetilebilir (yalnız dev/test) | Zorunlu; gevşetilirse Auth açılmaz |
| Anahtarlar | `generate-auth-dev-keys.mjs` ile yerel üretim | Ayrı üretilir, sır yöneticisinde; dev anahtarı **kullanılmaz** |
| İç ağ adresleri | `http` olabilir | `https` zorunlu |
| Parola kuralı, token ömrü (60 sn), CSRF, çerez öznitelikleri | Aynı | Aynı |

Yani gevşetilebilenler sınırlı, adı konmuş ve yalnız dev/test profilinde: başka bir güvenlik ayarını "geçici" kapatma. Steam 2FA'sını dev'de denemek istersen: Auth'u `mvn`/IDE ile çalıştırıyorsan ortam değişkeni `LTZ_AUTH_TWOFACTOR_REQUIREFORSTEAMACCOUNTS=true` yeter. Compose ile çalıştırıyorsan Compose ana makine değişkenlerini konteynere geçirmez; `application-dev.properties` içindeki `ltz.auth.two-factor.require-for-steam-accounts` satırını **geçici olarak** `true` yap ve commit etme.

## 3. "Auth açılmıyor" / "bir şey çalışmıyor" tablosu

| Belirti | Neden | Çözüm |
| --- | --- | --- |
| Auth açılışta `AUTH_OTP_HMAC_KEY` / `AUTH_TOTP_ENCRYPTION_KEY` hatası | Anahtar boş, kısa veya Base64 değil | `node ops/generate-auth-dev-keys.mjs` |
| Açılışta `previous-encryption-keys` hatası | Anahtar rotasyon listesi bozuk | `node ops/auth-env-check.mjs` hangi ayar olduğunu söyler |
| `prod` profilinde açılmıyor | `AUTH_MAIL_ADAPTER` `mail-service` değil, ya da güvenlik ayarı gevşetilmiş | Hata mesajı hangi ayarı söyler; prod'da gevşetilemez |
| Kod/bağlantı e-postası gelmiyor | `dev-mail` Compose profili kapalı | `COMPOSE_PROFILES=dev-mail` |
| `409 PASSWORD_REQUIRED` | Hesap Google/Steam ile açılmış, parolası yok | `POST /api/v1/auth/password/set/request`, sonra `/password/set` |
| Girişte `MFA_ENROLLMENT_REQUIRED` | Steam bağlı hesapta 2FA zorunlu ve henüz kurulu değil | `/login/2fa/enrollment/start`, sonra `/complete` |
| `403` ve hesap durumu | Hesap askıda/yasaklı; süreli askı kendiliğinden açılır | Yönetici uçlarından durum; ayrıntı `docs/api.md` |
| Veritabanı "checksum mismatch" | Uygulanmış bir migration dosyası değiştirilmiş | Yerel DB'yi sıfırla (`docs-auth/migration-yazim-kurallari.md`); uygulanmış migration'a asla dokunma |
| Google ile bağlama çalışmıyor | Dönüş adresi Google Console'da yok | `…/api/v1/auth/oauth/google/link/callback` adresini ekle |

## 4. Yapma listesi (kısa)

- Anahtarı, parolayı, token'ı koda, commit'e, loga veya sohbete yazma. `.env*` Git'te değildir; öyle kalsın.
- Production anahtarını yerele, yerel anahtarı production'a kopyalama.
- Test geçsin diye CSRF, token ömrü, parola kuralı veya yetki kontrolünü kapatma. Hızlı denemek için yukarıdaki **izinli** dev gevşetmeleri yeter.
- Uygulanmış migration dosyasını değiştirme; yeni sürüm yaz (Auth'ta sıradaki ve son boş sürüm V70; aralık için Kerem'e sor).
- Auth'a yeni bir uç, durum veya hata kodu gerekiyorsa Auth'ta değişiklik isteme: `docs-auth/verilen-cevaplar/<servis>/` içine talep yaz.

## 5. Komut özeti

| Ne yapıyor | Komut |
| --- | --- |
| Eksik anahtarları üret | `node ops/generate-auth-dev-keys.mjs` |
| ENV'i Docker'dan önce doğrula | `node ops/auth-env-check.mjs` |
| TOTP şifreleme anahtarını döndür | `node ops/generate-auth-dev-keys.mjs --rotate-totp` |
| Doğrulama (HMAC) anahtarını döndür | `node ops/generate-auth-dev-keys.mjs --rotate-hmac` |
| Aktif profili seç / göster | `.\ops\env.cmd use dev` · `.\ops\env.cmd status` |
| Auth + Gateway uçtan uca duman testi | `node ops/auth-smoke.mjs` (Docker ve güncel jar'lar gerekir) |

Anahtar rotasyonunun ayrıntısı: [`anahtar-rotasyonu.md`](anahtar-rotasyonu.md). Yeni özelliklerin listesi: [`verilen-cevaplar/bilgilendirme-2026-11-05-auth-degisiklikleri.md`](verilen-cevaplar/bilgilendirme-2026-11-05-auth-degisiklikleri.md).
