package com.ltz.community.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

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
