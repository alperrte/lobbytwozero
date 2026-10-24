package com.ltz.community.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.CommentPageRequest;
import com.ltz.community.api.dto.response.CommentPageResponse;
import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.application.mapper.CommentMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.service.TimeIdCursor.Position;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.enums.CommentStatus;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.CommentRepository;
import com.ltz.community.infrastructure.persistence.repository.CommentSpecifications;
import com.ltz.community.infrastructure.persistence.repository.ReplyCount;

/**
 * Yorum ve yanıt listeleri (misafire açık). Yorumları görmek, gönderiyi görmekle aynı kurala bağlıdır: gönderi
 * silinmiş veya çağıran için görünmezse 404 döner. Sayfalama keyset tabanlı ve eskiden yenidir; yanıtlar üst
 * yorum başına ayrı uçtan okunur, bu yüzden iç içe derinlik sorgu maliyetini artırmaz.
 */
@Service
public class CommentFeedService {

	static final int DEFAULT_LIMIT = 20;

	private final CommentRepository commentRepository;
	private final PostAccess postAccess;
	private final CommentMapper commentMapper;
	private final CurrentUserPort currentUser;

	public CommentFeedService(CommentRepository commentRepository, PostAccess postAccess, CommentMapper commentMapper,
			CurrentUserPort currentUser) {
		this.commentRepository = commentRepository;
		this.postAccess = postAccess;
		this.commentMapper = commentMapper;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public CommentPageResponse listComments(UUID postId, CommentPageRequest request) {
		postAccess.loadViewable(postId, currentUser.findUserId().orElse(null));
		Position after = request.cursor() == null ? null : TimeIdCursor.decode(request.cursor());
		return page(CommentSpecifications.topLevelOf(postId, after == null ? null : after.createdAt(),
				after == null ? null : after.id()), limit(request));
	}

	@Transactional(readOnly = true)
	public CommentPageResponse listReplies(UUID commentId, CommentPageRequest request) {
		Comment parent = commentRepository.findById(commentId)
				.filter(found -> found.getStatus() != CommentStatus.HIDDEN)
				.orElseThrow(CommentNotFoundException::new);
		postAccess.loadViewable(parent.getPostId(), currentUser.findUserId().orElse(null));
		Position after = request.cursor() == null ? null : TimeIdCursor.decode(request.cursor());
		return page(CommentSpecifications.repliesOf(commentId, after == null ? null : after.createdAt(),
				after == null ? null : after.id()), limit(request));
	}

	private static int limit(CommentPageRequest request) {
		return request.limit() == null ? DEFAULT_LIMIT : request.limit();
	}

	private CommentPageResponse page(Specification<Comment> specification, int limit) {
		// limit + 1 satır okunur: fazla satır varsa sonraki sayfa vardır (ayrı COUNT sorgusu yok).
		List<Comment> rows = commentRepository.findBy(specification,
				query -> query.sortBy(CommentSpecifications.oldestFirst()).limit(limit + 1).all());
		boolean hasMore = rows.size() > limit;
		List<Comment> comments = hasMore ? rows.subList(0, limit) : rows;

		// Sayfadaki yorumların yanıt sayıları tek sorguda gelir.
		Map<UUID, Integer> replyCounts = comments.isEmpty() ? Map.of()
				: commentRepository.countVisibleReplies(comments.stream().map(Comment::getId).toList()).stream()
						.collect(Collectors.toMap(ReplyCount::getParentId, count -> Math.toIntExact(count.getTotal())));
		List<CommentResponse> items = comments.stream()
				.map(comment -> commentMapper.toResponse(comment, replyCounts.getOrDefault(comment.getId(), 0)))
				.toList();
		Comment last = comments.isEmpty() ? null : comments.getLast();
		String nextCursor = hasMore ? TimeIdCursor.encode(last.getCreatedAt(), last.getId()) : null;
		return new CommentPageResponse(items, nextCursor);
	}
}
