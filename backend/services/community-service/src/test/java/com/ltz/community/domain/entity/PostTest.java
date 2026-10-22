package com.ltz.community.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostStatus;

class PostTest {

	@Test
	void publishCreatesVisiblePostOwnedByAuthorWithCreationTime() {
		UUID authorId = UUID.randomUUID();
		Instant now = Instant.parse("2026-10-21T10:15:30Z");

		Post post = Post.publish(authorId, "İlk gönderi", now);

		assertThat(post.getAuthorId()).isEqualTo(authorId);
		assertThat(post.getBody()).isEqualTo("İlk gönderi");
		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		assertThat(post.getCreatedAt()).isEqualTo(now);
		assertThat(post.getUpdatedAt()).isEqualTo(now);
		assertThat(post.getDeletedAt()).isNull();
		assertThat(post.getGameId()).isNull();
		assertThat(post.getLikeCount()).isZero();
		assertThat(post.getCommentCount()).isZero();
	}

	@Test
	void categoryDefaultsToGeneralAndChangingItUpdatesTimestampOnlyWhenDifferent() {
		Instant created = Instant.parse("2026-10-21T10:15:30Z");
		Instant later = created.plusSeconds(60);
		Post defaulted = Post.publish(UUID.randomUUID(), "x", created);
		Post explicit = Post.publish(UUID.randomUUID(), "x", null, PostCategory.QUESTION, created);

		assertThat(defaulted.getCategory()).isEqualTo(PostCategory.GENERAL);
		assertThat(Post.publish(UUID.randomUUID(), "x", null, null, created).getCategory()).isEqualTo(PostCategory.GENERAL);
		assertThat(explicit.getCategory()).isEqualTo(PostCategory.QUESTION);

		explicit.changeCategory(PostCategory.QUESTION, later);
		assertThat(explicit.getUpdatedAt()).isEqualTo(created);
		explicit.changeCategory(PostCategory.GUIDE, later);
		assertThat(explicit.getCategory()).isEqualTo(PostCategory.GUIDE);
		assertThat(explicit.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void publishCanReferenceGameAndLinkingChangesUpdatedAtOnlyWhenGameChanges() {
		UUID gameId = UUID.randomUUID();
		Instant created = Instant.parse("2026-10-21T10:15:30Z");
		Instant later = created.plusSeconds(60);
		Post post = Post.publish(UUID.randomUUID(), "Oyun", gameId, created);
		assertThat(post.getGameId()).isEqualTo(gameId);

		post.linkGame(gameId, later);
		assertThat(post.getUpdatedAt()).isEqualTo(created);

		UUID otherGame = UUID.randomUUID();
		post.linkGame(otherGame, later);
		assertThat(post.getGameId()).isEqualTo(otherGame);
		assertThat(post.getUpdatedAt()).isEqualTo(later);

		post.unlinkGame(later.plusSeconds(1));
		assertThat(post.getGameId()).isNull();
		assertThat(post.getUpdatedAt()).isEqualTo(later.plusSeconds(1));

		post.unlinkGame(later.plusSeconds(2));
		assertThat(post.getUpdatedAt()).isEqualTo(later.plusSeconds(1));
	}
}
