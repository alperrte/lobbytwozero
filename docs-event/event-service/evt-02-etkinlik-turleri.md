# EVT-02 — Etkinlik türleri: kaynaklar, kararlar ve varsayımlar

> Durum: **IN PROGRESS, doğrulama bekliyor.** Kod ve testler yazıldı; testler ve V112 migration'ı **çalıştırılmadı** (Docker dev veritabanında V112 henüz uygulanmadı).
> Son güncelleme: 2026-11-09 · Sorumlu: Nisa · Önceki görev: [EVT-01](evt-01-kararlar-ve-varsayimlar.md)

## 1. Gereksinim ve kararlar

| # | Kural | Kaynak |
| --- | --- | --- |
| G-1 | Turnuva, oyun gecesi, topluluk etkinliği ve oylama gibi türler desteklenir. | `LTZ_Servis_Icerikleri_Nisa.md` EVT-02 |
| G-2 | Platform içinde turnuva, oyun gecesi, topluluk oylaması veya benzeri etkinlikler oluşturulabilir. | `LTZ Gereksinim Dokümanı` FR-CONT-05 |
| G-3 | `type` değerleri TOURNAMENT, GAME_NIGHT, COMMUNITY, OTHER (`varchar` + CHECK). | `docs/database.md` §3.12 |
| K-1 | Oylama ayrı bir etkinlik türüdür; oy verme davranışı EVT-20'de. | Karar KD-2 (Nisa, 2026-11-09) |

## 2. Yapılanlar

- `EventType`: `TOURNAMENT, GAME_NIGHT, COMMUNITY, POLL, OTHER` (5 değer).
- **V112** `V112__add_poll_event_type.sql`: `ck_events_type` kaldırılıp beş değerle yeniden eklenir. V111 uygulanmış olduğundan değiştirilmedi. Mevcut satırlar eski dört değeri taşıdığı için yeniden ekleme güvenlidir; veri silinmez.
- Oluşturma uç noktası (`POST /api/v1/event/events`) `type` olarak beş değeri de kabul eder; `docs/api.md` ve `docs/database.md` güncellendi.
- Testler: `EventTypeTest` (enum ↔ V112 CHECK tutarlılığı, V111 alt küme), `EventControllerTest` (POLL kabul; `poll`, `VOTING`, `OYLAMA`, boş değer 400).

## 3. Varsayımlar (gereksinimde yazmıyor)

| # | Varsayım | Gerekçe |
| --- | --- | --- |
| VR-1 | Oylama türünün kodu **`POLL`**. | `docs/database.md`'deki `event_polls` ile uyumlu; kararda yalnız "ayrı tür" denmiş, kod adı belirtilmemiş. Başka ad istenirse `EventType` + yeni migration + `docs/api.md` değişir. |
| VR-2 | `COMMUNITY` "topluluk etkinliği", `OTHER` "benzeri/diğer" etkinlikler içindir; ikisi ayrı kalır. | FR-CONT-05 "veya benzeri" der; `database.md` ikisini de tanımlar. |
| VR-3 | Türe özel **hiçbir** alan veya doğrulama yoktur (ör. turnuvada oyun zorunlu, oylamada seçenek zorunlu değil). | Gereksinimde yok; turnuva EVT-18, oyun gecesi EVT-19 (oyun+zaman), oylama EVT-20 kapsamında. İş kuralı uydurulmadı. |
| VR-4 | Tür değerleri API'de büyük harf, tam eşleşme; küçük harf/Türkçe ad kabul edilmez. | Mevcut enum davranışı; sözleşme belirsizliğini önler. |
| VR-5 | Türleri listeleyen ayrı bir uç (`GET .../event-types`) **eklenmedi**. | EVT-02 "desteklenmesi" der, listeleme istemez; frontend sabit listeyi kullanabilir. Gerekirse EVT-06 (filtreleme) ile birlikte ele alınır. |

## 4. Etkilenen / etkilenmeyen

- **Etkilenmeyen:** `events` tablosu kolonları, durum kuralları, rol kuralları (KD-1), zaman kuralları.
- **EVT-14 (güncelleme):** Etkinlik türünün oluşturulduktan sonra değiştirilebilmesi henüz karara bağlanmadı (şu an güncelleme ucu yok).
- **Mevcut veri:** Dev veritabanında `events` boş (EVT-01 doğrulamasında 0 satır); yine de migration mevcut dört değerli satırları bozmaz.

## 5. Doğrulanmadı (NOT VERIFIED)

- V112, hiçbir veritabanında uygulanmadı; `ddl-auto=validate` ve `ck_events_type`'ın yeni değeri DB'de denenmedi. `EventServiceApplicationTests.contextLoads` ve gerçek PostgreSQL bunu dener, **çalıştırılmadı**.
- Hiçbir test çalıştırılmadı; yalnız derleme ve Checkstyle/PMD (`lint-ratchet`: 0 yeni ihlal) yerel olarak denendi.
- Docker'daki çalışan event-service imajı EVT-02 öncesi koddadır; yeniden build gerekir.
- DB seviyesinde `POLL` satırı yazılabildiği doğrulanmadı (DB testi altyapısı yok).
