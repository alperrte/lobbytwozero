package com.ltz.content.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.content.AuthTestSupport;
import com.ltz.content.api.dto.response.InboundEventResult;
import com.ltz.content.api.dto.response.InboundEventResult.Outcome;
import com.ltz.content.application.service.InboundEventService;
import com.ltz.content.domain.exception.InsufficientModerationAuthorityException;
import com.ltz.content.domain.exception.InvalidInboundEventException;
import com.ltz.content.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.content.infrastructure.security.SecurityConfig;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * {@code POST /internal/events}: gerçek RS256 servis token'ı ve yerel JWKS ile. Yalnız {@code aud=content-service},
 * {@code scope=events:ingest} ve zarftaki üreticiyle eşleşen {@code sub} kabul edilir; kullanıcı token'ı ve hatalı servis
 * token'ları servise ulaşmadan reddedilir.
 */
@WebMvcTest(InternalEventController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class InternalEventControllerTest {

	private static final UUID EVENT_ID = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID MODERATOR = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/internal/events";
	private static final JOSEObjectType SERVICE_TYPE = new JOSEObjectType("svc+jwt");

	@DynamicPropertySource
	static void authProperties(DynamicPropertyRegistry registry) {
		registry.add("ltz.content.auth.jwks-uri", AuthTestSupport::jwksUri);
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InboundEventService inboundEventService;

	private static String envelope(String producer) {
		return "{\"eventId\":\"" + EVENT_ID + "\",\"eventType\":\"ModerationActionTaken\",\"eventVersion\":1,"
				+ "\"occurredAt\":\"2026-11-07T10:00:00Z\",\"producer\":\"" + producer + "\",\"aggregateId\":\"" + CONTENT_ID
				+ "\",\"payload\":{\"targetType\":\"CONTENT\",\"targetId\":\"" + CONTENT_ID
				+ "\",\"action\":\"REMOVE\",\"moderatorId\":\"" + MODERATOR + "\",\"moderatorRole\":\"MODERATOR\","
				+ "\"reason\":\"Spam\"}}";
	}

	private ResultActions send(String token, String json) throws Exception {
		var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private static String adminToken() {
		return AuthTestSupport.serviceToken("svc:admin-service", "events:ingest");
	}

	private static String customServiceToken(java.util.function.UnaryOperator<JWTClaimsSet.Builder> change) {
		Instant now = Instant.now();
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
				.issuer(AuthTestSupport.ISSUER)
				.audience(AuthTestSupport.SERVICE_AUDIENCE)
				.subject("svc:admin-service")
				.jwtID(UUID.randomUUID().toString())
				.issueTime(Date.from(now))
				.notBeforeTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(30)))
				.claim("scope", "events:ingest");
		return AuthTestSupport.sign(AuthTestSupport.SIGNING_KEY, SERVICE_TYPE, change.apply(claims).build());
	}

	@Test
	void aValidServiceTokenDeliversTheEvent() throws Exception {
		when(inboundEventService.handle(any())).thenReturn(new InboundEventResult(EVENT_ID, Outcome.PROCESSED));

		send(adminToken(), envelope("admin-service")).andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
				.andExpect(jsonPath("$.outcome").value("PROCESSED"));

		verify(inboundEventService).handle(any());
	}

	@Test
	void theProducerMustMatchTheTokenCaller() throws Exception {
		send(adminToken(), envelope("social-service")).andExpect(status().isForbidden());
		send(AuthTestSupport.serviceToken("svc:social-service", "events:ingest"), envelope("admin-service"))
				.andExpect(status().isForbidden());

		verifyNoInteractions(inboundEventService);
	}

	@Test
	void theScopeIsRequired() throws Exception {
		send(AuthTestSupport.serviceToken("svc:admin-service", "account:erase"), envelope("admin-service"))
				.andExpect(status().isForbidden());
		send(AuthTestSupport.serviceToken("svc:admin-service", ""), envelope("admin-service")).andExpect(status().isForbidden());

		verifyNoInteractions(inboundEventService);
	}

	@Test
	void userTokensMissingTokensAndBrokenTokensAreUnauthorized() throws Exception {
		send(AuthTestSupport.userToken(MODERATOR, "ADMIN", "MODERATOR"), envelope("admin-service")).andExpect(status().isUnauthorized());
		send(null, envelope("admin-service")).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		send("bu-bir-jwt-degil", envelope("admin-service")).andExpect(status().isUnauthorized());

		verifyNoInteractions(inboundEventService);
	}

	@Test
	void wrongAudienceIssuerLifetimeAndSubjectAreRejected() throws Exception {
		Instant now = Instant.now();
		send(customServiceToken(claims -> claims.audience("community-service")), envelope("admin-service"))
				.andExpect(status().isUnauthorized());
		send(customServiceToken(claims -> claims.issuer("baska-yayinci")), envelope("admin-service"))
				.andExpect(status().isUnauthorized());
		send(customServiceToken(claims -> claims.expirationTime(Date.from(now.plusSeconds(600)))), envelope("admin-service"))
				.andExpect(status().isUnauthorized());
		send(customServiceToken(claims -> claims.expirationTime(Date.from(now.minusSeconds(600)))), envelope("admin-service"))
				.andExpect(status().isUnauthorized());
		send(customServiceToken(claims -> claims.subject("admin-service")), envelope("admin-service"))
				.andExpect(status().isUnauthorized());
		send(customServiceToken(claims -> claims.jwtID(null)), envelope("admin-service")).andExpect(status().isUnauthorized());

		verifyNoInteractions(inboundEventService);
	}

	@Test
	void otherInternalPathsStayClosedEvenForAValidServiceToken() throws Exception {
		mockMvc.perform(get("/internal/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/internal/v1/accounts/" + MODERATOR).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
				.andExpect(status().isForbidden());
		verifyNoInteractions(inboundEventService);
	}

	@Test
	void aServiceTokenIsNotAUserToken() throws Exception {
		mockMvc.perform(get("/api/v1/content/moderation/contents/" + CONTENT_ID + "/actions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidEnvelopesAreBadRequests() throws Exception {
		String[] invalid = { "{}", "{\"eventId\":\"" + EVENT_ID + "\"}", envelope("admin-service").replace("\"eventVersion\":1", "\"eventVersion\":0"),
				envelope("admin-service").replace("\"producer\":\"admin-service\"", "\"producer\":\"Admin Service\""),
				envelope("admin-service").replace("\"payload\"", "\"extra\":1,\"payload\""), "{\"eventId\":" };
		for (String json : invalid) {
			send(adminToken(), json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(inboundEventService);
	}

	@Test
	void serviceRejectionsBecomeBadRequests() throws Exception {
		when(inboundEventService.handle(any())).thenThrow(new InvalidInboundEventException("The event payload is invalid."));

		send(adminToken(), envelope("admin-service")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("The event payload is invalid."));
	}

	@Test
	void rolesAreNotReadFromServiceTokens() throws Exception {
		String withRoles = customServiceToken(claims -> claims.claim("roles", List.of("ADMIN")).claim("scope", "events:read"));

		send(withRoles, envelope("admin-service")).andExpect(status().isForbidden());
	}
	@Test
	void insufficientDecisionAuthorityIsForbiddenWithAConstantMessage() throws Exception {
		when(inboundEventService.handle(any())).thenThrow(new InsufficientModerationAuthorityException());

		send(adminToken(), envelope("admin-service")).andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("The deciding authority is not sufficient for this content."));
	}
}
