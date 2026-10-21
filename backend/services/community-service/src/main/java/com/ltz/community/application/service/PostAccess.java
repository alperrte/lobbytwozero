package com.ltz.community.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/**
 * Gönderiye erişim kuralları: silinmiş gönderi herkes için yok sayılır; sahibi olmayan kullanıcıya
 * VISIBLE dışındaki (ör. moderasyonla gizlenmiş) gönderinin varlığı sızdırılmaz.
 */
@Component
public class PostAccess {

	private final PostRepository postRepository;

	public PostAccess(PostRepository postRepository) {
		this.postRepository = postRepository;
	}

	public Post loadOwned(UUID postId, UUID userId) {
		return requireOwned(postRepository.findById(postId), userId);
	}

	/** Eşzamanlı görsel işlemlerini sıraya sokmak için satır kilidiyle yükler. */
	public Post loadOwnedForUpdate(UUID postId, UUID userId) {
		return requireOwned(postRepository.findByIdForUpdate(postId), userId);
	}

	/** Sahibi her durumda (silinmemişse), diğer kullanıcılar yalnız VISIBLE gönderiyi görebilir. */
	public Post loadViewable(UUID postId, UUID userId) {
		Post post = postRepository.findById(postId)
				.filter(found -> found.getStatus() != PostStatus.DELETED)
				.orElseThrow(PostNotFoundException::new);
		if (post.isOwnedBy(userId) || post.getStatus() == PostStatus.VISIBLE) {
			return post;
		}
		throw new PostNotFoundException();
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
