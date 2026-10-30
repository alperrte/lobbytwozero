# LIBRARY-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Hamza · **Kod durumu:** Silme ucu ve JWKS ayarı var, iş mantığı iskelet · **Güncelleme:** 2026-10-29

## ÖZET

Library'nin Auth ile bağı iki parça: kullanıcı token'ı ve hesap silme. Silme ucu `docs/api.md`'ye göre yazılı (kişisel tablo olmadığı için `204` ile no-op). Auth'tan başka bir şey istemiyor.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT, `sub` ve `roles`: `README.md` bölüm 1. `userId` yalnız `sub`'dan gelir, gövdeden alınmaz
- Silme ucu sözleşmesi: `README.md` bölüm 2

## SENİN YAPACAKLARIN

- [ ] İlk kişisel tabloyla (kütüphane, istek listesi) birlikte silme mantığını yaz (şu an `db/migration` boş)
- [ ] `main`'deki `contextLoads` testi `AUTH_SERVICE_URL` olmadan kırık. `chore/security` (#16) ile `pom.xml`'e eklenen CI ayarı bunu çözüyor, `main`'i dalına al
- [ ] Silme ucu `sub` ve `account_id` denetliyor mu bir kez daha bak (game-service `AccountErasureController` örnek)

- [ ] LIB-11: User'dan erişim kararı. Token: `{clientId: "library-service", clientSecret, audience: "user-service"}` → `scope=access:decide`. Ortam `AUTH_LIBRARY_SERVICE_CLIENT_SECRET`. Karar alınamazsa **erişilemez** (fail-closed)
- [ ] LIB-14: sinyal ucu. Kabul edilen token `aud=library-service`, `sub` tam `svc:recommendation-service`, `scope=signals:read`, `account_id` istekteki hesapla eşit; sonra User izni
- [ ] Dev'de `JWT_ACCESS_TTL_SECONDS=60` ile iptal testini tekrarla (900 sn prod'u temsil etmez)

## AUTH'TA BEKLEYEN

- Yok (2026-10-30). Kararlar ve sözleşme: `verilen-cevaplar/library-service/talep-01-servisler-arasi-yetki-iptal-ve-d4.md`. Gizlilik kararı User'ın, Auth'un değil

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/library-service/`
