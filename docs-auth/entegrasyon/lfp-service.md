# LFP-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Umut · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

Henüz kod yok. Auth'tan gereken: kullanıcı token'ı ve hesap silme. Engel ve gizlilik kontrolü için Social'ı çağıracaksın, onun için Auth'ta bir istemci kaydı lazım. Social'ın ucu yazılınca eklenecek.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT, `sub` (ilan sahipliği), `roles`: `README.md` bölüm 1
- Silme ucu sözleşmesi: `README.md` bölüm 2. LFP silme hedeflerinde
- Servis token mekanizması: `README.md` bölüm 3

## SENİN YAPACAKLARIN

- [ ] JWT doğrulayıcı (JWKS), ilan güncelleme ve kapatma `sub` sahipliğiyle
- [ ] `DELETE /internal/v1/accounts/{accountId}`. Yazılmadıkça LFP görevi `PENDING`
- [ ] Engel ve gizlilik (LFP-17): Social'ın `POST /internal/v1/blocks/check` ucu yazılınca bana yaz: `lfp-service` → `social-service`, `blocks:check` çifti eklenecek, değişken `AUTH_LFP_SERVICE_CLIENT_SECRET`
- [ ] Oyun doğrulama (LFP-18): game-service'i hangi kimlikle çağıracağını belirle. Servis token'ı mı, kullanıcı token'ı iletmek mi? Game'in kamuya açık okuma uçları token istemeyebilir, ona bak. Servis token'ı lazımsa kendi klasörüne `talep-NN` yaz

## AUTH'TA BEKLEYEN

- `lfp-service` istemcisi ve `blocks:check`: Social ucu yazılınca eklenecek
- LFP → messaging sözleşmesi: tanımsız (iki servis de iskelet)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/lfp-service/`, `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`
