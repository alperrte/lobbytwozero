# USER-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Kerem · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

User Service profil sahibi, Auth kimlik sahibi. Aralarındaki bağ iki olay (hesap oluştu, e-posta değişti) ve hesap silme. Olaylar Auth'ta **planlı, yazılmadı**; user-service başlayınca birlikte yapılacak.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT: `README.md` bölüm 1. Kullanıcı adı ve e-posta token'da yok, Auth'ta saklı
- Hesap silme çağrısını Auth yapar, `DELETE /internal/v1/accounts/{id}` ucunu user-service açar: `README.md` bölüm 2
- Auth'ta e-posta değiştirme ucu var: `POST /api/v1/auth/email/change` ve `/confirm`

## SENİN YAPACAKLARIN

- [ ] JWT doğrulayıcı (JWKS)
- [ ] Silme ucu. Profil ve profil görselleri silinir, kimlik bağı kalmaz. Yazılmadıkça User görevi `PENDING`
- [ ] Karar: kullanıcı adı ve görünen ad **hangi serviste duruyor**. Auth'ta `username` var ve herkese açık. Önerim: Auth kimlik kaynağı, User `userId` ile profil sahibi, kullanıcı adını Auth'tan bir kez alıp kopyalamak yerine her seferinde sormamak için olay ile eşitle

## AUTH'TA BEKLEYEN

- `UserRegistered` olayı (ACTIVE olunca): Auth tarafında yok. Önerim: user-service açık olunca Auth, `events:ingest` ile `user-service`'e gönderir (`svc:auth-service` kimliğiyle), yeni (çağıran, hedef) çifti eklenir. Karar user-service başlarken
- E-posta değişti olayı (borç B19): aynı yol
- `AccountStatusChanged` (D3): yok
- USER-07 (Steam ve Discord bağlantısı): Auth'ta Steam ve Google girişi var (kimlik kanıtı), Discord yok. Auth'ın bağlı hesap bilgisini User'a vermesi için sözleşme yazılı değil

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/user-service/`
