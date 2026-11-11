# Mail Service — Ajan Özeti (kopyala-kullan)

Bu sayfa, **kendi servisinden kullanıcıya e-posta attırmak** isteyen bir yapay zekâ ajanı içindir. Ayrıntı: [entegrasyon-rehberi.md](entegrasyon-rehberi.md). Mail Service'in kendi şablonunu değiştirecekseniz: [sablon-rehberi.md](sablon-rehberi.md).

## Yapman gerekenler

1. **Adres isteme.** Alıcının e-postasını bilmene gerek yok; yalnız `accountId` (Auth hesap kimliği) ver. Mail adresi Auth'tan çözer.
2. **Servisin kayıtlı mı?** Auth'ta `targets.mail-service=mail:notify` ve Mail'de `ltz.mail.platform.callers.<servis>` satırı yoksa istek `401/403` alır. Yoksa kullanıcıya/Mail sahibine bu iki satırı eklettir (rehber, Adım 1).
3. **Token al.** `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` gövde `{"clientId":"<servis>","clientSecret":"<sır>","audience":"mail-service"}`. 60 sn yaşar, önbelleğe al. Kalıp: `community-service` / `game-service` içindeki `AuthServiceTokenClient`.
4. **İsteği at.**

```
POST {MAIL_SERVICE_URL}/internal/v1/mail/notices
Authorization: Bearer <token>
{ "idempotencyKey": "<olaydan türetilmiş UUID>", "accountId": "<UUID>", "kind": "INFO|SUCCESS|WARNING|DANGER",
  "title": "≤80, tek satır", "message": "≤600, düz metin",
  "actionLabel": "≤30 (opsiyonel)", "actionLink": "https://... (opsiyonel, ikisi birlikte)", "locale": "tr|en" }
```

`202` = kuyruğa yazıldı. `5xx`/zaman aşımında **aynı `idempotencyKey` ile** yeniden dene.

## Yapma

- HTML yazma, parola/token/kod/kişisel veri koyma (düz metin; Mail kaçışlar).
- E-posta adresi gönderme (alan yok, `400`). Notification Service ile karıştırma.
- Kullanıcının "mail almak istemiyorum" tercihini atlama: Mail bunu **denetlemez**, sen (veya Notification) kontrol edersin.
- Toplu/duyuru için kullanma; sınır: hesap başına saatte 5, servis başına saatte 500 (`429`).
- Olay işleme akışını mail isteğine bağlama: commit sonrası, ayrı, hata yutan çağrı.

## Hata kodları (kısa)

`400` içerik/alan/bağlantı hatası · `401` token ya da servis kayıtsız · `403` kapsam · `409` aynı anahtar farklı içerik · `429` hız sınırı · `503` Mail yapılandırılmamış/geçici.

## Özel tasarım gerekiyorsa

Mail sahibine **şablon isteği** ilet: tür adı, hangi olayda gittiği, değişken alanlar, TR ve EN metin. Mail tarafında tek bir `.mail` dosyası eklenir (migration yok).
