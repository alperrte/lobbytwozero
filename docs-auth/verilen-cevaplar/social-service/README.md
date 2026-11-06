# SOCIAL-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Umut · **Branch:** `feature/social-service` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Main'de, aktif |
| Kullanıcı token doğrulaması (JWT) | UYGUN (kullanıcı ve servis token'ı için ayrı doğrulayıcı) |
| Hesap silme ucu | VAR. İki sıkılaştırma açık (aşağıda) |

## NOTLAR

| Not | Konu | Durum |
|---|---|---|
| [01](talep-01-servis-token-sikilastirma.md) | Servis token'ında `sub` tam eşleşme ve `account_id` denetimi | AÇIK (Social'da yapılacak) |

## BİLMEN GEREKENLER

- Kişisel ekip notun: `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`
- Doğrulayıcı rehberi: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Messaging ve lfp için Social'ın `JwtConfig` yapısı örnek alınabilir
- Token ömrü 60 saniye, banlı kullanıcı en fazla 90 saniye görünür
