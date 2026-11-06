package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ltz.content.TestImages;
import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.request.UpdateContentRequest;
import com.ltz.content.api.dto.response.ContentImageResponse;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.ContentImageLimitExceededException;
import com.ltz.content.domain.exception.ContentImageNotFoundException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.DraftLimitExceededException;
import com.ltz.content.domain.exception.DuplicateContentException;
import com.ltz.content.domain.exception.OfficialAnnouncementForbiddenException;
import com.ltz.content.domain.exception.TooManyContentsException;
import com.ltz.content.infrastructure.config.ClockConfig;
import com.ltz.content.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;

import jakarta.persistence.EntityManager;

/**
 * Oluştur → düzenle → görsel ekle → sil akışı gerçek PostgreSQL üzerinde, gerçek servisler ve gerçek veritabanı kısıtlarıyla.
 * Yalnız oturum (kimlik/rol) sahtedir. Sahiplik ihlali, mükerrer içerik, kota ve silme sonrası tutarlılık burada doğrulanır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ContentService.class, ContentImageService.class, ContentAccess.class, ContentMapper.class,
		ContentFingerprint.class, ImageSanitizer.class, ClockConfig.class })
class ContentLifecycleIntegrationTest extends PostgreSqlIntegrationTest {

	private static final UUID USER = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");

	@Autowired
	private ContentService contentService;

	@Autowired
	private ContentImageService imageService;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private CurrentUserPort currentUser;

	@BeforeEach
	void signInAsTheOwner() {
		asUser(USER, false);
	}

	private void asUser(UUID userId, boolean admin) {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(currentUser.hasAdminRole()).thenReturn(admin);
	}

	private static CreateContentRequest news(String title) {
		return new CreateContentRequest(ContentType.NEWS, null, title, "Kısa özet", "Gövde metni");
	}

	private int rows(String table, UUID contentId) {
		String column = "contents".equals(table) ? "id" : "content_id";
		return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, contentId);
	}

	// ------------------------------------------------------------------ tam akış

	@Test
	void draftLifecycleFromCreationToDeletion() throws IOException {
		ContentResponse created = contentService.createDraft(news("İlk haber"));
		assertThat(created.status()).isEqualTo(ContentStatus.DRAFT);
		assertThat(created.authorId()).isEqualTo(USER);
		assertThat(created.images()).isEmpty();

		ContentResponse edited = contentService.updateDraft(created.id(), new UpdateContentRequest("Düzenlenmiş haber", null, "Yeni gövde"));
		assertThat(edited.title()).isEqualTo("Düzenlenmiş haber");
		assertThat(edited.summary()).isEqualTo("Kısa özet");
		assertThat(edited.body()).isEqualTo("Yeni gövde");
		assertThat(edited.updatedAt()).isAfterOrEqualTo(created.updatedAt());

		ContentImageResponse image = imageService.addImage(created.id(), TestImages.png(40, 20));
		assertThat(image.sortOrder()).isZero();
		assertThat(image.mimeType()).isEqualTo("image/png");

		entityManager.flush();
		entityManager.clear();
		ContentResponse loaded = contentService.getContent(created.id());
		assertThat(loaded.title()).isEqualTo("Düzenlenmiş haber");
		assertThat(loaded.images()).hasSize(1);
		assertThat(loaded.images().get(0).contentUrl())
				.isEqualTo("/api/v1/content/contents/" + created.id() + "/images/" + image.id());
		assertThat(imageService.getImage(created.id(), image.id()).content()).isNotEmpty();

		contentService.deleteContent(created.id());
		entityManager.flush();
		entityManager.clear();

		UUID createdId = created.id();
		UUID imageId = image.id();
		assertThatThrownBy(() -> contentService.getContent(createdId)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> imageService.getImage(createdId, imageId)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> contentService.deleteContent(createdId)).isInstanceOf(ContentNotFoundException.class);
	}

	// ------------------------------------------------------------------ silme sonrası tutarlılık (CONT-07)

	@Test
	void deletionKeepsOnlyTheMarkedRowAndErasesTextAndImagesPermanently() throws IOException {
		UUID id = contentService.createDraft(news("Silinecek haber")).id();
		imageService.addImage(id, TestImages.png(10, 10));
		imageService.addImage(id, TestImages.jpeg(10, 10));
		entityManager.flush();
		assertThat(rows("content_images", id)).isEqualTo(2);
		assertThat(rows("content_translations", id)).isEqualTo(1);

		contentService.deleteContent(id);
		entityManager.flush();
		entityManager.clear();

		// Metin ve görseller aynı transaction'da kalıcı olarak silinir; yalnız silme işaretli contents satırı kalır.
		assertThat(rows("content_images", id)).isZero();
		assertThat(rows("content_translations", id)).isZero();
		assertThat(rows("contents", id)).isEqualTo(1);
		Content row = contentRepository.findById(id).orElseThrow();
		assertThat(row.isDeleted()).isTrue();
		assertThat(row.getContentHash()).isEqualTo(Content.ERASED_HASH);
		assertThat(contentRepository.findByIdAndDeletedAtIsNull(id)).isEmpty();
	}

	@Test
	void deletingContentNeverGivesTheQuotaBack() {
		for (int index = 0; index < ContentService.MAX_CREATIONS_PER_HOUR; index++) {
			contentService.deleteContent(contentService.createDraft(news("Kota haberi " + index)).id());
		}
		entityManager.flush();

		CreateContentRequest next = news("Kotadan sonraki haber");
		assertThatThrownBy(() -> contentService.createDraft(next)).isInstanceOf(TooManyContentsException.class);
	}

	@Test
	void imageUrlsOfADeletedContentStopWorking() throws IOException {
		UUID id = contentService.createDraft(news("Görselli haber")).id();
		UUID imageId = imageService.addImage(id, TestImages.png(10, 10)).id();
		contentService.deleteContent(id);
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> imageService.getImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> imageService.deleteImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);
		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> imageService.addImage(id, upload)).isInstanceOf(ContentNotFoundException.class);
	}

	@Test
	void theSameTextCanBeCreatedAgainAfterTheOriginalWasDeleted() {
		UUID first = contentService.createDraft(news("Tekrar kullanılan başlık")).id();
		contentService.deleteContent(first);
		entityManager.flush();

		ContentResponse again = contentService.createDraft(news("Tekrar kullanılan başlık"));

		assertThat(again.id()).isNotEqualTo(first);
	}

	// ------------------------------------------------------------------ sahiplik (IDOR)

	@Test
	void anotherUserCannotSeeEditDeleteOrUseImagesOfSomeoneElsesDraft() throws IOException {
		UUID id = contentService.createDraft(news("Özel taslak")).id();
		UUID imageId = imageService.addImage(id, TestImages.png(10, 10)).id();
		entityManager.flush();
		entityManager.clear();

		asUser(OTHER, false);

		assertThatThrownBy(() -> contentService.getContent(id)).isInstanceOf(ContentNotFoundException.class);
		UpdateContentRequest hijack = new UpdateContentRequest("Ele geçirildi", null, null);
		assertThatThrownBy(() -> contentService.updateDraft(id, hijack))
				.isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> contentService.deleteContent(id)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> imageService.getImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);
		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> imageService.addImage(id, upload)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> imageService.deleteImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);

		// Hiçbir şey değişmedi: sahibi hâlâ aynı içeriği ve görseli görür.
		asUser(USER, false);
		ContentResponse intact = contentService.getContent(id);
		assertThat(intact.title()).isEqualTo("Özel taslak");
		assertThat(intact.images()).hasSize(1);
	}

	@Test
	void theAuthorCannotBeChangedThroughEditing() {
		UUID id = contentService.createDraft(news("Sahipli haber")).id();

		contentService.updateDraft(id, new UpdateContentRequest("Başka başlık", null, null));
		entityManager.flush();
		entityManager.clear();

		Content row = contentRepository.findById(id).orElseThrow();
		assertThat(row.getAuthorId()).isEqualTo(USER);
		assertThat(row.getType()).isEqualTo(ContentType.NEWS);
		assertThat(row.getStatus()).isEqualTo(ContentStatus.DRAFT);
	}

	// ------------------------------------------------------------------ rol kuralı

	@Test
	void onlyAdministratorsCanCreateOfficialAnnouncements() {
		CreateContentRequest announcement = new CreateContentRequest(ContentType.ANNOUNCEMENT, null, "Planlı bakım", null, null);

		assertThatThrownBy(() -> contentService.createDraft(announcement)).isInstanceOf(OfficialAnnouncementForbiddenException.class);

		asUser(USER, true);
		ContentResponse created = contentService.createDraft(announcement);
		assertThat(created.type()).isEqualTo(ContentType.ANNOUNCEMENT);
		assertThat(created.status()).isEqualTo(ContentStatus.DRAFT);
		assertThat(created.authorId()).isEqualTo(USER);
	}

	@Test
	void anyRegisteredUserCanCreateNewsAndUserContent() {
		assertThat(contentService.createDraft(news("Herkesin haberi")).type()).isEqualTo(ContentType.NEWS);
		assertThat(contentService.createDraft(new CreateContentRequest(ContentType.USER_CONTENT, "en", "My guide", null, null)).type())
				.isEqualTo(ContentType.USER_CONTENT);
	}

	// ------------------------------------------------------------------ mükerrer ve kota (CONT-18)

	@Test
	void duplicateTextIsRejectedOnCreationAndOnEditing() {
		contentService.createDraft(news("Ayni haber"));
		UUID second = contentService.createDraft(news("Baska haber")).id();
		entityManager.flush();

		// Büyük/küçük harf ve boşluk farkları mükerrer saymayı engellemez.
		CreateContentRequest duplicate = news("  AYNI   HABER ");
		assertThatThrownBy(() -> contentService.createDraft(duplicate)).isInstanceOf(DuplicateContentException.class);

		UpdateContentRequest rename = new UpdateContentRequest("ayni haber", null, null);
		assertThatThrownBy(() -> contentService.updateDraft(second, rename))
				.isInstanceOf(DuplicateContentException.class);
	}

	@Test
	void anotherAuthorMayPublishTheSameText() {
		contentService.createDraft(news("Ortak haber"));
		entityManager.flush();

		asUser(OTHER, false);

		assertThat(contentService.createDraft(news("Ortak haber")).authorId()).isEqualTo(OTHER);
	}

	@Test
	void creationIsLimitedPerHourEvenWhenContentIsDeletedInBetween() {
		for (int index = 0; index < ContentService.MAX_CREATIONS_PER_HOUR; index++) {
			UUID id = contentService.createDraft(news("Haber numara " + index)).id();
			contentService.deleteContent(id);
		}
		entityManager.flush();

		CreateContentRequest extra = news("Bir tane daha");
		assertThatThrownBy(() -> contentService.createDraft(extra)).isInstanceOf(TooManyContentsException.class);
	}

	@Test
	void theNumberOfActiveDraftsIsLimited() {
		Instant longAgo = Instant.now().minusSeconds(7200);
		for (int index = 0; index < ContentService.MAX_ACTIVE_DRAFTS; index++) {
			contentRepository.save(Content.draft(USER, ContentType.NEWS, "tr", String.format("%064d", index), longAgo));
		}
		entityManager.flush();

		CreateContentRequest overflow = news("Yirmi birinci taslak");
		assertThatThrownBy(() -> contentService.createDraft(overflow))
				.isInstanceOf(DraftLimitExceededException.class);
	}

	// ------------------------------------------------------------------ görsel kotası

	@Test
	void imageLimitAppliesAndFreedSlotsAreReused() throws IOException {
		UUID id = contentService.createDraft(news("Çok görselli haber")).id();
		UUID third = null;
		for (int index = 0; index < 6; index++) {
			ContentImageResponse image = imageService.addImage(id, TestImages.png(10 + index, 10));
			assertThat(image.sortOrder()).isEqualTo(index);
			if (index == 2) {
				third = image.id();
			}
		}
		entityManager.flush();

		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> imageService.addImage(id, upload)).isInstanceOf(ContentImageLimitExceededException.class);

		imageService.deleteImage(id, third);
		entityManager.flush();
		assertThat(imageService.addImage(id, TestImages.png(10, 10)).sortOrder()).isEqualTo(2);
	}

	@Test
	void deletingAnImageTwiceIsNotFound() throws IOException {
		UUID id = contentService.createDraft(news("Görsel silme")).id();
		UUID imageId = imageService.addImage(id, TestImages.png(10, 10)).id();
		imageService.deleteImage(id, imageId);
		entityManager.flush();

		assertThatThrownBy(() -> imageService.deleteImage(id, imageId)).isInstanceOf(ContentImageNotFoundException.class);
	}
}
