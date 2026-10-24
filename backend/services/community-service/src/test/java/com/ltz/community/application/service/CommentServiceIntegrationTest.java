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

import com.ltz.community.api.dto.request.CommentPageRequest;
import com.ltz.community.api.dto.request.CreateCommentRequest;
import com.ltz.community.api.dto.request.UpdateCommentRequest;
import com.ltz.community.api.dto.response.CommentPageResponse;
import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.application.mapper.CommentMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.CommentStatus;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.CommentAccessDeniedException;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.InvalidPostQueryException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

import jakarta.persistence.EntityManager;

/** Yorum, düzenleme/silme ve yanıt akışını gerçek PostgreSQL üzerinde çalıştırır (COM-07, COM-08, COM-09). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ CommentService.class, CommentFeedService.class, PostAccess.class, CommentMapper.class, ClockConfig.class })
class CommentServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
	private static final ThreadLocal<UUID> CALLER = new ThreadLocal<>();
	private static final CommentPageRequest FIRST_PAGE = new CommentPageRequest(null, null);
	private static final CommentPageRequest BROKEN_CURSOR_PAGE = new CommentPageRequest(null, "bozuk");
	private static final UpdateCommentRequest HIJACK_EDIT = new UpdateCommentRequest("ele geçirildi");
	private static final UpdateCommentRequest AGAIN_EDIT = new UpdateCommentRequest("tekrar");

	@MockitoBean
	private CurrentUserPort currentUser;

	@Autowired
	private CommentService comments;

	@Autowired
	private CommentFeedService feed;

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

	@AfterEach
	void cleanUp() {
		CALLER.remove();
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

	private CommentResponse write(UUID postId, String body, UUID parentId) {
		return comments.createComment(postId, new CreateCommentRequest(body, parentId));
	}

	private int commentCount(UUID postId) {
		entityManager.clear();
		return postRepository.findById(postId).orElseThrow().getCommentCount();
	}

	private static CommentPageRequest page(Integer limit, String cursor) {
		return new CommentPageRequest(limit, cursor);
	}

	private static List<String> bodies(CommentPageResponse page) {
		return page.items().stream().map(CommentResponse::body).toList();
	}

	@Test
	void authenticatedUserCommentsOnAVisiblePostAndCounterIncreases() {
		Post post = savePost(PostStatus.VISIBLE);
		UUID author = UUID.randomUUID();
		actAs(author);

		CommentResponse created = write(post.getId(), "  harika oyun  ", null);

		assertThat(created.id()).isNotNull();
		assertThat(created.postId()).isEqualTo(post.getId());
		assertThat(created.authorId()).isEqualTo(author);
		assertThat(created.parentCommentId()).isNull();
		assertThat(created.body()).isEqualTo("harika oyun");
		assertThat(created.status()).isEqualTo(CommentStatus.VISIBLE);
		assertThat(created.replyCount()).isZero();
		assertThat(commentCount(post.getId())).isEqualTo(1);
	}

	@Test
	void cannotCommentOnHiddenDeletedOrUnknownPosts() {
		Post hidden = savePost(PostStatus.HIDDEN);
		Post deleted = savePost(PostStatus.DELETED);
		UUID hiddenId = hidden.getId();
		UUID deletedId = deleted.getId();
		UUID unknownId = UUID.randomUUID();
		// Gizli gönderinin sahibi bile gizli gönderiye etkileşim ekleyemez.
		actAs(hidden.getAuthorId());
		assertThatThrownBy(() -> write(hiddenId, "yorum", null)).isInstanceOf(PostNotFoundException.class);
		actAs(UUID.randomUUID());
		assertThatThrownBy(() -> write(hiddenId, "yorum", null)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> write(deletedId, "yorum", null)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> write(unknownId, "yorum", null)).isInstanceOf(PostNotFoundException.class);
		assertThat(commentCount(hiddenId)).isZero();
	}

	@Test
	void repliesKeepTheirParentRelationAtAnyDepth() {
		Post post = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());
		CommentResponse root = write(post.getId(), "kök", null);
		CommentResponse reply = write(post.getId(), "yanıt", root.id());
		CommentResponse nested = write(post.getId(), "yanıta yanıt", reply.id());
		CommentResponse deeper = write(post.getId(), "daha derin", nested.id());

		assertThat(reply.parentCommentId()).isEqualTo(root.id());
		assertThat(nested.parentCommentId()).isEqualTo(reply.id());
		assertThat(deeper.parentCommentId()).isEqualTo(nested.id());
		assertThat(commentCount(post.getId())).isEqualTo(4);

		CommentPageResponse topLevel = feed.listComments(post.getId(), FIRST_PAGE);
		assertThat(topLevel.items()).extracting(CommentResponse::id).containsExactly(root.id());
		assertThat(topLevel.items().getFirst().replyCount()).isEqualTo(1);
		CommentPageResponse rootReplies = feed.listReplies(root.id(), FIRST_PAGE);
		assertThat(rootReplies.items()).extracting(CommentResponse::id).containsExactly(reply.id());
		assertThat(rootReplies.items().getFirst().replyCount()).isEqualTo(1);
		assertThat(feed.listReplies(nested.id(), FIRST_PAGE).items()).extracting(CommentResponse::id)
				.containsExactly(deeper.id());
		assertThat(feed.listReplies(deeper.id(), FIRST_PAGE).items()).isEmpty();
	}

	@Test
	void replyTargetMustBeAVisibleCommentOfTheSamePost() {
		Post post = savePost(PostStatus.VISIBLE);
		Post otherPost = savePost(PostStatus.VISIBLE);
		UUID author = UUID.randomUUID();
		actAs(author);
		CommentResponse onOtherPost = write(otherPost.getId(), "başka gönderide", null);
		CommentResponse removed = write(post.getId(), "silinecek", null);
		comments.deleteComment(removed.id());
		UUID postId = post.getId();
		UUID crossPostParent = onOtherPost.id();
		UUID deletedParent = removed.id();
		UUID unknownParent = UUID.randomUUID();

		assertThatThrownBy(() -> write(postId, "yanıt", crossPostParent)).isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> write(postId, "yanıt", deletedParent)).isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> write(postId, "yanıt", unknownParent)).isInstanceOf(CommentNotFoundException.class);
		assertThat(commentCount(postId)).isZero();
	}

	@Test
	void topLevelCommentsPageOldestFirstWithoutGapsOrDuplicates() {
		Post post = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());
		List<String> written = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			written.add("yorum " + i);
			write(post.getId(), "yorum " + i, null);
		}

		List<String> seen = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			CommentPageResponse result = feed.listComments(post.getId(), page(2, cursor));
			seen.addAll(bodies(result));
			cursor = result.nextCursor();
			pages++;
		} while (cursor != null);

		assertThat(seen).containsExactlyElementsOf(written);
		assertThat(pages).isEqualTo(3);
	}

	@Test
	void invalidCursorIsRejected() {
		Post post = savePost(PostStatus.VISIBLE);
		UUID postId = post.getId();

		assertThatThrownBy(() -> feed.listComments(postId, BROKEN_CURSOR_PAGE))
				.isInstanceOf(InvalidPostQueryException.class);
	}

	@Test
	void authorEditsOwnCommentButNobodyElsesAndNotDeletedOnes() {
		Post post = savePost(PostStatus.VISIBLE);
		UUID author = UUID.randomUUID();
		actAs(author);
		CommentResponse created = write(post.getId(), "ilk", null);
		UUID commentId = created.id();

		CommentResponse edited = comments.updateComment(commentId, new UpdateCommentRequest("  düzenlendi "));
		assertThat(edited.body()).isEqualTo("düzenlendi");
		assertThat(edited.authorId()).isEqualTo(author);

		actAs(UUID.randomUUID());
		assertThatThrownBy(() -> comments.updateComment(commentId, HIJACK_EDIT))
				.isInstanceOf(CommentAccessDeniedException.class);
		assertThatThrownBy(() -> comments.deleteComment(commentId)).isInstanceOf(CommentAccessDeniedException.class);

		actAs(author);
		comments.deleteComment(commentId);
		assertThatThrownBy(() -> comments.updateComment(commentId, AGAIN_EDIT))
				.isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> comments.deleteComment(commentId)).isInstanceOf(CommentNotFoundException.class);
		assertThat(commentCount(post.getId())).isZero();
	}

	@Test
	void deletingACommentWithRepliesKeepsAPlaceholderAndTheRelation() {
		Post post = savePost(PostStatus.VISIBLE);
		UUID author = UUID.randomUUID();
		actAs(author);
		CommentResponse root = write(post.getId(), "gizli sır", null);
		CommentResponse reply = write(post.getId(), "yanıt", root.id());
		CommentResponse lonely = write(post.getId(), "yalnız", null);
		comments.deleteComment(root.id());
		comments.deleteComment(lonely.id());

		CommentPageResponse topLevel = feed.listComments(post.getId(), FIRST_PAGE);

		assertThat(topLevel.items()).hasSize(1);
		CommentResponse placeholder = topLevel.items().getFirst();
		assertThat(placeholder.id()).isEqualTo(root.id());
		assertThat(placeholder.status()).isEqualTo(CommentStatus.DELETED);
		assertThat(placeholder.body()).isNull();
		assertThat(placeholder.authorId()).isNull();
		assertThat(placeholder.replyCount()).isEqualTo(1);
		assertThat(feed.listReplies(root.id(), FIRST_PAGE).items()).extracting(CommentResponse::id)
				.containsExactly(reply.id());
		// Yalnız VISIBLE yorumlar sayılır: silinen iki yorum düşer, yanıt kalır.
		assertThat(commentCount(post.getId())).isEqualTo(1);
	}

	@Test
	void replyCountIgnoresDeletedReplies() {
		Post post = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());
		CommentResponse root = write(post.getId(), "kök", null);
		CommentResponse kept = write(post.getId(), "kalan", root.id());
		CommentResponse removed = write(post.getId(), "silinen", root.id());
		comments.deleteComment(removed.id());

		assertThat(feed.listComments(post.getId(), FIRST_PAGE).items().getFirst().replyCount()).isEqualTo(1);
		assertThat(feed.listReplies(root.id(), FIRST_PAGE).items()).extracting(CommentResponse::id)
				.containsExactly(kept.id());
	}

	@Test
	void commentsOfAHiddenPostAreVisibleOnlyToItsOwnerAndDeletedPostsHaveNone() {
		Post visible = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());
		CommentResponse comment = write(visible.getId(), "yorum", null);
		jdbcTemplate.update("update posts set status = 'HIDDEN' where id = ?", visible.getId());
		entityManager.clear();
		UUID postId = visible.getId();
		UUID commentId = comment.id();

		assertThatThrownBy(() -> feed.listComments(postId, FIRST_PAGE)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> feed.listReplies(commentId, FIRST_PAGE)).isInstanceOf(PostNotFoundException.class);
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		assertThatThrownBy(() -> feed.listComments(postId, FIRST_PAGE)).isInstanceOf(PostNotFoundException.class);

		when(currentUser.findUserId()).thenReturn(Optional.of(visible.getAuthorId()));
		assertThat(feed.listComments(postId, FIRST_PAGE).items()).hasSize(1);
	}

	@Test
	void repliesOfUnknownOrHiddenCommentsAreNotFound() {
		Post post = savePost(PostStatus.VISIBLE);
		actAs(UUID.randomUUID());
		CommentResponse comment = write(post.getId(), "yorum", null);
		jdbcTemplate.update("update comments set status = 'HIDDEN' where id = ?", comment.id());
		entityManager.clear();
		UUID hiddenId = comment.id();
		UUID unknownId = UUID.randomUUID();

		assertThatThrownBy(() -> feed.listReplies(hiddenId, FIRST_PAGE)).isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> feed.listReplies(unknownId, FIRST_PAGE)).isInstanceOf(CommentNotFoundException.class);
		// Moderasyonla gizlenmiş yorum listede görünmez.
		assertThat(feed.listComments(post.getId(), FIRST_PAGE).items()).isEmpty();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentCommentsAreAllCounted() throws Exception {
		Post post = savePost(PostStatus.VISIBLE);
		try {
			int writers = 12;
			runConcurrently(writers, () -> {
				actAs(UUID.randomUUID());
				write(post.getId(), "eşzamanlı", null);
				return null;
			});

			assertThat(postRepository.findById(post.getId()).orElseThrow().getCommentCount()).isEqualTo(writers);
		} finally {
			deleteTestData(post);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentDeletesOfTheSameCommentDecrementTheCounterOnce() throws Exception {
		Post post = savePost(PostStatus.VISIBLE);
		UUID author = UUID.randomUUID();
		try {
			actAs(author);
			CommentResponse keeper = write(post.getId(), "kalacak", null);
			CommentResponse target = write(post.getId(), "silinecek", null);
			List<Future<Boolean>> outcomes = new ArrayList<>();
			ExecutorService executor = Executors.newFixedThreadPool(6);
			try {
				for (int i = 0; i < 6; i++) {
					outcomes.add(executor.submit(() -> {
						actAs(author);
						try {
							comments.deleteComment(target.id());
							return true;
						} catch (RuntimeException _) {
							return false;
						}
					}));
				}
				int succeeded = 0;
				for (Future<Boolean> outcome : outcomes) {
					succeeded += outcome.get() ? 1 : 0;
				}
				assertThat(succeeded).isEqualTo(1);
			} finally {
				executor.shutdownNow();
				executor.awaitTermination(30, TimeUnit.SECONDS);
			}

			assertThat(postRepository.findById(post.getId()).orElseThrow().getCommentCount()).isEqualTo(1);
			assertThat(keeper.id()).isNotNull();
		} finally {
			deleteTestData(post);
		}
	}

	private void deleteTestData(Post post) {
		jdbcTemplate.update("delete from comments where post_id = ?", post.getId());
		jdbcTemplate.update("delete from posts where id = ?", post.getId());
	}

	private static void runConcurrently(int threads, Callable<Void> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Void>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(task));
			}
			for (Future<Void> result : results) {
				result.get();
			}
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(30, TimeUnit.SECONDS);
		}
	}
}
