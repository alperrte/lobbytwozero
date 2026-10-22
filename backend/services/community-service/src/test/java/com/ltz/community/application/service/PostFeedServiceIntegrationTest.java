package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.response.PostPageResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.InvalidPostQueryException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

import jakarta.persistence.EntityManager;

/** Akış sorgusunu gerçek PostgreSQL üzerinde çalıştırır (keyset sayfalama, görünürlük, görsel birleştirme). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostFeedService.class, PostAccess.class, PostMapper.class })
class PostFeedServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant BASE = Instant.parse("2026-10-21T10:00:00Z");

	@MockitoBean
	private CurrentUserPort currentUser;

	@Autowired
	private PostFeedService feed;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostImageRepository postImageRepository;

	private Post save(String body, Instant createdAt) {
		return postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), body, createdAt));
	}

	private Post saveWithStatus(String body, Instant createdAt, PostStatus status) {
		Post post = Post.publish(UUID.randomUUID(), body, createdAt);
		if (status == PostStatus.DELETED) {
			post.markDeleted(createdAt);
		} else {
			ReflectionTestUtils.setField(post, "status", status);
		}
		return postRepository.saveAndFlush(post);
	}

	private static PostFeedRequest request(Integer limit, String cursor) {
		return new PostFeedRequest(null, null, null, null, null, null, limit, cursor);
	}

	private static PostFeedRequest search(String q) {
		return new PostFeedRequest(q, null, null, null, null, null, null, null);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	/** Sayaçları Faz 3'ün yerine doğrudan günceller (veritabanı popularity_score'u hesaplar). */
	private void setCounters(Post post, int likes, int comments) {
		jdbcTemplate.update("update posts set like_count = ?, comment_count = ? where id = ?", likes, comments, post.getId());
		entityManager.clear();
	}

	private List<String> allBodies(PostSort sort, int limit) {
		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			PostPageResponse page = feed.listPosts(new PostFeedRequest(null, null, null, null, null, sort, limit, cursor));
			page.items().forEach(item -> seen.add(item.body()));
			cursor = page.nextCursor();
		} while (cursor != null);
		return seen;
	}

	private Post saveFull(String body, UUID gameId, PostCategory category, Instant createdAt) {
		return postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), body, gameId, category, createdAt));
	}

	private List<String> filtered(UUID gameId, PostCategory category, Instant from, Instant to) {
		return feed.listPosts(new PostFeedRequest(null, gameId, category, from, to, null, null, null)).items().stream()
				.map(PostViewResponse::body).toList();
	}

	private List<String> searchBodies(String q) {
		return feed.listPosts(search(q)).items().stream().map(PostViewResponse::body).toList();
	}

	private static List<UUID> ids(PostPageResponse page) {
		return page.items().stream().map(PostViewResponse::id).toList();
	}

	@Test
	void listsNewestPostsFirst() {
		Post oldest = save("eski", BASE);
		Post middle = save("orta", BASE.plusSeconds(60));
		Post newest = save("yeni", BASE.plusSeconds(120));

		PostPageResponse page = feed.listPosts(request(null, null));

		assertThat(ids(page)).containsExactly(newest.getId(), middle.getId(), oldest.getId());
		assertThat(page.nextCursor()).isNull();
		assertThat(page.items().getFirst().body()).isEqualTo("yeni");
		assertThat(page.items().getFirst().authorId()).isEqualTo(newest.getAuthorId());
		assertThat(page.items().getFirst().status()).isEqualTo(PostStatus.VISIBLE);
	}

	@Test
	void excludesHiddenAndDeletedPosts() {
		Post visible = save("görünür", BASE);
		saveWithStatus("gizli", BASE.plusSeconds(10), PostStatus.HIDDEN);
		saveWithStatus("silinmiş", BASE.plusSeconds(20), PostStatus.DELETED);

		PostPageResponse page = feed.listPosts(request(null, null));

		assertThat(ids(page)).containsExactly(visible.getId());
	}

	@Test
	void emptyFeedReturnsEmptyPageWithoutCursor() {
		PostPageResponse page = feed.listPosts(request(10, null));

		assertThat(page.items()).isEmpty();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void pagesThroughAllPostsWithoutDuplicatesOrGaps() {
		List<UUID> expected = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			expected.addFirst(save("gönderi " + i, BASE.plusSeconds(i * 10L)).getId());
		}

		List<UUID> seen = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			PostPageResponse page = feed.listPosts(request(2, cursor));
			assertThat(page.items()).hasSizeLessThanOrEqualTo(2);
			seen.addAll(ids(page));
			cursor = page.nextCursor();
			pages++;
		} while (cursor != null);

		assertThat(pages).isEqualTo(3);
		assertThat(seen).containsExactlyElementsOf(expected);
	}

	@Test
	void postsWithIdenticalTimestampsStillPageDeterministically() {
		List<UUID> all = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			all.add(save("aynı an " + i, BASE).getId());
		}

		List<UUID> seen = new ArrayList<>();
		String cursor = null;
		do {
			PostPageResponse page = feed.listPosts(request(2, cursor));
			seen.addAll(ids(page));
			cursor = page.nextCursor();
		} while (cursor != null);

		assertThat(seen).hasSize(5).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(all)
				.isSortedAccordingTo(java.util.Comparator.<UUID>reverseOrder());
	}

	@Test
	void newPostsDoNotShiftAlreadyIssuedCursor() {
		save("a", BASE);
		save("b", BASE.plusSeconds(10));
		Post third = save("c", BASE.plusSeconds(20));
		PostPageResponse first = feed.listPosts(request(1, null));
		assertThat(ids(first)).containsExactly(third.getId());

		save("sonradan gelen", BASE.plusSeconds(500));
		PostPageResponse second = feed.listPosts(request(1, first.nextCursor()));

		assertThat(second.items()).extracting(PostViewResponse::body).containsExactly("b");
	}

	@Test
	void defaultLimitIsTwentyAndNextCursorIsProvided() {
		for (int i = 0; i < 25; i++) {
			save("gönderi " + i, BASE.plusSeconds(i));
		}

		PostPageResponse page = feed.listPosts(request(null, null));

		assertThat(page.items()).hasSize(20);
		assertThat(page.nextCursor()).isNotBlank();
		assertThat(feed.listPosts(request(null, page.nextCursor())).items()).hasSize(5);
	}

	@Test
	void attachesImageMetadataWithoutContent() {
		Post withImages = save("görselli", BASE.plusSeconds(10));
		Post plain = save("görselsiz", BASE);
		postImageRepository.saveAndFlush(PostImage.attach(withImages.getId(), new byte[] { 1, 2, 3 }, "image/png", 30, 20, 1, BASE));
		postImageRepository.saveAndFlush(PostImage.attach(withImages.getId(), new byte[] { 4, 5 }, "image/jpeg", 10, 10, 0, BASE));

		PostPageResponse page = feed.listPosts(request(null, null));

		PostViewResponse first = page.items().getFirst();
		assertThat(first.id()).isEqualTo(withImages.getId());
		assertThat(first.images()).extracting(image -> image.sortOrder()).containsExactly(0, 1);
		assertThat(first.images().getFirst().mimeType()).isEqualTo("image/jpeg");
		assertThat(first.images().getFirst().contentUrl())
				.isEqualTo("/api/v1/community/posts/" + withImages.getId() + "/images/" + first.images().getFirst().id());
		assertThat(page.items().get(1).id()).isEqualTo(plain.getId());
		assertThat(page.items().get(1).images()).isEmpty();
	}

	@Test
	void detailShowsPostAuthorDatesCountersGameAndImagesToAnonymousVisitor() {
		UUID gameId = UUID.randomUUID();
		Post post = postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "ayrıntı", gameId, BASE));
		postImageRepository.saveAndFlush(PostImage.attach(post.getId(), new byte[] { 1 }, "image/png", 4, 5, 0, BASE));
		when(currentUser.findUserId()).thenReturn(Optional.empty());

		PostViewResponse view = feed.getPost(post.getId());

		assertThat(view.id()).isEqualTo(post.getId());
		assertThat(view.authorId()).isEqualTo(post.getAuthorId());
		assertThat(view.gameId()).isEqualTo(gameId);
		assertThat(view.body()).isEqualTo("ayrıntı");
		assertThat(view.createdAt()).isEqualTo(BASE);
		assertThat(view.updatedAt()).isEqualTo(BASE);
		assertThat(view.likeCount()).isZero();
		assertThat(view.commentCount()).isZero();
		assertThat(view.images()).hasSize(1);
		assertThat(view.images().getFirst().width()).isEqualTo(4);
	}

	@Test
	void detailOfHiddenPostIsVisibleOnlyToItsAuthor() {
		Post hidden = saveWithStatus("gizli", BASE, PostStatus.HIDDEN);
		UUID hiddenId = hidden.getId();

		when(currentUser.findUserId()).thenReturn(Optional.of(hidden.getAuthorId()));
		assertThat(feed.getPost(hiddenId).status()).isEqualTo(PostStatus.HIDDEN);

		when(currentUser.findUserId()).thenReturn(Optional.of(UUID.randomUUID()));
		assertThatThrownBy(() -> feed.getPost(hiddenId)).isInstanceOf(PostNotFoundException.class);

		when(currentUser.findUserId()).thenReturn(Optional.empty());
		assertThatThrownBy(() -> feed.getPost(hiddenId)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void detailOfDeletedOrUnknownPostIsNotFoundForEveryone() {
		Post deleted = saveWithStatus("silinmiş", BASE, PostStatus.DELETED);
		when(currentUser.findUserId()).thenReturn(Optional.of(deleted.getAuthorId()));
		UUID unknown = UUID.randomUUID();
		UUID deletedId = deleted.getId();

		assertThatThrownBy(() -> feed.getPost(deletedId)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> feed.getPost(unknown)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void feedItemsExposeInteractionCounters() {
		Post post = save("sayaçlı", BASE);

		PostViewResponse item = feed.listPosts(request(null, null)).items().getFirst();

		assertThat(item.id()).isEqualTo(post.getId());
		assertThat(item.likeCount()).isZero();
		assertThat(item.commentCount()).isZero();
	}

	@Test
	void searchFindsSubstringCaseInsensitivelyNewestFirst() {
		save("Elden Ring rehberi", BASE);
		save("Zelda hakkında", BASE.plusSeconds(10));
		save("ELDEN RING boss taktiği", BASE.plusSeconds(20));

		assertThat(searchBodies("elden ring")).containsExactly("ELDEN RING boss taktiği", "Elden Ring rehberi");
		assertThat(searchBodies("  ZELDA  ")).containsExactly("Zelda hakkında");
	}

	@Test
	void searchMatchesLowercaseTurkishCharactersExactly() {
		save("çok güzel bir oyun", BASE);

		assertThat(searchBodies("çok güzel")).containsExactly("çok güzel bir oyun");
		assertThat(searchBodies("güzel bir")).hasSize(1);
	}

	@Test
	void searchReturnsEmptyPageWhenNothingMatches() {
		save("Zelda", BASE);

		PostPageResponse page = feed.listPosts(search("mario"));

		assertThat(page.items()).isEmpty();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void searchNeverReturnsHiddenOrDeletedPosts() {
		save("rehber görünür", BASE);
		saveWithStatus("rehber gizli", BASE.plusSeconds(10), PostStatus.HIDDEN);
		saveWithStatus("rehber silinmiş", BASE.plusSeconds(20), PostStatus.DELETED);

		assertThat(searchBodies("rehber")).containsExactly("rehber görünür");
	}

	@Test
	void likeWildcardsInSearchTextAreTreatedAsPlainCharacters() {
		save("indirim %50 başladı", BASE);
		save("indirim 1500 oldu", BASE.plusSeconds(10));
		save("a_b kodu", BASE.plusSeconds(20));
		save("axb kodu", BASE.plusSeconds(30));
		save("yol C:\\oyunlar", BASE.plusSeconds(40));

		assertThat(searchBodies("%50")).containsExactly("indirim %50 başladı");
		assertThat(searchBodies("a_b")).containsExactly("a_b kodu");
		assertThat(searchBodies("c:\\oyun")).containsExactly("yol C:\\oyunlar");
		assertThat(searchBodies("%%")).isEmpty();
	}

	@Test
	void sqlInjectionAttemptIsSearchedAsLiteralText() {
		save("normal gönderi", BASE);

		assertThat(searchBodies("' or 1=1 --")).isEmpty();
		assertThat(searchBodies("x'); drop table posts; --")).isEmpty();
		assertThat(feed.listPosts(request(null, null)).items()).hasSize(1);
	}

	@Test
	void searchResultsPageWithCursor() {
		for (int i = 0; i < 5; i++) {
			save("rehber " + i, BASE.plusSeconds(i));
		}
		save("alakasız", BASE.plusSeconds(100));

		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			PostPageResponse page = feed.listPosts(new PostFeedRequest("rehber", null, null, null, null, null, 2, cursor));
			page.items().forEach(item -> seen.add(item.body()));
			cursor = page.nextCursor();
		} while (cursor != null);

		assertThat(seen).containsExactly("rehber 4", "rehber 3", "rehber 2", "rehber 1", "rehber 0");
	}

	@Test
	void blankSearchTextMeansNoSearch() {
		save("bir", BASE);
		save("iki", BASE.plusSeconds(1));

		assertThat(searchBodies("   ")).hasSize(2);
		assertThat(searchBodies("")).hasSize(2);
	}

	@Test
	void singleCharacterSearchIsRejected() {
		PostFeedRequest tooShort = search("a");

		assertThatThrownBy(() -> feed.listPosts(tooShort))
				.isInstanceOf(InvalidPostQueryException.class)
				.hasMessageContaining("at least 2");
	}

	@Test
	void filtersByRelatedGame() {
		UUID eldenRing = UUID.randomUUID();
		UUID zelda = UUID.randomUUID();
		saveFull("elden 1", eldenRing, null, BASE);
		saveFull("zelda", zelda, null, BASE.plusSeconds(10));
		saveFull("elden 2", eldenRing, null, BASE.plusSeconds(20));
		saveFull("oyunsuz", null, null, BASE.plusSeconds(30));

		assertThat(filtered(eldenRing, null, null, null)).containsExactly("elden 2", "elden 1");
		assertThat(filtered(zelda, null, null, null)).containsExactly("zelda");
		assertThat(filtered(UUID.randomUUID(), null, null, null)).isEmpty();
	}

	@Test
	void postsWithoutCategoryAreGeneralAndCanBeFilteredByCategory() {
		saveFull("genel", null, null, BASE);
		saveFull("rehber", null, PostCategory.GUIDE, BASE.plusSeconds(10));
		saveFull("soru", null, PostCategory.QUESTION, BASE.plusSeconds(20));
		saveFull("ekran görüntüsü", null, PostCategory.SCREENSHOT, BASE.plusSeconds(30));

		assertThat(filtered(null, PostCategory.GENERAL, null, null)).containsExactly("genel");
		assertThat(filtered(null, PostCategory.GUIDE, null, null)).containsExactly("rehber");
		assertThat(filtered(null, PostCategory.QUESTION, null, null)).containsExactly("soru");
		assertThat(filtered(null, PostCategory.SCREENSHOT, null, null)).containsExactly("ekran görüntüsü");
		assertThat(feed.listPosts(request(null, null)).items()).extracting(PostViewResponse::category)
				.containsExactly(PostCategory.SCREENSHOT, PostCategory.QUESTION, PostCategory.GUIDE, PostCategory.GENERAL);
	}

	@Test
	void dateRangeIncludesFromAndExcludesTo() {
		saveFull("t0", null, null, BASE);
		saveFull("t1", null, null, BASE.plusSeconds(60));
		saveFull("t2", null, null, BASE.plusSeconds(120));
		saveFull("t3", null, null, BASE.plusSeconds(180));

		assertThat(filtered(null, null, BASE.plusSeconds(60), BASE.plusSeconds(180))).containsExactly("t2", "t1");
		assertThat(filtered(null, null, BASE.plusSeconds(120), null)).containsExactly("t3", "t2");
		assertThat(filtered(null, null, null, BASE.plusSeconds(60))).containsExactly("t0");
		assertThat(filtered(null, null, BASE.plusSeconds(500), null)).isEmpty();
	}

	@Test
	void invertedOrEqualDateRangeIsRejected() {
		PostFeedRequest inverted = new PostFeedRequest(null, null, null, BASE.plusSeconds(10), BASE, null, null, null);
		PostFeedRequest equal = new PostFeedRequest(null, null, null, BASE, BASE, null, null, null);

		assertThatThrownBy(() -> feed.listPosts(inverted)).isInstanceOf(InvalidPostQueryException.class);
		assertThatThrownBy(() -> feed.listPosts(equal)).isInstanceOf(InvalidPostQueryException.class);
	}

	@Test
	void filtersCombineWithEachOtherSearchAndPaging() {
		UUID game = UUID.randomUUID();
		for (int i = 0; i < 4; i++) {
			saveFull("rehber boss " + i, game, PostCategory.GUIDE, BASE.plusSeconds(i * 10L));
		}
		saveFull("rehber başka oyun", UUID.randomUUID(), PostCategory.GUIDE, BASE.plusSeconds(100));
		saveFull("rehber soru", game, PostCategory.QUESTION, BASE.plusSeconds(110));
		saveFull("alakasız", game, PostCategory.GUIDE, BASE.plusSeconds(120));
		saveWithStatus("rehber gizli", BASE.plusSeconds(130), PostStatus.HIDDEN);

		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			PostPageResponse page = feed.listPosts(new PostFeedRequest("rehber", game, PostCategory.GUIDE,
					BASE.plusSeconds(10), BASE.plusSeconds(200), null, 2, cursor));
			page.items().forEach(item -> seen.add(item.body()));
			cursor = page.nextCursor();
		} while (cursor != null);

		assertThat(seen).containsExactly("rehber boss 3", "rehber boss 2", "rehber boss 1");
	}

	@Test
	void filtersNeverRevealHiddenOrDeletedPosts() {
		UUID game = UUID.randomUUID();
		saveFull("görünür", game, PostCategory.GUIDE, BASE);
		Post hidden = Post.publish(UUID.randomUUID(), "gizli", game, PostCategory.GUIDE, BASE.plusSeconds(10));
		ReflectionTestUtils.setField(hidden, "status", PostStatus.HIDDEN);
		postRepository.saveAndFlush(hidden);

		assertThat(filtered(game, PostCategory.GUIDE, null, null)).containsExactly("görünür");
	}

	@Test
	void newestIsTheDefaultSortAndOldestReversesIt() {
		save("a", BASE);
		save("b", BASE.plusSeconds(10));
		save("c", BASE.plusSeconds(20));

		assertThat(allBodies(null, 10)).containsExactly("c", "b", "a");
		assertThat(allBodies(PostSort.NEWEST, 10)).containsExactly("c", "b", "a");
		assertThat(allBodies(PostSort.OLDEST, 10)).containsExactly("a", "b", "c");
	}

	@Test
	void oldestPagesWithoutGapsEvenWhenTimestampsTie() {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			ids.add(save("aynı an " + i, BASE).getId());
		}
		save("sonra", BASE.plusSeconds(60));

		List<UUID> seen = new ArrayList<>();
		String cursor = null;
		do {
			PostPageResponse page = feed.listPosts(new PostFeedRequest(null, null, null, null, null, PostSort.OLDEST, 1, cursor));
			seen.addAll(ids(page));
			cursor = page.nextCursor();
		} while (cursor != null);

		assertThat(seen).hasSize(4).doesNotHaveDuplicates();
		assertThat(seen.subList(0, 3)).isSortedAccordingTo(java.util.Comparator.<UUID>naturalOrder());
		assertThat(seen.get(3)).isNotIn(ids);
	}

	@Test
	void popularSortsByEngagementThenNewest() {
		Post oldPopular = save("eski popüler", BASE);
		Post newPopular = save("yeni popüler", BASE.plusSeconds(10));
		Post mid = save("orta", BASE.plusSeconds(20));
		save("hiç etkileşim yok", BASE.plusSeconds(30));
		setCounters(oldPopular, 3, 2);
		setCounters(newPopular, 5, 0);
		setCounters(mid, 0, 1);

		assertThat(allBodies(PostSort.POPULAR, 10))
				.containsExactly("yeni popüler", "eski popüler", "orta", "hiç etkileşim yok");
	}

	@Test
	void popularPagesConsistentlyAcrossScoreTiesAndAllPageSizes() {
		for (int i = 0; i < 6; i++) {
			Post post = save("gönderi " + i, BASE.plusSeconds(i * 10L));
			setCounters(post, i % 3, 0);
		}
		List<String> everything = allBodies(PostSort.POPULAR, 50);

		assertThat(everything).containsExactly(
				"gönderi 5", "gönderi 2", "gönderi 4", "gönderi 1", "gönderi 3", "gönderi 0");
		for (int size = 1; size <= 4; size++) {
			assertThat(allBodies(PostSort.POPULAR, size)).containsExactlyElementsOf(everything);
		}
	}

	@Test
	void popularWithoutAnyEngagementFallsBackToNewestFirst() {
		save("eski", BASE);
		save("yeni", BASE.plusSeconds(10));

		assertThat(allBodies(PostSort.POPULAR, 10)).containsExactly("yeni", "eski");
	}

	@Test
	void sortingCombinesWithFiltersAndKeepsHiddenPostsOut() {
		UUID game = UUID.randomUUID();
		Post low = saveFull("düşük", game, PostCategory.GUIDE, BASE);
		Post high = saveFull("yüksek", game, PostCategory.GUIDE, BASE.plusSeconds(10));
		saveFull("başka oyun", UUID.randomUUID(), PostCategory.GUIDE, BASE.plusSeconds(20));
		setCounters(low, 1, 0);
		setCounters(high, 9, 0);
		Post hidden = Post.publish(UUID.randomUUID(), "gizli ama popüler", game, PostCategory.GUIDE, BASE.plusSeconds(30));
		ReflectionTestUtils.setField(hidden, "status", PostStatus.HIDDEN);
		setCounters(postRepository.saveAndFlush(hidden), 99, 99);

		PostPageResponse page = feed.listPosts(new PostFeedRequest(null, game, PostCategory.GUIDE, null, null, PostSort.POPULAR, null, null));

		assertThat(page.items()).extracting(PostViewResponse::body).containsExactly("yüksek", "düşük");
		assertThat(page.items().getFirst().likeCount()).isEqualTo(9);
	}

	@Test
	void cursorFromAnotherSortOrderIsRejected() {
		save("a", BASE);
		save("b", BASE.plusSeconds(10));
		String newestCursor = feed.listPosts(request(1, null)).nextCursor();

		PostFeedRequest popular = new PostFeedRequest(null, null, null, null, null, PostSort.POPULAR, 1, newestCursor);
		PostFeedRequest oldest = new PostFeedRequest(null, null, null, null, null, PostSort.OLDEST, 1, newestCursor);

		assertThatThrownBy(() -> feed.listPosts(popular)).isInstanceOf(InvalidPostQueryException.class);
		assertThatThrownBy(() -> feed.listPosts(oldest)).isInstanceOf(InvalidPostQueryException.class);
	}

	@Test
	void userHistoryListsOnlyThatUsersVisiblePostsToOthersAndAnonymous() {
		UUID author = UUID.randomUUID();
		postRepository.saveAndFlush(Post.publish(author, "benim 1", BASE));
		postRepository.saveAndFlush(Post.publish(author, "benim 2", BASE.plusSeconds(10)));
		postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "başkasının", BASE.plusSeconds(20)));
		Post hidden = Post.publish(author, "gizli", BASE.plusSeconds(30));
		ReflectionTestUtils.setField(hidden, "status", PostStatus.HIDDEN);
		postRepository.saveAndFlush(hidden);
		Post deleted = Post.publish(author, "silinmiş", BASE.plusSeconds(40));
		deleted.markDeleted(BASE.plusSeconds(41));
		postRepository.saveAndFlush(deleted);

		when(currentUser.findUserId()).thenReturn(Optional.empty());
		assertThat(feed.listUserPosts(author, request(null, null)).items()).extracting(PostViewResponse::body)
				.containsExactly("benim 2", "benim 1");

		when(currentUser.findUserId()).thenReturn(Optional.of(UUID.randomUUID()));
		assertThat(feed.listUserPosts(author, request(null, null)).items()).extracting(PostViewResponse::body)
				.containsExactly("benim 2", "benim 1");
	}

	@Test
	void userSeesOwnHiddenPostsInOwnHistoryButNeverDeletedOnes() {
		UUID author = UUID.randomUUID();
		postRepository.saveAndFlush(Post.publish(author, "görünür", BASE));
		Post hidden = Post.publish(author, "gizlenmiş", BASE.plusSeconds(10));
		ReflectionTestUtils.setField(hidden, "status", PostStatus.HIDDEN);
		postRepository.saveAndFlush(hidden);
		Post deleted = Post.publish(author, "silinmiş", BASE.plusSeconds(20));
		deleted.markDeleted(BASE.plusSeconds(21));
		postRepository.saveAndFlush(deleted);
		when(currentUser.findUserId()).thenReturn(Optional.of(author));

		List<PostViewResponse> items = feed.listUserPosts(author, request(null, null)).items();

		assertThat(items).extracting(PostViewResponse::body).containsExactly("gizlenmiş", "görünür");
		assertThat(items).extracting(PostViewResponse::status).containsExactly(PostStatus.HIDDEN, PostStatus.VISIBLE);
	}

	@Test
	void viewingAnotherUsersHistoryNeverExposesHiddenPostsEvenIfRequestedUserIsLoggedInElsewhere() {
		UUID author = UUID.randomUUID();
		Post hidden = Post.publish(author, "gizli", BASE);
		ReflectionTestUtils.setField(hidden, "status", PostStatus.HIDDEN);
		postRepository.saveAndFlush(hidden);
		when(currentUser.findUserId()).thenReturn(Optional.of(UUID.randomUUID()));

		assertThat(feed.listUserPosts(author, request(null, null)).items()).isEmpty();
	}

	@Test
	void userHistorySupportsPagingSearchFiltersAndSorting() {
		UUID author = UUID.randomUUID();
		UUID game = UUID.randomUUID();
		for (int i = 0; i < 5; i++) {
			postRepository.saveAndFlush(Post.publish(author, "rehber " + i, game, PostCategory.GUIDE, BASE.plusSeconds(i * 10L)));
		}
		postRepository.saveAndFlush(Post.publish(author, "alakasız", BASE.plusSeconds(100)));
		postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "rehber başkası", game, PostCategory.GUIDE, BASE));
		when(currentUser.findUserId()).thenReturn(Optional.empty());

		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			PostPageResponse page = feed.listUserPosts(author,
					new PostFeedRequest("rehber", game, PostCategory.GUIDE, null, null, PostSort.OLDEST, 2, cursor));
			page.items().forEach(item -> seen.add(item.body()));
			cursor = page.nextCursor();
		} while (cursor != null);

		assertThat(seen).containsExactly("rehber 0", "rehber 1", "rehber 2", "rehber 3", "rehber 4");
	}

	@Test
	void unknownUserHasEmptyHistoryWithoutError() {
		save("başkası", BASE);
		when(currentUser.findUserId()).thenReturn(Optional.empty());

		PostPageResponse page = feed.listUserPosts(UUID.randomUUID(), request(null, null));

		assertThat(page.items()).isEmpty();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void userHistoryValidatesParametersLikeTheFeed() {
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		UUID user = UUID.randomUUID();
		PostFeedRequest badCursor = request(10, "bozuk");
		PostFeedRequest shortSearch = search("a");

		assertThatThrownBy(() -> feed.listUserPosts(user, badCursor)).isInstanceOf(InvalidPostQueryException.class);
		assertThatThrownBy(() -> feed.listUserPosts(user, shortSearch)).isInstanceOf(InvalidPostQueryException.class);
	}

	@Test
	void invalidCursorIsRejected() {
		PostFeedRequest bad = request(10, "bozuk-imlec");

		assertThatThrownBy(() -> feed.listPosts(bad)).isInstanceOf(InvalidPostQueryException.class);
	}
}
