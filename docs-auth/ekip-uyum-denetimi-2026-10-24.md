# Auth ↔ Servisler Uyum Denetimi (2026-10-24)

> Kapsam: `origin/main` ve açık dallardaki servis kodunun, Auth sözleşmesine (`AUTH_SERVICE_ENTEGRASYON_REHBERI.md`,
> `servis-sozlesmeleri.md`) uyumu. **Yöntem: statik kod okuma**; servisler çalıştırılmadı. Sözleşmenin sahibi Auth'tur,
> servisler Auth'a uyar; Auth hiçbir servise göre gevşetilmez.

## Özet

| Servis | Sahip | JWT doğrulama | Silme ucu (`DELETE /internal/v1/accounts/{id}`) |
| --- | --- | --- | --- |
| Game | Hamza | **Uygun** (sözleşmeden sıkı) | **Var** (main, 2026-10-24 03:26; `sub` tam eşleşme + `account_id` denetimi dahil) |
| Social | Umut | **Uygun** (iki ayrı decoder); iki sıkılaştırma açık | **Var** |
| Hardware | Alper | **Uygun** (2026-10-24 sonrası main'de; JWKS, RS256, `at+jwt`, roller) | **Var** (V8, gerçek Auth ile denenmiş) |
| Community | Nisa | Geçici decoder her token'ı reddeder; yazma uçları 401 | Yok |
| library, review | Hamza | Doğrulayıcı yok (iskelet); servis token doğrulayıcısı var | **Var** (veri yok → idempotent 204) |
| user, mail, admin, notification, content, event, lfp, messaging, performance, recommendation | Kerem / Nisa / Umut / Alper | Kod yok (iskelet) | Yok |

Silme hedeflerinden (16) beşi ucu yazmış (Social, Game, Library, Review, Hardware); kalan 11'inde Auth görevi `404` ile PENDING kalır, hesap `DELETED` olamaz.
Auth tarafında kişisel veri artık bekleme süresi bitince hemen anonimleşir (bkz. `acik-guvenlik-borclari.md` S1).

## Umut — social, messaging, lfp

Social en uyumlu servis: `at+jwt` ve `svc+jwt` için ayrı decoder, `aud=social-service`, `scope=account:erase`, 60 sn ömür kuralı,
`iat` denetimi. İki açık nokta (Auth bunu 2026-10-23'te bildirmişti, hâlâ açık):

1. **`sub` tam eşleşme:** `JwtConfig.serviceTokenValidator` yalnız `svc:` önekine bakıyor; sözleşme silme ucunda **tam**
   `svc:auth-service` ister (silme yetkisi yalnız Auth'tadır).
2. **`account_id` ↔ yol:** `AccountErasureController.erase(accountId)` token'daki `account_id` claim'ini yoldaki `{accountId}` ile
   karşılaştırmıyor. Eşleşmezse `403` dönmeli (çalınan token başka hesabı sildiremesin).

Social commit mesajındaki "Auth tarafında adaptör ve svc+jwt üreticisi yok" artık geçerli değil: `ServiceTokenIssuer` ve
`HttpAccountDataEraser` Auth main'inde. messaging ve lfp: JWT doğrulayıcı ve silme ucu henüz yok; Social'ın `JwtConfig` yapısı örnek alınabilir.

## Alper — hardware, recommendation, performance (+ notification/admin sahipliği)

- **Hardware (güncelleme 2026-10-26):** Alper JWT doğrulayıcıyı ve hesap silme ucunu main'e eklemiş. İlk denetimdeki "doğrulayıcı yok" bulgusu KAPANDI. Alper'in Auth'a yönelik 4 talebi ve cevaplarım: `verilen-cevaplar/hardware-service/`. `recommendation` ve `performance` iskelet.
- **Gateway ve CODEOWNERS:** `chore/security` dalındaki `CODEOWNERS`'ta `api-gateway`, `admin-service`, `notification-service` sahibi yok.
  Gateway güven sınırıdır; sahipsiz bırakılmamalı.

## Nisa — community, content, event

- `community-service` dalı (`feature/community-service`, `main`'e birleşmemiş) `SecurityConfig.jwtDecoder()` ile her token'ı reddediyor.
  Gerçek decoder bağlanırken dikkat:
  1. `.jwt(Customizer.withDefaults())` `typ`, `iss`, `aud` denetlemez; rehberdeki validatörler (`JwtTypeValidator("at+jwt")`,
     `JwtIssuerValidator`, `aud`) eklenmeli, yalnız RS256 kabul edilmeli.
  2. Spring'in varsayılan authority dönüştürücüsü `scope`/`scp` okur, **`roles` değil**; `roles` claim'i `ROLE_` önekiyle
     eşlenmeli (rehber §3.4).
- Dal, Auth'un main'e birleşmesinden önceki bir tabana dayanıyor (`auth-service` kaynak dosyası 23 / 217); main merge edilmeli.
- Silme ucu yok. content ve event iskelet.

## Hamza — game, library, review

- **Game** doğrulayıcısı sözleşmeye uygun, hatta `kid` zorunluluğu ve UUID `sub` ile daha sıkı. Gerçek Auth ile 44 kontrollük akış koşturulmuş.
- **Silme ucu yazıldı (2026-10-24, main):** Game, Library ve Review `DELETE /internal/v1/accounts/{accountId}` uçlarını sözleşmeye uygun ekledi (`svc+jwt`, `sub=svc:auth-service` tam eşleşme, `account_id` = yol, `scope`, `aud`). Game audit kayıtları nil UUID ile anonimleşiyor (V171).
- Doğrulanmadı (tahmin): `ltz.rate-limit.trusted-proxies` bilerek boş; Gateway arkasında tüm anonim istekler tek IP kovasına düşebilir.
- library ve review iskelet.

## Ortak not: token ömrü ve iptal

Access token varsayılan ömrü 300 sn'den **60 sn**'ye indi. Askıya alınan/yasaklanan hesabın diğer servislerde görünürlüğü en fazla 60 sn;
servislerin ek bir şey yapması gerekmiyor. Refresh Auth'ta kalır.
