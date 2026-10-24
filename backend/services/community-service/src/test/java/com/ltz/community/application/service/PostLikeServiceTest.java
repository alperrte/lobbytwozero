package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ltz.community.api.dto.response.LikeStateResponse;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.exception.AuthenticationRequiredException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.PostLikeRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/** Servis dallarını (yarış sonucu, kimlik yokluğu) mock'larla doğrular; gerçek veritabanı davranışı entegrasyon testindedir. */
class PostLikeServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID POST_ID = UUID.randomUUID();

	private final PostRepository postRepository = mock(PostRepository.class);
	private final PostLikeRepository postLikeRepository = mock(PostLikeRepository.class);
	private final PostAccess postAccess = mock(PostAccess.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final PostLikeService service = new PostLikeService(postRepository, postLikeRepository, postAccess,
			currentUser, Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void authenticate() {
		when(currentUser.requireUserId()).thenReturn(USER_ID);
		Post post = Post.publish(UUID.randomUUID(), "gönderi", NOW);
		when(postAccess.loadViewable(POST_ID, USER_ID)).thenReturn(post);
		when(postAccess.loadInteractable(POST_ID, USER_ID)).thenReturn(post);
		when(postRepository.findLikeCountById(POST_ID)).thenReturn(Optional.of(4));
	}

	@Test
	void newLikeInsertsRowAndIncrementsCounter() {
		when(postLikeRepository.insertIfAbsent(POST_ID, USER_ID, NOW)).thenReturn(1);
		when(postRepository.incrementLikeCount(POST_ID)).thenReturn(1);

		assertThat(service.like(POST_ID)).isEqualTo(new LikeStateResponse(POST_ID, true, 4));
		verify(postRepository).incrementLikeCount(POST_ID);
	}

	@Test
	void repeatedLikeLeavesCounterUntouched() {
		when(postLikeRepository.insertIfAbsent(POST_ID, USER_ID, NOW)).thenReturn(0);

		assertThat(service.like(POST_ID).liked()).isTrue();
		verify(postRepository, never()).incrementLikeCount(any());
	}

	@Test
	void likeFailsWhenThePostStopsBeingVisibleDuringTheRace() {
		when(postLikeRepository.insertIfAbsent(POST_ID, USER_ID, NOW)).thenReturn(1);
		when(postRepository.incrementLikeCount(POST_ID)).thenReturn(0);

		assertThatThrownBy(() -> service.like(POST_ID)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void unlikeDecrementsOnlyWhenARowWasRemoved() {
		when(postLikeRepository.deleteLike(POST_ID, USER_ID)).thenReturn(1);
		assertThat(service.unlike(POST_ID)).isEqualTo(new LikeStateResponse(POST_ID, false, 4));
		verify(postRepository).decrementLikeCount(POST_ID);

		when(postLikeRepository.deleteLike(POST_ID, USER_ID)).thenReturn(0);
		service.unlike(POST_ID);
		verify(postRepository).decrementLikeCount(POST_ID);
	}

	@Test
	void unauthenticatedCallerIsRejectedBeforeAnyWork() {
		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));

		assertThatThrownBy(() -> service.like(POST_ID)).isInstanceOf(AuthenticationRequiredException.class);
		assertThatThrownBy(() -> service.unlike(POST_ID)).isInstanceOf(AuthenticationRequiredException.class);
		verify(postLikeRepository, never()).insertIfAbsent(any(), any(), any());
	}

	@Test
	void missingCounterRowIsReportedAsNotFound() {
		when(postLikeRepository.insertIfAbsent(POST_ID, USER_ID, NOW)).thenReturn(0);
		when(postRepository.findLikeCountById(POST_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.like(POST_ID)).isInstanceOf(PostNotFoundException.class);
	}
}
