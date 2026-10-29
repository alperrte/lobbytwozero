package com.ltz.community.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

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

import com.ltz.community.AuthTestSupport;
import com.ltz.community.api.controller.AccountErasureController;
import com.ltz.community.api.controller.InternalEventController;
import com.ltz.community.api.controller.PostLikeController;
import com.ltz.community.api.controller.UserPostController;
import com.ltz.community.api.dto.response.InboundEventResult;
import com.ltz.community.api.dto.response.InboundEventResult.Outcome;
import com.ltz.community.api.dto.response.LikeStateResponse;
import com.ltz.community.api.dto.response.PostPageResponse;
import com.ltz.community.application.service.AccountErasureService;
import com.ltz.community.application.service.InboundEventService;
import com.ltz.community.application.service.PostFeedService;
import com.ltz.community.application.service.PostLikeService;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Auth Service entegrasyonunun uçtan uca güvenlik testi: gerçek RS256 imzalı token'lar, yerel JWKS sunucusu ve gerçek
 * güvenlik zinciri. docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md §5'teki zorunlu test tablosunun
 * community karşılığıdır (token türü yola göre seçilir; kullanıcı token'ı iç uçta, servis token'ı kullanıcı uçlarında geçmez).
 */
@WebMvcTest(controllers = { AccountErasureController.class, InternalEventController.class, PostLikeController.class,
		UserPostController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class InternalEndpointSecurityTest {

	private static final UUID ACCOUNT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String ERASE_URL = "/internal/v1/accounts/" + ACCOUNT_ID;
	private static final String EVENTS_URL = "/internal/events";
	private static final String EVENT = """
			{"eventId":"0198a3c2-1111-7d44-9a55-3f1c2b4d5e66","eventType":"UserBlocked","eventVersion":1,
			 "occurredAt":"2026-10-21T10:00:00Z","producer":"social-service",
			 "aggregateId":"0198a3c2-2222-7d44-9a55-3f1c2b4d5e66",
			 "payload":{"blockerId":"0198a3c2-3333-7d44-9a55-3f1c2b4d5e66","blockedId":"0198a3c2-4444-7d44-9a55-3f1c2b4d5e66"}}
			""";

	@DynamicPropertySource
	static void authProperties(DynamicPropertyRegistry registry) {
		registry.add("ltz.community.auth.jwks-uri", AuthTestSupport::jwksUri);
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountErasureService erasureService;

	@MockitoBean
	private InboundEventService inboundEventService;

	@MockitoBean
	private PostLikeService postLikeService;

	@MockitoBean
	private PostFeedService postFeedService;

	private ResultActions erase(String token) throws Exception {
		return mockMvc.perform(delete(ERASE_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private static String eraseToken(UnaryOperator<JWTClaimsSet.Builder> change) {
		return AuthTestSupport.serviceToken(AuthTestSupport.AUTH_SUBJECT, AuthTestSupport.ERASE_SCOPE,
				claims -> change.apply(claims.claim("account_id", ACCOUNT_ID.toString())));
	}

	// ------------------------------------------------------------------ hesap silme ucu: kabul

	@Test
	void authsErasureTokenDeletesTheAccountAndRepeatedCallsStayNoContent() throws Exception {
		String token = AuthTestSupport.eraseToken(ACCOUNT_ID);

		erase(token).andExpect(status().isNoContent()).andExpect(content().string(""));
		erase(token).andExpect(status().isNoContent());

		verify(erasureService, times(2)).erase(ACCOUNT_ID);
	}

	// ------------------------------------------------------------------ hesap silme ucu: kimlik (401)

	@Test
	void missingOrGarbageTokensAreUnauthorized() throws Exception {
		mockMvc.perform(delete(ERASE_URL)).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		erase("bu-bir-jwt-degil").andExpect(status().isUnauthorized());
		verifyNoInteractions(erasureService);
	}

	@Test
	void userTokensAndTokensForOtherAudiencesAreUnauthorizedOnInternalEndpoints() throws Exception {
		erase(AuthTestSupport.userToken(USER_ID, "ADMIN")).andExpect(status().isUnauthorized());
		erase(eraseToken(claims -> claims.audience("social-service"))).andExpect(status().isUnauthorized());
		erase(eraseToken(claims -> claims.audience("ltz-api"))).andExpect(status().isUnauthorized());
		erase(eraseToken(claims -> claims.issuer("baska-yayinci"))).andExpect(status().isUnauthorized());
		verifyNoInteractions(erasureService);
	}

	@Test
	void expiredOverlongOrIncompleteServiceTokensAreUnauthorized() throws Exception {
		Instant now = Instant.now();
		erase(eraseToken(claims -> claims.issueTime(Date.from(now.minusSeconds(300)))
				.expirationTime(Date.from(now.minusSeconds(120))))).andExpect(status().isUnauthorized());
		erase(eraseToken(claims -> claims.expirationTime(Date.from(now.plusSeconds(300))))).andExpect(status().isUnauthorized());
		erase(eraseToken(claims -> claims.jwtID(null))).andExpect(status().isUnauthorized());
		erase(eraseToken(claims -> claims.issueTime(null))).andExpect(status().isUnauthorized());
		verifyNoInteractions(erasureService);
	}

	@Test
	void tokensWithWeakOrUnknownSignaturesAreUnauthorized() throws Exception {
		JWTClaimsSet claims = AuthTestSupport.validServiceClaims(AuthTestSupport.AUTH_SUBJECT, AuthTestSupport.ERASE_SCOPE)
				.claim("account_id", ACCOUNT_ID.toString()).build();

		erase(AuthTestSupport.hs256Token(claims, AuthTestSupport.serviceType())).andExpect(status().isUnauthorized());
		erase(AuthTestSupport.unsignedToken(claims)).andExpect(status().isUnauthorized());
		erase(AuthTestSupport.sign(AuthTestSupport.generateKey("bilinmeyen-kid"), AuthTestSupport.serviceType(), claims))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(erasureService);
	}

	// ------------------------------------------------------------------ hesap silme ucu: yetki (403)

	@Test
	void wrongScopeOrCallerOrAccountBindingIsForbidden() throws Exception {
		erase(AuthTestSupport.serviceToken(AuthTestSupport.AUTH_SUBJECT, "baska:kapsam",
				claims -> claims.claim("account_id", ACCOUNT_ID.toString()))).andExpect(status().isForbidden());
		// Başka bir servis (admin-service) silme yetkisi taşıyan token alsa bile "sub" tam olarak svc:auth-service olmalı.
		erase(AuthTestSupport.serviceToken("svc:admin-service", AuthTestSupport.ERASE_SCOPE,
				claims -> claims.claim("account_id", ACCOUNT_ID.toString()))).andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		// Çalınan token başka hesabı silemez: account_id yoldaki hesapla aynı olmalı, ve var olmalı.
		erase(AuthTestSupport.eraseToken(UUID.randomUUID())).andExpect(status().isForbidden());
		erase(AuthTestSupport.serviceToken(AuthTestSupport.AUTH_SUBJECT, AuthTestSupport.ERASE_SCOPE, claims -> claims))
				.andExpect(status().isForbidden());
		verifyNoInteractions(erasureService);
	}

	@Test
	void otherInternalPathsAndMethodsAreDeniedEvenWithAValidServiceToken() throws Exception {
		String token = AuthTestSupport.eraseToken(ACCOUNT_ID);

		mockMvc.perform(get(ERASE_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isForbidden());
		mockMvc.perform(get("/internal/baska-bir-yol").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/internal/v1/accounts/" + ACCOUNT_ID).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden());
		verifyNoInteractions(erasureService);
	}

	@Test
	void invalidAccountIdInThePathIsABadRequest() throws Exception {
		mockMvc.perform(delete("/internal/v1/accounts/not-a-uuid")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.eraseToken(ACCOUNT_ID)))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(erasureService);
	}

	// ------------------------------------------------------------------ olay girişi

	@Test
	void eventIngestionNeedsTheIngestScopeAndACallerMatchingTheProducer() throws Exception {
		when(inboundEventService.handle(any())).thenReturn(new InboundEventResult(UUID.randomUUID(), Outcome.PROCESSED));
		String ok = AuthTestSupport.serviceToken("svc:social-service", AuthTestSupport.INGEST_SCOPE, claims -> claims);

		mockMvc.perform(post(EVENTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + ok)
				.contentType(MediaType.APPLICATION_JSON).content(EVENT))
				.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PROCESSED"));
		// Silme kapsamı olay girişine izin vermez; çağıran başka servis adına olay yayınlayamaz.
		String wrongScope = AuthTestSupport.eraseToken(ACCOUNT_ID);
		String wrongCaller = AuthTestSupport.serviceToken("svc:admin-service", AuthTestSupport.INGEST_SCOPE, claims -> claims);
		mockMvc.perform(post(EVENTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongScope)
				.contentType(MediaType.APPLICATION_JSON).content(EVENT)).andExpect(status().isForbidden());
		mockMvc.perform(post(EVENTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongCaller)
				.contentType(MediaType.APPLICATION_JSON).content(EVENT)).andExpect(status().isForbidden());
		mockMvc.perform(post(EVENTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content(EVENT)).andExpect(status().isUnauthorized());
	}

	@Test
	void authsEventTokenWithTheExtraAccountIdClaimIsAccepted() throws Exception {
		when(inboundEventService.handle(any())).thenReturn(new InboundEventResult(UUID.randomUUID(), Outcome.PROCESSED));
		// Auth, olay token'ına olayın hesabını `account_id` olarak ekler (talep 05 cevabı); alıcı bunu yok sayar.
		String fromAuth = AuthTestSupport.serviceToken(AuthTestSupport.AUTH_SUBJECT, AuthTestSupport.INGEST_SCOPE,
				claims -> claims.claim("account_id", ACCOUNT_ID.toString()));
		String authEvent = """
				{"eventId":"0198a3c2-1111-7d44-9a55-3f1c2b4d5e66","eventType":"AccountStatusChanged","eventVersion":1,
				 "occurredAt":"2026-10-29T10:00:00Z","producer":"auth-service","aggregateId":"%s",
				 "payload":{"accountId":"%s","status":"SUSPENDED"}}""".formatted(ACCOUNT_ID, ACCOUNT_ID);

		mockMvc.perform(post(EVENTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + fromAuth)
				.contentType(MediaType.APPLICATION_JSON).content(authEvent))
				.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PROCESSED"));
	}

	// ------------------------------------------------------------------ kullanıcı uçları: token türü yola göre

	@Test
	void userEndpointsAcceptRealUserTokensAndRejectServiceTokens() throws Exception {
		when(postLikeService.like(POST_ID)).thenReturn(new LikeStateResponse(POST_ID, true, 1));
		String likeUrl = "/api/v1/community/posts/" + POST_ID + "/like";

		mockMvc.perform(put(likeUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "PLAYER")))
				.andExpect(status().isOk());
		// Servis token'ı (svc+jwt) kullanıcı uçlarında geçmez.
		mockMvc.perform(put(likeUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.eraseToken(ACCOUNT_ID)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put(likeUrl)).andExpect(status().isUnauthorized());
		mockMvc.perform(put(likeUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID,
				claims -> claims.audience("baska-api")))).andExpect(status().isUnauthorized());
	}

	@Test
	void publicReadsStayOpenToGuestsButRejectInvalidBearerTokens() throws Exception {
		when(postFeedService.listUserPosts(any(), any())).thenReturn(new PostPageResponse(java.util.List.of(), null));
		String url = "/api/v1/community/users/" + USER_ID + "/posts";

		mockMvc.perform(get(url)).andExpect(status().isOk());
		mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "PLAYER")))
				.andExpect(status().isOk());
		mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer sahte")).andExpect(status().isUnauthorized());
	}
}
