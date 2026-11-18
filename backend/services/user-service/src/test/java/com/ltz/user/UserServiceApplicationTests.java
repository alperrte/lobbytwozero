package com.ltz.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserServiceApplicationTests extends IntegrationTestSupport {

	@Test
	void contextLoads() {
		// Bağlam ayağa kalktıysa ve Flyway şeması uygulandıysa veritabanı sorgusu yanıt verir.
		assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
	}

}
