# EVENT-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Nisa · **Branch:** `feature/event-service` · **Güncelleme:** 2026-11-18

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | main'de (PR #149): etkinlik oluşturma, listeleme ve ayrıntı uçları (`docs/api.md` EVENT bölümü: IN PROGRESS) |
| Kullanıcı token doğrulaması (JWT) | Var (`JwtConfig`, `SecurityConfig`; JWKS `AUTH_SERVICE_URL` üzerinden). Gerçek Auth token'ıyla uçtan uca **NOT VERIFIED** (Event'in kendi kaydına göre) |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | Yok (kod aramasıyla doğrulandı 2026-11-18). `events.organizer_id` hesaba bağlı olduğundan gerekli; görev PENDING kalır. Bkz. [hatirlatma-2026-11-18-silme-ucu.md](../hatirlatma-2026-11-18-silme-ucu.md) |
| E-posta gönderme (Mail platform kapısı) | Kullanılmıyor. Etkinlik hatırlatması istenirse: `docs-mail/entegrasyon-rehberi.md` |

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

> **Yeni (2026-11-05):** [bilgilendirme-2026-11-05.md](bilgilendirme-2026-11-05.md) — Auth değişiklikleri ve bu servisten beklenenler. Durum: AÇIK
