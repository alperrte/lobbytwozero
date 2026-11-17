# MESSAGING-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Umut · **Branch:** `feature/messaging-service` · **Güncelleme:** 2026-11-13

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Uygulandı (LTZ-UMT-MSG-01..08) |
| Kullanıcı token doğrulaması (JWT) | Var (JWKS, RS256, `at+jwt`); Gateway üzerinden canlı PASS |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | Var; gerçek Auth ile canlı PASS, görev `CONFIRMED` (2026-11-13) |
| Engel sorgusu (`blocks:check` → social-service) | Var; canlı PASS |
| Bildirim olayı (`events:ingest` → notification-service) | Messaging tarafı hazır, Auth çifti bekleniyor: [talep-01](talep-01-notification-events-ingest.md) |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

| Belge | Konu | Durum |
|---|---|---|
| [bilgilendirme-2026-11-05.md](bilgilendirme-2026-11-05.md) | Hesap silme ucunun yazılması | KAPANDI — uç LTZ-UMT-MSG-07'de yazıldı, 2026-11-13'te gerçek Auth ile `CONFIRMED` |
| [talep-01-notification-events-ingest.md](talep-01-notification-events-ingest.md) | `messaging-service → notification-service: events:ingest` çifti | AÇIK (Auth) |

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür; açık canlı akış token bitiminde kapanır
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
