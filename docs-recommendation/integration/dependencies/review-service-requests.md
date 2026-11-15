# TASK 2 — Kullanıcının puanlarını toplu okuma talebi

Tarih: 2026-11-14 · Sahip: Hamza · Tüketici: Alper / recommendation-service
Durum: OPEN / REC-03 BLOCKED; Review/Auth kodu değiştirilmedi.

## Doğrulanmış mevcut durum

`GET /api/v1/review/games/{gameId}/rating` yalnız doğrulanmış kullanıcının tek oyundaki puanıdır.
`GET /api/v1/review/reviews/mine` sayfalı metin incelemelerini döndürür; `ReviewResponse`
rating alanı taşımaz. `/internal/v1/games/rating-summaries` oyun geneli aggregate'tir ve
Game caller iznine sahiptir; kişisel puan geçmişinin yerine kullanılamaz.
Tüm oyunlar için tek tek rating çağrısı N+1 yaratır; Recommendation Review DB'sine erişmez.

## Önerilen sözleşme — henüz uygulanmış endpoint değildir

Tercih edilen: `GET /api/v1/review/ratings/mine?after=<opaqueCursor>&size=50`.

- Kullanıcının kendi `at+jwt`'si request kapsamında iletilir; Review userId'yi sadece doğrulanmış
  `sub`'dan çıkarır. İstemci gövdesi/header'ı veya svc token kullanıcı kimliği yerine geçmez.
- Response: `{items:[{gameId:UUID,rating:number,updatedAt:UTC}],nextCursor:string|null}`.
- Rating aralığı ve yarım yıldız adımı mevcut Review kuralıyla aynıdır; kanonik Game UUID.
- Metin inceleme/username/email gerekmez. Yalnız görünür/izinli puanlar; hesap silme ve
  değerlendirme silme değişiklikleri sonraki okumada yansır; no-store/private.
- Sayfa 1–50, en çok 10 sayfa/500 sinyal; daha büyük pencere açık kısmi/veri-limit durumu.
  Cursor sabit sıralı olmalı; tekrar kayıt ve aynı snapshot içinde kayma önlenmeli.
- 401 token yok/geçersiz, 403 erişim yok, 400 cursor/size, 503 bağımlılık/DB hatası.
  3000/5000 ms timeout; redirect ve kör retry yok.

Alternatif internal hesap-bağlı sözleşme seçilirse Kerem'in Auth caller/audience/scope onayı
ayrıca gerekir. Şu anda Recommendation→Review kişisel puan izni varsayılmaz.

## Kabul testleri

A/B ayrımı, fake userId reddi, svc token reddi, boş/silinmiş puan, aralık/size/cursor,
duplicate/paging, 503 ve puan ağırlığının gerçek Recommendation sıralamasına etkisi.
Tür yakınlığı için Game metadata batch talebine de ihtiyaç vardır.

TASK 2 yanıtı `PERSONAL_RATINGS_UNAVAILABLE` bildirir; puan sıfırmış veya Game'in genel
rating özeti kullanıcının puanıymış gibi gösterilmez.
