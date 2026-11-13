-- LTZ-UMT-MSG-08: outbox teslimi (relay) ve saklama süresi.

-- Saklama temizliği olayları oluşma zamanına göre siler (durumdan bağımsız, docs-messaging/TASK-08.md): her mesaj bir
-- MessageSent olayı ürettiğinden tablo hızlı büyür; temizlik sorgusu tam tablo taraması yapmasın.
create index ix_outbox_events_occurred_at on outbox_events (occurred_at);
