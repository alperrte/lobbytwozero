package com.ltz.content.api.controller;

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

import com.ltz.content.api.dto.response.ContentImageContent;
import com.ltz.content.api.dto.response.ContentImageResponse;
import com.ltz.content.application.service.ContentImageService;

@RestController
@RequestMapping("/api/v1/content/contents/{contentId}/images")
public class ContentImageController {

	private final ContentImageService imageService;

	public ContentImageController(ContentImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ContentImageResponse addImage(@PathVariable UUID contentId, @RequestPart("file") MultipartFile file)
			throws IOException {
		return imageService.addImage(contentId, file.getBytes());
	}

	/**
	 * Yayımlanmış içeriğin görseli herkese açıktır, taslak görseli yalnız sahibine. Her istekte bağlı içeriğin hâlâ görünür
	 * (yayımlanmış ve silinmemiş) olduğu servis katmanında yeniden denetlenir; bu yüzden yanıt hiçbir önbelleğe alınmaz
	 * ({@code no-store}), silinen ya da kaldırılan içeriğin görseli önbellekten sunulmaz. Tarayıcı içeriği yorumlamaz
	 * (nosniff, sandbox CSP).
	 */
	@GetMapping("/{imageId}")
	public ResponseEntity<byte[]> getImage(@PathVariable UUID contentId, @PathVariable UUID imageId) {
		ContentImageContent image = imageService.getImage(contentId, imageId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.mimeType()))
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "default-src 'none'; sandbox")
				.body(image.content());
	}

	@DeleteMapping("/{imageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteImage(@PathVariable UUID contentId, @PathVariable UUID imageId) {
		imageService.deleteImage(contentId, imageId);
	}
}
