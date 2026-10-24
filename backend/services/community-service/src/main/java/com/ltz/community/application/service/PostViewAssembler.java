package com.ltz.community.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.api.dto.response.RepostSourceResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.infrastructure.persistence.repository.ForumRepository;
import com.ltz.community.infrastructure.persistence.repository.PostImageMeta;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostLikeRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/**
 * Gönderi listesini okuma görünümüne çevirir. Sayfadaki tüm gönderiler için görseller, çağıranın beğenileri ve repost
 * kaynakları toplu sorgulanır; gönderi başına ayrı sorgu yapılmaz (N+1 yok). Repost kaynağının görünürlüğü her
 * okumada yeniden hesaplanır: silinmiş, gizlenmiş ya da gizli bir forumdaki orijinalin içeriği repost üzerinden sızmaz.
 */
@Component
public class PostViewAssembler {

	private final PostRepository postRepository;
	private final PostImageRepository postImageRepository;
	private final PostLikeRepository postLikeRepository;
	private final ForumRepository forumRepository;
	private final PostMapper postMapper;

	public PostViewAssembler(PostRepository postRepository, PostImageRepository postImageRepository,
			PostLikeRepository postLikeRepository, ForumRepository forumRepository, PostMapper postMapper) {
		this.postRepository = postRepository;
		this.postImageRepository = postImageRepository;
		this.postLikeRepository = postLikeRepository;
		this.forumRepository = forumRepository;
		this.postMapper = postMapper;
	}

	/** {@code viewerId} misafir için null'dır; bu durumda {@code likedByMe} her zaman false'tur. */
	public List<PostViewResponse> assemble(List<Post> posts, UUID viewerId) {
		if (posts.isEmpty()) {
			return List.of();
		}
		Map<UUID, Post> originals = loadAccessibleOriginals(posts);
		List<UUID> postIds = posts.stream().map(Post::getId).toList();
		List<UUID> imageOwnerIds = Stream.concat(postIds.stream(), originals.keySet().stream()).toList();
		Map<UUID, List<PostImageMeta>> imagesByPost = postImageRepository.findByPostIdInOrderBySortOrderAsc(imageOwnerIds)
				.stream().collect(Collectors.groupingBy(PostImageMeta::getPostId));
		Set<UUID> likedByViewer = viewerId == null ? Set.of()
				: new HashSet<>(postLikeRepository.findLikedPostIds(viewerId, postIds));
		return posts.stream()
				.map(post -> postMapper.toView(post, imagesByPost.getOrDefault(post.getId(), List.of()),
						likedByViewer.contains(post.getId()), source(post, originals, imagesByPost)))
				.toList();
	}

	private RepostSourceResponse source(Post post, Map<UUID, Post> originals, Map<UUID, List<PostImageMeta>> images) {
		if (!post.isRepost()) {
			return null;
		}
		Post original = originals.get(post.getRepostOfId());
		return original == null ? RepostSourceResponse.unavailable(post.getRepostOfId())
				: postMapper.toSource(original, images.getOrDefault(original.getId(), List.of()));
	}

	/** Sayfadaki repost'ların, herkese açık (VISIBLE ve forumu da görünür) orijinalleri; tek seferde yüklenir. */
	private Map<UUID, Post> loadAccessibleOriginals(List<Post> posts) {
		Set<UUID> originalIds = posts.stream().filter(Post::isRepost).map(Post::getRepostOfId)
				.collect(Collectors.toSet());
		if (originalIds.isEmpty()) {
			return Map.of();
		}
		List<Post> visible = postRepository.findAllById(originalIds).stream()
				.filter(original -> original.getStatus() == PostStatus.VISIBLE)
				.toList();
		Set<UUID> forumIds = visible.stream().map(Post::getForumId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Set<UUID> visibleForums = forumIds.isEmpty() ? Set.of()
				: forumRepository.findAllById(forumIds).stream().filter(Forum::isVisible).map(Forum::getId)
						.collect(Collectors.toSet());
		return visible.stream()
				.filter(original -> original.getForumId() == null || visibleForums.contains(original.getForumId()))
				.collect(Collectors.toMap(Post::getId, original -> original));
	}
}
