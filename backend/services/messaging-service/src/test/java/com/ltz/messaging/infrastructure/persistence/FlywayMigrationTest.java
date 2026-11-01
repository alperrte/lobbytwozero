package com.ltz.messaging.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.domain.entity.Conversation;

/** V131 şeması gerçek PostgreSQL'de: tür/başlık tutarlılığı, birebir tekilliği ve tek aktif sahip DB'de zorlanır. */
class FlywayMigrationTest extends IntegrationTestSupport {

    @Test
    void migrationUygulanmisVeTablolarVar() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(versions).contains("131");
        assertThat(tables).contains("conversations", "conversation_members");
    }

    @Test
    void turBaslikVeBirebirAnahtariTutarliOlmali() {
        assertThatThrownBy(() -> insertConversation("DIRECT", null, null))
                .hasMessageContaining("ck_conversations_direct_key");
        assertThatThrownBy(() -> insertConversation("GROUP", "Takım", "a:b"))
                .hasMessageContaining("ck_conversations_direct_key");
        assertThatThrownBy(() -> insertConversation("GROUP", null, null))
                .hasMessageContaining("ck_conversations_title");
        assertThatThrownBy(() -> insertConversation("GROUP", "", null))
                .hasMessageContaining("ck_conversations_title");
        assertThatThrownBy(() -> insertConversation("DIRECT", "Başlık", "c:d"))
                .hasMessageContaining("ck_conversations_title");
        // PostgreSQL CHECK kısıtlarını ada göre sırayla denetler; bilinmeyen tür ilk olarak başlık kuralına takılır.
        assertThatThrownBy(() -> insertConversation("CHANNEL", "Takım", null))
                .hasMessageContaining("ck_conversations_");
    }

    @Test
    void ayniCiftIcinIkinciBirebirSohbetOlusamaz() {
        String key = Conversation.directKey(UUID.randomUUID(), UUID.randomUUID());
        insertConversation("DIRECT", null, key);

        assertThatThrownBy(() -> insertConversation("DIRECT", null, key))
                .hasMessageContaining("uq_conversations_direct_key");
    }

    @Test
    void ayniAndaTekAktifSahipAyrilanSahipSayilmaz() {
        UUID group = insertConversation("GROUP", "Takım", null);
        UUID firstOwner = UUID.randomUUID();
        insertMember(group, firstOwner, "OWNER");

        assertThatThrownBy(() -> insertMember(group, UUID.randomUUID(), "OWNER"))
                .hasMessageContaining("uq_conversation_members_active_owner");

        jdbc.update("update conversation_members set left_at = joined_at where user_id = ?", firstOwner);
        assertThatCode(() -> insertMember(group, UUID.randomUUID(), "OWNER")).doesNotThrowAnyException();
    }

    @Test
    void rolCikaranVeAyrilmaZamaniKurallari() {
        UUID group = insertConversation("GROUP", "Takım", null);

        assertThatThrownBy(() -> insertMember(group, UUID.randomUUID(), "MODERATOR"))
                .hasMessageContaining("ck_conversation_members_role");
        assertThatThrownBy(() -> jdbc.update("insert into conversation_members "
                        + "(conversation_id, user_id, role, joined_at, removed_by) values (?, ?, 'MEMBER', now(), ?)",
                group, UUID.randomUUID(), UUID.randomUUID()))
                .hasMessageContaining("ck_conversation_members_removed");
        assertThatThrownBy(() -> jdbc.update("insert into conversation_members "
                        + "(conversation_id, user_id, role, joined_at, left_at) values (?, ?, 'MEMBER', now(), ?)",
                group, UUID.randomUUID(), Timestamp.from(Instant.now().minusSeconds(60))))
                .hasMessageContaining("ck_conversation_members_left");
    }

    @Test
    void uyelikVarOlanSohbeteBagliSohbetSilinceUyelikDeSilinir() {
        assertThatThrownBy(() -> insertMember(UUID.randomUUID(), UUID.randomUUID(), "MEMBER"))
                .hasMessageContaining("fk_conversation_members_conversation");

        UUID group = insertConversation("GROUP", "Takım", null);
        insertMember(group, UUID.randomUUID(), "OWNER");
        jdbc.update("delete from conversations where id = ?", group);
        assertThat(activeMemberCount(group)).isZero();
    }

    private UUID insertConversation(String type, String title, String directKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into conversations (id, type, title, direct_key, created_by, created_at) "
                + "values (?, ?, ?, ?, ?, now())", id, type, title, directKey, UUID.randomUUID());
        return id;
    }

    private void insertMember(UUID conversationId, UUID userId, String role) {
        jdbc.update("insert into conversation_members (conversation_id, user_id, role, joined_at) "
                + "values (?, ?, ?, now())", conversationId, userId, role);
    }
}
