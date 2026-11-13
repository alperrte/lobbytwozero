package com.ltz.lfp.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.ltz.lfp.IntegrationTestSupport;

/**
 * Gerçek PostgreSQL üzerinde V141: tablolar oluşur ve kurallar uygulama atlansa bile veritabanında da korunur
 * (savunma derinliği). {@code ddl-auto=validate} bağlam açılışında entity ↔ şema uyumunu ayrıca doğrular.
 */
class FlywayMigrationTest extends IntegrationTestSupport {

    private static final String INSERT = "insert into lfp_listings (id, owner_id, game_id, game_title_snapshot, "
            + "platform, skill_level, players_needed, play_time_start, play_time_end, mic_required, description, "
            + "status, expires_at, created_at, updated_at, closed_at) values (?, ?, ?, ?, ?, ?, ?, "
            + "now() + cast(? as interval), now() + cast(? as interval), false, ?, ?, "
            + "now() + cast(? as interval), now(), now(), %s)";

    @Test
    void v141Uygulanir() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where version = '141' "
                + "and success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void gecerliSatirKabulEdilirRolleriIlanlaBirlikteSilinir() {
        UUID id = insert(row());
        jdbc.update("insert into lfp_listing_roles (listing_id, position, role) values (?, 0, 'Tank'), "
                + "(?, 1, 'Healer')", id, id);

        jdbc.update("delete from lfp_listings where id = ?", id);

        assertThat(jdbc.queryForObject("select count(*) from lfp_listing_roles where listing_id = ?", Integer.class,
                id)).isZero();
    }

    @Test
    void durumPlatformVeOyuncuSayisiKisitlari() {
        assertRejected(row().platform("SEGA"));
        assertRejected(row().status("FULL"));
        assertRejected(row().players(0));
        assertRejected(row().players(51));
        assertRejected(row().description("   "));
        assertRejected(row().skill(" "));
        assertRejected(row().title(" "));
    }

    @Test
    void sureEnAz1SaatEnCok7Gun() {
        assertRejected(row().expires("59 minutes"));
        assertRejected(row().expires("7 days 1 minute"));
        insert(row().expires("1 hour"));
        insert(row().expires("7 days"));
    }

    @Test
    void oyunZamaniBitisiBaslangicIsterVeSonraOlmali() {
        assertRejected(row().playTime(null, "2 hours"));
        assertRejected(row().playTime("2 hours", "1 hour"));
        assertRejected(row().playTime("2 hours", "2 hours"));
        insert(row().playTime("2 hours", null));
        insert(row().playTime("2 hours", "3 hours"));
    }

    @Test
    void kapanisZamaniYalnizKapaliIlanda() {
        assertRejected(row().status("CLOSED"));
        assertRejected(row().closedNow());
        insert(row().status("CLOSED").closedNow());
        insert(row().status("EXPIRED"));
    }

    @Test
    void rolPozisyonuVeUzunlukKisitlariIlanOlmadanRolYok() {
        UUID id = insert(row());

        assertThatThrownBy(() -> jdbc.update("insert into lfp_listing_roles values (?, 5, 'x')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lfp_listing_roles values (?, 0, '  ')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lfp_listing_roles values (?, 0, 'x')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insert(Row row) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT.formatted(row.closed ? "now()" : "null"), id, UUID.randomUUID(), UUID.randomUUID(),
                row.title, row.platform, row.skill, row.players, row.start, row.end, row.description, row.status,
                row.expires);
        return id;
    }

    private void assertRejected(Row row) {
        assertThatThrownBy(() -> insert(row)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Row row() {
        return new Row();
    }

    /** Geçerli bir satırdan başlayıp tek alanı bozmak için küçük kurucu. */
    private static final class Row {
        private String title = "Game";
        private String platform = "PC";
        private String skill;
        private int players = 2;
        private String start;
        private String end;
        private String description = "Açıklama";
        private String status = "OPEN";
        private String expires = "24 hours";
        private boolean closed;

        Row title(String value) {
            title = value;
            return this;
        }

        Row platform(String value) {
            platform = value;
            return this;
        }

        Row skill(String value) {
            skill = value;
            return this;
        }

        Row players(int value) {
            players = value;
            return this;
        }

        Row playTime(String startOffset, String endOffset) {
            start = startOffset;
            end = endOffset;
            return this;
        }

        Row description(String value) {
            description = value;
            return this;
        }

        Row status(String value) {
            status = value;
            return this;
        }

        Row expires(String value) {
            expires = value;
            return this;
        }

        Row closedNow() {
            closed = true;
            return this;
        }
    }
}
