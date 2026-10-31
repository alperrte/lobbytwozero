# MAIL-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Kerem · **Branch:** `feature/mail-service` · **Güncelleme:** 2026-10-26

---

## DURUM (AUTH AÇISINDAN)

| Konu | Durum |
|---|---|
| Servisin durumu | MAIL-01/02 kodu ve testi tamam (54 test, resmi kapı PASS, coverage %88,99); Auth adaptörü hazır, 4 e-posta akışı Mailpit ile uçtan uca doğrulandı (2026-10-31) |
| Kullanıcı token doğrulaması (JWT) | Yok, bilerek: Mail'in kullanıcıya açık ucu yok; yalnız Auth'un `svc+jwt` token'ı (`aud=mail-service`, `sub=svc:auth-service`, `scope=mail:send`, `account_id`) kabul edilir |
| Hesap silme ucu (`DELETE /internal/v1/accounts/{accountId}`) | Var (`scope=account:erase`, idempotent, `204`). Auth silme koordinatörüyle (14 gün bekleme) uçtan uca denenmedi |

## BU BRANCH İÇİN AUTH'TAN GELEN TALEP VE CEVAPLAR

Şu an Auth'a yönelik açık bir talep yok. Soru çıkarsa buraya yazılır.

**Auth'tan beklenenler (Auth adaptörüyle karşılandı, 2026-10-31):** `ltz.auth.mail.adapter=mail-service`; her çağrıda yeni `svc+jwt`; `POST /internal/v1/mail/{verification-codes,password-resets,account-deletions,security-notifications}`; gövde `idempotencyKey, accountId, recipientEmail` + türe özgü alanlar (`locale` gönderilmez → iki dilli); yalnız `202` başarı, diğer her yanıt Auth'ta teslim hatası. Sözleşme ayrıntısı: `docs/api.md` `## MAIL`; Mail tarafı özeti: `docs-mail/integration/README.md`.

**Ek not:** Auth'un dört e-posta portu var (doğrulama kodu, parola sıfırlama, hesap silme onayı, güvenlik bildirimi). Varsayılan adaptör fail-closed (`unavailable`), dev'de Mailpit (`dev-smtp`); prod yalnız `mail-service` adaptörüyle açılır.

## BİLMEN GEREKENLER

- Kullanıcı token'ını nasıl doğrulayacağın: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`
- Servisler arası sözleşmeler ve servis token kuralları: `docs-auth/servis-sozlesmeleri.md`
- Hesap silme ucunu nasıl yazacağın: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
- Açık güvenlik borçları ve gecikmeler: `docs-auth/acik-guvenlik-borclari.md`
- Token ömrü artık 60 saniye. Banlanan ya da çıkış yapan kullanıcı en fazla 90 saniye (60 + 30 tolerans) görünür. Ek bir şey yapman gerekmiyor
- Auth kaynağını derleyen testin varsa (ProductionAuthSupport gibi) `docs-auth/README.md` içindeki "Başkalarının bağlı olduğu Auth imzaları" bölümüne bak

Talebin ya da sorun varsa bu klasöre yeni bir `talep-NN-...md` olarak yazılır, cevap aynı şablonla verilir.
