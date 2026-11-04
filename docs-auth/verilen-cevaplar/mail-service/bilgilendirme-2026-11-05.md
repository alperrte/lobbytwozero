# MAIL-SERVICE — BİLGİLENDİRME (2026-11-05)

**Gönderen:** Kerem (auth-service) · **Branch:** `feature/mail-service` · **Durum:** BİLGİ

Ayrıntılı ve herkes için ortak metin: [../bilgilendirme-2026-11-05-auth-degisiklikleri.md](../bilgilendirme-2026-11-05-auth-degisiklikleri.md). Bu dosya yalnız bu servisi ilgilendiren kısmı özetler.

## NE DEĞİŞTİ (ÖZET)

- Servis token ve kullanıcı JWT sözleşmesi **değişmedi.** Auth sınıflarını kullanan testlerin imzaları bozulmadı.
- Yeni: Google hesap bağlama, Steam bağlı hesapta zorunlu 2FA kurulumu, parolasız hesap için parola belirleme. Kaldırıldı: `POST /api/v1/auth/oauth/steam/unlink` (bağlanan kimlik artık kaldırılamaz).

## BU SERVİS İÇİN

**Bilgi**

- Parola belirleme kodu mevcut `POST /internal/v1/mail/verification-codes` ucunu kullanır; sözleşme değişmedi, yeni uç yok. E-posta metni genel "doğrulama kodu" şablonudur. İstenirse "parola belirleme" için ayrı bir şablon türü eklenebilir (isteğe bağlı; Auth tarafı bunu gerektirmez).
- `WELCOME` ve `SUSPICIOUS_LOGIN` şablonları için Auth tetikleyicisi hâlâ yok; Auth çıkmadan Mail tarafı uç açmaz.
- Üretim SMTP sağlayıcısı kararı Kerem'dedir.
- Durum: **BİLGİ**

