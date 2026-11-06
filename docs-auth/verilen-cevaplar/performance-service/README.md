# PERFORMANCE-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Alper · **Branch:** `feature/performance-service` · **Güncelleme:** 2026-11-06

> **REQ-04-A güncellemesi:** Kullanıcının bildirdiği Kerem onayıyla Performance okuma izinleri Auth'a eklendi. Uygulama kaydı: [talep-02-performans-okuma-izinleri.md](talep-02-performans-okuma-izinleri.md). Bu kayıt Alper'in uygulama kaydıdır; Kerem adına yeni cevap yazıldığı anlamına gelmez. Güncel test/kabul kanıtı Performance `auth-integration-06.md` içindedir.

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | Performance kodu ve Auth entegrasyon testleri mevcut; üretim tüketicileri ayrı iş |
| Kullanıcı token doğrulaması (JWT) | İzin/cihaz akışlarında izole gerçek Auth/Gateway kabulü PASS; bütün oturum/rapor akışları çalışan servislerle E2E kabulü değildir |
| Cihaz token'ı (`aud=performance-service`) | Auth'ta HAZIR (talep 01 A) |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | İzole gerçek Auth worker → Performance 204/CONFIRMED PASS; ortak DEV/tüm servis Saga kabulü ayrıca bekler |
| Performance okuma izinleri (REQ-04-A) | Auth'ta uygulandı; güncel test/HTTP kanıtı `docs-performance/integration/auth/auth-integration-06.md` |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

| # | Konu | Cevap | Durum |
|---|---|---|---|
| [01](talep-01-cihaz-token-hedefi-iptal-ve-cihaz-dogrulama.md) | Cihaz token hedefi, iptal yayılımı, Hardware → Performance cihaz doğrulama | Cihaz token'ında `target=performance`; `devices:verify` hesaba bağlı token; iptal bildirimi yapılmıyor (60 sn) | AUTH HAZIR (A, C), B KARAR: kabul |
| [02](talep-02-performans-okuma-izinleri.md) | Performance Request 04: Hardware özeti ve Recommendation FPS referansı | `summary:read` ve `reference:read` hesaba bağlı kapsamlar; kullanıcı tarafından bildirilen Kerem onayıyla uygulandı | KAPANDI (Auth izinleri ve izole gerçek HTTP kabulü); üretim tüketici istemcileri ayrı iş |

Önceki bağlam: masaüstü cihaz kimliği [hardware talep 02](../hardware-service/talep-02-masaustu-cihaz-kimligi.md) (Auth cihaz kimliği verir, aktif cihaz kararı Performance'ta kalır).

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.

> **Yeni (2026-11-05):** [bilgilendirme-2026-11-05.md](bilgilendirme-2026-11-05.md) — Auth değişiklikleri ve bu servisten beklenenler. Durum: KAPANDI
