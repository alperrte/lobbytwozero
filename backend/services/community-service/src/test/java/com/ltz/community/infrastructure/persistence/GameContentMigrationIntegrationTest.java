package com.ltz.community.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * V181 (GAME-11) şema kuralları ve eski verinin korunması gerçek PostgreSQL üzerinde: eski gönderiler V100 şemasında
 * oluşturulup V181'e yükseltilir; türsüz kalır ve eski kategori bilgisi değişmez.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameContentMigrationIntegrationTest extends PostgreSqlIntegrationTest {

	private static final String UPGRADE_SCHEMA = "upgrade_v181";
	private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-05T10:00:00Z"));

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	private void insertPost(UUID id, String contentType, String title, UUID gameId, UUID repostOfId) {
		jdbc.update("""
				insert into posts (id, author_id, game_id, body, status, created_at, updated_at, content_type, title, repost_of_id)
				values (?, ?, ?, 'gövde', 'VISIBLE', ?, ?, ?, ?, ?)
				""", id, UUID.randomUUID(), gameId, NOW, NOW, contentType, title, repostOfId);
	}

	@Test
	void validTypedContentAndLegacyRowsAreAccepted() {
		UUID game = UUID.randomUUID();
		insertPost(UUID.randomUUID(), "GUIDES", "Rehber", game, null);
		insertPost(UUID.randomUUID(), "EASTER_EGGS", "Yumurta", game, null);
		insertPost(UUID.randomUUID(), "EXPERIENCES", "Deneyim", game, null);
		UUID legacy = UUID.randomUUID();
		insertPost(legacy, null, null, null, null);

		assertThat(jdbc.queryForObject("select count(*) from posts where content_type is not null", Long.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select category from posts where id = ?", String.class, legacy)).isEqualTo("GENERAL");
	}

	/** Her ihlal ayrı (otomatik onaylı) bir ifadedir; hata alan transaction sonraki ifadeleri bozmasın diye test transaction'ı dışındadır. */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void databaseRejectsInconsistentTypeTitleAndGameCombinations() {
		UUID game = UUID.randomUUID();
		UUID original = UUID.randomUUID();
		insertPost(original, null, null, game, null);
		try {
			assertRejected(game, original);
		} finally {
			jdbc.update("delete from posts where id = ?", original);
		}
	}

	private void assertRejected(UUID game, UUID original) {
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), "GUIDES", null, game, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), "GUIDES", "   ", game, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), "GUIDES", "t".repeat(121), game, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), null, "başlıksız tür", game, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), "GUIDES", "Rehber", null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), "NEWS", "Haber", game, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), "guides", "Rehber", game, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPost(UUID.randomUUID(), "GUIDES", "Rehber", game, original))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/** Kendi bağlantılarıyla çalışan Flyway kullanıldığından test transaction'ının dışında yürütülür. */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void upgradingFromV100KeepsEveryExistingPostUntypedAndUntouched() {
		jdbc.execute("drop schema if exists " + UPGRADE_SCHEMA + " cascade");
		try {
			Flyway.configure().dataSource(dataSource).schemas(UPGRADE_SCHEMA, "public").defaultSchema(UPGRADE_SCHEMA)
					.locations("classpath:db/migration").target("100").load().migrate();
			UUID legacyGuide = UUID.randomUUID();
			UUID legacyPlain = UUID.randomUUID();
			UUID game = UUID.randomUUID();
			jdbc.update("insert into " + UPGRADE_SCHEMA + ".posts (id, author_id, game_id, body, status, created_at, updated_at, category) "
					+ "values (?, ?, ?, 'eski rehber', 'VISIBLE', ?, ?, 'GUIDE')", legacyGuide, UUID.randomUUID(), game, NOW, NOW);
			jdbc.update("insert into " + UPGRADE_SCHEMA + ".posts (id, author_id, body, status, created_at, updated_at) "
					+ "values (?, ?, 'eski düz', 'HIDDEN', ?, ?)", legacyPlain, UUID.randomUUID(), NOW, NOW);

			Flyway.configure().dataSource(dataSource).schemas(UPGRADE_SCHEMA, "public").defaultSchema(UPGRADE_SCHEMA)
					.locations("classpath:db/migration").load().migrate();

			assertThat(jdbc.queryForObject("select count(*) from " + UPGRADE_SCHEMA + ".posts where content_type is null and title is null",
					Long.class)).isEqualTo(2);
			assertThat(jdbc.queryForObject("select category from " + UPGRADE_SCHEMA + ".posts where id = ?", String.class, legacyGuide))
					.isEqualTo("GUIDE");
			assertThat(jdbc.queryForObject("select status from " + UPGRADE_SCHEMA + ".posts where id = ?", String.class, legacyPlain))
					.isEqualTo("HIDDEN");
			assertThat(jdbc.queryForObject("select body from " + UPGRADE_SCHEMA + ".posts where id = ?", String.class, legacyGuide))
					.isEqualTo("eski rehber");
		} finally {
			jdbc.execute("drop schema if exists " + UPGRADE_SCHEMA + " cascade");
		}
	}
}
