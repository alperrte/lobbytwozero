# req-01 — game-service: etkinlik araması için oyun adı arama sözleşmesinin sınırları

> Kimden: Event Service (Nisa) · Kime: game-service sahibi (Hamza) · Tarih: 2026-11-09 · Durum: **AÇIK (engelleyici değil; EVT-05'in tam karşılanmasını sınırlıyor)**
> Bu talep yalnız bildirim ve ihtiyaç tanımıdır. Event Service tarafında game-service kodu değiştirilmedi. **Auth tarafında değişiklik gerekmiyor:** kullanılan iki uç herkese açıktır, servis kimliği/secret ve yeni ENV gerekmez.

## Bağlam

Event Service (EVT-05, karar KD-5/B) etkinlik araması için oyun adını Game Service'te aratır, bulunan `gameId`'lerle kendi veritabanındaki etkinlikleri sorgular (oyun adı Event DB'sine kopyalanmaz). EVT-17 ile etkinlikler `gameId` ile oyuna bağlanır (doğrulama `POST /api/v1/game/games/batch`).

## Kullandığımız mevcut sözleşme (docs/api.md GAME-03 / GAME-15; çalışan dev servise karşı da doğrulandı)

| Uç | Yetki | Kullanım | Doğrulanan davranış |
| --- | --- | --- | --- |
| `GET /api/v1/game/games/search?q=&page=&size=` | Public | Oyun adı araması; tüm sayfalar okunur | `q` zorunlu (eksik/boş → 400), `size` 1–100 (101 → 400), `page` ≤ 10000 (10001 → 400), `totalElements` döner, yanıtta `items[].game.gameId` var, sıra `TITLE` artan + `gameId` (kararlı) |
| `POST /api/v1/game/games/batch` | Public | Tek `gameId` ile "yayında mı" doğrulaması | `items` (ACTIVE, her tür) / `unavailable` |

## Sınırlar (gereksinimi karşılamada eksik bırakanlar)

| # | Sınır | Etkisi | İhtiyaç |
| --- | --- | --- | --- |
| S-1 | Arama **önek** aramasıdır (`normalized_title like 'q%'`); kelime ortası eşleşmez ("witcher" → "The Witcher 3" bulunmaz). Etkinlik başlığı aramasında ise "içerir" kullanılıyor. | Oyun adı eşleşmesi başlık eşleşmesinden daha dar. | Ürün kararına bağlı: "içerir" veya kelime-başı eşleşme seçeneği. |
| S-2 | Arama yalnız **ACTIVE + MAIN_GAME** döner; DLC vb. dönmez. Event bağlantısı için ise `batch` her ACTIVE türü (DLC dahil) kabul eder. | DLC'ye bağlı bir etkinlik oyun adıyla **bulunamaz**. | Aramada tür seçimi (ör. `types`/`includeDlc`) **veya** Event'in yalnız MAIN_GAME'e bağlamasına ürün kararı. |
| S-3 | Çok sayıda eşleşme: `q=a` dev ortamda **8352** oyunla eşleşiyor. Event, eksik sonuç üretmemek için tüm sayfaları okuyor ve 1000'den fazla eşleşmede isteği 422 ile reddediyor. | Kısa metinlerde oyun adı araması çalışmıyor (kullanıcı metni uzatmak zorunda). | **Tercih edilen çözüm:** verilen bir `gameId` kümesini ada göre süzen uç, ör. `POST /api/v1/game/games/name-match` `{ "q": "...", "gameIds": [≤100] }` → eşleşen kimlikler. Event, yalnız açık etkinliklerde bağlı olan (az sayıda) `gameId`'yi gönderir; hem eksiksiz hem ucuz olur. (Uç adı bir **öneridir**.) |
| S-4 | Arama ucunda istek sınırı (rate limit) belgelenmemiş (`batch`/`prices` için 100 ani / 20 sn var). Event bir arama için en çok 11 sıralı çağrı yapabilir. | Yük/429 davranışı belirsiz. | Arama ucu için sınırın belgelenmesi; 429'da Event 503 döner (yeniden deneme yok). |

## Beklenen

S-3 için bir ön tasarım yanıtı (uç yapılacak mı); S-1 ve S-2 için ürün kararı. Event Service mevcut sözleşmeyle çalışır ve sınırlar `docs-event/event-service/evt-05-etkinlik-arama.md` ve `docs/api.md` EVT-05 kaydında açıkça yazılıdır.

## Etki

Bu talep karşılanmadan EVT-05'in "ilişkili oyuna göre arama" kısmı **tam karşılanmış sayılmaz** (önek + yalnız ana oyun + en çok 1000 eşleşme sınırlarıyla çalışır).
