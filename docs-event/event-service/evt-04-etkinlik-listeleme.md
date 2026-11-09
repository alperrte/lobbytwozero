# EVT-04 — Etkinlik listeleme: gereksinim karşılama durumu, varsayımlar ve doğrulama

> Durum: **IN PROGRESS, doğrulama bekliyor.** Kod ve testler yazıldı; **testler, V113 migration'ı ve liste sorgusu hiçbir veritabanında çalıştırılmadı.** Docker'daki event-service imajı EVT-04 öncesi koddadır.
> Son güncelleme: 2026-11-09 · Sorumlu: Nisa · Önceki görevler: [EVT-01](evt-01-kararlar-ve-varsayimlar.md), [EVT-02](evt-02-etkinlik-turleri.md), [EVT-03 (kısmi)](evt-03-etkinlik-detayi.md)

## 1. Kaynaklar

| # | Kaynak | İçerik |
| --- | --- | --- |
| K-1 | `LTZ_Servis_Icerikleri_Nisa.md` EVT-04 | "Yaklaşan ve mevcut etkinliklerin listelenebilmesi." |
| K-2 | `LTZ Gereksinim Dokümanı` FR-CONT-05/06 | Etkinlik tarih/açıklama/katılım bilgisi içerir; kullanıcı etkinlik takvimini görüntüler (takvim = EVT-07). |
| K-3 | `docs/SECURITY.md` §9, §10 | Sayfalama limiti sunucuda zorunlu; dinamik sıralama/filtre alanları allowlist ile sınırlı; `ProblemDetail`. |
| K-4 | Karar KD-4 (Nisa, 2026-11-09) | Etkinlik görüntüleme yalnız giriş yapan kullanıcılara açık; misafir 401. |
| K-5 | `docs/architecture.md` §8 | Etkinlik durumları `PLANNED → ACTIVE → COMPLETED`, `PLANNED/ACTIVE → CANCELLED`. |

## 2. Gereksinim karşılama tablosu

| # | Gereksinim | Durum | Kodlandı | Doğrulandı | Docker'da |
| --- | --- | --- | --- | --- | --- |
| 1 | **Yaklaşan** etkinlikler listelenir | **Karşılandı** (varsayım VR-1) | `GET /api/v1/event/events` → `EventService.listEvents`, `EventRepository.findByEndsAtAfter...` | Testler yazıldı; çalıştırılmadı | Hayır (imaj EVT-04 öncesi) |
| 2 | **Mevcut** (devam eden) etkinlikler listelenir | **Karşılandı** (varsayım VR-1) | aynı sorgu (`endsAt > şimdi`) | aynı | Hayır |
| 3 | İptal/tamamlanmış etkinlikler listelenmez | **Karşılandı** (varsayım VR-2) | durum filtresi `PLANNED, ACTIVE` | aynı | Hayır |
| 4 | Sayfalama sunucuda sınırlı | **Karşılandı** (SECURITY.md §9) | `EventListRequest` (`size` 1–50, varsayılan 20; `page` 0–1000) | `EventControllerTest` yazıldı; çalıştırılmadı | Hayır |
| 5 | Yalnız giriş yapan kullanıcı görür; misafir 401 | **Karşılandı** (KD-4) | `SecurityConfig` (`anyRequest().authenticated()`) | `EventEndpointSecurityTest` yazıldı; çalıştırılmadı | Hayır |
| 6 | Liste öğesinde katılım bilgisi (kapasite, sayı) | **Kapsam dışı — EVT-04 metni istemiyor** | — | — | — |
| 7 | Arama / filtreleme / sıralama seçenekleri | **Kapsam dışı** (EVT-05, EVT-06) | Sıralama sabit: başlangıç zamanı artan | — | — |
| 8 | Takvim görünümü | **Kapsam dışı** (EVT-07) | — | — | — |

EVT-04'ün kendi ifadesi ("yaklaşan ve mevcut etkinliklerin listelenebilmesi") bu sürümde karşılanmıştır; **doğrulama** (testlerin ve sorgunun çalıştırılması) senden beklenir. Liste öğelerinde katılım bilgisi EVT-04'ün metninde yoktur; ileride gerekirse EVT-13/EVT-08 sonrası eklenir (bkz. [EVT-03 takip listesi](evt-03-etkinlik-detayi.md) §3.1).

## 3. Davranış

`GET /api/v1/event/events?page=&size=` — etkinlikleri **başlangıç zamanına göre artan** (eşitlikte kimliğe göre) sıralı, sayfalı döndürür. Bir etkinlik şu koşulun tümünü sağlıyorsa listelenir:

- `endsAt > şimdi` (henüz bitmemiş: ya başlamamış = **yaklaşan**, ya başlamış ve sürüyor = **mevcut**) **ve**
- `status ∈ {PLANNED, ACTIVE}`.

Yanıt: `items` (özet: kimlik, düzenleyici, tür, başlık, başlangıç, bitiş, durum — **açıklama yok**), `page`, `size`, `totalItems`, `totalPages`, `hasNext`.

## 4. Varsayımlar (gereksinimde yazmıyor — onay bekliyor)

