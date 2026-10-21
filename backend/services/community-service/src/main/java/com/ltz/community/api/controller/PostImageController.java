package com.ltz.community.api.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ltz.community.api.dto.response.PostImageContent;
import com.ltz.community.api.dto.response.PostImageResponse;
import com.ltz.community.application.service.PostImageService;

@RestController
@RequestMapping("/api/v1/community/posts/{postId}/images")
public class PostImageController {

	private final PostImageService postImageService;

	public PostImageController(PostImageService postImageService) {
		this.postImageService = postImageService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public PostImageResponse addImage(@PathVariable UUID postId, @RequestPart("file") MultipartFile file)
			throws IOException {
		return postImageService.addImage(postId, file.getBytes());
	}

	@GetMapping("/{imageId}")
	public ResponseEntity<byte[]> getImage(@PathVariable UUID postId, @PathVariable UUID imageId) {
		PostImageContent image = postImageService.getImage(postId, imageId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.mimeType()))
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
				.header("Content-Security-Policy", "default-src 'none'; sandbox")
				.body(image.content());
	}

	@DeleteMapping("/{imageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteImage(@PathVariable UUID postId, @PathVariable UUID imageId) {
		postImageService.deleteImage(postId, imageId);
	}
}
