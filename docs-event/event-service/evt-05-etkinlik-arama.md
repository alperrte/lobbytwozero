# EVT-05 — Etkinlik arama: gereksinim karşılama durumu, sınırlar ve bağımlılıklar

> **EVT-05 KISMEN KARŞILANDI — tamamlandı DEĞİL.** Etkinlik adı **ve** (KD-5/B ile) oyun adı araması kodlandı; ancak Game Service'in mevcut arama sözleşmesi (yalnız **önek**, yalnız **ana oyun**, 1000+ eşleşmede eksiksiz sonuç alınamıyor) "ilişkili oyuna göre arama"yı tam karşılamıyor. Engel ve talep: [Game talebi req-01](../game-service/talep/req-01.md). Kod ve testler yazıldı; **testler ve sorgular hiçbir veritabanında çalıştırılmadı.** Docker'daki imaj EVT-05 öncesidir.
> Son güncelleme: 2026-11-09 · Sorumlu: Nisa · İlgili: [EVT-04](evt-04-etkinlik-listeleme.md), [EVT-17](evt-17-oyun-baglantisi.md)

## 1. Kaynaklar

| # | Kaynak | İçerik |
| --- | --- | --- |
| K-1 | `LTZ_Servis_Icerikleri_Nisa.md` EVT-05 | "Etkinlik adına veya ilişkili oyuna göre arama yapılabilmesi." |
| K-2 | `docs/architecture.md` §3, §6.1 | Ayrı arama servisi yoktur; arama her servisin kendi verisinde yapılır; `event → game` RestClient ile. |
| K-3 | `docs/SECURITY.md` §7, §9, §10 | Dış sağlayıcı yanıtı güvenilmez girdi; sorgular parametrik; sayfalama sunucuda sınırlı; `ProblemDetail`. |
| K-4 | Karar KD-5/B (Nisa, 2026-11-09) | Oyun adı araması Game Service'te yapılır; bulunan `gameId`'lerle Event DB'sindeki etkinlikler aranır; oyun adı kopyalanmaz. |
| K-5 | Karar KD-4 | Etkinlik görüntüleme yalnız giriş yapan kullanıcılara açık; misafir 401. |
| K-6 | Game sözleşmesi (`docs/api.md` GAME-03, GAME-15; kod: `GameDiscoveryController`, `DiscoveryQuery`, `GameDiscoveryRepository`) | Arama public, `q` zorunlu, **önek** (`normalized_title like 'q%'`), yalnız ACTIVE + MAIN_GAME, `size` 1–100, `page` ≤ 10000, `totalElements`, sıra başlık+id. |

## 2. Gereksinim karşılama tablosu

| # | Gereksinim | Durum | Kodlandı | Doğrulandı | Docker'da |
| --- | --- | --- | --- | --- | --- |
| 1 | **Etkinlik adına göre** arama (içerir, büyük/küçük harf duyarsız) | **Karşılandı** | `q` → `EventRepository.searchByTitle` | Testler yazıldı; çalıştırılmadı | Hayır (imaj EVT-05 öncesi) |
| 2 | **İlişkili oyun adına göre** arama | **Kısmen karşılandı** (Game sözleşme sınırları, §4) | `GameServiceClient.findGameIdsByNamePrefix` + `searchByTitleOrGame` | Testler yazıldı; çalıştırılmadı | Hayır |
| 3 | Etkinlik adı **veya** oyun adı eşleşmesi; iki yoldan eşleşen etkinlik **tek kez** | **Karşılandı** | tek sorguda `lower(title) like … or game_id in (…)` (tek tablo, join yok) | `EventRepositoryTest` yazıldı; çalıştırılmadı | Hayır |
| 4 | Liste kapsamı, kararlı sıra ve sayfalama korunur (EVT-04) | **Karşılandı** | aynı koşullar (`endsAt > şimdi`, `PLANNED/ACTIVE`, `startsAt, id`) ve `Page` | aynı | Hayır |
| 5 | Game sonuçlarının yalnız ilk sayfası alınmaz; eksik sonuç/yanlış toplam yok | **Karşılandı** (sınır: 1000 oyun) | tüm sayfalar okunur; toplam/tutarlılık denetlenir; aşımda 422 | `GameServiceClientTest` yazıldı; çalıştırılmadı | Hayır |
| 6 | Game Service erişilemezse "oyun bulunamadı" gibi gösterilmez | **Karşılandı** | `GameCatalogUnavailableException` → 503 (kırpılmış sonuç/başlık-only geri düşüş yok) | `EventServiceTest`, controller/güvenlik testleri | Hayır |
| 7 | Başlık araması açıklamayı kapsamaz | **Karşılandı** | yalnız `title` | `EventRepositoryTest.descriptionIsNeverSearched` | Hayır |
| 8 | İptal, tamamlanmış ve süresi geçmiş etkinlikler arama dışı | **Karşılandı** | liste kapsamı koşulu | `EventRepositoryTest` | Hayır |
| 9 | Oyun adı events tablosuna kopyalanmaz | **Karşılandı** | yalnız `game_id` | — | — |
| 10 | Yalnız giriş yapan kullanıcı arar; misafir 401 | **Karşılandı** (KD-4) | `SecurityConfig` | `EventEndpointSecurityTest` | Hayır |

