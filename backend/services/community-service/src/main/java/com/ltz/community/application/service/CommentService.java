package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.CreateCommentRequest;
import com.ltz.community.api.dto.request.UpdateCommentRequest;
import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.application.mapper.CommentMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.exception.CommentAccessDeniedException;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.CommentRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/**
 * Yorum yazma, yanıtlama, düzenleme ve silme (COM-07, COM-08, COM-09). Yorum yalnız herkese açık (VISIBLE)
 * gönderiye yazılır; yanıt, aynı gönderideki herkese açık bir yoruma verilir (sınırsız iç içe). Düzenleme ve silme
 * yalnız yorumun yazarına açıktır. {@code posts.comment_count} (VISIBLE yorum sayısı) ekleme/silme ile aynı
 * transaction'da atomik güncellenir.
 */
@Service
public class CommentService {

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final PostAccess postAccess;
	private final CommentMapper commentMapper;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public CommentService(CommentRepository commentRepository, PostRepository postRepository, PostAccess postAccess,
			CommentMapper commentMapper, CurrentUserPort currentUser, Clock clock) {
		this.commentRepository = commentRepository;
		this.postRepository = postRepository;
		this.postAccess = postAccess;
		this.commentMapper = commentMapper;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public CommentResponse createComment(UUID postId, CreateCommentRequest request) {
		UUID authorId = currentUser.requireUserId();
		postAccess.loadInteractable(postId, authorId);
		UUID parentId = request.parentCommentId();
		if (parentId != null) {
			requireReplyableParent(postId, parentId);
		}
		Comment saved = commentRepository.save(
				Comment.write(postId, authorId, parentId, request.body().strip(), Instant.now(clock)));
		// Artırma koşulu VISIBLE'dır; gönderi bu arada gizlendi/silindiyse 0 satır etkilenir ve işlem geri alınır.
		if (postRepository.incrementCommentCount(postId) == 0) {
			throw new PostNotFoundException();
		}
		return commentMapper.toResponse(saved, 0);
	}

	@Transactional
	public CommentResponse updateComment(UUID commentId, UpdateCommentRequest request) {
		Comment comment = loadOwnedVisible(commentId, currentUser.requireUserId());
		comment.editBody(request.body().strip(), Instant.now(clock));
		return commentMapper.toResponse(commentRepository.save(comment), 0);
	}

	/** Soft delete: yanıt ilişkisi korunur; yazarın kendi yorumunu çifte silmesi 404/409 ile engellenir. */
	@Transactional
	public void deleteComment(UUID commentId) {
		Comment comment = loadOwnedVisible(commentId, currentUser.requireUserId());
		comment.markDeleted(Instant.now(clock));
		commentRepository.save(comment);
		postRepository.decrementCommentCount(comment.getPostId());
	}

	/** Yanıt hedefi aynı gönderide ve herkese açık olmalıdır; aksi halde varlığı sızdırılmadan 404 döner. */
	private void requireReplyableParent(UUID postId, UUID parentId) {
		commentRepository.findById(parentId)
				.filter(parent -> parent.getPostId().equals(postId) && parent.isVisible())
				.orElseThrow(CommentNotFoundException::new);
	}

	/** Yazarı olmayan kullanıcıya VISIBLE yorum 403, VISIBLE dışı (silinmiş/gizli) yorum 404'tür. */
	private Comment loadOwnedVisible(UUID commentId, UUID userId) {
		Comment comment = commentRepository.findById(commentId)
				.filter(Comment::isVisible)
				.orElseThrow(CommentNotFoundException::new);
		if (!comment.isOwnedBy(userId)) {
			throw new CommentAccessDeniedException();
		}
		return comment;
	}
}
