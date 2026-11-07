package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import com.ltz.community.api.dto.request.GameContentListRequest;
import com.ltz.community.api.dto.response.GameContentDetailResponse;
import com.ltz.community.api.dto.response.GameContentPageResponse;
import com.ltz.community.api.dto.response.GameContentSummaryResponse;
import com.ltz.community.application.service.GameContentService;
import com.ltz.community.domain.enums.GameContentType;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(GameContentController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class GameContentControllerTest {

	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID AUTHOR_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final Instant AT = Instant.parse("2026-11-05T10:00:00Z");
	private static final String LIST_URL = "/api/v1/community/games/" + GAME_ID + "/content";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GameContentService gameContentService;

	private static GameContentPageResponse page(GameContentType type, long total) {
		GameContentSummaryResponse item = new GameContentSummaryResponse(CONTENT_ID, GAME_ID, GameContentType.GUIDES,
				"Başlık", "özet", AUTHOR_ID, AT, AT, LIST_URL + "/" + CONTENT_ID);
		return new GameContentPageResponse(GAME_ID, type, List.of(item), 0, 20, total);
	}

	@Test
	void guestsReadTheListWithTheAgreedShape() throws Exception {
		when(gameContentService.list(eq(GAME_ID), any(GameContentListRequest.class))).thenReturn(page(null, 1));

		mockMvc.perform(get(LIST_URL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
				.andExpect(jsonPath("$.type").doesNotExist())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(CONTENT_ID.toString()))
				.andExpect(jsonPath("$.items[0].gameId").value(GAME_ID.toString()))
				.andExpect(jsonPath("$.items[0].type").value("GUIDES"))
				.andExpect(jsonPath("$.items[0].title").value("Başlık"))
				.andExpect(jsonPath("$.items[0].excerpt").value("özet"))
				.andExpect(jsonPath("$.items[0].authorId").value(AUTHOR_ID.toString()))
				.andExpect(jsonPath("$.items[0].publishedAt").value("2026-11-05T10:00:00Z"))
				.andExpect(jsonPath("$.items[0].updatedAt").value("2026-11-05T10:00:00Z"))
				.andExpect(jsonPath("$.items[0].detailPath").value(LIST_URL + "/" + CONTENT_ID))
				.andExpect(jsonPath("$.items[0].body").doesNotExist());
	}

	@Test
	void passesTypePageAndSizeToTheService() throws Exception {
		when(gameContentService.list(eq(GAME_ID), any(GameContentListRequest.class)))
				.thenReturn(page(GameContentType.EASTER_EGGS, 1));

		mockMvc.perform(get(LIST_URL).param("type", "EASTER_EGGS").param("page", "3").param("size", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("EASTER_EGGS"));

		ArgumentCaptor<GameContentListRequest> captured = ArgumentCaptor.forClass(GameContentListRequest.class);
		verify(gameContentService).list(eq(GAME_ID), captured.capture());
		assertThat(captured.getValue().type()).isEqualTo(GameContentType.EASTER_EGGS);
		assertThat(captured.getValue().pageOrDefault()).isEqualTo(3);
		assertThat(captured.getValue().sizeOrDefault()).isEqualTo(50);
	}

	@Test
	void defaultsToFirstPageOfTwentyWhenNotGiven() throws Exception {
		when(gameContentService.list(eq(GAME_ID), any(GameContentListRequest.class))).thenReturn(page(null, 0));

		mockMvc.perform(get(LIST_URL)).andExpect(status().isOk());

		ArgumentCaptor<GameContentListRequest> captured = ArgumentCaptor.forClass(GameContentListRequest.class);
		verify(gameContentService).list(eq(GAME_ID), captured.capture());
		assertThat(captured.getValue().pageOrDefault()).isZero();
		assertThat(captured.getValue().sizeOrDefault()).isEqualTo(20);
	}

	@Test
	void invalidQueryParametersAreBadRequestsAndNeverReachTheService() throws Exception {
		for (String[] bad : new String[][] { { "size", "0" }, { "size", "51" }, { "size", "abc" }, { "page", "-1" },
				{ "page", "10001" }, { "page", "x" }, { "type", "guides" }, { "type", "NEWS" }, { "type", "1" } }) {
			mockMvc.perform(get(LIST_URL).param(bad[0], bad[1]))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		mockMvc.perform(get("/api/v1/community/games/not-a-uuid/content")).andExpect(status().isBadRequest());

		verifyNoInteractions(gameContentService);
	}

	@Test
	void unknownPageBeyondTheDataIsAnOrdinaryEmptySuccess() throws Exception {
		when(gameContentService.list(eq(GAME_ID), any(GameContentListRequest.class)))
				.thenReturn(new GameContentPageResponse(GAME_ID, null, List.of(), 9, 20, 0));

		mockMvc.perform(get(LIST_URL).param("page", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void guestsReadTheDetail() throws Exception {
		when(gameContentService.detail(GAME_ID, CONTENT_ID)).thenReturn(new GameContentDetailResponse(CONTENT_ID, GAME_ID,
				GameContentType.EXPERIENCES, "Deneyim", "gövde", AUTHOR_ID, AT, AT, 3, 2, false, List.of()));

		mockMvc.perform(get(LIST_URL + "/" + CONTENT_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("EXPERIENCES"))
				.andExpect(jsonPath("$.title").value("Deneyim"))
				.andExpect(jsonPath("$.body").value("gövde"))
				.andExpect(jsonPath("$.publishedAt").value("2026-11-05T10:00:00Z"))
				.andExpect(jsonPath("$.likeCount").value(3))
				.andExpect(jsonPath("$.commentCount").value(2))
				.andExpect(jsonPath("$.likedByMe").value(false))
				.andExpect(jsonPath("$.images").isEmpty());
	}

	@Test
	void invisibleOrMissingDetailIsNotFound() throws Exception {
		when(gameContentService.detail(GAME_ID, CONTENT_ID)).thenThrow(new PostNotFoundException());

		mockMvc.perform(get(LIST_URL + "/" + CONTENT_ID))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(get(LIST_URL + "/not-a-uuid")).andExpect(status().isBadRequest());
	}

	@Test
	void temporaryDatabaseOutagesAreServiceUnavailableNeverAnEmptyListOrAServerError() throws Exception {
		when(gameContentService.list(eq(GAME_ID), any(GameContentListRequest.class)))
				.thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://secret-host/db refused"))
				.thenThrow(new CannotCreateTransactionException("pool exhausted"))
				.thenThrow(new QueryTimeoutException("timeout"));

		for (int attempt = 0; attempt < 3; attempt++) {
			mockMvc.perform(get(LIST_URL))
					.andExpect(status().isServiceUnavailable())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.status").value(503))
					.andExpect(jsonPath("$.detail").value("The service is temporarily unavailable. Try again later."))
					.andExpect(jsonPath("$.items").doesNotExist());
		}
	}
}
