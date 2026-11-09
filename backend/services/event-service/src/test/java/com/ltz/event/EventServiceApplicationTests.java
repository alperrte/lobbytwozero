package com.ltz.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.ltz.event.infrastructure.persistence.PostgreSqlIntegrationTest;

/** Tüm migration'lar geçici bir veritabanında uygulanır ve ddl-auto=validate ile doğrulanır; event_db'ye yazılmaz. */
@SpringBootTest
class EventServiceApplicationTests extends PostgreSqlIntegrationTest {

	@Test
	void contextLoads() {
	}

}
