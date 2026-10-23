-- Development schema baseline: initialize an empty hardware_db.
-- External user, actor and device UUIDs are logical references, never cross-service foreign keys.

CREATE TABLE outbox_events (
    id uuid CONSTRAINT pk_outbox_events PRIMARY KEY,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type varchar(128) NOT NULL,
    event_version integer NOT NULL DEFAULT 1,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    sent_at timestamptz,
    CONSTRAINT ck_outbox_events_aggregate CHECK (aggregate_type = 'HARDWARE_REVIEW'),
    CONSTRAINT ck_outbox_events_type CHECK (event_type IN ('ContentReported','HardwareReviewModerated')),
    CONSTRAINT ck_outbox_events_version CHECK (event_version = 1),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING','SENT','FAILED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_events_payload CHECK (jsonb_typeof(payload) = 'object' AND octet_length(payload::text) <= 4096)
);

CREATE INDEX ix_outbox_events_status_next ON outbox_events(status,next_attempt_at);

CREATE TABLE hardware_review_reports (
    id uuid CONSTRAINT pk_hardware_review_reports PRIMARY KEY,
    review_id uuid NOT NULL CONSTRAINT fk_hardware_review_reports_review REFERENCES hardware_reviews(id),
    reporter_id uuid NOT NULL,
    reason varchar(32) NOT NULL,
    details varchar(500),
    outbox_event_id uuid CONSTRAINT fk_hardware_review_reports_event REFERENCES outbox_events(id) ON DELETE SET NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_hardware_review_reports_review_reporter UNIQUE(review_id,reporter_id),
    CONSTRAINT uq_hardware_review_reports_event UNIQUE(outbox_event_id),
    CONSTRAINT ck_hardware_review_reports_reason CHECK (reason IN
        ('SPAM','HARASSMENT','HATE_SPEECH','SEXUAL_CONTENT','PERSONAL_DATA','OTHER')),
    CONSTRAINT ck_hardware_review_reports_details CHECK (details IS NULL OR length(btrim(details)) BETWEEN 1 AND 500),
    CONSTRAINT ck_hardware_review_reports_version CHECK (version >= 0)
);

CREATE INDEX ix_hardware_review_reports_review_created ON hardware_review_reports(review_id,created_at,id);
