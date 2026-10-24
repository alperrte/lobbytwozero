package com.ltz.community.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.ForumStatus;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/**
 * Gönderiye erişim kuralları: silinmiş gönderi herkes için yok sayılır; sahibi olmayan kullanıcıya
 * VISIBLE dışındaki (ör. moderasyonla gizlenmiş) gönderinin varlığı sızdırılmaz.
 */
@Component
public class PostAccess {

	private final PostRepository postRepository;
	private final ForumRepository forumRepository;

	public PostAccess(PostRepository postRepository, ForumRepository forumRepository) {
		this.postRepository = postRepository;
		this.forumRepository = forumRepository;
	}

	public Post loadOwned(UUID postId, UUID userId) {
		return requireOwned(postRepository.findById(postId), userId);
	}

	/** Eşzamanlı görsel işlemlerini sıraya sokmak için satır kilidiyle yükler. */
	public Post loadOwnedForUpdate(UUID postId, UUID userId) {
		return requireOwned(postRepository.findByIdForUpdate(postId), userId);
	}

	/**
	 * Sahibi her durumda (silinmemişse), diğer kullanıcılar yalnız VISIBLE gönderiyi görebilir. Gizli (HIDDEN) bir
	 * forumun konusu kimseye (sahibine dahil) görünmez.
	 */
	public Post loadViewable(UUID postId, UUID userId) {
		Post post = postRepository.findById(postId)
				.filter(found -> found.getStatus() != PostStatus.DELETED)
				.orElseThrow(PostNotFoundException::new);
		if (!post.isOwnedBy(userId) && post.getStatus() != PostStatus.VISIBLE) {
			throw new PostNotFoundException();
		}
		if (post.getForumId() != null && !forumRepository.existsByIdAndStatus(post.getForumId(), ForumStatus.VISIBLE)) {
			throw new PostNotFoundException();
		}
		return post;
	}

	/** Etkileşim (beğeni, yorum) yalnız herkese açık gönderiye yapılır; gizli içerik sahibine bile etkileşime kapalıdır. */
	public Post loadInteractable(UUID postId, UUID userId) {
		Post post = loadViewable(postId, userId);
		if (post.getStatus() != PostStatus.VISIBLE) {
			throw new PostNotFoundException();
		}
		return post;
	}

	private Post requireOwned(Optional<Post> found, UUID userId) {
		Post post = found
				.filter(candidate -> candidate.getStatus() != PostStatus.DELETED)
				.orElseThrow(PostNotFoundException::new);
		if (post.isOwnedBy(userId)) {
			return post;
		}
		if (post.getStatus() != PostStatus.VISIBLE) {
			throw new PostNotFoundException();
		}
		throw new PostAccessDeniedException();
	}
}
