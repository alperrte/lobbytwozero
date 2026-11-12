-- REV-15/REV-07: moderasyon durumları (HIDDEN, ARCHIVED), rapor kapatma ve moderasyon denetim kaydı.
-- Moderasyon uçları Review'ın kendisindedir (Admin servisine teslim yok); bu yüzden Admin teslim işareti kaldırılır.

-- İnceleme durumları: görünür olmayan (HIDDEN/ARCHIVED) incelemenin metni saklanır; DELETED yalnız kullanıcı silmesidir.
ALTER TABLE reviews DROP CONSTRAINT ck_reviews_status;
ALTER TABLE reviews DROP CONSTRAINT ck_reviews_content;
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN', 'ARCHIVED', 'DELETED'));
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_content CHECK (
    (status IN ('VISIBLE', 'HIDDEN', 'ARCHIVED') AND body IS NOT NULL AND body ~ '[^[:space:]]' AND deleted_at IS NULL)
    OR (status = 'DELETED' AND body IS NULL AND deleted_at IS NOT NULL)
);

-- Rapor kapatma: açık rapor RECEIVED'dır; moderatör REVIEWED (işlem yapıldı) veya DISMISSED (reddedildi) ile kapatır, yeniden açılmaz.
DROP INDEX ix_review_reports_undelivered;
ALTER TABLE review_reports DROP COLUMN delivered_at;
ALTER TABLE review_reports DROP CONSTRAINT ck_review_reports_status;
ALTER TABLE review_reports ADD COLUMN closed_at timestamptz;
ALTER TABLE review_reports ADD COLUMN closed_by uuid;
ALTER TABLE review_reports ADD CONSTRAINT ck_review_reports_status CHECK (status IN ('RECEIVED', 'REVIEWED', 'DISMISSED'));
ALTER TABLE review_reports ADD CONSTRAINT ck_review_reports_closure CHECK (
    (status = 'RECEIVED' AND closed_at IS NULL AND closed_by IS NULL)
    OR (status <> 'RECEIVED' AND closed_at IS NOT NULL AND closed_at >= created_at)
);
-- Yönetim listesi: duruma göre gruplama.
CREATE INDEX ix_review_reports_status_review ON review_reports (status, review_id);

-- Moderasyon denetim kaydı: kim (moderatör hesabı; hesap silinirse NULL), ne, neden, hangi inceleme sürümünde.
CREATE TABLE review_moderation_actions (
    id uuid PRIMARY KEY,
    review_id uuid NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    actor_id uuid,
    action varchar(32) NOT NULL,
    reason varchar(500) NOT NULL,
    review_version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_review_moderation_action CHECK (action IN ('HIDE', 'ARCHIVE', 'RESTORE', 'REPORTS_REVIEWED', 'REPORTS_DISMISSED')),
    CONSTRAINT ck_review_moderation_reason CHECK (reason ~ '[^[:space:]]'),
    CONSTRAINT ck_review_moderation_version CHECK (review_version >= 0)
);
CREATE INDEX ix_review_moderation_review ON review_moderation_actions (review_id, created_at, id);
CREATE INDEX ix_review_moderation_actor ON review_moderation_actions (actor_id) WHERE actor_id IS NOT NULL;
