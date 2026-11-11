package com.ltz.messaging.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.application.port.AccountDataEraserPort;

import lombok.RequiredArgsConstructor;

/**
 * Hesap verisi silme, set tabanlı SQL (docs-messaging/TASK-07-TASARIM.md §1). Sıra:
 * <ol>
 *   <li>Hesabın sohbetleri kilitlenir (eşzamanlı gönderimle {@code seq} ve üyelik sırası korunur).</li>
 *   <li>Hesabın mesajlarının ve hesabın verdiği tepkiler, hesabın ek bağlantıları (gönderilmemişler dahil) ve başka
 *       bağlantısı kalmayan ek içerikleri silinir.</li>
 *   <li>Hesabın mesajları herkesten silinmiş gibi temizlenir; gönderen ve silen anonim kimliğe, istemci kimliği mesaj
 *       kimliğine çevrilir (satır sıra ve okundu bilgisi için kalır).</li>
 *   <li>Gizleme, okuma aralığı ve üyelik satırları silinir; başkalarının satırlarındaki hesap izleri anonimleşir.</li>
 *   <li>Aktif sahibi silinen gruba ayrılma kuralıyla yeni sahip atanır; aktif üyesi kalmayan grup silinir; birebir
 *       sohbet anahtarı hesabın kimliğini içermeyecek biçimde değişir.</li>
 *   <li>Bekleyen outbox olaylarından hesap çıkarılır; oran sayaçları ve canlı akış aktarım kayıtları silinir.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JdbcAccountDataEraser implements AccountDataEraserPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void erase(UUID accountId, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("account", accountId)
                .addValue("accountText", accountId.toString())
                .addValue("anonymous", ANONYMOUS_ACCOUNT)
                .addValue("now", Timestamp.from(now));
        List<UUID> conversations = jdbc.queryForList("""
                select c.id from conversations c
                where c.id in (select m.conversation_id from conversation_members m where m.user_id = :account)
                order by c.id
                for update""", params, UUID.class);
        List<UUID> ownedGroups = jdbc.queryForList("""
                select m.conversation_id from conversation_members m
                join conversations c on c.id = m.conversation_id
                where m.user_id = :account and m.left_at is null and m.role = 'OWNER' and c.type = 'GROUP'""",
                params, UUID.class);

        eraseMessages(params);
        eraseMemberships(params, conversations, ownedGroups);
        eraseOutbox(params);
    }

    private void eraseMessages(MapSqlParameterSource params) {
        jdbc.update("""
                delete from message_reactions
                where user_id = :account or message_id in (select id from messages where sender_id = :account)""",
                params);
        List<UUID> blobs = jdbc.queryForList("""
                delete from message_attachments
                where uploader_id = :account or message_id in (select id from messages where sender_id = :account)
                returning blob_id""", params, UUID.class);
        if (!blobs.isEmpty()) {
            jdbc.update("""
                    delete from attachment_blobs b
                    where b.id in (:blobs)
                      and not exists (select 1 from message_attachments a where a.blob_id = b.id)""",
                    new MapSqlParameterSource("blobs", blobs));
        }
        jdbc.update("""
                update messages set body = null, gif_provider = null, gif_id = null, gif_url = null,
                    gif_preview_url = null, gif_width = null, gif_height = null,
                    deleted_at = coalesce(deleted_at, :now), deleted_by = :anonymous,
                    sender_id = :anonymous, client_message_id = id::text
                where sender_id = :account""", params);
        jdbc.update("update messages set deleted_by = :anonymous where deleted_by = :account", params);
        jdbc.update("delete from message_hidden_for_users where user_id = :account", params);
        jdbc.update("delete from message_read_ranges where user_id = :account", params);
        jdbc.update("delete from rate_limit_windows where user_id = :account", params);
        // Kısa ömürlü canlı akış aktarım kayıtları (≤ 5 dk) da hesabın kimliğini ve yüklerini taşıyabilir.
        jdbc.update("delete from stream_relay_events where position(:accountText in payload) > 0", params);
    }

    private void eraseMemberships(MapSqlParameterSource params, List<UUID> conversations, List<UUID> ownedGroups) {
        jdbc.update("delete from conversation_members where user_id = :account", params);
        jdbc.update("update conversation_members set removed_by = :anonymous where removed_by = :account", params);
        if (!ownedGroups.isEmpty()) {
            // Ayrılma kuralıyla aynı sıra: önce ADMIN, sonra en eski katılan, eşitlikte kimlik.
            jdbc.update("""
                    update conversation_members m set role = 'OWNER'
                    from (select distinct on (c.conversation_id) c.conversation_id, c.user_id
                          from conversation_members c
                          where c.conversation_id in (:owned) and c.left_at is null
                          order by c.conversation_id, (c.role <> 'ADMIN'), c.joined_at, c.user_id) s
                    where m.conversation_id = s.conversation_id and m.user_id = s.user_id""",
                    new MapSqlParameterSource("owned", ownedGroups));
        }
        if (!conversations.isEmpty()) {
            jdbc.update("""
                    delete from conversations c
                    where c.id in (:conversations) and c.type = 'GROUP'
                      and not exists (select 1 from conversation_members m
                                      where m.conversation_id = c.id and m.left_at is null)""",
                    new MapSqlParameterSource("conversations", conversations));
        }
        jdbc.update("""
                update conversations set direct_key = 'erased:' || id::text
                where type = 'DIRECT' and position(:accountText in direct_key) > 0""", params);
        jdbc.update("update conversations set created_by = :anonymous where created_by = :account", params);
    }

    private void eraseOutbox(MapSqlParameterSource params) {
        jdbc.update("delete from outbox_events where payload ->> 'senderId' = :accountText", params);
        jdbc.update("""
                update outbox_events
                set payload = jsonb_set(payload, '{recipientIds}', (payload -> 'recipientIds') - cast(:accountText as text))
                where payload -> 'recipientIds' @> jsonb_build_array(cast(:accountText as text))""", params);
    }
}
