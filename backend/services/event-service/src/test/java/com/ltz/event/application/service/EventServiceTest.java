package com.ltz.event.application.service;

import com.ltz.event.api.dto.response.EventPageResponse;
import com.ltz.event.api.dto.request.EventListRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Collection;
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

	private EventService eventService;

	@BeforeEach
	void setUp() {
		eventService = new EventService(eventRepository, new EventMapper(), currentUser,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static CreateEventRequest request(String title, String description, Instant startsAt, Instant endsAt) {
		return new CreateEventRequest(EventType.GAME_NIGHT, title, description, startsAt, endsAt);
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
	void listsOpenEventsWithTheClockAndAllowedStatuses() {
		Event stored = Event.schedule(OTHER_ORGANIZER_ID, EventType.TOURNAMENT, "Turnuva", "Uzun açıklama",
				NOW.plusSeconds(60), NOW.plusSeconds(120), NOW);
		Page<Event> page = new PageImpl<>(List.of(stored), PageRequest.of(1, 5), 6);
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(any(Instant.class), any(),
				any(Pageable.class))).thenReturn(page);

		EventPageResponse response = eventService.listEvents(new EventListRequest(1, 5));

		ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<EventStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(eventRepository).findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(now.capture(), statuses.capture(),
				pageable.capture());
		assertThat(now.getValue()).isEqualTo(NOW);
		assertThat(statuses.getValue()).containsExactlyInAnyOrder(EventStatus.PLANNED, EventStatus.ACTIVE);
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
		assertThat(response.items().getFirst().organizerId()).isEqualTo(OTHER_ORGANIZER_ID);
		assertThat(response.items().getFirst().startsAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(response.items().getFirst().endsAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(response.items().getFirst().status()).isEqualTo(EventStatus.PLANNED);
	}

	@Test
	void listUsesDefaultPagingWhenNothingIsGiven() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		when(eventRepository.findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(any(Instant.class), any(),
				any(Pageable.class))).thenReturn(Page.empty());

		EventPageResponse response = eventService.listEvents(new EventListRequest(null, null));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(eventRepository).findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(any(Instant.class), any(),
				pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isZero();
		assertThat(pageable.getValue().getPageSize()).isEqualTo(EventListRequest.DEFAULT_SIZE);
		assertThat(response.items()).isEmpty();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	void unauthenticatedCallersCannotListEvents() {
		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));

		assertThatThrownBy(() -> eventService.listEvents(new EventListRequest(null, null)))
				.isInstanceOf(AuthenticationRequiredException.class);
		verifyNoInteractions(eventRepository);
	}
}
