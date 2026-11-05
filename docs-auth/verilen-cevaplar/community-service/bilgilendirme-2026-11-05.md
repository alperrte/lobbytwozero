# COMMUNITY-SERVICE — BİLGİLENDİRME (2026-11-05)

**Gönderen:** Kerem (auth-service) · **Branch:** `feature/community-service` · **Durum:** BİLGİ

Ayrıntılı ve herkes için ortak metin: [../bilgilendirme-2026-11-05-auth-degisiklikleri.md](../bilgilendirme-2026-11-05-auth-degisiklikleri.md). Bu dosya yalnız bu servisi ilgilendiren kısmı özetler.

## NE DEĞİŞTİ (ÖZET)

- Servis token ve kullanıcı JWT sözleşmesi **değişmedi.** Auth sınıflarını kullanan testlerin imzaları bozulmadı.
- Yeni: Google hesap bağlama, Steam bağlı hesapta zorunlu 2FA kurulumu, parolasız hesap için parola belirleme. Kaldırıldı: `POST /api/v1/auth/oauth/steam/unlink` (bağlanan kimlik artık kaldırılamaz).

## BU SERVİS İÇİN

**Bilgi**

- `AUTH_EVENT_TARGETS=community` henüz açılmadı; Community olayları almaya hazır olduğunda haber ver, Kerem dev ortamında açar. Gerçek Auth + Community uçtan uca deneme henüz yapılmadı.
- Durum: **BİLGİ**
