package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ltz.community.api.dto.request.CreateRepostRequest;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.api.dto.response.RepostSourceResponse;
import com.ltz.community.application.service.RepostService;
import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.exception.AlreadyRepostedException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(RepostController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class RepostControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID REPOST_ID = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
	private static final UUID AUTHOR_ID = UUID.fromString("0198a3c2-ffff-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/community/posts/" + POST_ID + "/reposts";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RepostService repostService;

	private static PostViewResponse repostView(String comment) {
		Instant at = Instant.parse("2026-10-21T10:00:00Z");
		RepostSourceResponse source = new RepostSourceResponse(POST_ID, true, AUTHOR_ID, null, PostCategory.GENERAL,
				"özgün", at, List.of());
		return new PostViewResponse(REPOST_ID, USER_ID, null, null, PostCategory.GENERAL, comment, PostStatus.VISIBLE, at,
				at, 0, 0, false, List.of(), source);
	}

	@Test
	void authenticatedUserRepostsWithAComment() throws Exception {
		when(repostService.repost(eq(POST_ID), any(CreateRepostRequest.class))).thenReturn(repostView("not"));

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"not\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(REPOST_ID.toString()))
				.andExpect(jsonPath("$.authorId").value(USER_ID.toString()))
				.andExpect(jsonPath("$.body").value("not"))
				.andExpect(jsonPath("$.repostOf.postId").value(POST_ID.toString()))
				.andExpect(jsonPath("$.repostOf.authorId").value(AUTHOR_ID.toString()))
				.andExpect(jsonPath("$.repostOf.available").value(true));

		ArgumentCaptor<CreateRepostRequest> captured = ArgumentCaptor.forClass(CreateRepostRequest.class);
		verify(repostService).repost(eq(POST_ID), captured.capture());
		assertThat(captured.getValue().comment()).isEqualTo("not");
	}

	@Test
	void repostWithoutABodyIsAPlainRepost() throws Exception {
		when(repostService.repost(eq(POST_ID), isNull())).thenReturn(repostView(null));

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body").doesNotExist());
	}

	@Test
	void anonymousCallersAreRejected() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(repostService);
	}

	@Test
	void invalidCommentsAndIdentifiersAreRejected() throws Exception {
		String tooLong = "{\"comment\":\"" + "a".repeat(501) + "\"}";
		for (String json : new String[] { tooLong, "{\"comment\":\"a\\u0007b\"}", "{\"extra\":1}" }) {
			mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
					.contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(post("/api/v1/community/posts/not-a-uuid/reposts")
				.with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(repostService);
	}

	@Test
	void domainErrorsAreMappedToProblemDetails() throws Exception {
		when(repostService.repost(eq(POST_ID), any())).thenThrow(new PostNotFoundException())
				.thenThrow(new AlreadyRepostedException());

		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}
}
