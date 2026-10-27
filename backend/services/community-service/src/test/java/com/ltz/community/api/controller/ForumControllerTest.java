package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ltz.community.api.dto.request.CreateForumRequest;
import com.ltz.community.api.dto.request.ForumListRequest;
import com.ltz.community.api.dto.response.ForumPageResponse;
import com.ltz.community.api.dto.response.ForumResponse;
import com.ltz.community.application.service.ForumService;
import com.ltz.community.domain.enums.ForumStatus;
import com.ltz.community.domain.exception.ForumGameMismatchException;
import com.ltz.community.domain.exception.ForumNotFoundException;
import com.ltz.community.domain.exception.ForumSlugTakenException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.ModeratorRequiredException;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(ForumController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class ForumControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID FORUM_ID = UUID.fromString("0198a3c2-dddd-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/community/forums";
	private static final String VALID = "{\"slug\":\"elden-ring\",\"title\":\"Elden Ring\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ForumService forumService;

	private static ForumResponse forum() {
		Instant at = Instant.parse("2026-10-21T10:00:00Z");
		return new ForumResponse(FORUM_ID, GAME_ID, "elden-ring", "Elden Ring", "Açıklama", ForumStatus.VISIBLE, at, at);
	}

	@Test
	void moderatorCreatesAForum() throws Exception {
		when(forumService.createForum(any(CreateForumRequest.class))).thenReturn(forum());

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slug\":\"elden-ring\",\"title\":\"Elden Ring\",\"description\":\"Açıklama\",\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(FORUM_ID.toString()))
				.andExpect(jsonPath("$.slug").value("elden-ring"))
				.andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
				.andExpect(jsonPath("$.status").value("VISIBLE"));

		ArgumentCaptor<CreateForumRequest> captured = ArgumentCaptor.forClass(CreateForumRequest.class);
		verify(forumService).createForum(captured.capture());
		assertThat(captured.getValue().gameId()).isEqualTo(GAME_ID);
	}

	@Test
	void writeEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(patch(URL + "/" + FORUM_ID).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(forumService);
	}

	@Test
	void invalidForumBodiesAreRejected() throws Exception {
		String[] invalid = { "{}", "{\"slug\":\"ab\",\"title\":\"x\"}", "{\"slug\":\"Büyük Harf\",\"title\":\"x\"}",
				"{\"slug\":\"cift--tire\",\"title\":\"x\"}", "{\"slug\":\"-bas\",\"title\":\"x\"}",
				"{\"slug\":\"gecerli-slug\",\"title\":\"   \"}", "{\"slug\":\"gecerli-slug\",\"title\":\"" + "a".repeat(121) + "\"}",
				"{\"slug\":\"gecerli-slug\",\"title\":\"x\",\"description\":\"" + "a".repeat(1001) + "\"}",
				"{\"slug\":\"gecerli-slug\",\"title\":\"a\\u0007b\"}", "{\"slug\":\"gecerli-slug\",\"title\":\"x\",\"gameId\":\"degil\"}",
				"{\"slug\":\"gecerli-slug\",\"title\":\"x\",\"extra\":1}" };
		for (String json : invalid) {
			mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
					.contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(forumService);
	}

	@Test
	void anonymousVisitorsCanListAndReadForums() throws Exception {
		when(forumService.listForums(any(ForumListRequest.class))).thenReturn(new ForumPageResponse(List.of(forum()), "sonraki"));
		when(forumService.getForum(FORUM_ID)).thenReturn(forum());

		mockMvc.perform(get(URL).param("gameId", GAME_ID.toString()).param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].slug").value("elden-ring"))
				.andExpect(jsonPath("$.nextCursor").value("sonraki"));
		mockMvc.perform(get(URL + "/" + FORUM_ID)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Elden Ring"));

		ArgumentCaptor<ForumListRequest> captured = ArgumentCaptor.forClass(ForumListRequest.class);
		verify(forumService).listForums(captured.capture());
		assertThat(captured.getValue().gameId()).isEqualTo(GAME_ID);
		assertThat(captured.getValue().limit()).isEqualTo(5);
	}

	@Test
	void invalidListParametersAndIdentifiersAreRejected() throws Exception {
		mockMvc.perform(get(URL).param("limit", "0")).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("limit", "51")).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("gameId", "degil")).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("cursor", "x".repeat(201))).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL + "/not-a-uuid")).andExpect(status().isBadRequest());
		verifyNoInteractions(forumService);
	}

	@Test
	void moderatorUpdatesAForum() throws Exception {
		when(forumService.updateForum(eq(FORUM_ID), any())).thenReturn(forum());

		mockMvc.perform(patch(URL + "/" + FORUM_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Yeni\",\"status\":\"HIDDEN\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(patch(URL + "/" + FORUM_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(patch(URL + "/" + FORUM_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(patch(URL + "/" + FORUM_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"degistir\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void domainErrorsAreMappedToProblemDetails() throws Exception {
		when(forumService.createForum(any())).thenThrow(new ModeratorRequiredException())
				.thenThrow(new ForumSlugTakenException()).thenThrow(new GameNotAvailableException());
		when(forumService.getForum(FORUM_ID)).thenThrow(new ForumNotFoundException());
		when(forumService.updateForum(eq(FORUM_ID), any())).thenThrow(new ForumGameMismatchException("çelişki"));

		for (int expected : new int[] { 403, 409, 422 }) {
			mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
					.contentType(MediaType.APPLICATION_JSON).content(VALID))
					.andExpect(status().is(expected))
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		mockMvc.perform(get(URL + "/" + FORUM_ID)).andExpect(status().isNotFound());
		mockMvc.perform(patch(URL + "/" + FORUM_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
				.andExpect(status().isUnprocessableContent());
	}
}
