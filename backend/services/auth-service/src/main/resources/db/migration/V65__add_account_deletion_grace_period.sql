-- AUTH-16 ek: silme için geri alma (bekleme) süresi. Talep, scheduled_for anına kadar iptal edilebilir; görevler de bu
-- andan önce çalışmaz (account_deletion_tasks.next_attempt_at = scheduled_for). Mevcut talepler beklemesiz sayılır.
ALTER TABLE account_deletion_requests ADD COLUMN scheduled_for timestamptz;
ALTER TABLE account_deletion_requests ADD COLUMN cancelled_at timestamptz;

UPDATE account_deletion_requests SET scheduled_for = requested_at;

ALTER TABLE account_deletion_requests ALTER COLUMN scheduled_for SET NOT NULL;

ALTER TABLE account_deletion_requests DROP CONSTRAINT ck_account_deletion_requests_status;
ALTER TABLE account_deletion_requests ADD CONSTRAINT ck_account_deletion_requests_status
    CHECK (status IN ('COORDINATING', 'COMPLETED', 'CANCELLED'));
ALTER TABLE account_deletion_requests ADD CONSTRAINT ck_account_deletion_requests_cancellation
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL));
ALTER TABLE account_deletion_requests ADD CONSTRAINT ck_account_deletion_requests_schedule
    CHECK (scheduled_for >= requested_at);
