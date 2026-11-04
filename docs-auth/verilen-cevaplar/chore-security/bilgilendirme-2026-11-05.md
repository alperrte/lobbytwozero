# CHORE-SECURITY — BİLGİLENDİRME (2026-11-05)

**Gönderen:** Kerem (auth-service) · **Branch:** `chore/security` · **Durum:** AÇIK

Ayrıntılı ve herkes için ortak metin: [../bilgilendirme-2026-11-05-auth-degisiklikleri.md](../bilgilendirme-2026-11-05-auth-degisiklikleri.md). Bu dosya yalnız bu servisi ilgilendiren kısmı özetler.

## NE DEĞİŞTİ (ÖZET)

- Servis token ve kullanıcı JWT sözleşmesi **değişmedi.** Auth sınıflarını kullanan testlerin imzaları bozulmadı.
- Yeni: Google hesap bağlama, Steam bağlı hesapta zorunlu 2FA kurulumu, parolasız hesap için parola belirleme. Kaldırıldı: `POST /api/v1/auth/oauth/steam/unlink` (bağlanan kimlik artık kaldırılamaz).

## BU SERVİS İÇİN

**İstekler (Alper'in alanı)**

1. **Bağımlılık yamaları (B13).** Auth'ta Tomcat 11.0.25 ve Jackson 2.21.7/3.1.7 yamaları yalnız `auth-service/pom.xml`'de. Diğer 17 servis eski, CVE'li sürümlerde. Öneri: bu sürümleri ortak parent pom'a (`dependencyManagement`) taşıyalım; tek PR, servis kodu değişmez. Dependabot PR'ları (#49, #50, #51) bunu tek tek açıyor, parent pom ile toplu yönetmek daha az gürültü ve daha az unutulan servis demek.
2. **Rate limit (B1).** Gateway'deki IP sınırı bellek içinde ve her Gateway örneğinde ayrı sayılıyor; çok örnekte sınır örnek başına işliyor. Öneri (katmanlı savunma): Gateway genel IP sınırı kalsın; her servis kendi yazma uçlarında hesap/servis kimliği başına kendi sınırını uygulasın; çok örnekli kurulum netleşince sayaç ortak bir yere (ör. Redis) taşınsın. Auth tarafında kendi deneme sınırı ve Gateway'den güvenilir IP kullanımı Kerem'de.
3. **İç ağ şifrelemesi (B18, bilgi).** Servisler birbirine düz `http` ile konuşuyor; ağı dinleyen biri 30 sn'lik servis token'ını aynı servis ve hesap için yeniden oynatabilir. Auth `prod` profilinde `https` zorunluluğunu getirdi; servislerin birbirini sertifikayla doğrulaması (mTLS) deploy mimarisine bağlı, deploy planı netleşince birlikte karar verelim.

Durum: **AÇIK** (1 ve 2 Alper'de; 3 bilgi)
