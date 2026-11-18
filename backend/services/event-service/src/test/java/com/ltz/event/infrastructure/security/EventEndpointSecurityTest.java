package com.ltz.event.infrastructure.security;

import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.application.port.EventSearchCriteria;
import com.ltz.event.domain.exception.GameCatalogUnavailableException;
import java.util.Set;
import com.ltz.event.application.port.GameCatalogPort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
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

	@MockitoBean
	private GameCatalogPort gameCatalog;

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

	// ------------------------------------------------------------------ liste (EVT-04)

	@Test
	void listingRequiresAValidToken() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer bu-bir-jwt-degil"))
				.andExpect(status().isUnauthorized());
		JWTClaimsSet claims = AuthTestSupport.validUserClaims(USER_ID).claim("roles", List.of("PLAYER")).build();
		String serviceTyped = AuthTestSupport.sign(AuthTestSupport.SIGNING_KEY, AuthTestSupport.serviceType(), claims);
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTyped))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void listingRejectsOutOfRangePagingWith400() throws Exception {
		String token = AuthTestSupport.userToken(USER_ID, "PLAYER");

		mockMvc.perform(get(URL).param("size", "1000").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(eventRepository);
	}

	// ------------------------------------------------------------------ arama (EVT-05)

	@Test
	void searchRejectsNulCharactersBeforeTheyReachTheDatabase() throws Exception {
		mockMvc.perform(get(URL).param("q", "a\u0000b").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + AuthTestSupport.userToken(USER_ID, "PLAYER")))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(eventRepository);
	}

	// ------------------------------------------------------------------ EVT-17 / EVT-05 (oyun)

	@Test
	void eventIsLinkedToAnAvailableGameOwnedByTheTokenSubject() throws Exception {
		UUID gameId = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
		when(gameCatalog.isAvailable(gameId)).thenReturn(true);
		saveEchoesEvent();
		String body = validBody().replace("\"type\"", "\"gameId\":\"" + gameId + "\",\"type\"");

		postWith(AuthTestSupport.userToken(USER_ID, "PLAYER"), body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gameId").value(gameId.toString()))
				.andExpect(jsonPath("$.organizerId").value(USER_ID.toString()));
	}

	@Test
	void unavailableGameIsRejectedWith422AndNothingIsSaved() throws Exception {
		UUID gameId = UUID.randomUUID();
		when(gameCatalog.isAvailable(gameId)).thenReturn(false);
		String body = validBody().replace("\"type\"", "\"gameId\":\"" + gameId + "\",\"type\"");

		postWith(AuthTestSupport.userToken(USER_ID, "PLAYER"), body).andExpect(status().isUnprocessableContent());
		verify(eventRepository, never()).save(any(Event.class));
	}

	@Test
	void gameServiceOutageOnCreateIs503AndNothingIsSaved() throws Exception {
		UUID gameId = UUID.randomUUID();
		when(gameCatalog.isAvailable(gameId)).thenThrow(new GameCatalogUnavailableException(new IllegalStateException()));
		String body = validBody().replace("\"type\"", "\"gameId\":\"" + gameId + "\",\"type\"");

		postWith(AuthTestSupport.userToken(USER_ID, "PLAYER"), body).andExpect(status().isServiceUnavailable());
		verify(eventRepository, never()).save(any(Event.class));
	}

	// ------------------------------------------------------------------ liste, arama ve filtre (kriter)

	private void stubSearch() {
		when(eventRepository.search(any(EventSearchCriteria.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(storedEvent()), PageRequest.of(0, 20), 1));
	}

	private EventSearchCriteria searchedCriteria() {
		ArgumentCaptor<EventSearchCriteria> criteria = ArgumentCaptor.forClass(EventSearchCriteria.class);
		verify(eventRepository).search(criteria.capture(), any(Pageable.class));
		return criteria.getValue();
	}

	private ResultActions listAs(String role, String name, String... values) throws Exception {
		return mockMvc.perform(get(URL).param(name, values).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + AuthTestSupport.userToken(USER_ID, role)));
	}

	@Test
	void everyRegisteredRoleListsEventsWithAValidToken() throws Exception {
		stubSearch();

		for (String role : new String[] { "PLAYER", "MODERATOR", "ADMIN", "CONTENT_CREATOR", "GAME_DEVELOPER" }) {
			mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,
					"Bearer " + AuthTestSupport.userToken(USER_ID, role)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[0].title").value("Bahar Turnuvası"))
					.andExpect(jsonPath("$.items[0].type").value("TOURNAMENT"))
					.andExpect(jsonPath("$.totalItems").value(1));
		}
	}

	@Test
	void searchRequiresAValidTokenAndRunsTheSearch() throws Exception {
		stubSearch();

		mockMvc.perform(get(URL).param("q", "bahar")).andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);

		listAs("PLAYER", "q", "bahar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].title").value("Bahar Turnuvası"));
		assertThat(searchedCriteria().titlePattern()).isEqualTo("%bahar%");
	}

	@Test
	void searchByGameNameUsesTheGameIdsFromTheCatalogAndTheEventDatabase() throws Exception {
		UUID gameId = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(Instant.class), any())).thenReturn(true);
		when(gameCatalog.findGameIdsByNamePrefix("elden")).thenReturn(Set.of(gameId));
		stubSearch();

		listAs("PLAYER", "q", "elden").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.titlePattern()).isEqualTo("%elden%");
		assertThat(criteria.textGameIds()).containsExactly(gameId);
	}

	@Test
	void searchReturns503WhenTheGameServiceIsDownAndNeverAPartialResult() throws Exception {
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(Instant.class), any())).thenReturn(true);
		when(gameCatalog.findGameIdsByNamePrefix("elden"))
				.thenThrow(new GameCatalogUnavailableException(new IllegalStateException()));

		listAs("PLAYER", "q", "elden").andExpect(status().isServiceUnavailable());
		verify(eventRepository, never()).search(any(), any());
	}

	@Test
	void filtersReachTheCriteriaAndNeverCallTheGameService() throws Exception {
		UUID gameId = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
		stubSearch();

		mockMvc.perform(get(URL)
				.param("type", "TOURNAMENT", "POLL")
				.param("gameId", gameId.toString())
				.param("from", "2099-12-05T00:00:00Z")
				.param("to", "2099-12-06T00:00:00Z")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "PLAYER")))
				.andExpect(status().isOk());

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.types()).containsExactlyInAnyOrder(EventType.TOURNAMENT, EventType.POLL);
		assertThat(criteria.gameId()).isEqualTo(gameId);
		assertThat(criteria.from()).isEqualTo(Instant.parse("2099-12-05T00:00:00Z"));
		assertThat(criteria.to()).isEqualTo(Instant.parse("2099-12-06T00:00:00Z"));
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void invalidFiltersAreRejectedBeforeTheyReachTheDatabase() throws Exception {
		listAs("PLAYER", "type", "VOTING").andExpect(status().isBadRequest());
		listAs("PLAYER", "gameId", "oyun").andExpect(status().isBadRequest());
		listAs("PLAYER", "from", "yarin").andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("from", "2099-12-06T00:00:00Z").param("to", "2099-12-05T00:00:00Z")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "PLAYER")))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void filtersRequireAValidToken() throws Exception {
		mockMvc.perform(get(URL).param("type", "POLL")).andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}

	// ------------------------------------------------------------------ EVT-06 platform

	@Test
	void eventIsCreatedWithItsOwnPlatformsAndNoGame() throws Exception {
		saveEchoesEvent();
		String body = validBody().replace("{\"type\"", "{\"platforms\":[\"XBOX\",\"PC\"],\"type\"");

		postWith(AuthTestSupport.userToken(USER_ID, "PLAYER"), body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gameId").doesNotExist())
				.andExpect(jsonPath("$.platforms[0]").value("PC"))
				.andExpect(jsonPath("$.platforms[1]").value("XBOX"));

		ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(saved.capture());
		assertThat(saved.getValue().getPlatforms()).containsExactly(EventPlatform.PC, EventPlatform.XBOX);
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void invalidPlatformInTheCreateRequestIsRejectedBeforeAnythingIsSaved() throws Exception {
		String body = validBody().replace("{\"type\"", "{\"platforms\":[\"PS5\"],\"type\"");

		postWith(AuthTestSupport.userToken(USER_ID, "PLAYER"), body).andExpect(status().isBadRequest());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void platformFilterReachesTheCriteriaTogetherWithTheOtherFilters() throws Exception {
		UUID gameId = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
		stubSearch();

		mockMvc.perform(get(URL)
				.param("platform", "PC", "XBOX")
				.param("type", "TOURNAMENT")
				.param("gameId", gameId.toString())
				.param("from", "2099-12-05T00:00:00Z")
				.param("to", "2099-12-06T00:00:00Z")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + AuthTestSupport.userToken(USER_ID, "PLAYER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].platforms").isArray());

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.platforms()).containsExactlyInAnyOrder(EventPlatform.PC, EventPlatform.XBOX);
		assertThat(criteria.types()).containsExactly(EventType.TOURNAMENT);
		assertThat(criteria.gameId()).isEqualTo(gameId);
		assertThat(criteria.from()).isEqualTo(Instant.parse("2099-12-05T00:00:00Z"));
		assertThat(criteria.to()).isEqualTo(Instant.parse("2099-12-06T00:00:00Z"));
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void invalidPlatformFilterIs400AndNeverReachesTheDatabase() throws Exception {
		listAs("PLAYER", "platform", "PS5").andExpect(status().isBadRequest());
		listAs("PLAYER", "platform", "pc").andExpect(status().isBadRequest());
		verifyNoInteractions(eventRepository);
	}

	@Test
	void platformFilterRequiresAValidToken() throws Exception {
		mockMvc.perform(get(URL).param("platform", "PC")).andExpect(status().isUnauthorized());
		verifyNoInteractions(eventRepository);
	}
}
