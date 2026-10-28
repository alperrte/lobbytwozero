# MAIL-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Kerem · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

Auth bugün e-postayı **kendi içinde** yalnız geliştirmede, Mailpit'e gönderiyor. Üretimde gerçek teslim Mail Service'in işi olacak ve Auth'ta bu bağ henüz **yok** (üretim e-posta bağdaştırıcısı bilerek kapalı). Güvenlik ve hesap durumu e-postaları (MAIL-03, MAIL-04) için Auth'ta hiç port yok.

## AUTH'TAN HAZIR OLANLAR

- Üç port: `VerificationCodeMailPort` (doğrulama kodu), `PasswordResetMailPort` (parola sıfırlama), `AccountDeletionMailPort` (silme onayı)
- Dev adaptörü Mailpit'e gönderiyor. Hata mesajlarında alıcı adresi loga yazılmıyor
- Auth doğrulama kodunu, bağlantıyı ve token'ı **kendisi üretir**; teslimi ve HTML şablonu Mail Service'in işi (MAIL-16: Mail token üretmez, parola değiştirmez, token loglamaz)

## SENİN YAPACAKLARIN

- [ ] Mail Service gövdesi: kuyruk, durumlar, tekrar deneme, idempotency (MAIL-11, 13, 14). Bunlar Mail'in kendi işi
- [ ] Auth ile sözleşme: Auth ne gönderecek? Önerim (taslak, onay bekliyor): `POST /internal/v1/mail` gövdesi `{ type, to, locale, templateData, idempotencyKey }`, `aud=mail-service`, `scope=mail:send`, `sub=svc:auth-service`. Tür örnekleri: `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `ACCOUNT_DELETION`, `EMAIL_CHANGE`, `PASSWORD_CHANGED`, `SUSPICIOUS_LOGIN`, `ACCOUNT_SUSPENDED`, `ACCOUNT_BANNED`, `WELCOME`
- [ ] Silme ucu: `DELETE /internal/v1/accounts/{accountId}`. Mail gönderim geçmişi ve kuyruk kayıtları silinir. Yazılmadıkça Mail görevi `PENDING`

## AUTH'TA BEKLEYEN

- **MAIL-03 (güvenlik e-postaları):** parola değişti, şüpheli giriş. Auth bunları tetiklemiyor (borç B12). Mail sözleşmesi yazılınca bağlanır
- **MAIL-04 (hesap durumu e-postaları):** admin askıya alma ve yasaklama sonrası e-posta yok, aynı bağ
- **MAIL-05 (hoş geldin):** tetikleyici belirsiz. Önerim: kullanıcı ACTIVE olunca Auth tetikler
- E-posta değiştirme kodunun hangi porttan gittiği ayrıca kontrol edilmedi: Mail sözleşmesi yazılırken netleştirilecek
- Üretim e-posta bağdaştırıcısı: Mail Service hazır olunca yazılacak. Sorun: ana akışı bloke etmemesi gerekir (kuyruğa yazıp dönmek)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/mail-service/`, `docs-auth/acik-guvenlik-borclari.md` (B12), `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Kerem.md` (MAIL-01 ile MAIL-16)