**Sonuç:** 1, 3–10 karşılandı (kodlandı, doğrulanmadı). 2 **kısmen** karşılandı: oyun adı araması, Game'in mevcut sözleşmesinin izin verdiği kapsamda çalışır (§4). Bu yüzden EVT-05 **tamamlanmış sayılmaz**; Game talebi (req-01) ve ürün kararı bekleniyor.

## 3. Davranış

`GET /api/v1/event/events?q=<metin>&page=&size=` (EVT-04 liste ucunun isteğe bağlı parametresi).

1. `q` kırpılır; boşsa arama yoktur (düz liste). 100 karakter sınırı, kontrol karakteri (NUL dahil) 400.
2. Listelenen kapsamda **oyuna bağlı hiç etkinlik yoksa** Game Service'e gidilmez (oyun sonucu çıktıyı değiştiremez); yalnız başlık araması yapılır.
3. Oyuna bağlı etkinlik varsa Game Service'te `GET /api/v1/game/games/search?q=<metin>&page=&size=100` **tüm sayfalarıyla** okunur → adı metinle **başlayan** (ana) oyunların kimlikleri.
4. Event DB'sinde tek sorgu: başlığı metni **içeren** (büyük/küçük harf duyarsız, Türkçe dahil) **veya** `game_id` bulunan kimliklerden biri olan etkinlikler; EVT-04 ile aynı kapsam, sıra ve sayfalama; her etkinlik bir kez.
5. Hiç oyun eşleşmezse yalnız başlık sorgusu çalışır.

**Hata sözleşmesi:**
- Game Service erişilemez / zaman aşımı / 4xx-5xx / 429 / geçersiz veya tutarsız yanıt → **503** `ProblemDetail` ("The game catalog is temporarily unavailable."); sonuç **kırpılmaz, başlık-only'ye düşülmez**.
- Eşleşen oyun sayısı 1000'i aşarsa → **422** `ProblemDetail`, `code: GAME_SEARCH_TOO_BROAD` ("metni uzatın"); sonuç kırpılmaz. Sınır `ltz.event.game-search.max-games` (varsayılan 1000 = en çok 10 sayfa).
- Bu iki durum yalnız oyuna bağlı açık etkinlik varken ve `q` verildiğinde oluşur.

## 4. Game sözleşmesinin sınırları ve etkisi (neden "tam" değil)

Mevcut sözleşme çalışan dev Game Service'e karşı da gözlendi (yanıt alanları; `size=101`, `page=10001`, eksik/boş `q` → 400; `q=a` için `totalElements = 8352`).

