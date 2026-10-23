package com.ltz.social.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.domain.entity.Friendship;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;

/** V121 şeması gerçek PostgreSQL'de: tekillik, CHECK ve uuid sıralama kuralları DB seviyesinde uygulanır. */
class FlywayMigrationTest extends IntegrationTestSupport {

    private static final UUID HIGH_BIT_SET = new UUID(0x8000_0000_0000_0000L, 0L);
    private static final UUID HIGH_BIT_CLEAR = new UUID(0x0000_0000_0000_0001L, 0L);

    @Autowired
    private FriendshipRepository friendships;

    @Test
    void migrationUygulanmisVeTablolarVar() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(versions).contains("121", "122", "123", "124");
        assertThat(tables).contains("friend_requests", "friendships", "outbox_events", "friend_invite_codes",
                "user_blocks");
    }

    @Test
    void kendineIstekDbSeviyesindeReddedilir() {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> insertRequest(user, user, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_friend_requests_distinct_users");
    }

    @Test
    void gecersizDurumVeTutarsizYanitZamaniReddedilir() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        assertThatThrownBy(() -> insertRequest(sender, receiver, "BLOCKED"))
                .hasMessageContaining("ck_friend_requests_status");
        // PENDING iken responded_at dolu olamaz; yanıtlanmışta boş olamaz.
        assertThatThrownBy(() -> insertRequest(sender, receiver, "PENDING", Instant.now()))
                .hasMessageContaining("ck_friend_requests_responded");
        assertThatThrownBy(() -> insertRequest(sender, receiver, "ACCEPTED", null))
                .hasMessageContaining("ck_friend_requests_responded");
    }

    @Test
    void ayniCiftIcinYondenBagimsizTekBekleyenIstek() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insertRequest(first, second, "PENDING");

        assertThatThrownBy(() -> insertRequest(first, second, "PENDING"))
                .hasMessageContaining("uq_friend_requests_pending_pair");
        assertThatThrownBy(() -> insertRequest(second, first, "PENDING"))
                .hasMessageContaining("uq_friend_requests_pending_pair");
    }

    @Test
    void bekleyenOlmayanIstekler_ayniCiftIcinBirikebilir() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insertRequest(first, second, "REJECTED", Instant.now());
        insertRequest(first, second, "CANCELLED", Instant.now());
        insertRequest(second, first, "REJECTED", Instant.now());

        assertThatCode(() -> insertRequest(first, second, "PENDING")).doesNotThrowAnyException();
    }

    @Test
    void farkliCiftlerBagimsizdir() {
        UUID shared = UUID.randomUUID();

        assertThatCode(() -> {
            insertRequest(shared, UUID.randomUUID(), "PENDING");
            insertRequest(shared, UUID.randomUUID(), "PENDING");
            insertRequest(UUID.randomUUID(), shared, "PENDING");
        }).doesNotThrowAnyException();
    }

    @Test
    void arkadaslikSirasiVeTekilligiDbdeZorunlu() {
        UUID low = new UUID(1L, 0L);
        UUID high = new UUID(2L, 0L);

        assertThatThrownBy(() -> insertFriendship(high, low)).hasMessageContaining("ck_friendships_order");
        assertThatThrownBy(() -> insertFriendship(low, low)).hasMessageContaining("ck_friendships_order");
        insertFriendship(low, high);
        assertThatThrownBy(() -> insertFriendship(low, high)).hasMessageContaining("pk_friendships");
    }

    @Test
    void javaSiralamasiPostgresUuidSirasiylaUyumlu_isaretBitiDahil() {
        // İşaretli UUID.compareTo ile sıralansaydı en az biri CHECK'i ihlal ederdi.
        List<UUID[]> pairs = List.of(
                new UUID[] {HIGH_BIT_SET, HIGH_BIT_CLEAR},
                new UUID[] {HIGH_BIT_CLEAR, HIGH_BIT_SET},
                new UUID[] {new UUID(7L, 0x8000_0000_0000_0000L), new UUID(7L, 1L)},
                new UUID[] {new UUID(-1L, -1L), new UUID(0L, 0L)});

        for (UUID[] pair : pairs) {
            assertThatCode(() -> friendships.saveAndFlush(Friendship.between(pair[0], pair[1], Instant.now())))
                    .doesNotThrowAnyException();
        }
        for (int i = 0; i < 100; i++) {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            assertThatCode(() -> friendships.saveAndFlush(Friendship.between(a, b, Instant.now())))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void outboxKisitlari() {
        assertThatThrownBy(() -> insertOutbox("DONE", 0)).hasMessageContaining("ck_outbox_events_status");
        assertThatThrownBy(() -> insertOutbox("PENDING", -1)).hasMessageContaining("ck_outbox_events_attempts");
        assertThatCode(() -> insertOutbox("PENDING", 0)).doesNotThrowAnyException();
        assertThatCode(() -> insertOutbox("SENT", 2)).doesNotThrowAnyException();
        assertThatCode(() -> insertOutbox("FAILED", 10)).doesNotThrowAnyException();
    }

    @Test
    void davetKoduKisitlari() {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> insertInvite(UUID.randomUUID(), "SHORT", 1, 0, "1 day"))
                .hasMessageContaining("ck_friend_invite_codes_code");
        // I, L, O, U Crockford alfabesinde yoktur.
        assertThatThrownBy(() -> insertInvite(UUID.randomUUID(), "ILOU012345", 1, 0, "1 day"))
                .hasMessageContaining("ck_friend_invite_codes_code");
        assertThatThrownBy(() -> insertInvite(UUID.randomUUID(), "abcdefghjk", 1, 0, "1 day"))
                .hasMessageContaining("ck_friend_invite_codes_code");
        assertThatThrownBy(() -> insertInvite(UUID.randomUUID(), "0123456789", 1, 0, "-1 day"))
                .hasMessageContaining("ck_friend_invite_codes_expiry");
        assertThatThrownBy(() -> insertInvite(UUID.randomUUID(), "0123456789", 0, 0, "1 day"))
                .hasMessageContaining("ck_friend_invite_codes_uses");
        assertThatThrownBy(() -> insertInvite(UUID.randomUUID(), "0123456789", 2, 3, "1 day"))
                .hasMessageContaining("ck_friend_invite_codes_uses");

        insertInvite(user, "ABCDEFGHJK", 5, 0, "1 day");
        assertThatThrownBy(() -> insertInvite(UUID.randomUUID(), "ABCDEFGHJK", 5, 0, "1 day"))
                .hasMessageContaining("uq_friend_invite_codes_code");
        // Kullanıcı başına tek etkin kod; iptal edilince yenisi alınabilir.
        assertThatThrownBy(() -> insertInvite(user, "MNPQRSTVWX", 5, 0, "1 day"))
                .hasMessageContaining("uq_friend_invite_codes_active_user");
        jdbc.update("update friend_invite_codes set revoked_at = now() where user_id = ?", user);
        assertThatCode(() -> insertInvite(user, "MNPQRSTVWX", 5, 0, "1 day")).doesNotThrowAnyException();
    }

    @Test
    void engelKisitlari_yonluVeTekil() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThatThrownBy(() -> insertBlock(first, first)).hasMessageContaining("ck_user_blocks_distinct_users");
        insertBlock(first, second);
        assertThatThrownBy(() -> insertBlock(first, second)).hasMessageContaining("pk_user_blocks");
        // Engel yönlüdür: ters yöndeki engel ayrı kayıttır.
        assertThatCode(() -> insertBlock(second, first)).doesNotThrowAnyException();
    }

    private void insertBlock(UUID blocker, UUID blocked) {
        jdbc.update("insert into user_blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", blocker,
                blocked);
    }

    private void insertOutbox(String status, int attempts) {
        jdbc.update("insert into outbox_events (id, aggregate_type, aggregate_id, event_type, payload, occurred_at, "
                + "status, attempts) values (?, 'FriendRequest', ?, 'FriendRequestSent', '{}'::jsonb, now(), ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), status, attempts);
    }

    private void insertInvite(UUID user, String code, int maxUses, int usedCount, String expiresIn) {
        jdbc.update("insert into friend_invite_codes (id, user_id, code, created_at, expires_at, max_uses, used_count) "
                + "values (?, ?, ?, now(), now() + cast(? as interval), ?, ?)", UUID.randomUUID(), user, code,
                expiresIn, maxUses, usedCount);
    }

    private void insertRequest(UUID sender, UUID receiver, String status) {
        insertRequest(sender, receiver, status, "PENDING".equals(status) ? null : Instant.now());
    }

    private void insertRequest(UUID sender, UUID receiver, String status, Instant respondedAt) {
        jdbc.update("insert into friend_requests (id, sender_id, receiver_id, status, created_at, responded_at) "
                        + "values (?, ?, ?, ?, now(), ?)", UUID.randomUUID(), sender, receiver, status,
                respondedAt == null ? null : Timestamp.from(respondedAt));
    }

    private void insertFriendship(UUID low, UUID high) {
        jdbc.update("insert into friendships (user_low_id, user_high_id, created_at) values (?, ?, now())", low, high);
    }
}
