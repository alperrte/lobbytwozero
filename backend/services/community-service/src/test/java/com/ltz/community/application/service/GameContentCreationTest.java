package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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
import com.ltz.community.application.port.GameContentQuotaPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.GameContentType;
import com.ltz.community.domain.exception.AuthenticationRequiredException;
import com.ltz.community.domain.exception.GameCatalogUnavailableException;
import com.ltz.community.domain.exception.GameContentRuleException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.TooManyGameContentException;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/** Oyuna bağlı içerik (GAME-11) yazma kuralları: zorunluluklar, kota, oyun doğrulaması, sahiplik ve tür değişmezliği. */
@ExtendWith(MockitoExtension.class)
class GameContentCreationTest {

	private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER_GAME_ID = UUID.fromString("0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66");

	@Mock
	private PostRepository postRepository;

	@Mock
	private PostImageRepository postImageRepository;

	@Mock
	private ForumRepository forumRepository;

	@Mock
	private BlockPolicy blockPolicy;

	@Mock
	private CurrentUserPort currentUser;

	@Mock
	private GameCatalogPort gameCatalog;

	@Mock
	private GameContentQuotaPort gameContentQuota;

	private PostService postService;
	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		postService = new PostService(postRepository, postImageRepository, forumRepository,
				new PostAccess(postRepository, forumRepository, blockPolicy), new PostMapper(), currentUser, gameCatalog,
				gameContentQuota, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static CreatePostRequest request(UUID gameId, UUID forumId, GameContentType type, String title) {
		return new CreatePostRequest("  rehber metni  ", gameId, null, forumId, type, title);
	}

	private Post existing(UUID authorId, GameContentType type) {
		Post post = type == null ? Post.publish(authorId, "içerik", GAME_ID, NOW.minusSeconds(60))
				: Post.publishGameContent(authorId, GAME_ID, type, "Başlık", "içerik", NOW.minusSeconds(60));
		ReflectionTestUtils.setField(post, "id", POST_ID);
		return post;
	}

	// ------------------------------------------------------------------ oluşturma

	@Test
	void createsGameContentOwnedByTheCallerAfterTheCatalogConfirmsTheGame() {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(gameContentQuota.saveWithinQuota(any(Post.class), any(Instant.class), anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.createPost(request(GAME_ID, null, GameContentType.EASTER_EGGS, "  Gizli oda  "));

		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
		verify(gameContentQuota).saveWithinQuota(saved.capture(), any(Instant.class), anyInt());
		Post post = saved.getValue();
		assertThat(post.getAuthorId()).isEqualTo(userId);
		assertThat(post.getGameId()).isEqualTo(GAME_ID);
		assertThat(post.getContentType()).isEqualTo(GameContentType.EASTER_EGGS);
		assertThat(post.getTitle()).isEqualTo("Gizli oda");
		assertThat(post.getBody()).isEqualTo("rehber metni");
		assertThat(post.getForumId()).isNull();
		assertThat(response.contentType()).isEqualTo(GameContentType.EASTER_EGGS);
		assertThat(response.title()).isEqualTo("Gizli oda");
	}

	@Test
	void rejectsGameContentWithoutAGameBeforeAnyExternalCall() {
		when(currentUser.requireUserId()).thenReturn(userId);

		assertThatThrownBy(() -> postService.createPost(request(null, null, GameContentType.GUIDES, "Başlık")))
				.isInstanceOf(GameContentRuleException.class);

		verifyNoInteractions(gameCatalog);
		verify(postRepository, never()).save(any());
	}

	@Test
	void rejectsGameContentWithoutAUsableTitle() {
		when(currentUser.requireUserId()).thenReturn(userId);

		assertThatThrownBy(() -> postService.createPost(request(GAME_ID, null, GameContentType.GUIDES, null)))
				.isInstanceOf(GameContentRuleException.class);
		assertThatThrownBy(() -> postService.createPost(request(GAME_ID, null, GameContentType.GUIDES, "   ")))
				.isInstanceOf(GameContentRuleException.class);

		verifyNoInteractions(gameCatalog);
		verify(postRepository, never()).save(any());
	}

	@Test
	void rejectsGameContentAsAForumTopic() {
		when(currentUser.requireUserId()).thenReturn(userId);

		assertThatThrownBy(() -> postService.createPost(request(GAME_ID, UUID.randomUUID(), GameContentType.GUIDES, "Başlık")))
				.isInstanceOf(GameContentRuleException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void rejectsATitleOnAPostWithoutAContentType() {
		when(currentUser.requireUserId()).thenReturn(userId);

		assertThatThrownBy(() -> postService.createPost(request(GAME_ID, null, null, "Başlık")))
				.isInstanceOf(GameContentRuleException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void rejectsAnUnknownGameAndSurfacesACatalogOutageInsteadOfSaving() {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(false);
		when(gameCatalog.isAvailable(OTHER_GAME_ID)).thenThrow(new GameCatalogUnavailableException(new RuntimeException("down")));

		assertThatThrownBy(() -> postService.createPost(request(GAME_ID, null, GameContentType.GUIDES, "Başlık")))
				.isInstanceOf(GameNotAvailableException.class);
		assertThatThrownBy(() -> postService.createPost(request(OTHER_GAME_ID, null, GameContentType.GUIDES, "Başlık")))
				.isInstanceOf(GameCatalogUnavailableException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void stopsAtTheHourlyQuotaBeforeCallingTheCatalog() {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.countByAuthorIdAndContentTypeIsNotNullAndCreatedAtAfter(userId, NOW.minus(Duration.ofHours(1))))
				.thenReturn((long) PostService.MAX_GAME_CONTENT_PER_HOUR);

		assertThatThrownBy(() -> postService.createPost(request(GAME_ID, null, GameContentType.GUIDES, "Başlık")))
				.isInstanceOf(TooManyGameContentException.class);

		verifyNoInteractions(gameCatalog);
		verify(postRepository, never()).save(any());
	}

	@Test
	void staysBelowTheQuotaOneShortOfTheLimit() {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.countByAuthorIdAndContentTypeIsNotNullAndCreatedAtAfter(userId, NOW.minus(Duration.ofHours(1))))
				.thenReturn(PostService.MAX_GAME_CONTENT_PER_HOUR - 1L);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(gameContentQuota.saveWithinQuota(any(Post.class), any(Instant.class), anyInt()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(postService.createPost(request(GAME_ID, null, GameContentType.EXPERIENCES, "Başlık")).contentType())
				.isEqualTo(GameContentType.EXPERIENCES);
	}

	@Test
	void anonymousCallersCannotCreateGameContent() {
		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));

		assertThatThrownBy(() -> postService.createPost(request(GAME_ID, null, GameContentType.GUIDES, "Başlık")))
				.isInstanceOf(AuthenticationRequiredException.class);

		verify(postRepository, never()).save(any());
	}

	// ------------------------------------------------------------------ düzenleme ve oyun bağlantısı

	@Test
	void ownerEditsTheTitleOfGameContent() {
		Post post = existing(userId, GameContentType.GUIDES);
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.updatePost(POST_ID, new UpdatePostRequest("yeni metin", null, "  Yeni başlık "));

		assertThat(response.title()).isEqualTo("Yeni başlık");
		assertThat(response.contentType()).isEqualTo(GameContentType.GUIDES);
		assertThat(response.updatedAt()).isEqualTo(NOW);
	}

	@Test
	void omittedTitleKeepsTheCurrentOne() {
		Post post = existing(userId, GameContentType.GUIDES);
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(postService.updatePost(POST_ID, new UpdatePostRequest("yeni metin", null, null)).title())
				.isEqualTo("Başlık");
	}

	@Test
	void sameTitleDoesNotTouchUpdatedAt() {
		Post post = existing(userId, GameContentType.GUIDES);
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.updatePost(POST_ID, new UpdatePostRequest("içerik", null, "Başlık"));

		assertThat(response.updatedAt()).isEqualTo(NOW.minusSeconds(60));
	}

	@Test
	void titleCannotBeSetOnAnOrdinaryPostOrBlanked() {
		Post ordinary = existing(userId, null);
		Post typed = existing(userId, GameContentType.GUIDES);
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(ordinary)).thenReturn(Optional.of(typed));

		assertThatThrownBy(() -> postService.updatePost(POST_ID, new UpdatePostRequest("metin", null, "Başlık")))
				.isInstanceOf(GameContentRuleException.class);
		assertThatThrownBy(() -> postService.updatePost(POST_ID, new UpdatePostRequest("metin", null, "   ")))
				.isInstanceOf(GameContentRuleException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void nonOwnersCannotEditGameContent() {
		Post post = existing(UUID.randomUUID(), GameContentType.GUIDES);
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		assertThatThrownBy(() -> postService.updatePost(POST_ID, new UpdatePostRequest("ele geçir", null, "Başlık")))
				.isInstanceOf(PostAccessDeniedException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void gameLinkOfGameContentCanChangeButNeverBeRemoved() {
		Post post = existing(userId, GameContentType.GUIDES);
		when(currentUser.requireUserId()).thenReturn(userId);
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
		when(gameCatalog.isAvailable(OTHER_GAME_ID)).thenReturn(true);
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(postService.linkGame(POST_ID, new LinkGameRequest(OTHER_GAME_ID)).gameId()).isEqualTo(OTHER_GAME_ID);
		assertThatThrownBy(() -> postService.unlinkGame(POST_ID)).isInstanceOf(GameContentRuleException.class);
	}
}
