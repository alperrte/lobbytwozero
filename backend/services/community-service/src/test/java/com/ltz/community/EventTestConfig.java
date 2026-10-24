package com.ltz.community;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.ltz.community.application.service.BlockPolicy;
import com.ltz.community.application.service.InteractionEvents;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.messaging.inbox.InboxEventStore;
import com.ltz.community.infrastructure.messaging.outbox.OutboxEventPublisher;

/**
 * {@code @DataJpaTest} dilimlerine outbox/inbox ve engelleme altyapısını ekler (gerçek PostgreSQL üzerinde çalışır).
 * Dilim JdbcClient bean'i sağlamadığından burada JdbcTemplate üzerinden kurulur.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ OutboxEventPublisher.class, InboxEventStore.class, InteractionEvents.class, BlockPolicy.class,
		ClockConfig.class })
public class EventTestConfig {

	@Bean
	JdbcClient jdbcClient(JdbcTemplate jdbcTemplate) {
		return JdbcClient.create(jdbcTemplate);
	}
}
