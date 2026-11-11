-- REV-07: kullanıcıların uygunsuz inceleme raporları (Review tarafı). Admin'e teslim henüz yoktur: delivered_at NULL = iletilmedi.
CREATE TABLE review_reports (
    id uuid PRIMARY KEY,
    review_id uuid NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    reporter_id uuid NOT NULL,
    reason varchar(32) NOT NULL,
    detail varchar(500),
    status varchar(32) NOT NULL DEFAULT 'RECEIVED',
    delivered_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_review_reports_reason CHECK (reason IN ('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'PERSONAL_INFO', 'SPOILER', 'OFF_TOPIC', 'OTHER')),
    CONSTRAINT ck_review_reports_status CHECK (status IN ('RECEIVED')),
    CONSTRAINT ck_review_reports_detail CHECK (detail IS NULL OR detail ~ '[^[:space:]]'),
    CONSTRAINT ck_review_reports_version CHECK (version >= 0),
    CONSTRAINT ck_review_reports_times CHECK (updated_at >= created_at AND (delivered_at IS NULL OR delivered_at >= created_at))
);
-- Mükerrer rapor engeli: aynı raportör aynı incelemeyi yalnız bir kez raporlayabilir.
CREATE UNIQUE INDEX uq_review_reports_review_reporter ON review_reports (review_id, reporter_id);
CREATE INDEX ix_review_reports_reporter ON review_reports (reporter_id);
-- Admin teslimi için bekleyen raporlar (delivered_at NULL).
CREATE INDEX ix_review_reports_undelivered ON review_reports (created_at, id) WHERE delivered_at IS NULL;
