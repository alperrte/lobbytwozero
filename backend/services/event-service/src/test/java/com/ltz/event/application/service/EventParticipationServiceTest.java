package com.ltz.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.event.api.dto.request.EventListRequest;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.api.dto.response.ParticipationResponse;
import com.ltz.event.application.mapper.EventMapper;
import com.ltz.event.application.port.CurrentUserPort;
import com.ltz.event.application.port.EventSearchCriteria;
import com.ltz.event.application.port.GameCatalogPort;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.entity.EventParticipation;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.enums.ParticipationStatus;
import com.ltz.event.domain.exception.EventNotFoundException;
import com.ltz.event.domain.exception.ParticipationConflictException;
import com.ltz.event.domain.exception.ParticipationConflictException.Reason;
import com.ltz.event.domain.exception.ParticipationNotFoundException;
import com.ltz.event.infrastructure.persistence.repository.EventParticipationRepository;
import com.ltz.event.infrastructure.persistence.repository.EventRepository;

/** EVT-08/09/13: katılım, iptal, kontenjan ve izleyiciye özel alanlar (depo taklit edilir). */
@ExtendWith(MockitoExtension.class)
class EventParticipationServiceTest {

	private static final UUID VIEWER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID ORGANIZER_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

	@Mock
	private EventRepository eventRepository;

	@Mock
	private EventParticipationRepository participationRepository;

	@Mock
	private CurrentUserPort currentUser;

	@Mock
	private GameCatalogPort gameCatalog;

	private EventService eventService;

	@BeforeEach
	void setUp() {
		eventService = new EventService(eventRepository, participationRepository, new EventMapper(), currentUser,
				gameCatalog, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static Event openEvent(Integer capacity) {
		Event event = Event.schedule(ORGANIZER_ID, EventType.GAME_NIGHT, new Event.Options(null, Set.of(), capacity),
				"Gece", "Açıklama", new Event.Period(NOW.plusSeconds(3600), NOW.plusSeconds(7200)),
				NOW.minusSeconds(60));
		withId(event);
		return event;
	}

	/** Kimlik kalıcılaştırmada üretilir; birim testte elle atanır. */
	private static Event withId(Event event) {
		ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
		return event;
	}

	private void viewerIs(UUID id) {
		when(currentUser.requireUserId()).thenReturn(id);
	}

	private static UUID idOf(Event event) {
		return event.getId();
	}

	@Test
	void joiningReservesASeatAndReturnsAnActiveParticipation() {
		Event event = openEvent(5);
		viewerIs(VIEWER_ID);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event));
		when(participationRepository.findByEventIdAndUserIdAndStatus(idOf(event), VIEWER_ID, ParticipationStatus.ACTIVE))
				.thenReturn(Optional.empty());
		when(participationRepository.saveAndFlush(any(EventParticipation.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(participationRepository.reserveSeat(idOf(event), NOW)).thenReturn(1);

		ParticipationResponse response = eventService.joinEvent(idOf(event));

		assertThat(response.status()).isEqualTo(ParticipationStatus.ACTIVE);
		assertThat(response.eventId()).isEqualTo(idOf(event));
		assertThat(response.joinedAt()).isEqualTo(NOW);
		assertThat(response.cancelledAt()).isNull();
	}

	@Test
	void joiningAnUnknownEventIsNotFound() {
		viewerIs(VIEWER_ID);
		UUID unknown = UUID.randomUUID();
		when(eventRepository.findById(unknown)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> eventService.joinEvent(unknown)).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void theOrganizerCannotJoinTheirOwnEvent() {
		Event event = openEvent(5);
		viewerIs(ORGANIZER_ID);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.joinEvent(idOf(event)))
				.isInstanceOfSatisfying(ParticipationConflictException.class,
						e -> assertThat(e.getReason()).isEqualTo(Reason.ORGANIZER_CANNOT_JOIN));
		verify(participationRepository, never()).reserveSeat(any(), any());
	}

	@Test
	void anEventThatAlreadyEndedIsNotOpen() {
		Event event = withId(Event.schedule(ORGANIZER_ID, EventType.OTHER, "T", "D", NOW.plusSeconds(60),
				NOW.plusSeconds(120), NOW.minusSeconds(3600)));
		viewerIs(VIEWER_ID);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event));
		Clock later = Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC);
		EventService lateService = new EventService(eventRepository, participationRepository, new EventMapper(),
				currentUser, gameCatalog, later);

		assertThatThrownBy(() -> lateService.joinEvent(idOf(event)))
				.isInstanceOfSatisfying(ParticipationConflictException.class,
						e -> assertThat(e.getReason()).isEqualTo(Reason.EVENT_NOT_OPEN));
	}