| # | Varsayım | Gerekçe / sonuç |
| --- | --- | --- |
| VR-1 | "Yaklaşan" ve "mevcut" **zamana göre** türetilir (`endsAt > şimdi`); durum alanına tek başına bakılmaz. | Durum geçişleri (`PLANNED → ACTIVE → COMPLETED`, EVT-16) henüz uygulanmadı; yalnız `status = ACTIVE` ile "mevcut" etkinlik hiç görünmezdi ve süresi geçmiş ama durumu hâlâ `PLANNED` olan etkinlik "yaklaşan" görünürdü. EVT-16 gelince bu sorgu yeniden değerlendirilir. |
| VR-2 | `CANCELLED` ve `COMPLETED` etkinlikler listelenmez. | İptal/tamamlanmış etkinlik "yaklaşan veya mevcut" değildir. Bunlar yine de ID ile detayda (EVT-03) görülebilir. Geçmiş etkinlik listesi EVT-23'tür. |
| VR-3 | Sıralama sabit: `startsAt` artan, sonra `id`. | Gereksinim sıralama seçeneği istemiyor (EVT-06); kararlı sayfalama için kimlik ikincil anahtar. |
| VR-4 | Varsayılan sayfa boyutu 20, en çok 50; sayfa en çok 1000. | SECURITY.md sınır ister ama sayı vermez; community ile aynı mertebe. |
| VR-5 | Liste öğesi özet DTO'dur (açıklama yok). | Açıklama 4000 karaktere kadardır; liste yükünü şişirmemek için. Ayrıntı EVT-03 ucundan. |
| VR-6 | Sayfalama sayfa/boyut (offset) tabanlıdır, toplam sayı döner. | Basit ve takvim/arama görevleriyle uyumlu; çok büyük veri setinde keyset'e geçmek gerekebilir. |
| VR-7 | Yeni kısmi indeks `ix_events_open_starts_at (starts_at, id) WHERE status IN ('PLANNED','ACTIVE')` (V113). | Liste sorgusunun sıralama/sayfalamasını karşılamak için. Yalnız indeks; tablo/veri değişmez. |

## 5. Yapılanlar

- `GET /api/v1/event/events` (sayfa/boyut); `EventListRequest`, `EventSummaryResponse`, `EventPageResponse`; `EventMapper.toSummary/toPage`; `EventService.listEvents`; `EventRepository.findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc`.
- **V113** `V113__add_open_events_index.sql`.
- Hata sözleşmesi: geçersiz `page`/`size` (negatif, sınır aşımı, sayı değil) → 400 `ProblemDetail`; token yok/geçersiz → 401.
- Testler: `EventServiceTest` (saat, durum kümesi, sayfalama, varsayılanlar, kimliksiz), `EventControllerTest` (200, sayfalama parametreleri, varsayılanlar, geçersiz parametreler, 401), `EventEndpointSecurityTest` (beş rolle 200; token yok/bozuk/`svc+jwt` 401; sınır aşımı 400).
- **Gerçek PostgreSQL testi** `EventRepositoryTest`: V111–V113'ü çalıştırır (`ddl-auto=validate`); yaklaşan+mevcut+aktif sıralaması, bitmiş/iptal/tamamlanmış hariç tutma, tam şimdi biten etkinlik, sayfalama ve toplamlar, aynı başlangıçta kararlı sıra; ayrıca `POLL`'un saklanabildiği ve CHECK kısıtlarının (bilinmeyen tür/durum, `ends_at <= starts_at`) reddettiği.

## 6. Test altyapısı değişikliği (dikkat)

- Yeni test bağımlılığı: `org.testcontainers:testcontainers-postgresql:2.0.5` (community-service ile aynı sürüm/kalıp; **SECURITY.md §2 gereği bağımlılık incelemesi sende**) ve `maven-surefire-plugin` `TESTCONTAINERS_RYUK_DISABLED=true`.
- Yeni `PostgreSqlIntegrationTest` (community'deki kalıp): `POSTGRES_PASSWORD` tanımlıysa (pre-push) sunucuda **geçici, rastgele adlı** bir veritabanı açıp JVM sonunda siler; değilse Docker'da Testcontainers PostgreSQL 17 başlatır.
- **`EventServiceApplicationTests` artık bu geçici veritabanını kullanır.** Önceden `event_db`'ye (ortak dev veritabanı) bağlanıp migration uyguluyordu (V112'yi bu şekilde uygulamıştı). Bundan sonra pre-push `event_db`'yi değiştirmez; dev veritabanına migration, event-service container'ının başlamasıyla uygulanır.
- Testcontainers yoluna düşülürse Docker gerekir; pre-push `POSTGRES_PASSWORD` verdiği için normalde geçici veritabanı yolu kullanılır.

## 7. Doğrulanmadı (NOT VERIFIED)

- Hiçbir EVT-04 testi çalıştırılmadı; yalnız derleme ve Checkstyle/PMD (`lint-ratchet`: 0 yeni ihlal) yerel olarak denendi.
- **V113 ve liste sorgusu hiçbir PostgreSQL'de çalıştırılmadı**; `EventRepositoryTest` bunu sınar ama çalıştırılmadı. Sıralama/sayfalama davranışı bu yüzden **doğrulanmış değildir**.
- Kısmi indeksin sorgu planında kullanıldığı (`EXPLAIN`) denenmedi.
- Docker'daki event-service imajı EVT-04 öncesidir: `GET /api/v1/event/events` (liste) ucu imajda yoktur; `ix_events_open_starts_at` dev veritabanında yoktur.
- Gerçek Auth token'ı ve Gateway üzerinden çağrı denenmedi.
- Bu sürümde liste kimliksiz çağrılırsa `GET /api/v1/event/events` 401 döner (aynı yol eski imajda da 401 döndüğü için bu gözlem EVT-04'ün varlığını kanıtlamaz).
