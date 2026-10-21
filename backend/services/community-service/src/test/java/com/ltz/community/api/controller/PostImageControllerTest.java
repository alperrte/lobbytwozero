package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import com.ltz.community.TestImages;
import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.service.ImageSanitizer;
import com.ltz.community.application.service.PostAccess;
import com.ltz.community.application.service.PostImageService;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;
import com.ltz.community.infrastructure.security.SecurityContextCurrentUser;

@WebMvcTest(PostImageController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class, SecurityContextCurrentUser.class,
		PostImageService.class, PostAccess.class, ImageSanitizer.class, PostMapper.class, ClockConfig.class })
class PostImageControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID IMAGE_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/community/posts/" + POST_ID + "/images";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostRepository postRepository;

	@MockitoBean
	private PostImageRepository postImageRepository;

	private final RequestPostProcessor owner = jwt().jwt(token -> token.subject(USER_ID.toString()));

	private Post postOf(UUID authorId, PostStatus status) {
		Post post = Post.publish(authorId, "içerik", Instant.parse("2026-10-21T08:00:00Z"));
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	@BeforeEach
	void stubStorage() {
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(postOf(USER_ID, PostStatus.VISIBLE)));
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(postOf(USER_ID, PostStatus.VISIBLE)));
		when(postImageRepository.findSortOrdersByPostId(POST_ID)).thenReturn(List.of());
		when(postImageRepository.save(any(PostImage.class))).thenAnswer(invocation -> {
			PostImage image = invocation.getArgument(0);
			ReflectionTestUtils.setField(image, "id", IMAGE_ID);
			return image;
		});
	}

	private MockMultipartFile file(byte[] bytes, String filename, String contentType) {
		return new MockMultipartFile("file", filename, contentType, bytes);
	}

	@Test
	void ownerUploadsPngAndGetsStoredImageMetadata() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.png(40, 20), "photo.png", "image/png")).with(owner))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(IMAGE_ID.toString()))
				.andExpect(jsonPath("$.postId").value(POST_ID.toString()))
				.andExpect(jsonPath("$.mimeType").value("image/png"))
				.andExpect(jsonPath("$.width").value(40))
				.andExpect(jsonPath("$.height").value(20))
				.andExpect(jsonPath("$.sortOrder").value(0))
				.andExpect(jsonPath("$.contentUrl").value(URL + "/" + IMAGE_ID));

		ArgumentCaptor<PostImage> saved = ArgumentCaptor.forClass(PostImage.class);
		verify(postImageRepository).save(saved.capture());
		assertThat(saved.getValue().getSizeBytes()).isEqualTo(saved.getValue().getContent().length);
	}

	@Test
	void mimeTypeComesFromImageContentNotFromClientDeclaration() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.jpeg(30, 30), "photo.png", "image/png")).with(owner))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mimeType").value("image/jpeg"));
	}

	@Test
	void textFileDisguisedAsPngIsRejected() throws Exception {
		byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

		mockMvc.perform(multipart(URL).file(file(script, "photo.png", "image/png")).with(owner))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		verify(postImageRepository, never()).save(any());
	}

	@Test
	void gifIsRejected() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.gif(8, 8), "anim.gif", "image/gif")).with(owner))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void corruptImageIsRejected() throws Exception {
		byte[] png = TestImages.png(64, 64);
		byte[] truncated = java.util.Arrays.copyOf(png, png.length / 2);

		mockMvc.perform(multipart(URL).file(file(truncated, "photo.png", "image/png")).with(owner))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void emptyFileIsRejected() throws Exception {
		mockMvc.perform(multipart(URL).file(file(new byte[0], "photo.png", "image/png")).with(owner))
				.andExpect(status().isBadRequest());
	}

	@Test
	void oversizedFileIsRejected() throws Exception {
		byte[] big = new byte[PostImage.MAX_SIZE_BYTES + 1];

		mockMvc.perform(multipart(URL).file(file(big, "big.png", "image/png")).with(owner))
				.andExpect(status().isContentTooLarge());

		verify(postImageRepository, never()).save(any());
	}

	@Test
	void missingFilePartIsRejected() throws Exception {
		mockMvc.perform(multipart(URL).with(owner)).andExpect(status().isBadRequest());
	}

	@Test
	void nonMultipartRequestIsRejected() throws Exception {
		mockMvc.perform(post(URL).with(owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void uploadRequiresAuthentication() throws Exception {
		mockMvc.perform(multipart(URL).file(file(TestImages.png(8, 8), "photo.png", "image/png")))
				.andExpect(status().isUnauthorized());

		verify(postImageRepository, never()).save(any());
	}

	@Test
	void anotherUserCannotUploadToPost() throws Exception {
		when(postRepository.findByIdForUpdate(POST_ID))
				.thenReturn(Optional.of(postOf(UUID.randomUUID(), PostStatus.VISIBLE)));

		mockMvc.perform(multipart(URL).file(file(TestImages.png(8, 8), "photo.png", "image/png")).with(owner))
				.andExpect(status().isForbidden());

		verify(postImageRepository, never()).save(any());
	}

	@Test
	void fifthImageIsRejectedWithConflict() throws Exception {
		when(postImageRepository.findSortOrdersByPostId(POST_ID)).thenReturn(List.of(0, 1, 2, 3));

		mockMvc.perform(multipart(URL).file(file(TestImages.png(8, 8), "photo.png", "image/png")).with(owner))
				.andExpect(status().isConflict());
	}

	@Test
	void imageContentIsServedWithServerVerifiedTypeAndHardenedHeaders() throws Exception {
		PostImage image = PostImage.attach(POST_ID, TestImages.png(8, 8), "image/png", 8, 8, 0, Instant.now());
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.of(image));

		mockMvc.perform(get(URL + "/" + IMAGE_ID).with(owner))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(image.getContent()))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Content-Disposition", "inline"))
				.andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
	}

	@Test
	void imageContentRequiresAuthentication() throws Exception {
		mockMvc.perform(get(URL + "/" + IMAGE_ID)).andExpect(status().isUnauthorized());
	}

	@Test
	void imageOfAnotherUsersHiddenPostIsNotFound() throws Exception {
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(postOf(UUID.randomUUID(), PostStatus.HIDDEN)));

		mockMvc.perform(get(URL + "/" + IMAGE_ID).with(owner)).andExpect(status().isNotFound());

		verify(postImageRepository, never()).findByIdAndPostId(any(), any());
	}

	@Test
	void unknownImageIsNotFound() throws Exception {
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.empty());

		mockMvc.perform(get(URL + "/" + IMAGE_ID).with(owner)).andExpect(status().isNotFound());
	}

	@Test
	void malformedImageIdIsRejected() throws Exception {
		mockMvc.perform(get(URL + "/not-a-uuid").with(owner)).andExpect(status().isBadRequest());
	}

	@Test
	void ownerDeletesImage() throws Exception {
		PostImage image = PostImage.attach(POST_ID, new byte[] { 1 }, "image/png", 1, 1, 0, Instant.now());
		when(postImageRepository.findByIdAndPostId(IMAGE_ID, POST_ID)).thenReturn(Optional.of(image));

		mockMvc.perform(delete(URL + "/" + IMAGE_ID).with(owner)).andExpect(status().isNoContent());

		verify(postImageRepository).delete(image);
	}

	@Test
	void anotherUserCannotDeleteImage() throws Exception {
		when(postRepository.findByIdForUpdate(POST_ID))
				.thenReturn(Optional.of(postOf(UUID.randomUUID(), PostStatus.VISIBLE)));

		mockMvc.perform(delete(URL + "/" + IMAGE_ID).with(owner)).andExpect(status().isForbidden());

		verify(postImageRepository, never()).delete(any());
	}

	@Test
	void deleteRequiresAuthentication() throws Exception {
		mockMvc.perform(delete(URL + "/" + IMAGE_ID)).andExpect(status().isUnauthorized());
	}
}
