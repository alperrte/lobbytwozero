package com.ltz.community.application.service;

import com.ltz.community.EventTestConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.ltz.community.api.dto.request.CreateCommentRequest;
import com.ltz.community.api.dto.request.CreateForumRequest;
import com.ltz.community.api.dto.request.CreatePostRequest;
import com.ltz.community.api.dto.request.LinkGameRequest;
import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.request.UpdateForumRequest;
import com.ltz.community.api.dto.response.ForumResponse;
import com.ltz.community.api.dto.response.PostResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.mapper.CommentMapper;
import com.ltz.community.application.mapper.ForumMapper;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.domain.enums.ForumStatus;
import com.ltz.community.domain.exception.ForumGameMismatchException;
import com.ltz.community.domain.exception.ForumNotFoundException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;

import jakarta.persistence.EntityManager;

/** Forum konularını (posts.forum_id) ve forumun oyun/görünürlük kurallarını gerçek PostgreSQL üzerinde doğrular. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, ForumService.class, ForumMapper.class, PostService.class, PostFeedService.class, PostAccess.class,
		PostViewAssembler.class, PostMapper.class, CommentService.class, CommentMapper.class, PostLikeService.class,
		ClockConfig.class })
class ForumPostIntegrationTest extends PostgreSqlIntegrationTest {

	private static final UUID GAME_ID = UUID.randomUUID();
	private static final UUID OTHER_GAME_ID = UUID.randomUUID();

	@MockitoBean
	private CurrentUserPort currentUser;

	@MockitoBean
	private GameCatalogPort gameCatalog;

	@Autowired
	private ForumService forums;

	@Autowired
	private PostService posts;

	@Autowired
	private PostFeedService feed;

	@Autowired
	private CommentService comments;

	@Autowired
	private PostLikeService likes;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private UUID member;

	@BeforeEach
	void setUp() {
		member = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(member);
		when(currentUser.findUserId()).thenReturn(Optional.of(member));
		when(currentUser.hasModeratorRole()).thenReturn(false);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(gameCatalog.isAvailable(OTHER_GAME_ID)).thenReturn(true);
	}

	private ForumResponse openForum(String slug, UUID gameId) {
		when(currentUser.hasModeratorRole()).thenReturn(true);
		try {
			return forums.createForum(new CreateForumRequest(slug, "Forum " + slug, null, gameId));
		} finally {
			when(currentUser.hasModeratorRole()).thenReturn(false);
		}
	}

	private void setForumStatus(UUID forumId, ForumStatus status) {
		when(currentUser.hasModeratorRole()).thenReturn(true);
		forums.updateForum(forumId, new UpdateForumRequest(null, null, status));
		when(currentUser.hasModeratorRole()).thenReturn(false);
		entityManager.flush();
		entityManager.clear();
	}

	private void linkGame(UUID postId, UUID gameId) {
		posts.linkGame(postId, new LinkGameRequest(gameId));
	}

	private PostResponse thread(UUID forumId, String body, UUID gameId) {
		return posts.createPost(new CreatePostRequest(body, gameId, null, forumId));
	}

	private static PostFeedRequest forumFeed(UUID forumId) {
		return new PostFeedRequest(null, null, forumId, null, null, null, null, null, null);
	}

	@Test
	void memberOpensAThreadInAGeneralForumWithAnOptionalGame() {
		ForumResponse general = openForum("genel-forum", null);

		PostResponse plain = thread(general.id(), "konu", null);
		PostResponse withGame = thread(general.id(), "oyunlu konu", GAME_ID);

		assertThat(plain.forumId()).isEqualTo(general.id());
		assertThat(plain.gameId()).isNull();
		assertThat(withGame.gameId()).isEqualTo(GAME_ID);
		verify(gameCatalog).isAvailable(GAME_ID);
	}

	@Test
	void threadInAGameForumInheritsTheGameWithoutACatalogCall() {
		ForumResponse gameForum = openForum("oyun-forumu", GAME_ID);
		org.mockito.Mockito.clearInvocations(gameCatalog);

		PostResponse inherited = thread(gameForum.id(), "konu", null);
		PostResponse explicit = thread(gameForum.id(), "ikinci konu", GAME_ID);

		assertThat(inherited.gameId()).isEqualTo(GAME_ID);
		assertThat(explicit.gameId()).isEqualTo(GAME_ID);
		verify(gameCatalog, never()).isAvailable(any());
	}

	@Test
	void threadCannotContradictTheGameOfItsForum() {
		ForumResponse gameForum = openForum("oyun-forumu-2", GAME_ID);
		UUID forumId = gameForum.id();

		assertThatThrownBy(() -> thread(forumId, "yanlış oyun", OTHER_GAME_ID))
				.isInstanceOf(ForumGameMismatchException.class);
	}

	@Test
	void gameLinkOfAGameForumThreadCannotBeChangedOrRemoved() {
		ForumResponse gameForum = openForum("oyun-forumu-3", GAME_ID);
		PostResponse created = thread(gameForum.id(), "konu", null);
		UUID postId = created.id();

		assertThat(posts.linkGame(postId, new LinkGameRequest(GAME_ID)).gameId()).isEqualTo(GAME_ID);
		assertThatThrownBy(() -> linkGame(postId, OTHER_GAME_ID))
				.isInstanceOf(ForumGameMismatchException.class);
		assertThatThrownBy(() -> posts.unlinkGame(postId)).isInstanceOf(ForumGameMismatchException.class);
	}

	@Test
	void gameLinkOfAGeneralForumThreadStaysFree() {
		ForumResponse general = openForum("genel-forum-2", null);
		PostResponse created = thread(general.id(), "konu", GAME_ID);
		UUID postId = created.id();

		assertThat(posts.linkGame(postId, new LinkGameRequest(OTHER_GAME_ID)).gameId()).isEqualTo(OTHER_GAME_ID);
		posts.unlinkGame(postId);
		entityManager.flush();
		entityManager.clear();
		assertThat(feed.getPost(postId).gameId()).isNull();
	}

	@Test
	void threadsRequireAnExistingVisibleForum() {
		ForumResponse closed = openForum("kapali-forum", null);
		setForumStatus(closed.id(), ForumStatus.HIDDEN);
		UUID closedId = closed.id();
		UUID unknownId = UUID.randomUUID();

		assertThatThrownBy(() -> thread(closedId, "konu", null)).isInstanceOf(ForumNotFoundException.class);
		assertThatThrownBy(() -> thread(unknownId, "konu", null)).isInstanceOf(ForumNotFoundException.class);
	}

	@Test
	void feedCanBeFilteredByForum() {
		ForumResponse first = openForum("forum-bir", null);
		ForumResponse second = openForum("forum-iki", null);
		PostResponse inFirst = thread(first.id(), "birinci", null);
		thread(second.id(), "ikinci", null);
		posts.createPost(new CreatePostRequest("forum dışı", null, null, null));
		entityManager.flush();
		entityManager.clear();

		List<UUID> ids = feed.listPosts(forumFeed(first.id())).items().stream().map(PostViewResponse::id).toList();

		assertThat(ids).containsExactly(inFirst.id());
		assertThat(feed.listPosts(forumFeed(first.id())).items().getFirst().forumId()).isEqualTo(first.id());
		assertThat(feed.getPost(inFirst.id()).forumId()).isEqualTo(first.id());
	}

	@Test
	void hiddenForumHidesItsThreadsEverywhereAndShowsThemAgainWhenReopened() {
		ForumResponse forum = openForum("gizlenecek-forum", null);
		PostResponse inForum = thread(forum.id(), "gizlenecek konu", null);
		PostResponse outside = posts.createPost(new CreatePostRequest("dışarıdaki", null, null, null));
		CreateCommentRequest comment = new CreateCommentRequest("yorum", null);
		comments.createComment(inForum.id(), comment);
		entityManager.flush();
		entityManager.clear();
		UUID threadId = inForum.id();

		setForumStatus(forum.id(), ForumStatus.HIDDEN);

		List<UUID> publicFeed = feed.listPosts(forumFeed(null)).items().stream().map(PostViewResponse::id).toList();
		assertThat(publicFeed).containsExactly(outside.id());
		assertThat(feed.listPosts(forumFeed(forum.id())).items()).isEmpty();
		assertThat(feed.listUserPosts(member, forumFeed(null)).items()).extracting(PostViewResponse::id)
				.containsExactly(outside.id());
		// Gizli forumun konusu sahibine bile görünmez ve etkileşime kapalıdır.
		assertThatThrownBy(() -> feed.getPost(threadId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> likes.like(threadId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> comments.createComment(threadId, comment)).isInstanceOf(PostNotFoundException.class);

		setForumStatus(forum.id(), ForumStatus.VISIBLE);
		assertThat(feed.listPosts(forumFeed(forum.id())).items()).extracting(PostViewResponse::id).containsExactly(threadId);
		assertThat(feed.getPost(threadId).forumId()).isEqualTo(forum.id());
	}

	@Test
	void forumSlugMustBeLowercaseKebabCase() {
		ForumResponse forum = openForum("kisitlar", null);

		assertThatThrownBy(() -> jdbcTemplate.update("update forums set slug = 'Bozuk Slug' where id = ?", forum.id()))
				.hasMessageContaining("ck_forums_slug");
	}

	@Test
	void forumStatusIsLimitedToVisibleAndHidden() {
		ForumResponse forum = openForum("kisitlar-durum", null);

		assertThatThrownBy(() -> jdbcTemplate.update("update forums set status = 'DELETED' where id = ?", forum.id()))
				.hasMessageContaining("ck_forums_status");
	}

	@Test
	void forumWithThreadsCannotBeDeleted() {
		ForumResponse forum = openForum("kisitlar-silme", null);
		thread(forum.id(), "konu", null);
		entityManager.flush();

		assertThatThrownBy(() -> jdbcTemplate.update("delete from forums where id = ?", forum.id()))
				.hasMessageContaining("fk_posts_forum");
	}
}
