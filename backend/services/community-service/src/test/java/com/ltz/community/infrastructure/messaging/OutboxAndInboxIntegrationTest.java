package com.ltz.community.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.community.EventTestConfig;
import com.ltz.community.application.port.DomainEventPublisher;
import com.ltz.community.application.port.InboxPort;
import com.ltz.community.infrastructure.messaging.outbox.OutboxStore;
import com.ltz.community.infrastructure.messaging.outbox.OutboxStore.OutboxRecord;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;

/** Outbox yazımı (aynı transaction), kiralama/durum geçişleri ve inbox tekilleştirmesi gerçek PostgreSQL üzerinde. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, OutboxStore.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxAndInboxIntegrationTest extends PostgreSqlIntegrationTest {

	@Autowired
	private DomainEventPublisher publisher;

	@Autowired
	private InboxPort inbox;

	@Autowired
	private OutboxStore store;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void emptyOutbox() {
		jdbcTemplate.update("delete from outbox_events");
		jdbcTemplate.update("delete from inbox_events");
	}

	@AfterEach
	void cleanUp() {
		emptyOutbox();
	}

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	private int outboxCount() {
		return jdbcTemplate.queryForObject("select count(*) from outbox_events", Integer.class);
	}

	@Test
	void publishingNeedsAnActiveTransaction() {
		UUID aggregateId = UUID.randomUUID();
		Map<String, Object> payload = Map.of("k", "v");

		assertThatThrownBy(() -> publisher.publish("POST", aggregateId, "PostLiked", payload))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(outboxCount()).isZero();
	}

	@Test
	void eventIsWrittenWithTheBusinessTransactionAndRollsBackWithIt() {
		UUID aggregateId = UUID.randomUUID();

		Map<String, Object> payload = Map.of("postId", aggregateId, "n", 1);

		tx().executeWithoutResult(status -> {
			publisher.publish("POST", aggregateId, "PostLiked", payload);
			status.setRollbackOnly();
		});
		assertThat(outboxCount()).isZero();

		tx().executeWithoutResult(status -> publisher.publish("POST", aggregateId, "PostLiked", payload));
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"select aggregate_type, aggregate_id, event_type, event_version, status, attempts, cast(payload as text) as payload from outbox_events");
		assertThat(row).containsEntry("aggregate_type", "POST").containsEntry("aggregate_id", aggregateId)
				.containsEntry("event_type", "PostLiked").containsEntry("event_version", 1)
				.containsEntry("status", "PENDING").containsEntry("attempts", 0);
		assertThat((String) row.get("payload")).contains("\"n\"").contains(aggregateId.toString());
	}

	@Test
	void dueEventsAreLeasedOnceInOccurrenceOrder() {
		for (int i = 0; i < 3; i++) {
			tx().executeWithoutResult(status -> publisher.publish("POST", UUID.randomUUID(), "PostLiked", Map.of("i", 1)));
		}
		Instant now = Instant.now().plusSeconds(1);

		List<OutboxRecord> first = store.claimDue(now, 2, now.plus(Duration.ofMinutes(1)));
		List<OutboxRecord> second = store.claimDue(now, 10, now.plus(Duration.ofMinutes(1)));
		List<OutboxRecord> third = store.claimDue(now, 10, now.plus(Duration.ofMinutes(1)));

		assertThat(first).hasSize(2);
		assertThat(second).hasSize(1);
		assertThat(third).isEmpty();
		assertThat(first.getFirst().occurredAt()).isBeforeOrEqualTo(first.getLast().occurredAt());
		assertThat(first.getFirst().payloadJson()).contains("\"i\"");
		// Kira süresi dolunca olay yeniden görünür (teslim edilmediyse).
		assertThat(store.claimDue(now.plus(Duration.ofMinutes(2)), 10, now.plus(Duration.ofMinutes(3)))).hasSize(3);
	}

	@Test
	void sentFailedAndRetryStatesAreRecorded() {
		tx().executeWithoutResult(status -> publisher.publish("POST", UUID.randomUUID(), "PostLiked", Map.of("i", 1)));
		Instant now = Instant.now().plusSeconds(1);
		OutboxRecord claimed = store.claimDue(now, 1, now.plusSeconds(60)).getFirst();

		store.markFailedAttempt(claimed.id(), 1, now.plusSeconds(30));
		assertThat(jdbcTemplate.queryForMap("select status, attempts from outbox_events"))
				.containsEntry("status", "PENDING").containsEntry("attempts", 1);
		assertThat(store.claimDue(now.plusSeconds(10), 1, now.plusSeconds(70))).isEmpty();

		store.markFailedAttempt(claimed.id(), 2, null);
		assertThat(jdbcTemplate.queryForMap("select status, attempts from outbox_events"))
				.containsEntry("status", "FAILED").containsEntry("attempts", 2);

		store.markSent(claimed.id(), now);
		assertThat(jdbcTemplate.queryForObject("select status from outbox_events", String.class)).isEqualTo("SENT");
	}

	@Test
	void sentEventsAreRetainedThenPurged() {
		tx().executeWithoutResult(status -> publisher.publish("POST", UUID.randomUUID(), "PostLiked", Map.of("i", 1)));
		Instant now = Instant.now().plusSeconds(1);
		OutboxRecord claimed = store.claimDue(now, 1, now.plusSeconds(60)).getFirst();
		store.markSent(claimed.id(), now.minus(Duration.ofDays(20)));

		assertThat(store.purgeSentBefore(now.minus(Duration.ofDays(30)))).isZero();
		assertThat(store.purgeSentBefore(now.minus(Duration.ofDays(14)))).isEqualTo(1);
		assertThat(outboxCount()).isZero();
	}

	@Test
	void inboxAcceptsAnEventIdOnlyOnce() {
		UUID eventId = UUID.randomUUID();

		Boolean first = tx().execute(status -> inbox.markProcessed(eventId, "UserBlocked", "social-service"));
		Boolean again = tx().execute(status -> inbox.markProcessed(eventId, "UserBlocked", "social-service"));

		assertThat(first).isTrue();
		assertThat(again).isFalse();
		assertThat(jdbcTemplate.queryForObject("select count(*) from inbox_events", Integer.class)).isEqualTo(1);
	}

	@Test
	void inboxRecordRollsBackWithItsTransactionAndNeedsOne() {
		UUID eventId = UUID.randomUUID();

		tx().executeWithoutResult(status -> {
			inbox.markProcessed(eventId, "UserBlocked", "social-service");
			status.setRollbackOnly();
		});
		assertThat(jdbcTemplate.queryForObject("select count(*) from inbox_events", Integer.class)).isZero();
		assertThatThrownBy(() -> inbox.markProcessed(eventId, "UserBlocked", "social-service"))
				.isInstanceOf(IllegalTransactionStateException.class);
	}
}
