# EVENT-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Nisa · **Kod durumu:** İskelet (yalnız `EventServiceApplication`) · **Güncelleme:** 2026-10-29

## ÖZET

Henüz kod yok. Auth'tan gereken: kullanıcı token'ı, roller ve hesap silme. Katılım cezası Event'in kendi veritabanında tutulur, Auth'tan hesap durumu beklenmiyor.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT ve roller: `README.md` bölüm 1. `pom.xml`'de `oauth2-resource-server` var, bağlanmamış
- Silme ucu sözleşmesi: `README.md` bölüm 2. Event silme hedeflerinde (katılım kayıtları, etkinlikler)

## SENİN YAPACAKLARIN

- [ ] **Rol kararı:** "yetkili kullanıcı" (EVT-01, EVT-14, EVT-15) hangi rol? Auth rolleri `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`. Önerim: etkinlik oluşturma `ADMIN` ve `MODERATOR`, katılım `PLAYER` ve üstü. Kararı yaz, Auth'ta bir şey değişmez
- [ ] JWT doğrulayıcı (JWKS), `ROLE_` eşlemesi, sahiplik `sub` ile
- [ ] `DELETE /internal/v1/accounts/{accountId}`. Yazılmadıkça Event görevi `PENDING`
- [ ] Banlı katılımcı davranışı: token en fazla 90 sn yaşar, sonra giriş kapanır. Bunun ötesinde Auth'tan bir şey gerekmiyor
- [ ] Bildirim, duyuru ve e-posta çağrıları (EVT-21, 22, 24) için kendi klasörüne `talep-NN` yaz: hangi hedef, hangi olay. Şu an Auth'ta bu çiftler tanımlı değil

## AUTH'TA BEKLEYEN

- Event için servis istemcisi ve `events:ingest` çiftleri: tanımsız

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/event-service/`
