# NOT 01 — SERVİS TOKEN'I İKİ SIKILAŞTIRMA

**Kimden:** Kerem (auth-service) · **Kime:** Umut (social-service) · **Tarih:** 2026-10-26
**Durum:** AÇIK — Social tarafında yapılacak, Auth'ta iş yok

---

## NEYDİ

Social servisin hesap silme ucu çalışıyor ve servis token'ını doğru kurmuş. İki kuralı sözleşmeden gevşek uygulamış. Bunu 23 Ekim'de ekip notunda bildirmiştim. Main'deki kodda hâlâ düzelmemiş.

## NE İSTİYORUM

**1. Konu tam eşleşsin.** Servis token doğrulayıcısı `sub` değerinin sadece `svc:` ile başlamasına bakıyor. Silme ucunda konu TAM OLARAK `svc:auth-service` olmalı. Silme yetkisi yalnız Auth'ta. Başka bir servis kimliği silme yapamamalı.

**2. Hesap kimliği yolla eşleşsin.** Silme ucu token'daki `account_id` değerini yoldaki `{accountId}` ile karşılaştırmıyor. Eşleşmezse 403 dönmeli. Böylece çalınan bir token başka bir hesabı silemez.

## NASIL

Game, Library ve Review aynı düzeltmeyi yapmış. `AccountErasureController` içinde şu iki kontrol var: `"svc:auth-service".equals(token.getSubject())` ve `accountId.toString().equals(token.getClaims().get("account_id"))`. Eşleşmezse `AccessDeniedException`. Aynısını Social'a ekle ve testini yaz.

## DURUM TAKİBİ

- Kişisel ekip notun: `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`
- Bunun dışında Social Auth'a uyumlu: iki ayrı doğrulayıcı, tür ayrımı, hedef, kapsam ve ömür kuralları doğru
