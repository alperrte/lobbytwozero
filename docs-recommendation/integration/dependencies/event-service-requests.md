# TASK 4 — Event keşif ve katılım uygunluğu sözleşmesi talebi

2026-11-15 · REC-15 · Sahip: **Nisa** · **BLOCKED / NOT IMPLEMENTED**.

## Mevcut kanıt

`backend/services/event-service/src/main/java` yalnız `EventServiceApplication` içerir.
`docs/api.md` EVENT bölümünde kayıt yoktur. Gateway route'u çalışan etkinlik iş API'si değildir.
Mimari/DB belgelerindeki etkinlik durumları ve tablolar hedef tasarımdır; canlı etkinlik,
katılım veya kapasite kanıtı olarak kullanılamaz. Bu task Event kodunu/DB'sini değiştirmez.

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
