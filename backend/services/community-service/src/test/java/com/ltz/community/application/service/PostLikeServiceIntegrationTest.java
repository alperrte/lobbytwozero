package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.response.LikeStateResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

import jakarta.persistence.EntityManager;

/** Beğeni akışını gerçek PostgreSQL üzerinde çalıştırır: idempotentlik, sayaç doğruluğu, eşzamanlılık, likedByMe. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostLikeService.class, PostFeedService.class, PostAccess.class, PostViewAssembler.class, PostMapper.class,
		ClockConfig.class })
class PostLikeServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
	private static final ThreadLocal<UUID> CALLER = new ThreadLocal<>();

	@MockitoBean
	private CurrentUserPort currentUser;

	@Autowired
	private PostLikeService likes;

	@Autowired
	private PostFeedService feed;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	/** Mock tek kez ve iş parçacığı başına kimlik verecek şekilde kurulur (Mockito stub'ı eşzamanlı değiştirilemez). */
	@BeforeEach
	void stubCurrentUser() {
		when(currentUser.requireUserId()).thenAnswer(invocation -> CALLER.get());
		when(currentUser.findUserId()).thenAnswer(invocation -> Optional.ofNullable(CALLER.get()));
	}

	private static void actAs(UUID userId) {
		CALLER.set(userId);
	}

	private Post savePost(PostStatus status) {
		Post post = Post.publish(UUID.randomUUID(), "gönderi", NOW);
		if (status != PostStatus.VISIBLE) {
			ReflectionTestUtils.setField(post, "status", status);
			if (status == PostStatus.DELETED) {
				ReflectionTestUtils.setField(post, "deletedAt", NOW);
			}
		}
		return postRepository.saveAndFlush(post);
	}

	private int likeRows(UUID postId) {
		return jdbcTemplate.queryForObject("select count(*) from post_likes where post_id = ?", Integer.class, postId);
	}

	@AfterEach
	void cleanUp() {
		CALLER.remove();
	}

	@Test
	void likeIsIdempotentAndCountsOncePerUser() {
		Post post = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());

		LikeStateResponse first = likes.like(post.getId());
		LikeStateResponse again = likes.like(post.getId());

		assertThat(first).isEqualTo(new LikeStateResponse(post.getId(), true, 1));
		assertThat(again).isEqualTo(first);
		assertThat(likeRows(post.getId())).isEqualTo(1);
	}

	@Test
	void differentUsersAddUpAndUnlikeRemovesOnlyOwnLike() {
		Post post = savePost(PostStatus.VISIBLE);
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();

		actAs(alice);
		likes.like(post.getId());
		actAs(bob);
		LikeStateResponse bothLiked = likes.like(post.getId());
		assertThat(bothLiked.likeCount()).isEqualTo(2);

		LikeStateResponse afterUnlike = likes.unlike(post.getId());
		assertThat(afterUnlike).isEqualTo(new LikeStateResponse(post.getId(), false, 1));
		assertThat(likes.unlike(post.getId()).likeCount()).isEqualTo(1);
		assertThat(likeRows(post.getId())).isEqualTo(1);
	}

	@Test
	void unlikeWithoutPriorLikeNeverGoesBelowZero() {
		Post post = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());

		assertThat(likes.unlike(post.getId())).isEqualTo(new LikeStateResponse(post.getId(), false, 0));
	}

	@Test
	void hiddenDeletedAndUnknownPostsCannotBeLiked() {
		Post hidden = savePost(PostStatus.HIDDEN);
		Post deleted = savePost(PostStatus.DELETED);
		UUID hiddenId = hidden.getId();
		UUID deletedId = deleted.getId();
		UUID unknownId = UUID.randomUUID();
		// Gizli gönderinin sahibi bile gizli gönderiyi beğenemez; başkası için gönderi hiç görünmez.
		actAs(hidden.getAuthorId());
		assertThatThrownBy(() -> likes.like(hiddenId)).isInstanceOf(PostNotFoundException.class);
		actAs(UUID.randomUUID());
		assertThatThrownBy(() -> likes.like(hiddenId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> likes.like(deletedId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> likes.like(unknownId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> likes.unlike(hiddenId)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void likedByMeReflectsOnlyTheCallersLikesInFeedAndDetail() {
		Post liked = savePost(PostStatus.VISIBLE);
		Post other = savePost(PostStatus.VISIBLE);
		UUID alice = UUID.randomUUID();
		actAs(alice);
		likes.like(liked.getId());
		entityManager.clear();

		List<PostViewResponse> items = feed.listPosts(new PostFeedRequest(null, null, null, null, null, null, PostSort.NEWEST, null, null))
				.items();
		assertThat(items).filteredOn(PostViewResponse::likedByMe).extracting(PostViewResponse::id)
				.containsExactly(liked.getId());
		assertThat(feed.getPost(other.getId()).likedByMe()).isFalse();
		assertThat(feed.getPost(liked.getId()).likeCount()).isEqualTo(1);

		actAs(UUID.randomUUID());
		assertThat(feed.getPost(liked.getId()).likedByMe()).isFalse();
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		assertThat(feed.getPost(liked.getId()).likedByMe()).isFalse();
	}

	@Test
	void likesDriveThePopularSortWithoutManualCounterUpdates() {
		Post quiet = savePost(PostStatus.VISIBLE);
		Post popular = savePost(PostStatus.VISIBLE);
		for (int i = 0; i < 3; i++) {
			actAs(UUID.randomUUID());
			likes.like(popular.getId());
		}
		entityManager.clear();
		when(currentUser.findUserId()).thenReturn(Optional.empty());

		List<UUID> order = feed.listPosts(new PostFeedRequest(null, null, null, null, null, null, PostSort.POPULAR, null, null))
				.items().stream().map(PostViewResponse::id).toList();

		assertThat(order).containsExactly(popular.getId(), quiet.getId());
	}

	@Test
	void editingAPostDoesNotOverwriteTheLikeCounter() {
		Post post = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());
		likes.like(post.getId());

		Post reloaded = postRepository.findById(post.getId()).orElseThrow();
		reloaded.editBody("düzenlendi", NOW.plusSeconds(1));
		postRepository.saveAndFlush(reloaded);
		entityManager.clear();

		assertThat(postRepository.findById(post.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentLikesFromDifferentUsersAreAllCounted() throws Exception {
		Post post = savePost(PostStatus.VISIBLE);
		try {
			int users = 12;
			runConcurrently(users, () -> {
				actAs(UUID.randomUUID());
				return likes.like(post.getId());
			});

			assertThat(postRepository.findLikeCountById(post.getId())).contains(users);
			assertThat(likeRows(post.getId())).isEqualTo(users);
		} finally {
			deleteTestData(post);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentLikesFromTheSameUserCreateOneLike() throws Exception {
		Post post = savePost(PostStatus.VISIBLE);
		UUID user = UUID.randomUUID();
		try {
			runConcurrently(10, () -> {
				actAs(user);
				return likes.like(post.getId());
			});

			assertThat(postRepository.findLikeCountById(post.getId())).contains(1);
			assertThat(likeRows(post.getId())).isEqualTo(1);
		} finally {
			deleteTestData(post);
		}
	}

	private void deleteTestData(Post post) {
		jdbcTemplate.update("delete from post_likes where post_id = ?", post.getId());
		jdbcTemplate.update("delete from posts where id = ?", post.getId());
	}

	private static void runConcurrently(int threads, Callable<LikeStateResponse> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<LikeStateResponse>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(task));
			}
			for (Future<LikeStateResponse> result : results) {
				result.get();
			}
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(30, TimeUnit.SECONDS);
		}
	}
}
