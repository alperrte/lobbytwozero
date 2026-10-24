package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.response.LikeStateResponse;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.PostLikeRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/**
 * Beğeni ekleme/geri alma (COM-06). İşlemler idempotenttir: tekrar beğenmek mükerrer kayıt ve sayaç artışı
 * yaratmaz, olmayan beğeniyi geri almak sayacı düşürmez. Mükerrerliği veritabanı PK'si ve
 * {@code on conflict do nothing} garanti eder; sayaç yalnız satır gerçekten eklendiğinde/silindiğinde ve aynı
 * transaction içinde atomik güncellenir.
 */
@Service
public class PostLikeService {

	private final PostRepository postRepository;
	private final PostLikeRepository postLikeRepository;
	private final PostAccess postAccess;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public PostLikeService(PostRepository postRepository, PostLikeRepository postLikeRepository, PostAccess postAccess,
			CurrentUserPort currentUser, Clock clock) {
		this.postRepository = postRepository;
		this.postLikeRepository = postLikeRepository;
		this.postAccess = postAccess;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	/** Yalnız herkese açık (VISIBLE) gönderi beğenilebilir; diğer durumlar 404'tür (gizli içerik sızdırılmaz). */
	@Transactional
	public LikeStateResponse like(UUID postId) {
		UUID userId = currentUser.requireUserId();
		postAccess.loadInteractable(postId, userId);
		boolean inserted = postLikeRepository.insertIfAbsent(postId, userId, Instant.now(clock)) == 1;
		// Artırma koşulu VISIBLE'dır; gönderi bu arada gizlendi/silindiyse 0 satır etkilenir ve işlem geri alınır.
		if (inserted && postRepository.incrementLikeCount(postId) == 0) {
			throw new PostNotFoundException();
		}
		return state(postId, true);
	}

	@Transactional
	public LikeStateResponse unlike(UUID postId) {
		UUID userId = currentUser.requireUserId();
		postAccess.loadViewable(postId, userId);
		if (postLikeRepository.deleteLike(postId, userId) == 1) {
			postRepository.decrementLikeCount(postId);
		}
		return state(postId, false);
	}

	private LikeStateResponse state(UUID postId, boolean liked) {
		int likeCount = postRepository.findLikeCountById(postId).orElseThrow(PostNotFoundException::new);
		return new LikeStateResponse(postId, liked, likeCount);
	}
}
