package com.ltz.content.api.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ltz.content.api.dto.response.ModerationActionResponse;
import com.ltz.content.application.service.ContentModerationService;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.enums.ModerationSource;
import com.ltz.content.domain.enums.ModeratorRole;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.ModeratorRequiredException;
import com.ltz.content.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.content.infrastructure.security.SecurityConfig;

/**
 * Moderasyon geçmişi (salt okunur). Yayından kaldırma/geri alma için doğrudan yazma ucu YOKTUR: karar yalnız admin-service
 * olayıyla ({@code POST /internal/events}) uygulanır; bu yüzden geçmiş yolunda yazma yöntemleri 405'tir. Rol denetimi servis
 * katmanında test edilir.
 */
@WebMvcTest(ContentModerationController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class ContentModerationControllerTest {

	private static final UUID MODERATOR = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/content/moderation/contents/" + CONTENT_ID + "/actions";
	private static final String BODY = "{\"action\":\"REMOVE\",\"reason\":\"Spam\"}";
	private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentModerationService moderationService;

	private final RequestPostProcessor moderator = jwt().jwt(token -> token.subject(MODERATOR.toString()));

	private static ModerationActionResponse response() {
		return new ModerationActionResponse(UUID.randomUUID(), CONTENT_ID, ModerationActionType.REMOVE, ContentStatus.REMOVED,
				MODERATOR, ModeratorRole.MODERATOR, ModerationSource.ADMIN_SERVICE, "Spam", null, NOW);
	}

	@Test
	void historyReturnsTheRecords() throws Exception {
		when(moderationService.history(CONTENT_ID)).thenReturn(List.of(response()));

		mockMvc.perform(get(URL).with(moderator)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].action").value("REMOVE"))
				.andExpect(jsonPath("$[0].resultingStatus").value("REMOVED"))
				.andExpect(jsonPath("$[0].actorRole").value("MODERATOR"))
				.andExpect(jsonPath("$[0].source").value("ADMIN_SERVICE"))
				.andExpect(jsonPath("$[0].reason").value("Spam"));
	}

	@Test
	void historyErrorsAreMappedToProblemDetails() throws Exception {
		doThrow(new ModeratorRequiredException()).when(moderationService).history(CONTENT_ID);
		mockMvc.perform(get(URL).with(moderator)).andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Only moderators or administrators can perform this action."));

		doThrow(new ContentNotFoundException()).when(moderationService).history(CONTENT_ID);
		mockMvc.perform(get(URL).with(moderator)).andExpect(status().isNotFound());
	}

	@Test
	void aMalformedContentIdIsABadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/content/moderation/contents/not-a-uuid/actions").with(moderator))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(moderationService);
	}

	@Test
	void thereIsNoDirectWriteEndpointForModerationDecisions() throws Exception {
		mockMvc.perform(post(URL).with(moderator).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(put(URL).with(moderator).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(patch(URL).with(moderator).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(delete(URL).with(moderator)).andExpect(status().isMethodNotAllowed());
		verifyNoInteractions(moderationService);
	}

	@Test
	void requestsWithoutATokenAreUnauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
		verifyNoInteractions(moderationService);
	}
}
