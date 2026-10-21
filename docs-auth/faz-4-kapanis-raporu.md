# LTZ-KER-AUTH-04 — Faz 4 Kapanış Raporu (Şifre Değiştir ve Şifremi Unuttum)

> Tarih: 2026-10-21 · Sorumlu: Kerem · Kapsam: AUTH-09, AUTH-10

## 1. Durum

| Görev | Durum | Not |
| --- | --- | --- |
| AUTH-09 Parola değiştirme | IMPLEMENTED | Mevcut parola + Argon2id, politika (12–128, dönüştürme yok), tekrar kontrolü, tüm oturumlar iptal |
| AUTH-10 Şifremi unuttum | IMPLEMENTED (development'ta Mailpit) / production e-posta teslimi **PENDING** | 256 bit tek kullanımlık token, özet saklama, numaralandırma yok |

## 2. Tasarım

- **Değiştirme:** CSRF + kimlik zorunlu; mevcut parola denemesi hesap bazında 15 dk'da 5 ile sınırlı. Başarıda tüm oturumlar iptal, `token_version` artar, bekleyen sıfırlama token'ları geçersiz, cookie'ler silinir (yeniden giriş). Eşzamanlı iki değişiklikten biri başarılı olur (hesap satır kilidi + doğrulanan hash'in yeniden denetimi).
- **Sıfırlama isteği:** yanıt hesaptan bağımsız (`202 CHECK_EMAIL`); sınır (429) hesap varlığından bağımsız; uygun olmayan hesaba (doğrulanmamış, askıda, parolasız) e-posta yok; e-posta istek iş parçacığı dışında gönderilir. Bekleme (60 sn) sessizdir; yeni token eskisini geçersiz kılar (DB'de hesap başına tek aktif token).
- **Sıfırlama:** token satır kilidiyle tüketilir (4 eşzamanlı istekten 1 başarılı); çöp token için Argon2 hesaplanmaz; başarıda tüm oturumlar iptal, otomatik giriş **yok**, 2FA **kapatılmaz**.
- **Bağlantı:** `{FRONTEND_PUBLIC_URL}/auth/reset-password#token=…` (fragment). prod profilinde https zorunlu.
- **Mail:** yeni `PasswordResetMailPort` (MAIL-02); `VerificationCodeMailPort` ve Faz 1–3 akışları değişmedi. Dev'de Mailpit adaptörü, aksi halde teslimi reddeden adaptör (token `DELIVERY_FAILED`).

## 3. Migration ve ENV

- `password_reset_tokens` ve `auth_sessions.revoke_reason` CHECK'i (`PASSWORD_CHANGED`, `PASSWORD_RESET`) `V62` içinde. V65–V70 boş.
- Yeni ENV anahtarı **yok** (`FRONTEND_PUBLIC_URL` mevcut). Yeni bağımlılık yok.

## 4. Açık maddeler

- **Zamanlama:** bilinmeyen hesapta DB'ye token yazılmaz; aradaki fark milisaniye mertebesindedir (token üretimi/özeti dummy olarak yapılır, e-posta zaten asenkron). Tam eşitleme için sabit gecikme eklenmedi.
- **Sıfırlama isteği kilidi:** aynı e-postaya 15 dk'da 5 istek sonrası kilit, başkasının sıfırlama talebini geçici engelleyebilir (hesap varlığı sızmaz). IP limiti güvenilir IP (K8) gelince eklenecek.
- **Parolasız hesaplar** (yalnız Google/Steam) parola belirleyemez; parola ekleme akışı kapsam dışı.
- **Bildirim e-postası yok:** parola değişti/sıfırlandı bilgilendirmesi (MAIL-03) gönderilmiyor.
- **Önceki fazlardan açık:** AUTH-13 IP limiti (K8), D3 (diğer servislerde iptal ≤5 dk), Gateway CORS ve cookie → Bearer, tablo temizlik işleri, hesap bağlama ucu, production Mail Service (PENDING).
