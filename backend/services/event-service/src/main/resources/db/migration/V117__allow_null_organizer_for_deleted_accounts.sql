-- Hesap silme (karar KD-3, Nisa 2026-11-09): silinen hesabın etkinliklerinde düzenleyici bağlantısı kaldırılır.
-- organizer_id artık null olabilir; null = "silinmiş kullanıcı" (etiket API/arayüz gösteriminde üretilir, DB'de metin yok).
-- V111 uygulanmış bir migration olduğundan değiştirilmez.

alter table events alter column organizer_id drop not null;
