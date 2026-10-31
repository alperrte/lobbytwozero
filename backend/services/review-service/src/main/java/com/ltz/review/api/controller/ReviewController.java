package com.ltz.review.api.controller;

import com.ltz.review.api.dto.request.CreateReviewRequest;
import com.ltz.review.api.dto.request.UpdateReviewRequest;
import com.ltz.review.api.dto.response.ReviewResponse;
import com.ltz.review.application.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) { this.service = service; }

    @PostMapping("/games/{gameId}/reviews")
    public ResponseEntity<ReviewResponse> create(@PathVariable UUID gameId, @AuthenticationPrincipal Jwt actor,
            @Valid @RequestBody CreateReviewRequest request) {
        var response = service.create(UUID.fromString(actor.getSubject()), gameId, request.body());
        return ResponseEntity.created(URI.create("/api/v1/review/reviews/" + response.id())).body(response);
    }

    @GetMapping("/reviews/{reviewId}")
    public ReviewResponse get(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor) {
        return service.get(UUID.fromString(actor.getSubject()), reviewId);
    }

    @PatchMapping("/reviews/{reviewId}")
    public ReviewResponse update(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor,
            @Valid @RequestBody UpdateReviewRequest request) {
        return service.update(UUID.fromString(actor.getSubject()), reviewId, request.version(), request.body());
    }

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor, @RequestParam @Min(0) long version) {
        service.delete(UUID.fromString(actor.getSubject()), reviewId, version);
    }
}
