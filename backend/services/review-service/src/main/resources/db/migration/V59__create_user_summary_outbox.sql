-- REV-10: Review -> User "ReviewSummaryChanged" için kullanıcı başına birleştirilmiş (coalesced) outbox.
-- Satır, kullanıcının görünür inceleme sayısının User'a iletilmesi gerektiğini belirtir; sayı gönderim anında Review
-- verisinden hesaplanır. occurred_at kullanıcı başına KESİN artandır (User eşit/eski zamanlı olayı yok sayar).
CREATE TABLE review_user_summary_outbox (
    user_id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz,
    last_error varchar(32),
    CONSTRAINT ck_review_user_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_review_user_outbox_error CHECK (last_error IS NULL OR last_error ~ '^[A-Z0-9_]{1,32}$')
);
CREATE INDEX ix_review_user_outbox_due ON review_user_summary_outbox (next_attempt_at, occurred_at) WHERE delivered_at IS NULL;
