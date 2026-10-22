package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.community.api.dto.response.PostImageContent;
import com.ltz.community.api.dto.response.PostImageResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.service.ImageSanitizer.SanitizedImage;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.InvalidImageException;
import com.ltz.community.domain.exception.InvalidImageException.Reason;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.PostImageLimitExceededException;
import com.ltz.community.domain.exception.PostImageNotFoundException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:15:30Z");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID IMAGE_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final byte[] UPLOAD = { 1, 2, 3 };
	private static final byte[] CLEAN = { 9, 9 };

	@Mock
	private PostRepository postRepository;

	@Mock
	private PostImageRepository postImageRepository;

	@Mock
	private ImageSanitizer imageSanitizer;

	@Mock
	private CurrentUserPort currentUser;

	private PostImageService service;

	@BeforeEach
	void setUp() {
		service = new PostImageService(postImageRepository, new PostAccess(postRepository), imageSanitizer,
				new PostMapper(), currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Post post(UUID authorId, PostStatus status) {
		Post post = Post.publish(authorId, "içerik", NOW.minusSeconds(60));
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	private PostImage storedImage() {
		PostImage image = PostImage.attach(POST_ID, CLEAN, "image/png", 10, 10, 0, NOW);
		ReflectionTestUtils.setField(image, "id", IMAGE_ID);
		return image;
	}

	private void stubOwnerUpload(UUID owner, List<Integer> usedSlots) {
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(owner, PostStatus.VISIBLE)));
		when(postImageRepository.findSortOrdersByPostId(POST_ID)).thenReturn(usedSlots);
	}

	@Test
	void addImageStoresSanitizedContentInFirstFreeSlot() {
		UUID owner = UUID.randomUUID();
		stubOwnerUpload(owner, List.of(0, 2));
		when(imageSanitizer.sanitize(UPLOAD)).thenReturn(new SanitizedImage(CLEAN, "image/png", 30, 20));
		when(postImageRepository.save(any(PostImage.class))).thenAnswer(invocation -> {
			PostImage saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", IMAGE_ID);
			return saved;
		});

		PostImageResponse response = service.addImage(POST_ID, UPLOAD);

		ArgumentCaptor<PostImage> saved = ArgumentCaptor.forClass(PostImage.class);
		verify(postImageRepository).save(saved.capture());
		assertThat(saved.getValue().getContent()).isEqualTo(CLEAN);
		assertThat(saved.getValue().getMimeType()).isEqualTo("image/png");
		assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
		assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
		assertThat(response.id()).isEqualTo(IMAGE_ID);
		assertThat(response.sizeBytes()).isEqualTo(CLEAN.length);
		assertThat(response.width()).isEqualTo(30);
		assertThat(response.height()).isEqualTo(20);
		assertThat(response.contentUrl()).isEqualTo("/api/v1/community/posts/" + POST_ID + "/images/" + IMAGE_ID);
	}

	@Test
	void firstImageGetsSlotZero() {
		stubOwnerUpload(UUID.randomUUID(), List.of());
		when(imageSanitizer.sanitize(UPLOAD)).thenReturn(new SanitizedImage(CLEAN, "image/jpeg", 5, 5));
		when(postImageRepository.save(any(PostImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostImageResponse response = service.addImage(POST_ID, UPLOAD);

		assertThat(response.sortOrder()).isZero();
	}

	@Test
	void addImageIsRejectedWhenLimitIsReachedWithoutProcessingUpload() {
		stubOwnerUpload(UUID.randomUUID(), List.of(0, 1, 2, 3));

		assertThatThrownBy(() -> service.addImage(POST_ID, UPLOAD)).isInstanceOf(PostImageLimitExceededException.class);

		verify(imageSanitizer, never()).sanitize(any());
		verify(postImageRepository, never()).save(any());
	}

	@Test
	void invalidUploadIsNotStored() {
		stubOwnerUpload(UUID.randomUUID(), List.of());
		when(imageSanitizer.sanitize(UPLOAD)).thenThrow(new InvalidImageException(Reason.UNSUPPORTED_TYPE));

		assertThatThrownBy(() -> service.addImage(POST_ID, UPLOAD)).isInstanceOf(InvalidImageException.class);

		verify(postImageRepository, never()).save(any());
	}

	@Test
	void anotherUserCannotAddImage() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findByIdForUpdate(POST_ID))
				.thenReturn(Optional.of(post(UUID.randomUUID(), PostStatus.VISIBLE)));

		assertThatThrownBy(() -> service.addImage(POST_ID, UPLOAD)).isInstanceOf(PostAccessDeniedException.class);

		verify(imageSanitizer, never()).sanitize(any());
		verify(postImageRepository, never()).save(any());
	}

	@Test
	void imageCannotBeAddedToMissingOrHiddenPost() {
		UUID owner = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.empty())
				.thenReturn(Optional.of(post(owner, PostStatus.HIDDEN)));

		assertThatThrownBy(() -> service.addImage(POST_ID, UPLOAD)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> service.addImage(POST_ID, UPLOAD)).isInstanceOf(PostNotEditableException.class);

		verify(postImageRepository, never()).save(any());
	}

	@Test
	void anyAuthenticatedUserCanViewImageOfVisiblePost() {
		when(currentUser.findUserId()).thenReturn(Optional.of(UUID.randomUUID()));
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(UUID.randomUUID(), PostStatus.VISIBLE)));
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.of(storedImage()));

		PostImageContent content = service.getImage(POST_ID, IMAGE_ID);

		assertThat(content.content()).isEqualTo(CLEAN);
		assertThat(content.mimeType()).isEqualTo("image/png");
	}

	@Test
	void hiddenPostImageIsVisibleToOwnerOnly() {
		UUID owner = UUID.randomUUID();
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(owner, PostStatus.HIDDEN)));
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.of(storedImage()));

		when(currentUser.findUserId()).thenReturn(Optional.of(owner));
		assertThat(service.getImage(POST_ID, IMAGE_ID).content()).isEqualTo(CLEAN);

		when(currentUser.findUserId()).thenReturn(Optional.of(UUID.randomUUID()));
		assertThatThrownBy(() -> service.getImage(POST_ID, IMAGE_ID)).isInstanceOf(PostNotFoundException.class);

		when(currentUser.findUserId()).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getImage(POST_ID, IMAGE_ID)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void anonymousVisitorCanViewImageOfVisiblePost() {
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(UUID.randomUUID(), PostStatus.VISIBLE)));
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.of(storedImage()));

		assertThat(service.getImage(POST_ID, IMAGE_ID).content()).isEqualTo(CLEAN);
	}

	@Test
	void imageOfDeletedPostIsNotFoundEvenForOwner() {
		UUID owner = UUID.randomUUID();
		when(currentUser.findUserId()).thenReturn(Optional.of(owner));
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(owner, PostStatus.DELETED)));

		assertThatThrownBy(() -> service.getImage(POST_ID, IMAGE_ID)).isInstanceOf(PostNotFoundException.class);

		verify(postImageRepository, never()).findByIdAndPostId(any(), any());
	}

	@Test
	void imageFromAnotherPostIsNotFound() {
		when(currentUser.findUserId()).thenReturn(Optional.of(UUID.randomUUID()));
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(UUID.randomUUID(), PostStatus.VISIBLE)));
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getImage(POST_ID, IMAGE_ID)).isInstanceOf(PostImageNotFoundException.class);
	}

	@Test
	void ownerDeletesImage() {
		UUID owner = UUID.randomUUID();
		PostImage image = storedImage();
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(owner, PostStatus.VISIBLE)));
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.of(image));

		service.deleteImage(POST_ID, IMAGE_ID);

		verify(postImageRepository).delete(image);
	}

	@Test
	void anotherUserCannotDeleteImage() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findByIdForUpdate(POST_ID))
				.thenReturn(Optional.of(post(UUID.randomUUID(), PostStatus.VISIBLE)));

		assertThatThrownBy(() -> service.deleteImage(POST_ID, IMAGE_ID)).isInstanceOf(PostAccessDeniedException.class);

		verify(postImageRepository, never()).delete(any());
	}

	@Test
	void deletingUnknownImageIsNotFound() {
		UUID owner = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post(owner, PostStatus.VISIBLE)));
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteImage(POST_ID, IMAGE_ID)).isInstanceOf(PostImageNotFoundException.class);
	}
}
