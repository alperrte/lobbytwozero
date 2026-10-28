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
- [ ] Engel ve gizlilik (LFP-17): Social'ın `POST /internal/v1/blocks/check` ucu henüz yazılmadı. Auth tarafı hazır: `{clientId: "lfp-service", clientSecret, audience: "social-service"}` ile 60 sn'lik `blocks:check` token'ı al. Ortama `AUTH_LFP_SERVICE_CLIENT_SECRET` ver (`node ops/generate-auth-dev-keys.mjs` üretir)
- [ ] Oyun doğrulama (LFP-18): game-service'i hangi kimlikle çağıracağını belirle. Servis token'ı mı, kullanıcı token'ı iletmek mi? Game'in kamuya açık okuma uçları token istemeyebilir, ona bak. Servis token'ı lazımsa kendi klasörüne `talep-NN` yaz

## AUTH'TA BEKLEYEN

- `lfp-service` istemcisi ve `blocks:check`: **Auth'ta hazır** (2026-10-29)
- LFP → messaging sözleşmesi: tanımsız (iki servis de iskelet)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/lfp-service/`, `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`
