package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.content.api.dto.request.ReplaceCategoriesRequest;
import com.ltz.content.api.dto.request.ReplaceSourcesRequest;
import com.ltz.content.api.dto.request.SourceRequest;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.application.port.GameCatalogPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentCategory;
import com.ltz.content.domain.entity.ContentCategoryAssignment;
import com.ltz.content.domain.entity.ContentSource;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.GameCatalogUnavailableException;
import com.ltz.content.domain.exception.GameNotAvailableException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryAssignmentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentSourceRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

/** Kategoriler (CONT-03), kaynaklar (CONT-16) ve oyun ilişkisi (CONT-01/13): yalnız sahibi, yalnız düzenlenebilir içerik. */
class ContentMetadataServiceTest {

	private static final UUID USER = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

	private final ContentRepository contentRepository = mock(ContentRepository.class);
	private final ContentCategoryRepository categoryRepository = mock(ContentCategoryRepository.class);
	private final ContentCategoryAssignmentRepository assignmentRepository = mock(ContentCategoryAssignmentRepository.class);
	private final ContentSourceRepository sourceRepository = mock(ContentSourceRepository.class);
	private final ContentTranslationRepository translationRepository = mock(ContentTranslationRepository.class);
	private final GameCatalogPort gameCatalog = mock(GameCatalogPort.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final ContentViewAssembler assembler = new ContentViewAssembler(translationRepository,
			mock(ContentImageRepository.class), assignmentRepository, sourceRepository, new ContentMapper());
	private final ContentMetadataService service = new ContentMetadataService(contentRepository, categoryRepository,
			assignmentRepository, sourceRepository, new ContentAccess(contentRepository), assembler, gameCatalog, currentUser,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void stubStorage() {
		when(currentUser.requireUserId()).thenReturn(USER);
		when(translationRepository.findByIdContentId(CONTENT_ID))
				.thenReturn(List.of(ContentTranslation.of(CONTENT_ID, "tr", "Yeni güncelleme çıktı", null, null)));
		when(contentRepository.save(any(Content.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

	private static SourceRequest source(String title, String url, LocalDate date) {
		return new SourceRequest(title, url, date);
	}

	// ------------------------------------------------------------------ kategoriler

	@Test
	void ownerReplacesTheCategoriesOfADraft() {
		Content content = content(USER, ContentStatus.DRAFT);
		ContentCategory pc = newCategory("PC");
		ContentCategory update = newCategory("UPDATE");
		when(categoryRepository.findByCodeIn(anyCollection())).thenReturn(List.of(pc, update));

		service.replaceCategories(CONTENT_ID, new ReplaceCategoriesRequest(List.of("PC", "UPDATE", "PC")));

		verify(assignmentRepository).deleteByContentId(CONTENT_ID);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ContentCategoryAssignment>> saved = ArgumentCaptor.forClass(List.class);
		verify(assignmentRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).hasSize(2);
		assertThat(saved.getValue()).extracting(assignment -> assignment.getId().contentId()).containsOnly(CONTENT_ID);
		assertThat(content.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void unknownCategoryCodesAreRejectedAndNothingChanges() {
		content(USER, ContentStatus.DRAFT);
		when(categoryRepository.findByCodeIn(anyCollection())).thenReturn(List.of(newCategory("PC")));
		ReplaceCategoriesRequest request = new ReplaceCategoriesRequest(List.of("PC", "NOT_A_CATEGORY"));

		assertThatThrownBy(() -> service.replaceCategories(CONTENT_ID, request)).isInstanceOf(InvalidContentRequestException.class);

		verify(assignmentRepository, never()).deleteByContentId(any());
		verify(assignmentRepository, never()).saveAll(anyIterable());
	}

	@Test
	void anEmptyListClearsTheCategories() {
		content(USER, ContentStatus.DRAFT);

		service.replaceCategories(CONTENT_ID, new ReplaceCategoriesRequest(List.of()));

		verify(assignmentRepository).deleteByContentId(CONTENT_ID);
		verify(categoryRepository, never()).findByCodeIn(anyCollection());
	}

	// ------------------------------------------------------------------ kaynaklar

	@Test
	void ownerReplacesTheSourcesInOrder() {
		content(USER, ContentStatus.PUBLISHED);

		service.replaceSources(CONTENT_ID, new ReplaceSourcesRequest(List.of(
				source("  Resmî duyuru  ", "https://example.org/news/1", LocalDate.of(2026, 11, 1)),
				source("Basın bülteni", "https://press.example.org/a?b=c#d", null))));

		verify(sourceRepository).deleteByContentId(CONTENT_ID);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ContentSource>> saved = ArgumentCaptor.forClass(List.class);
		verify(sourceRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).extracting(ContentSource::getTitle).containsExactly("Resmî duyuru", "Basın bülteni");
		assertThat(saved.getValue()).extracting(ContentSource::getSortOrder).containsExactly(0, 1);
		assertThat(saved.getValue().get(0).getSourceDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(saved.getValue().get(1).getSourceDate()).isNull();
	}

	@Test
	void onlyHttpsLinksWithAHostAndNoUserInfoAreAccepted() {
		content(USER, ContentStatus.DRAFT);
		String[] unsafe = { "http://example.org/a", "ftp://example.org/a", "javascript:alert(1)", "data:text/html,x",
				"//example.org/a", "https://", "https:///path", "https://user:pass@example.org/a", "https://exa mple.org/a",
				"file:///etc/passwd", "https:example.org" };
		for (String url : unsafe) {
			ReplaceSourcesRequest request = new ReplaceSourcesRequest(List.of(source("Kaynak", url, null)));

			assertThatThrownBy(() -> service.replaceSources(CONTENT_ID, request)).isInstanceOf(InvalidContentRequestException.class);
		}
		verify(sourceRepository, never()).saveAll(anyIterable());
	}

	@Test
	void anEmptyListClearsTheSources() {
		content(USER, ContentStatus.DRAFT);

		service.replaceSources(CONTENT_ID, new ReplaceSourcesRequest(List.of()));

		verify(sourceRepository).deleteByContentId(CONTENT_ID);
	}

	// ------------------------------------------------------------------ oyun ilişkisi

	@Test
	void theGameIsValidatedAgainstTheCatalogBeforeItIsLinked() {
		Content content = content(USER, ContentStatus.DRAFT);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);

		var response = service.linkGame(CONTENT_ID, GAME_ID);

		assertThat(content.getGameId()).isEqualTo(GAME_ID);
		assertThat(response.gameId()).isEqualTo(GAME_ID);
		verify(contentRepository).save(content);
	}

	@Test
	void anUnknownGameIsRejected() {
		Content content = content(USER, ContentStatus.DRAFT);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.linkGame(CONTENT_ID, GAME_ID)).isInstanceOf(GameNotAvailableException.class);

		assertThat(content.getGameId()).isNull();
		verify(contentRepository, never()).save(any());
	}

	@Test
	void anUnreachableCatalogNeverLinksAnUnverifiedGame() {
		Content content = content(USER, ContentStatus.DRAFT);
		when(gameCatalog.isAvailable(GAME_ID)).thenThrow(new GameCatalogUnavailableException(new IllegalStateException("down")));

		assertThatThrownBy(() -> service.linkGame(CONTENT_ID, GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);

		assertThat(content.getGameId()).isNull();
	}

	@Test
	void relinkingTheSameGameSkipsTheCatalogCall() {
		Content content = content(USER, ContentStatus.DRAFT);
		content.linkGame(GAME_ID, NOW.minusSeconds(10));

		service.linkGame(CONTENT_ID, GAME_ID);

		verify(gameCatalog, never()).isAvailable(any());
		verify(contentRepository, never()).save(any());
	}

	@Test
	void theGameLinkCanBeRemoved() {
		Content content = content(USER, ContentStatus.PUBLISHED);
		content.linkGame(GAME_ID, NOW.minusSeconds(10));

		var response = service.unlinkGame(CONTENT_ID);

		assertThat(content.getGameId()).isNull();
		assertThat(response.gameId()).isNull();
		verify(contentRepository).save(content);
	}

	// ------------------------------------------------------------------ sahiplik ve durum

	@Test
	void someoneElsesDraftIsInvisibleToEveryMetadataOperation() {
		content(OTHER, ContentStatus.DRAFT);
		ReplaceCategoriesRequest categories = new ReplaceCategoriesRequest(List.of());
		ReplaceSourcesRequest sources = new ReplaceSourcesRequest(List.of());

		assertThatThrownBy(() -> service.replaceCategories(CONTENT_ID, categories)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> service.replaceSources(CONTENT_ID, sources)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> service.linkGame(CONTENT_ID, GAME_ID)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> service.unlinkGame(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);
		verify(gameCatalog, never()).isAvailable(any());
	}

	@Test
	void removedContentCannotBeChanged() {
		content(USER, ContentStatus.REMOVED);
		ReplaceCategoriesRequest categories = new ReplaceCategoriesRequest(List.of());

		assertThatThrownBy(() -> service.replaceCategories(CONTENT_ID, categories)).isInstanceOf(ContentNotEditableException.class);
		assertThatThrownBy(() -> service.linkGame(CONTENT_ID, GAME_ID)).isInstanceOf(ContentNotEditableException.class);
	}

	private ContentCategory newCategory(String code) {
		ContentCategory category = instantiate(ContentCategory.class);
		ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(category, "code", code);
		return category;
	}

	private static <T> T instantiate(Class<T> type) {
		try {
			var constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
