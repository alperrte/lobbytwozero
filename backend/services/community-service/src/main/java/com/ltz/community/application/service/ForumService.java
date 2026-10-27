package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.CreateForumRequest;
import com.ltz.community.api.dto.request.ForumListRequest;
import com.ltz.community.api.dto.request.UpdateForumRequest;
import com.ltz.community.api.dto.response.ForumPageResponse;
import com.ltz.community.api.dto.response.ForumResponse;
import com.ltz.community.application.mapper.ForumMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.application.service.TimeIdCursor.Position;
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.exception.ForumNotFoundException;
import com.ltz.community.domain.exception.ForumSlugTakenException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.ModeratorRequiredException;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.ForumSpecifications;

/**
 * Oyun forumları (COM-19). Forum oluşturma ve güncelleme/gizleme yalnız moderatör ya da yöneticiye açıktır; okuma
 * (liste ve detay) herkese açıktır ve yalnız VISIBLE forumları gösterir (moderatör gizli forumu da görür). Forumun
 * {@code slug} ve {@code gameId} alanları oluşturulduktan sonra değişmez; oyun ilişkisi game-service kataloğuna karşı
 * doğrulanır.
 */
@Service
public class ForumService {

	static final int DEFAULT_LIMIT = 20;

	private final ForumRepository forumRepository;
	private final GameCatalogPort gameCatalog;
	private final ForumMapper forumMapper;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ForumService(ForumRepository forumRepository, GameCatalogPort gameCatalog, ForumMapper forumMapper,
			CurrentUserPort currentUser, Clock clock) {
		this.forumRepository = forumRepository;
		this.gameCatalog = gameCatalog;
		this.forumMapper = forumMapper;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	/**
	 * Dış servis çağrısı (gameId doğrulaması) bağlantı havuzunu tutmasın diye bilinçli olarak transaction
	 * dışındadır; yalnız {@code save} kendi kısa transaction'ında çalışır. Eşzamanlı aynı slug yarışını veritabanı
	 * UNIQUE kısıtı yakalar.
	 */
	public ForumResponse createForum(CreateForumRequest request) {
		UUID creatorId = currentUser.requireUserId();
		requireModerator();
		if (request.gameId() != null && !gameCatalog.isAvailable(request.gameId())) {
			throw new GameNotAvailableException();
		}
		if (forumRepository.existsBySlug(request.slug())) {
			throw new ForumSlugTakenException();
		}
		Forum forum = Forum.open(creatorId, request.gameId(), request.slug(), request.title().strip(),
				normalizeDescription(request.description()), Instant.now(clock));
		try {
			return forumMapper.toResponse(forumRepository.saveAndFlush(forum));
		} catch (DataIntegrityViolationException _) {
			throw new ForumSlugTakenException();
		}
	}

	@Transactional
	public ForumResponse updateForum(UUID forumId, UpdateForumRequest request) {
		currentUser.requireUserId();
		requireModerator();
		Forum forum = forumRepository.findById(forumId).orElseThrow(ForumNotFoundException::new);
		forum.update(request.title() == null ? null : request.title().strip(),
				normalizeDescription(request.description()), request.status(), Instant.now(clock));
		return forumMapper.toResponse(forumRepository.save(forum));
	}

	@Transactional(readOnly = true)
	public ForumResponse getForum(UUID forumId) {
		Forum forum = forumRepository.findById(forumId)
				.filter(found -> found.isVisible() || currentUser.hasModeratorRole())
				.orElseThrow(ForumNotFoundException::new);
		return forumMapper.toResponse(forum);
	}

	@Transactional(readOnly = true)
	public ForumPageResponse listForums(ForumListRequest request) {
		int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
		Position after = request.cursor() == null ? null : TimeIdCursor.decode(request.cursor());
		// limit + 1 satır okunur: fazla satır varsa sonraki sayfa vardır (ayrı COUNT sorgusu yok).
		List<Forum> rows = forumRepository.findBy(
				ForumSpecifications.visible(request.gameId(), after == null ? null : after.createdAt(),
						after == null ? null : after.id()),
				query -> query.sortBy(ForumSpecifications.oldestFirst()).limit(limit + 1).all());
		boolean hasMore = rows.size() > limit;
		List<Forum> forums = hasMore ? rows.subList(0, limit) : rows;
		Forum last = forums.isEmpty() ? null : forums.getLast();
		String nextCursor = hasMore ? TimeIdCursor.encode(last.getCreatedAt(), last.getId()) : null;
		return new ForumPageResponse(forums.stream().map(forumMapper::toResponse).toList(), nextCursor);
	}

	private void requireModerator() {
		if (!currentUser.hasModeratorRole()) {
			throw new ModeratorRequiredException();
		}
	}

	/** Boş açıklama "açıklama yok" sayılır. */
	private static String normalizeDescription(String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.strip();
		return text.isEmpty() ? null : text;
	}
}
