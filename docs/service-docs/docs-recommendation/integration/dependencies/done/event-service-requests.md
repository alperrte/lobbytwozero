# TASK 4 — Event keşif ve katılım uygunluğu sözleşmesi talebi

2026-11-15 · REC-15 · Sahip: **Nisa** · **KARŞILANDI (2026-11-18)** — keşif ve eligibility kapandı; açık madde kalmadı.

## Karşılanma (2026-11-18, ikinci tur)

Alper, Nisa'nın tarafındaki eksiği de Event Service'e ekledi (EVT-08/09/13, V116/V117; kayıt: `docs/api.md` EVENT).
**Karar (sözleşme):** ayrı bir batch eligibility ucu **açılmadı**; mevcut `GET /api/v1/event/events` yanıtı izleyiciye özel
`capacity`, `participantCount`, `remainingCapacity`, `viewerParticipation` ve `joinable` alanlarını taşır. `joinable` = izleyicinin
aktif katılımı yok, etkinlik açık ve bitmemiş, dolu değil, izleyici düzenleyen değil. Recommendation `joinable=false` olanı önermez
ve alanları sıkı doğrular (eksik/bozuk alan 503). Katılım mutasyonunda kapasite Event'te yeniden denetlenir (öneri rezervasyon değildir).
Katılım cezası (EVT-10/11) bir katılım kuralıdır, öneriyle ilgisizdir; cezalı kullanıcıya da öneri yapılır, sınırlama bildirilmez.
Sahibi (Nisa) bu sözleşme kararını onaylamalı; farklı bir batch ucu isterse Recommendation adaptörü yalnız `HttpEventDiscoverySource` içinde değişir.

## Güncel durum (2026-11-18)

Event dalı main'e alındı (PR #149) ve Recommendation dalına birleştirildi. Event'te EVT-01..06 ve EVT-17 vardır
(V111–V115). Recommendation **keşif** tarafını Event'in mevcut `GET /api/v1/event/events` (EVT-04/05/06) ucuyla uyguladı:
`GET /api/v1/recommendation/events` (REC-15, `event-affinity-v1`, stateless). Kullanıcı token'ı iletilir; tahmini path veya
DB okuması yoktur. Kayıt: `docs/api.md` RECOMMENDATION "Yaklaşan etkinlik önerisi (REC-15)".

| Talep maddesi | Durum |
| --- | --- |
| Sınırlı sayfalı keşif, ≤50/sayfa, sabit sıra | **Karşılandı** (EVT-04: `startsAt` artan, size ≤50); Recommendation en çok 4 sayfa okur, kesilirse `EVENT_SCAN_TRUNCATED` |
| eventId, görünür özet, gameId/tür, platform | **Karşılandı** (EVT-06 `platforms`, EVT-17 `gameId`); tür kodları Game GAME-15'ten |
| UTC Instant zamanlar | **Karşılandı** (`startsAt`/`endsAt` Instant) |
| Cancelled/past dışlama | **Karşılandı**: Event yalnız PLANNED/ACTIVE ve `endsAt > şimdi` döner; Recommendation ayrıca kendi saatiyle süzer |
| Açık/özel görünürlük | **N/A**: KD-4 gereği tüm giriş yapmış kullanıcılar görür; özel etkinlik kavramı yok |
| Katılım durumu, kapasite/dolu | **Karşılandı** (EVT-08/13): liste alanları `viewerParticipation`, `capacity`, `participantCount`, `remainingCapacity`, `joinable` |
| Kayıt penceresi, katılım şartları | **N/A** — kayıt penceresi kavramı yok (etkinlik bitene kadar açık); ceza öneriyi etkilemez, katılımda Event uygular |
| Batch eligibility ucu | **Gerekmedi** — `joinable` liste yanıtında; ayrı uç açılmadı (karar yukarıda) |
| 429/503/timeout | Recommendation tarafı: tek deneme, 403→`SOURCE_ACCESS_DENIED`, diğer hatalar 503 `SOURCE_UNAVAILABLE` |

**Nisa için kalan istek:** yok. Katılıma uygunluk (`joinable`) gerçek veridir; EVT-10/11 gelse de Recommendation değişmez.

## Eski kanıt (2026-11-15, artık geçersiz)

O tarihte `event-service` yalnız `EventServiceApplication` içeriyordu ve `docs/api.md` EVENT bölümünde kayıt yoktu.

## İstenen en dar kabiliyet

Nisa'nın sahibi olduğu sözleşme üzerinden izleyicinin görebileceği geçerli etkinlik/turnuvaları
sınırlı sayfada keşfetme ve batch eligibility. Route/DTO/izin adı owner kararı bekler.

- Kanonik eventId, sınırlı görünür özet ve varsa kanonik gameId/tür bağlantısı; bilginin
  kaynağı ve kişiselleştirmede kullanılabilirliği açık olmalı. Popülerlik sayacı uydurulmamalı.
- Açık/özel görünürlük, yayın/iptal/tamamlanma durumu, başlangıç/bitiş ve kayıt penceresi;
  zamanlar offset içeren ISO-8601 Instant/UTC semantiğiyle dönmeli.
- Çağıranın katılım durumu, erişim/yaş/ceza gibi mevcut katılım şartları ve kapasite uygunluğu
  Event tarafından değerlendirilmelidir; özel katılımcı listesi Recommendation'a dönmemeli.
- Private/full/cancelled/past veya kayıt penceresi kapanmış etkinlik dışlanmalı. ACTIVE
  etkinliğe kayıt mümkün mü, başvuru kesim saati nedir: owner'ın gerçek kuralı belirleyicidir.
- Stable cursor/sıra, limit ve batch tamlık semantiği tanımlanmalı; başlangıç talebi ≤50/sayfa.
  İptal/kapasite değişikliği önbellekte öneri üretmeye devam ettirmemeli.
- Öneri katılım rezervasyonu değildir; katılım mutasyonunda kapasite/izin yeniden Event'te
  denetlenmelidir. Recommendation kullanıcı adına katılım veya başka mutasyon yapmaz.
- Auth/user bağlamı, private enumeration koruması, 401/403/404/429/503 ve timeout davranışı
  belgelenmeli. Eksik API için tahmini path'e çağrı veya doğrudan DB okuma yapılmayacak.

## Beklenen owner kabul kanıtı

Açık/özel/dolu/iptal/tamamlanmış etkinlik, katılmış kullanıcı, kayıt sınır saatleri ve farklı
saat dilimlerinde aynı Instant, kapasite yarışı, batch/paging ve timeout testleri + API kaydı.
İzinli User tercih verisiyle oyun/tür ilişkisi de netleşince Recommendation deterministik
sıralama/gerekçe testi ekleyebilir. Mevcut olumlu etkinlik önerisi **NOT IMPLEMENTED**.
