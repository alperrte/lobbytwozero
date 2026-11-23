# DUYURU (2026-11-18) — Kullanıcıya e-posta attırmak isteyen servisler için Mail platform kapısı

**Gönderen:** Kerem (mail-service) · **Alıcılar:** event-service, community-service, notification-service (ve isteyen diğer servis sahipleri) · **Durum:** AÇIK

Mail Service, başka servislerin kullanıcıya bilgilendirme e-postası attırması için hazır bir kapı sunuyor. Kodlanmış ve testli (Mail 111 test). Henüz kimse kullanmıyor ve gerçek Auth ile uçtan uca denenmedi (NOT VERIFIED); ilk kullanan servisle birlikte denenecek.

## Ne işe yarar
- Etkinlik daveti/hatırlatması (MAIL-06), bildirim e-postası (MAIL-07) gibi, bir olay olunca hesaba bildirim maili.
- Alıcının adresini bilmeniz gerekmez: yalnız `accountId` verirsiniz. Adres, gönderim anında Mail tarafından Auth'tan çözülür.
- Tasarım (logo, altbilgi, yasal not, TR/EN) Mail'indir; siz yalnız içerik yazarsınız.

## Nasıl
`POST /internal/v1/mail/notices` (`aud=mail-service`, kapsam `mail:notify`). Kayıt ve örnekler: [../entegrasyon-rehberi.md](../entegrasyon-rehberi.md). Özet:
1. Auth'ta servisiniz için `targets.mail-service=mail:notify` ve Mail'de `ltz.mail.platform.callers.<servis>` satırı (PR ile; Mail sahibi ekler).
2. Auth'tan `audience=mail-service` token (60 sn).
3. İstek: `accountId`, `kind`, `title`, `message` (+ isteğe bağlı düğme). `202` kuyruğa yazıldı demektir.

## Bilmeniz gerekenler
- Hesap başına ve çağıran başına saatlik hız sınırı vardır (`429`); varsayılan 5 ve 500.
- Alıcı doğrulanmamışsa, hesap aktif değilse ya da adres yoksa mail kalıcı olarak atlanır (hata dönmez, kuyrukta bekletilmez).
- Hangi kullanıcıya ne zaman e-posta gideceği kararı sizindir (Notification Service ile karıştırmayın; kullanıcının bildirim tercihine uymak sizin sorumluluğunuzdur).
- Sorunuz olursa bu klasöre `talep-NN-...md` yazın.
