# ADMIN-SERVICE — BİLGİLENDİRME (2026-11-05)

**Gönderen:** Kerem (auth-service) · **Branch:** `feature/admin-service` · **Durum:** AÇIK

Ayrıntılı ve herkes için ortak metin: [../bilgilendirme-2026-11-05-auth-degisiklikleri.md](../bilgilendirme-2026-11-05-auth-degisiklikleri.md). Bu dosya yalnız bu servisi ilgilendiren kısmı özetler.

## NE DEĞİŞTİ (ÖZET)

- Servis token ve kullanıcı JWT sözleşmesi **değişmedi.** Auth sınıflarını kullanan testlerin imzaları bozulmadı.
- Yeni: Google hesap bağlama, Steam bağlı hesapta zorunlu 2FA kurulumu, parolasız hesap için parola belirleme. Kaldırıldı: `POST /api/v1/auth/oauth/steam/unlink` (bağlanan kimlik artık kaldırılamaz).

## BU SERVİS İÇİN

**İstek:** Hesap silme ucu henüz yok.

- Yapılacak: `DELETE /internal/v1/accounts/{accountId}` (servis token'ı: `aud=admin-service`, `sub=svc:auth-service`, `scope=account:erase`, `account_id` = yoldaki hesap). Başarıda ve hesap zaten yoksa `204`; sözleşme `docs-auth/servis-sozlesmeleri.md` §3.1.
- Neden: Auth silme koordinatörü 16 hedeften hepsi onaylamadan hesabı "silindi" saymaz. Bu uç yazılmadıkça görev `PENDING` kalır, hesap silme tamamlanmaz.
- Verisi olmayan servis de ucu açıp `204` döner (Auth kararı, S2).
- Durum: **AÇIK** (bu serviste yapılacak)

Ek: servis→Auth iç uçları (`/internal/v1/accounts/**`) hazırdır; Admin yazılınca `servis-sozlesmeleri.md` §3.4 ile bağlanır.

**Yeni (B8):** `POST /internal/v1/accounts/{id}/status` gövdesine isteğe bağlı `suspendedUntil` (ISO-8601) eklendi; yalnız `SUSPENDED` hedefinde ve 1 dk – 365 gün aralığında geçerlidir, süre dolunca Auth hesabı kendisi açar. Alan verilmezse davranış eskisi gibidir (süresiz askı). Admin arayüzü isterse "şu tarihe kadar askıya al" seçeneği sunabilir. Durum: **BİLGİ**

