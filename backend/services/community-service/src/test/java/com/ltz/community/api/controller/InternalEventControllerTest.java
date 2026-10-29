package com.ltz.community.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import com.ltz.community.api.dto.request.InboundEventRequest;
import com.ltz.community.api.dto.response.InboundEventResult;
import com.ltz.community.api.dto.response.InboundEventResult.Outcome;
import com.ltz.community.application.service.InboundEventService;
import com.ltz.community.domain.exception.InvalidInboundEventException;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(InternalEventController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class InternalEventControllerTest {

	private static final String URL = "/internal/events";
	private static final UUID EVENT_ID = UUID.fromString("0198a3c2-1111-7d44-9a55-3f1c2b4d5e66");
	private static final String VALID = """
			{"eventId":"0198a3c2-1111-7d44-9a55-3f1c2b4d5e66","eventType":"UserBlocked","eventVersion":1,
			 "occurredAt":"2026-10-21T10:00:00Z","producer":"social-service",
			 "aggregateId":"0198a3c2-2222-7d44-9a55-3f1c2b4d5e66",
			 "payload":{"blockerId":"0198a3c2-3333-7d44-9a55-3f1c2b4d5e66","blockedId":"0198a3c2-4444-7d44-9a55-3f1c2b4d5e66"}}
			""";

	private final RequestPostProcessor service = jwt().jwt(token -> token.subject("svc:social-service"))
			.authorities(new SimpleGrantedAuthority("SCOPE_events:ingest"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InboundEventService inboundEventService;

	@Test
	void trustedServiceDeliversAnEvent() throws Exception {
		when(inboundEventService.handle(any(InboundEventRequest.class)))
				.thenReturn(new InboundEventResult(EVENT_ID, Outcome.PROCESSED));

		mockMvc.perform(post(URL).with(service).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
				.andExpect(jsonPath("$.outcome").value("PROCESSED"));
		verify(inboundEventService).handle(any(InboundEventRequest.class));
	}

	@Test
	void callersWithoutAServiceIdentityAreRejectedBeforeAnyProcessing() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		// Kimlik doğrulanmış ama servis yetkisi olmayan (örn. son kullanıcı) çağıran.
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_other")))
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isForbidden());
		// Tanımsız yöntem/yol iç uçlarda reddedilir (denyAll).
		mockMvc.perform(get(URL).with(service)).andExpect(status().isForbidden());
		verifyNoInteractions(inboundEventService);
	}

	@Test
	void aServiceCannotPublishEventsOnBehalfOfAnotherProducer() throws Exception {
		RequestPostProcessor adminToken = jwt().jwt(token -> token.subject("svc:admin-service"))
				.authorities(new SimpleGrantedAuthority("SCOPE_events:ingest"));

		// Zarf social-service diyor ama token admin-service'e ait.
		mockMvc.perform(post(URL).with(adminToken).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(inboundEventService);
	}

	@Test
	void malformedEnvelopesAreRejected() throws Exception {
		String[] invalid = { "{}", VALID.replace("\"eventVersion\":1", "\"eventVersion\":0"),
				VALID.replace("\"eventId\":\"0198a3c2-1111-7d44-9a55-3f1c2b4d5e66\"", "\"eventId\":\"degil\""),
				VALID.replace("UserBlocked", "Kötü Ad!"), VALID.replace("social-service", "Social Service"),
				VALID.replace("\"payload\":{", "\"extra\":1,\"payload\":{"),
				VALID.replace("2026-10-21T10:00:00Z", "dun") };
		for (String json : invalid) {
			mockMvc.perform(post(URL).with(service).contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(inboundEventService);
	}

	@Test
	void invalidEventsAreReportedAsProblemDetails() throws Exception {
		when(inboundEventService.handle(any(InboundEventRequest.class)))
				.thenThrow(new InvalidInboundEventException("The event payload is invalid."));

		mockMvc.perform(post(URL).with(service).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("The event payload is invalid."));
	}
}
