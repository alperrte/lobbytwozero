package com.ltz.lfp.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.lfp.api.dto.request.CreateListingRequest;
import com.ltz.lfp.api.dto.response.ListingResponse;
import com.ltz.lfp.application.service.ListingService;
import com.ltz.lfp.application.service.ListingView;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Oyun arkadaşı ilanları (LTZ-UMT-LFP-01). İlanın sahibi her zaman token sahibidir. */
@RestController
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    /** LFP-01..09: ilan açar; 201 + {@code Location}. */
    @PostMapping(LfpApiPaths.LISTINGS)
    public ResponseEntity<ListingResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateListingRequest request) {
        ListingView listing = listingService.create(AuthenticatedUser.id(jwt), request.toCommand());
        return ResponseEntity.created(UriComponentsBuilder.fromPath(LfpApiPaths.LISTING).build(listing.id()))
                .body(ListingResponse.from(listing));
    }
}
