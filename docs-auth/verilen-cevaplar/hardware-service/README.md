# HARDWARE-SERVICE — AUTH'A VERİLEN CEVAPLAR

**Sahip:** Alper · **Branch:** `feature/hardware-service` · **Hazırlayan:** Kerem (auth-service) · **Tarih:** 2026-10-26

Alper'in Auth'tan istediği 4 talebe ve ek sorularına cevaplar burada. Her talep için ayrı dosya var. Hepsi şu sırayla yazıldı: neydi, ne soruldu, benim cevabım, neden, senden ne bekliyorum.

---

## ÖZET TABLO

| Talep | Konu | Cevabım | Durum |
|---|---|---|---|
| [01](talep-01-token-iptali-ve-hesap-durumu.md) | Banlanan veya çıkış yapan kullanıcının eski token'ı | En fazla 90 sn (60 sn token + 30 sn tolerans). Olay ya da sorgu ucu yok | KAPANDI |
| [02](talep-02-masaustu-cihaz-kimligi.md) | Masaüstü cihaz kimliği | B yolu: kimliği Auth verecek. Şu an geliştirmeye kapalı, masaüstü tarihi belli olunca açılır | KARAR VERİLDİ, SIRADA |
| [03](talep-03-gercek-dev-silme-kabulu.md) | Gerçek DEV'de silme kabulü | Evet yapalım. Ama bizim 14 gün bekleme eklememiz E2E betiğini etkiliyor, tek satır ayar lazım | CEVAPLANDI, DENEME BEKLİYOR |
| [04](talep-04-debug-loglarinda-oauth-formlari.md) | DEBUG açıkken OAuth formları loglanıyor | Haklısın, ve sorun daha genişti (Hibernate varlık dökümü, SQL parametreleri). Düzelttim, 3 modda 22/22 | KAPANDI (main'e girince etkin) |

## ALPER'İN YAPMASI GEREKENLER (KISA LİSTE)

1. Belgendeki "15 dakika" ifadesini "90 saniye" yap (talep 01)
2. `ops/hardware-auth-e2e.mjs` içinde Auth ortamına `LTZ_AUTH_DELETION_GRACEPERIOD: 'PT0S'` ekle (talep 03)
3. Belgendeki "diğer 15 ACK" ifadesini "kalan 11 ACK" yap (talep 03)
4. Masaüstü uygulamasının başlama tarihi belli olunca bana haber ver (talep 02)
5. Log düzeltmesi main'e girince iki Google/Steam testini DEBUG açıkken bir kez koştur (talep 04)

## KERİM'İN YAPACAKLARI

1. ~~Log sertleştirmesini yapmak (talep 04)~~ YAPILDI (2026-10-27)
2. Bizim değişiklikleri main'e almak, sonra ortak DEV denemesi (talep 03)
3. Masaüstü cihaz kimliği tasarımı, tarih belli olunca (talep 02)

---

## EK SORULAR (ALPER'İN BELGELERİNDEN)

**Katalog yazma izni (`hardware:catalog:write`) hangi role bağlanacak**
Auth izin taşımaz, sadece rol taşır. Roller: PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN. Senin kodun ADMIN rolünü katalog yazmaya, ADMIN ve MODERATOR'ü yorum moderasyonuna bağlamış. Bu eşleme doğru ve uygun. Auth'tan başka bir şey beklemiyorum. Eşleme kendi servisinin kararı.

**Mutabakat listesi (issuer, audience, algoritma, JWKS, UUID claim, roller, iptal, cookie, CSRF, saat sapması)**
Hepsi yazılı ve uygulanmış durumda. Hardware kendi doğrulayıcısını yazmış. İkimizin de baktığı yer `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`. Ek mutabakat gerekmiyor.

## BU ARADA SENİN SERVİSİNİN AUTH'LA UYUMU

- Kullanıcı token'ı doğrulaması: uygun
- Silme ucu (`DELETE /internal/v1/accounts/{accountId}`): uygun ve gerçek Auth'la denenmiş
- Masaüstü senkronizasyon ucu: Auth'taki cihaz kimliği gelene kadar kapalı kalacak (talep 02)

## BİR UYARI: SENİN TESTLERİN AUTH KAYNAĞINA BAĞLI

`ProductionAuthSupport` Auth kaynak dosyalarını derleyip yansımayla çağırıyor. Yani Auth'ta bir imza değişirse senin testin sessizce kırılır. Ben bunu fark ettim ve önledim. Auth tarafında bu imzaları kilitleyen bir test ekledim. Hangi imzalara bağlı olduğunuz `docs-auth/README.md` içinde "Başkalarının bağlı olduğu Auth imzaları" başlığında yazılı.
