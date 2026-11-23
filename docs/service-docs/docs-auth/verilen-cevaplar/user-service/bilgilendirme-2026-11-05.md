# USER-SERVICE — BİLGİLENDİRME (2026-11-05)

**Gönderen:** Kerem (auth-service) · **Branch:** `feature/user-service` · **Durum:** AÇIK

Ayrıntılı ve herkes için ortak metin: [../bilgilendirme-2026-11-05-auth-degisiklikleri.md](../bilgilendirme-2026-11-05-auth-degisiklikleri.md). Bu dosya yalnız bu servisi ilgilendiren kısmı özetler.

## NE DEĞİŞTİ (ÖZET)

- Servis token ve kullanıcı JWT sözleşmesi **değişmedi.** Auth sınıflarını kullanan testlerin imzaları bozulmadı.
- Yeni: Google hesap bağlama, Steam bağlı hesapta zorunlu 2FA kurulumu, parolasız hesap için parola belirleme. Kaldırıldı: `POST /api/v1/auth/oauth/steam/unlink` (bağlanan kimlik artık kaldırılamaz).

## BU SERVİS İÇİN

**Bilgi ve açık istek**

- `ExternalIdentityLinked` olayında `provider` artık `STEAM` veya `GOOGLE` olabilir (`providerSubject`: SteamID64 veya Google `sub`).
- `ExternalIdentityUnlinked` olayı şu an **üretilmiyor** (kullanıcı kimliği kaldıramaz). Kaldırma yönetici yoluyla planlı; o gün olay geri gelir.
- Açık istek: `AccountEmailChanged` olayını tüketme (B19). Profil servisi e-posta tutuyorsa bu olay olmadan eskir.
- Durum: **AÇIK** (olay tüketimi User'da yapılacak)
