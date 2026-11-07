package com.ltz.community.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.GameContentType;
import com.ltz.community.domain.exception.TooManyGameContentException;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/** Gerçek PostgreSQL üzerinde eşzamanlı oluşturma isteği son kota yerini iki kez kullanamaz. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GameContentQuotaAdapter.class)
class GameContentQuotaIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

	@Autowired
	private GameContentQuotaAdapter quota;

	@Autowired
	private PostRepository posts;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void twoConcurrentRequestsCanFillOnlyOneRemainingSlot() throws Exception {
		UUID authorId = UUID.randomUUID();
		UUID gameId = UUID.randomUUID();
		try {
			for (int index = 0; index < 9; index++) {
				posts.saveAndFlush(Post.publishGameContent(authorId, gameId, GameContentType.GUIDES,
						"Rehber " + index, "gövde", NOW.minusSeconds(1)));
			}
			CountDownLatch start = new CountDownLatch(1);
			Callable<Boolean> attempt = () -> {
				start.await();
				try {
					quota.saveWithinQuota(Post.publishGameContent(authorId, gameId, GameContentType.GUIDES,
							"Son rehber", "gövde", NOW), NOW.minusSeconds(3600), 10);
					return true;
				} catch (TooManyGameContentException exception) {
					return false;
				}
			};
			try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
				Future<Boolean> first = executor.submit(attempt);
				Future<Boolean> second = executor.submit(attempt);
				start.countDown();
				assertThat(first.get(30, TimeUnit.SECONDS) ^ second.get(30, TimeUnit.SECONDS)).isTrue();
			}
			assertThat(posts.countByAuthorIdAndContentTypeIsNotNullAndCreatedAtAfter(authorId,
					NOW.minusSeconds(3600))).isEqualTo(10);
		} finally {
			jdbc.update("delete from posts where author_id = ?", authorId);
		}
	}
}
