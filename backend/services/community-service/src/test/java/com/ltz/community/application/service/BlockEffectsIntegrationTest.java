package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ltz.community.EventTestConfig;
import com.ltz.community.api.dto.request.CommentPageRequest;
import com.ltz.community.api.dto.request.CreateCommentRequest;
import com.ltz.community.api.dto.request.CreateRepostRequest;
import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.mapper.CommentMapper;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.UserBlockRepository;

/**
 * Kullanıcı engellemenin (COM-21) iki yönlü etkisi: akış, arama, profil geçmişi, detay, yorum listeleri, etkileşimler
 * (beğeni, yorum, yanıt, repost) ve repost kaynağı. Misafir ve ilgisiz kullanıcılar etkilenmez.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, PostFeedService.class, CommentFeedService.class, PostLikeService.class,
		CommentService.class, RepostService.class, PostAccess.class, PostViewAssembler.class, PostMapper.class,
		CommentMapper.class })
class BlockEffectsIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
	private static final CommentPageRequest FIRST_PAGE = new CommentPageRequest(null, null);

	@MockitoBean
	private CurrentUserPort currentUser;

	@Autowired
	private PostFeedService feed;

	@Autowired
	private CommentFeedService commentFeed;

	@Autowired
	private PostLikeService likes;

	@Autowired
	private CommentService comments;

	@Autowired
	private RepostService reposts;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private UserBlockRepository userBlockRepository;

	@Autowired
	private BlockPolicy blockPolicy;

	private final UUID viewer = UUID.randomUUID();
	private final UUID blockedByViewer = UUID.randomUUID();
	private final UUID blockingViewer = UUID.randomUUID();
	private final UUID bystander = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		userBlockRepository.insertIfAbsent(viewer, blockedByViewer, NOW);
		userBlockRepository.insertIfAbsent(blockingViewer, viewer, NOW);
		actAs(viewer);
	}

	private void actAs(UUID userId) {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(currentUser.findUserId()).thenReturn(Optional.of(userId));
	}

	private void asGuest() {
		when(currentUser.findUserId()).thenReturn(Optional.empty());
	}

	private Post postBy(UUID author, String body) {
		return postRepository.saveAndFlush(Post.publish(author, body, NOW));
	}

	private static PostFeedRequest search(String q) {
		return new PostFeedRequest(q, null, null, null, null, null, null, null, null);
	}

	private List<UUID> feedIds() {
		return feed.listPosts(search(null)).items().stream().map(PostViewResponse::id).toList();
	}

	@Test
	void blockPolicyIsBidirectionalAndIgnoresGuestsAndSelf() {
		assertThat(blockPolicy.counterparts(viewer)).containsExactlyInAnyOrder(blockedByViewer, blockingViewer);
		assertThat(blockPolicy.counterparts(blockedByViewer)).containsExactly(viewer);
		assertThat(blockPolicy.counterparts(bystander)).isEmpty();
		assertThat(blockPolicy.counterparts(null)).isEmpty();
		assertThat(blockPolicy.isBlocked(viewer, blockedByViewer)).isTrue();
		assertThat(blockPolicy.isBlocked(blockedByViewer, viewer)).isTrue();
		assertThat(blockPolicy.isBlocked(viewer, bystander)).isFalse();
		assertThat(blockPolicy.isBlocked(null, blockedByViewer)).isFalse();
		assertThat(blockPolicy.isBlocked(viewer, viewer)).isFalse();
	}

	@Test
	void feedSearchAndProfileHideBlockedUsersInBothDirections() {
		Post mine = postBy(viewer, "ortak anahtar benim");
		Post fromBystander = postBy(bystander, "ortak anahtar ilgisiz");
		Post fromBlocked = postBy(blockedByViewer, "ortak anahtar engellediğim");
		Post fromBlocker = postBy(blockingViewer, "ortak anahtar beni engelleyen");

		assertThat(feedIds()).containsExactlyInAnyOrder(mine.getId(), fromBystander.getId())
				.doesNotContain(fromBlocked.getId(), fromBlocker.getId());
		assertThat(feed.listPosts(search("ortak anahtar")).items()).extracting(PostViewResponse::id)
				.containsExactlyInAnyOrder(mine.getId(), fromBystander.getId());
		assertThat(feed.listUserPosts(blockedByViewer, search(null)).items()).isEmpty();
		assertThat(feed.listUserPosts(blockingViewer, search(null)).items()).isEmpty();
		assertThat(feed.listUserPosts(bystander, search(null)).items()).hasSize(1);
	}

	@Test
	void guestsAndBystandersStillSeeEverything() {
		Post fromBlocked = postBy(blockedByViewer, "engellenen yazar");
		Post fromBlocker = postBy(blockingViewer, "engelleyen yazar");
		asGuest();

		assertThat(feedIds()).contains(fromBlocked.getId(), fromBlocker.getId());
		assertThat(feed.getPost(fromBlocked.getId()).id()).isEqualTo(fromBlocked.getId());

		actAs(bystander);
		assertThat(feedIds()).contains(fromBlocked.getId(), fromBlocker.getId());
	}

	@Test
	void detailLikeCommentAndRepostOfABlockedUsersPostLookLikeNotFound() {
		Post fromBlocked = postBy(blockedByViewer, "engellenen");
		Post fromBlocker = postBy(blockingViewer, "engelleyen");

		for (Post post : List.of(fromBlocked, fromBlocker)) {
			UUID postId = post.getId();
			assertThatThrownBy(() -> feed.getPost(postId)).isInstanceOf(PostNotFoundException.class);
			assertThatThrownBy(() -> likes.like(postId)).isInstanceOf(PostNotFoundException.class);
			assertThatThrownBy(() -> commentFeed.listComments(postId, FIRST_PAGE)).isInstanceOf(PostNotFoundException.class);
			assertThatThrownBy(() -> commentOn(postId)).isInstanceOf(PostNotFoundException.class);
			assertThatThrownBy(() -> repost(postId)).isInstanceOf(PostNotFoundException.class);
		}
	}

	private void commentOn(UUID postId) {
		comments.createComment(postId, new CreateCommentRequest("yorum", null));
	}

	private void repost(UUID postId) {
		reposts.repost(postId, new CreateRepostRequest(null));
	}

	@Test
	void commentsOfBlockedUsersAreHiddenFromListsAndCannotBeRepliedTo() {
		Post post = postBy(bystander, "herkese açık");
		actAs(blockedByViewer);
		CommentResponse fromBlocked = comments.createComment(post.getId(), new CreateCommentRequest("engellenenin yorumu", null));
		actAs(bystander);
		CommentResponse fromBystander = comments.createComment(post.getId(), new CreateCommentRequest("ilgisizin yorumu", null));
		actAs(viewer);
		CommentResponse mine = comments.createComment(post.getId(), new CreateCommentRequest("benim yorumum", null));
		UUID blockedCommentId = fromBlocked.id();
		UUID postId = post.getId();

		assertThat(commentFeed.listComments(postId, FIRST_PAGE).items()).extracting(CommentResponse::id)
				.containsExactlyInAnyOrder(fromBystander.id(), mine.id());
		assertThatThrownBy(() -> replyTo(postId, blockedCommentId)).isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> commentFeed.listReplies(blockedCommentId, FIRST_PAGE)).isInstanceOf(CommentNotFoundException.class);

		asGuest();
		assertThat(commentFeed.listComments(postId, FIRST_PAGE).items()).hasSize(3);
	}

	private void replyTo(UUID postId, UUID parentId) {
		comments.createComment(postId, new CreateCommentRequest("yanıt", parentId));
	}

	@Test
	void repliesFromBlockedUsersAreHiddenInsideAnOtherwiseVisibleThread() {
		Post post = postBy(bystander, "herkese açık");
		actAs(bystander);
		CommentResponse root = comments.createComment(post.getId(), new CreateCommentRequest("kök", null));
		actAs(blockedByViewer);
		comments.createComment(post.getId(), new CreateCommentRequest("engellenenin yanıtı", root.id()));
		actAs(blockingViewer);
		comments.createComment(post.getId(), new CreateCommentRequest("engelleyenin yanıtı", root.id()));
		actAs(bystander);
		CommentResponse fine = comments.createComment(post.getId(), new CreateCommentRequest("ilgisizin yanıtı", root.id()));
		actAs(viewer);

		assertThat(commentFeed.listReplies(root.id(), FIRST_PAGE).items()).extracting(CommentResponse::id)
				.containsExactly(fine.id());
	}

	@Test
	void repostSourceFromABlockedUserIsUnavailableOnlyForTheBlockedPair() {
		Post original = postBy(blockedByViewer, "engellenenin içeriği");
		UUID reposterId = bystander;
		actAs(reposterId);
		PostViewResponse repost = reposts.repost(original.getId(), new CreateRepostRequest("not"));
		UUID repostId = repost.id();

		actAs(viewer);
		var asViewer = feed.getPost(repostId).repostOf();
		assertThat(asViewer.available()).isFalse();
		assertThat(asViewer.body()).isNull();
		assertThat(asViewer.authorId()).isNull();

		actAs(bystander);
		assertThat(feed.getPost(repostId).repostOf().available()).isTrue();
		asGuest();
		assertThat(feed.getPost(repostId).repostOf().available()).isTrue();
	}

	@Test
	void blockedUserCannotCommentOnTheBlockersPost() {
		Post post = postBy(viewer, "benim gönderim");
		actAs(blockedByViewer);
		UUID postId = post.getId();

		assertThatThrownBy(() -> commentOn(postId)).isInstanceOf(PostNotFoundException.class);
	}
}
