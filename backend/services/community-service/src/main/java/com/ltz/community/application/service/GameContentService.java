package com.ltz.community.application.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.GameContentListRequest;
import com.ltz.community.api.dto.response.GameContentDetailResponse;
import com.ltz.community.api.dto.response.GameContentPageResponse;
import com.ltz.community.api.dto.response.GameContentSummaryResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.PostSpecifications;

/**
 * Oyuna bağlı topluluk içeriğinin (rehber, easter egg, oyuncu deneyimi; GAME-11) okuma tarafı. Liste, toplam sayı ve
 * detay aynı görünürlük kurallarını kullanır: yalnız herkese açık (VISIBLE) içerik (sahibine bile gizli içerik burada
 * görünmez), gizli forumda olmayan, yazarı çağıranla engelleme ilişkisinde olmayan içerik. Böylece gizli/silinmiş/engelli
 * kayıtların varlığı ne listeden ne de {@code totalElements}'tan sızar. Oyun kimliği Game Service'te doğrulanmaz
 * (okumada dış bağımlılık yoktur); eşleşme yoksa liste boştur.
 */
@Service
public class GameContentService {

	static final int EXCERPT_MAX_LENGTH = 200;
	private static final String ELLIPSIS = "…";

	private final PostRepository postRepository;
	private final PostAccess postAccess;
	private final PostViewAssembler viewAssembler;
	private final BlockPolicy blockPolicy;
	private final CurrentUserPort currentUser;

	public GameContentService(PostRepository postRepository, PostAccess postAccess, PostViewAssembler viewAssembler,
			BlockPolicy blockPolicy, CurrentUserPort currentUser) {
		this.postRepository = postRepository;
		this.postAccess = postAccess;
		this.viewAssembler = viewAssembler;
		this.blockPolicy = blockPolicy;
		this.currentUser = currentUser;
	}

	/** {@code publishedAt} azalan, eşitlikte id azalan (kararlı) sıra; sayfa ve toplam aynı koşuldan gelir. */
	@Transactional(readOnly = true)
	public GameContentPageResponse list(UUID gameId, GameContentListRequest request) {
		Set<UUID> blockedAuthors = blockPolicy.counterparts(currentUser.findUserId().orElse(null));
		int page = request.pageOrDefault();
		int size = request.sizeOrDefault();
		Page<Post> result = postRepository.findAll(
				PostSpecifications.gameContent(gameId, request.type(), blockedAuthors),
				PageRequest.of(page, size, PostSpecifications.sort(PostSort.NEWEST)));
		List<GameContentSummaryResponse> items = result.getContent().stream().map(GameContentService::toSummary).toList();
		return new GameContentPageResponse(gameId, request.type(), items, page, size, result.getTotalElements());
	}

	/** İçerik yoksa, bu oyuna/türlü içeriğe ait değilse ya da çağıran için görünmezse 404 (varlığı sızdırılmaz). */
	@Transactional(readOnly = true)
	public GameContentDetailResponse detail(UUID gameId, UUID contentId) {
		UUID viewerId = currentUser.findUserId().orElse(null);
		Post post = postAccess.loadPublished(contentId, viewerId);
		if (!post.isGameContent() || !gameId.equals(post.getGameId())) {
			throw new PostNotFoundException();
		}
		PostViewResponse view = viewAssembler.assemble(List.of(post), viewerId).getFirst();
		return new GameContentDetailResponse(post.getId(), post.getGameId(), post.getContentType(), post.getTitle(),
				post.getBody(), post.getAuthorId(), post.getCreatedAt(), post.getUpdatedAt(), view.likeCount(),
				view.commentCount(), view.likedByMe(), view.images());
	}

	private static GameContentSummaryResponse toSummary(Post post) {
		return new GameContentSummaryResponse(post.getId(), post.getGameId(), post.getContentType(), post.getTitle(),
				excerpt(post.getBody()), post.getAuthorId(), post.getCreatedAt(), post.getUpdatedAt(),
				detailPath(post.getGameId(), post.getId()));
	}

	static String detailPath(UUID gameId, UUID contentId) {
		return "/api/v1/community/games/" + gameId + "/content/" + contentId;
	}

	/**
	 * Düz metin özeti: boşluklar (satır sonu dahil) tek boşluğa indirilir ve en çok {@value #EXCERPT_MAX_LENGTH} karakter
	 * (Unicode kod noktası) alınır; kesildiyse "…" eklenir. Gövde zaten düz metindir; HTML ayrıştırılmaz ya da üretilmez.
	 */
	static String excerpt(String body) {
		String text = body == null ? "" : body.replaceAll("\\s+", " ").strip();
		if (text.codePointCount(0, text.length()) <= EXCERPT_MAX_LENGTH) {
			return text;
		}
		int end = text.offsetByCodePoints(0, EXCERPT_MAX_LENGTH);
		return text.substring(0, end).stripTrailing() + ELLIPSIS;
	}
}