| # | Sınır | Etki |
| --- | --- | --- |
| S-1 | **Önek** araması (kelime ortası eşleşmez) | Oyun adı eşleşmesi başlık eşleşmesinden dar: "witcher" → "The Witcher 3" **bulunmaz**; "the w…" bulunur. |
| S-2 | Yalnız **ACTIVE + MAIN_GAME** | DLC'ye bağlı etkinlik oyun adıyla **bulunamaz** (bağlantı ise her ACTIVE türe izinli, EVT-17 VR-1). |
| S-3 | Eksiksiz sonuç için tüm eşleşmelerin okunması gerekir | `q=a` 8352 oyunla eşleşir → 1000 sınırı aşılır → **422**. Kısa metinler (≈1–2 harf) oyuna bağlı etkinlik varken çalışmaz. |
| S-4 | Arama ucu için istek sınırı belgesiz | Bir arama en çok 11 sıralı çağrı yapar; 429 → 503. |

Bunlar [Game talebi req-01](../game-service/talep/req-01.md) ile iletildi. **Tercih edilen çözüm (S-3):** Game'de verilen `gameId` kümesini ada göre süzen bir uç; böylece Event yalnız açık etkinliklerdeki az sayıda `gameId`'yi sorar (eksiksiz ve ucuz). Geçici bir çözümle (ör. yalnız ilk sayfa, oyun adını kopyalama) tamamlandı **denmedi**.

## 5. Varsayımlar

| # | Varsayım | Gerekçe |
| --- | --- | --- |
| VR-1 | Arama, liste kapsamıyla aynı kümede yapılır (yaklaşan + devam eden; iptal/tamamlanmış/bitmiş yok). | EVT-04 ile tutarlılık; kullanıcı kararı da aynı yönde. |
| VR-2 | Başlıkta "içerir", oyun adında "başlar" (Game'in sözleşmesi). | Sınır S-1; talep S-1. |
| VR-3 | `q` en çok 100 karakter, min uzunluk yok. | Başlık sınırı 120; Game `q` sınırı 128. |
| VR-4 | Üst sınır 1000 oyun (yapılandırılabilir). | Tek aramada en çok 10 sayfa çağrısı; `IN` listesi bağlama sınırının çok altında. |
| VR-5 | Performans: `lower(title) like '%…%'` indeks kullanmaz; `game_id in (…)` kısmi indeks `ix_events_game_id` kullanır. | Veri hacmi küçükken sorun değil; büyürse `pg_trgm` (yeni eklenti kararı). |

## 6. Doğrulama durumu

**Doğrulanan:**
- Derleme ve Checkstyle/PMD (`lint-ratchet`: event-service 0 yeni ihlal) — ajan çalıştırdı.
- Game Service arama sözleşmesi çalışan dev servise karşı **salt-okunur** gözlendi (yukarıda).
- Dev PostgreSQL'de Türkçe/Unicode `lower()`/`ilike` davranışı (elle `select`).

**Yazılan testler (çalıştırılmadı):** `EventSearchTest`, `EventServiceTest` (başlık, başlık-veya-oyun, Game çağrısının atlanması, 503/422'nin yutulmaması), `GameServiceClientTest` (sözleşme, çok sayfa, 1000 sınırı, hata/tutarsızlık, kodlama; `MockRestServiceServer`), `EventControllerTest`, `EventEndpointSecurityTest`, `EventRepositoryTest` (gerçek PostgreSQL: başlık-veya-oyun, tek sefer dönme, kapsam, sıra+sayfalama, açıklama aranmaz, 1000 `gameId`).

**Doğrulanmayan (NOT VERIFIED):** tüm yeni testler; JPQL sorgularının (`escape '!'`, `lower(:pattern)`, `in :gameIds`) Hibernate'te çözümlenmesi; V113/V114; gerçek Game Service'e karşı uçtan uca arama; Gateway üzerinden çağrı; gerçek Auth token'ı.

**Docker:** event-service container'ı EVT-03 kodunda (V112); jar'da `GameServiceClient`, `EventSearch`, liste/arama sorguları ve V113/V114 yok; `events.game_id` veritabanında yok. **Güncel değil**, yeniden build edilmedi.
