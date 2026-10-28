package com.ltz.community.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import com.ltz.community.EventTestConfig;
import com.ltz.community.application.port.DomainEventPublisher;
import com.ltz.community.application.port.EventDeliveryPort;
import com.ltz.community.application.port.EventDeliveryPort.Envelope;
import com.ltz.community.application.port.EventDeliveryPort.Target;
import com.ltz.community.infrastructure.messaging.outbox.OutboxDispatcher;
import com.ltz.community.infrastructure.messaging.outbox.OutboxStore;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;

/** Relay'in teslim, yeniden deneme (geri çekilme), kalıcı hata ve temizlik davranışı gerçek PostgreSQL üzerinde. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, OutboxStore.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxDispatcherIntegrationTest extends PostgreSqlIntegrationTest {

	private static final int MAX_ATTEMPTS = 3;

	@Autowired
	private DomainEventPublisher publisher;

	@Autowired
	private OutboxStore store;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final List<Delivery> deliveries = new ArrayList<>();
	private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
	private MutableClock clock;
	private OutboxDispatcher dispatcher;

	private record Delivery(Target target, Envelope envelope) {
	}

	/** Test sırasında ilerletilebilen saat. */
	private static final class MutableClock extends Clock {

		private Instant now = Instant.now();

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@BeforeEach
	void setUp() {
		clock = new MutableClock();
		clock.advance(Duration.ofMinutes(1));
		EventDeliveryPort delivery = (target, envelope) -> {
			RuntimeException error = failure.get();
			if (error != null) {
				throw error;
			}
			deliveries.add(new Delivery(target, envelope));
		};
		dispatcher = new OutboxDispatcher(store, delivery, clock, 10, MAX_ATTEMPTS, 10, 60);
		jdbcTemplate.update("delete from outbox_events");
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from outbox_events");
	}

	private UUID publish(String eventType, Map<String, Object> payload) {
		UUID aggregateId = UUID.randomUUID();
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				publisher.publish("POST", aggregateId, eventType, payload));
		return aggregateId;
	}

	private Map<String, Object> state() {
		return jdbcTemplate.queryForMap("select status, attempts from outbox_events");
	}

	@Test
	void eventsAreRoutedToTheirServicesAndMarkedSent() {
		UUID reportAggregate = publish("ContentReported", Map.of("reportId", "r1"));
		publish("CommentCreated", Map.of("commentId", "c1"));
		publish("PostLiked", Map.of("postId", "p1"));
		publish("PostReposted", Map.of("postId", "p2"));

		int delivered = dispatcher.dispatchBatch();

		assertThat(delivered).isEqualTo(4);
		assertThat(deliveries).extracting(Delivery::target)
				.containsExactlyInAnyOrder(Target.ADMIN, Target.NOTIFICATION, Target.NOTIFICATION, Target.NOTIFICATION);
		Envelope first = deliveries.stream().filter(d -> d.target() == Target.ADMIN).findFirst().orElseThrow().envelope();
		assertThat(first.eventType()).isEqualTo("ContentReported");
		assertThat(first.producer()).isEqualTo("community-service");
		assertThat(first.eventVersion()).isEqualTo(1);
		assertThat(first.aggregateId()).isEqualTo(reportAggregate);
		assertThat(first.payload()).containsEntry("reportId", "r1");
		assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_events where status = 'SENT' and sent_at is not null",
				Integer.class)).isEqualTo(4);
		// Teslim edilenler bir daha gönderilmez.
		assertThat(dispatcher.dispatchBatch()).isZero();
		assertThat(deliveries).hasSize(4);
	}

	@Test
	void failedDeliveryIsRetriedWithBackoffAndDeliveredOnRecovery() {
		publish("PostLiked", Map.of("postId", "p1"));
		failure.set(new RestClientException("alıcı kapalı"));

		assertThat(dispatcher.dispatchBatch()).isZero();
		assertThat(state()).containsEntry("status", "PENDING").containsEntry("attempts", 1);
		// Geri çekilme süresi dolmadan yeniden denenmez.
		clock.advance(Duration.ofSeconds(5));
		assertThat(dispatcher.dispatchBatch()).isZero();
		assertThat(state()).containsEntry("attempts", 1);

		failure.set(null);
		clock.advance(Duration.ofSeconds(120));
		assertThat(dispatcher.dispatchBatch()).isEqualTo(1);
		assertThat(state()).containsEntry("status", "SENT");
	}

	@Test
	void eventBecomesFailedAfterTheMaximumNumberOfAttempts() {
		publish("PostLiked", Map.of("postId", "p1"));
		failure.set(new RestClientException("alıcı kapalı"));

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			dispatcher.dispatchBatch();
			clock.advance(Duration.ofHours(2));
		}

		assertThat(state()).containsEntry("status", "FAILED").containsEntry("attempts", MAX_ATTEMPTS);
		failure.set(null);
		assertThat(dispatcher.dispatchBatch()).isZero();
		assertThat(deliveries).isEmpty();
	}

	@Test
	void unknownEventTypesFailImmediatelyWithoutDelivery() {
		publish("SomethingElse", Map.of("x", 1));

		assertThat(dispatcher.dispatchBatch()).isZero();

		assertThat(state()).containsEntry("status", "FAILED").containsEntry("attempts", 1);
		assertThat(deliveries).isEmpty();
	}

	@Test
	void corruptPayloadFailsPermanently() {
		publish("PostLiked", Map.of("postId", "p1"));
		jdbcTemplate.update("update outbox_events set payload = '\"yalnizca-metin\"'::jsonb");

		assertThat(dispatcher.dispatchBatch()).isZero();

		assertThat(state()).containsEntry("status", "FAILED");
		assertThat(deliveries).isEmpty();
	}

	@Test
	void oldDeliveredEventsArePurgedAtMostOncePerHour() {
		publish("PostLiked", Map.of("postId", "p1"));
		dispatcher.dispatchBatch();
		jdbcTemplate.update("update outbox_events set sent_at = ?", java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(30))));

		clock.advance(Duration.ofMinutes(10));
		dispatcher.dispatchBatch();
		assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_events", Integer.class)).isEqualTo(1);

		clock.advance(Duration.ofHours(2));
		dispatcher.dispatchBatch();
		assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_events", Integer.class)).isZero();
	}
}
