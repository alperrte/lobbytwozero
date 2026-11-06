package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ltz.content.TestImages;
import com.ltz.content.api.dto.request.ContentFeedRequest;
import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.request.ReplaceCategoriesRequest;
import com.ltz.content.api.dto.request.ReplaceSourcesRequest;
import com.ltz.content.api.dto.request.SourceRequest;
import com.ltz.content.api.dto.request.TranslationRequest;
import com.ltz.content.api.dto.request.UpdateContentRequest;
import com.ltz.content.api.dto.response.ContentPageResponse;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.application.port.GameCatalogPort;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.AdministratorRequiredException;
import com.ltz.content.domain.exception.ContentAlreadyPublishedException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.GameNotAvailableException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.PublishRequirementsNotMetException;
import com.ltz.content.infrastructure.config.ClockConfig;
import com.ltz.content.infrastructure.persistence.JdbcContentFeed;
import com.ltz.content.infrastructure.persistence.PostgreSqlIntegrationTest;

import jakarta.persistence.EntityManager;

/**
 * CONT-02 akışı gerçek PostgreSQL üzerinde: taslak → kategori/kaynak/oyun/çeviri → yayın → herkese açık akış ve arama →
 * öne çıkarma → silme sonrası tutarlılık. Yalnız oturum ve game-service sahtedir; sorgular, kısıtlar ve servisler gerçektir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ContentService.class, ContentImageService.class, ImageSanitizer.class, ContentPublishingService.class, ContentTranslationService.class,
		ContentMetadataService.class, ContentFeedService.class, JdbcContentFeed.class, ContentAccess.class,
		ContentMapper.class, ContentFingerprint.class, ContentViewAssembler.class, ClockConfig.class,
		ContentPublicationLifecycleIntegrationTest.JdbcClientConfig.class })
class ContentPublicationLifecycleIntegrationTest extends PostgreSqlIntegrationTest {

	@TestConfiguration
	static class JdbcClientConfig {
		@Bean
		JdbcClient jdbcClient(JdbcTemplate jdbcTemplate) {
			return JdbcClient.create(jdbcTemplate);
		}
	}

	private static final UUID USER = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID ADMIN = UUID.fromString("0198a3c2-9d30-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String BODY = "Bu gövde metni yayın için gereken otuz karakterden açıkça daha uzundur.";

	@Autowired
	private ContentService contentService;

	@Autowired
	private ContentImageService imageService;

	@Autowired
	private ContentPublishingService publishingService;

	@Autowired
	private ContentTranslationService translationService;

	@Autowired
	private ContentMetadataService metadataService;

	@Autowired
	private ContentFeedService feedService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private CurrentUserPort currentUser;

	@MockitoBean
	private GameCatalogPort gameCatalog;

	@BeforeEach
	void signInAsTheOwner() {
		asUser(USER, false);
		when(gameCatalog.isAvailable(GAME)).thenReturn(true);
	}

	private void asUser(UUID userId, boolean admin) {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(currentUser.findUserId()).thenReturn(Optional.of(userId));
		when(currentUser.hasAdminRole()).thenReturn(admin);
	}

	private void asAnonymous() {
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		when(currentUser.hasAdminRole()).thenReturn(false);
	}

	private ContentPageResponse feed(String q, String category) {
		entityManager.flush();
		entityManager.clear();
		return feedService.list(new ContentFeedRequest(q, category, null, null, null, null, null, null, null, null));
	}

	private int rows(String table, UUID contentId) {
		String column = "contents".equals(table) ? "id" : "content_id";
		return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, contentId);
	}

	// ------------------------------------------------------------------ yayın akışı

	/** Yayın kuralları sağlanıp içerik yayımlanır; sonraki testlerin ortak başlangıcı. Dönüş: yayımlanmış içeriğin kimliği. */
	private UUID publishedContent() {
		UUID id = contentService.createDraft(new CreateContentRequest(ContentType.NEWS, null, "Yeni güncelleme çıktı", "Kısa özet", "Kısa gövde")).id();
		contentService.updateContent(id, new UpdateContentRequest(null, null, BODY));
		metadataService.replaceCategories(id, new ReplaceCategoriesRequest(List.of("PC", "UPDATE")));
		metadataService.replaceSources(id, new ReplaceSourcesRequest(List.of(
				new SourceRequest("IGN", "https://www.ign.com/articles/a", LocalDate.parse("2026-11-01")))));
		metadataService.linkGame(id, GAME);
		publishingService.publish(id);
		return id;
	}

	@Test
	void aDraftThatDoesNotMeetThePublishRulesIsRejectedAndStaysOutOfTheFeed() {
		UUID id = contentService.createDraft(new CreateContentRequest(ContentType.NEWS, null, "Yeni güncelleme çıktı", "Kısa özet", "Kısa gövde")).id();

		assertThatThrownBy(() -> publishingService.publish(id)).isInstanceOfSatisfying(PublishRequirementsNotMetException.class,
				exception -> assertThat(exception.getIssues()).containsExactly("tr:BODY_TOO_SHORT"));
		assertThat(feed(null, null).items()).isEmpty();
	}

	@Test
	void publishingMakesTheContentVisibleToAnonymousVisitorsWithSearchAndFilters() {
		UUID id = publishedContent();

		ContentResponse published = contentService.getContent(id, null);
		assertThat(published.status()).isEqualTo(ContentStatus.PUBLISHED);
		assertThat(published.publishedAt()).isNotNull();
		assertThat(published.categories()).containsExactly("PC", "UPDATE");
		assertThat(published.gameId()).isEqualTo(GAME);
		assertThat(published.sources()).singleElement().satisfies(source -> assertThat(source.title()).isEqualTo("IGN"));
		assertThatThrownBy(() -> publishingService.publish(id)).isInstanceOf(ContentAlreadyPublishedException.class);

		asAnonymous();
		assertThat(feed(null, null).items()).singleElement().satisfies(card -> {
			assertThat(card.id()).isEqualTo(id);
			assertThat(card.title()).isEqualTo("Yeni güncelleme çıktı");
			assertThat(card.categories()).containsExactly("PC", "UPDATE");
			assertThat(card.featured()).isFalse();
		});
		assertThat(feed("GÜNCELLEME", null).items()).hasSize(1);
		assertThat(feed("otuz karakterden", "PC").items()).hasSize(1);
		assertThat(feed("yok böyle bir şey", null).items()).isEmpty();
		assertThat(feed(null, "ESPORTS").items()).isEmpty();
		assertThat(contentService.getContent(id, null).body()).isEqualTo(BODY);
	}

	@Test
	void onlyAdministratorsCanFeatureAndTheFeaturedFilterFollows() {
		UUID id = publishedContent();

		assertThatThrownBy(() -> publishingService.feature(id)).isInstanceOf(AdministratorRequiredException.class);
		asUser(ADMIN, true);
		assertThat(publishingService.feature(id).featured()).isTrue();
		asAnonymous();
		entityManager.flush();
		assertThat(feedService.list(new ContentFeedRequest(null, null, null, null, true, null, null, null, null, null)).items()).hasSize(1);

		asUser(ADMIN, true);
		assertThat(publishingService.unfeature(id).featured()).isFalse();
		asAnonymous();
		entityManager.flush();
		assertThat(feedService.list(new ContentFeedRequest(null, null, null, null, true, null, null, null, null, null)).items()).isEmpty();
	}

	@Test
	void anEnglishTranslationIsShownAndSearchedWhenRequested() {
		UUID id = publishedContent();

		ContentResponse translated = translationService.putTranslation(id, "en",
				new TranslationRequest("New update released", "Short", "This English body is clearly longer than thirty characters."));
		assertThat(translated.availableLocales()).containsExactly("tr", "en");

		asAnonymous();
		entityManager.flush();
		entityManager.clear();
		ContentPageResponse english = feedService.list(new ContentFeedRequest(null, null, null, null, null, null, null, "en", null, null));
		assertThat(english.items()).singleElement().satisfies(card -> assertThat(card.title()).isEqualTo("New update released"));
		assertThat(feed("released", null).items()).hasSize(1);
		assertThat(contentService.getContent(id, "en").title()).isEqualTo("New update released");
		assertThat(contentService.getContent(id, "tr").title()).isEqualTo("Yeni güncelleme çıktı");
	}

	@Test
	void deletingPublishedContentRemovesItFromEverywhereAndErasesItsTextPermanently() {
		UUID id = publishedContent();
		translationService.putTranslation(id, "en",
				new TranslationRequest("New update released", null, "This English body is clearly longer than thirty characters."));
		entityManager.flush();

		contentService.deleteContent(id);

		assertThat(feed(null, null).items()).isEmpty();
		assertThat(feed("released", null).items()).isEmpty();
		asAnonymous();
		assertThatThrownBy(() -> contentService.getContent(id, null)).isInstanceOf(ContentNotFoundException.class);
		assertThat(rows("contents", id)).isEqualTo(1);
		assertThat(rows("content_translations", id)).isZero();
		assertThat(rows("content_category_assignments", id)).isZero();
		assertThat(rows("content_sources", id)).isZero();
		assertThat(jdbc.queryForObject("select featured from contents where id = ?", Boolean.class, id)).isFalse();
	}

	@Test
	void publishRulesApplyWhenAPublishedTranslationIsReplacedThroughTheTranslationEndpoint() {
		UUID id = publishedContent();
		translationService.putTranslation(id, "en",
				new TranslationRequest("New update released", "Short", "This English body is clearly longer than thirty characters."));
		TranslationRequest tooShort = new TranslationRequest("Hi", null, "short");

		assertThatThrownBy(() -> translationService.putTranslation(id, "en", tooShort)).isInstanceOfSatisfying(
				PublishRequirementsNotMetException.class,
				exception -> assertThat(exception.getIssues()).containsExactly("en:TITLE_TOO_SHORT", "en:BODY_TOO_SHORT"));

		entityManager.flush();
		entityManager.clear();
		assertThat(contentService.getContent(id, "en").title()).isEqualTo("New update released");
	}

	@Test
	void atMostFiveCategoriesAreAcceptedEvenWhenTheServiceIsCalledDirectly() {
		UUID id = contentService.createDraft(new CreateContentRequest(ContentType.NEWS, null, "Çok kategorili haber", null, null)).id();
		ReplaceCategoriesRequest six = new ReplaceCategoriesRequest(
				List.of("PC", "XBOX", "MOBILE", "VR", "UPDATE", "GUIDE"));
		ReplaceCategoriesRequest five = new ReplaceCategoriesRequest(List.of("PC", "XBOX", "MOBILE", "VR", "UPDATE"));

		assertThatThrownBy(() -> metadataService.replaceCategories(id, six)).isInstanceOf(InvalidContentRequestException.class);
		assertThat(metadataService.replaceCategories(id, five).categories()).hasSize(5);
	}

	// ------------------------------------------------------------------ görsel URL'si (önbelleksiz, her istekte yeniden denetim)

	@Test
	void aPublishedImageIsReadableByGuestsUntilTheContentIsDeleted() throws IOException {
		UUID id = publishedContent();
		UUID imageId = imageService.addImage(id, TestImages.png(10, 10)).id();
		entityManager.flush();
		entityManager.clear();
		asAnonymous();
		assertThat(imageService.getImage(id, imageId).content()).isNotEmpty();

		asUser(USER, false);
		contentService.deleteContent(id);
		entityManager.flush();
		entityManager.clear();

		asAnonymous();
		assertThatThrownBy(() -> imageService.getImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);
		assertThat(jdbc.queryForObject("select count(*) from content_images where content_id = ?", Integer.class, id)).isZero();
	}

	@Test
	void aPublishedImageStopsBeingServedToGuestsWhenTheContentIsRemoved() throws IOException {
		UUID id = publishedContent();
		UUID imageId = imageService.addImage(id, TestImages.jpeg(10, 10)).id();
		entityManager.flush();
		entityManager.clear();
		asAnonymous();
		assertThat(imageService.getImage(id, imageId).content()).isNotEmpty();

		// Moderatörce kaldırma (sonraki faz) şimdilik doğrudan durum değişikliğiyle benzetilir.
		jdbc.update("update contents set status = 'REMOVED' where id = ?", id);
		entityManager.clear();

		assertThatThrownBy(() -> imageService.getImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);
	}

	@Test
	void aDraftImageIsNeverServedToGuests() throws IOException {
		UUID id = contentService.createDraft(new CreateContentRequest(ContentType.NEWS, null, "Taslak görselli haber", null, null)).id();
		UUID imageId = imageService.addImage(id, TestImages.png(10, 10)).id();
		entityManager.flush();
		entityManager.clear();

		assertThat(imageService.getImage(id, imageId).content()).isNotEmpty();
		asAnonymous();
		assertThatThrownBy(() -> imageService.getImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);
	}

	@Test
	void someoneElsesDraftIsInvisibleInTheFeedAndTheDetail() {
		UUID id = contentService.createDraft(new CreateContentRequest(ContentType.NEWS, null, "Gizli taslak", null, null)).id();
		asUser(OTHER, false);
		ReplaceCategoriesRequest categories = new ReplaceCategoriesRequest(List.of("PC"));
		TranslationRequest translation = new TranslationRequest("Hidden", null, null);

		assertThat(feed(null, null).items()).isEmpty();
		assertThatThrownBy(() -> contentService.getContent(id, null)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> publishingService.publish(id)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> metadataService.replaceCategories(id, categories)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> translationService.putTranslation(id, "en", translation)).isInstanceOf(ContentNotFoundException.class);
	}

	@Test
	void anUnknownGameIsRejectedAndNothingIsWritten() {
		UUID unknown = UUID.randomUUID();
		when(gameCatalog.isAvailable(unknown)).thenReturn(false);
		UUID id = contentService.createDraft(new CreateContentRequest(ContentType.NEWS, null, "Oyun haberi", null, null)).id();

		assertThatThrownBy(() -> metadataService.linkGame(id, unknown)).isInstanceOf(GameNotAvailableException.class);

		entityManager.flush();
		assertThat(jdbc.queryForObject("select game_id from contents where id = ?", UUID.class, id)).isNull();
	}

	@Test
	void unknownCategoryCodesAreRejected() {
		UUID id = contentService.createDraft(new CreateContentRequest(ContentType.NEWS, null, "Kategori haberi", null, null)).id();

		ReplaceCategoriesRequest request = new ReplaceCategoriesRequest(List.of("PC", "YOK_BOYLE"));

		assertThatThrownBy(() -> metadataService.replaceCategories(id, request)).isInstanceOf(InvalidContentRequestException.class);
	}
}
