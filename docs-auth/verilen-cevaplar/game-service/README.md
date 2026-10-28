# GAME-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Hamza · **Branch:** `feature/game-service` · **Güncelleme:** 2026-10-26

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
| [01](talep-01-kapanan-talepler.md) | Game raporundaki "AUTH OWNER ACTION" maddeleri | HEPSİ KAPANDI |
| [02](talep-02-jwks-httpclient-ve-coverage-onayi.md) | JWKS HttpClient yaşam döngüsü ve POLICY_REVIEW_REQUIRED onayı | CEVAPLANDI, Hamza'da yapılacak |

## BİLMEN GEREKENLER

- Kişisel ekip notun: `docs-auth/d4-ekip-notlari/03_HAMZA_game_library_review.md`
- Testlerinin Auth kaynağına bağlı olması: kayıt 01'in sonundaki uyarı
- Token ömrü 60 saniye, banlı kullanıcı en fazla 90 saniye görünür
