# GAME-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Hamza · **Kod durumu:** Aktif · **Güncelleme:** 2026-11-12 (aşağıdaki "Senin yapacakların" maddeleri koda karşı doğrulanıp kapatıldı)

## ÖZET

Game, Auth sözleşmesine en iyi uyan servis: kullanıcı JWT'si, servis token'lı silme ucu ve JWKS yaşam döngüsü yazılı ve denenmiş. Auth'tan başka bir şey istemiyor.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT: JWKS, `at+jwt`, `roles` (`ADMIN`, `MODERATOR`, `GAME_DEVELOPER`, `PLAYER`), `sub=userId`
- Hesap silme ucu: `AccountErasureController` hem `sub` tam eşleşmesini hem `account_id`'yi denetliyor. Diğer servisler için örnek budur
- Oyun bazlı geliştirici yetkisi Auth'tan değil, Game'in kendi veritabanından gelir (Auth'ta oyun bazlı rol yok, olmayacak)

## SENİN YAPACAKLARIN

- [x] **Yapıldı (2026-11-12 doğrulandı).** `docs-Game Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md` access ömrünü 60 sn olarak yazıyor ("Güncel Auth varsayılan access ömrü 60 saniye", token kuralı en çok 60 sn); kalan "yaklaşık 5 dakika" ifadesi access ömrü değil Nimbus JWKS önbelleğidir.
- [x] **Yapıldı (2026-11-12 doğrulandı).** `chore/security` Game dalına alındı (`HEAD`'e birleşik). Game'de `.*` import'ı yok (0), `pre-push full dev` Checkstyle/PMD yeni ihlal 0 ve `SAFE TO COMMIT`.
- [x] **Yapıldı (2026-11-12 doğrulandı).** JWKS `HttpClient` yaşam döngüsü commit'li (`168759c5`, 2026-10-29): `ServiceTokenDecoderConfiguration.closeJwksClient()` `@PreDestroy` ile istemciyi kapatır.

Yaş kapısı (+18, 2026-11-12): Game `adult_verified` iddiasını yalnız boolean `true` için okur; Auth tarafında yapılacak bir şey kalmadı. Ayrıntı: `docs/api.md` GAME bölümü (`includeAdult`, `includeAdultApplied`) ve `docs-security/game-service/NOTLAR.md`.

## AUTH'TA BEKLEYEN

- **Review özet okuma: HAZIR (2026-10-31).** `{clientId: "game-service", clientSecret, audience: "review-service"}` → `scope=rating-summaries:read`, 60 sn. Ortam `AUTH_GAME_SERVICE_CLIENT_SECRET` (Auth'taki ile aynı değer Game ortamına). Yalnız `GET /games/{gameId}/rating-summary` ve `POST /games/rating-summaries` içindir; kullanıcı JWT'sine fallback yok. Bkz. `verilen-cevaplar/review-service/talep-01-*`
- **GAME-26 referans kontrolü: HAZIR (2026-11-05).** `audience: "library-service"` → `scope=game-references:read`; `audience: "review-service"` → `scope=game-references:read rating-summaries:read`. Yeni secret yok. Ayrıntı: `verilen-cevaplar/game-service/talep-04-referans-kontrolu-servis-kimligi-ciftleri.md`.
- Hesap durumu olayı (`AccountStatusChanged`): Auth'ta hazır (2026-10-29, outbox, yalnız yönetici durum değişikliğinde). Game isterse alıcı uç yazar ve hedef olarak eklenir; istemezse 60 ila 90 sn gecikme kabulü geçerli
- Kullanıcı görünen adı ve profil: JWT'de e-posta, kullanıcı adı ve hesap durumu yok. User Service yazılınca oradan alınacak (Auth'ın işi değil)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/game-service/`, `docs-auth/d4-ekip-notlari/03_HAMZA_game_library_review.md`
