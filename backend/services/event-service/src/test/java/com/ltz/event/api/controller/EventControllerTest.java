package com.ltz.event.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ltz.event.api.dto.request.CreateEventRequest;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.application.service.EventService;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.exception.EventCreationForbiddenException;
import com.ltz.event.domain.exception.InvalidEventScheduleException;
import com.ltz.event.domain.exception.InvalidEventScheduleException.Reason;
import com.ltz.event.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.event.infrastructure.security.SecurityConfig;

/** HTTP sözleşmesi: durum kodları, doğrulama ve hata eşlemeleri (servis taklit edilir). */
@WebMvcTest(EventController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class EventControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID EVENT_ID = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/event/events";
	private static final String VALID = """
			{"type":"GAME_NIGHT","title":"Cuma Oyun Gecesi","description":"Birlikte oynuyoruz.",
			 "startsAt":"2099-12-05T18:00:00Z","endsAt":"2099-12-05T21:00:00Z"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventService eventService;

	private static RequestPostProcessor as(String role) {
		return jwt().jwt(token -> token.subject(USER_ID.toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	private static EventResponse created() {
		Instant at = Instant.parse("2026-11-09T10:00:00Z");
		return new EventResponse(EVENT_ID, USER_ID, EventType.GAME_NIGHT, "Cuma Oyun Gecesi", "Birlikte oynuyoruz.",
				Instant.parse("2099-12-05T18:00:00Z"), Instant.parse("2099-12-05T21:00:00Z"), EventStatus.PLANNED, at,
				at);
	}

	private ResultActions create(String role, String json) throws Exception {
		return mockMvc.perform(post(URL).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void adminCreatesAnEvent() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(created());

		create("ADMIN", VALID)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
				.andExpect(jsonPath("$.organizerId").value(USER_ID.toString()))
				.andExpect(jsonPath("$.type").value("GAME_NIGHT"))
				.andExpect(jsonPath("$.title").value("Cuma Oyun Gecesi"))
				.andExpect(jsonPath("$.status").value("PLANNED"))
				.andExpect(jsonPath("$.startsAt").value("2099-12-05T18:00:00Z"))
				.andExpect(jsonPath("$.endsAt").value("2099-12-05T21:00:00Z"));

		ArgumentCaptor<CreateEventRequest> captured = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(eventService).createEvent(captured.capture());
		assertThat(captured.getValue().type()).isEqualTo(EventType.GAME_NIGHT);
		assertThat(captured.getValue().startsAt()).isEqualTo(Instant.parse("2099-12-05T18:00:00Z"));
	}

	@Test
	void moderatorCreatesAnEvent() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(created());

		create("MODERATOR", VALID).andExpect(status().isCreated());
	}

	@Test
	void anonymousRequestIsUnauthorized() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(eventService);
	}

	@Test
	void registeredPlayerCreatesAnEvent() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(created());

		create("PLAYER", VALID).andExpect(status().isCreated());
	}

	@Test
	void rolesOutsideTheCreatorSetAreForbidden() throws Exception {
		// PLAYER Auth'ta geri alınamayan temel roldür; bu roller PLAYER olmadan gelirse yetki vermez.
		for (String role : new String[] { "CONTENT_CREATOR", "GAME_DEVELOPER", "UNKNOWN" }) {
			create(role, VALID)
					.andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		verifyNoInteractions(eventService);
	}

	@Test
	void serviceLevelForbiddenIsMappedTo403() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class))).thenThrow(new EventCreationForbiddenException());

		create("ADMIN", VALID)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void invalidScheduleIsMappedTo400WithAReason() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class)))
				.thenThrow(new InvalidEventScheduleException(Reason.STARTS_IN_THE_PAST));

		create("ADMIN", VALID)
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.reason").value("STARTS_IN_THE_PAST"));
	}

	@Test
	void unexpectedFailuresDoNotLeakDetails() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class)))
				.thenThrow(new IllegalStateException("jdbc:postgresql://secret-host/event_db"));

		create("ADMIN", VALID)
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
	}

	@Test
	void invalidBodiesAreRejected() throws Exception {
		String window = "\"startsAt\":\"2099-12-05T18:00:00Z\",\"endsAt\":\"2099-12-05T21:00:00Z\"";
		String[] invalid = {
				"{}",
				// zorunlu alanlar
				"{\"title\":\"T\",\"description\":\"D\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"description\":\"D\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"D\",\"endsAt\":\"2099-12-05T21:00:00Z\"}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"D\",\"startsAt\":\"2099-12-05T18:00:00Z\"}",
				// boş / yalnız boşluk
				"{\"type\":\"GAME_NIGHT\",\"title\":\"   \",\"description\":\"D\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"   \"," + window + "}",
				// uzunluk sınırları
				"{\"type\":\"GAME_NIGHT\",\"title\":\"" + "a".repeat(121) + "\",\"description\":\"D\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"" + "a".repeat(4001) + "\"," + window + "}",
				// kontrol karakterleri
				"{\"type\":\"GAME_NIGHT\",\"title\":\"a\\u0007b\",\"description\":\"D\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"a\\nb\",\"description\":\"D\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"a\\u0000b\"," + window + "}",
				// geçersiz tür / tarih
				"{\"type\":\"OYLAMA\",\"title\":\"T\",\"description\":\"D\"," + window + "}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"D\",\"startsAt\":\"yarin\",\"endsAt\":\"2099-12-05T21:00:00Z\"}",
				// istemcinin belirleyemeyeceği alanlar (mass assignment)
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"D\"," + window + ",\"organizerId\":\"" + USER_ID + "\"}",
				"{\"type\":\"GAME_NIGHT\",\"title\":\"T\",\"description\":\"D\"," + window + ",\"status\":\"ACTIVE\"}",
				"not json" };
		for (String json : invalid) {
			create("ADMIN", json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(eventService);
	}

	@Test
	void descriptionMayContainLineBreaks() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(created());

		create("ADMIN", """
				{"type":"COMMUNITY","title":"T","description":"Satır 1\\nSatır 2\\tsekme",
				 "startsAt":"2099-12-05T18:00:00Z","endsAt":"2099-12-05T21:00:00Z"}
				""").andExpect(status().isCreated());
	}

	@Test
	void everyDocumentedEventTypeIsAccepted() throws Exception {
		when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(created());

		for (EventType type : EventType.values()) {
			create("ADMIN", VALID.replace("GAME_NIGHT", type.name())).andExpect(status().isCreated());
		}
	}
}
