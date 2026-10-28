# MESSAGING-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Umut · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

Henüz kod yok. Auth'tan gereken: kullanıcı token'ı ve hesap silme. Engel kontrolü için Social'ı çağırmak gerekecek ve bunun için Auth'ta bir istemci kaydı lazım. O kayıt Social'ın ucu yazılınca eklenecek.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT, `sub`, `roles`: `README.md` bölüm 1
- Silme ucu sözleşmesi: `README.md` bölüm 2. Messaging silme hedeflerinde
- Servis token mekanizması: `README.md` bölüm 3

## SENİN YAPACAKLARIN

- [ ] JWT doğrulayıcı (JWKS)
- [ ] `DELETE /internal/v1/accounts/{accountId}`. Yazılmadıkça Messaging görevi `PENDING`, hesap silme tamamlanmaz
- [ ] Engel kontrolü (MSG-15): Social'ın `POST /internal/v1/blocks/check` ucunu çağıracaksın (`docs/api.md` Social, tasarım aşamasında). Önce Social ucunu yazsın, sonra bana yaz: `messaging-service` → `social-service`, kapsam `blocks:check` çifti eklenecek, gizli anahtar değişkeni `AUTH_MESSAGING_SERVICE_CLIENT_SECRET` olacak
- [ ] Yeni mesaj bildirimi (MSG-16) için notification sözleşmesi: yok. İhtiyacı kendi klasörüne `talep-NN` yaz

## AUTH'TA BEKLEYEN

- `messaging-service` istemcisi ve `blocks:check` kapsamı: Social ucu yazılınca eklenecek
- Kullanıcı adı, avatar, çevrimiçi durumu: Auth'ta yok ve olmayacak (User Service)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/messaging-service/`, `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`
