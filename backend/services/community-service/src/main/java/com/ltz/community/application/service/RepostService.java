package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.CreateRepostRequest;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.AlreadyRepostedException;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/**
 * Kaynağa atıflı repost (COM-20). Repost ayrı bir satırdır ve yalnız orijinale referans tutar; orijinalin içeriği,
 * sahibi ve zaman damgaları değişmez. Yalnız herkese açık (VISIBLE, forumu görünür) gönderi repostlanabilir; erişilemeyen
 * gönderi 404 döner, böylece varlığı ve içeriği sızmaz. Bir repost'un reposta'ı zincir kurmaz, doğrudan orijinale bağlanır.
 */
@Service
public class RepostService {

	private final PostRepository postRepository;
	private final PostAccess postAccess;
	private final PostViewAssembler viewAssembler;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public RepostService(PostRepository postRepository, PostAccess postAccess, PostViewAssembler viewAssembler,
			CurrentUserPort currentUser, Clock clock) {
		this.postRepository = postRepository;
		this.postAccess = postAccess;
		this.viewAssembler = viewAssembler;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	/**
	 * Aynı kullanıcı aynı orijinali (silinmemiş) bir kez repostlayabilir: önce hızlı kontrol, eşzamanlı yarışta ise
	 * {@code ux_posts_one_repost_per_user} UNIQUE indeksi mükerrer kaydı engeller (409).
	 */
	@Transactional
	public PostViewResponse repost(UUID postId, CreateRepostRequest request) {
		UUID userId = currentUser.requireUserId();
		Post target = postAccess.loadInteractable(postId, userId);
		// Hedef bir repost ise kaynak, onun orijinalidir; orijinal de herkese açık olmalıdır.
		Post original = target.isRepost() ? postAccess.loadInteractable(target.getRepostOfId(), userId) : target;
		if (postRepository.existsByAuthorIdAndRepostOfIdAndStatusNot(userId, original.getId(), PostStatus.DELETED)) {
			throw new AlreadyRepostedException();
		}
		Post saved;
		try {
			saved = postRepository.saveAndFlush(
					Post.repost(userId, original.getId(), normalizeComment(request), Instant.now(clock)));
		} catch (DataIntegrityViolationException _) {
			throw new AlreadyRepostedException();
		}
		return viewAssembler.assemble(List.of(saved), userId).getFirst();
	}

	/** Boş yorum "yorum yok" sayılır. */
	private static String normalizeComment(CreateRepostRequest request) {
		if (request == null || request.comment() == null) {
			return null;
		}
		String comment = request.comment().strip();
		return comment.isEmpty() ? null : comment;
	}
}
