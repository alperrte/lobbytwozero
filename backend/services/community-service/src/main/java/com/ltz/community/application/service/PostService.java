package com.ltz.community.application.service;

import java.time.Clock;
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
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.ForumGameMismatchException;
import com.ltz.community.domain.exception.ForumNotFoundException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

@Service
public class PostService {

	private final PostRepository postRepository;
	private final PostImageRepository postImageRepository;
	private final ForumRepository forumRepository;
	private final PostAccess postAccess;
	private final PostMapper postMapper;
	private final CurrentUserPort currentUser;
	private final GameCatalogPort gameCatalog;
	private final Clock clock;

	public PostService(PostRepository postRepository, PostImageRepository postImageRepository,
			ForumRepository forumRepository, PostAccess postAccess, PostMapper postMapper, CurrentUserPort currentUser,
			GameCatalogPort gameCatalog, Clock clock) {
		this.postRepository = postRepository;
		this.postImageRepository = postImageRepository;
		this.forumRepository = forumRepository;
		this.postAccess = postAccess;
		this.postMapper = postMapper;
		this.currentUser = currentUser;
		this.gameCatalog = gameCatalog;
		this.clock = clock;
	}

	/**
	 * Dış servis çağrısı (gameId doğrulaması) bağlantı havuzunu (küçük) tutmasın diye bilinçli olarak
	 * transaction dışındadır; yalnız {@code save} kendi kısa transaction'ında çalışır.
	 */
	public PostResponse createPost(CreatePostRequest request) {
		UUID authorId = currentUser.requireUserId();
		Forum forum = request.forumId() == null ? null : requireVisibleForum(request.forumId());
		UUID gameId = resolveGameId(forum, request.gameId());
		Post post = Post.publishInForum(authorId, request.forumId(), request.body().strip(), gameId,
				request.category(), Instant.now(clock));
		return postMapper.toResponse(postRepository.save(post));
	}

	@Transactional
	public PostResponse updatePost(UUID postId, UpdatePostRequest request) {
		Post post = postAccess.loadOwned(postId, currentUser.requireUserId());
		requireEditable(post);
		Instant now = Instant.now(clock);
		post.editBody(request.body().strip(), now);
		if (request.category() != null) {
			post.changeCategory(request.category(), now);
		}
		return postMapper.toResponse(postRepository.save(post));
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
