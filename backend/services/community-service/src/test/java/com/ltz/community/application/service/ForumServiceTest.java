package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.ltz.community.api.dto.request.CreateForumRequest;
import com.ltz.community.application.mapper.ForumMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.enums.ForumStatus;
import com.ltz.community.domain.exception.ForumSlugTakenException;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;

/** Eşzamanlı aynı slug yarışı gibi gerçek veritabanında deterministik üretilemeyen dalları doğrular. */
class ForumServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

	private final ForumRepository forumRepository = mock(ForumRepository.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final ForumService service = new ForumService(forumRepository, mock(GameCatalogPort.class),
			new ForumMapper(), currentUser, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void uniqueConstraintRaceIsReportedAsSlugConflict() {
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
		when(currentUser.hasModeratorRole()).thenReturn(true);
		when(forumRepository.existsBySlug("yarisma")).thenReturn(false);
		when(forumRepository.saveAndFlush(any(Forum.class))).thenThrow(new DataIntegrityViolationException("uq_forums_slug"));

		CreateForumRequest request = new CreateForumRequest("yarisma", "Başlık", null, null);

		assertThatThrownBy(() -> service.createForum(request))
				.isInstanceOf(ForumSlugTakenException.class);
	}

	@Test
	void updateKeepsTimestampWhenNothingChanges() {
		Forum forum = Forum.open(UUID.randomUUID(), null, "sabit", "Başlık", "Açıklama", NOW);

		forum.update(null, null, null, NOW.plusSeconds(60));
		forum.update("Başlık", "Açıklama", ForumStatus.VISIBLE, NOW.plusSeconds(60));
		assertThat(forum.getUpdatedAt()).isEqualTo(NOW);

		forum.update(null, null, ForumStatus.HIDDEN, NOW.plusSeconds(60));
		assertThat(forum.isVisible()).isFalse();
		assertThat(forum.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
	}
}
