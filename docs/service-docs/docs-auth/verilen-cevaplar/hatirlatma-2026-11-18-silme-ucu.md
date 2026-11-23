# HATIRLATMA (2026-11-18) — Hesap silme ucu (D4) eksik 5 servis

**Gönderen:** Kerem (auth-service) · **Durum:** AÇIK · **Kapsam:** Admin, Content, Event, Notification, Recommendation

Auth silme koordinatörü 16 hedeften **hepsi** onaylamadan hesabı "silindi" saymaz. Aşağıdaki 5 serviste ucun main'de bulunmadığını kod aramasıyla doğruladık (2026-11-16, Event için 2026-11-18). Bu servislerin görevi `PENDING` kalır ve silme tamamlanmaz. Auth tarafında yapılacak iş yok; hazır olan Auth sözleşmesi aşağıdadır.

## Yapılacak (her servis için aynı)

`DELETE /internal/v1/accounts/{accountId}` ucu:

- Servis token'ı: `aud=<servis-adı>`, `sub=svc:auth-service`, `scope=account:erase`, `account_id` = yoldaki hesap.
- Başarıda ve hesap zaten yoksa `204` (idempotent). Verisi olmayan servis de ucu açıp `204` döner.
- Ayrıntı ve örnek: [../d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md](../d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md), sözleşme: [../servis-sozlesmeleri.md](../servis-sozlesmeleri.md) §3.1.

## Servis bazlı not

| Servis | Sahip | Not |
| --- | --- | --- |
| event-service | Nisa | main'e geldi (2026-11-18): JWT doğrulaması ve 3 uç var. `events.organizer_id` hesaba bağlı bir REF; hesap silinince bu kayıtların silinmesi/anonimleştirilmesi gerekir. Silme ucu yok. |
| content-service | Nisa | Silme ucu yok. |
| recommendation-service | Alper | Silme ucu yok. |
| admin-service | ortak | Sorumlu kişi belirlenmeli; silme ucu yok. |
| notification-service | ortak | Sorumlu kişi belirlenmeli; silme ucu yok. |

Ayrıca bkz. her servisin klasöründeki `bilgilendirme-2026-11-05.md`. Bu hatırlatma onların yerine geçmez, durumu günceller.
