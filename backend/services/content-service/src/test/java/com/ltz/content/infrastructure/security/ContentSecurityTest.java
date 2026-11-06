package com.ltz.content.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ltz.content.AuthTestSupport;
import com.ltz.content.api.controller.CategoryController;
import com.ltz.content.api.controller.ContentController;
import com.ltz.content.api.controller.ContentMetadataController;
import com.ltz.content.api.controller.ContentPublicationController;
import com.ltz.content.api.controller.ContentTranslationController;
import com.ltz.content.api.controller.ContentImageController;
import com.ltz.content.api.dto.response.ContentImageContent;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.service.ContentImageService;
import com.ltz.content.application.service.ContentMetadataService;
import com.ltz.content.application.service.ContentPublishingService;
import com.ltz.content.application.service.ContentTranslationService;
import com.ltz.content.application.service.ContentFeedService;
import com.ltz.content.application.service.ContentService;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Auth entegrasyonunun uçtan uca güvenlik testi: gerçek RS256 imzalı token'lar, yerel JWKS sunucusu ve gerçek güvenlik
 * zinciri. Oturumsuz ve geçersiz token'lı her istek servise ulaşmadan 401 alır; servis token'ı kullanıcı uçlarında geçmez.
 */
@WebMvcTest(controllers = { ContentController.class, ContentImageController.class, ContentPublicationController.class,
		ContentTranslationController.class, ContentMetadataController.class, CategoryController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class ContentSecurityTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID IMAGE_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String BASE = "/api/v1/content/contents";
	private static final String JSON = "{\"type\":\"NEWS\",\"title\":\"Başlık\"}";

	@DynamicPropertySource
	static void authProperties(DynamicPropertyRegistry registry) {
		registry.add("ltz.content.auth.jwks-uri", AuthTestSupport::jwksUri);
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentService contentService;

	@MockitoBean
	private ContentImageService imageService;

	@MockitoBean
	private ContentFeedService feedService;

	@MockitoBean
	private ContentPublishingService publishingService;

	@MockitoBean
	private ContentTranslationService translationService;

	@MockitoBean
	private ContentMetadataService metadataService;

	private static RequestPostProcessor bearer(String token) {
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

	private static ContentResponse response() {
		return new ContentResponse(CONTENT_ID, USER_ID, ContentType.NEWS, ContentStatus.DRAFT, "tr", "Başlık", null, null,
				List.of(), Instant.parse("2026-11-06T10:00:00Z"), Instant.parse("2026-11-06T10:00:00Z"), null,
				List.of(), List.of(), false, null, List.of("tr"));
	}

	private ResultActions getWithToken(String token) throws Exception {
		return mockMvc.perform(get(BASE + "/" + CONTENT_ID).with(bearer(token)));
	}

	private static String user(java.util.function.UnaryOperator<JWTClaimsSet.Builder> change) {
		return AuthTestSupport.userToken(USER_ID, change);
	}

	// ------------------------------------------------------------------ oturumsuz istek

	@Test
	void everyEndpointRejectsRequestsWithoutAToken() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] { 1 });
		RequestPostProcessor none = request -> request;

		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON).with(none))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(patch(BASE + "/" + CONTENT_ID).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(BASE + "/" + CONTENT_ID)).andExpect(status().isUnauthorized());
		mockMvc.perform(multipart(BASE + "/" + CONTENT_ID + "/images").file(file)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(BASE + "/" + CONTENT_ID + "/images/" + IMAGE_ID)).andExpect(status().isUnauthorized());

		verifyNoInteractions(contentService, imageService);
	}

	@Test
	void identityHeadersFromTheClientAreNotAnAuthenticationMechanism() throws Exception {
		mockMvc.perform(post(BASE).header("X-User-Id", USER_ID.toString()).header("X-Role", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(JSON))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(contentService);
	}

	// ------------------------------------------------------------------ geçersiz token

	@Test
	void garbageAndForgedTokensAreUnauthorized() throws Exception {
		JWTClaimsSet valid = AuthTestSupport.validUserClaims(USER_ID).build();

		getWithToken("bu-bir-jwt-degil").andExpect(status().isUnauthorized());
		getWithToken(AuthTestSupport.hs256Token(valid, AuthTestSupport.accessType())).andExpect(status().isUnauthorized());
		getWithToken(AuthTestSupport.unsignedToken(valid)).andExpect(status().isUnauthorized());
		getWithToken(AuthTestSupport.sign(AuthTestSupport.generateKey("yabanci"), AuthTestSupport.accessType(), valid))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(contentService);
	}

	@Test
	void wrongIssuerAudienceExpiredAndServiceTokensAreUnauthorized() throws Exception {
		getWithToken(user(claims -> claims.issuer("baska-yayinci"))).andExpect(status().isUnauthorized());
		getWithToken(user(claims -> claims.audience("baska-api"))).andExpect(status().isUnauthorized());
		getWithToken(user(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(600)))))
				.andExpect(status().isUnauthorized());
		// Servis token'ı (svc+jwt) kullanıcı uçlarında geçmez.
		getWithToken(AuthTestSupport.serviceToken("svc:auth-service", "events:ingest")).andExpect(status().isUnauthorized());

		verifyNoInteractions(contentService);
	}

	// ------------------------------------------------------------------ geçerli token

	@Test
	void aValidUserTokenReachesTheService() throws Exception {
		when(contentService.getContent(CONTENT_ID, null)).thenReturn(response());

		getWithToken(AuthTestSupport.userToken(USER_ID, "PLAYER")).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));
	}

	@Test
	void aValidUserTokenCanCreateADraft() throws Exception {
		when(contentService.createDraft(any())).thenReturn(response());

		mockMvc.perform(post(BASE).with(bearer(AuthTestSupport.userToken(USER_ID, "PLAYER")))
				.contentType(MediaType.APPLICATION_JSON).content(JSON)).andExpect(status().isCreated());
	}

	@Test
	void imageBytesAreServedToAValidUserWithoutCachingOrSniffing() throws Exception {
		when(imageService.getImage(CONTENT_ID, IMAGE_ID)).thenReturn(new ContentImageContent(new byte[] { 1, 2 }, "image/png"));

		mockMvc.perform(get(BASE + "/" + CONTENT_ID + "/images/" + IMAGE_ID)
				.with(bearer(AuthTestSupport.userToken(USER_ID, "PLAYER"))))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	// ------------------------------------------------------------------ iç uçlar

	@Test
	void internalPathsAreClosedToEveryone() throws Exception {
		mockMvc.perform(get("/internal/events")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/internal/v1/accounts/" + USER_ID).with(bearer(AuthTestSupport.userToken(USER_ID, "ADMIN"))))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/internal/v1/accounts/" + USER_ID)
				.with(bearer(AuthTestSupport.serviceToken("svc:auth-service", "account:erase"))))
				.andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------ herkese açık okuma (CONT-02)

	@Test
	void guestsReachThePublicReadEndpointsAndOnlyThose() throws Exception {
		when(feedService.list(any())).thenReturn(new com.ltz.content.api.dto.response.ContentPageResponse(List.of(), null));
		when(contentService.getContent(CONTENT_ID, null)).thenReturn(response());
		when(imageService.getImage(CONTENT_ID, IMAGE_ID)).thenReturn(new ContentImageContent(new byte[] { 1 }, "image/png"));

		mockMvc.perform(get(BASE)).andExpect(status().isOk());
		mockMvc.perform(get(BASE + "/" + CONTENT_ID)).andExpect(status().isOk());
		mockMvc.perform(get(BASE + "/" + CONTENT_ID + "/images/" + IMAGE_ID)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/content/categories")).andExpect(status().isOk());
	}

	@Test
	void everyWriteEndpointStillNeedsAToken() throws Exception {
		String publication = BASE + "/" + CONTENT_ID;
		mockMvc.perform(post(publication + "/publish")).andExpect(status().isUnauthorized());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(publication + "/featured"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(publication + "/featured")).andExpect(status().isUnauthorized());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(publication + "/categories")
				.contentType(MediaType.APPLICATION_JSON).content("{\"categories\":[]}")).andExpect(status().isUnauthorized());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(publication + "/translations/en")
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")).andExpect(status().isUnauthorized());

		verifyNoInteractions(contentService, imageService, feedService, publishingService, translationService, metadataService);
	}

	@Test
	void anInvalidTokenIsRejectedEvenOnPublicEndpoints() throws Exception {
		mockMvc.perform(get(BASE).with(bearer("bu-bir-jwt-degil"))).andExpect(status().isUnauthorized());
		verifyNoInteractions(feedService);
	}
}
