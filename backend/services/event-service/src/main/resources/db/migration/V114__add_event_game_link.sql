-- EVT-17: etkinlik–oyun bağlantısı. game_id, game-service'teki oyunun kimliğine mantıksal referanstır (REF): FK yoktur,
-- oyun varlığı oluşturma sırasında Game Service ile doğrulanır. Oyun ADI bu tabloya kopyalanmaz (karar KD-5: ad
-- araması çalışma anında Game Service üzerinden yapılır). Mevcut etkinliklerin oyunu yoktur (null).
-- EVT-05 oyun adına göre arama bulunan oyun kimlikleriyle "game_id in (...)" sorgular; bu kısmi indeks onu karşılar.

alter table events add column game_id uuid;

create index ix_events_game_id on events (game_id) where game_id is not null;
