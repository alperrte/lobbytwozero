package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.CreatePostRequest;
import com.ltz.community.api.dto.request.LinkGameRequest;
import com.ltz.community.api.dto.request.UpdatePostRequest;
import com.ltz.community.api.dto.response.PostResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.application.port.GameContentQuotaPort;
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.ForumGameMismatchException;
import com.ltz.community.domain.exception.ForumNotFoundException;
import com.ltz.community.domain.exception.GameContentRuleException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.domain.exception.TooManyGameContentException;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

@Service
public class PostService {

	/** Kullanıcı başına saatlik türlü içerik (GAME-11) oluşturma sınırı; silinen içerik de sayılır (sil-yeniden-oluştur ile aşılamaz). */
	static final int MAX_GAME_CONTENT_PER_HOUR = 10;

	private final PostRepository postRepository;
	private final PostImageRepository postImageRepository;
	private final ForumRepository forumRepository;
	private final PostAccess postAccess;
	private final PostMapper postMapper;
	private final CurrentUserPort currentUser;
	private final GameCatalogPort gameCatalog;
	private final GameContentQuotaPort gameContentQuota;
	private final Clock clock;

	public PostService(PostRepository postRepository, PostImageRepository postImageRepository,
			ForumRepository forumRepository, PostAccess postAccess, PostMapper postMapper, CurrentUserPort currentUser,
			GameCatalogPort gameCatalog, GameContentQuotaPort gameContentQuota, Clock clock) {
		this.postRepository = postRepository;
		this.postImageRepository = postImageRepository;
		this.forumRepository = forumRepository;
		this.postAccess = postAccess;
		this.postMapper = postMapper;
		this.currentUser = currentUser;
		this.gameCatalog = gameCatalog;
		this.gameContentQuota = gameContentQuota;
		this.clock = clock;
	}

	/**
	 * Dış servis çağrısı (gameId doğrulaması) bağlantı havuzunu (küçük) tutmasın diye bilinçli olarak
	 * transaction dışındadır; yalnız {@code save} kendi kısa transaction'ında çalışır.
	 */
	public PostResponse createPost(CreatePostRequest request) {
		UUID authorId = currentUser.requireUserId();
		if (request.contentType() != null) {
			return createGameContent(authorId, request);
		}
		if (request.title() != null) {
			throw new GameContentRuleException("A title is only allowed on game content (contentType is required).");
		}
		Forum forum = request.forumId() == null ? null : requireVisibleForum(request.forumId());
		UUID gameId = resolveGameId(forum, request.gameId());
		Post post = Post.publishInForum(authorId, request.forumId(), request.body().strip(), gameId,
				request.category(), Instant.now(clock));
		return postMapper.toResponse(postRepository.save(post));
	}

	/**
	 * Oyuna bağlı türlü içerik (GAME-11): başlık ve {@code gameId} zorunludur, forum konusu olamaz. Oyun, mevcut COM-14
	 * doğrulamasıyla katalogda aranır (kesintide 503); kota dış çağrıdan önce denetlenir.
	 */
	private PostResponse createGameContent(UUID authorId, CreatePostRequest request) {
		if (request.forumId() != null) {
			throw new GameContentRuleException("Game content cannot be opened as a forum topic.");
		}
		if (request.gameId() == null) {
			throw new GameContentRuleException("Game content requires a gameId.");
		}
		String title = request.title() == null ? "" : request.title().strip();
		if (title.isEmpty()) {
			throw new GameContentRuleException("Game content requires a title.");
		}
		Instant now = Instant.now(clock);
		if (postRepository.countByAuthorIdAndContentTypeIsNotNullAndCreatedAtAfter(authorId,
				now.minus(Duration.ofHours(1))) >= MAX_GAME_CONTENT_PER_HOUR) {
			throw new TooManyGameContentException();
		}
		requireAvailableGame(request.gameId());
		Post post = Post.publishGameContent(authorId, request.gameId(), request.contentType(), title,
				request.body().strip(), now);
		return postMapper.toResponse(gameContentQuota.saveWithinQuota(post, now.minus(Duration.ofHours(1)),
				MAX_GAME_CONTENT_PER_HOUR));
	}

