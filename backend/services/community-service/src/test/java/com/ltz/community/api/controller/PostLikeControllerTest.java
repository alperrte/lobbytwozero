package com.ltz.community.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ltz.community.api.dto.response.LikeStateResponse;
import com.ltz.community.application.service.PostLikeService;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(PostLikeController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class PostLikeControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/community/posts/" + POST_ID + "/like";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostLikeService postLikeService;

	@Test
	void authenticatedUserLikesAPost() throws Exception {
		when(postLikeService.like(POST_ID)).thenReturn(new LikeStateResponse(POST_ID, true, 3));

		mockMvc.perform(put(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.postId").value(POST_ID.toString()))
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(3));
	}

	@Test
	void authenticatedUserRemovesTheLike() throws Exception {
		when(postLikeService.unlike(POST_ID)).thenReturn(new LikeStateResponse(POST_ID, false, 2));

		mockMvc.perform(delete(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false))
				.andExpect(jsonPath("$.likeCount").value(2));
		verify(postLikeService).unlike(POST_ID);
	}

	@Test
	void anonymousCallersAreRejectedAndNothingIsExecuted() throws Exception {
		mockMvc.perform(put(URL)).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(delete(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(postLikeService);
	}

	@Test
	void invalidPostIdAndUnknownPostAreProblemDetails() throws Exception {
		mockMvc.perform(put("/api/v1/community/posts/not-a-uuid/like")
				.with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isBadRequest());

		when(postLikeService.like(POST_ID)).thenThrow(new PostNotFoundException());
		mockMvc.perform(put(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}
}
