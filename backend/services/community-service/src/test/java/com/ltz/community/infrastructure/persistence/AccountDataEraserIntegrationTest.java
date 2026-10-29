package com.ltz.community.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ltz.community.EventTestConfig;
import com.ltz.community.application.port.AccountErasurePort;
import com.ltz.community.application.service.AccountErasureService;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.entity.Report;
import com.ltz.community.domain.enums.ReportReason;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.infrastructure.persistence.repository.CommentRepository;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostLikeRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.ReportRepository;
import com.ltz.community.infrastructure.persistence.repository.UserBlockRepository;

import jakarta.persistence.EntityManager;

/**
 * Auth'un hesap silme çağrısının veri etkisi gerçek PostgreSQL üzerinde: silinen hesabın hiçbir kişisel verisi kalmaz,
 * başkalarının verisi (yorumlar, beğeniler, ilgisiz içerik) bozulmaz, işlem idempotenttir (D4 rehberi §3, §5).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, AccountDataEraser.class, AccountErasureService.class })
class AccountDataEraserIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
	private static final UUID ANONYMOUS = AccountErasurePort.DELETED_ACCOUNT_ID;

	@Autowired
	private AccountErasureService erasure;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostImageRepository postImageRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private PostLikeRepository postLikeRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private UserBlockRepository userBlockRepository;

	@Autowired
	private ForumRepository forumRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private final UUID account = UUID.randomUUID();
	private final UUID other = UUID.randomUUID();
	private final UUID third = UUID.randomUUID();

	private Post accountPost;
	private Post accountDeletedPost;
	private Post accountRepost;
	private Post otherPost;
	private Post otherPostTwo;
	private Comment othersCommentOnAccountPost;
	private Comment accountCommentOnOthersPost;
	private Comment othersReplyToAccountComment;
	private Report reportByAccount;
	private Report reportAboutAccount;
	private Report unrelatedReport;

	@BeforeEach
	void seed() {
		accountPost = postRepository.saveAndFlush(Post.publish(account, "hesabın gizli gönderisi", NOW));
		accountDeletedPost = Post.publish(account, "daha önce silinmiş ama metni saklanan gönderi", NOW);
		accountDeletedPost.markDeleted(NOW);
		accountDeletedPost = postRepository.saveAndFlush(accountDeletedPost);
		otherPost = postRepository.saveAndFlush(Post.publish(other, "başkasının gönderisi", NOW));
		otherPostTwo = postRepository.saveAndFlush(Post.publish(other, "başkasının ikinci gönderisi", NOW));
		accountRepost = postRepository.saveAndFlush(Post.repost(account, otherPostTwo.getId(), "hesabın repost notu", NOW));
		postRepository.saveAndFlush(Post.repost(account, otherPost.getId(), null, NOW));
		postImageRepository.saveAndFlush(PostImage.attach(accountPost.getId(), new byte[] { 1, 2 }, "image/png", 2, 2, 0, NOW));

		othersCommentOnAccountPost = commentRepository.saveAndFlush(
				Comment.write(accountPost.getId(), other, null, "başkasının yorumu", NOW));
		accountCommentOnOthersPost = commentRepository.saveAndFlush(
				Comment.write(otherPost.getId(), account, null, "hesabın yorumu", NOW));
		othersReplyToAccountComment = commentRepository.saveAndFlush(
				Comment.write(otherPost.getId(), other, accountCommentOnOthersPost.getId(), "başkasının yanıtı", NOW));
		Comment accountDeletedComment = Comment.write(otherPost.getId(), account, null, "silinmiş ama saklanan yorum", NOW);
		accountDeletedComment.markDeleted(NOW);
		commentRepository.saveAndFlush(accountDeletedComment);
		// Sayaçlar: otherPost'ta 2 görünür yorum var (hesabın + başkasının yanıtı) ve hesap otherPost'u beğenmiş.
		postLikeRepository.insertIfAbsent(otherPost.getId(), account, NOW);
		postLikeRepository.insertIfAbsent(otherPost.getId(), third, NOW);
		postLikeRepository.insertIfAbsent(accountPost.getId(), other, NOW);
		jdbcTemplate.update("update posts set like_count = 2, comment_count = 2 where id = ?", otherPost.getId());
		jdbcTemplate.update("update posts set like_count = 1, comment_count = 1 where id = ?", accountPost.getId());

		reportByAccount = reportRepository.saveAndFlush(
				Report.open(ReportTarget.POST, otherPost.getId(), other, account, ReportReason.SPAM, "hesabın raporu", NOW));
		reportAboutAccount = reportRepository.saveAndFlush(
				Report.open(ReportTarget.POST, accountPost.getId(), account, other, ReportReason.HARASSMENT, null, NOW));
		unrelatedReport = reportRepository.saveAndFlush(
				Report.open(ReportTarget.POST, otherPost.getId(), other, third, ReportReason.OTHER, null, NOW));

		userBlockRepository.insertIfAbsent(account, other, NOW);
		userBlockRepository.insertIfAbsent(third, account, NOW);
		userBlockRepository.insertIfAbsent(other, third, NOW);
		forumRepository.saveAndFlush(Forum.open(account, null, "hesabin-forumu", "Forum", null, NOW));

		outbox("PostLiked", "{\"actorId\": \"" + account + "\", \"recipientId\": \"" + other + "\"}");
		outbox("CommentCreated", "{\"actorId\": \"" + other + "\", \"recipientIds\": [\"" + account + "\"]}");
		outbox("ContentReported", "{\"reporterId\": \"" + account + "\", \"targetAuthorId\": \"" + other + "\"}");
		outbox("PostLiked", "{\"actorId\": \"" + third + "\", \"recipientId\": \"" + other + "\"}");
		entityManager.clear();
	}

	private void outbox(String type, String payloadJson) {
		jdbcTemplate.update("""
				insert into outbox_events (id, aggregate_type, aggregate_id, event_type, event_version, payload, occurred_at, status)
				values (?, 'POST', ?, ?, 1, cast(? as jsonb), now(), 'PENDING')
				""", UUID.randomUUID(), UUID.randomUUID(), type, payloadJson);
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private Map<String, Object> row(String table, UUID id) {
		return jdbcTemplate.queryForMap("select * from " + table + " where id = ?", id);
	}

	@Test
	void accountsPostsAndCommentsAreAnonymizedAndNoLongerReferenceTheAccount() {
		erasure.erase(account);

		assertThat(count("select count(*) from posts where author_id = ?", account)).isZero();
		assertThat(count("select count(*) from comments where author_id = ?", account)).isZero();
		for (Post post : List.of(accountPost, accountDeletedPost)) {
			Map<String, Object> stored = row("posts", post.getId());
			assertThat(stored).containsEntry("author_id", ANONYMOUS).containsEntry("status", "DELETED")
					.containsEntry("body", AccountErasurePort.REMOVED_CONTENT);
			assertThat(stored.get("deleted_at")).isNotNull();
		}
		// Repost'un metni isteğe bağlıdır: not silinir (null), referans anonimleşir.
		Map<String, Object> repost = row("posts", accountRepost.getId());
		assertThat(repost).containsEntry("author_id", ANONYMOUS).containsEntry("status", "DELETED");
		assertThat(repost.get("body")).isNull();
		assertThat(count("select count(*) from posts where body like '%hesabın%' or body like '%saklanan%'")).isZero();
		assertThat(count("select count(*) from comments where body like '%hesabın%' or body like '%saklanan%'")).isZero();
		assertThat(count("select count(*) from post_images where post_id = ?", accountPost.getId())).isZero();
	}

	@Test
	void othersDataAroundTheAccountIsLeftIntact() {
		erasure.erase(account);

		// Başkasının gönderisi/yorumu/yanıtı silinmez, yanıt ilişkisi korunur.
		assertThat(row("posts", otherPost.getId())).containsEntry("author_id", other).containsEntry("status", "VISIBLE")
				.containsEntry("body", "başkasının gönderisi");
		assertThat(row("comments", othersCommentOnAccountPost.getId())).containsEntry("author_id", other)
				.containsEntry("status", "VISIBLE").containsEntry("body", "başkasının yorumu");
		assertThat(row("comments", othersReplyToAccountComment.getId())).containsEntry("author_id", other)
				.containsEntry("status", "VISIBLE").containsEntry("parent_comment_id", accountCommentOnOthersPost.getId());
		// Başkasının hesabın gönderisine bıraktığı beğeni satırı durur (başkasının verisi).
		assertThat(count("select count(*) from post_likes where user_id = ?", other)).isEqualTo(1);
		assertThat(count("select count(*) from post_likes where user_id = ?", third)).isEqualTo(1);
	}

	@Test
	void likesAreRemovedAndCountersOfTheOtherPostsAreCorrected() {
		erasure.erase(account);

		assertThat(count("select count(*) from post_likes where user_id = ?", account)).isZero();
		Map<String, Object> counters = row("posts", otherPost.getId());
		// like_count 2 → 1 (hesabın beğenisi çıktı); comment_count 2 → 1 (hesabın görünür yorumu çıktı, silinmiş zaten sayılmıyordu).
		assertThat(counters).containsEntry("like_count", 1).containsEntry("comment_count", 1);
		// Hesabın kendi gönderisinin sayaçları başkalarının verisidir.
		assertThat(row("posts", accountPost.getId())).containsEntry("like_count", 1).containsEntry("comment_count", 1);
	}

	@Test
	void reportsByTheAccountAreDeletedAndReportsAboutItLoseOnlyTheAuthorReference() {
		erasure.erase(account);

		assertThat(reportRepository.findById(reportByAccount.getId())).isEmpty();
		assertThat(row("community_reports", reportAboutAccount.getId())).containsEntry("target_author_id", ANONYMOUS)
				.containsEntry("reporter_id", other).containsEntry("status", "OPEN");
		assertThat(row("community_reports", unrelatedReport.getId())).containsEntry("target_author_id", other)
				.containsEntry("reporter_id", third);
		assertThat(count("select count(*) from community_reports where reporter_id = ? or target_author_id = ?", account, account))
				.isZero();
	}

	@Test
	void blocksForumsAndOutboxEventsOfTheAccountAreCleared() {
		erasure.erase(account);

		assertThat(count("select count(*) from user_blocks where blocker_id = ? or blocked_id = ?", account, account)).isZero();
		assertThat(userBlockRepository.existsBetween(other, third)).as("ilgisiz engelleme durur").isTrue();
		assertThat(count("select count(*) from forums where created_by = ?", account)).isZero();
		assertThat(forumRepository.findAll()).anySatisfy(forum -> assertThat(forum.getCreatedBy()).isEqualTo(ANONYMOUS));
		// Hesabın kimliğini taşıyan her olay silinir; ilgisiz olay durur.
		assertThat(count("select count(*) from outbox_events where cast(payload as text) like ?", "%" + account + "%")).isZero();
		assertThat(count("select count(*) from outbox_events where cast(payload as text) like ?", "%" + third + "%")).isEqualTo(1);
	}

	@Test
	void erasureIsIdempotentAndHarmlessForUnknownAccounts() {
		erasure.erase(account);
		int posts = count("select count(*) from posts");
		int comments = count("select count(*) from comments");
		int events = count("select count(*) from outbox_events");

		erasure.erase(account);
		erasure.erase(UUID.randomUUID());

		assertThat(count("select count(*) from posts")).isEqualTo(posts);
		assertThat(count("select count(*) from comments")).isEqualTo(comments);
		assertThat(count("select count(*) from outbox_events")).isEqualTo(events);
		assertThat(row("posts", otherPost.getId())).containsEntry("author_id", other);
		assertThat(row("posts", accountPost.getId())).containsEntry("author_id", ANONYMOUS);
	}

	@Test
	void anonymizedContentIsInvisibleAndTheOwnerCanRepostAgainAfterwards() {
		erasure.erase(account);

		// Anonimleşen repost, "bir kullanıcı bir orijinali bir kez repostlar" kısmi UNIQUE indeksinden çıkar.
		assertThat(count("select count(*) from posts where status = 'VISIBLE' and author_id = ?", ANONYMOUS)).isZero();
		postRepository.saveAndFlush(Post.repost(other, otherPostTwo.getId(), null, NOW));
		assertThat(count("select count(*) from posts where repost_of_id = ? and status <> 'DELETED'", otherPostTwo.getId()))
				.isEqualTo(1);
	}
}
