# CHORE-SECURITY — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Kerem (güvenlik sorumlusu) · **Branch:** `chore/security` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | CI ve güvenlik altyapısı (Checkstyle, PMD, Trivy, Gitleaks) |
| Kullanıcı token doğrulaması (JWT) | Uygulanamaz |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | Uygulanamaz |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

Tarama sonuçları her ekibe ayrı rapor olarak gidiyor. Auth için sonuç: Checkstyle 0 ihlal, PMD de 7 üretim kaydı karar bekliyor, bağımlılık açığı (CVE) 0. Ayrıntı `docs-auth/README.md` içindeki güvenlik taraması bölümünde.

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
