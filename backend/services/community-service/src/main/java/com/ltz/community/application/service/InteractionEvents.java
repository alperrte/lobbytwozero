package com.ltz.community.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.community.application.port.DomainEventPublisher;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.entity.Post;

/**
 * Topluluk etkileşimlerinin bildirim olayları (COM-18). Olaylar çağıranın transaction'ında outbox'a yazılır ve ortak
 * notification-service'e iletilir; bildirimin kendisi (metin, kanal, tercih) notification-service'in sorumluluğundadır.
 * Yük en az gerekli kimliklerden oluşur: yorum/gönderi metni ve görünen ad taşınmaz. Kişi kendi içeriğine yaptığı
 * etkileşim için bildirim üretilmez; alıcı yoksa olay yazılmaz.
 */
@Component
public class InteractionEvents {

	static final String EVENT_COMMENT_CREATED = "CommentCreated";
	static final String EVENT_POST_LIKED = "PostLiked";
	static final String EVENT_POST_REPOSTED = "PostReposted";
	static final String AGGREGATE_POST = "POST";
	static final String AGGREGATE_COMMENT = "COMMENT";
	private static final String POST_ID = "postId";
	private static final String ACTOR_ID = "actorId";

	private final DomainEventPublisher eventPublisher;

	public InteractionEvents(DomainEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Alıcılar gönderinin sahibi ve (yanıtsa) üst yorumun yazarıdır; yorumu yazan kişi ve aynı kişiyi ikinci kez
	 * bildirmemek için tekrarlar çıkarılır.
	 */
	public void commentCreated(Comment comment, Post post, Comment parent) {
		UUID actorId = comment.getAuthorId();
		List<UUID> recipients = new ArrayList<>();
		addRecipient(recipients, post.getAuthorId(), actorId);
		if (parent != null) {
			addRecipient(recipients, parent.getAuthorId(), actorId);
		}
		if (recipients.isEmpty()) {
			return;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("commentId", comment.getId());
		payload.put(POST_ID, comment.getPostId());
		payload.put("parentCommentId", comment.getParentCommentId());
		payload.put(ACTOR_ID, actorId);
		payload.put("recipientIds", recipients);
		payload.put("isReply", parent != null);
		eventPublisher.publish(AGGREGATE_COMMENT, comment.getId(), EVENT_COMMENT_CREATED, payload);
	}

	public void postLiked(Post post, UUID actorId) {
		if (post.isOwnedBy(actorId)) {
			return;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(POST_ID, post.getId());
		payload.put(ACTOR_ID, actorId);
		payload.put("recipientId", post.getAuthorId());
		eventPublisher.publish(AGGREGATE_POST, post.getId(), EVENT_POST_LIKED, payload);
	}

	public void postReposted(Post original, Post repost) {
		if (original.isOwnedBy(repost.getAuthorId())) {
			return;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(POST_ID, original.getId());
		payload.put("repostId", repost.getId());
		payload.put(ACTOR_ID, repost.getAuthorId());
		payload.put("recipientId", original.getAuthorId());
		eventPublisher.publish(AGGREGATE_POST, original.getId(), EVENT_POST_REPOSTED, payload);
	}

	private static void addRecipient(List<UUID> recipients, UUID candidate, UUID actorId) {
		if (!candidate.equals(actorId) && !recipients.contains(candidate)) {
			recipients.add(candidate);
		}
	}
}
