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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ltz.content.api.dto.request.ReplaceCategoriesRequest;
import com.ltz.content.api.dto.request.ReplaceSourcesRequest;
import com.ltz.content.api.dto.request.TranslationRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.api.dto.response.ContentSourceResponse;
import com.ltz.content.application.service.ContentMetadataService;
import com.ltz.content.application.service.ContentPublishingService;
import com.ltz.content.application.service.ContentTranslationService;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.AdministratorRequiredException;
import com.ltz.content.domain.exception.ContentAlreadyPublishedException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.ContentNotPublishedException;
import com.ltz.content.domain.exception.ContentTranslationNotFoundException;
import com.ltz.content.domain.exception.GameCatalogUnavailableException;
import com.ltz.content.domain.exception.GameNotAvailableException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.PublishRequirementsNotMetException;
import com.ltz.content.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.content.infrastructure.security.SecurityConfig;

/** Yayınlama, öne çıkarma, çeviri ve üst veri uçları: doğrulama, hata eşleme ve oturum zorunluluğu. */
@WebMvcTest({ ContentPublicationController.class, ContentTranslationController.class, ContentMetadataController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class ContentWorkflowControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String BASE = "/api/v1/content/contents/" + CONTENT_ID;
	private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentPublishingService publishingService;

	@MockitoBean
	private ContentTranslationService translationService;

	@MockitoBean
	private ContentMetadataService metadataService;

	private final RequestPostProcessor owner = jwt().jwt(token -> token.subject(USER_ID.toString()));

	private static ContentResponse response() {
		return new ContentResponse(CONTENT_ID, USER_ID, ContentType.NEWS, ContentStatus.PUBLISHED, "tr", "Yeni Güncelleme",
				"Kısa özet", "Gövde", List.of(), NOW, NOW, GAME_ID, List.of("PC"),
				List.of(new ContentSourceResponse("IGN", "https://www.ign.com/a", LocalDate.parse("2026-11-01"))), true, NOW,
				List.of("tr", "en"));
	}

	private ResultActions putJson(String path, String json) throws Exception {
		return mockMvc.perform(put(BASE + path).with(owner).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	// ------------------------------------------------------------------ yayınlama / öne çıkarma

	@Test
	void publishReturnsThePublishedContent() throws Exception {
		when(publishingService.publish(CONTENT_ID)).thenReturn(response());

		mockMvc.perform(post(BASE + "/publish").with(owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.publishedAt").value("2026-11-07T09:00:00Z"))
				.andExpect(jsonPath("$.featured").value(true))
				.andExpect(jsonPath("$.availableLocales[1]").value("en"));
	}

	@Test
	void publishErrorsAreMappedToProblemDetails() throws Exception {
		doThrow(new PublishRequirementsNotMetException(List.of("tr:BODY_TOO_SHORT"))).when(publishingService).publish(CONTENT_ID);
		mockMvc.perform(post(BASE + "/publish").with(owner))
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.issues[0]").value("tr:BODY_TOO_SHORT"));

		doThrow(new ContentAlreadyPublishedException()).when(publishingService).publish(CONTENT_ID);
		mockMvc.perform(post(BASE + "/publish").with(owner)).andExpect(status().isConflict());

		doThrow(new ContentNotFoundException()).when(publishingService).publish(CONTENT_ID);
		mockMvc.perform(post(BASE + "/publish").with(owner)).andExpect(status().isNotFound());
	}

	@Test
	void featureAndUnfeatureAreMappedAndAdminErrorsAre403() throws Exception {
		when(publishingService.feature(CONTENT_ID)).thenReturn(response());
		when(publishingService.unfeature(CONTENT_ID)).thenReturn(response());

		mockMvc.perform(put(BASE + "/featured").with(owner)).andExpect(status().isOk());
		mockMvc.perform(delete(BASE + "/featured").with(owner)).andExpect(status().isOk());

		doThrow(new AdministratorRequiredException()).when(publishingService).feature(CONTENT_ID);
		mockMvc.perform(put(BASE + "/featured").with(owner)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Only administrators can perform this action."));

		doThrow(new ContentNotPublishedException()).when(publishingService).feature(CONTENT_ID);
		mockMvc.perform(put(BASE + "/featured").with(owner)).andExpect(status().isConflict());
	}

	// ------------------------------------------------------------------ çeviri

	@Test
	void putTranslationPassesLocaleAndBodyToTheService() throws Exception {
		when(translationService.putTranslation(eq(CONTENT_ID), eq("en"), any())).thenReturn(response());

		putJson("/translations/en", "{\"title\":\"New update\",\"summary\":\"Short\",\"body\":\"Body\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableLocales[1]").value("en"));

		ArgumentCaptor<TranslationRequest> sent = ArgumentCaptor.forClass(TranslationRequest.class);
		verify(translationService).putTranslation(eq(CONTENT_ID), eq("en"), sent.capture());
		assertThat(sent.getValue().title()).isEqualTo("New update");
		assertThat(sent.getValue().body()).isEqualTo("Body");
	}

	@Test
	void invalidTranslationBodiesAreRejected() throws Exception {
		String[] invalid = { "{}", "{\"title\":\"\"}", "{\"title\":\"   \"}", "{\"title\":\"" + "a".repeat(151) + "\"}",
				"{\"title\":\"iki\\nsatır\"}", "{\"title\":\"Başlık\",\"summary\":\"" + "a".repeat(501) + "\"}",
				"{\"title\":\"Başlık\",\"body\":\"kontrol\\u0001karakteri\"}", "{\"title\":\"Başlık\",\"locale\":\"en\"}",
				"{\"title\":\"Başlık\",\"status\":\"PUBLISHED\"}", "{\"title\":" };
		for (String json : invalid) {
			putJson("/translations/en", json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(translationService);
	}

	@Test
	void translationErrorsAreMappedToProblemDetails() throws Exception {
		doThrow(new InvalidContentRequestException("Unsupported locale.")).when(translationService).putTranslation(eq(CONTENT_ID), eq("de"), any());
		putJson("/translations/de", "{\"title\":\"Titel\"}").andExpect(status().isBadRequest());

		doThrow(new ContentNotEditableException()).when(translationService).putTranslation(eq(CONTENT_ID), eq("en"), any());
		putJson("/translations/en", "{\"title\":\"Title\"}").andExpect(status().isConflict());

		doThrow(new ContentTranslationNotFoundException()).when(translationService).deleteTranslation(CONTENT_ID, "en");
		mockMvc.perform(delete(BASE + "/translations/en").with(owner)).andExpect(status().isNotFound());
	}

	@Test
	void deleteTranslationReturnsTheRemainingContent() throws Exception {
		when(translationService.deleteTranslation(CONTENT_ID, "en")).thenReturn(response());

		mockMvc.perform(delete(BASE + "/translations/en").with(owner)).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ kategoriler

	@Test
	void replaceCategoriesPassesTheCodes() throws Exception {
		when(metadataService.replaceCategories(eq(CONTENT_ID), any())).thenReturn(response());

		putJson("/categories", "{\"categories\":[\"PC\",\"UPDATE\"]}").andExpect(status().isOk())
				.andExpect(jsonPath("$.categories[0]").value("PC"));

		ArgumentCaptor<ReplaceCategoriesRequest> sent = ArgumentCaptor.forClass(ReplaceCategoriesRequest.class);
		verify(metadataService).replaceCategories(eq(CONTENT_ID), sent.capture());
		assertThat(sent.getValue().categories()).containsExactly("PC", "UPDATE");
	}

	@Test
	void invalidCategoryRequestsAreRejected() throws Exception {
		String[] invalid = { "{}", "{\"categories\":null}", "{\"categories\":[\"PC\",\"\"]}", "{\"categories\":[null]}",
				"{\"categories\":[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]}", "{\"categories\":[\"" + "A".repeat(33) + "\"]}",
				"{\"categories\":\"PC\"}" };
		for (String json : invalid) {
			putJson("/categories", json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(metadataService);
	}

	// ------------------------------------------------------------------ kaynaklar

	@Test
	void replaceSourcesPassesTitleUrlAndDate() throws Exception {
		when(metadataService.replaceSources(eq(CONTENT_ID), any())).thenReturn(response());

		putJson("/sources", "{\"sources\":[{\"title\":\"IGN\",\"url\":\"https://www.ign.com/a\",\"date\":\"2026-11-01\"}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sources[0].title").value("IGN"))
				.andExpect(jsonPath("$.sources[0].date").value("2026-11-01"));

		ArgumentCaptor<ReplaceSourcesRequest> sent = ArgumentCaptor.forClass(ReplaceSourcesRequest.class);
		verify(metadataService).replaceSources(eq(CONTENT_ID), sent.capture());
		assertThat(sent.getValue().sources()).singleElement().satisfies(source -> {
			assertThat(source.url()).isEqualTo("https://www.ign.com/a");
			assertThat(source.date()).isEqualTo(LocalDate.parse("2026-11-01"));
		});
	}

	@Test
	void unsafeOrInvalidSourcesAreRejected() throws Exception {
		String[] invalid = {
				"{}", "{\"sources\":null}", "{\"sources\":[null]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"http://www.ign.com/a\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"javascript:alert(1)\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"data:text/html,x\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"https://a b.com\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"\"}]}",
				"{\"sources\":[{\"title\":\"\",\"url\":\"https://www.ign.com/a\"}]}",
				"{\"sources\":[{\"title\":\"IGN\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"https://" + "a".repeat(500) + ".com\"}]}",
				"{\"sources\":[{\"title\":\"" + "a".repeat(151) + "\",\"url\":\"https://www.ign.com/a\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"https://www.ign.com/a\",\"date\":\"2999-01-01\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"https://www.ign.com/a\",\"date\":\"dün\"}]}",
				"{\"sources\":[{\"title\":\"IGN\",\"url\":\"https://a.com\",\"extra\":1}]}",
				"{\"sources\":[" + "{\"title\":\"A\",\"url\":\"https://a.com\"},".repeat(3)
						+ "{\"title\":\"A\",\"url\":\"https://a.com\"}]}" };
		for (String json : invalid) {
			putJson("/sources", json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(metadataService);
	}

	// ------------------------------------------------------------------ oyun

	@Test
	void linkAndUnlinkGame() throws Exception {
		when(metadataService.linkGame(CONTENT_ID, GAME_ID)).thenReturn(response());
		when(metadataService.unlinkGame(CONTENT_ID)).thenReturn(response());

		putJson("/game", "{\"gameId\":\"" + GAME_ID + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.gameId").value(GAME_ID.toString()));
		mockMvc.perform(delete(BASE + "/game").with(owner)).andExpect(status().isOk());
	}

	@Test
	void invalidGameRequestsAreRejected() throws Exception {
		for (String json : new String[] { "{}", "{\"gameId\":null}", "{\"gameId\":\"not-a-uuid\"}", "{\"gameId\":1}" }) {
			putJson("/game", json).andExpect(status().isBadRequest());
		}
		verifyNoInteractions(metadataService);
	}

	@Test
	void gameErrorsAreMappedTo422And503WithoutLeakingTheCause() throws Exception {
		doThrow(new GameNotAvailableException()).when(metadataService).linkGame(CONTENT_ID, GAME_ID);
		putJson("/game", "{\"gameId\":\"" + GAME_ID + "\"}").andExpect(status().isUnprocessableContent());

		doThrow(new GameCatalogUnavailableException(new IllegalStateException("http://game-service:8080 secret")))
				.when(metadataService).linkGame(CONTENT_ID, GAME_ID);
		putJson("/game", "{\"gameId\":\"" + GAME_ID + "\"}").andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The game catalog is temporarily unavailable."))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}

	// ------------------------------------------------------------------ oturum

	@Test
	void everyWriteEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(post(BASE + "/publish")).andExpect(status().isUnauthorized());
		mockMvc.perform(put(BASE + "/featured")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(BASE + "/featured")).andExpect(status().isUnauthorized());
		mockMvc.perform(put(BASE + "/translations/en").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(BASE + "/translations/en")).andExpect(status().isUnauthorized());
		mockMvc.perform(put(BASE + "/categories").contentType(MediaType.APPLICATION_JSON).content("{\"categories\":[]}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put(BASE + "/sources").contentType(MediaType.APPLICATION_JSON).content("{\"sources\":[]}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put(BASE + "/game").contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(BASE + "/game")).andExpect(status().isUnauthorized());
		verifyNoInteractions(publishingService, translationService, metadataService);
	}
}
