# LIBRARY-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Hamza · **Branch:** `feature/library-service` · **Güncelleme:** 2026-10-26

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
| [01](talep-01-servisler-arasi-yetki-iptal-ve-d4.md) | LIB-10/11/14 servisler arası yetki, logout/iptal penceresi, D4 gerçek E2E | Kullanıcı adına çağrıda kullanıcının kendi token'ı; Library → User `privacy:read` (+ `events:ingest`); Recommendation → Library hesaba bağlı `signals:read`; iptal 90 sn; D4 (b) PT0S ile izole | AUTH HAZIR (2026-10-30), USER/SOCIAL/LIBRARY UÇLARI BEKLENİYOR |

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
