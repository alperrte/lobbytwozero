# REVIEW-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Hamza · **Branch:** `feature/review-service` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | İskelet; hesap silme ucu eklenmiş (veri yok → idempotent 204) |
| Kullanıcı token doğrulaması (JWT) | Servis token doğrulayıcısı var; kullanıcı token doğrulayıcısı için bkz. rehber |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | VAR (main'de, sözleşmeye uygun) |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

| # | Konu | Cevap | Durum |
|---|---|---|---|
| [01](talep-01-servis-kimligi-ciftleri-ve-moderasyon-yetkisi.md) | Servis kimliği çiftleri (Game/Admin/Notification) ve insan moderatör yetkisi | Game → Review `rating-summaries:read` hazır; Review ↔ Admin ve Review → Notification alıcı yokken açılmaz; yetki servis kimliğinden, insan Admin'de doğrulanır | AUTH HAZIR (Game), ALICILAR BEKLİYOR |

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
