package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.response.PostPageResponse;
import com.ltz.community.application.service.PostFeedService;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(UserPostController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class UserPostControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "/api/v1/community/users/" + USER_ID + "/posts";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostFeedService postFeedService;

	@Test
	void anonymousVisitorCanReadUserPostHistory() throws Exception {
		when(postFeedService.listUserPosts(eq(USER_ID), any(PostFeedRequest.class)))
				.thenReturn(new PostPageResponse(List.of(), "sonraki"));

		mockMvc.perform(get(URL).param("limit", "5").param("sort", "OLDEST"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.nextCursor").value("sonraki"));

		ArgumentCaptor<PostFeedRequest> captured = ArgumentCaptor.forClass(PostFeedRequest.class);
		verify(postFeedService).listUserPosts(eq(USER_ID), captured.capture());
		assertThat(captured.getValue().limit()).isEqualTo(5);
		assertThat(captured.getValue().sort()).isEqualTo(PostSort.OLDEST);
	}

	@Test
	void authenticatedUserCanReadHistoryToo() throws Exception {
		when(postFeedService.listUserPosts(eq(USER_ID), any(PostFeedRequest.class)))
				.thenReturn(new PostPageResponse(List.of(), null));

		mockMvc.perform(get(URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isOk());
	}

	@Test
	void malformedUserIdAndParametersAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/community/users/not-a-uuid/posts"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(get(URL).param("limit", "0")).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("sort", "BEST")).andExpect(status().isBadRequest());

		verify(postFeedService, never()).listUserPosts(any(), any());
	}

	@Test
	void onlyTheHistoryListingIsPublicUnderTheUsersPath() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/community/users/" + USER_ID)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/community/users/" + USER_ID + "/posts/extra")).andExpect(status().isUnauthorized());
	}
}
