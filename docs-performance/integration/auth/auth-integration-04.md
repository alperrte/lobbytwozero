# Auth entegrasyonu 04

Güncelleme: 2026-11-06 · Kapsam: Faz 4 (PERF-14/15/16/19/21)

## Kullanılan Auth yüzeyi

| Uç / belirteç | Kullanım | Durum |
| --- | --- | --- |
| Kullanıcı token'ı (`at+jwt`, JWKS ile doğrulanır) | `GET /sessions`, `/sessions/{id}/samples|stats|series`, `/stats` | **Kaynak sözleşme testi** — Auth'un üretim `AccessTokenIssuer`/JWKS kodu kullanılır; çalışan Auth ile bu okuma akışları E2E denenmedi |
| Servis token'ı (`svc+jwt`) | Hardware/Recommendation sözleşme uçları | **REQ-04-A KAPANDI** — hesaba bağlı kapsamlar eklendi; gerçek Auth token'larıyla iki okuma ucu 200, hesap/servis/yetki retleri PASS. [Kabul/kalite kanıtı](auth-integration-06.md) |
| Cihaz token'ı (`dev+jwt`) | Bu fazın uçlarında kabul edilmez (401) | Test edildi |

## Doğrulananlar (testle)

- Kullanıcı token'ı olmadan, cihaz token'ı ve servis token'ıyla kullanıcı okuma uçları → 401.
- Başkasının ve olmayan oturum → aynı 404 (`PERFORMANCE_NOT_FOUND`); yanıt gövdesi (`detail`, `status`, `code`) birebir aynıdır.
- Sözleşme uçları: yanlış servis konusu, yanlış/başka uçun kapsamı, başka hesaba bağlı `account_id`, yanlış audience, kullanıcı ve cihaz token'ı, kimliksiz, POST → reddedilir.
- Olumlu Hardware/Recommendation testleri Auth'un gerçek `ServiceClientTokenIssuer` çıktısını kullanır. Hardware `devices:verify summary:read`, Recommendation yalnız `reference:read` taşır; konu, kapsam ve hesap bağı korunur. Yalnız `devices:verify` kapsamıyla imzalanmış dar negatif test token'ı iki okuma ucunda **403** alır.
- Auth'un kendi çıkış `ServiceTokenIssuer` token'ı doğru okuma kapsamını taşısa da `sub=svc:auth-service` olduğu için reddedilir. Bu, istemci token üretiminin bozuk olduğu anlamına gelmez.
- İstenen kapsam adları (`summary:read`, `reference:read`) Auth'un kapsam desenini geçer (gerçek issuer ile token üretilebildi).

## REQ-04-A uygulaması ve kalan tüketici işleri

- Kullanıcının bildirdiği Kerem onayı kapsamında iki kapsam `TokenClaims`, `ALLOWED_TARGET_SCOPES` ve `ACCOUNT_BOUND_TARGET_SCOPES` listelerine eklendi. `application.properties` Hardware → Performance hedefi genişletildi, Recommendation → Performance hedefi eklendi. Auth mevcut servis token ucunu kullanır; yeni ENV/migration yoktur.
- Eksik/geçersiz/olmayan/pasif hedef hesap, yanlış istemci sırrı ve yetkisiz istemci retleri Auth testlerine eklendi. Mevcut token üretici/servis/controller imzaları korundu; Hardware token'ı hedefin iki kapsamını birlikte taşır.
- `devices:verify` çifti eklendiği için Hardware → Performance cihaz doğrulaması bu engelden ayrıdır; bunun doğrulaması [auth-integration-01.md](auth-integration-01.md) içindedir.
- Hardware/Recommendation tüketici kodu yok/değiştirilmedi; sözleşme yalnız Performance tarafında doğrulandı.

## Çıkarım

Performance'ın alıcı yetki denetimleri korundu. Auth izin genişletmesi ve gerçek istemci-credential token'ıyla iki ucun kabulü [auth-integration-06.md](auth-integration-06.md) içinde izlenir. Hardware ve Recommendation üretim tüketici istemcileri bu göreve dahil değildir; onların geliştirilmesi artık eksik Auth iznine bağlı değildir.
