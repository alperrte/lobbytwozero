package com.ltz.content.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ltz.content.api.dto.request.ContentFeedRequest;
import com.ltz.content.api.dto.response.CategoryResponse;
import com.ltz.content.api.dto.response.ContentCardResponse;
import com.ltz.content.api.dto.response.ContentPageResponse;
import com.ltz.content.application.service.ContentFeedService;
import com.ltz.content.application.service.ContentService;
import com.ltz.content.domain.enums.CategoryKind;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.InvalidContentQueryException;
import com.ltz.content.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.content.infrastructure.security.SecurityConfig;

/** Herkese açık akış ve kategori uçları (CONT-01/03/12/13): anonim okuma, sorgu doğrulaması, hata eşleme. */
@WebMvcTest({ ContentController.class, CategoryController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class ContentFeedControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID AUTHOR_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/content/contents";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentFeedService feedService;

	@MockitoBean
	private ContentService contentService;

	private static ContentPageResponse page() {
		ContentCardResponse card = new ContentCardResponse(CONTENT_ID, AUTHOR_ID, ContentType.NEWS, "tr", "Yeni Güncelleme",
				"Kısa özet", GAME_ID, List.of("PC", "UPDATE"), "/api/v1/content/contents/" + CONTENT_ID + "/images/x", true,
				Instant.parse("2026-11-07T09:00:00Z"));
		return new ContentPageResponse(List.of(card), "abc_DEF-123");
	}

	@Test
	void anonymousVisitorsCanReadTheFeed() throws Exception {
		when(feedService.list(any())).thenReturn(page());

		mockMvc.perform(get(URL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(CONTENT_ID.toString()))
				.andExpect(jsonPath("$.items[0].title").value("Yeni Güncelleme"))
				.andExpect(jsonPath("$.items[0].categories[1]").value("UPDATE"))
				.andExpect(jsonPath("$.items[0].featured").value(true))
				.andExpect(jsonPath("$.items[0].gameId").value(GAME_ID.toString()))
				.andExpect(jsonPath("$.nextCursor").value("abc_DEF-123"));
	}

	@Test
	void queryParametersAreBoundToTheFeedRequest() throws Exception {
		when(feedService.list(any())).thenReturn(new ContentPageResponse(List.of(), null));

		mockMvc.perform(get(URL).param("q", "güncelleme").param("category", "PC").param("gameId", GAME_ID.toString())
				.param("type", "ANNOUNCEMENT").param("featured", "true").param("from", "2026-11-01T00:00:00Z")
				.param("to", "2026-11-08T00:00:00Z").param("locale", "en").param("limit", "10").param("cursor", "abc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		ArgumentCaptor<ContentFeedRequest> sent = ArgumentCaptor.forClass(ContentFeedRequest.class);
		verify(feedService).list(sent.capture());
		ContentFeedRequest request = sent.getValue();
		assertThat(request.q()).isEqualTo("güncelleme");
		assertThat(request.category()).isEqualTo("PC");
		assertThat(request.gameId()).isEqualTo(GAME_ID);
		assertThat(request.type()).isEqualTo(ContentType.ANNOUNCEMENT);
		assertThat(request.featured()).isTrue();
		assertThat(request.from()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(request.to()).isEqualTo(Instant.parse("2026-11-08T00:00:00Z"));
		assertThat(request.locale()).isEqualTo("en");
		assertThat(request.limit()).isEqualTo(10);
		assertThat(request.cursor()).isEqualTo("abc");
	}

	@Test
	void invalidQueryParametersAreRejectedBeforeTheService() throws Exception {
		String[][] invalid = {
				{ "limit", "0" }, { "limit", "51" }, { "limit", "-1" }, { "limit", "abc" },
				{ "locale", "de" }, { "locale", "TR" },
				{ "category", "pc" }, { "category", "PC;DROP" }, { "category", "A".repeat(33) },
				{ "q", "a".repeat(101) }, { "q", "kontrol\u0001karakteri" },
				{ "type", "POEM" }, { "gameId", "not-a-uuid" }, { "featured", "belki" },
				{ "from", "yarın" }, { "cursor", "a".repeat(201) } };
		for (String[] parameter : invalid) {
			mockMvc.perform(get(URL).param(parameter[0], parameter[1]))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		verifyNoInteractions(feedService);
	}

	@Test
	void serviceQueryErrorsAreBadRequests() throws Exception {
		when(feedService.list(any())).thenThrow(new InvalidContentQueryException("The cursor is not valid."));

		mockMvc.perform(get(URL).param("cursor", "bozuk"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("The cursor is not valid."));
	}

	@Test
	void aBrokenBearerTokenIsStillRejectedOnPublicEndpoints() throws Exception {
		mockMvc.perform(get(URL).header("Authorization", "Bearer bozuk.token.degeri"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(feedService);
	}

	@Test
	void anonymousVisitorsCanListTheFixedCategories() throws Exception {
		when(feedService.listCategories()).thenReturn(List.of(
				new CategoryResponse("PC", CategoryKind.PLATFORM, "PC", "PC"),
				new CategoryResponse("UPDATE", CategoryKind.TOPIC, "Güncelleme", "Update")));

		mockMvc.perform(get("/api/v1/content/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].code").value("PC"))
				.andExpect(jsonPath("$[0].kind").value("PLATFORM"))
				.andExpect(jsonPath("$[1].nameTr").value("Güncelleme"))
				.andExpect(jsonPath("$[1].nameEn").value("Update"));
	}
}
