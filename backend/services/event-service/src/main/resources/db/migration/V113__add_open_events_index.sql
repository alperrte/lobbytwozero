-- EVT-04: yaklaşan ve mevcut etkinliklerin listelenmesi. Liste sorgusu yalnız PLANNED/ACTIVE etkinlikleri başlangıç
-- zamanına göre (starts_at, id) sıralı ve sayfalı okur; bu kısmi indeks sıralamayı ve sayfalamayı karşılar.
-- Yalnız indekstir: tablo, kolon veya veri değişmez.

create index ix_events_open_starts_at on events (starts_at, id) where status in ('PLANNED', 'ACTIVE');
