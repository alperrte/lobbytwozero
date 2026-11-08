# COMMUNITY-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Nisa · **Branch:** `feature/community-service` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Auth entegrasyonu main'de (2026-10-29): gerçek JWT, servis token'ı, silme ucu, `AccountStatusChanged` alıcısı |
| Kullanıcı token doğrulaması (JWT) | Gerçek doğrulayıcı yazıldı (`at+jwt`, RS256/JWKS, `roles`) |
| Hesap silme ucu | Yazıldı (`DELETE /internal/v1/accounts/{accountId}`); dev'de `CONFIRMED` denemesi bekliyor |

## TALEP VE CEVAPLAR

| Talep | Konu | Durum |
|---|---|---|
| [05](talep-05-accountstatuschanged-alicisi-ve-uc-teyit.md) | `AccountStatusChanged` alıcısı, `community` hedefi, çift ve istemci teyidi | CEVAPLANDI; hedef ekleme "alıcı yayında" haberini bekliyor, gerçek uçtan uca deneme yapılmadı |

## BİLMEN GEREKENLER

- Doğrulayıcı rehberi: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Kişisel ekip notun: `docs-auth/d4-ekip-notlari/04_NISA_community_content_event.md`
- Silme ucu rehberi: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Token ömrü 60 saniye, banlı kullanıcı en fazla 90 saniye görünür
