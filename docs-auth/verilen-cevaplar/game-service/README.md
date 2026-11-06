# GAME-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Hamza · **Branch:** `feature/game-service` · **Güncelleme:** 2026-11-05

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Main'de, aktif (library ve review iskelet) |
| Kullanıcı token doğrulaması (JWT) | UYGUN, sözleşmeden sıkı (kid zorunlu, UUID sub, roller birebir) |
| Hesap silme ucu | VAR (Game, Library, Review). Sözleşmeye uygun |

## KAYITLAR

| Kayıt | Konu | Durum |
|---|---|---|
| [03](talep-03-review-puan-okuma-kapsami.md) | `game-service → review-service` puan okuma kapsamı (çift zaten var: `rating-summaries:read`) | KAPANDI (Review uyarlandı, Auth'ta iş yok) |

## BİLMEN GEREKENLER

- Kişisel ekip notun: `docs-auth/d4-ekip-notlari/03_HAMZA_game_library_review.md`
- Testlerinin Auth kaynağına bağlı olması: `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümü
- Token ömrü 60 saniye, banlı kullanıcı en fazla 90 saniye görünür

