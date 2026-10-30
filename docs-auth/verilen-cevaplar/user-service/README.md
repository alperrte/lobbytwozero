# USER-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Kerem · **Branch:** `feature/user-service` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | İskelet (henüz iş yok) |
| Kullanıcı token doğrulaması (JWT) | Yok (kod yok) |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | Yok. En çok kişisel veri burada olduğu için silme ucu öncelikli |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

| # | Konu | Cevap | Durum |
|---|---|---|---|
| [01](talep-01-olay-hedefi-servis-token-ciftleri-ve-silme-durumu.md) | REQ-01..04: `user` olay hedefi, kayıtlı hesapların yeniden yayını, silme bekleme durumu, servis token çiftleri | REQ-01 `user` hedefi `.env.example` ve anahtar betiğiyle varsayılan açık; REQ-02 karar B (eski DEV hesapları yeniden kaydolur, bundan sonrakiler kaybolmaz); REQ-03 hayır, token alamaz; REQ-04 social/library/hardware/review çiftleri Auth'ta hazır | AUTH HAZIR (2026-10-30), uçtan uca NOT VERIFIED |

**Ek not:** Auth artık `UserRegistered` ve `AccountEmailChanged` olaylarını transactional outbox ile yayımlayabiliyor (2026-10-29); olay e-posta taşımaz, yalnız hesap kimliği ve (kayıtta) kullanıcı adı taşır. Hedef listesi `ltz.auth.events.targets` varsayılan boştur, User'ın alıcı ucu (`POST /internal/events`, `aud=user-service`, `scope=events:ingest`) yazılınca Auth ortamına `user` eklenir. Profil e-posta tutacaksa değişikliği kendi tarafında eşitle (borç B19, kısmen kapandı).

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