	@Test
	void joiningTwiceIsRejectedWithoutTouchingTheSeatCounter() {
		Event event = openEvent(5);
		viewerIs(VIEWER_ID);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event));
		when(participationRepository.findByEventIdAndUserIdAndStatus(idOf(event), VIEWER_ID, ParticipationStatus.ACTIVE))
				.thenReturn(Optional.of(EventParticipation.join(idOf(event), VIEWER_ID, NOW)));

		assertThatThrownBy(() -> eventService.joinEvent(idOf(event)))
				.isInstanceOfSatisfying(ParticipationConflictException.class,
						e -> assertThat(e.getReason()).isEqualTo(Reason.ALREADY_JOINED));
		verify(participationRepository, never()).reserveSeat(any(), any());
	}

	@Test
	void aRaceOnTheUniqueIndexIsReportedAsAlreadyJoined() {
		Event event = openEvent(5);
		viewerIs(VIEWER_ID);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event));
		when(participationRepository.findByEventIdAndUserIdAndStatus(idOf(event), VIEWER_ID, ParticipationStatus.ACTIVE))
				.thenReturn(Optional.empty());
		when(participationRepository.saveAndFlush(any(EventParticipation.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> eventService.joinEvent(idOf(event)))
				.isInstanceOfSatisfying(ParticipationConflictException.class,
						e -> assertThat(e.getReason()).isEqualTo(Reason.ALREADY_JOINED));
	}

	@Test
	void aFullEventIsRejectedWhenTheAtomicReservationAffectsNoRow() {
		Event event = openEvent(1);
		viewerIs(VIEWER_ID);
		when(participationRepository.findByEventIdAndUserIdAndStatus(idOf(event), VIEWER_ID, ParticipationStatus.ACTIVE))
				.thenReturn(Optional.empty());
		when(participationRepository.saveAndFlush(any(EventParticipation.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(participationRepository.reserveSeat(idOf(event), NOW)).thenReturn(0);
		// Rezervasyon başarısızlığından sonra etkinlik dolu görünür.
		Event full = openEvent(1);
		ReflectionTestUtils.setField(full, "id", idOf(event));
		ReflectionTestUtils.setField(full, "participantCount", 1);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event), Optional.of(full));

		assertThatThrownBy(() -> eventService.joinEvent(idOf(event)))
				.isInstanceOfSatisfying(ParticipationConflictException.class,
						e -> assertThat(e.getReason()).isEqualTo(Reason.EVENT_FULL));
	}

	@Test
	void cancellingMarksTheOwnParticipationAndReleasesTheSeat() {
		UUID eventId = UUID.randomUUID();
		viewerIs(VIEWER_ID);
		EventParticipation participation = EventParticipation.join(eventId, VIEWER_ID, NOW.minusSeconds(60));
		when(participationRepository.findByEventIdAndUserIdAndStatus(eventId, VIEWER_ID, ParticipationStatus.ACTIVE))
				.thenReturn(Optional.of(participation));
		when(participationRepository.saveAndFlush(participation)).thenReturn(participation);

		ParticipationResponse response = eventService.cancelParticipation(eventId);

		assertThat(response.status()).isEqualTo(ParticipationStatus.CANCELLED_BY_USER);
		assertThat(response.cancelledAt()).isEqualTo(NOW);
		verify(participationRepository).releaseSeat(eventId, NOW);
	}

	@Test
	void cancellingWithoutAnActiveParticipationIsNotFound() {
		UUID eventId = UUID.randomUUID();
		viewerIs(VIEWER_ID);
		when(participationRepository.findByEventIdAndUserIdAndStatus(eventId, VIEWER_ID, ParticipationStatus.ACTIVE))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> eventService.cancelParticipation(eventId))
				.isInstanceOf(ParticipationNotFoundException.class);
		verify(participationRepository, never()).releaseSeat(any(), any());
	}

	@Test
	void detailShowsViewerStatusAndJoinableForAnOpenEventWithFreeSeats() {
		Event event = openEvent(5);
		viewerIs(VIEWER_ID);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event));
		when(participationRepository.findByUserIdAndEventIdInAndStatusIn(eq(VIEWER_ID), any(), any()))
				.thenReturn(List.of());

		EventResponse response = eventService.getEvent(idOf(event));

		assertThat(response.capacity()).isEqualTo(5);
		assertThat(response.participantCount()).isZero();
		assertThat(response.remainingCapacity()).isEqualTo(5);
		assertThat(response.viewerParticipation()).isNull();
		assertThat(response.joinable()).isTrue();
	}

	@Test
	void detailIsNotJoinableWhenTheViewerAlreadyParticipatesOrOwnsTheEvent() {
		Event event = openEvent(null);
		viewerIs(VIEWER_ID);
		when(eventRepository.findById(idOf(event))).thenReturn(Optional.of(event));
		when(participationRepository.findByUserIdAndEventIdInAndStatusIn(eq(VIEWER_ID), any(), any()))
				.thenReturn(List.of(EventParticipation.join(idOf(event), VIEWER_ID, NOW)));

		EventResponse joined = eventService.getEvent(idOf(event));

		assertThat(joined.viewerParticipation()).isEqualTo(ParticipationStatus.ACTIVE);
		assertThat(joined.joinable()).isFalse();
		assertThat(joined.capacity()).isNull();
		assertThat(joined.remainingCapacity()).isNull();
	}

	@Test
	void listItemsCarryViewerStatusAndAFullEventIsNotJoinable() {
		Event open = openEvent(5);
		Event full = openEvent(1);
		ReflectionTestUtils.setField(full, "participantCount", 1);
		viewerIs(VIEWER_ID);
		when(eventRepository.search(any(EventSearchCriteria.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(open, full)));
		when(participationRepository.findByUserIdAndEventIdInAndStatusIn(eq(VIEWER_ID), any(), any()))
				.thenReturn(List.of());

		var page = eventService.listEvents(new EventListRequest(null, null, null, null, null, null, null, null));

		assertThat(page.items()).hasSize(2);
		assertThat(page.items().get(0).joinable()).isTrue();
		assertThat(page.items().get(1).joinable()).isFalse();
		assertThat(page.items().get(1).remainingCapacity()).isZero();
	}
}
