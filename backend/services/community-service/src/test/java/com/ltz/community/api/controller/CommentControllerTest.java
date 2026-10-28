package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ltz.community.api.dto.request.CommentPageRequest;
import com.ltz.community.api.dto.request.CreateCommentRequest;
import com.ltz.community.api.dto.response.CommentPageResponse;
import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.application.service.CommentFeedService;
import com.ltz.community.application.service.CommentService;
import com.ltz.community.domain.enums.CommentStatus;
import com.ltz.community.domain.exception.CommentAccessDeniedException;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.InvalidPostQueryException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(CommentController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class CommentControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID COMMENT_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final UUID PARENT_ID = UUID.fromString("0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66");
	private static final String POST_COMMENTS = "/api/v1/community/posts/" + POST_ID + "/comments";
	private static final String COMMENT_URL = "/api/v1/community/comments/" + COMMENT_ID;
	private static final String JSON_BODY = "{\"body\":\"merhaba\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CommentService commentService;

	@MockitoBean
	private CommentFeedService commentFeedService;

	private static CommentResponse response(UUID parent, String body) {
		Instant at = Instant.parse("2026-10-21T10:00:00Z");
		return new CommentResponse(COMMENT_ID, POST_ID, USER_ID, parent, body, CommentStatus.VISIBLE, at, at, 0);
	}

	@Test
	void authenticatedUserCreatesATopLevelComment() throws Exception {
		when(commentService.createComment(eq(POST_ID), any(CreateCommentRequest.class)))
				.thenReturn(response(null, "merhaba"));

		mockMvc.perform(post(POST_COMMENTS).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content(JSON_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(COMMENT_ID.toString()))
				.andExpect(jsonPath("$.postId").value(POST_ID.toString()))
				.andExpect(jsonPath("$.body").value("merhaba"))
				.andExpect(jsonPath("$.parentCommentId").doesNotExist())
				.andExpect(jsonPath("$.replyCount").value(0));
	}

	@Test
	void replyCarriesTheParentCommentId() throws Exception {
		when(commentService.createComment(eq(POST_ID), any(CreateCommentRequest.class)))
				.thenReturn(response(PARENT_ID, "yanıt"));

		mockMvc.perform(post(POST_COMMENTS).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"yanıt\",\"parentCommentId\":\"" + PARENT_ID + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentCommentId").value(PARENT_ID.toString()));

		ArgumentCaptor<CreateCommentRequest> captured = ArgumentCaptor.forClass(CreateCommentRequest.class);
		verify(commentService).createComment(eq(POST_ID), captured.capture());
		assertThat(captured.getValue().parentCommentId()).isEqualTo(PARENT_ID);
	}

	@Test
	void writeEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(post(POST_COMMENTS).contentType(MediaType.APPLICATION_JSON).content(JSON_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(patch(COMMENT_URL).contentType(MediaType.APPLICATION_JSON).content(JSON_BODY))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(COMMENT_URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(commentService);
	}

	@Test
	void invalidCommentBodiesAreRejectedBeforeReachingTheService() throws Exception {
		String tooLong = "{\"body\":\"" + "a".repeat(2001) + "\"}";
		String[] invalid = { "{}", "{\"body\":\"   \"}", "{\"body\":\"\"}", tooLong, "{\"body\":\"a\\u0007b\"}",
				"{\"body\":\"x\",\"parentCommentId\":\"degil\"}", "{\"body\":\"x\",\"extra\":1}" };
		for (String json : invalid) {
			mockMvc.perform(post(POST_COMMENTS).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
					.contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
			mockMvc.perform(patch(COMMENT_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
					.contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(post(POST_COMMENTS).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\",\"parentCommentId\":\"degil\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(commentService);
	}

	@Test
	void anonymousVisitorsCanReadCommentsAndReplies() throws Exception {
		when(commentFeedService.listComments(eq(POST_ID), any(CommentPageRequest.class)))
				.thenReturn(new CommentPageResponse(List.of(response(null, "yorum")), "sonraki"));
		when(commentFeedService.listReplies(eq(COMMENT_ID), any(CommentPageRequest.class)))
				.thenReturn(new CommentPageResponse(List.of(), null));

		mockMvc.perform(get(POST_COMMENTS).param("limit", "5").param("cursor", "abc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].body").value("yorum"))
				.andExpect(jsonPath("$.nextCursor").value("sonraki"));
		mockMvc.perform(get(COMMENT_URL + "/replies"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray());

		ArgumentCaptor<CommentPageRequest> captured = ArgumentCaptor.forClass(CommentPageRequest.class);
		verify(commentFeedService).listComments(eq(POST_ID), captured.capture());
		assertThat(captured.getValue().limit()).isEqualTo(5);
		assertThat(captured.getValue().cursor()).isEqualTo("abc");
	}

	@Test
	void invalidPagingParametersAndIdentifiersAreRejected() throws Exception {
		mockMvc.perform(get(POST_COMMENTS).param("limit", "0")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POST_COMMENTS).param("limit", "51")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POST_COMMENTS).param("cursor", "x".repeat(201))).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/community/posts/not-a-uuid/comments")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/community/comments/not-a-uuid/replies")).andExpect(status().isBadRequest());
		verifyNoInteractions(commentFeedService);
	}

	@Test
	void authorUpdatesAndDeletesOwnComment() throws Exception {
		when(commentService.updateComment(eq(COMMENT_ID), any())).thenReturn(response(null, "düzenlendi"));

		mockMvc.perform(patch(COMMENT_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"düzenlendi\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").value("düzenlendi"));
		mockMvc.perform(delete(COMMENT_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isNoContent());
		verify(commentService).deleteComment(COMMENT_ID);
	}

	@Test
	void domainErrorsAreMappedToProblemDetails() throws Exception {
		when(commentService.createComment(eq(POST_ID), any())).thenThrow(new PostNotFoundException());
		when(commentService.updateComment(eq(COMMENT_ID), any())).thenThrow(new CommentAccessDeniedException());
		doThrow(new CommentNotFoundException()).when(commentService).deleteComment(COMMENT_ID);
		when(commentFeedService.listComments(eq(POST_ID), any())).thenThrow(new InvalidPostQueryException("The cursor is invalid."));
		when(commentFeedService.listReplies(eq(COMMENT_ID), any()))
				.thenThrow(new ObjectOptimisticLockingFailureException("Comment", COMMENT_ID));

		mockMvc.perform(post(POST_COMMENTS).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content(JSON_BODY))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch(COMMENT_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content(JSON_BODY))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(delete(COMMENT_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(POST_COMMENTS)).andExpect(status().isBadRequest());
		mockMvc.perform(get(COMMENT_URL + "/replies")).andExpect(status().isConflict());
	}
}
