package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.community.EventTestConfig;
import com.ltz.community.api.dto.request.GameContentListRequest;
import com.ltz.community.api.dto.response.GameContentDetailResponse;
import com.ltz.community.api.dto.response.GameContentPageResponse;
import com.ltz.community.api.dto.response.GameContentSummaryResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.GameContentType;
import com.ltz.community.domain.enums.ModerationAction;
import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.PostLikeRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.UserBlockRepository;

import jakarta.persistence.EntityManager;

/**
 * Oyuna bağlı içerik okumasını (GAME-11) gerçek PostgreSQL üzerinde doğrular: tür/oyun filtresi, kararlı sıralama,
 * sayfalama, liste-detay-toplam görünürlük tutarlılığı (gizli, silinmiş, moderasyonla gizlenmiş, engelli yazar, gizli forum).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, GameContentService.class, PostAccess.class, PostViewAssembler.class, PostMapper.class,
		ModerationService.class })
class GameContentServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant BASE = Instant.parse("2026-11-01T10:00:00Z");

	@MockitoBean
	private CurrentUserPort currentUser;

	@Autowired
	private GameContentService contents;

	@Autowired
	private ModerationService moderation;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private ForumRepository forumRepository;

	@Autowired
	private UserBlockRepository userBlockRepository;

	@Autowired
	private PostLikeRepository postLikeRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private final UUID gameId = UUID.randomUUID();
	private final UUID otherGameId = UUID.randomUUID();
	private final UUID viewer = UUID.randomUUID();

	@BeforeEach
	void guestByDefault() {
		when(currentUser.findUserId()).thenReturn(Optional.empty());
	}

	private void actAs(UUID userId) {
		when(currentUser.findUserId()).thenReturn(Optional.of(userId));
	}

	private Post content(UUID author, UUID game, GameContentType type, String title, String body, Instant at) {
		return postRepository.saveAndFlush(Post.publishGameContent(author, game, type, title, body, at));
	}

	private Post guide(String title, Instant at) {
		return content(UUID.randomUUID(), gameId, GameContentType.GUIDES, title, "gövde " + title, at);
	}

	private void refresh() {
		entityManager.flush();
		entityManager.clear();
	}

	private static GameContentListRequest all() {
		return new GameContentListRequest(null, null, null);
	}

	private static GameContentListRequest only(GameContentType type) {
		return new GameContentListRequest(type, null, null);
	}

	private List<String> titles(GameContentPageResponse page) {
		return page.items().stream().map(GameContentSummaryResponse::title).toList();
	}

	// ------------------------------------------------------------------ liste: kapsam, filtre, sıralama

	@Test
	void listsOnlyTypedContentOfTheGameAndNeverTagsLegacyPostsWithAType() {
		guide("rehber", BASE);
		content(UUID.randomUUID(), gameId, GameContentType.EASTER_EGGS, "yumurta", "gizli", BASE.plusSeconds(1));
		content(UUID.randomUUID(), gameId, GameContentType.EXPERIENCES, "deneyim", "oynadım", BASE.plusSeconds(2));
		content(UUID.randomUUID(), otherGameId, GameContentType.GUIDES, "başka oyun", "x", BASE.plusSeconds(3));
		// Eski gönderiler: türsüz. category=GUIDE olan eski gönderi de "GUIDES" sayılmaz (geriye dönük tür atama yok).
		postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "eski rehber", gameId, PostCategory.GUIDE, BASE.plusSeconds(4)));
		postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "oyunlu düz gönderi", gameId, BASE.plusSeconds(5)));

		GameContentPageResponse page = contents.list(gameId, all());

		assertThat(titles(page)).containsExactly("deneyim", "yumurta", "rehber");
		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.gameId()).isEqualTo(gameId);
		assertThat(page.type()).isNull();
		assertThat(page.page()).isZero();
		assertThat(page.size()).isEqualTo(20);
	}

	@Test
	void filtersByTypeAndTotalFollowsTheFilter() {
		guide("rehber bir", BASE);
		guide("rehber iki", BASE.plusSeconds(1));
		content(UUID.randomUUID(), gameId, GameContentType.EASTER_EGGS, "yumurta", "gizli", BASE.plusSeconds(2));

		GameContentPageResponse guides = contents.list(gameId, only(GameContentType.GUIDES));
		GameContentPageResponse eggs = contents.list(gameId, only(GameContentType.EASTER_EGGS));
		GameContentPageResponse experiences = contents.list(gameId, only(GameContentType.EXPERIENCES));

		assertThat(titles(guides)).containsExactly("rehber iki", "rehber bir");
		assertThat(guides.totalElements()).isEqualTo(2);
		assertThat(guides.type()).isEqualTo(GameContentType.GUIDES);
		assertThat(titles(eggs)).containsExactly("yumurta");
		assertThat(experiences.items()).isEmpty();
		assertThat(experiences.totalElements()).isZero();
	}

	@Test
	void summaryCarriesTheAgreedFieldsWithPlainTextExcerptAndRealDetailPath() {
		UUID author = UUID.randomUUID();
		String body = "ilk satır\n\n   ikinci   satır <b>kalın</b>";
		Post saved = content(author, gameId, GameContentType.GUIDES, "Başlık", body, BASE);

		GameContentSummaryResponse item = contents.list(gameId, all()).items().getFirst();

		assertThat(item.id()).isEqualTo(saved.getId());
		assertThat(item.gameId()).isEqualTo(gameId);
		assertThat(item.type()).isEqualTo(GameContentType.GUIDES);
		assertThat(item.title()).isEqualTo("Başlık");
		assertThat(item.excerpt()).isEqualTo("ilk satır ikinci satır <b>kalın</b>");
		assertThat(item.authorId()).isEqualTo(author);
		assertThat(item.publishedAt()).isEqualTo(BASE);
		assertThat(item.updatedAt()).isEqualTo(BASE);
		assertThat(item.detailPath()).isEqualTo("/api/v1/community/games/" + gameId + "/content/" + saved.getId());
	}

	@Test
	void equalPublishedAtFallsBackToIdDescendingAndPagesNeverOverlap() {
		List<Post> same = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			same.add(guide("aynı zaman " + i, BASE));
		}
		List<UUID> expected = same.stream().map(Post::getId).sorted(Comparator.reverseOrder()).toList();

		List<UUID> seen = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			GameContentPageResponse response = contents.list(gameId, new GameContentListRequest(null, page, 2));
			response.items().forEach(item -> seen.add(item.id()));
			assertThat(response.totalElements()).isEqualTo(5);
			assertThat(response.size()).isEqualTo(2);
		}

		assertThat(seen).containsExactlyElementsOf(expected);
	}

	@Test
	void pageBeyondTheLastIsAnEmptySuccessWithTheRealTotal() {
		guide("tek", BASE);

		GameContentPageResponse beyond = contents.list(gameId, new GameContentListRequest(null, 7, 10));

		assertThat(beyond.items()).isEmpty();
		assertThat(beyond.totalElements()).isEqualTo(1);
		assertThat(beyond.page()).isEqualTo(7);
	}

	@Test
	void unknownGameIsAnEmptySuccessNotAnError() {
		GameContentPageResponse page = contents.list(UUID.randomUUID(), all());

		assertThat(page.items()).isEmpty();
		assertThat(page.totalElements()).isZero();
	}

	// ------------------------------------------------------------------ görünürlük: liste = toplam = detay

	@Test
	void hiddenDeletedAndModeratedContentLeavesTheListAndTheTotalAndRestoreBringsItBack() {
		Post stays = guide("kalan", BASE);
		Post hiddenByStatus = guide("gizli", BASE.plusSeconds(1));
		Post deleted = guide("silinen", BASE.plusSeconds(2));
		Post moderated = guide("moderasyonla gizlenen", BASE.plusSeconds(3));
		ReflectionTestUtils.setField(hiddenByStatus, "status", com.ltz.community.domain.enums.PostStatus.HIDDEN);
		postRepository.saveAndFlush(hiddenByStatus);
		deleted.markDeleted(BASE);
		postRepository.saveAndFlush(deleted);
		moderation.apply(ReportTarget.POST, moderated.getId(), ModerationAction.HIDE, null);
		refresh();

		GameContentPageResponse page = contents.list(gameId, all());

		assertThat(titles(page)).containsExactly("kalan");
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.items().getFirst().id()).isEqualTo(stays.getId());

		moderation.apply(ReportTarget.POST, moderated.getId(), ModerationAction.RESTORE, null);
		refresh();
		GameContentPageResponse restored = contents.list(gameId, all());
		assertThat(titles(restored)).containsExactly("moderasyonla gizlenen", "kalan");
		assertThat(restored.totalElements()).isEqualTo(2);
	}

	@Test
	void ownerDoesNotSeeTheirOwnHiddenContentInListOrDetail() {
		UUID owner = UUID.randomUUID();
		Post hidden = content(owner, gameId, GameContentType.GUIDES, "gizlenen", "x", BASE);
		moderation.apply(ReportTarget.POST, hidden.getId(), ModerationAction.HIDE, null);
		refresh();
		actAs(owner);

		assertThat(contents.list(gameId, all()).items()).isEmpty();
		assertThat(contents.list(gameId, all()).totalElements()).isZero();
		UUID hiddenId = hidden.getId();
		assertThatThrownBy(() -> contents.detail(gameId, hiddenId)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void blockedAuthorsAreExcludedInBothDirectionsFromListTotalAndDetailButNotForOthers() {
		UUID blockedByViewer = UUID.randomUUID();
		UUID blockingViewer = UUID.randomUUID();
		UUID bystander = UUID.randomUUID();
		userBlockRepository.insertIfAbsent(viewer, blockedByViewer, BASE);
		userBlockRepository.insertIfAbsent(blockingViewer, viewer, BASE);
		Post fromBlocked = content(blockedByViewer, gameId, GameContentType.GUIDES, "engellenen", "x", BASE);
		Post fromBlocker = content(blockingViewer, gameId, GameContentType.GUIDES, "engelleyen", "x", BASE.plusSeconds(1));
		Post fromBystander = content(bystander, gameId, GameContentType.GUIDES, "ilgisiz", "x", BASE.plusSeconds(2));
		refresh();

		actAs(viewer);
		GameContentPageResponse forViewer = contents.list(gameId, all());
		assertThat(titles(forViewer)).containsExactly("ilgisiz");
		assertThat(forViewer.totalElements()).isEqualTo(1);
		UUID blockedId = fromBlocked.getId();
		UUID blockerId = fromBlocker.getId();
		assertThatThrownBy(() -> contents.detail(gameId, blockedId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> contents.detail(gameId, blockerId)).isInstanceOf(PostNotFoundException.class);
		assertThat(contents.detail(gameId, fromBystander.getId()).title()).isEqualTo("ilgisiz");

		when(currentUser.findUserId()).thenReturn(Optional.empty());
		assertThat(contents.list(gameId, all()).totalElements()).isEqualTo(3);
		actAs(bystander);
		assertThat(contents.list(gameId, all()).totalElements()).isEqualTo(3);
	}

	@Test
	void contentInAHiddenForumIsInvisibleEvenThoughServicesNeverCreateIt() {
		Forum open = forumRepository.saveAndFlush(Forum.open(UUID.randomUUID(), null, "acik", "Açık", null, BASE));
		Forum closed = forumRepository.saveAndFlush(Forum.open(UUID.randomUUID(), null, "kapali", "Kapalı", null, BASE));
		jdbcTemplate.update("update forums set status = 'HIDDEN' where id = ?", closed.getId());
		Post inOpen = Post.publishGameContent(UUID.randomUUID(), gameId, GameContentType.GUIDES, "açık forum", "x", BASE);
		ReflectionTestUtils.setField(inOpen, "forumId", open.getId());
		Post inClosed = Post.publishGameContent(UUID.randomUUID(), gameId, GameContentType.GUIDES, "kapalı forum", "x", BASE.plusSeconds(1));
		ReflectionTestUtils.setField(inClosed, "forumId", closed.getId());
		postRepository.saveAndFlush(inOpen);
		postRepository.saveAndFlush(inClosed);
		refresh();

		GameContentPageResponse page = contents.list(gameId, all());

		assertThat(titles(page)).containsExactly("açık forum");
		assertThat(page.totalElements()).isEqualTo(1);
		UUID closedId = inClosed.getId();
		assertThatThrownBy(() -> contents.detail(gameId, closedId)).isInstanceOf(PostNotFoundException.class);
	}

	// ------------------------------------------------------------------ detay

	@Test
	void detailReturnsBodyCountersAndLikeStateOfTheCaller() {
		UUID author = UUID.randomUUID();
		Post saved = content(author, gameId, GameContentType.EXPERIENCES, "Deneyimim", "uzun gövde\nikinci satır", BASE);
		postLikeRepository.insertIfAbsent(saved.getId(), viewer, BASE);
		jdbcTemplate.update("update posts set like_count = 1, comment_count = 2 where id = ?", saved.getId());
		refresh();

		actAs(viewer);
		GameContentDetailResponse mine = contents.detail(gameId, saved.getId());
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		GameContentDetailResponse guest = contents.detail(gameId, saved.getId());

		assertThat(mine.type()).isEqualTo(GameContentType.EXPERIENCES);
		assertThat(mine.title()).isEqualTo("Deneyimim");
		assertThat(mine.body()).isEqualTo("uzun gövde\nikinci satır");
		assertThat(mine.authorId()).isEqualTo(author);
		assertThat(mine.publishedAt()).isEqualTo(BASE);
		assertThat(mine.likeCount()).isEqualTo(1);
		assertThat(mine.commentCount()).isEqualTo(2);
		assertThat(mine.likedByMe()).isTrue();
		assertThat(mine.images()).isEmpty();
		assertThat(guest.likedByMe()).isFalse();
	}

	@Test
	void detailHidesWrongGameUntypedPostAndUnknownIdsAsNotFound() {
		Post typed = guide("rehber", BASE);
		Post untyped = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "düz", gameId, BASE));
		UUID typedId = typed.getId();
		UUID untypedId = untyped.getId();

		assertThatThrownBy(() -> contents.detail(otherGameId, typedId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> contents.detail(gameId, untypedId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> contents.detail(gameId, UUID.randomUUID())).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void deletedContentIsNotFoundInDetail() {
		Post deleted = guide("silinecek", BASE);
		deleted.markDeleted(BASE);
		postRepository.saveAndFlush(deleted);
		UUID deletedId = deleted.getId();

		assertThatThrownBy(() -> contents.detail(gameId, deletedId)).isInstanceOf(PostNotFoundException.class);
	}
}
