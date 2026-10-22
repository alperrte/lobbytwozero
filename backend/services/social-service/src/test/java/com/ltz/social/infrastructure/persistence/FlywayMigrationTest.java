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

        assertThat(versions).contains("121");
        assertThat(tables).contains("friend_requests", "friendships");
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
