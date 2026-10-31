package com.ltz.performance;

import com.ltz.performance.infrastructure.persistence.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
class PerformanceServiceApplicationTests extends PostgresIntegrationSupport {

	@Test
	void contextLoads() {
	}

}
