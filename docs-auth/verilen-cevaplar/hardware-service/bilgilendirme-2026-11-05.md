# HARDWARE-SERVICE — BİLGİLENDİRME (2026-11-05)

**Gönderen:** Kerem (auth-service) · **Branch:** `feature/hardware-service` · **Durum:** BİLGİ

Ayrıntılı ve herkes için ortak metin: [../bilgilendirme-2026-11-05-auth-degisiklikleri.md](../bilgilendirme-2026-11-05-auth-degisiklikleri.md). Bu dosya yalnız bu servisi ilgilendiren kısmı özetler.

## NE DEĞİŞTİ (ÖZET)

- Servis token ve kullanıcı JWT sözleşmesi **değişmedi.** Auth sınıflarını kullanan testlerin imzaları bozulmadı.
- Yeni: Google hesap bağlama, Steam bağlı hesapta zorunlu 2FA kurulumu, parolasız hesap için parola belirleme. Kaldırıldı: `POST /api/v1/auth/oauth/steam/unlink` (bağlanan kimlik artık kaldırılamaz).

## BU SERVİS İÇİN

**Bilgi**

- Cihaz bağlama (`POST /api/v1/auth/devices`) parola ister; yalnız Google/Steam ile açılmış kullanıcı `409 PASSWORD_REQUIRED` alıyordu. Artık kullanıcı `password/set` ile parola belirleyip cihazı bağlayabilir. Hardware tarafında kod değişikliği gerekmez.
- Steam bağlı hesaplarda girişte 2FA kurulumu zorunlu olabilir; masaüstü istemcisi `MFA_ENROLLMENT_REQUIRED` durumunu ele almalı (Frontend ile aynı akış).
- Durum: **BİLGİ**

