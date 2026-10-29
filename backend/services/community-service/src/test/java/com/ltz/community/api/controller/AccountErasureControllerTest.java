package com.ltz.community.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ltz.community.application.service.AccountErasureService;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

/** Denetleyicinin kendi kuralları (sub tam eşleşme, account_id = yol); imza/yetki zinciri InternalEndpointSecurityTest'tedir. */
@WebMvcTest(AccountErasureController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class AccountErasureControllerTest {

	private static final UUID ACCOUNT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/internal/v1/accounts/" + ACCOUNT_ID;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountErasureService erasureService;

	private static RequestPostProcessor eraser(String subject, String accountId) {
		return jwt().jwt(token -> {
			token.subject(subject);
			if (accountId != null) {
				token.claim("account_id", accountId);
			}
		}).authorities(new SimpleGrantedAuthority("SCOPE_account:erase"));
	}

	@Test
	void authsTokenBoundToThePathAccountErasesIt() throws Exception {
		mockMvc.perform(delete(URL).with(eraser("svc:auth-service", ACCOUNT_ID.toString())))
				.andExpect(status().isNoContent()).andExpect(content().string(""));

		verify(erasureService).erase(ACCOUNT_ID);
	}

	@Test
	void callerOrAccountMismatchesAreForbiddenWithAProblemDetail() throws Exception {
		mockMvc.perform(delete(URL).with(eraser("svc:admin-service", ACCOUNT_ID.toString())))
				.andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(delete(URL).with(eraser("svc:auth-service", UUID.randomUUID().toString())))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete(URL).with(eraser("svc:auth-service", null))).andExpect(status().isForbidden());
		mockMvc.perform(delete(URL).with(eraser("svc:auth-service-2", ACCOUNT_ID.toString()))).andExpect(status().isForbidden());

		verifyNoInteractions(erasureService);
	}

	@Test
	void aTokenWithoutTheEraseScopeNeverReachesTheController() throws Exception {
		mockMvc.perform(delete(URL).with(jwt().jwt(token -> token.subject("svc:auth-service")
				.claim("account_id", ACCOUNT_ID.toString())))).andExpect(status().isForbidden());

		verifyNoInteractions(erasureService);
	}
}
