package com.ltz.community.application.service;

import com.ltz.community.EventTestConfig;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ltz.community.api.dto.request.CreateForumRequest;
import com.ltz.community.api.dto.request.CreatePostRequest;
import com.ltz.community.api.dto.request.CreateRepostRequest;
import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.request.UpdateForumRequest;
import com.ltz.community.api.dto.request.UpdatePostRequest;
import com.ltz.community.api.dto.response.ForumResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.api.dto.response.RepostSourceResponse;
import com.ltz.community.application.mapper.ForumMapper;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.enums.ForumStatus;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.AlreadyRepostedException;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

import jakarta.persistence.EntityManager;

/** Kaynağa atıflı repost'u gerçek PostgreSQL üzerinde doğrular (COM-20). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, RepostService.class, PostService.class, PostFeedService.class, PostAccess.class, PostViewAssembler.class,
		PostMapper.class, ForumService.class, ForumMapper.class, ClockConfig.class })
class RepostServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

	@MockitoBean
	private CurrentUserPort currentUser;

	@MockitoBean
	private GameCatalogPort gameCatalog;

	@Autowired
	private RepostService reposts;

	@Autowired
	private PostService posts;

	@Autowired
	private PostFeedService feed;

	@Autowired
	private ForumService forums;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostImageRepository postImageRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private UUID originalAuthor;
	private UUID reposter;

	@BeforeEach
	void setUp() {
		originalAuthor = UUID.randomUUID();
		reposter = UUID.randomUUID();
		actAs(reposter);
		when(currentUser.hasModeratorRole()).thenReturn(false);
	}

	private void actAs(UUID userId) {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(currentUser.findUserId()).thenReturn(Optional.of(userId));
	}

	private Post original(String body) {
		return postRepository.saveAndFlush(Post.publish(originalAuthor, body, NOW));
	}

	private void setStatus(UUID postId, String status) {
		jdbcTemplate.update("update posts set status = ?, deleted_at = case when ? = 'DELETED' then now() else null end where id = ?",
				status, status, postId);
		entityManager.clear();
	}

	private static CreateRepostRequest comment(String text) {
		return new CreateRepostRequest(text);
	}

	private List<PostViewResponse> feedItems() {
		entityManager.flush();
		entityManager.clear();
		return feed.listPosts(new PostFeedRequest(null, null, null, null, null, null, null, null, null)).items();
	}

	@Test
	void repostCarriesExplicitAttributionAndLeavesTheOriginalUntouched() {
		Post source = original("özgün içerik");
		Instant sourceUpdatedAt = source.getUpdatedAt();

		PostViewResponse view = reposts.repost(source.getId(), comment("  bunu görün  "));
		entityManager.flush();
		entityManager.clear();

		assertThat(view.id()).isNotEqualTo(source.getId());
		assertThat(view.authorId()).isEqualTo(reposter);
		assertThat(view.body()).isEqualTo("bunu görün");
		assertThat(view.status()).isEqualTo(PostStatus.VISIBLE);
		RepostSourceResponse attribution = view.repostOf();
		assertThat(attribution.available()).isTrue();
		assertThat(attribution.postId()).isEqualTo(source.getId());
		assertThat(attribution.authorId()).isEqualTo(originalAuthor);
		assertThat(attribution.body()).isEqualTo("özgün içerik");

		Post untouched = postRepository.findById(source.getId()).orElseThrow();
		assertThat(untouched.getAuthorId()).isEqualTo(originalAuthor);
		assertThat(untouched.getBody()).isEqualTo("özgün içerik");
		assertThat(untouched.getUpdatedAt()).isEqualTo(sourceUpdatedAt);
		assertThat(untouched.isRepost()).isFalse();
	}

	@Test
	void commentIsOptionalAndBlankCommentMeansPlainRepost() {
		Post first = original("bir");
		Post second = original("iki");

		PostViewResponse plain = reposts.repost(first.getId(), null);
		PostViewResponse blank = reposts.repost(second.getId(), comment("   "));

		assertThat(plain.body()).isNull();
		assertThat(blank.body()).isNull();
		assertThat(plain.repostOf().authorId()).isEqualTo(originalAuthor);
	}

	@Test
	void sameUserRepostsAnOriginalOnlyOnceUntilTheRepostIsDeleted() {
		Post source = original("tek sefer");
		UUID sourceId = source.getId();
		PostViewResponse first = reposts.repost(sourceId, null);

		CreateRepostRequest second = comment("ikinci");
		assertThatThrownBy(() -> reposts.repost(sourceId, second)).isInstanceOf(AlreadyRepostedException.class);

		posts.deletePost(first.id());
		entityManager.flush();
		PostViewResponse again = reposts.repost(sourceId, null);
		assertThat(again.id()).isNotEqualTo(first.id());
	}

	@Test
	void repostOfARepostPointsStraightAtTheOriginal() {
		Post source = original("kök");
		PostViewResponse firstRepost = reposts.repost(source.getId(), comment("ilk"));
		UUID third = UUID.randomUUID();
		actAs(third);

		PostViewResponse chained = reposts.repost(firstRepost.id(), comment("zincir"));

		assertThat(chained.repostOf().postId()).isEqualTo(source.getId());
		assertThat(chained.repostOf().authorId()).isEqualTo(originalAuthor);
		// Aynı kullanıcı orijinali bir de repost üzerinden tekrar paylaşamaz.
		UUID sourceId = source.getId();
		assertThatThrownBy(() -> reposts.repost(sourceId, null)).isInstanceOf(AlreadyRepostedException.class);
	}

	@Test
	void inaccessiblePostsCannotBeReposted() {
		Post hidden = original("gizli");
		Post deleted = original("silinmiş");
		setStatus(hidden.getId(), "HIDDEN");
		setStatus(deleted.getId(), "DELETED");
		UUID hiddenId = hidden.getId();
		UUID deletedId = deleted.getId();
		UUID unknownId = UUID.randomUUID();

		assertThatThrownBy(() -> reposts.repost(hiddenId, null)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> reposts.repost(deletedId, null)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> reposts.repost(unknownId, null)).isInstanceOf(PostNotFoundException.class);
		// Orijinalin sahibi bile gizli gönderisini repostlayamaz.
		actAs(originalAuthor);
		assertThatThrownBy(() -> reposts.repost(hiddenId, null)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void threadInAHiddenForumCannotBeRepostedAndTheSourceStaysHidden() {
		when(currentUser.hasModeratorRole()).thenReturn(true);
		ForumResponse forum = forums.createForum(new CreateForumRequest("repost-forumu", "Forum", null, null));
		when(currentUser.hasModeratorRole()).thenReturn(false);
		PostViewResponse inForum = feed.getPost(posts.createPost(new CreatePostRequest("konu", null, null, forum.id())).id());
		Post source = original("forumsuz");
		reposts.repost(source.getId(), null);
		UUID threadId = inForum.id();

		when(currentUser.hasModeratorRole()).thenReturn(true);
		forums.updateForum(forum.id(), new UpdateForumRequest(null, null, ForumStatus.HIDDEN));
		when(currentUser.hasModeratorRole()).thenReturn(false);
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> reposts.repost(threadId, null)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void repostDoesNotLeakAnOriginalThatLaterBecomesInaccessible() {
		Post source = original("sonradan kaldırılacak");
		PostViewResponse repost = reposts.repost(source.getId(), comment("not"));
		entityManager.flush();

		for (String status : new String[] { "HIDDEN", "DELETED" }) {
			setStatus(source.getId(), status);
			PostViewResponse stored = feed.getPost(repost.id());
			RepostSourceResponse attribution = stored.repostOf();
			assertThat(attribution.available()).isFalse();
			assertThat(attribution.postId()).isEqualTo(source.getId());
			assertThat(attribution.authorId()).isNull();
			assertThat(attribution.body()).isNull();
			assertThat(attribution.images()).isEmpty();
			// Reposter'ın kendi notu etkilenmez.
			assertThat(stored.body()).isEqualTo("not");
		}

		jdbcTemplate.update("update posts set status = 'VISIBLE', deleted_at = null where id = ?", source.getId());
		entityManager.clear();
		assertThat(feed.getPost(repost.id()).repostOf().available()).isTrue();
	}

	@Test
	void sourceFromAHiddenForumStaysUnavailableThroughTheRepost() {
		when(currentUser.hasModeratorRole()).thenReturn(true);
		ForumResponse forum = forums.createForum(new CreateForumRequest("kaynak-forum", "Forum", null, null));
		when(currentUser.hasModeratorRole()).thenReturn(false);
		UUID threadId = posts.createPost(new CreatePostRequest("forum konusu", null, null, forum.id())).id();
		// Repost oluşturulduktan sonra forum gizlenir; kaynak içerik repost üzerinden de görünmemelidir.
		UUID repostId = postRepository.saveAndFlush(Post.repost(reposter, threadId, null, NOW)).getId();
		when(currentUser.hasModeratorRole()).thenReturn(true);
		forums.updateForum(forum.id(), new UpdateForumRequest(null, null, ForumStatus.HIDDEN));
		when(currentUser.hasModeratorRole()).thenReturn(false);
		entityManager.flush();
		entityManager.clear();

		assertThat(feed.getPost(repostId).repostOf().available()).isFalse();
	}

	@Test
	void sourceImagesAreEmbeddedWithoutExtraQueriesPerRepost() {
		Post source = original("görselli");
		postImageRepository.saveAndFlush(PostImage.attach(source.getId(), new byte[] { 1, 2 }, "image/png", 4, 4, 0, NOW));
		reposts.repost(source.getId(), null);

		List<PostViewResponse> items = feedItems();

		PostViewResponse repostItem = items.stream().filter(item -> item.repostOf() != null).findFirst().orElseThrow();
		assertThat(repostItem.images()).isEmpty();
		assertThat(repostItem.repostOf().images()).hasSize(1);
		assertThat(repostItem.repostOf().images().getFirst().contentUrl()).contains("/posts/" + source.getId() + "/images/");
		assertThat(items.stream().filter(item -> item.repostOf() == null).findFirst().orElseThrow().id()).isEqualTo(source.getId());
	}

	@Test
	void repostCannotBeEditedButOwnerCanDeleteItAndOthersCannot() {
		Post source = original("kaynak");
		PostViewResponse repost = reposts.repost(source.getId(), comment("not"));
		UUID repostId = repost.id();

		UpdatePostRequest edit = new UpdatePostRequest("değiştir", null);
		assertThatThrownBy(() -> posts.updatePost(repostId, edit))
				.isInstanceOf(PostNotEditableException.class);

		actAs(UUID.randomUUID());
		assertThatThrownBy(() -> posts.deletePost(repostId)).isInstanceOf(PostAccessDeniedException.class);

		actAs(reposter);
		posts.deletePost(repostId);
		entityManager.flush();
		entityManager.clear();
		assertThatThrownBy(() -> feed.getPost(repostId)).isInstanceOf(PostNotFoundException.class);
		assertThat(feed.getPost(source.getId()).body()).isEqualTo("kaynak");
	}

	@Test
	void repostCannotPointAtItself() {
		Post source = original("kaynak");
		Post repost = postRepository.saveAndFlush(Post.repost(reposter, source.getId(), null, NOW));

		assertThatThrownBy(() -> jdbcTemplate.update("update posts set repost_of_id = id where id = ?", repost.getId()))
				.hasMessageContaining("ck_posts_repost_not_self");
	}

	@Test
	void repostRowsCannotCarryAGameOrAForum() {
		Post source = original("kaynak");
		Post repost = postRepository.saveAndFlush(Post.repost(reposter, source.getId(), null, NOW));

		assertThatThrownBy(() -> jdbcTemplate.update("update posts set game_id = ? where id = ?", UUID.randomUUID(), repost.getId()))
				.hasMessageContaining("ck_posts_repost_plain");
	}

	@Test
	void plainPostsStillRequireABody() {
		Post source = original("kaynak");

		assertThatThrownBy(() -> jdbcTemplate.update("update posts set body = null where id = ?", source.getId()))
				.hasMessageContaining("ck_posts_body_length");
	}

	@Test
	void duplicateActiveRepostsAreRejectedByTheDatabase() {
		Post source = original("kaynak");
		postRepository.saveAndFlush(Post.repost(reposter, source.getId(), null, NOW));

		assertThatThrownBy(() -> postRepository.saveAndFlush(Post.repost(reposter, source.getId(), "tekrar", NOW)))
				.hasMessageContaining("ux_posts_one_repost_per_user");
	}
}
