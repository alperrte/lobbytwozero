package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.content.TestImages;
import com.ltz.content.api.dto.response.ContentImageContent;
import com.ltz.content.api.dto.response.ContentImageResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentImage;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.ContentAccessDeniedException;
import com.ltz.content.domain.exception.ContentImageLimitExceededException;
import com.ltz.content.domain.exception.ContentImageNotFoundException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.InvalidImageException;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;

class ContentImageServiceTest {

	private static final UUID USER = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID IMAGE_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

	private final ContentRepository contentRepository = mock(ContentRepository.class);
	private final ContentImageRepository imageRepository = mock(ContentImageRepository.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final ContentImageService service = new ContentImageService(imageRepository, new ContentAccess(contentRepository),
			new ImageSanitizer(), new ContentMapper(), currentUser, Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void stubStorage() {
		when(currentUser.requireUserId()).thenReturn(USER);
		when(currentUser.findUserId()).thenReturn(Optional.of(USER));
		when(imageRepository.save(any(ContentImage.class))).thenAnswer(invocation -> {
			ContentImage image = invocation.getArgument(0);
			ReflectionTestUtils.setField(image, "id", IMAGE_ID);
			return image;
		});
	}

	private void content(UUID author, ContentStatus status) {
		Content content = Content.draft(author, ContentType.NEWS, "tr", "a".repeat(64), NOW);
		ReflectionTestUtils.setField(content, "id", CONTENT_ID);
		ReflectionTestUtils.setField(content, "status", status);
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
	}

	private ContentImage storedImage() {
		ContentImage image = ContentImage.attach(CONTENT_ID, new byte[] { 1, 2, 3 }, "image/png", 10, 10, 0, NOW);
		ReflectionTestUtils.setField(image, "id", IMAGE_ID);
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.of(image));
		return image;
	}

	// ------------------------------------------------------------------ ekleme

	@Test
	void ownerAddsAnImageToTheDraftInTheFirstFreeSlot() throws IOException {
		content(USER, ContentStatus.DRAFT);
		when(imageRepository.findSortOrdersByContentId(CONTENT_ID)).thenReturn(List.of(0, 2));

		ContentImageResponse response = service.addImage(CONTENT_ID, TestImages.png(40, 20));

		ArgumentCaptor<ContentImage> saved = ArgumentCaptor.forClass(ContentImage.class);
		verify(imageRepository).save(saved.capture());
		assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
		assertThat(saved.getValue().getMimeType()).isEqualTo("image/png");
		assertThat(saved.getValue().getSizeBytes()).isEqualTo(saved.getValue().getContent().length);
		assertThat(response.id()).isEqualTo(IMAGE_ID);
		assertThat(response.contentUrl()).isEqualTo("/api/v1/content/contents/" + CONTENT_ID + "/images/" + IMAGE_ID);
	}

	@Test
	void theImageLimitIsEnforced() throws IOException {
		content(USER, ContentStatus.DRAFT);
		when(imageRepository.findSortOrdersByContentId(CONTENT_ID)).thenReturn(List.of(0, 1, 2, 3, 4, 5));

		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, upload))
				.isInstanceOf(ContentImageLimitExceededException.class);
		verify(imageRepository, never()).save(any());
	}

	@Test
	void invalidFilesAreRejectedAndNothingIsStored() {
		content(USER, ContentStatus.DRAFT);
		when(imageRepository.findSortOrdersByContentId(CONTENT_ID)).thenReturn(List.of());

		byte[] svg = TestImages.svgWithScript();
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, svg)).isInstanceOf(InvalidImageException.class);
		byte[] html = TestImages.html();
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, html)).isInstanceOf(InvalidImageException.class);
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, new byte[0])).isInstanceOf(InvalidImageException.class);
		verify(imageRepository, never()).save(any());
	}

	@Test
	void imagesCannotBeAddedToSomeoneElsesDraft() throws IOException {
		content(OTHER, ContentStatus.DRAFT);

		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, upload)).isInstanceOf(ContentNotFoundException.class);
		verify(imageRepository, never()).save(any());
	}

	@Test
	void imagesCannotBeAddedToSomeoneElsesPublishedContent() throws IOException {
		content(OTHER, ContentStatus.PUBLISHED);

		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, upload)).isInstanceOf(ContentAccessDeniedException.class);
	}

	@Test
	void imagesCannotBeAddedToRemovedOrArchivedContent() throws IOException {
		content(USER, ContentStatus.REMOVED);

		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, upload)).isInstanceOf(ContentNotEditableException.class);
		verify(imageRepository, never()).save(any());
	}

	@Test
	void addingToAMissingOrDeletedContentIsNotFound() throws IOException {
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.empty());

		byte[] upload = TestImages.png(10, 10);
		assertThatThrownBy(() -> service.addImage(CONTENT_ID, upload)).isInstanceOf(ContentNotFoundException.class);
	}

	// ------------------------------------------------------------------ okuma

	@Test
	void ownerReadsTheDraftImageBytes() {
		content(USER, ContentStatus.DRAFT);
		storedImage();

		ContentImageContent image = service.getImage(CONTENT_ID, IMAGE_ID);

		assertThat(image.content()).containsExactly(1, 2, 3);
		assertThat(image.mimeType()).isEqualTo("image/png");
	}

	@Test
	void draftImagesOfAnotherUserAreNotReadable() {
		content(OTHER, ContentStatus.DRAFT);
		storedImage();

		assertThatThrownBy(() -> service.getImage(CONTENT_ID, IMAGE_ID)).isInstanceOf(ContentNotFoundException.class);
	}

	@Test
	void publishedContentImagesAreReadableByAnyUser() {
		content(OTHER, ContentStatus.PUBLISHED);
		storedImage();

		ContentImageContent image = service.getImage(CONTENT_ID, IMAGE_ID);

		assertThat(image.mimeType()).isEqualTo("image/png");
	}

	@Test
	void anImageOfADifferentContentIsNotFound() {
		content(USER, ContentStatus.DRAFT);
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getImage(CONTENT_ID, IMAGE_ID)).isInstanceOf(ContentImageNotFoundException.class);
	}

	// ------------------------------------------------------------------ silme

	@Test
	void ownerDeletesADraftImage() {
		content(USER, ContentStatus.DRAFT);
		ContentImage image = storedImage();

		service.deleteImage(CONTENT_ID, IMAGE_ID);

		verify(imageRepository).delete(image);
	}

	@Test
	void imagesOfSomeoneElsesContentCannotBeDeleted() {
		content(OTHER, ContentStatus.DRAFT);
		storedImage();

		assertThatThrownBy(() -> service.deleteImage(CONTENT_ID, IMAGE_ID)).isInstanceOf(ContentNotFoundException.class);
		verify(imageRepository, never()).delete(any(ContentImage.class));
	}

	@Test
	void imagesOfRemovedContentCannotBeDeleted() {
		content(USER, ContentStatus.REMOVED);
		storedImage();

		assertThatThrownBy(() -> service.deleteImage(CONTENT_ID, IMAGE_ID)).isInstanceOf(ContentNotEditableException.class);
		verify(imageRepository, never()).delete(any(ContentImage.class));
	}

	@Test
	void deletingAMissingImageIsNotFound() {
		content(USER, ContentStatus.DRAFT);
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteImage(CONTENT_ID, IMAGE_ID)).isInstanceOf(ContentImageNotFoundException.class);
	}

	@Test
	void ownerCanAddAndDeleteImagesOfPublishedContent() throws IOException {
		content(USER, ContentStatus.PUBLISHED);
		when(imageRepository.findSortOrdersByContentId(CONTENT_ID)).thenReturn(List.of());
		byte[] upload = TestImages.png(10, 10);

		assertThat(service.addImage(CONTENT_ID, upload).sortOrder()).isZero();

		ContentImage image = storedImage();
		service.deleteImage(CONTENT_ID, IMAGE_ID);
		verify(imageRepository).delete(image);
	}

	@Test
	void guestsReadImagesOfPublishedContentButNeverOfDrafts() {
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		storedImage();
		content(OTHER, ContentStatus.PUBLISHED);
		assertThat(service.getImage(CONTENT_ID, IMAGE_ID).mimeType()).isEqualTo("image/png");

		content(OTHER, ContentStatus.DRAFT);
		assertThatThrownBy(() -> service.getImage(CONTENT_ID, IMAGE_ID)).isInstanceOf(ContentNotFoundException.class);
	}
}
