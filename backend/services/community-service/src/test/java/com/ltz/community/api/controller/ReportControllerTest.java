package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ltz.community.api.dto.request.CreateReportRequest;
import com.ltz.community.api.dto.response.ReportResponse;
import com.ltz.community.application.service.ReportService;
import com.ltz.community.domain.enums.ReportReason;
import com.ltz.community.domain.enums.ReportStatus;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.domain.exception.AlreadyReportedException;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.domain.exception.ReportingOwnContentException;
import com.ltz.community.domain.exception.TooManyReportsException;
import com.ltz.community.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.community.infrastructure.security.SecurityConfig;

@WebMvcTest(ReportController.class)
@Import({ SecurityConfig.class, ProblemDetailSecurityHandler.class })
class ReportControllerTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID POST_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID COMMENT_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final UUID REPORT_ID = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
	private static final String POST_URL = "/api/v1/community/posts/" + POST_ID + "/reports";
	private static final String COMMENT_URL = "/api/v1/community/comments/" + COMMENT_ID + "/reports";
	private static final String VALID = "{\"reason\":\"SPAM\",\"detail\":\"reklam\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReportService reportService;

	private static ReportResponse response(ReportTarget target, UUID targetId) {
		return new ReportResponse(REPORT_ID, target, targetId, ReportReason.SPAM, ReportStatus.OPEN,
				Instant.parse("2026-10-21T10:00:00Z"));
	}

	@Test
	void authenticatedUserReportsAPostAndAComment() throws Exception {
		when(reportService.reportPost(eq(POST_ID), any(CreateReportRequest.class))).thenReturn(response(ReportTarget.POST, POST_ID));
		when(reportService.reportComment(eq(COMMENT_ID), any(CreateReportRequest.class)))
				.thenReturn(response(ReportTarget.COMMENT, COMMENT_ID));

		mockMvc.perform(post(POST_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
				.andExpect(jsonPath("$.targetType").value("POST"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.reporterId").doesNotExist())
				.andExpect(jsonPath("$.targetAuthorId").doesNotExist());
		mockMvc.perform(post(COMMENT_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"HARASSMENT\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetType").value("COMMENT"));

		ArgumentCaptor<CreateReportRequest> captured = ArgumentCaptor.forClass(CreateReportRequest.class);
		verify(reportService).reportPost(eq(POST_ID), captured.capture());
		assertThat(captured.getValue().reason()).isEqualTo(ReportReason.SPAM);
		assertThat(captured.getValue().detail()).isEqualTo("reklam");
	}

	@Test
	void anonymousCallersAreRejected() throws Exception {
		mockMvc.perform(post(POST_URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mockMvc.perform(post(COMMENT_URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(reportService);
	}

	@Test
	void invalidRequestsAreRejectedBeforeReachingTheService() throws Exception {
		String[] invalid = { "{}", "{\"reason\":\"BORING\"}", "{\"reason\":\"spam\"}", "{\"reason\":null}", "{\"reason\":1}",
				"{\"reason\":\"SPAM\",\"detail\":\"" + "a".repeat(501) + "\"}", "{\"reason\":\"SPAM\",\"detail\":\"a\\u0007b\"}",
				"{\"reason\":\"SPAM\",\"reporterId\":\"" + USER_ID + "\"}" };
		for (String json : invalid) {
			mockMvc.perform(post(POST_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
					.contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(post("/api/v1/community/posts/not-a-uuid/reports")
				.with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(reportService);
	}

	@Test
	void domainErrorsAreMappedToProblemDetails() throws Exception {
		when(reportService.reportPost(eq(POST_ID), any())).thenThrow(new PostNotFoundException())
				.thenThrow(new AlreadyReportedException()).thenThrow(new ReportingOwnContentException())
				.thenThrow(new TooManyReportsException());
		when(reportService.reportComment(eq(COMMENT_ID), any())).thenThrow(new CommentNotFoundException());

		int[] expected = { 404, 409, 422, 429 };
		for (int status : expected) {
			mockMvc.perform(post(POST_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
					.contentType(MediaType.APPLICATION_JSON).content(VALID))
					.andExpect(status().is(status))
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		mockMvc.perform(post(COMMENT_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isNotFound());
	}

	@Test
	void rateLimitedResponsesTellTheClientWhenToRetry() throws Exception {
		when(reportService.reportPost(eq(POST_ID), any())).thenThrow(new TooManyReportsException());

		mockMvc.perform(post(POST_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString())))
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "3600"));
	}
}
