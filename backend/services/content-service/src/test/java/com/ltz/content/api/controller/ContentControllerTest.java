package com.ltz.content.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.service.ContentService;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.ContentAccessDeniedException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.DraftLimitExceededException;
import com.ltz.content.domain.exception.DuplicateContentException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.OfficialAnnouncementForbiddenException;
import com.ltz.content.domain.exception.TooManyContentsException;
import com.ltz.content.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.content.infrastructure.security.SecurityConfig;

@WebMvcTest(ContentController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class ContentControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/content/contents";
	private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentService contentService;

	private final RequestPostProcessor owner = jwt().jwt(token -> token.subject(USER_ID.toString()));

	private static ContentResponse response() {
		return new ContentResponse(CONTENT_ID, USER_ID, ContentType.NEWS, ContentStatus.DRAFT, "tr", "Yeni Güncelleme",
				"Kısa özet", null, List.of(), NOW, NOW);
	}

	private org.springframework.test.web.servlet.ResultActions create(String json) throws Exception {
		return mockMvc.perform(post(URL).with(owner).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private org.springframework.test.web.servlet.ResultActions update(String json) throws Exception {
		return mockMvc.perform(patch(URL + "/" + CONTENT_ID).with(owner).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	// ------------------------------------------------------------------ oluşturma

	@Test
	void createReturns201WithTheDraftAndPassesTheRequestToTheService() throws Exception {
		when(contentService.createDraft(any())).thenReturn(response());

		create("{\"type\":\"NEWS\",\"locale\":\"tr\",\"title\":\"Yeni Güncelleme\",\"summary\":\"Kısa özet\",\"body\":\"Gövde\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()))
				.andExpect(jsonPath("$.authorId").value(USER_ID.toString()))
				.andExpect(jsonPath("$.type").value("NEWS"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.locale").value("tr"))
				.andExpect(jsonPath("$.title").value("Yeni Güncelleme"))
				.andExpect(jsonPath("$.images").isEmpty());

		ArgumentCaptor<CreateContentRequest> sent = ArgumentCaptor.forClass(CreateContentRequest.class);
		verify(contentService).createDraft(sent.capture());
		assertThat(sent.getValue().type()).isEqualTo(ContentType.NEWS);
		assertThat(sent.getValue().title()).isEqualTo("Yeni Güncelleme");
	}

	@Test
	void onlyTypeAndTitleAreRequiredForADraft() throws Exception {
		when(contentService.createDraft(any())).thenReturn(response());

		create("{\"type\":\"USER_CONTENT\",\"title\":\"Rehber\"}").andExpect(status().isCreated());
	}

	@Test
	void invalidCreateRequestsAreRejectedBeforeTheService() throws Exception {
		String longTitle = "a".repeat(151);
		String longSummary = "a".repeat(501);
		String longBody = "a".repeat(20_001);
		String[] invalid = {
				"{\"title\":\"Başlık\"}",
				"{\"type\":\"NEWS\"}",
				"{\"type\":\"NEWS\",\"title\":\"   \"}",
				"{\"type\":\"NEWS\",\"title\":\"\"}",
				"{\"type\":\"news\",\"title\":\"Başlık\"}",
				"{\"type\":\"POEM\",\"title\":\"Başlık\"}",
				"{\"type\":1,\"title\":\"Başlık\"}",
				"{\"type\":\"NEWS\",\"title\":\"" + longTitle + "\"}",
				"{\"type\":\"NEWS\",\"title\":\"iki\\nsatır\"}",
				"{\"type\":\"NEWS\",\"title\":\"kontrol\\u0001karakteri\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"summary\":\"" + longSummary + "\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"summary\":\"kontrol\\u0000karakteri\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"body\":\"" + longBody + "\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"body\":\"kontrol\\u0007karakteri\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"locale\":\"de\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"locale\":\"TR\"}",
				"{\"type\":\"NEWS\",\"title\":",
				"" };
		for (String json : invalid) {
			create(json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(contentService);
	}

	@Test
	void validationErrorsListTheFieldsWithoutEchoingTheInput() throws Exception {
		create("{\"type\":\"NEWS\",\"title\":\"   \"}")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.violations[0].field").value("title"))
				.andExpect(jsonPath("$.detail").value("Request validation failed."));
	}

	@Test
	void theClientCannotSetTheAuthorStatusOrPublicationFields() throws Exception {
		String[] attempts = {
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"authorId\":\"" + UUID.randomUUID() + "\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"status\":\"PUBLISHED\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"publishedAt\":\"2026-01-01T00:00:00Z\"}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"featured\":true}",
				"{\"type\":\"NEWS\",\"title\":\"Başlık\",\"role\":\"ADMIN\"}" };
		for (String json : attempts) {
			create(json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(contentService);
	}

	@Test
	void createErrorsAreMappedToProblemDetails() throws Exception {
		String json = "{\"type\":\"ANNOUNCEMENT\",\"title\":\"Duyuru\"}";

		doThrow(new OfficialAnnouncementForbiddenException()).when(contentService).createDraft(any());
		create(json).andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Only administrators can create official announcements."));

		doThrow(new DuplicateContentException()).when(contentService).createDraft(any());
		create(json).andExpect(status().isConflict());

		doThrow(new DraftLimitExceededException(20)).when(contentService).createDraft(any());
		create(json).andExpect(status().isConflict());

		doThrow(new TooManyContentsException()).when(contentService).createDraft(any());
		create(json).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "3600"));
	}

	// ------------------------------------------------------------------ okuma

	@Test
	void getReturnsTheContent() throws Exception {
		when(contentService.getContent(CONTENT_ID)).thenReturn(response());

		mockMvc.perform(get(URL + "/" + CONTENT_ID).with(owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Yeni Güncelleme"))
				.andExpect(jsonPath("$.summary").value("Kısa özet"));
	}

	@Test
	void getOfAMissingOrForeignDraftIs404() throws Exception {
		when(contentService.getContent(CONTENT_ID)).thenThrow(new ContentNotFoundException());

		mockMvc.perform(get(URL + "/" + CONTENT_ID).with(owner)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Content not found."));
	}

	@Test
	void aMalformedIdIsABadRequest() throws Exception {
		mockMvc.perform(get(URL + "/not-a-uuid").with(owner)).andExpect(status().isBadRequest());
		mockMvc.perform(delete(URL + "/not-a-uuid").with(owner)).andExpect(status().isBadRequest());
		verifyNoInteractions(contentService);
	}

	// ------------------------------------------------------------------ düzenleme

	@Test
	void patchUpdatesOnlyTheGivenFields() throws Exception {
		when(contentService.updateDraft(eq(CONTENT_ID), any())).thenReturn(response());

		update("{\"summary\":\"Kısa özet\"}").andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("Kısa özet"));
	}

	@Test
	void theClientCannotChangeAuthorTypeLocaleOrStatusWhenEditing() throws Exception {
		String[] attempts = {
				"{\"title\":\"Yeni\",\"authorId\":\"" + UUID.randomUUID() + "\"}",
				"{\"title\":\"Yeni\",\"type\":\"ANNOUNCEMENT\"}",
				"{\"title\":\"Yeni\",\"status\":\"PUBLISHED\"}",
				"{\"title\":\"Yeni\",\"locale\":\"en\"}",
				"{\"title\":\"Yeni\",\"featured\":true}" };
		for (String json : attempts) {
			update(json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(contentService);
	}

	@Test
	void invalidPatchValuesAreRejected() throws Exception {
		update("{\"title\":\"" + "a".repeat(151) + "\"}").andExpect(status().isBadRequest());
		update("{\"title\":\"iki\\nsatır\"}").andExpect(status().isBadRequest());
		update("{\"summary\":\"" + "a".repeat(501) + "\"}").andExpect(status().isBadRequest());
		update("{\"body\":\"kontrol\\u0001karakteri\"}").andExpect(status().isBadRequest());
		verifyNoInteractions(contentService);
	}

	@Test
	void patchErrorsAreMappedToProblemDetails() throws Exception {
		when(contentService.updateDraft(eq(CONTENT_ID), any())).thenThrow(new InvalidContentRequestException("At least one of title, summary or body is required."));
		update("{}").andExpect(status().isBadRequest());

		when(contentService.updateDraft(eq(CONTENT_ID), any())).thenThrow(new ContentNotFoundException());
		update("{\"title\":\"Yeni\"}").andExpect(status().isNotFound());

		when(contentService.updateDraft(eq(CONTENT_ID), any())).thenThrow(new ContentAccessDeniedException());
		update("{\"title\":\"Yeni\"}").andExpect(status().isForbidden());

		when(contentService.updateDraft(eq(CONTENT_ID), any())).thenThrow(new ContentNotEditableException());
		update("{\"title\":\"Yeni\"}").andExpect(status().isConflict());

		when(contentService.updateDraft(eq(CONTENT_ID), any())).thenThrow(new DuplicateContentException());
		update("{\"title\":\"Yeni\"}").andExpect(status().isConflict());

		when(contentService.updateDraft(eq(CONTENT_ID), any()))
				.thenThrow(new ObjectOptimisticLockingFailureException(Content.class, CONTENT_ID));
		update("{\"title\":\"Yeni\"}").andExpect(status().isConflict());
	}

	// ------------------------------------------------------------------ silme

	@Test
	void deleteReturns204() throws Exception {
		mockMvc.perform(delete(URL + "/" + CONTENT_ID).with(owner)).andExpect(status().isNoContent());

		verify(contentService).deleteContent(CONTENT_ID);
	}

	@Test
	void deleteErrorsAreMapped() throws Exception {
		doThrow(new ContentNotFoundException()).when(contentService).deleteContent(CONTENT_ID);
		mockMvc.perform(delete(URL + "/" + CONTENT_ID).with(owner)).andExpect(status().isNotFound());

		doThrow(new ContentNotEditableException()).when(contentService).deleteContent(CONTENT_ID);
		mockMvc.perform(delete(URL + "/" + CONTENT_ID).with(owner)).andExpect(status().isConflict());
	}

	// ------------------------------------------------------------------ genel

	@Test
	void unexpectedFailuresNeverLeakInternalDetails() throws Exception {
		when(contentService.getContent(CONTENT_ID)).thenThrow(new IllegalStateException("jdbc:postgresql://db password=secret"));

		mockMvc.perform(get(URL + "/" + CONTENT_ID).with(owner))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}

	@Test
	void requestsWithoutATokenAreUnauthorizedAndNeverReachTheService() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"NEWS\",\"title\":\"Başlık\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(get(URL + "/" + CONTENT_ID)).andExpect(status().isUnauthorized());
		verifyNoInteractions(contentService);
	}
}
