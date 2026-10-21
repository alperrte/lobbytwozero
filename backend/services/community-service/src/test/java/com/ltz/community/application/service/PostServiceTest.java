package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.community.api.dto.request.CreatePostRequest;
import com.ltz.community.api.dto.request.LinkGameRequest;
import com.ltz.community.api.dto.request.UpdatePostRequest;
import com.ltz.community.api.dto.response.PostResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.AuthenticationRequiredException;
import com.ltz.community.domain.exception.GameCatalogUnavailableException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:15:30Z");
	private static final Instant CREATED_AT = NOW.minusSeconds(3600);
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");

	@Mock
	private PostRepository postRepository;

	@Mock
	private PostImageRepository postImageRepository;

	@Mock
	private CurrentUserPort currentUser;

	@Mock
	private GameCatalogPort gameCatalog;

	private PostService postService;

	@BeforeEach
	void setUp() {
		postService = new PostService(postRepository, postImageRepository, new PostAccess(postRepository),
				new PostMapper(), currentUser, gameCatalog, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Post existingPost(UUID authorId, PostStatus status) {
		Post post = Post.publish(authorId, "Eski içerik", CREATED_AT);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	@Test
	void createPostStoresOwnerFromAuthenticatedUserAndCreationTime() {
		UUID userId = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.createPost(new CreatePostRequest("  Merhaba LTZ  ", null));

		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
		verify(postRepository).save(saved.capture());
		assertThat(saved.getValue().getAuthorId()).isEqualTo(userId);
		assertThat(saved.getValue().getBody()).isEqualTo("Merhaba LTZ");
		assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
		assertThat(response.authorId()).isEqualTo(userId);
		assertThat(response.body()).isEqualTo("Merhaba LTZ");
		assertThat(response.status()).isEqualTo(PostStatus.VISIBLE);
		assertThat(response.createdAt()).isEqualTo(NOW);
	}

	@Test
	void createPostWithoutGameDoesNotCallGameCatalog() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.createPost(new CreatePostRequest("Merhaba", null));

		assertThat(response.gameId()).isNull();
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void createPostLinksVerifiedGame() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.createPost(new CreatePostRequest("Merhaba", GAME_ID));

		assertThat(response.gameId()).isEqualTo(GAME_ID);
	}

	@Test
	void createPostWithUnknownGameIsRejectedAndStoresNothing() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(false);

		CreatePostRequest request = new CreatePostRequest("Merhaba", GAME_ID);

		assertThatThrownBy(() -> postService.createPost(request))
				.isInstanceOf(GameNotAvailableException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void createPostFailsClosedWhenGameCatalogIsUnavailable() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(gameCatalog.isAvailable(GAME_ID)).thenThrow(new GameCatalogUnavailableException(new IOException("down")));

		CreatePostRequest request = new CreatePostRequest("Merhaba", GAME_ID);

		assertThatThrownBy(() -> postService.createPost(request))
				.isInstanceOf(GameCatalogUnavailableException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void linkGameSetsVerifiedGameForOwner() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.VISIBLE);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(postRepository.save(post)).thenReturn(post);

		PostResponse response = postService.linkGame(POST_ID, new LinkGameRequest(GAME_ID));

		assertThat(response.gameId()).isEqualTo(GAME_ID);
		assertThat(response.updatedAt()).isEqualTo(NOW);
	}

	@Test
	void linkingTheSameGameAgainSkipsCatalogCallAndKeepsUpdatedAt() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.VISIBLE);
		post.linkGame(GAME_ID, CREATED_AT);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		PostResponse response = postService.linkGame(POST_ID, new LinkGameRequest(GAME_ID));

		assertThat(response.gameId()).isEqualTo(GAME_ID);
		verifyNoInteractions(gameCatalog);
		verify(postRepository, never()).save(any());
	}

	@Test
	void linkGameRejectsUnknownGameAndKeepsPost() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.VISIBLE);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(false);

		LinkGameRequest request = new LinkGameRequest(GAME_ID);

		assertThatThrownBy(() -> postService.linkGame(POST_ID, request))
				.isInstanceOf(GameNotAvailableException.class);

		assertThat(post.getGameId()).isNull();
		verify(postRepository, never()).save(any());
	}

	@Test
	void linkGameChecksOwnershipBeforeCallingGameCatalog() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findById(POST_ID))
				.thenReturn(Optional.of(existingPost(UUID.randomUUID(), PostStatus.VISIBLE)));

		LinkGameRequest request = new LinkGameRequest(GAME_ID);

		assertThatThrownBy(() -> postService.linkGame(POST_ID, request))
				.isInstanceOf(PostAccessDeniedException.class);

		verifyNoInteractions(gameCatalog);
	}

	@Test
	void linkGameIsRejectedForHiddenPostOfOwner() {
		UUID owner = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(existingPost(owner, PostStatus.HIDDEN)));

		LinkGameRequest request = new LinkGameRequest(GAME_ID);

		assertThatThrownBy(() -> postService.linkGame(POST_ID, request))
				.isInstanceOf(PostNotEditableException.class);

		verifyNoInteractions(gameCatalog);
	}

	@Test
	void unlinkGameClearsGameForOwner() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.VISIBLE);
		post.linkGame(GAME_ID, CREATED_AT);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		postService.unlinkGame(POST_ID);

		assertThat(post.getGameId()).isNull();
		assertThat(post.getUpdatedAt()).isEqualTo(NOW);
		verify(postRepository).save(post);
		verifyNoInteractions(gameCatalog);
	}

	@Test
	void unlinkGameByAnotherUserIsForbidden() {
		Post post = existingPost(UUID.randomUUID(), PostStatus.VISIBLE);
		post.linkGame(GAME_ID, CREATED_AT);
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		assertThatThrownBy(() -> postService.unlinkGame(POST_ID)).isInstanceOf(PostAccessDeniedException.class);

		assertThat(post.getGameId()).isEqualTo(GAME_ID);
	}

	@Test
	void createPostFailsWithoutAuthenticatedUserAndStoresNothing() {
		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));

		CreatePostRequest request = new CreatePostRequest("Merhaba", null);

		assertThatThrownBy(() -> postService.createPost(request))
				.isInstanceOf(AuthenticationRequiredException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void updatePostChangesBodyAndUpdatedAtForOwner() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.VISIBLE);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(postRepository.save(post)).thenReturn(post);

		PostResponse response = postService.updatePost(POST_ID, new UpdatePostRequest("  Yeni içerik "));

		assertThat(response.body()).isEqualTo("Yeni içerik");
		assertThat(response.authorId()).isEqualTo(owner);
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
		assertThat(response.updatedAt()).isEqualTo(NOW);
	}

	@Test
	void updatePostWithIdenticalBodyKeepsUpdatedAt() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.VISIBLE);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(postRepository.save(post)).thenReturn(post);

		PostResponse response = postService.updatePost(POST_ID, new UpdatePostRequest("Eski içerik"));

		assertThat(response.updatedAt()).isEqualTo(CREATED_AT);
	}

	@Test
	void updatePostByAnotherUserIsForbiddenAndChangesNothing() {
		Post post = existingPost(UUID.randomUUID(), PostStatus.VISIBLE);
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		UpdatePostRequest request = new UpdatePostRequest("Ele geçirme");

		assertThatThrownBy(() -> postService.updatePost(POST_ID, request))
				.isInstanceOf(PostAccessDeniedException.class);

		assertThat(post.getBody()).isEqualTo("Eski içerik");
		verify(postRepository, never()).save(any());
	}

	@Test
	void updateMissingPostIsNotFound() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

		UpdatePostRequest request = new UpdatePostRequest("Yeni");

		assertThatThrownBy(() -> postService.updatePost(POST_ID, request))
				.isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void updateDeletedPostIsNotFoundEvenForOwner() {
		UUID owner = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(existingPost(owner, PostStatus.DELETED)));

		UpdatePostRequest request = new UpdatePostRequest("Yeni");

		assertThatThrownBy(() -> postService.updatePost(POST_ID, request))
				.isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void hiddenPostIsNotFoundForAnotherUser() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findById(POST_ID))
				.thenReturn(Optional.of(existingPost(UUID.randomUUID(), PostStatus.HIDDEN)));

		UpdatePostRequest request = new UpdatePostRequest("Yeni");

		assertThatThrownBy(() -> postService.updatePost(POST_ID, request))
				.isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void deletePostSoftDeletesOwnPost() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.VISIBLE);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

		postService.deletePost(POST_ID);

		verify(postRepository).save(post);
		verify(postImageRepository).deleteByPostId(POST_ID);
		assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
		assertThat(post.getDeletedAt()).isEqualTo(NOW);
		assertThat(post.getUpdatedAt()).isEqualTo(NOW);
		assertThat(post.getBody()).isEqualTo("Eski içerik");
		verify(postRepository, never()).delete(any());
		verify(postRepository, never()).deleteById(any());
	}

	@Test
	void ownerCanDeleteOwnHiddenPost() {
		UUID owner = UUID.randomUUID();
		Post post = existingPost(owner, PostStatus.HIDDEN);
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

		postService.deletePost(POST_ID);

		assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
	}

	@Test
	void deletePostByAnotherUserIsForbiddenAndKeepsPost() {
		Post post = existingPost(UUID.randomUUID(), PostStatus.VISIBLE);
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

		assertThatThrownBy(() -> postService.deletePost(POST_ID)).isInstanceOf(PostAccessDeniedException.class);

		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		assertThat(post.getDeletedAt()).isNull();
		verify(postRepository, never()).save(any());
		verify(postImageRepository, never()).deleteByPostId(any());
	}

	@Test
	void deleteMissingOrAlreadyDeletedPostIsNotFound() {
		UUID owner = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.empty())
				.thenReturn(Optional.of(existingPost(owner, PostStatus.DELETED)));

		assertThatThrownBy(() -> postService.deletePost(POST_ID)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> postService.deletePost(POST_ID)).isInstanceOf(PostNotFoundException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void deleteHiddenPostOfAnotherUserIsNotFound() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(postRepository.findByIdForUpdate(POST_ID))
				.thenReturn(Optional.of(existingPost(UUID.randomUUID(), PostStatus.HIDDEN)));

		assertThatThrownBy(() -> postService.deletePost(POST_ID)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void hiddenPostCannotBeEditedByItsOwner() {
		UUID owner = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(owner);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(existingPost(owner, PostStatus.HIDDEN)));

		UpdatePostRequest request = new UpdatePostRequest("Yeni");

		assertThatThrownBy(() -> postService.updatePost(POST_ID, request))
				.isInstanceOf(PostNotEditableException.class);

		verify(postRepository, never()).save(any());
	}
}
