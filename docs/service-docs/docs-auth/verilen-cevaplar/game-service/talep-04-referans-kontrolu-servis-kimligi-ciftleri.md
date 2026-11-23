# GAME-SERVICE — TALEP 04: GAME-26 REFERANS KONTROLÜ SERVİS KİMLİĞİ ÇİFTLERİ

**Kimden:** Kerem (auth-service) · **Kime:** Hamza (game-service) · **Tarih:** 2026-11-05 · **Durum:** CEVAPLANDI, Auth tarafı hazır

## KISA CEVAP

Evet, iki izin Auth'ta tanımlandı. Yeni secret ya da yeni ENV yok; mevcut `AUTH_GAME_SERVICE_CLIENT_SECRET` kullanılır.

| İstemci | Hedef (`aud`) | Kapsam |
| --- | --- | --- |
| `game-service` | `library-service` | `game-references:read` (yeni çift) |
| `game-service` | `review-service` | `rating-summaries:read` + `game-references:read` (mevcut çifte ek kapsam) |

## SORULARA CEVAPLAR

1. **Ekleyebilir misiniz, kurallara aykırı mı?** Eklendi. D3/D4'e aykırı değil: hedef başına tek `aud`, token ≤60 sn, kullanıcı claim'i (`roles`, `sid`, `ver`, `amr`, `account_id`) taşımaz, varsayılan imtiyaz yok; yalnız `game-service` bu iki çifti alır.
2. **Kapsam adı:** `game-references:read` uygun, aynen kullanıldı. Hesaba bağlı değildir (token `account_id` taşımaz, hesap durumu sorulmaz).
3. **Mevcut review çiftine ikinci kapsam:** Sorun yok, ayrı çift gerekmedi. Aynı hedefte kapsamlar ya hep hesaba bağlı ya hiç; ikisi de bağsız olduğu için karışmaz. Token iki kapsamı tek `scope` alanında boşlukla ayrılmış (`game-references:read rating-summaries:read`) taşır. Review'ın mevcut kontrolü `scope` claim'ini Spring'in `JwtGrantedAuthoritiesConverter`'ı ile boşluktan ayırıp `SCOPE_*` yetkisine çevirdiği ve `hasAuthority("SCOPE_rating-summaries:read")` baktığı için ikinci kapsam mevcut ucu bozmaz (kodda doğrulandı; Review testi gerçek iki kapsamlı token ile yine de çalıştırılmalı).
4. **Belge ve test:** Auth tarafında yapıldı: `ServiceAudienceTokenTest` (game→library token, başka istemcilerin alamaması), `ServiceClientConfigTest` (kapsam hesaba bağlı bir kapsamla karışamaz), `docs-auth/servis-sozlesmeleri.md`, `docs-auth/entegrasyon/game-service.md`, `docs/api.md`.

## SİZDE KALANLAR

- **Token:** `POST /internal/v1/service-tokens` `{clientId: "game-service", clientSecret, audience: "library-service" | "review-service"}`. Kullanıcı token'ına geri düşme yok; 401/403'te yeniden deneme yok; her çağrıda yeni token.
- **Alıcı uçlar (Library ve Review):** yalnız sayı döner, kişisel veri yok; kapsam doğrulaması (`SCOPE_game-references:read`), tam `sub=svc:game-service`, tek `aud`, `typ=svc+jwt`, ≤60 sn ömür bu servislerin işidir.
- **Auth'un önerisi:** Library ve Review ucu yazılınca gerçek Auth `ServiceClientTokenIssuer`'ı ile (Review'ın `InternalRatingSummaryTest` kalıbı gibi) token üretip test edin; Game tarafında "sorgu başarısız ya da servis ulaşılamaz = taşıma yapılmaz (fail-closed)" kuralı korunmalı.

## YAYIN

Auth değişikliği küçük ve geriye uyumludur: eski token'lar ve mevcut `rating-summaries:read` çifti bozulmaz. Önce Library ve Review alıcı uçları hazır olmalı; Auth'un yayını beklenmeden kullanılamaz, o yüzden sıra Auth, sonra Library/Review/Game.

- Durum: **CEVAPLANDI** (Auth hazır; Library, Review ve Game alıcı/istemci işleri kendi sahiplerinde)
