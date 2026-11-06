# PERFORMANCE-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Alper · **Branch:** `feature/performance-service` · **Güncelleme:** 2026-10-31

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Faz 1 kodu tamamlandı (Performance tarafı), main'e girmedi |
| Kullanıcı token doğrulaması (JWT) | Performance tarafında; gerçek Auth ile uçtan uca NOT VERIFIED |
| Cihaz token'ı (`aud=performance-service`) | Auth'ta HAZIR (talep 01 A) |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | Performance tarafında hazırlanıyor; ortak DEV doğrulaması bekliyor |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

Bu branch için açık talep yok. Kapanan talepler temizlendi; gerekirse git geçmişinden okunur (`git log --follow -- docs-auth/verilen-cevaplar/<servis>/`).

Önceki bağlam: masaüstü cihaz kimliği [hardware talep 02](../hardware-service/talep-02-masaustu-cihaz-kimligi.md) (Auth cihaz kimliği verir, aktif cihaz kararı Performance'ta kalır).

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.

