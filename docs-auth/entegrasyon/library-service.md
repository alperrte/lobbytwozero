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

## AUTH'TA BEKLEYEN

- Yok. Gizlilik ve profil bilgisi (LIB-10/11) User Service'e bağlı, Auth'a değil

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/library-service/`
