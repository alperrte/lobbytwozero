# SOCIAL-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Umut · **Kod durumu:** Aktif · **Güncelleme:** 2026-10-29

## ÖZET

JWT doğrulaması ve silme ucu çalışıyor. **Silme ucunda iki güvenlik açığı hâlâ açık** (kodda doğrulandı): `sub` tam eşleşmiyor ve `account_id` hiç denetlenmiyor. Olay gönderme tarafı Auth'ta hazır, Social'ın kendi tarafında henüz bağlı değil.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT: `README.md` bölüm 1
- Silme: Auth `DELETE /internal/v1/accounts/{id}` çağırır
- Servis token'ı: `social-service` → `community-service`, `events:ingest` (`UserBlocked`, `UserUnblocked`). Gizli anahtar `AUTH_SOCIAL_SERVICE_CLIENT_SECRET`

## SENİN YAPACAKLARIN

- [ ] **Silme ucu, açık 1:** `JwtConfig.java` satır 81-82 `sub` için yalnız `svc:` önekini arıyor. `svc:admin-service` gibi bir token da silme çağırabilir. Doğrusu `sub` **tam** `svc:auth-service` olmalı
- [ ] **Silme ucu, açık 2:** `AccountErasureController` yalnız `erasure.erase(accountId)` çağırıyor, `account_id` claim'ine bakmıyor. Çalınan bir token başka hesabı silebilir. `account_id` yoldaki `accountId` ile aynı olmalı. Doğru örnek: game-service `AccountErasureController` (iki denetimi tek yerde yapıyor). Test de `account_id` üretmeli (`IntegrationTestSupport.validServiceClaims`)
- [ ] **Olay gönderme:** `UnavailableEventPublisher` yerine gerçek yayıncı. Akış: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` `{clientId: "social-service", clientSecret, audience: "community-service"}` ile 60 sn'lik token, sonra `POST {COMMUNITY_SERVICE_URL}/internal/events` `Authorization: Bearer`. Community'nin kapsam adı düzeltmesi bekleniyor (`community-service.md`)
- [ ] **`POST /internal/v1/blocks/check`** (tasarlandı, yazılmadı): messaging ve lfp bunu çağıracak. Auth tarafı hazır: iki istemci `audience: "social-service"` ile `blocks:check` token'ı alıyor (`aud=social-service`, `scope=blocks:check`, `sub=svc:messaging-service` ya da `svc:lfp-service`, 60 sn). Ucu yazarken `aud`, `typ=svc+jwt`, `scope` ve `sub` denetimini kendin yap

## AUTH'TA BEKLEYEN

- `blocks:check` kapsamı ve messaging, lfp istemci kayıtları: **Auth'ta hazır** (2026-10-29), Social ucu bekleniyor
- `user-service` istemcisi (`blocks:check`, `friends:check`): **Auth'ta hazır** (2026-10-30). Arkadaşlık sorgu ucunu sen tasarlarsın; uçta `scope=friends:check` iste. `blocks/check` ucu main'de (PR #35) yalnız `svc:messaging-service` ve `svc:lfp-service` kabul ediyor; listeye `svc:user-service` ekle
- Social → notification ve social → messaging olay teslimi: Auth'ta bu çiftler tanımlı değil, ihtiyaç yazılırsa karar
- Kullanıcı varlık doğrulaması ve profil: user-service yazılınca

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/social-service/talep-01-servis-token-sikilastirma.md`, `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`
