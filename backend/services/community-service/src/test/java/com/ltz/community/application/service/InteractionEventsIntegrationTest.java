package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ltz.community.EventTestConfig;
import com.ltz.community.api.dto.request.CreateCommentRequest;
import com.ltz.community.api.dto.request.CreateRepostRequest;
import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.application.mapper.CommentMapper;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/** Etkileşimlerin bildirim olaylarını (COM-18) ve alıcı kurallarını gerçek PostgreSQL üzerinde doğrular. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, PostLikeService.class, CommentService.class, RepostService.class, PostAccess.class,
		PostViewAssembler.class, PostMapper.class, CommentMapper.class })
class InteractionEventsIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

	@MockitoBean
	private CurrentUserPort currentUser;

	@Autowired
	private PostLikeService likes;

	@Autowired
	private CommentService comments;

	@Autowired
	private RepostService reposts;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final UUID author = UUID.randomUUID();
	private final UUID visitor = UUID.randomUUID();

	private void actAs(UUID userId) {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(currentUser.findUserId()).thenReturn(Optional.of(userId));
	}

	private Post post() {
		Post post = postRepository.saveAndFlush(Post.publish(author, "gönderi metni", NOW));
		scope = post.getId();
		return post;
	}

	private UUID scope;

	/** Yalnız bu testin gönderisine ait olayları döndürür (diğer testlerin artıkları karışmaz). */
	private List<Map<String, Object>> events(String type) {
		return jdbcTemplate.queryForList(
				"select aggregate_type, aggregate_id, cast(payload as text) as payload from outbox_events "
						+ "where event_type = ? and cast(payload as text) like ? order by occurred_at, id",
				type, "%" + scope + "%");
	}

	@Test
	void firstLikeNotifiesThePostAuthorAndRepeatsDoNot() {
		Post post = post();
		actAs(visitor);

		likes.like(post.getId());
		likes.like(post.getId());
		likes.unlike(post.getId());

		List<Map<String, Object>> liked = events("PostLiked");
		assertThat(liked).hasSize(1);
		assertThat(liked.getFirst()).containsEntry("aggregate_type", "POST").containsEntry("aggregate_id", post.getId());
		String payload = (String) liked.getFirst().get("payload");
		assertThat(payload).contains(post.getId().toString(), visitor.toString(), author.toString())
				.doesNotContain("gönderi metni");
	}

	@Test
	void likingYourOwnPostNotifiesNobody() {
		Post post = post();
		actAs(author);

		likes.like(post.getId());

		assertThat(events("PostLiked")).isEmpty();
	}

	@Test
	void commentNotifiesThePostAuthorWithoutTheCommentText() {
		Post post = post();
		actAs(visitor);

		CommentResponse comment = comments.createComment(post.getId(), new CreateCommentRequest("özel yorum metni", null));

		List<Map<String, Object>> created = events("CommentCreated");
		assertThat(created).hasSize(1);
		assertThat(created.getFirst()).containsEntry("aggregate_type", "COMMENT").containsEntry("aggregate_id", comment.id());
		String payload = (String) created.getFirst().get("payload");
		assertThat(payload).contains(author.toString(), visitor.toString(), "\"isReply\": false")
				.doesNotContain("özel yorum metni");
	}

	@Test
	void replyNotifiesThePostAuthorAndTheParentCommentAuthorOnce() {
		Post post = post();
		UUID commenter = UUID.randomUUID();
		actAs(commenter);
		CommentResponse parent = comments.createComment(post.getId(), new CreateCommentRequest("ilk", null));
		actAs(visitor);

		comments.createComment(post.getId(), new CreateCommentRequest("yanıt", parent.id()));

		String payload = (String) events("CommentCreated").getLast().get("payload");
		assertThat(payload).contains(author.toString(), commenter.toString(), "\"isReply\": true");
		assertThat(payload.split(author.toString(), -1)).hasSize(2);
	}

	@Test
	void postAuthorReplyingToACommenterNotifiesOnlyTheCommenter() {
		Post post = post();
		UUID commenter = UUID.randomUUID();
		actAs(commenter);
		CommentResponse parent = comments.createComment(post.getId(), new CreateCommentRequest("ilk", null));
		actAs(author);

		comments.createComment(post.getId(), new CreateCommentRequest("teşekkürler", parent.id()));

		String payload = (String) events("CommentCreated").getLast().get("payload");
		assertThat(payload).contains(commenter.toString()).contains("\"recipientIds\": [\"" + commenter + "\"]");
	}

	@Test
	void commentingOnYourOwnPostOrRepliesToYourselfNotifyNobody() {
		Post post = post();
		actAs(author);
		CommentResponse own = comments.createComment(post.getId(), new CreateCommentRequest("kendi gönderime", null));
		comments.createComment(post.getId(), new CreateCommentRequest("kendi yorumuma", own.id()));

		assertThat(events("CommentCreated")).isEmpty();
	}

	@Test
	void repostNotifiesTheOriginalAuthorButSelfRepostDoesNot() {
		Post post = post();
		actAs(visitor);
		reposts.repost(post.getId(), new CreateRepostRequest("not"));

		List<Map<String, Object>> reposted = events("PostReposted");
		assertThat(reposted).hasSize(1);
		assertThat(reposted.getFirst()).containsEntry("aggregate_id", post.getId());
		assertThat((String) reposted.getFirst().get("payload")).contains(author.toString(), visitor.toString())
				.doesNotContain("\"not\"");

		actAs(author);
		reposts.repost(post.getId(), null);
		assertThat(events("PostReposted")).hasSize(1);
	}
}
