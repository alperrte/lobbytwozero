package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.response.PostImageContent;
import com.ltz.community.api.dto.response.PostImageResponse;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.service.ImageSanitizer.SanitizedImage;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.PostImageLimitExceededException;
import com.ltz.community.domain.exception.PostImageNotFoundException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;

@Service
public class PostImageService {

	private final PostImageRepository postImageRepository;
	private final PostAccess postAccess;
	private final ImageSanitizer imageSanitizer;
	private final PostMapper postMapper;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public PostImageService(PostImageRepository postImageRepository, PostAccess postAccess,
			ImageSanitizer imageSanitizer, PostMapper postMapper, CurrentUserPort currentUser, Clock clock) {
		this.postImageRepository = postImageRepository;
		this.postAccess = postAccess;
		this.imageSanitizer = imageSanitizer;
		this.postMapper = postMapper;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public PostImageResponse addImage(UUID postId, byte[] upload) {
		Post post = postAccess.loadOwnedForUpdate(postId, currentUser.requireUserId());
		if (post.getStatus() != PostStatus.VISIBLE) {
			throw new PostNotEditableException();
		}
		List<Integer> usedSlots = postImageRepository.findSortOrdersByPostId(postId);
		if (usedSlots.size() >= PostImage.MAX_IMAGES_PER_POST) {
			throw new PostImageLimitExceededException(PostImage.MAX_IMAGES_PER_POST);
		}
		SanitizedImage image = imageSanitizer.sanitize(upload);
		PostImage saved = postImageRepository.save(PostImage.attach(postId, image.content(), image.mimeType(),
				image.width(), image.height(), firstFreeSlot(usedSlots), Instant.now(clock)));
		return postMapper.toResponse(saved);
	}

	@Transactional(readOnly = true)
	public PostImageContent getImage(UUID postId, UUID imageId) {
		postAccess.loadViewable(postId, currentUser.requireUserId());
		PostImage image = postImageRepository.findByIdAndPostId(imageId, postId)
				.orElseThrow(PostImageNotFoundException::new);
		return new PostImageContent(image.getContent(), image.getMimeType());
	}

	@Transactional
	public void deleteImage(UUID postId, UUID imageId) {
		postAccess.loadOwnedForUpdate(postId, currentUser.requireUserId());
		PostImage image = postImageRepository.findByIdAndPostId(imageId, postId)
				.orElseThrow(PostImageNotFoundException::new);
		postImageRepository.delete(image);
	}

	private int firstFreeSlot(List<Integer> usedSlots) {
		Set<Integer> used = new HashSet<>(usedSlots);
		int slot = 0;
		while (used.contains(slot)) {
			slot++;
		}
		return slot;
	}
}
