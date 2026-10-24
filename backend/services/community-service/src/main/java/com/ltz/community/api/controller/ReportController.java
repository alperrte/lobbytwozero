package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.request.CreateReportRequest;
import com.ltz.community.api.dto.response.ReportResponse;
import com.ltz.community.application.service.ReportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/community")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@PostMapping("/posts/{postId}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public ReportResponse reportPost(@PathVariable UUID postId, @Valid @RequestBody CreateReportRequest request) {
		return reportService.reportPost(postId, request);
	}

	@PostMapping("/comments/{commentId}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public ReportResponse reportComment(@PathVariable UUID commentId, @Valid @RequestBody CreateReportRequest request) {
		return reportService.reportComment(commentId, request);
	}
}
