package com.ltz.content.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ltz.content.TestImages;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.service.ContentAccess;
import com.ltz.content.application.service.ContentImageService;
import com.ltz.content.application.service.ImageSanitizer;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentImage;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.infrastructure.config.ClockConfig;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.content.infrastructure.security.SecurityConfig;
import com.ltz.content.infrastructure.security.SecurityContextCurrentUser;

/**
 * Görsel ucu: gerçek servis zinciri ve gerçek {@link ImageSanitizer} ile (yalnız depolama sahte). Geçersiz görsel,
 * sahiplik ihlali ve oturumsuz istek senaryolarını HTTP düzeyinde doğrular.
 */
@WebMvcTest(ContentImageController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class, SecurityContextCurrentUser.class,
		ContentImageService.class, ContentAccess.class, ImageSanitizer.class, ContentMapper.class, ClockConfig.class })
class ContentImageControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER_ID = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID IMAGE_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/content/contents/" + CONTENT_ID + "/images";
	private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentRepository contentRepository;

	@MockitoBean
	private ContentImageRepository imageRepository;

	private final RequestPostProcessor owner = jwt().jwt(token -> token.subject(USER_ID.toString()));
	private final RequestPostProcessor stranger = jwt().jwt(token -> token.subject(OTHER_ID.toString()));

	private void givenContent(UUID author, ContentStatus status) {
		Content content = Content.draft(author, ContentType.NEWS, "tr", "a".repeat(64), NOW);
		ReflectionTestUtils.setField(content, "id", CONTENT_ID);
		ReflectionTestUtils.setField(content, "status", status);
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
	}

	@BeforeEach
	void stubStorage() {
		givenContent(USER_ID, ContentStatus.DRAFT);
		when(imageRepository.findSortOrdersByContentId(CONTENT_ID)).thenReturn(List.of());
		when(imageRepository.save(any(ContentImage.class))).thenAnswer(invocation -> {
			ContentImage image = invocation.getArgument(0);
			ReflectionTestUtils.setField(image, "id", IMAGE_ID);
			return image;
		});
	}

	private static MockMultipartFile file(byte[] bytes, String filename, String contentType) {
		return new MockMultipartFile("file", filename, contentType, bytes);
	}

	// ------------------------------------------------------------------ yükleme

	@Test
	void ownerUploadsAPngAndGetsTheStoredImageMetadata() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.png(40, 20), "foto.png", "image/png")).with(owner))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(IMAGE_ID.toString()))
				.andExpect(jsonPath("$.contentId").value(CONTENT_ID.toString()))
				.andExpect(jsonPath("$.mimeType").value("image/png"))
				.andExpect(jsonPath("$.width").value(40))
				.andExpect(jsonPath("$.height").value(20))
				.andExpect(jsonPath("$.sortOrder").value(0))
				.andExpect(jsonPath("$.contentUrl").value(URL + "/" + IMAGE_ID));

		ArgumentCaptor<ContentImage> saved = ArgumentCaptor.forClass(ContentImage.class);
		verify(imageRepository).save(saved.capture());
		assertThat(saved.getValue().getSizeBytes()).isEqualTo(saved.getValue().getContent().length);
	}

	@Test
	void jpegIsAccepted() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.jpeg(30, 30), "foto.jpg", "image/jpeg")).with(owner))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mimeType").value("image/jpeg"));
	}

	@Test
	void theDeclaredContentTypeAndFileNameAreNotTrusted() throws Exception {
		// Gerçek bir PNG, yalan söyleyen ad ve türle gelse de içeriğine göre kabul edilir ve gerçek türle saklanır.
		mockMvc.perform(multipart(URL).file(file(TestImages.png(10, 10), "betik.html", "text/html")).with(owner))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mimeType").value("image/png"));
	}

	@Test
	void svgAndHtmlAreRejectedEvenWhenTheyClaimToBeImages() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.svgWithScript(), "logo.svg", "image/svg+xml")).with(owner))
				.andExpect(status().isUnsupportedMediaType());
		mockMvc.perform(multipart(URL).file(file(TestImages.svgWithScript(), "foto.png", "image/png")).with(owner))
				.andExpect(status().isUnsupportedMediaType());
		mockMvc.perform(multipart(URL).file(file(TestImages.html(), "foto.png", "image/png")).with(owner))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(multipart(URL).file(file(TestImages.gif(10, 10), "anim.gif", "image/gif")).with(owner))
				.andExpect(status().isUnsupportedMediaType());

		verify(imageRepository, never()).save(any());
	}

	@Test
	void corruptEmptyAndOversizedFilesAreRejected() throws Exception {
		byte[] png = TestImages.png(40, 40);
		byte[] truncated = java.util.Arrays.copyOf(png, png.length / 2);

		mockMvc.perform(multipart(URL).file(file(truncated, "bozuk.png", "image/png")).with(owner))
				.andExpect(status().isUnprocessableContent());
		mockMvc.perform(multipart(URL).file(file(new byte[0], "bos.png", "image/png")).with(owner))
				.andExpect(status().isBadRequest());
		mockMvc.perform(multipart(URL).file(file(new byte[ContentImage.MAX_SIZE_BYTES + 1], "buyuk.png", "image/png")).with(owner))
				.andExpect(status().isContentTooLarge());
		mockMvc.perform(multipart(URL).file(file(TestImages.png(ContentImage.MAX_SIDE_PIXELS + 1, 1), "genis.png", "image/png"))
				.with(owner)).andExpect(status().isUnprocessableContent());

		verify(imageRepository, never()).save(any());
	}

	@Test
	void aRequestWithoutTheFilePartIsABadRequest() throws Exception {
		mockMvc.perform(multipart(URL).with(owner)).andExpect(status().isBadRequest());
	}

	@Test
	void theImageLimitPerContentIsEnforced() throws Exception {
		when(imageRepository.findSortOrdersByContentId(CONTENT_ID)).thenReturn(List.of(0, 1, 2, 3, 4, 5));

		mockMvc.perform(multipart(URL).file(file(TestImages.png(10, 10), "a.png", "image/png")).with(owner))
				.andExpect(status().isConflict());
		verify(imageRepository, never()).save(any());
	}

	// ------------------------------------------------------------------ sahiplik

	@Test
	void anotherUserCannotUploadToSomeoneElsesDraft() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.png(10, 10), "a.png", "image/png")).with(stranger))
				.andExpect(status().isNotFound());

		verify(imageRepository, never()).save(any());
	}

	@Test
	void anotherUserCannotUploadToSomeoneElsesPublishedContent() throws Exception {
		givenContent(USER_ID, ContentStatus.PUBLISHED);

		mockMvc.perform(multipart(URL).file(file(TestImages.png(10, 10), "a.png", "image/png")).with(stranger))
				.andExpect(status().isForbidden());
	}

	@Test
	void imagesCannotBeAddedToContentThatIsNoLongerADraft() throws Exception {
		givenContent(USER_ID, ContentStatus.PUBLISHED);

		mockMvc.perform(multipart(URL).file(file(TestImages.png(10, 10), "a.png", "image/png")).with(owner))
				.andExpect(status().isConflict());
	}

	@Test
	void uploadsWithoutATokenAreUnauthorized() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.png(10, 10), "a.png", "image/png")))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		verify(imageRepository, never()).save(any());
	}

	// ------------------------------------------------------------------ okuma

	@Test
	void ownerReadsTheDraftImageWithSafeHeaders() throws Exception {
		ContentImage image = ContentImage.attach(CONTENT_ID, new byte[] { 1, 2, 3 }, "image/png", 10, 10, 0, NOW);
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.of(image));

		mockMvc.perform(get(URL + "/" + IMAGE_ID).with(owner))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(new byte[] { 1, 2, 3 }))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
				.andExpect(header().string("Content-Disposition", "inline"));
	}

	@Test
	void anotherUserCannotReadADraftImage() throws Exception {
		ContentImage image = ContentImage.attach(CONTENT_ID, new byte[] { 1 }, "image/png", 10, 10, 0, NOW);
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.of(image));

		mockMvc.perform(get(URL + "/" + IMAGE_ID).with(stranger)).andExpect(status().isNotFound());
	}

	@Test
	void readingWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get(URL + "/" + IMAGE_ID)).andExpect(status().isUnauthorized());
	}

	@Test
	void aMissingImageIs404() throws Exception {
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.empty());

		mockMvc.perform(get(URL + "/" + IMAGE_ID).with(owner)).andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ silme

	@Test
	void ownerDeletesAnImage() throws Exception {
		ContentImage image = ContentImage.attach(CONTENT_ID, new byte[] { 1 }, "image/png", 10, 10, 0, NOW);
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.of(image));

		mockMvc.perform(delete(URL + "/" + IMAGE_ID).with(owner)).andExpect(status().isNoContent());

		verify(imageRepository).delete(image);
	}

	@Test
	void anotherUserCannotDeleteAnImage() throws Exception {
		ContentImage image = ContentImage.attach(CONTENT_ID, new byte[] { 1 }, "image/png", 10, 10, 0, NOW);
		when(imageRepository.findByIdAndContentId(IMAGE_ID, CONTENT_ID)).thenReturn(Optional.of(image));

		mockMvc.perform(delete(URL + "/" + IMAGE_ID).with(stranger)).andExpect(status().isNotFound());

		verify(imageRepository, never()).delete(any(ContentImage.class));
	}

	@Test
	void deletingWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(delete(URL + "/" + IMAGE_ID)).andExpect(status().isUnauthorized());
	}
}
