package com.ltz.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailServiceApplicationTests extends IntegrationTestSupport {

	@Test
	void contextLoads() {
		assertThat(mockMvc).isNotNull();
	}

}
