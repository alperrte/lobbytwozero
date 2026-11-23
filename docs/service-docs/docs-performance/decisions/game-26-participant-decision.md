# GAME-26: Performance referans katılımcısı kararı

2026-11-18 · Sahip: Alper · Durum: **KAPATILDI, uygulanmayacak**

## Karar

Performance Service, GAME-26 oyun dış kimlik düzeltme (REASSIGN/SPLIT) akışında referans katılımcısı olmayacak.
`prepare`, `finalize` ve karar sorgusu uçları, kalıcı operasyon kaydı ve gameId bazlı yazma engeli Performance'a eklenmeyecek.

## Kaynak

[GAME-26 sözleşmesi](../../docs-Game%20Service/GAME-26-Servis-Sahipleri-Entegrasyon-Sozlesmesi.md) §5 Performance'ı beş katılımcıdan biri sayar
(`PerformanceSession.gameId`). Bu karar Performance tarafındaki seçimi kapatır; sözleşmenin sahibi Hamza'dır ve belgesi bu kararla değiştirilmedi.

## Sonuç ve Game tarafı için not

- Performance'ta bu iş için kod, migration, ENV veya `docs/api.md` kaydı yoktur.
- Sözleşme gereği eksik veya devre dışı katılımcı `0 referans` sayılmaz. Performance katılımcı listesinde kalırsa Game'in
  `FOREIGN_REFERENCE_SAFETY_UNVERIFIED` koruması kalkmaz ve GAME-26 kimlik mutasyonu tamamlanamaz.
- Hamza'nın seçeneği: Performance'ı katılımcı listesinden çıkarıp kararını belgelemek (Performance oyun kimliğine bağlı oturum
  verisini başka yolla ele almak) veya bu kararı geri çevirmek.

## Recommendation etkisi

Yok. Recommendation `gameId` saklamaz; Performance verisini yalnız okur.
