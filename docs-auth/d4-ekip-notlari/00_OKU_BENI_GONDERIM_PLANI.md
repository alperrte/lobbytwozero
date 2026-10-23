# D4 / Hesap Silme — Ekip Gönderim Planı

> Tarih: 2026-10-24 · Hazırlayan: Kerem · Auth tarafı `feature/auth-service` dalında (`7e56440`). Sözleşmenin kaynağı:
> [`docs-auth/servis-sozlesmeleri.md`](../servis-sozlesmeleri.md) §3–§3.3; ekip rehberi
> [`docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`](../../docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md).

## Ne oldu (30 saniye)

- Auth artık hesap silinirken **16 servisi gerçekten çağırıyor**: `DELETE <SERVİS>_SERVICE_URL/internal/v1/accounts/{accountId}`.
- Her çağrıda yeni bir **servis token'ı** (D4, `typ=svc+jwt`) gönderiyor. Kurallar Auth'ta sabit; hiçbir servise göre gevşetilmez.
- Yalnız `200`/`204` onay sayılır. Uç yoksa (`404`), `202`, `401/403`, `5xx`, 5 sn zaman aşımı → görev bekler, yeniden denenir.
- **16 servisin hepsi onaylamadan hiçbir hesap `DELETED` olmaz.** Yani her servis bu ucu açmak zorunda (verin yoksa da `204`).
- Bugün ucu olan tek servis **Social** (main'de) ve onda da 2 denetim eksik.

## Kime ne

| Kişi | Servisler | Okunacak dosyalar |
| --- | --- | --- |
| **Umut** | social, messaging, lfp | [01](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md) + [02](02_UMUT_social_messaging_lfp.md) |
| **Hamza** | game, library, review | [01](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md) + [03](03_HAMZA_game_library_review.md) |
| **Nisa** | community, content, event | [01](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md) + [04](04_NISA_community_content_event.md) |
| **Alper** | hardware, recommendation, performance + repo sahibi | [01](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md) + [05](05_ALPER_hardware_recommendation_performance_ve_repo_sahibi.md) |
| **Kerem** | user, mail (+ auth açık işleri) | [06](06_KEREM_TAKIP_LISTESI.md) |
| Ortak servisler | notification, admin | Sahibi Alper'le netleşince [01](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md) |

## Kısa mesajlar

**Umut'a:**
> Selam Umut, Auth tarafında D4 servis token'ı ve gerçek silme adaptörü bitti; Auth artık Social'ı gerçekten çağırıyor ve Social'ın
> doğrulayıcısıyla uyumluluğu test ettim (kabul ediyor). Ama Social'da 2 denetim eksik: `sub` tam olarak `svc:auth-service` olmalı
> (şu an yalnız `svc:` öneki), `account_id` claim'i yoldaki `{accountId}` ile aynı değilse `403`. Ayrıca messaging ve lfp de silme
> hedefi; ikisine de aynı uç lazım. Ayrıntı ve kod `docs-auth/d4-ekip-notlari/` altında 01 ve 02'de.

**Hamza'ya:**
> Selam Hamza, Game artık hesap silme hedefi (S2 kararı). Auth her hesap silmede Game, Library ve Review'a
> `DELETE /internal/v1/accounts/{id}` atıyor; uç yoksa hesap silinemiyor. Game'deki kullanıcı REF'leri (editör, geliştirici yetkisi,
> içerik yazarı, feedback, audit aktörü) için ne silinip ne anonimleşeceğini birlikte karar verelim. Bu arada Game belgesinde §11'de
> listelediğin 46 Auth + 3 Gateway Sonar bulgusunun hepsi kapandı (0 bulgu). Ayrıntı `docs-auth/d4-ekip-notlari/` altında 01 ve 03'te.

**Nisa'ya:**
> Selam Nisa, Auth'ta hesap silme koordinasyonu artık gerçek: her hesap silmede Community, Content ve Event'e
> `DELETE /internal/v1/accounts/{id}` geliyor. Üç servise de bu iç uç lazım (verin yoksa da `204`). Sözleşme, örnek kod ve test listesi
> `docs-auth/d4-ekip-notlari/` altında 01 ve 04'te.

**Alper'e:**
> Selam Alper, Auth tarafında D4 servis kimliği ve gerçek silme adaptörü bitti, main'e PR açacağım. Hardware, Recommendation ve
> Performance da silme hedefi; ucun açılması lazım. Repo sahibi olarak senden birkaç karar bekliyorum: notification/admin silme ucunun
> sahibi, iskelet servislerin %80 kapsam kuralı ve prod'da iç ağ TLS'i. Ayrıntı `docs-auth/d4-ekip-notlari/` altında 01 ve 05'te.
