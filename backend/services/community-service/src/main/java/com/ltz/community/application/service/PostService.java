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
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

@Service
public class PostService {

	private final PostRepository postRepository;
	private final PostImageRepository postImageRepository;
	private final PostAccess postAccess;
	private final PostMapper postMapper;
	private final CurrentUserPort currentUser;
	private final GameCatalogPort gameCatalog;
	private final Clock clock;

	public PostService(PostRepository postRepository, PostImageRepository postImageRepository, PostAccess postAccess,
			PostMapper postMapper, CurrentUserPort currentUser, GameCatalogPort gameCatalog, Clock clock) {
		this.postRepository = postRepository;
		this.postImageRepository = postImageRepository;
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
		if (request.gameId() != null) {
			requireAvailableGame(request.gameId());
		}
		Post post = Post.publish(authorId, request.body().strip(), request.gameId(), Instant.now(clock));
		return postMapper.toResponse(postRepository.save(post));
	}

	@Transactional
	public PostResponse updatePost(UUID postId, UpdatePostRequest request) {
		Post post = postAccess.loadOwned(postId, currentUser.requireUserId());
		requireEditable(post);
		post.editBody(request.body().strip(), Instant.now(clock));
		return postMapper.toResponse(postRepository.save(post));
	}

	/** Oyun bağlantısını ekler/değiştirir. Dış çağrı transaction dışındadır; çakışma {@code @Version} ile yakalanır. */
	public PostResponse linkGame(UUID postId, LinkGameRequest request) {
		Post post = postAccess.loadOwned(postId, currentUser.requireUserId());
		requireEditable(post);
		if (request.gameId().equals(post.getGameId())) {
			return postMapper.toResponse(post);
		}
		requireAvailableGame(request.gameId());
		post.linkGame(request.gameId(), Instant.now(clock));
		return postMapper.toResponse(postRepository.save(post));
	}

	@Transactional
	public void unlinkGame(UUID postId) {
		Post post = postAccess.loadOwned(postId, currentUser.requireUserId());
		requireEditable(post);
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

	private void requireEditable(Post post) {
		if (post.getStatus() != PostStatus.VISIBLE) {
			throw new PostNotEditableException();
		}
	}

	private void requireAvailableGame(UUID gameId) {
		if (!gameCatalog.isAvailable(gameId)) {
			throw new GameNotAvailableException();
		}
	}
}
