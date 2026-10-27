package com.ltz.community.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.application.mapper.CommentMapper;
import com.ltz.community.domain.enums.CommentStatus;

class CommentTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
	private static final UUID POST_ID = UUID.randomUUID();
	private static final UUID AUTHOR = UUID.randomUUID();

	@Test
	void writeCreatesAVisibleCommentWithRelation() {
		UUID parent = UUID.randomUUID();

		Comment comment = Comment.write(POST_ID, AUTHOR, parent, "yanıt", NOW);

		assertThat(comment.getPostId()).isEqualTo(POST_ID);
		assertThat(comment.getAuthorId()).isEqualTo(AUTHOR);
		assertThat(comment.getParentCommentId()).isEqualTo(parent);
		assertThat(comment.getStatus()).isEqualTo(CommentStatus.VISIBLE);
		assertThat(comment.isVisible()).isTrue();
		assertThat(comment.isOwnedBy(AUTHOR)).isTrue();
		assertThat(comment.isOwnedBy(UUID.randomUUID())).isFalse();
		assertThat(comment.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void editBodyTouchesUpdatedAtOnlyOnRealChange() {
		Comment comment = Comment.write(POST_ID, AUTHOR, null, "ilk", NOW);

		comment.editBody("ilk", NOW.plusSeconds(5));
		assertThat(comment.getUpdatedAt()).isEqualTo(NOW);

		comment.editBody("ikinci", NOW.plusSeconds(10));
		assertThat(comment.getBody()).isEqualTo("ikinci");
		assertThat(comment.getUpdatedAt()).isEqualTo(NOW.plusSeconds(10));
	}

	@Test
	void markDeletedIsASoftDeleteAndMapperHidesContentAndAuthor() {
		Comment comment = Comment.write(POST_ID, AUTHOR, null, "gizli", NOW);
		CommentMapper mapper = new CommentMapper();
		assertThat(mapper.toResponse(comment, 2).body()).isEqualTo("gizli");

		comment.markDeleted(NOW.plusSeconds(1));

		assertThat(comment.isVisible()).isFalse();
		assertThat(comment.getDeletedAt()).isEqualTo(NOW.plusSeconds(1));
		CommentResponse placeholder = mapper.toResponse(comment, 2);
		assertThat(placeholder.status()).isEqualTo(CommentStatus.DELETED);
		assertThat(placeholder.body()).isNull();
		assertThat(placeholder.authorId()).isNull();
		assertThat(placeholder.replyCount()).isEqualTo(2);
	}
}
