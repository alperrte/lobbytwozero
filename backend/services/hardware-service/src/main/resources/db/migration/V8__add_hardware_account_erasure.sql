-- Approved with Auth owner: targeted personal audit erasure and a 17-minute pseudonymous fence.
-- V1-V7 remain immutable. No other service database is accessed.
CREATE TABLE hardware_account_erasure_fences (
    account_hash varchar(64) CONSTRAINT pk_hardware_account_erasure_fences PRIMARY KEY,
    expires_at timestamptz NOT NULL,
    CONSTRAINT ck_hardware_account_erasure_fences_hash CHECK (account_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_hardware_account_erasure_fences_expiry ON hardware_account_erasure_fences(expires_at);
CREATE INDEX ix_hardware_review_audit_actor ON hardware_review_audit(actor_id);
CREATE INDEX ix_hardware_review_reports_reporter ON hardware_review_reports(reporter_id);
CREATE INDEX ix_outbox_events_aggregate ON outbox_events(aggregate_id);
CREATE INDEX ix_outbox_events_owner ON outbox_events((payload->'payload'->>'ownerId'));

CREATE OR REPLACE FUNCTION reject_hardware_catalog_audit_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.actor_id::text = current_setting('ltz.hardware.erasure_account', true) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Catalog audit is append-only' USING ERRCODE = '23514';
END;
$$;
DROP TRIGGER hardware_catalog_audit_immutable ON hardware_catalog_audit;
CREATE TRIGGER hardware_catalog_audit_immutable BEFORE UPDATE OR DELETE ON hardware_catalog_audit
    FOR EACH ROW EXECUTE FUNCTION reject_hardware_catalog_audit_change();
CREATE TRIGGER hardware_catalog_audit_no_truncate BEFORE TRUNCATE ON hardware_catalog_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_hardware_catalog_audit_change();

CREATE OR REPLACE FUNCTION reject_hardware_review_audit_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('ltz.hardware.erasure_account', true) <> '' AND
        (OLD.actor_id::text = current_setting('ltz.hardware.erasure_account', true) OR EXISTS (
            SELECT 1 FROM hardware_reviews r WHERE r.id=OLD.target_id
            AND r.user_id::text=current_setting('ltz.hardware.erasure_account', true))) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Review audit is append-only' USING ERRCODE = '23514';
END;
$$;
DROP TRIGGER hardware_review_audit_immutable ON hardware_review_audit;
CREATE TRIGGER hardware_review_audit_immutable BEFORE UPDATE OR DELETE ON hardware_review_audit
    FOR EACH ROW EXECUTE FUNCTION reject_hardware_review_audit_change();
CREATE TRIGGER hardware_review_audit_no_truncate BEFORE TRUNCATE ON hardware_review_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_hardware_review_audit_change();

-- Ordinary application audit writes remain append-only. This function is called only by the
-- authenticated D4 adapter in a transaction holding the same account lock as all user writes.
-- No SECURITY DEFINER, trigger disabling, autonomous transaction or session-wide bypass.
CREATE FUNCTION erase_hardware_account(account_id uuid) RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE
    review_ids uuid[];
    profile_ids uuid[];
BEGIN
    -- Bound parent cascades (at most 200 components + 500 receipts per profile at the application boundary).
    SELECT ARRAY(SELECT id FROM hardware_profiles WHERE user_id=account_id ORDER BY id LIMIT 20) INTO profile_ids;
    -- Cross-user report/moderation also locks target reviews, before touching their children.
    SELECT ARRAY(SELECT id FROM hardware_reviews WHERE user_id=account_id ORDER BY id LIMIT 20 FOR UPDATE) INTO review_ids;
    PERFORM set_config('ltz.hardware.erasure_account',account_id::text,true);
    DELETE FROM hardware_catalog_audit WHERE id IN (
        SELECT id FROM hardware_catalog_audit WHERE actor_id=account_id ORDER BY id LIMIT 1000);
    DELETE FROM hardware_review_audit WHERE id IN (
        SELECT id FROM hardware_review_audit WHERE actor_id=account_id OR target_id=ANY(review_ids) ORDER BY id LIMIT 1000);
    DELETE FROM outbox_events WHERE id IN (
        SELECT id FROM outbox_events WHERE aggregate_id=ANY(review_ids)
            OR id IN (SELECT outbox_event_id FROM hardware_review_reports WHERE reporter_id=account_id)
            OR payload->'payload'->>'ownerId'=account_id::text ORDER BY id LIMIT 1000);
    -- Keep the report link until its event is removed; ON DELETE SET NULL marks processed links.
    DELETE FROM hardware_review_reports WHERE id IN (
        SELECT id FROM hardware_review_reports WHERE (reporter_id=account_id OR review_id=ANY(review_ids))
            AND outbox_event_id IS NULL ORDER BY id LIMIT 1000);
    -- Do not orphan audit/outbox references or acknowledge a parent with unfinished children.
    DELETE FROM hardware_reviews r WHERE id=ANY(review_ids)
        AND NOT EXISTS(SELECT 1 FROM hardware_review_reports x WHERE x.review_id=r.id)
        AND NOT EXISTS(SELECT 1 FROM hardware_review_audit x WHERE x.target_id=r.id)
        AND NOT EXISTS(SELECT 1 FROM outbox_events x WHERE x.aggregate_id=r.id);
    DELETE FROM hardware_write_limits WHERE bucket_key IN ('actor:'||account_id, 'moderator:'||account_id,
        'sync-user:'||account_id, 'sync-apply-user:'||account_id)
        OR bucket_key LIKE 'sync-device:'||account_id||':%'
        OR bucket_key IN (SELECT 'sync-apply-device:'||c.device_id FROM hardware_sync_consents c WHERE c.profile_id=ANY(profile_ids));
    -- Cascades remove components, field/category privacy, consent and cached sync receipts.
    DELETE FROM hardware_profiles WHERE id=ANY(profile_ids);
    PERFORM set_config('ltz.hardware.erasure_account','',true);
    RETURN NOT (
        EXISTS(SELECT 1 FROM hardware_profiles WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM hardware_reviews WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM hardware_review_reports WHERE reporter_id=account_id)
        OR EXISTS(SELECT 1 FROM hardware_catalog_audit WHERE actor_id=account_id)
        OR EXISTS(SELECT 1 FROM hardware_review_audit WHERE actor_id=account_id)
        OR EXISTS(SELECT 1 FROM outbox_events WHERE payload->'payload'->>'ownerId'=account_id::text));
END;
$$;
