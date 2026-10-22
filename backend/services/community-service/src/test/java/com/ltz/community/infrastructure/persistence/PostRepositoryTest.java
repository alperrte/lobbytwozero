package com.ltz.community.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

import jakarta.persistence.EntityManager;

/** Flyway V91 migration'ını gerçek PostgreSQL üzerinde çalıştırır (ddl-auto=validate); her test geri alınır. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest extends PostgreSqlIntegrationTest {

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void savesPostWithGeneratedIdVersionAndUtcTimestamps() {
		UUID authorId = UUID.randomUUID();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

		Post saved = postRepository.saveAndFlush(Post.publish(authorId, "Merhaba", now));
		entityManager.clear();

		Post loaded = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getId()).isNotNull();
		assertThat(loaded.getId().version()).isEqualTo(7);
		assertThat(loaded.getAuthorId()).isEqualTo(authorId);
		assertThat(loaded.getBody()).isEqualTo("Merhaba");
		assertThat(loaded.getStatus()).isEqualTo(PostStatus.VISIBLE);
		assertThat(loaded.getCreatedAt()).isEqualTo(now);
		assertThat(loaded.getUpdatedAt()).isEqualTo(now);
		assertThat(loaded.getVersion()).isZero();
	}

	@Test
	void gameReferenceIsOptionalAndPersistedWithoutForeignKey() {
		UUID gameId = UUID.randomUUID();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

		Post withGame = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "Oyunlu", gameId, now));
		Post withoutGame = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "Oyunsuz", now));
		entityManager.clear();

		assertThat(postRepository.findById(withGame.getId()).orElseThrow().getGameId()).isEqualTo(gameId);
		assertThat(postRepository.findById(withoutGame.getId()).orElseThrow().getGameId()).isNull();
	}

	@Test
	void linkAndUnlinkGamePersist() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		UUID gameId = UUID.randomUUID();
		Post saved = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "Gönderi", now));
		entityManager.clear();

		Post loaded = postRepository.findById(saved.getId()).orElseThrow();
		loaded.linkGame(gameId, now.plusSeconds(5));
		postRepository.saveAndFlush(loaded);
		entityManager.clear();
		assertThat(postRepository.findById(saved.getId()).orElseThrow().getGameId()).isEqualTo(gameId);

		Post linked = postRepository.findById(saved.getId()).orElseThrow();
		linked.unlinkGame(now.plusSeconds(10));
		postRepository.saveAndFlush(linked);
		entityManager.clear();
		assertThat(postRepository.findById(saved.getId()).orElseThrow().getGameId()).isNull();
	}

	@Test
	void databaseRejectsBlankBody() {
		Post blank = Post.publish(UUID.randomUUID(), "   ", Instant.now());
		assertThatThrownBy(() -> postRepository.saveAndFlush(blank))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsBodyLongerThanLimit() {
		String body = "a".repeat(Post.BODY_MAX_LENGTH + 1);

		Post tooLong = Post.publish(UUID.randomUUID(), body, Instant.now());

		assertThatThrownBy(() -> postRepository.saveAndFlush(tooLong))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void updateIncrementsVersionAndPersistsNewBody() {
		Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		Post saved = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "Eski", created));
		entityManager.clear();

		Post loaded = postRepository.findById(saved.getId()).orElseThrow();
		loaded.editBody("Yeni", created.plusSeconds(60));
		postRepository.saveAndFlush(loaded);
		entityManager.clear();

		Post reloaded = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getBody()).isEqualTo("Yeni");
		assertThat(reloaded.getUpdatedAt()).isEqualTo(created.plusSeconds(60));
		assertThat(reloaded.getCreatedAt()).isEqualTo(created);
		assertThat(reloaded.getVersion()).isEqualTo(1L);
	}

	@Test
	void staleUpdateIsRejectedByOptimisticLock() {
		Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		Post saved = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "Eski", created));
		entityManager.clear();
		Post stale = postRepository.findById(saved.getId()).orElseThrow();
		entityManager.detach(stale);
		Post winner = postRepository.findById(saved.getId()).orElseThrow();
		winner.editBody("Kazanan", created.plusSeconds(1));
		postRepository.saveAndFlush(winner);

		stale.editBody("Kaybeden", created.plusSeconds(2));

		assertThatThrownBy(() -> postRepository.saveAndFlush(stale))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void softDeleteKeepsRowAndRecordsDeletionTime() {
		Instant created = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		Post saved = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "Silinecek", created));
		entityManager.clear();

		Post loaded = postRepository.findById(saved.getId()).orElseThrow();
		loaded.markDeleted(created.plusSeconds(30));
		postRepository.saveAndFlush(loaded);
		entityManager.clear();

		Post reloaded = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(PostStatus.DELETED);
		assertThat(reloaded.getDeletedAt()).isEqualTo(created.plusSeconds(30));
		assertThat(reloaded.getBody()).isEqualTo("Silinecek");
		assertThat(reloaded.getVersion()).isEqualTo(1L);
	}

	@Test
	void categoryIsPersistedDefaultsToGeneralAndIsRestrictedByDatabase() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		Post guide = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "rehber", null, PostCategory.GUIDE, now));
		Post plain = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "düz", now));
		entityManager.clear();

		assertThat(postRepository.findById(guide.getId()).orElseThrow().getCategory()).isEqualTo(PostCategory.GUIDE);
		assertThat(postRepository.findById(plain.getId()).orElseThrow().getCategory()).isEqualTo(PostCategory.GENERAL);

		UUID id = plain.getId();
		assertThatThrownBy(() -> jdbcTemplate.update("update posts set category = 'BOGUS' where id = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void interactionCountersDefaultToZeroAndRejectNegativeValues() {
		Post saved = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "sayaç", Instant.now()));
		entityManager.clear();
		Post loaded = postRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getLikeCount()).isZero();
		assertThat(loaded.getCommentCount()).isZero();

		UUID id = saved.getId();
		assertThatThrownBy(() -> jdbcTemplate.update("update posts set like_count = -1 where id = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"insert into posts (id, author_id, body, status, created_at, updated_at) values (?, ?, 'x', 'DELETED', now(), now())",
			"insert into posts (id, author_id, body, status, created_at, updated_at, deleted_at) values (?, ?, 'x', 'VISIBLE', now(), now(), now())",
			"insert into posts (id, author_id, body, status, created_at, updated_at) values (?, ?, 'x', 'BOGUS', now(), now())" })
	void databaseRejectsInconsistentStatusRows(String insertSql) {
		UUID id = UUID.randomUUID();
		UUID authorId = UUID.randomUUID();
		assertThatThrownBy(() -> jdbcTemplate.update(insertSql, id, authorId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
