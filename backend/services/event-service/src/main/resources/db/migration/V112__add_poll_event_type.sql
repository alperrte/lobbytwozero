-- EVT-02: etkinlik türleri. Karar KD-2 (Nisa, 2026-11-09): oylama, etkinliğe bağlı bir özellik değil AYRI bir etkinlik
-- türüdür. ck_events_type, V111'deki dört değere POLL ekleyerek yeniden tanımlanır. V111 uygulanmış bir migration
-- olduğundan değiştirilmez. Mevcut satırlar yalnız eski dört değeri taşıdığı için kısıt güvenle yeniden eklenir.
-- Oy verme davranışı (event_polls, event_poll_options, event_poll_votes) EVT-20'de eklenir; bu migration tablo eklemez.

alter table events drop constraint ck_events_type;
alter table events add constraint ck_events_type
    check (type in ('TOURNAMENT', 'GAME_NIGHT', 'COMMUNITY', 'POLL', 'OTHER'));