	@Transactional
	public PostResponse updatePost(UUID postId, UpdatePostRequest request) {
		Post post = postAccess.loadOwned(postId, currentUser.requireUserId());
		requireEditable(post);
		Instant now = Instant.now(clock);
		if (request.title() != null) {
			editTitle(post, request.title(), now);
		}
		post.editBody(request.body().strip(), now);
		if (request.category() != null) {
			post.changeCategory(request.category(), now);
		}
		return postMapper.toResponse(postRepository.save(post));
	}

	private static void editTitle(Post post, String rawTitle, Instant now) {
		if (!post.isGameContent()) {
			throw new GameContentRuleException("A title is only allowed on game content.");
		}
		String title = rawTitle.strip();
		if (title.isEmpty()) {
			throw new GameContentRuleException("Game content requires a title.");
		}
		post.editTitle(title, now);
	}

	/** Oyun bağlantısını ekler/değiştirir. Dış çağrı transaction dışındadır; çakışma {@code @Version} ile yakalanır. */
	public PostResponse linkGame(UUID postId, LinkGameRequest request) {
		Post post = postAccess.loadOwned(postId, currentUser.requireUserId());
		requireEditable(post);
		if (request.gameId().equals(post.getGameId())) {
			return postMapper.toResponse(post);
		}
		requireCompatibleWithForumGame(post, request.gameId());
		requireAvailableGame(request.gameId());
		post.linkGame(request.gameId(), Instant.now(clock));
		return postMapper.toResponse(postRepository.save(post));
	}

	@Transactional
	public void unlinkGame(UUID postId) {
		Post post = postAccess.loadOwned(postId, currentUser.requireUserId());
		requireEditable(post);
		if (post.isGameContent()) {
			throw new GameContentRuleException("Game content must stay linked to its game.");
		}
		requireCompatibleWithForumGame(post, null);
		post.unlinkGame(Instant.now(clock));
		postRepository.save(post);
	}

	/** Gönderi soft delete edilir; ona bağlı görsel baytları da silinir (gönderi artık erişilemez). */
	@Transactional
	public void deletePost(UUID postId) {
		Post post = postAccess.loadOwnedForUpdate(postId, currentUser.requireUserId());
		post.markDeleted(Instant.now(clock));
		postRepository.save(post);
		postImageRepository.deleteByPostId(postId);
	}

	/** Repost düzenlenemez (içerik orijinale aittir); yalnız silinebilir. */
	private void requireEditable(Post post) {
		if (post.getStatus() != PostStatus.VISIBLE || post.isRepost()) {
			throw new PostNotEditableException();
		}
	}

	private Forum requireVisibleForum(UUID forumId) {
		return forumRepository.findById(forumId).filter(Forum::isVisible).orElseThrow(ForumNotFoundException::new);
	}

	/**
	 * Oyuna bağlı bir forumun konusu o oyuna bağlanır; çelişen bir {@code gameId} reddedilir. Forum oluşturulurken
	 * oyun kataloğuyla doğrulandığı için forumun oyunu için ayrıca katalog çağrısı yapılmaz.
	 */
	private UUID resolveGameId(Forum forum, UUID requestedGameId) {
		if (forum != null && forum.getGameId() != null) {
			if (requestedGameId != null && !requestedGameId.equals(forum.getGameId())) {
				throw new ForumGameMismatchException("The post must be linked to the game of its forum.");
			}
			return forum.getGameId();
		}
		if (requestedGameId != null) {
			requireAvailableGame(requestedGameId);
		}
		return requestedGameId;
	}

	/** Oyuna bağlı forumdaki bir konunun oyun bağlantısı değiştirilemez ve kaldırılamaz ({@code newGameId} null = kaldırma). */
	private void requireCompatibleWithForumGame(Post post, UUID newGameId) {
		if (post.getForumId() == null) {
			return;
		}
		forumRepository.findById(post.getForumId())
				.map(Forum::getGameId)
				.filter(forumGameId -> !forumGameId.equals(newGameId))
				.ifPresent(forumGameId -> {
					throw new ForumGameMismatchException("The post must stay linked to the game of its forum.");
				});
	}

	private void requireAvailableGame(UUID gameId) {
		if (!gameCatalog.isAvailable(gameId)) {
			throw new GameNotAvailableException();
		}
	}
}
