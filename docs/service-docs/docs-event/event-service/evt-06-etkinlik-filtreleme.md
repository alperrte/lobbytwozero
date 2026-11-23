# EVT-06 — Etkinlik filtreleme: gereksinim karşılama durumu

> **EVT-06: dört filtre de kodlandı (oyun, etkinlik türü, tarih, platform); doğrulama bekliyor.** Platform, etkinliğin **kendi alanıdır** (karar KD-6/A): bağlı oyundan türetilmez. Platform **sözlüğü** (PC, PLAYSTATION, XBOX, NINTENDO_SWITCH, MOBILE, VR) **KD-6a ile onaylandı** (Nisa, 2026-11-11; Content Service ile aynı altı değer) — bu bir **karar kaydıdır, işlevsel doğrulama değildir** (§4 ve §7 ayrı). Testler ve sorgular **çalıştırılmadı**; Docker imajı güncel, ama token'la davranış doğrulanamadı.
> Son güncelleme: 2026-11-11 · Sorumlu: Nisa · İlgili: [EVT-04](evt-04-etkinlik-listeleme.md), [EVT-05](evt-05-etkinlik-arama.md), [EVT-17](evt-17-oyun-baglantisi.md)

## 1. Kaynaklar

| # | Kaynak | İçerik |
| --- | --- | --- |
| K-1 | `LTZ_Servis_Icerikleri_Nisa.md` EVT-06 | "Oyun, etkinlik türü, tarih ve platform kriterleriyle filtreleme yapılabilmesi." |
| K-2 | Karar KD-6/A (Nisa, 2026-11-11) | Platform etkinliğin kendi alanı; bağlı oyundan otomatik türetilmez; oyuna bağlanmamış etkinlik de platformla filtrelenebilir. |
| K-2a | Karar KD-6a (Nisa, 2026-11-11) | Platform sözlüğü: `PC`, `PLAYSTATION`, `XBOX`, `NINTENDO_SWITCH`, `MOBILE`, `VR` (Content Service ile aynı altı değer). **Onaylandı.** |
| K-3 | `docs/SECURITY.md` §9, §10 | Dinamik filtre alanları allowlist ile sınırlı; sayfalama sunucuda sınırlı; parametrik sorgu; `ProblemDetail`. |
| K-4 | Karar KD-4 | Etkinlik görüntüleme yalnız giriş yapan kullanıcılara açık; misafir 401. |
| K-5 | `docs/database.md` §3.12 (+ V115) | `events`; `event_platforms` (V115). |

## 2. Gereksinim karşılama tablosu (dört filtre)

