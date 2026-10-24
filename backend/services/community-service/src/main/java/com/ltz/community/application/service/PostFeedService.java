package com.ltz.community.application.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.response.PostPageResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.PostSearchCriteria;
import com.ltz.community.application.port.PostSearchCriteria.FeedPosition;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.InvalidPostQueryException;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.PostSpecifications;

/** Gönderi okuma tarafı: herkese açık akış. Silinmiş ve gizlenmiş gönderiler akışta görünmez. */
@Service
public class PostFeedService {

	static final int DEFAULT_LIMIT = 20;
	static final int MIN_SEARCH_LENGTH = 2;

	private final PostRepository postRepository;
	private final PostAccess postAccess;
	private final PostViewAssembler viewAssembler;
	private final CurrentUserPort currentUser;

	public PostFeedService(PostRepository postRepository, PostAccess postAccess, PostViewAssembler viewAssembler,
			CurrentUserPort currentUser) {
		this.postRepository = postRepository;
		this.postAccess = postAccess;
		this.viewAssembler = viewAssembler;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public PostPageResponse listPosts(PostFeedRequest request) {
		return page(criteria(request, Set.of(PostStatus.VISIBLE), null));
	}

	/**
	 * Kullanıcının gönderi geçmişi (profil). Misafir ve diğer kullanıcılar yalnız herkese açık (VISIBLE) gönderileri
	 * görür; kullanıcının kendisi moderasyonla gizlenmiş (HIDDEN) gönderilerini de görür. Silinmiş gönderi hiç
	 * listelenmez. Bilinmeyen kullanıcı için boş sayfa döner (kullanıcının varlığı sızdırılmaz); engelleme ve profil
	 * gizliliği kuralları (social/user-service) Faz 4'te (COM-21) uygulanacaktır.
	 */
	@Transactional(readOnly = true)
	public PostPageResponse listUserPosts(UUID userId, PostFeedRequest request) {
		boolean ownProfile = currentUser.findUserId().filter(userId::equals).isPresent();
		Set<PostStatus> statuses = ownProfile ? Set.of(PostStatus.VISIBLE, PostStatus.HIDDEN) : Set.of(PostStatus.VISIBLE);
		return page(criteria(request, statuses, userId));
	}

	/** İstek parametrelerini doğrulayıp sorgu kriterine çevirir (akış ve profil geçmişi için ortak). */
	private PostSearchCriteria criteria(PostFeedRequest request, Set<PostStatus> statuses, UUID authorId) {
		int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
		PostSort sort = request.sort() == null ? PostSort.NEWEST : request.sort();
		FeedPosition after = request.cursor() == null ? null : PostCursor.decode(request.cursor(), sort);
		if (request.from() != null && request.to() != null && !request.from().isBefore(request.to())) {
			throw new InvalidPostQueryException("The 'from' date must be before the 'to' date.");
		}
		return new PostSearchCriteria(statuses, authorId, searchText(request.q()), request.gameId(),
				request.forumId(), request.category(), request.from(), request.to(), sort, after, limit);
	}

	/** Boşluklar kırpılır; boş metin "arama yok" sayılır, tek karakterlik arama reddedilir (gürültü ve taramayı sınırlar). */
	private static String searchText(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String text = raw.strip();
		if (text.length() < MIN_SEARCH_LENGTH) {
			throw new InvalidPostQueryException("The search text must be at least " + MIN_SEARCH_LENGTH + " characters long.");
		}
		return text;
	}

	/**
	 * Gönderi detayı. Misafir ve diğer kullanıcılar yalnız VISIBLE gönderiyi görür; gönderi sahibi kendi gizlenmiş
	 * (HIDDEN) gönderisini de görür. Silinmiş gönderi herkes için 404'tür.
	 */
	@Transactional(readOnly = true)
	public PostViewResponse getPost(UUID postId) {
		UUID viewerId = currentUser.findUserId().orElse(null);
		Post post = postAccess.loadViewable(postId, viewerId);
		return viewAssembler.assemble(List.of(post), viewerId).getFirst();
	}

	private PostPageResponse page(PostSearchCriteria criteria) {
		// limit + 1 satır okunur: fazla satır varsa sonraki sayfa vardır (ayrı COUNT sorgusu yok).
		List<Post> rows = postRepository.findBy(PostSpecifications.matching(criteria),
				query -> query.sortBy(PostSpecifications.sort(criteria.sort())).limit(criteria.limit() + 1).all());
		boolean hasMore = rows.size() > criteria.limit();
		List<Post> posts = hasMore ? rows.subList(0, criteria.limit()) : rows;

		List<PostViewResponse> items = viewAssembler.assemble(posts, currentUser.findUserId().orElse(null));
		Post last = posts.isEmpty() ? null : posts.getLast();
		String nextCursor = hasMore
				? PostCursor.encode(criteria.sort(),
						new FeedPosition(last.getCreatedAt(), last.getId(), last.getPopularityScore()))
				: null;
		return new PostPageResponse(items, nextCursor);
	}
}
