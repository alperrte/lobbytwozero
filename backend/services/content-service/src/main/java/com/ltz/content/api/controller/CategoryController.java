package com.ltz.content.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.response.CategoryResponse;
import com.ltz.content.application.service.ContentFeedService;

/** Sabit kategori listesi (CONT-03): herkese açık, salt okunur. */
@RestController
@RequestMapping("/api/v1/content/categories")
public class CategoryController {

	private final ContentFeedService feedService;

	public CategoryController(ContentFeedService feedService) {
		this.feedService = feedService;
	}

	@GetMapping
	public List<CategoryResponse> listCategories() {
		return feedService.listCategories();
	}
}
