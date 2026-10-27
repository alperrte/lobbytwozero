package com.ltz.community.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import com.ltz.community.infrastructure.messaging.outbox.OutboxDispatcher;
import com.ltz.community.infrastructure.messaging.outbox.OutboxRelay;

class OutboxRelayTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(OutboxDispatcher.class, () -> mock(OutboxDispatcher.class))
			.withUserConfiguration(OutboxRelay.class);

	@Test
	void relayIsOffByDefaultSoEventsStayPendingInTheOutbox() {
		runner.run(context -> assertThat(context).doesNotHaveBean(OutboxRelay.class));
		runner.withPropertyValues("ltz.outbox.relay.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(OutboxRelay.class));
	}

	@Test
	void relayIsCreatedOnlyWhenExplicitlyEnabled() {
		runner.withPropertyValues("ltz.outbox.relay.enabled=true")
				.run(context -> assertThat(context).hasSingleBean(OutboxRelay.class));
	}

	@Test
	void aFailingBatchDoesNotStopTheScheduler() throws NoSuchMethodException {
		OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);
		when(dispatcher.dispatchBatch()).thenThrow(new IllegalStateException("veritabanı yok")).thenReturn(1);
		OutboxRelay relay = new OutboxRelay(dispatcher);

		relay.relay();
		relay.relay();

		verify(dispatcher, times(2)).dispatchBatch();
		assertThat(OutboxRelay.class.getMethod("relay").getAnnotation(Scheduled.class).fixedDelayString())
				.contains("ltz.outbox.relay.interval-ms");
	}
}
