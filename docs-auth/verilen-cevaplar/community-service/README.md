# COMMUNITY-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Nisa · **Branch:** `feature/community-service` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Aktif geliştirme, henüz main'e birleşmedi |
| Kullanıcı token doğrulaması (JWT) | Geçici decoder her token'ı reddediyor (yazma uçları 401). Gerçek doğrulayıcı bekliyor |
| Hesap silme ucu | YOK. Hesap silme hedeflerinden biri, görevi PENDING kalıyor |

## TALEP VE CEVAPLAR

| Talep | Konu | Durum |
|---|---|---|
| [01](talep-01-hesap-durumu-ve-gorsel-erisimi.md) | Gerçek JWT, banlı hesap, görsel erişimi | CEVAPLANDI, Community'de iş var |
| [02](talep-02-servis-token-silme-ve-jwks-sorulari.md) | Servis token'ı, silme, durum olayı, JWKS | CEVAPLANDI, saklama süresi Alper'de, imtiyaz tanımı Auth'ta planlı |

## BİLMEN GEREKENLER

- Doğrulayıcı rehberi: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Kişisel ekip notun: `docs-auth/d4-ekip-notlari/04_NISA_community_content_event.md`
- Silme ucu rehberi: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Token ömrü 60 saniye, banlı kullanıcı en fazla 90 saniye görünür
