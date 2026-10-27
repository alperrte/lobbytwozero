package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ltz.community.api.dto.request.CreateForumRequest;
import com.ltz.community.api.dto.request.ForumListRequest;
import com.ltz.community.api.dto.request.UpdateForumRequest;
import com.ltz.community.api.dto.response.ForumPageResponse;
import com.ltz.community.api.dto.response.ForumResponse;
import com.ltz.community.application.mapper.ForumMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.domain.enums.ForumStatus;
import com.ltz.community.domain.exception.AuthenticationRequiredException;
import com.ltz.community.domain.exception.ForumNotFoundException;
import com.ltz.community.domain.exception.ForumSlugTakenException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.InvalidPostQueryException;
import com.ltz.community.domain.exception.ModeratorRequiredException;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;

/** Forum yönetimi ve okumasını gerçek PostgreSQL üzerinde çalıştırır (COM-19). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ForumService.class, ForumMapper.class, ClockConfig.class })
class ForumServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final UUID MODERATOR = UUID.randomUUID();
	private static final UUID GAME_ID = UUID.randomUUID();

	@MockitoBean
	private CurrentUserPort currentUser;

	@MockitoBean
	private GameCatalogPort gameCatalog;

	@Autowired
	private ForumService forums;

	@BeforeEach
	void actAsModerator() {
		when(currentUser.requireUserId()).thenReturn(MODERATOR);
		when(currentUser.hasModeratorRole()).thenReturn(true);
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
	}

	private static CreateForumRequest forum(String slug, UUID gameId) {
		return new CreateForumRequest(slug, "  Başlık " + slug + "  ", "  Açıklama  ", gameId);
	}

	private static ForumListRequest list(UUID gameId, Integer limit, String cursor) {
		return new ForumListRequest(gameId, limit, cursor);
	}

	private void createForumWith(String slug, UUID gameId) {
		forums.createForum(forum(slug, gameId));
	}

	private void renameForum(UUID forumId) {
		forums.updateForum(forumId, new UpdateForumRequest("x", null, null));
	}

	private void listWithCursor(String cursor) {
		forums.listForums(list(null, null, cursor));
	}

	private void actAsOrdinaryUser() {
		when(currentUser.hasModeratorRole()).thenReturn(false);
	}

	@Test
	void moderatorOpensAForumLinkedToACatalogGame() {
		ForumResponse created = forums.createForum(forum("elden-ring", GAME_ID));

		assertThat(created.id()).isNotNull();
		assertThat(created.slug()).isEqualTo("elden-ring");
		assertThat(created.title()).isEqualTo("Başlık elden-ring");
		assertThat(created.description()).isEqualTo("Açıklama");
		assertThat(created.gameId()).isEqualTo(GAME_ID);
		assertThat(created.status()).isEqualTo(ForumStatus.VISIBLE);
		assertThat(forums.getForum(created.id())).isEqualTo(created);
		verify(gameCatalog).isAvailable(GAME_ID);
	}

	@Test
	void generalForumNeedsNoGameAndBlankDescriptionIsDropped() {
		ForumResponse created = forums.createForum(new CreateForumRequest("genel-sohbet", "Genel", "   ", null));

		assertThat(created.gameId()).isNull();
		assertThat(created.description()).isNull();
		verify(gameCatalog, never()).isAvailable(any());
	}

	@Test
	void onlyModeratorsCanCreateOrUpdateForums() {
		ForumResponse existing = forums.createForum(forum("kurallar", null));
		actAsOrdinaryUser();
		UUID existingId = existing.id();

		assertThatThrownBy(() -> createForumWith("yetkisiz", null)).isInstanceOf(ModeratorRequiredException.class);
		assertThatThrownBy(() -> renameForum(existingId))
				.isInstanceOf(ModeratorRequiredException.class);

		when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException("Authentication is required."));
		assertThatThrownBy(() -> createForumWith("misafir", null)).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void unknownGameAndDuplicateSlugAreRejected() {
		UUID unknownGame = UUID.randomUUID();
		when(gameCatalog.isAvailable(unknownGame)).thenReturn(false);
		createForumWith("benzersiz", null);

		assertThatThrownBy(() -> createForumWith("oyunsuz", unknownGame)).isInstanceOf(GameNotAvailableException.class);
		assertThatThrownBy(() -> createForumWith("benzersiz", null)).isInstanceOf(ForumSlugTakenException.class);
	}

	@Test
	void moderatorUpdatesTitleDescriptionAndVisibilityButNotSlugOrGame() {
		ForumResponse created = forums.createForum(forum("tartisma", GAME_ID));

		ForumResponse renamed = forums.updateForum(created.id(), new UpdateForumRequest("  Yeni Başlık ", "Yeni açıklama", null));
		assertThat(renamed.title()).isEqualTo("Yeni Başlık");
		assertThat(renamed.description()).isEqualTo("Yeni açıklama");
		assertThat(renamed.slug()).isEqualTo("tartisma");
		assertThat(renamed.gameId()).isEqualTo(GAME_ID);

		ForumResponse hidden = forums.updateForum(created.id(), new UpdateForumRequest(null, null, ForumStatus.HIDDEN));
		assertThat(hidden.status()).isEqualTo(ForumStatus.HIDDEN);
		assertThat(hidden.title()).isEqualTo("Yeni Başlık");

		UUID unknown = UUID.randomUUID();
		assertThatThrownBy(() -> renameForum(unknown))
				.isInstanceOf(ForumNotFoundException.class);
	}

	@Test
	void hiddenForumIsInvisibleToEveryoneButModerators() {
		ForumResponse created = forums.createForum(forum("gizli-forum", null));
		forums.updateForum(created.id(), new UpdateForumRequest(null, null, ForumStatus.HIDDEN));
		UUID id = created.id();

		assertThat(forums.getForum(id).status()).isEqualTo(ForumStatus.HIDDEN);
		actAsOrdinaryUser();
		assertThatThrownBy(() -> forums.getForum(id)).isInstanceOf(ForumNotFoundException.class);
		assertThat(forums.listForums(list(null, null, null)).items()).extracting(ForumResponse::id).doesNotContain(id);

		UUID unknown = UUID.randomUUID();
		assertThatThrownBy(() -> forums.getForum(unknown)).isInstanceOf(ForumNotFoundException.class);
	}

	@Test
	void listIsOldestFirstFiltersByGameAndPagesWithoutGapsOrDuplicates() {
		UUID otherGame = UUID.randomUUID();
		when(gameCatalog.isAvailable(otherGame)).thenReturn(true);
		List<UUID> forGame = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			forGame.add(forums.createForum(forum("oyun-forumu-" + i, GAME_ID)).id());
		}
		forums.createForum(forum("baska-oyun", otherGame));
		forums.createForum(forum("genel", null));

		List<UUID> seen = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			ForumPageResponse page = forums.listForums(list(GAME_ID, 2, cursor));
			page.items().forEach(item -> seen.add(item.id()));
			cursor = page.nextCursor();
			pages++;
		} while (cursor != null);

		assertThat(seen).containsExactlyElementsOf(forGame);
		assertThat(pages).isEqualTo(3);
		assertThat(forums.listForums(list(null, 50, null)).items()).hasSize(7);
	}

	@Test
	void invalidCursorIsRejected() {
		assertThatThrownBy(() -> listWithCursor("bozuk")).isInstanceOf(InvalidPostQueryException.class);
	}
}
