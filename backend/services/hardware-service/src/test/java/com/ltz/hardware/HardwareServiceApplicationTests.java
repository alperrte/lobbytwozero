package com.ltz.hardware;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
class HardwareServiceApplicationTests extends PostgresIntegrationSupport {

	@Test
	void contextLoads() {
	}

}
