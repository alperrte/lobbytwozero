# CONTENT-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Nisa · **Kod durumu:** İskelet (yalnız `ContentServiceApplication`) · **Güncelleme:** 2026-10-29

## ÖZET

Henüz kod yok. Auth'tan gereken: kullanıcı token'ı, roller ve hesap silme. Asıl açık, kimin yayın yapabileceği kararı.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT ve roller: `README.md` bölüm 1. `pom.xml`'de `oauth2-resource-server` bağımlılığı var, bağlanmamış
- Silme ucu sözleşmesi: `README.md` bölüm 2. Content silme hedeflerinde (içerik ve dosya deposu temizliği senin tarafında)

## SENİN YAPACAKLARIN

- [ ] **Rol kararı:** CONT-04 (resmi duyuru, yetkili yöneticiler), CONT-05/09 (kayıtlı kullanıcı haber yayımlayabilir), CONT-17 (İçerik Üreticisi). Auth rolleri `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`. Önerim: resmi duyuru `ADMIN` (veya `ADMIN` ve `MODERATOR`), kayıtlı kullanıcı yayını `PLAYER` ve üstü, `CONTENT_CREATOR` ek görünürlük. Kararı yaz, Auth'ta bir şey değişmez
- [ ] JWT doğrulayıcı (JWKS) ve `ROLE_` eşlemesi
- [ ] `DELETE /internal/v1/accounts/{accountId}`. Yazılmadıkça Content görevi `PENDING`
- [ ] Content → notification veya admin çağrısı yapacaksan kendi klasörüne `talep-NN` yaz: hangi hedef, hangi olay, neden. Şu an bu çiftler tanımlı değil

## AUTH'TA BEKLEYEN

- Content için servis istemcisi ve `events:ingest` çiftleri: tanımsız (ihtiyaç yazılınca eklenir)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/content-service/`
