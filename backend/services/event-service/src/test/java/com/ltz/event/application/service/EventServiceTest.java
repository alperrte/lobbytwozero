package com.ltz.event.application.service;

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
}
