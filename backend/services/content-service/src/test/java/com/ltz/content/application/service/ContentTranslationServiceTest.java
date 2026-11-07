package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.content.api.dto.request.TranslationRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.entity.ContentTranslationId;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.ContentTranslationNotFoundException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.PublishRequirementsNotMetException;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryAssignmentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentSourceRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

/** Çoklu dil (CONT-15): yalnız sahibi, yalnız tr/en, ilk dil çeviri ucuyla değiştirilemez ya da silinemez. */
class ContentTranslationServiceTest {

	private static final UUID USER = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");
	private static final String LONG_BODY = "This body is clearly longer than thirty characters for publication.";

	private final ContentRepository contentRepository = mock(ContentRepository.class);
	private final ContentTranslationRepository translationRepository = mock(ContentTranslationRepository.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final ContentViewAssembler assembler = new ContentViewAssembler(translationRepository,
			mock(ContentImageRepository.class), mock(ContentCategoryAssignmentRepository.class),
			mock(ContentSourceRepository.class), new ContentMapper());
	private final ContentTranslationService service = new ContentTranslationService(contentRepository,
			translationRepository, new ContentAccess(contentRepository), assembler, currentUser,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final List<ContentTranslation> stored = new ArrayList<>();

	@BeforeEach
	void stubStorage() {
		when(currentUser.requireUserId()).thenReturn(USER);
		stored.clear();
		stored.add(ContentTranslation.of(CONTENT_ID, "tr", "Yeni güncelleme çıktı", null, "Türkçe gövde metni burada yer alıyor."));
		when(contentRepository.save(any(Content.class))).then(returnsFirstArg());
		when(translationRepository.findByIdContentId(CONTENT_ID)).thenAnswer(invocation -> List.copyOf(stored));
		when(translationRepository.findById(any(ContentTranslationId.class))).thenAnswer(invocation -> {
			ContentTranslationId id = invocation.getArgument(0);
			return stored.stream().filter(translation -> translation.getId().equals(id)).findFirst();
		});
		when(translationRepository.existsById(any(ContentTranslationId.class))).thenAnswer(invocation -> {
			ContentTranslationId id = invocation.getArgument(0);
			return stored.stream().anyMatch(translation -> translation.getId().equals(id));
		});
		when(translationRepository.save(any(ContentTranslation.class))).thenAnswer(invocation -> {
			ContentTranslation translation = invocation.getArgument(0);
			stored.removeIf(existing -> existing.getId().equals(translation.getId()));
			stored.add(translation);
			return translation;
		});
		doAnswer(invocation -> {
			ContentTranslationId id = invocation.getArgument(0);
			stored.removeIf(existing -> existing.getId().equals(id));
			return null;
		}).when(translationRepository).deleteById(any(ContentTranslationId.class));
	}

	private Content content(UUID author, ContentStatus status) {
		Content content = Content.draft(author, ContentType.NEWS, "tr", "a".repeat(64), NOW.minusSeconds(600));
		ReflectionTestUtils.setField(content, "id", CONTENT_ID);
		ReflectionTestUtils.setField(content, "status", status);
		if (status != ContentStatus.DRAFT) {
			ReflectionTestUtils.setField(content, "publishedAt", NOW.minusSeconds(300));
		}
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
		return content;
	}

	private static TranslationRequest english(String title, String summary, String body) {
		return new TranslationRequest(title, summary, body);
	}

	// ------------------------------------------------------------------ ekleme / değiştirme

	@Test
	void ownerAddsAnEnglishTranslationToADraft() {
		Content content = content(USER, ContentStatus.DRAFT);

		ContentResponse response = service.putTranslation(CONTENT_ID, "en", english("  New update released ", "  Short  ", "  "));

		ArgumentCaptor<ContentTranslation> saved = ArgumentCaptor.forClass(ContentTranslation.class);
		verify(translationRepository).save(saved.capture());
		assertThat(saved.getValue().getId()).isEqualTo(new ContentTranslationId(CONTENT_ID, "en"));
		assertThat(saved.getValue().getTitle()).isEqualTo("New update released");
		assertThat(saved.getValue().getSummary()).isEqualTo("Short");
		assertThat(saved.getValue().getBody()).isNull();
		assertThat(response.locale()).isEqualTo("en");
		assertThat(response.availableLocales()).containsExactly("tr", "en");
		assertThat(content.getUpdatedAt()).isEqualTo(NOW);
		verify(contentRepository).save(content);
	}

	@Test
	void anExistingTranslationIsReplacedCompletely() {
		content(USER, ContentStatus.DRAFT);
		stored.add(ContentTranslation.of(CONTENT_ID, "en", "Old title", "Old summary", "Old body"));

		ContentResponse response = service.putTranslation(CONTENT_ID, "en", english("New title", null, null));

		assertThat(response.title()).isEqualTo("New title");
		assertThat(response.summary()).isNull();
		assertThat(response.body()).isNull();
		assertThat(stored).hasSize(2);
	}

	@Test
	void theOriginalLanguageIsNotEditedThroughTheTranslationEndpoint() {
		content(USER, ContentStatus.DRAFT);

		TranslationRequest original = english("Başlık", null, null);

		assertThatThrownBy(() -> service.putTranslation(CONTENT_ID, "tr", original))
				.isInstanceOf(InvalidContentRequestException.class);
		assertThatThrownBy(() -> service.deleteTranslation(CONTENT_ID, "tr")).isInstanceOf(InvalidContentRequestException.class);
		verify(translationRepository, never()).save(any());
	}

	@Test
	void onlyTurkishAndEnglishAreSupported() {
		content(USER, ContentStatus.DRAFT);
		TranslationRequest request = english("Titel", null, null);

		for (String locale : new String[] { "de", "TR", "", "tr-TR", "xx" }) {
			assertThatThrownBy(() -> service.putTranslation(CONTENT_ID, locale, request)).isInstanceOf(InvalidContentRequestException.class);
			assertThatThrownBy(() -> service.deleteTranslation(CONTENT_ID, locale)).isInstanceOf(InvalidContentRequestException.class);
		}
	}

	@Test
	void aBlankTitleIsRejected() {
		content(USER, ContentStatus.DRAFT);
		TranslationRequest request = english("   ", null, null);

		assertThatThrownBy(() -> service.putTranslation(CONTENT_ID, "en", request)).isInstanceOf(InvalidContentRequestException.class);
	}

	// ------------------------------------------------------------------ yayımlanmış içerik

	@Test
	void translationsOfPublishedContentMustMeetThePublishRules() {
		content(USER, ContentStatus.PUBLISHED);
		TranslationRequest tooShort = english("Hi", null, "short");

		assertThatThrownBy(() -> service.putTranslation(CONTENT_ID, "en", tooShort))
				.isInstanceOfSatisfying(PublishRequirementsNotMetException.class, exception -> assertThat(exception.getIssues())
						.containsExactly("en:TITLE_TOO_SHORT", "en:BODY_TOO_SHORT"));
		assertThat(stored).hasSize(1);
	}

	@Test
	void aCompleteTranslationCanBeAddedToPublishedContent() {
		content(USER, ContentStatus.PUBLISHED);

		ContentResponse response = service.putTranslation(CONTENT_ID, "en", english("New update released", null, LONG_BODY));

		assertThat(response.locale()).isEqualTo("en");
		assertThat(response.status()).isEqualTo(ContentStatus.PUBLISHED);
	}

	@Test
	void removedContentCannotBeTranslated() {
		content(USER, ContentStatus.REMOVED);

		TranslationRequest request = english("Title", null, null);

		assertThatThrownBy(() -> service.putTranslation(CONTENT_ID, "en", request))
				.isInstanceOf(ContentNotEditableException.class);
	}

	// ------------------------------------------------------------------ silme

	@Test
	void ownerDeletesATranslationAndTheOriginalRemains() {
		Content content = content(USER, ContentStatus.DRAFT);
		stored.add(ContentTranslation.of(CONTENT_ID, "en", "Old title", null, null));

		ContentResponse response = service.deleteTranslation(CONTENT_ID, "en");

		assertThat(response.locale()).isEqualTo("tr");
		assertThat(response.availableLocales()).containsExactly("tr");
		verify(translationRepository).deleteById(new ContentTranslationId(CONTENT_ID, "en"));
		verify(contentRepository).save(content);
	}

	@Test
	void deletingAMissingTranslationIsNotFound() {
		content(USER, ContentStatus.DRAFT);

		assertThatThrownBy(() -> service.deleteTranslation(CONTENT_ID, "en")).isInstanceOf(ContentTranslationNotFoundException.class);
	}

	// ------------------------------------------------------------------ sahiplik

	@Test
	void someoneElsesDraftCannotBeTranslated() {
		content(OTHER, ContentStatus.DRAFT);

		TranslationRequest request = english("Title", null, null);

		assertThatThrownBy(() -> service.putTranslation(CONTENT_ID, "en", request))
				.isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> service.deleteTranslation(CONTENT_ID, "en")).isInstanceOf(ContentNotFoundException.class);
		verify(translationRepository, never()).save(any());
	}
}
