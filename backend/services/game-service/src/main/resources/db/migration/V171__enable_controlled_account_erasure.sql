-- Hamza + Kerem: yalnız hesap silmede dar kapsamlı audit anonimleştirmesi.
-- Mevcut V31–V40 değiştirilmez. V171–V180 tahsisi: 2026-10-24.
-- Bu tablo yalnız aynı transaction içindeki yetki bağlamıdır; başarılı çağrıda boş kalır.
create table game_account_erasure_context (
    id uuid primary key default gen_random_uuid(),
    transaction_id bigint not null unique,
    account_id uuid not null check (account_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    created_at timestamptz not null default clock_timestamp(),
    version bigint not null default 0
);
revoke all on table game_account_erasure_context from public;

create or replace function reject_game_admin_audit_mutation() returns trigger
language plpgsql security definer set search_path = pg_catalog as $$
declare
    erased uuid;
    nil_id constant uuid := '00000000-0000-0000-0000-000000000000';
begin
    if tg_op <> 'UPDATE' then
        raise exception 'Game admin audit is append-only';
    end if;
    select account_id into erased from public.game_account_erasure_context
        where transaction_id = txid_current();
    if erased is null
        or (old.actor_id <> erased
            and position(erased::text in lower(coalesce(old.reason, '') || ' ' || coalesce(old.details, ''))) = 0
            and not exists (select 1 from public.game_developer_feedback f where f.author_id = erased
                and old.details like 'feedback=' || f.id::text || ';%')
            and not exists (select 1 from public.game_developer_media m where m.author_id = erased
                and old.details = 'target=MEDIA,targetId=' || m.id::text)
            and not exists (select 1 from public.game_update_notes n where n.author_id = erased
                and old.details = 'target=UPDATE_NOTE,targetId=' || n.id::text))
        or new.actor_id is distinct from (case when old.actor_id = erased then nil_id else old.actor_id end)
        or new.reason is not null or new.details is not null
        or (to_jsonb(new) - array['actor_id','reason','details'])
            is distinct from (to_jsonb(old) - array['actor_id','reason','details']) then
        raise exception 'Game admin audit is append-only';
    end if;
    -- Satır seçimi yalnız erase_game_account_data içinde yapılır; bağlam tablosu
    -- uygulama rolüne açılmaz. Burada normal alanların değişmesi de kesin engellenir.
    return new;
end;
$$;

create or replace function reject_game_identity_audit_mutation() returns trigger
language plpgsql security definer set search_path = pg_catalog as $$
declare
    erased uuid;
    nil_id constant uuid := '00000000-0000-0000-0000-000000000000';
begin
    if tg_op <> 'UPDATE' then
        raise exception 'Game identity audit is append-only';
    end if;
    select account_id into erased from public.game_account_erasure_context
        where transaction_id = txid_current();
    if erased is null
        or (old.actor_id <> erased and position(erased::text in lower(old.evidence)) = 0)
        or new.actor_id is distinct from (case when old.actor_id = erased then nil_id else old.actor_id end)
        or new.evidence is distinct from '[silinen hesap: metin kaldırıldı]'
        or (to_jsonb(new) - array['actor_id','evidence'])
            is distinct from (to_jsonb(old) - array['actor_id','evidence']) then
        raise exception 'Game identity audit is append-only';
    end if;
    return new;
end;
$$;

create function erase_game_account_data(erased uuid) returns void
language plpgsql security definer set search_path = pg_catalog set lock_timeout = '1s' as $$
declare
    nil_id constant uuid := '00000000-0000-0000-0000-000000000000';
begin
    if erased is null or erased = nil_id then
        raise exception 'Invalid account erasure identity' using errcode = '22023';
    end if;

    -- Silme sırasında yeni REF yazılması ile satır taramasının yarışmasını engeller.
    -- Transaction bitince serbest bırakılır; kalıcı hesap tombstone'u oluşturulmaz.
    lock table public.games, public.game_identity_audits, public.game_admin_audits,
        public.game_field_overrides, public.game_developer_grants,
        public.game_developer_contents, public.game_developer_media,
        public.game_update_notes, public.game_developer_feedback
        in share row exclusive mode;

    insert into public.game_account_erasure_context(transaction_id, account_id)
        values (txid_current(), erased);

    -- Hedef kişi audit.details içinde bulunabilir; aktör başkasıysa o kimlik korunur.
    -- Regex ile metin temizlenmez: ilgili metin alanları tamamen kaldırılır.
    update public.game_admin_audits a
        set actor_id = case when a.actor_id = erased then nil_id else a.actor_id end,
            reason = null, details = null
        where a.actor_id = erased
            or position(erased::text in lower(coalesce(a.reason, '') || ' ' || coalesce(a.details, ''))) > 0
            or exists (select 1 from public.game_developer_feedback f where f.author_id = erased
                and a.details like 'feedback=' || f.id::text || ';%')
            or exists (select 1 from public.game_developer_media m where m.author_id = erased
                and a.details = 'target=MEDIA,targetId=' || m.id::text)
            or exists (select 1 from public.game_update_notes n where n.author_id = erased
                and a.details = 'target=UPDATE_NOTE,targetId=' || n.id::text);

    update public.game_identity_audits
        set actor_id = case when actor_id = erased then nil_id else actor_id end,
            evidence = '[silinen hesap: metin kaldırıldı]'
        where actor_id = erased or position(erased::text in lower(evidence)) > 0;

    update public.games set editor_actor_id = null, editor_evidence = null,
        version = version + 1, updated_at = clock_timestamp() where editor_actor_id = erased;
    update public.game_field_overrides set actor_id = nil_id, version = version + 1 where actor_id = erased;

    delete from public.game_developer_grants where developer_user_id = erased;
    update public.game_developer_grants
        set granted_by = case when granted_by = erased then nil_id else granted_by end,
            -- revoked_at/revoked_by CHECK korunur; revoke edilmiş yetki açılmaz.
            revoked_by = case when revoked_by = erased then nil_id else revoked_by end,
            version = version + 1
        where granted_by = erased or revoked_by = erased;

    delete from public.game_developer_contents where author_id = erased;
    delete from public.game_developer_media where author_id = erased;
    delete from public.game_update_notes where author_id = erased;
    delete from public.game_developer_feedback where author_id = erased;

    -- Hesap yalnız moderatörse başkasının içeriği korunur.
    update public.game_developer_contents set moderated_by = null, moderation_reason = null,
        version = version + 1, updated_at = clock_timestamp() where moderated_by = erased;
    update public.game_developer_media set moderated_by = null, moderation_reason = null,
        version = version + 1, updated_at = clock_timestamp() where moderated_by = erased;
    update public.game_update_notes set moderated_by = null, moderation_reason = null,
        version = version + 1, updated_at = clock_timestamp() where moderated_by = erased;

    delete from public.game_account_erasure_context where transaction_id = txid_current();
end;
$$;

-- PUBLIC'a hesap silme veya bağlam tablosu yetkisi verilmez. Migration sahibi
-- çalıştırabilir; ayrı runtime rolü varsa yalnız erase_game_account_data(uuid)
-- EXECUTE yetkisi deployment'ta açıkça tahsis edilir. Audit/context DML verilmez.
revoke all on function erase_game_account_data(uuid) from public;
revoke all on function reject_game_admin_audit_mutation() from public;
revoke all on function reject_game_identity_audit_mutation() from public;
