package com.ltz.content;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.ltz.content.infrastructure.persistence.PostgreSqlIntegrationTest;

/** Tüm bağlam (Flyway V101-V102, Hibernate validate, güvenlik zinciri) gerçek PostgreSQL üzerinde açılır. */
@SpringBootTest
class ContentServiceApplicationTests extends PostgreSqlIntegrationTest {

	@Test
	void contextLoads() {
	}

}
