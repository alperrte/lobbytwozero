# PERFORMANCE-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Alper · **Branch:** `feature/performance-service` · **Güncelleme:** 2026-11-06

> **REQ-04-A güncellemesi:** Kullanıcının bildirdiği Kerem onayıyla Performance okuma izinleri Auth'a eklendi. Uygulama kaydı: [talep-02-performans-okuma-izinleri.md](talep-02-performans-okuma-izinleri.md). Bu kayıt Alper'in uygulama kaydıdır; Kerem adına yeni cevap yazıldığı anlamına gelmez. Güncel test/kabul kanıtı Performance `auth-integration-06.md` içindedir.

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Performance kodu ve Auth entegrasyon testleri mevcut; üretim tüketicileri ayrı iş |
| Kullanıcı token doğrulaması (JWT) | İzin/cihaz akışlarında izole gerçek Auth/Gateway kabulü PASS; bütün oturum/rapor akışları çalışan servislerle E2E kabulü değildir |
| Cihaz token'ı (`aud=performance-service`) | Auth'ta hazır; izole gerçek HTTP kabulü PASS |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | İzole gerçek Auth worker → Performance 204/CONFIRMED PASS; ortak DEV/tüm servis Saga kabulü ayrıca bekler |
| Performance okuma izinleri (REQ-04-A) | Auth'ta uygulandı; güncel test/HTTP kanıtı `docs-performance/integration/auth/auth-integration-06.md` |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

Auth'tan beklenen açık talep kalmadı. `main` dalındaki belge temizliğiyle kaldırılan eski talep 01 ve bilgilendirme dosyaları geri getirilmedi; gerektiğinde git geçmişinden okunur (`git log --follow -- docs-auth/verilen-cevaplar/performance-service/`).

Güncel REQ-04-A uygulama kaydı [talep-02-performans-okuma-izinleri.md](talep-02-performans-okuma-izinleri.md) içinde korunur. Auth 537 + Performance 138 test ve 15 gerçek HTTP kontrolü PASS. [Kabul/kalite kanıtı](../../../docs-performance/integration/auth/auth-integration-06.md). Üretim Hardware/Recommendation istemcileri ve ortak DEV/production kabulü ayrı işlerdir.

Önceki bağlam: masaüstü cihaz kimliği [hardware talep 02](../hardware-service/talep-02-masaustu-cihaz-kimligi.md) (Auth cihaz kimliği verir, aktif cihaz kararı Performance'ta kalır).

## BİLMEN GEREKENLER

- Auth için tek giriş noktası: [AUTH_REHBERI.md](../../AUTH_REHBERI.md).
- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