| Filtre | Durum | Davranış | Kodlandı | Doğrulandı | Docker'da (imaj / veritabanı) |
| --- | --- | --- | --- | --- | --- |
| **Oyun** (`gameId`) | **Kodlandı** | `events.game_id = ?` (oyunun varlığı denetlenmez) | `EventSearchRepositoryImpl` | Testler yazıldı, çalıştırılmadı | İmajda var, `game_id` kolonu var; davranış token'la denenmedi |
| **Etkinlik türü** (`type`) | **Kodlandı** | Tekrarlanabilir/virgüllü, en çok 5, VEYA | aynı | aynı | İmajda var; davranış denenmedi |
| **Tarih** (`from`, `to`) | **Kodlandı** | Süre kesişimi `[from, to)`: `startsAt < to` ve `endsAt > from`; ikisi isteğe bağlı | aynı | aynı | İmajda var; davranış denenmedi |
| **Platform** (`platform`) | **Kodlandı** (sözlük KD-6a **onaylandı**) | Etkinliğin **kendi** platformlarından en az biri verilenlerden biri (VEYA, en çok 6); **platformu belirtilmemiş etkinlikler eşleşmez**; verilmezse filtre yok | aynı (`exists` alt sorgusu) | aynı | İmajda var; `event_platforms` tablosu var (V115); davranış denenmedi |
| Filtreler birbiriyle ve `q` aramasıyla VE; EVT-04 kapsamı/sırası/sayfalaması korunur | **Kodlandı** | tek kriter sorgusu | `EventSearchRepository.search` | `EventRepositoryTest` (yazıldı) | İmajda var |
| Geçersiz değer → 400 `ProblemDetail` | **Kodlandı** | tür, platform, UUID, tarih, aralık, sınırlar | `EventListRequest` | `EventControllerTest` (yazıldı) | Token olmadığından 400 gözlenemedi (yetkisiz çağrı 401 döner) |
| Misafir 401 | **Karşılandı (Docker'da gözlendi)** | token'sız `GET` (platform filtreli dahil) → 401 | `SecurityConfig` | Gateway üzerinden gözlendi | **Gözlendi** |

"Kodlandı", testlerin çalıştırılıp geçtiği anlamına gelmez; yukarıdaki tek **gözlenmiş** davranış token'sız 401'dir.

## 3. Davranış

`GET /api/v1/event/events` — EVT-04/EVT-05 ile aynı uç:

| Parametre | Anlam |
| --- | --- |
| `type` | Etkinlik türü; birden çok verilebilir (`type=TOURNAMENT&type=POLL` veya virgüllü), kümeden biri. En çok 5; tanımsız değer 400. |
| `platform` | **Etkinliğin kendi platformu**: `PC`, `PLAYSTATION`, `XBOX`, `NINTENDO_SWITCH`, `MOBILE`, `VR`; birden çok verilebilir (kümeden biri). En çok 6; tanımsız/küçük harfli değer (`PS5`, `pc`) 400. **Platformu belirtilmemiş etkinlikler eşleşmez.** Birden çok platformu eşleşen etkinlik **bir kez** döner. Verilmezse filtre yoktur ve platformsuz etkinlikler de listelenir. |
| `gameId` | Etkinliğin bağlı olduğu oyun (UUID). |
| `from`, `to` | UTC ISO-8601 anları; süre `[from, to)` ile kesişiyorsa döner; ikisi isteğe bağlı; ikisi verilirse `to > from`; 1970–9999 arası. |

Tüm filtreler birbirine ve `q` aramasına **VE** ile bağlanır; liste kapsamı (`endsAt > şimdi`, `PLANNED/ACTIVE`) her zaman uygulanır. Filtre kullanan bir istek Game Service'i çağırmaz.

**Platform alanı (oluşturma ve yanıtlar):**
- `POST /api/v1/event/events` gövdesine isteğe bağlı `platforms` (dizi, en çok 6, yinelenenler birleşir). Verilmezse etkinlik platformsuzdur.
- Detay, liste ve oluşturma yanıtlarında `platforms` (enum sırasında dizi; platformsuzsa `[]`).
- Platform **bağlı oyundan türetilmez**: oyuna bağlı ama platformu verilmemiş etkinlik platform filtresinde çıkmaz; oyuna bağlı olmayan ama platformlu etkinlik çıkar.
- **Mevcut etkinliklere platform atanmadı** (V115 satır eklemez); dev veritabanında zaten etkinlik yoktur (0 satır).

## 4. Platform sözlüğü — KD-6a: ONAYLANDI

**Karar (Nisa, 2026-11-11):** Event Service, Content Service ile **aynı altı platform değerini** kullanır: `PC`, `PLAYSTATION`, `XBOX`, `NINTENDO_SWITCH`, `MOBILE`, `VR`. Platform etkinliğin **kendi alanı** olarak kalır (KD-6/A; bağlı oyundan türetilmez).

**Sözlük taraması (karara gerekçe):** LTZ'de tek, kodlanmış ve servisler arası paylaşılan bir platform sözlüğü yoktu:

| Kaynak | Sözlük | Not |
| --- | --- | --- |
| Content Service (`content_categories`, V103; `docs/database.md`) | **Sabit LTZ listesi:** `PC, PLAYSTATION, XBOX, NINTENDO_SWITCH, MOBILE, VR` (`^[A-Z][A-Z0-9_]{1,31}$`) | **Onaylanan sözlük.** Kendi DB'sinde referans veri; paylaşılan API'si yok. LFP-03 gereksinimi de aynı kaba kümeyi örnekler ("PC, PlayStation, Xbox veya diğer"). |
| Game Service (`catalog_terms kind=PLATFORM`; `GET /api/v1/game/terms?kind=PLATFORM`) | İnce taneli sağlayıcı kodları (`win`, `mac`, `linux`, `switch`, `ps4--1`, `ps5`, `xboxone`, `series-x-s`, `ios`, `android`, …) | Kullanılmaz: sağlayıcıya bağlı, değişken. |
| User Service (`favorite platforms`) | Serbest katalog kodları (küçük harf) | Kullanılmaz. |

**Onayın gerekçesi:** LTZ'nin kendi sabit, belgelenmiş listesi; ince taneli sağlayıcı kodları etkinlik filtresi için gereksiz ve kararsız (`ps4--1`); Game'e doğrulama bağımlılığı yok (oluşturma ve filtre Game'siz çalışır); kod biçimi diğer Event enum'larıyla uyumlu.

**Bilinen sınırlar (karar bunları kabul eder):** tek `MOBILE` (iOS/Android ayrımı yok), `PC` (Windows/Mac/Linux ayrımı yok), "diğer/Web" değeri yok. **Sözlük ileride değişirse:** `EventPlatform` enum + CHECK'i güncelleyen yeni migration (V116+; V115 değiştirilmez) + testler + belgeler.

### 4.1 Karar kaydı — işlevsel doğrulamadan ayrı

| Konu | Durum | Kanıt / not |
| --- | --- | --- |
| **KD-6a kararı** | **ONAYLANDI** (Nisa, 2026-11-11) | Bu belge; açık karar listesinden çıkarıldı (`docs-event/README.md`) |
| Kod ile sözlüğün uyumu (statik karşılaştırma) | **Uyumlu** (ajan dosyaları karşılaştırdı, 2026-11-11) | `EventPlatform` = V115 `ck_event_platforms_platform` = Content V103 PLATFORM tohumu: altı değer, aynı sıra, kod biçimi Content'in `^[A-Z][A-Z0-9_]{1,31}$` kuralına uyar. Kod değişikliği gerekmedi |
| Enum↔CHECK tutarlılığının **otomatik** denetimi | Test yazıldı (`EventPlatformTest`), **çalıştırılmadı** | Kullanıcı çalıştıracak |
| Dev veritabanında kısıt | **Gözlendi** | `ck_event_platforms_platform` altı değerle `pg_constraint`'ten okundu (11 Kasım, Docker) |
| **Platform işlevselliği** (oluşturma, filtre, VE birleşimi, 400) | **DOĞRULANMADI** | Testler çalıştırılmadı; token'lı çağrı yapılamadı (bkz. §7) |

Karar onayı, platform filtresinin çalıştığının kanıtı **değildir**; EVT-06'nın kapanışı §7-§8'deki doğrulamaya bağlıdır.

## 5. Varsayımlar (gereksinimde yazmıyor)

| # | Varsayım | Gerekçe / sonuç |
| --- | --- | --- |
| VR-1 | "Tarih" filtresi **süre kesişimi** (`startsAt < to` ve `endsAt > from`); `to` hariç. | "Bugün olan etkinlikler" dünden başlayıp süren etkinlikleri de kapsar. Yalnız başlangıç saatine bakmak istenirse tek koşul değişir. |
| VR-2 | Zamanlar UTC an (`Instant`); tarih-yalnız (`2026-12-05`) kabul edilmez. | Saat dilimi belirsizliğini önler. |
| VR-3 | Tür ve platform filtrelerinde birden çok değer VEYA'dır. | Sınırlar enum boyutu (5 / 6). |
| VR-4 | `gameId` filtresi oyunun varlığını Game Service'te doğrulamaz. | Salt kimlik eşleştirmesi. |
| VR-5 | Filtre kapsamı liste kapsamıyla sınırlıdır (geçmiş/iptal/tamamlanmış yok). | EVT-04/05 ile tutarlılık; geçmiş EVT-23. |
| VR-6 | Etkinlik başına en çok 6 platform (sözlük boyutu); yinelenenler birleşir. | Sözlük 6 değerdir. |
| VR-7 | Platform, oluşturulduktan sonra değiştirilemez (güncelleme ucu yok). | EVT-14 (güncelleme) kapsamı. |

## 6. Teknik değişiklik

- **V115** `V115__create_event_platforms_table.sql`: `event_platforms (event_id FK ON DELETE CASCADE, platform, PK (event_id, platform), CHECK platform sözlüğü)` + `ix_event_platforms_platform`. Mevcut satırlar değişmez; platform atanmaz. **Not:** dosyanın başlık yorumu yazıldığı sırada "sözlük kararı açık (KD-6a)" der; V115 dev veritabanına uygulandığı için değiştirilmez (Flyway checksum). Güncel durum bu belgededir: KD-6a onaylandı.
- `EventPlatform` enum (domain), `Event.platforms` (`@ElementCollection`, EAGER + `@BatchSize(100)`: liste sorgusunda N+1 yok; salt okunur getter, enum sırası), `CreateEventRequest.platforms`, `EventResponse`/`EventSummaryResponse.platforms`, `EventListRequest.platform`, `EventSearchCriteria.platforms`, `EventSearchRepositoryImpl` (korelasyonlu `exists` alt sorgusu: birden çok platform eşleşse de tek satır).
- Önceki turdaki kriter tabanlı sorgu (tür/oyun/tarih) korunur; platform onun bir koşuludur.

## 7. Doğrulama durumu

**Doğrulanan (kanıtı olanlar):**
- Derleme ve Checkstyle/PMD: `lint-ratchet` event-service 0 yeni ihlal (ajan çalıştırdı).
- **Docker (imaj ve veritabanı ayrı):** çalışan imajın jar'ında EVT-01..06, EVT-17 ve platform kodu var; `flyway_schema_history` 111–115 `success`, V113–V115 bu başlatmada uygulandı; `events.game_id`, `event_platforms`, indeksler ve `ck_event_platforms_platform` var; servis `ddl-auto=validate` ile hatasız başladı; sağlık 200; token'sız çağrılar 401.

**Yazılan testler (çalıştırılmadı):** `EventPlatformTest` (enum↔V115), `EventTest`, `EventServiceTest`, `EventControllerTest`, `EventEndpointSecurityTest`, `EventRepositoryTest` (gerçek PostgreSQL: platform kalıcılığı/sırası, CHECK ve cascade, tek başına filtre, platformsuzun eşleşmemesi, oyundan bağımsızlık, çoklu platform tek sefer, kapsam, tür+oyun+tarih ile VE, tarih kesişimi, başlık-veya-oyun aramasıyla VE, sayfalama toplamı).

**Doğrulanmayan (NOT VERIFIED):** tüm yeni testler; Criteria `exists` alt sorgusunun ve `@ElementCollection` eşlemesinin sorgu davranışı (servis başladığı için eşleme `validate`'ten geçti, ama sorgular çalıştırılmadı); geçerli token'la 201/200/400/422/503 davranışları; Event → Game çağrısı; Gateway üzerinden token'lı çağrı.

**Ortam bulgusu (Event kodundan bağımsız):** bu oturumda Docker Desktop motoru kendiliğinden yeniden başladı; sonrasında Auth'un JWKS ucu ve Game'in herkese açık uçları doğrudan ve Gateway'den 401 döndü (kod ve belgeler bu uçları herkese açık tanımlar; 9 Kasım'da 200'dü). Nedeni teşhis edilmedi, başka servislere dokunulmadı. Event'in token doğrulaması Auth JWKS'ine bağlıdır; bu durum düzelmeden token'la uçtan uca doğrulama yapılamaz.

## 8. Token'la yapılacak kontroller (sende)

Geçerli bir `PLAYER`/`MODERATOR`/`ADMIN` token'ıyla (Gateway: `/api/v1/event/events`):
1. `POST` `{"platforms":["PC","XBOX"], ...}` → 201, yanıtta `platforms:["PC","XBOX"]`; `platforms` olmadan → 201, `platforms:[]`; `["PS5"]` → 400.
2. `GET ?platform=PC` → yalnız PC'li etkinlikler; platformsuz etkinlik **çıkmamalı**; `?platform=PS5` → 400 ProblemDetail.
3. Oyuna bağlı ama platformsuz bir etkinlik `?platform=PC`'de **çıkmamalı**; oyunsuz PC'li etkinlik çıkmalı.
4. `?platform=PC&type=TOURNAMENT&gameId=…&from=…&to=…` birlikte; `?platform=PC&q=…`.
5. Platform verilmeden liste: platformlu ve platformsuz etkinlikler birlikte.
