# CONTENT-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Nisa · **Branch:** `feature/content-service` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | İskelet (henüz iş yok) |
| Kullanıcı token doğrulaması (JWT) | Yok (kod yok) |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | Yok. Hesap silme hedeflerinden biri, görevi PENDING kalır |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

Şu an Auth'a yönelik açık bir talep yok. Soru çıkarsa buraya yazılır.

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
