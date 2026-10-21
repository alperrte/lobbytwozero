package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.ltz.community.application.mapper.PostMapper;
import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.application.service.PostAccess;
import com.ltz.community.application.service.PostService;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.GameCatalogUnavailableException;
import com.ltz.community.infrastructure.config.ClockConfig;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;
import com.ltz.community.infrastructure.security.SecurityContextCurrentUser;

@WebMvcTest(PostController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class, SecurityContextCurrentUser.class,
		PostService.class, PostAccess.class, PostMapper.class, ClockConfig.class })
class PostControllerTest {

	private static final String URL = "/api/v1/community/posts";
	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostRepository postRepository;

	@MockitoBean
	private PostImageRepository postImageRepository;

	@MockitoBean
	private GameCatalogPort gameCatalog;

	@BeforeEach
	void stubSave() {
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
			Post post = invocation.getArgument(0);
			ReflectionTestUtils.setField(post, "id", POST_ID);
			return post;
		});
	}

	@Test
	void authenticatedUserCreatesPostOwnedByTokenSubject() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"  Merhaba LTZ!\\nİkinci satır  \"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(POST_ID.toString()))
				.andExpect(jsonPath("$.authorId").value(USER_ID.toString()))
				.andExpect(jsonPath("$.body").value("Merhaba LTZ!\nİkinci satır"))
				.andExpect(jsonPath("$.status").value("VISIBLE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());

		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
		verify(postRepository).save(saved.capture());
		assertThat(saved.getValue().getAuthorId()).isEqualTo(USER_ID);
	}

	@Test
	void clientSuppliedAuthorIsRejectedAsUnknownProperty() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Merhaba\",\"authorId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		verify(postRepository, never()).save(any());
	}

	@Test
	void anonymousRequestIsRejectedWithProblemDetail() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Merhaba\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401));

		verify(postRepository, never()).save(any());
	}

	@Test
	void cookieOnlyWriteRequestIsBlockedByCsrfAsUnauthenticated() throws Exception {
		// jwt() yardımcısı CSRF'yi kendisi atlattığı için çerezle gelen gerçek bir tarayıcı isteği taklit edilir.
		mockMvc.perform(post(URL)
				.cookie(new jakarta.servlet.http.Cookie("SESSION", "browser-credential"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Merhaba\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		verify(postRepository, never()).save(any());
	}

	@Test
	void nonBearerAuthorizationHeaderDoesNotBypassCsrf() throws Exception {
		mockMvc.perform(post(URL).header("Authorization", "Basic dXNlcjpwYXNz")
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Merhaba\"}"))
				.andExpect(status().isForbidden());

		verify(postRepository, never()).save(any());
	}

	@Test
	void bearerRequestIsNotBlockedByCsrfEvenWhenCookiesArePresent() throws Exception {
		mockMvc.perform(post(URL).header("Authorization", "Bearer not-a-verifiable-token")
				.cookie(new jakarta.servlet.http.Cookie("SESSION", "browser-credential"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Merhaba\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void bearerTokenIsRejectedWhileJwtVerificationIsNotConfigured() throws Exception {
		mockMvc.perform(post(URL).header("Authorization", "Bearer not-a-verifiable-token")
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Merhaba\"}"))
				.andExpect(status().isUnauthorized());

		verify(postRepository, never()).save(any());
	}

	@Test
	void tokenWithNonUuidSubjectIsRejected() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject("not-a-uuid")))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Merhaba\"}"))
				.andExpect(status().isUnauthorized());

		verify(postRepository, never()).save(any());
	}

	@Test
	void blankBodyIsRejectedWithFieldViolation() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.violations", hasSize(1)))
				.andExpect(jsonPath("$.violations[0].field").value("body"));

		verify(postRepository, never()).save(any());
	}

	@Test
	void missingBodyIsRejected() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void tooLongBodyIsRejected() throws Exception {
		String body = "a".repeat(Post.BODY_MAX_LENGTH + 1);

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
				.andExpect(status().isBadRequest());

		verify(postRepository, never()).save(any());
	}

	@Test
	void bodyAtMaximumLengthIsAccepted() throws Exception {
		String body = "a".repeat(Post.BODY_MAX_LENGTH);

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void controlCharactersInBodyAreRejected() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"bad\\u0000text\"}"))
				.andExpect(status().isBadRequest());

		verify(postRepository, never()).save(any());
	}

	@Test
	void malformedJsonIsRejectedWithoutLeakingDetails() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest());
	}

	private Post storedPost(UUID authorId, PostStatus status) {
		Post post = Post.publish(authorId, "Eski içerik", Instant.parse("2026-10-21T08:00:00Z"));
		ReflectionTestUtils.setField(post, "id", POST_ID);
		ReflectionTestUtils.setField(post, "status", status);
		return post;
	}

	@Test
	void createPostWithVerifiedGameStoresGameId() throws Exception {
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Oyun hakkında\",\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gameId").value(GAME_ID.toString()));

		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
		verify(postRepository).save(saved.capture());
		assertThat(saved.getValue().getGameId()).isEqualTo(GAME_ID);
	}

	@Test
	void createPostWithoutGameDoesNotCallCatalogAndReturnsNullGameId() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Oyunsuz\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gameId").isEmpty());

		verify(gameCatalog, never()).isAvailable(any());
	}

	@Test
	void createPostWithUnknownGameIsUnprocessable() throws Exception {
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(false);

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Oyun\",\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		verify(postRepository, never()).save(any());
	}

	@Test
	void createPostReturnsServiceUnavailableWhenGameCatalogIsDown() throws Exception {
		when(gameCatalog.isAvailable(GAME_ID)).thenThrow(new GameCatalogUnavailableException(new IOException("down")));

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Oyun\",\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The game catalog is temporarily unavailable."));

		verify(postRepository, never()).save(any());
	}

	@Test
	void createPostRejectsMalformedGameId() throws Exception {
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Oyun\",\"gameId\":\"not-a-uuid\"}"))
				.andExpect(status().isBadRequest());

		verify(gameCatalog, never()).isAvailable(any());
	}

	@Test
	void ownerLinksGameToPost() throws Exception {
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(storedPost(USER_ID, PostStatus.VISIBLE)));
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(true);
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(put(URL + "/" + POST_ID + "/game").with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameId").value(GAME_ID.toString()));
	}

	@Test
	void linkingGameToAnotherUsersPostIsForbiddenWithoutCallingCatalog() throws Exception {
		when(postRepository.findById(POST_ID))
				.thenReturn(Optional.of(storedPost(UUID.randomUUID(), PostStatus.VISIBLE)));

		mockMvc.perform(put(URL + "/" + POST_ID + "/game").with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isForbidden());

		verify(gameCatalog, never()).isAvailable(any());
	}

	@Test
	void linkGameRequiresGameIdAndAuthentication() throws Exception {
		mockMvc.perform(put(URL + "/" + POST_ID + "/game").with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put(URL + "/" + POST_ID + "/game").contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void linkGameWithUnknownGameIsUnprocessable() throws Exception {
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(storedPost(USER_ID, PostStatus.VISIBLE)));
		when(gameCatalog.isAvailable(GAME_ID)).thenReturn(false);

		mockMvc.perform(put(URL + "/" + POST_ID + "/game").with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + GAME_ID + "\"}"))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void ownerUnlinksGame() throws Exception {
		Post post = storedPost(USER_ID, PostStatus.VISIBLE);
		post.linkGame(GAME_ID, Instant.parse("2026-10-21T08:30:00Z"));
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		mockMvc.perform(delete(URL + "/" + POST_ID + "/game")
				.with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isNoContent());

		assertThat(post.getGameId()).isNull();
		verify(postRepository).save(post);
	}

	@Test
	void anotherUserCannotUnlinkGame() throws Exception {
		Post post = storedPost(UUID.randomUUID(), PostStatus.VISIBLE);
		post.linkGame(GAME_ID, Instant.parse("2026-10-21T08:30:00Z"));
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

		mockMvc.perform(delete(URL + "/" + POST_ID + "/game")
				.with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isForbidden());

		assertThat(post.getGameId()).isEqualTo(GAME_ID);
	}

	@Test
	void ownerUpdatesPostBody() throws Exception {
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(storedPost(USER_ID, PostStatus.VISIBLE)));

		mockMvc.perform(patch(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Düzenlenmiş içerik\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(POST_ID.toString()))
				.andExpect(jsonPath("$.authorId").value(USER_ID.toString()))
				.andExpect(jsonPath("$.body").value("Düzenlenmiş içerik"))
				.andExpect(jsonPath("$.createdAt").value("2026-10-21T08:00:00Z"));
	}

	@Test
	void anotherUserCannotUpdatePost() throws Exception {
		when(postRepository.findById(POST_ID))
				.thenReturn(Optional.of(storedPost(UUID.randomUUID(), PostStatus.VISIBLE)));

		mockMvc.perform(patch(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Ele geçirme\"}"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		verify(postRepository, never()).save(any());
	}

	@Test
	void updatingUnknownPostReturnsNotFound() throws Exception {
		when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

		mockMvc.perform(patch(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Yeni\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void updatingHiddenPostByOwnerReturnsConflict() throws Exception {
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(storedPost(USER_ID, PostStatus.HIDDEN)));

		mockMvc.perform(patch(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Yeni\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void concurrentModificationReturnsConflict() throws Exception {
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(storedPost(USER_ID, PostStatus.VISIBLE)));
		when(postRepository.save(any(Post.class)))
				.thenThrow(new ObjectOptimisticLockingFailureException(Post.class, POST_ID));

		mockMvc.perform(patch(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Yeni\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void updateRequiresAuthentication() throws Exception {
		mockMvc.perform(patch(URL + "/" + POST_ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Yeni\"}"))
				.andExpect(status().isUnauthorized());

		verify(postRepository, never()).findById(any());
	}

	@Test
	void updateRejectsInvalidBodyAndUnknownProperties() throws Exception {
		mockMvc.perform(patch(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(patch(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Yeni\",\"authorId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest());

		verify(postRepository, never()).findById(any());
	}

	@Test
	void ownerDeletesPost() throws Exception {
		Post post = storedPost(USER_ID, PostStatus.VISIBLE);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

		mockMvc.perform(delete(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		verify(postRepository).save(post);
		verify(postImageRepository).deleteByPostId(POST_ID);
		assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
	}

	@Test
	void anotherUserCannotDeletePost() throws Exception {
		Post post = storedPost(UUID.randomUUID(), PostStatus.VISIBLE);
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));

		mockMvc.perform(delete(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(post.getStatus()).isEqualTo(PostStatus.VISIBLE);
		verify(postRepository, never()).save(any());
	}

	@Test
	void deletingUnknownOrAlreadyDeletedPostReturnsNotFound() throws Exception {
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.empty())
				.thenReturn(Optional.of(storedPost(USER_ID, PostStatus.DELETED)));

		mockMvc.perform(delete(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete(URL + "/" + POST_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteRequiresAuthenticationAndValidPostId() throws Exception {
		mockMvc.perform(delete(URL + "/" + POST_ID)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(URL + "/not-a-uuid").with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isBadRequest());

		verify(postRepository, never()).findById(any());
		verify(postRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void updateRejectsMalformedPostId() throws Exception {
		mockMvc.perform(patch(URL + "/not-a-uuid").with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Yeni\"}"))
				.andExpect(status().isBadRequest());
	}
}
