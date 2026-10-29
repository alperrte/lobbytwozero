# GAME-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Hamza · **Kod durumu:** Aktif · **Güncelleme:** 2026-10-29

## ÖZET

Game, Auth sözleşmesine en iyi uyan servis: kullanıcı JWT'si, servis token'lı silme ucu ve JWKS yaşam döngüsü yazılı ve denenmiş. Auth'tan başka bir şey istemiyor.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT: JWKS, `at+jwt`, `roles` (`ADMIN`, `MODERATOR`, `GAME_DEVELOPER`, `PLAYER`), `sub=userId`
- Hesap silme ucu: `AccountErasureController` hem `sub` tam eşleşmesini hem `account_id`'yi denetliyor. Diğer servisler için örnek budur
- Oyun bazlı geliştirici yetkisi Auth'tan değil, Game'in kendi veritabanından gelir (Auth'ta oyun bazlı rol yok, olmayacak)

## SENİN YAPACAKLARIN

- [ ] Belgendeki (`LTZ-HAM-GAME-04-Auth-Entegrasyonu.md`) "Auth varsayılan access ömrü 5 dakika" ifadesini güncelle: artık **60 sn**, yayılım üst sınırı 90 sn. Aynı belgede "silme için karar" diyen yerler uygulanmış durumda
- [ ] `chore/security` (#16) `main`'de. Kendi dalına `main`'i aldıktan sonra strict doğrulamayı çalıştır. Checkstyle borcun (`.*` import'ları) `main`'deki CI'ı kırmızı tutuyor, düzeltmeni commit'leyip PR aç
- [ ] JWKS `HttpClient` yaşam döngüsü düzeltmen onaylıydı, commit'li değilse commit'le

## AUTH'TA BEKLEYEN

- Hesap durumu olayı (`AccountStatusChanged`): Auth'ta hazır (2026-10-29, outbox, yalnız yönetici durum değişikliğinde). Game isterse alıcı uç yazar ve hedef olarak eklenir; istemezse 60 ila 90 sn gecikme kabulü geçerli
- Kullanıcı görünen adı ve profil: JWT'de e-posta, kullanıcı adı ve hesap durumu yok. User Service yazılınca oradan alınacak (Auth'ın işi değil)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/game-service/`, `docs-auth/d4-ekip-notlari/03_HAMZA_game_library_review.md`
