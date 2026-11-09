package com.ltz.event.infrastructure.security;

import com.ltz.event.domain.enums.EventType;
import java.util.Optional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import com.ltz.event.AuthTestSupport;
import com.ltz.event.api.controller.EventController;
import com.ltz.event.application.mapper.EventMapper;
import com.ltz.event.application.service.EventService;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.infrastructure.config.ClockConfig;
import com.ltz.event.infrastructure.persistence.repository.EventRepository;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Auth entegrasyonunun uçtan uca güvenlik testi: gerçek RS256 imzalı token'lar, yerel JWKS sunucusu, gerçek güvenlik
 * zinciri ve gerçek servis/yetki katmanı (yalnız veritabanı deposu taklit edilir). docs-auth/d4-ekip-notlari/
 * 01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md §5'teki zorunlu test tablosunun event karşılığıdır.
 */
@WebMvcTest(controllers = EventController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class, SecurityContextCurrentUser.class,
		EventService.class, EventMapper.class, ClockConfig.class })
class EventEndpointSecurityTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/event/events";

	@DynamicPropertySource
	static void authProperties(DynamicPropertyRegistry registry) {
		registry.add("ltz.event.auth.jwks-uri", AuthTestSupport::jwksUri);
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventRepository eventRepository;

	/** Şimdiden bir gün sonra başlayıp üç saat süren geçerli bir istek gövdesi. */
	private static String validBody() {
		Instant startsAt = Instant.now().plusSeconds(86_400);
		return """
				{"type":"TOURNAMENT","title":"Bahar Turnuvası","description":"Eleme usulü.",
				 "startsAt":"%s","endsAt":"%s"}
				""".formatted(startsAt, startsAt.plusSeconds(10_800));
	}

	private ResultActions postWith(String token, String body) throws Exception {
		var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(body);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private void saveEchoesEvent() {
		when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	// ------------------------------------------------------------------ başarılı akış ve sahiplik

	@Test
	void adminWithValidTokenCreatesAnEventOwnedByTheTokenSubject() throws Exception {
		saveEchoesEvent();

		postWith(AuthTestSupport.userToken(USER_ID, "ADMIN"), validBody())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.organizerId").value(USER_ID.toString()))
				.andExpect(jsonPath("$.status").value("PLANNED"))
				.andExpect(jsonPath("$.type").value("TOURNAMENT"));

		ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(saved.capture());
		assertThat(saved.getValue().getOrganizerId()).isEqualTo(USER_ID);
		assertThat(saved.getValue().getStatus()).isEqualTo(EventStatus.PLANNED);
	}

	@Test
	void moderatorWithValidTokenCreatesAnEvent() throws Exception {
		saveEchoesEvent();

		postWith(AuthTestSupport.userToken(USER_ID, "MODERATOR"), validBody()).andExpect(status().isCreated());
	}

	@Test
	void clientSuppliedIdentityHeadersAreIgnored() throws Exception {
		saveEchoesEvent();

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(validBody())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "ADMIN"))
				.header("X-User-Id", OTHER_ID.toString())
				.header("X-Role", "ADMIN"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.organizerId").value(USER_ID.toString()));

		ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(saved.capture());
		assertThat(saved.getValue().getOrganizerId()).isEqualTo(USER_ID);
	}

	@Test
	void clientCannotSetTheOrganizerInTheBody() throws Exception {
		String body = validBody().replace("{\"type\"", "{\"organizerId\":\"" + OTHER_ID + "\",\"type\"");

		postWith(AuthTestSupport.userToken(USER_ID, "ADMIN"), body).andExpect(status().isBadRequest());
		verifyNoInteractions(eventRepository);
	}

	// ------------------------------------------------------------------ rol (403) ve kimlik (401)

	@Test
	void registeredPlayerWithValidTokenCreatesAnEventOwnedByTheTokenSubject() throws Exception {
		saveEchoesEvent();

		postWith(AuthTestSupport.userToken(USER_ID, "PLAYER"), validBody())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.organizerId").value(USER_ID.toString()));
	}

	@Test
	void accountWithAdditionalRolesCreatesAnEventThroughThePlayerBaseRole() throws Exception {
		saveEchoesEvent();

		postWith(AuthTestSupport.userToken(USER_ID, "PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER"), validBody())
				.andExpect(status().isCreated());
	}

	@Test
	void rolesOutsideTheCreatorSetAreForbidden() throws Exception {
		for (String role : new String[] { "CONTENT_CREATOR", "GAME_DEVELOPER" }) {
			postWith(AuthTestSupport.userToken(USER_ID, role), validBody())
					.andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		verifyNoInteractions(eventRepository);
	}

	@Test
	void tokenWithoutRolesIsForbidden() throws Exception {
		postWith(AuthTestSupport.userToken(USER_ID, claims -> claims.claim("roles", List.of())), validBody())
				.andExpect(status().isForbidden());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void missingTokenIsUnauthorized() throws Exception {
		postWith(null, validBody())
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(eventRepository);
	}

	@Test
	void malformedTokenIsUnauthorized() throws Exception {
		postWith("bu-bir-jwt-degil", validBody()).andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		Instant past = Instant.now().minusSeconds(3600);
		String token = AuthTestSupport.userToken(USER_ID, claims -> claims.claim("roles", List.of("ADMIN"))
				.issueTime(Date.from(past.minusSeconds(300))).notBeforeTime(Date.from(past.minusSeconds(300)))
				.expirationTime(Date.from(past)));

		postWith(token, validBody()).andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void wrongIssuerAudienceAndMissingClaimsAreUnauthorized() throws Exception {
		UnaryOperator<JWTClaimsSet.Builder> admin = claims -> claims.claim("roles", List.of("ADMIN"));
		String wrongIssuer = AuthTestSupport.userToken(USER_ID, claims -> admin.apply(claims).issuer("baska-yayinci"));
		String wrongAudience = AuthTestSupport.userToken(USER_ID, claims -> admin.apply(claims).audience("baska-servis"));
		String noSubject = AuthTestSupport.userToken(USER_ID, claims -> admin.apply(claims).subject(null));
		String noIssuedAt = AuthTestSupport.userToken(USER_ID, claims -> admin.apply(claims).issueTime(null));

		for (String token : new String[] { wrongIssuer, wrongAudience, noSubject, noIssuedAt }) {
			postWith(token, validBody()).andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(eventRepository);
	}

	@Test
	void serviceTokenTypeIsNotAcceptedOnUserEndpoints() throws Exception {
		JWTClaimsSet claims = AuthTestSupport.validUserClaims(USER_ID).claim("roles", List.of("ADMIN")).build();
		String serviceTyped = AuthTestSupport.sign(AuthTestSupport.SIGNING_KEY, AuthTestSupport.serviceType(), claims);

		postWith(serviceTyped, validBody()).andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void tokensSignedWithUnsupportedOrUnknownKeysAreUnauthorized() throws Exception {
		JWTClaimsSet claims = AuthTestSupport.validUserClaims(USER_ID).claim("roles", List.of("ADMIN")).build();
		RSAKey strangerKey = AuthTestSupport.generateKey(AuthTestSupport.KEY_ID);

		postWith(AuthTestSupport.sign(strangerKey, AuthTestSupport.accessType(), claims), validBody())
				.andExpect(status().isUnauthorized());
		postWith(AuthTestSupport.hs256Token(claims, AuthTestSupport.accessType()), validBody())
				.andExpect(status().isUnauthorized());
		postWith(AuthTestSupport.unsignedToken(claims), validBody()).andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}

	// ------------------------------------------------------------------ iş kuralları (gerçek servis ile)

	@Test
	void pastStartIsRejectedWith400AndNothingIsSaved() throws Exception {
		Instant past = Instant.now().minusSeconds(3600);
		String body = """
				{"type":"COMMUNITY","title":"Geçmiş","description":"Geçmişte başlıyor.",
				 "startsAt":"%s","endsAt":"%s"}
				""".formatted(past, past.plusSeconds(7200));

		postWith(AuthTestSupport.userToken(USER_ID, "ADMIN"), body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.reason").value("STARTS_IN_THE_PAST"));
		verify(eventRepository, never()).save(any(Event.class));
	}

	@Test
	void endNotAfterStartIsRejectedWith400AndNothingIsSaved() throws Exception {
		Instant startsAt = Instant.now().plusSeconds(86_400);
		String body = """
				{"type":"COMMUNITY","title":"Ters","description":"Bitiş başlangıçtan önce.",
				 "startsAt":"%s","endsAt":"%s"}
				""".formatted(startsAt, startsAt.minusSeconds(60));

		postWith(AuthTestSupport.userToken(USER_ID, "MODERATOR"), body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.reason").value("ENDS_NOT_AFTER_START"));
		verify(eventRepository, never()).save(any(Event.class));
	}

	// ------------------------------------------------------------------ kapalı yollar

	@Test
	void internalPathsAreClosedEvenForAdmins() throws Exception {
		mockMvc.perform(delete("/internal/v1/accounts/" + OTHER_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "ADMIN")))
				.andExpect(status().isForbidden());
	}

	@Test
	void unknownApiPathsRequireAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/event/unknown").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------ detay okuma (EVT-03)

	private ResultActions getWith(String token, UUID eventId) throws Exception {
		var request = get(URL + "/" + eventId);
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private static Event storedEvent() {
		return Event.schedule(OTHER_ID, EventType.TOURNAMENT, "Bahar Turnuvası", "Eleme usulü.",
				Instant.now().plusSeconds(86_400), Instant.now().plusSeconds(97_200), Instant.now());
	}

	@Test
	void everyRegisteredRoleReadsEventDetailsWithAValidToken() throws Exception {
		UUID eventId = UUID.randomUUID();
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(storedEvent()));

		for (String role : new String[] { "PLAYER", "MODERATOR", "ADMIN", "CONTENT_CREATOR", "GAME_DEVELOPER" }) {
			getWith(AuthTestSupport.userToken(USER_ID, role), eventId)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.title").value("Bahar Turnuvası"))
					.andExpect(jsonPath("$.organizerId").value(OTHER_ID.toString()));
		}
	}

	@Test
	void eventDetailsRequireAValidToken() throws Exception {
		UUID eventId = UUID.randomUUID();

		getWith(null, eventId).andExpect(status().isUnauthorized());
		getWith("bu-bir-jwt-degil", eventId).andExpect(status().isUnauthorized());
		JWTClaimsSet claims = AuthTestSupport.validUserClaims(USER_ID).claim("roles", List.of("PLAYER")).build();
		getWith(AuthTestSupport.sign(AuthTestSupport.SIGNING_KEY, AuthTestSupport.serviceType(), claims), eventId)
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void unknownEventReturns404ForAnAuthenticatedUser() throws Exception {
		UUID eventId = UUID.randomUUID();
		when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

		getWith(AuthTestSupport.userToken(USER_ID, "PLAYER"), eventId).andExpect(status().isNotFound());
	}
}
