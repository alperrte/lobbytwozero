# Auth entegrasyonu 04

Tarih: 2026-10-31 · Kapsam: Faz 4 (PERF-14/15/16/19/21)

## Kullanılan Auth yüzeyi

| Uç / belirteç | Kullanım | Durum |
| --- | --- | --- |
| Kullanıcı token'ı (`at+jwt`, JWKS ile doğrulanır) | `GET /sessions`, `/sessions/{id}/samples|stats|series`, `/stats` | **Gerçek Auth** — testler Auth'un üretim `AccessTokenIssuer`/JWKS koduyla imzalar ve doğrular |
| Servis token'ı (`svc+jwt`) | Hardware/Recommendation sözleşme uçları | **BLOCKED** (REQ-04-A / REQ-01-C) |
| Cihaz token'ı (`dev+jwt`) | Bu fazın uçlarında kabul edilmez (401) | Test edildi |

## Doğrulananlar (testle)

- Kullanıcı token'ı olmadan, cihaz token'ı ve servis token'ıyla kullanıcı okuma uçları → 401.
- Başkasının ve olmayan oturum → aynı 404 (`PERFORMANCE_NOT_FOUND`); yanıt gövdesi (`detail`, `status`, `code`) birebir aynıdır.
- Sözleşme uçları: yanlış servis konusu, yanlış/başka uçun kapsamı, başka hesaba bağlı `account_id`, yanlış audience, kullanıcı ve cihaz token'ı, kimliksiz, POST → reddedilir.
- Gerçek Auth `ServiceTokenIssuer` ile üretilen token (`sub=svc:auth-service`, kapsam `summary:read` ya da `devices:verify`) iki sözleşme ucunda **403** alır: Performance, konu doğrulamasını gevşetmiyor.
- İstenen kapsam adları (`summary:read`, `reference:read`) Auth'un kapsam desenini geçer (gerçek issuer ile token üretilebildi).

## BLOCKED / doğrulanamayanlar

- Hardware ve Recommendation'ın gerçek servis token'ı alıp çağırması: Auth bugün `sub=svc:auth-service` dışında konu üretmiyor (REQ-04-A). Testler yerel imzalı sözleşme token'ı kullanır ve gerçek entegrasyon olarak sunulmaz.
- Hardware/Recommendation tüketici kodu yok/değiştirilmedi; sözleşme yalnız Performance tarafında doğrulandı.

## Çıkarım

Auth tarafında değişiklik gelince yapılacak tek Performance işi: gerçek token'larla uçtan uca testin eklenmesi ve gerekirse `sub`/kapsam adlarının Auth'un seçtiği adlarla hizalanması (`PerformanceInternalController` sabitleri ve güvenlik eşleşmeleri).
