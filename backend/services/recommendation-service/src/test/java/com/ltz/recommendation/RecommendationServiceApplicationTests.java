package com.ltz.recommendation;

import com.ltz.recommendation.infrastructure.persistence.DisposableDatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = DisposableDatabaseInitializer.class)
class RecommendationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
