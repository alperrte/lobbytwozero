# NOTIFICATION-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Ortak · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

Notification'ın Auth'tan doğrudan beklediği bir uç veya olay yazılı değil. İki bağ var: Community olay gönderecek, hesap silmede Auth Notification'ı da çağıracak.

## AUTH'TAN HAZIR OLANLAR

- Silme ucu sözleşmesi: `README.md` bölüm 2. Notification silme hedeflerinde
- Servis token'ı (Community → Notification): `community-service` bu hedef için `events:ingest` token'ı alabilir

## SENİN YAPACAKLARIN

- [ ] JWT doğrulayıcı (JWKS), kullanıcının kendi bildirimlerini okuması için
- [ ] Olay alıcı uç (örn. `POST /internal/events`): `README.md` bölüm 4 kuralları: `aud=notification-service`, `typ=svc+jwt`, `scope=events:ingest` (yetki `SCOPE_events:ingest`), `sub=svc:community-service`. Community şu olayları gönderecek: `CommentCreated`, `PostLiked`, `PostReposted`
- [ ] `DELETE /internal/v1/accounts/{accountId}`: kullanıcının bildirimleri silinir. Yazılmadıkça Notification görevi `PENDING`

## AUTH'TA BEKLEYEN

- Karar: güvenlik ve hesap durumu olayları (parola değişti, askı, yasak) uygulama içi bildirim olarak da istenecek mi? İstenirse Auth → Notification çifti ve olay sözleşmesi yazılır. Şu an yok
- Social ve Messaging'in Notification'a olay göndermesi: çift tanımlı değil, ihtiyaç yazılırsa karar

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/notification-service/`
