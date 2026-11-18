package com.ltz.event.application.service;

import com.ltz.event.domain.enums.EventPlatform;
import java.util.Arrays;
import com.ltz.event.application.port.EventSearchCriteria;
import java.util.Set;
import com.ltz.event.domain.exception.GameSearchTooBroadException;
import com.ltz.event.domain.exception.GameNotAvailableException;
import com.ltz.event.domain.exception.GameCatalogUnavailableException;
import com.ltz.event.application.port.GameCatalogPort;
import static org.mockito.ArgumentMatchers.eq;
import com.ltz.event.api.dto.response.EventPageResponse;
import com.ltz.event.api.dto.request.EventListRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import java.util.List;
import com.ltz.event.domain.exception.EventNotFoundException;
import static org.mockito.Mockito.never;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ltz.event.api.dto.request.CreateEventRequest;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.application.mapper.EventMapper;
import com.ltz.event.application.port.CurrentUserPort;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.exception.AuthenticationRequiredException;
import com.ltz.event.domain.exception.EventCreationForbiddenException;
import com.ltz.event.domain.exception.InvalidEventScheduleException;
import com.ltz.event.infrastructure.persistence.repository.EventRepository;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER_ORGANIZER_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

	@Mock
	private EventRepository eventRepository;

	@Mock
	private CurrentUserPort currentUser;

	@Mock
	private GameCatalogPort gameCatalog;

	private EventService eventService;

	@BeforeEach
	void setUp() {
		eventService = new EventService(eventRepository, new EventMapper(), currentUser, gameCatalog,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static CreateEventRequest request(String title, String description, Instant startsAt, Instant endsAt) {
		return new CreateEventRequest(EventType.GAME_NIGHT, title, description, startsAt, endsAt, null, null);
	}

	@Test
	void authorizedUserCreatesAPlannedEventOwnedByTheAuthenticatedUser() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		EventResponse response = eventService.createEvent(
				request("Cuma Gecesi", "Birlikte oynuyoruz.", NOW.plusSeconds(3600), NOW.plusSeconds(10800)));

		ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(saved.capture());
		assertThat(saved.getValue().getOrganizerId()).isEqualTo(USER_ID);
		assertThat(saved.getValue().getStatus()).isEqualTo(EventStatus.PLANNED);
		assertThat(response.organizerId()).isEqualTo(USER_ID);
		assertThat(response.type()).isEqualTo(EventType.GAME_NIGHT);
		assertThat(response.title()).isEqualTo("Cuma Gecesi");
		assertThat(response.status()).isEqualTo(EventStatus.PLANNED);
		assertThat(response.startsAt()).isEqualTo(NOW.plusSeconds(3600));
		assertThat(response.endsAt()).isEqualTo(NOW.plusSeconds(10800));
		assertThat(response.createdAt()).isEqualTo(NOW);
	}

	@Test
	void titleAndDescriptionAreTrimmedBeforeSaving() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		eventService.createEvent(request("  Turnuva  ", "\n Açıklama \t", NOW.plusSeconds(60), NOW.plusSeconds(120)));

		ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(saved.capture());
		assertThat(saved.getValue().getTitle()).isEqualTo("Turnuva");
		assertThat(saved.getValue().getDescription()).isEqualTo("Açıklama");
	}

	@Test
	void userWithoutACreatorRoleCannotCreateEvents() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(false);

		assertThatThrownBy(() -> eventService.createEvent(
				request("Başlık", "Açıklama", NOW.plusSeconds(60), NOW.plusSeconds(120))))
				.isInstanceOf(EventCreationForbiddenException.class);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void unauthenticatedCallersAreRejectedBeforeAnythingIsSaved() {
		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));

		assertThatThrownBy(() -> eventService.createEvent(
				request("Başlık", "Açıklama", NOW.plusSeconds(60), NOW.plusSeconds(120))))
				.isInstanceOf(AuthenticationRequiredException.class);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void pastStartIsRejectedAndNothingIsSaved() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);

		assertThatThrownBy(() -> eventService.createEvent(
				request("Başlık", "Açıklama", NOW.minusSeconds(60), NOW.plusSeconds(120))))
				.isInstanceOf(InvalidEventScheduleException.class);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void endNotAfterStartIsRejectedAndNothingIsSaved() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);

		assertThatThrownBy(() -> eventService.createEvent(
				request("Başlık", "Açıklama", NOW.plusSeconds(120), NOW.plusSeconds(60))))
				.isInstanceOf(InvalidEventScheduleException.class);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void authenticatedUserReadsAnyEventById() {
		Event stored = Event.schedule(OTHER_ORGANIZER_ID, EventType.POLL, "Oylama", "Hangi oyun?", NOW.plusSeconds(60),
				NOW.plusSeconds(120), NOW);
		UUID eventId = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(stored));

		EventResponse response = eventService.getEvent(eventId);

		assertThat(response.organizerId()).isEqualTo(OTHER_ORGANIZER_ID);
		assertThat(response.type()).isEqualTo(EventType.POLL);
		assertThat(response.title()).isEqualTo("Oylama");
		assertThat(response.description()).isEqualTo("Hangi oyun?");
		assertThat(response.startsAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(response.endsAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(response.status()).isEqualTo(EventStatus.PLANNED);
	}

	@Test
	void readingDoesNotRequireTheCreatorRole() {
		UUID eventId = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(
				Event.schedule(OTHER_ORGANIZER_ID, EventType.OTHER, "T", "D", NOW.plusSeconds(60), NOW.plusSeconds(120), NOW)));

		assertThat(eventService.getEvent(eventId)).isNotNull();
		verify(currentUser, never()).canCreateEvents();
	}

	@Test
	void unknownEventIsReportedAsNotFound() {
		UUID eventId = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> eventService.getEvent(eventId)).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void unauthenticatedCallersCannotReadEvents() {
		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));

		assertThatThrownBy(() -> eventService.getEvent(UUID.randomUUID()))
				.isInstanceOf(AuthenticationRequiredException.class);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void unauthenticatedCallersCannotListEvents() {
		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));

		assertThatThrownBy(() -> eventService.listEvents(EventListRequest.paged(null, null, null)))
				.isInstanceOf(AuthenticationRequiredException.class);
		verifyNoInteractions(eventRepository);
	}

	// ------------------------------------------------------------------ EVT-17 oyun bağlantısı

	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");

	private CreateEventRequest requestWithGame(UUID gameId) {
		return new CreateEventRequest(EventType.GAME_NIGHT, "Oyun Gecesi", "Birlikte", NOW.plusSeconds(60),
				NOW.plusSeconds(120), gameId, null);
	}

	@Test
	void eventCanBeLinkedToAnAvailableGame() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		EventResponse response = eventService.createEvent(requestWithGame(GAME_ID));

		ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(saved.capture());
		assertThat(saved.getValue().getGameId()).isEqualTo(GAME_ID);
		assertThat(response.gameId()).isEqualTo(GAME_ID);
	}

	@Test
	void unavailableGameIsRejectedAndNothingIsSaved() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(false);

		assertThatThrownBy(() -> eventService.createEvent(requestWithGame(GAME_ID)))
				.isInstanceOf(GameNotAvailableException.class);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void catalogOutageIsNotReportedAsMissingGameAndNothingIsSaved() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(gameCatalog.isAvailable(GAME_ID)).thenThrow(new GameCatalogUnavailableException(new IllegalStateException()));

		assertThatThrownBy(() -> eventService.createEvent(requestWithGame(GAME_ID)))
				.isInstanceOf(GameCatalogUnavailableException.class);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void gameServiceIsNotCalledWithoutAGameId() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		EventResponse response = eventService.createEvent(requestWithGame(null));

		assertThat(response.gameId()).isNull();
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void invalidScheduleIsRejectedBeforeTheGameServiceIsCalled() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);

		assertThatThrownBy(() -> eventService.createEvent(new CreateEventRequest(EventType.OTHER, "T", "D",
				NOW.minusSeconds(60), NOW.plusSeconds(120), GAME_ID, null)))
				.isInstanceOf(InvalidEventScheduleException.class);
		verifyNoInteractions(gameCatalog);
		verifyNoInteractions(eventRepository);
	}

	@Test
	void unauthorizedCallerCannotTriggerGameServiceCalls() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(false);

		assertThatThrownBy(() -> eventService.createEvent(requestWithGame(GAME_ID)))
				.isInstanceOf(EventCreationForbiddenException.class);
		verifyNoInteractions(gameCatalog);
	}

	// ------------------------------------------------------------------ EVT-05 oyun adına göre arama

	private Event linkedEvent(String title, UUID gameId) {
		return Event.schedule(OTHER_ORGANIZER_ID, EventType.GAME_NIGHT, gameId, title, "D", NOW.plusSeconds(60),
				NOW.plusSeconds(120), NOW);
	}

	// ------------------------------------------------------------------ EVT-04/05/06 liste, arama, filtre (kriter)

	private EventSearchCriteria searchedCriteria() {
		ArgumentCaptor<EventSearchCriteria> criteria = ArgumentCaptor.forClass(EventSearchCriteria.class);
		verify(eventRepository).search(criteria.capture(), any(Pageable.class));
		return criteria.getValue();
	}

	private void stubEmptySearch() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.search(any(EventSearchCriteria.class), any(Pageable.class))).thenReturn(Page.empty());
	}

	@Test
	void listsOpenEventsWithTheClockAllowedStatusesAndPaging() {
		Event stored = Event.schedule(OTHER_ORGANIZER_ID, EventType.TOURNAMENT, GAME_ID, "Turnuva", "Uzun açıklama",
				NOW.plusSeconds(60), NOW.plusSeconds(120), NOW);
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.search(any(EventSearchCriteria.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(stored), PageRequest.of(1, 5), 6));

		EventPageResponse response = eventService.listEvents(EventListRequest.paged(1, 5, null));

		ArgumentCaptor<EventSearchCriteria> criteria = ArgumentCaptor.forClass(EventSearchCriteria.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(eventRepository).search(criteria.capture(), pageable.capture());
		assertThat(criteria.getValue().now()).isEqualTo(NOW);
		assertThat(criteria.getValue().statuses()).containsExactlyInAnyOrder(EventStatus.PLANNED, EventStatus.ACTIVE);
		assertThat(criteria.getValue().titlePattern()).isNull();
		assertThat(criteria.getValue().textGameIds()).isEmpty();
		assertThat(criteria.getValue().types()).isEmpty();
		assertThat(criteria.getValue().gameId()).isNull();
		assertThat(criteria.getValue().from()).isNull();
		assertThat(criteria.getValue().to()).isNull();
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();

		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(5);
		assertThat(response.totalItems()).isEqualTo(6);
		assertThat(response.totalPages()).isEqualTo(2);
		assertThat(response.hasNext()).isFalse();
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().title()).isEqualTo("Turnuva");
		assertThat(response.items().getFirst().type()).isEqualTo(EventType.TOURNAMENT);
		assertThat(response.items().getFirst().gameId()).isEqualTo(GAME_ID);
		assertThat(response.items().getFirst().organizerId()).isEqualTo(OTHER_ORGANIZER_ID);
		assertThat(response.items().getFirst().startsAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(response.items().getFirst().endsAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(response.items().getFirst().status()).isEqualTo(EventStatus.PLANNED);
	}

	@Test
	void listUsesDefaultPagingWhenNothingIsGiven() {
		stubEmptySearch();

		EventPageResponse response = eventService.listEvents(EventListRequest.paged(null, null, null));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(eventRepository).search(any(EventSearchCriteria.class), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isZero();
		assertThat(pageable.getValue().getPageSize()).isEqualTo(EventListRequest.DEFAULT_SIZE);
		assertThat(response.items()).isEmpty();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	void listWithoutSearchTextNeverTouchesTheGameService() {
		stubEmptySearch();

		eventService.listEvents(EventListRequest.paged(null, null, null));

		verifyNoInteractions(gameCatalog);
		verify(eventRepository, never()).existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(), any());
	}

	@Test
	void blankSearchTextIsTreatedAsNoSearch() {
		stubEmptySearch();

		eventService.listEvents(EventListRequest.paged(null, null, "   "));

		assertThat(searchedCriteria().titlePattern()).isNull();
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void searchTextBuildsAnEscapedPatternAndSkipsTheGameServiceWhenNoListedEventHasAGame() {
		stubEmptySearch();
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(Instant.class), any())).thenReturn(false);

		eventService.listEvents(EventListRequest.paged(null, null, "  %Tur_nuva  "));

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.titlePattern()).isEqualTo("%!%Tur!_nuva%");
		assertThat(criteria.textGameIds()).isEmpty();
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void searchAlsoMatchesEventsLinkedToGamesFoundByName() {
		Set<UUID> games = Set.of(GAME_ID);
		stubEmptySearch();
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(eq(NOW), any())).thenReturn(true);
		when(gameCatalog.findGameIdsByNamePrefix("elden")).thenReturn(games);

		eventService.listEvents(EventListRequest.paged(null, null, " elden "));

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.titlePattern()).isEqualTo("%elden%");
		assertThat(criteria.textGameIds()).isEqualTo(games);
	}

	@Test
	void searchWithNoMatchingGameUsesTheTitleOnly() {
		stubEmptySearch();
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(Instant.class), any())).thenReturn(true);
		when(gameCatalog.findGameIdsByNamePrefix("zzz")).thenReturn(Set.of());

		eventService.listEvents(EventListRequest.paged(null, null, "zzz"));

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.titlePattern()).isEqualTo("%zzz%");
		assertThat(criteria.textGameIds()).isEmpty();
	}

	@Test
	void gameServiceOutageDuringSearchIsNotHiddenByATitleOnlyFallback() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(Instant.class), any())).thenReturn(true);
		when(gameCatalog.findGameIdsByNamePrefix("elden"))
				.thenThrow(new GameCatalogUnavailableException(new IllegalStateException()));

		assertThatThrownBy(() -> eventService.listEvents(EventListRequest.paged(null, null, "elden")))
				.isInstanceOf(GameCatalogUnavailableException.class);
		verify(eventRepository, never()).search(any(), any());
	}

	@Test
	void tooBroadGameSearchIsNotTruncated() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(Instant.class), any())).thenReturn(true);
		when(gameCatalog.findGameIdsByNamePrefix("a")).thenThrow(new GameSearchTooBroadException(1000));

		assertThatThrownBy(() -> eventService.listEvents(EventListRequest.paged(null, null, "a")))
				.isInstanceOf(GameSearchTooBroadException.class);
		verify(eventRepository, never()).search(any(), any());
	}

	@Test
	void filtersArePassedToTheCriteriaWithoutCallingTheGameService() {
		stubEmptySearch();
		Instant from = NOW.plusSeconds(3600);
		Instant to = NOW.plusSeconds(7200);

		eventService.listEvents(new EventListRequest(null, null, null,
				Arrays.asList(EventType.TOURNAMENT, EventType.POLL, EventType.TOURNAMENT, null),
				Arrays.asList(EventPlatform.PC, EventPlatform.XBOX, EventPlatform.PC, null), GAME_ID, from, to));

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.types()).containsExactlyInAnyOrder(EventType.TOURNAMENT, EventType.POLL);
		assertThat(criteria.platforms()).containsExactlyInAnyOrder(EventPlatform.PC, EventPlatform.XBOX);
		assertThat(criteria.gameId()).isEqualTo(GAME_ID);
		assertThat(criteria.from()).isEqualTo(from);
		assertThat(criteria.to()).isEqualTo(to);
		assertThat(criteria.titlePattern()).isNull();
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void filtersAndSearchTextCombine() {
		stubEmptySearch();
		when(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(any(Instant.class), any())).thenReturn(true);
		when(gameCatalog.findGameIdsByNamePrefix("elden")).thenReturn(Set.of(GAME_ID));

		eventService.listEvents(new EventListRequest(null, null, "elden", List.of(EventType.GAME_NIGHT),
				List.of(EventPlatform.VR), null, null, null));

		EventSearchCriteria criteria = searchedCriteria();
		assertThat(criteria.titlePattern()).isEqualTo("%elden%");
		assertThat(criteria.textGameIds()).containsExactly(GAME_ID);
		assertThat(criteria.types()).containsExactly(EventType.GAME_NIGHT);
		assertThat(criteria.platforms()).containsExactly(EventPlatform.VR);
	}

	// ------------------------------------------------------------------ EVT-06 platform

	@Test
	void eventIsCreatedWithItsOwnPlatformsWithoutAGame() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		EventResponse response = eventService.createEvent(new CreateEventRequest(EventType.TOURNAMENT, "Turnuva", "D",
				NOW.plusSeconds(60), NOW.plusSeconds(120), null,
				Arrays.asList(EventPlatform.XBOX, EventPlatform.PC, EventPlatform.XBOX, null)));

		ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(saved.capture());
		assertThat(saved.getValue().getPlatforms()).containsExactly(EventPlatform.PC, EventPlatform.XBOX);
		assertThat(saved.getValue().getGameId()).isNull();
		assertThat(response.platforms()).containsExactly(EventPlatform.PC, EventPlatform.XBOX);
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void eventWithoutPlatformsHasAnEmptyPlatformListNotADerivedOne() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(currentUser.canCreateEvents()).thenReturn(true);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		EventResponse linked = eventService.createEvent(requestWithGame(GAME_ID));
		EventResponse unlinked = eventService.createEvent(requestWithGame(null));

		assertThat(linked.platforms()).isEmpty();
		assertThat(unlinked.platforms()).isEmpty();
	}

	@Test
	void platformsAreReturnedInListAndDetailResponses() {
		Event stored = Event.schedule(OTHER_ORGANIZER_ID, EventType.TOURNAMENT, null,
				Set.of(EventPlatform.NINTENDO_SWITCH, EventPlatform.PC), "Turnuva", "D", NOW.plusSeconds(60),
				NOW.plusSeconds(120), NOW);
		UUID eventId = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(stored));
		when(eventRepository.search(any(EventSearchCriteria.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(stored), PageRequest.of(0, 20), 1));

		assertThat(eventService.getEvent(eventId).platforms())
				.containsExactly(EventPlatform.PC, EventPlatform.NINTENDO_SWITCH);
		assertThat(eventService.listEvents(EventListRequest.paged(null, null, null)).items().getFirst().platforms())
				.containsExactly(EventPlatform.PC, EventPlatform.NINTENDO_SWITCH);
	}

	@Test
	void withoutAPlatformFilterTheCriteriaHasNoPlatforms() {
		stubEmptySearch();

		eventService.listEvents(EventListRequest.paged(null, null, null));

		assertThat(searchedCriteria().platforms()).isEmpty();
	}
}
