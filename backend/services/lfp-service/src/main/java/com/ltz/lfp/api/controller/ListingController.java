package com.ltz.lfp.api.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.lfp.api.dto.request.CreateListingRequest;
import com.ltz.lfp.api.dto.request.UpdateListingRequest;
import com.ltz.lfp.api.dto.response.ContactResponse;
import com.ltz.lfp.api.dto.response.ListingPageResponse;
import com.ltz.lfp.api.dto.response.ListingResponse;
import com.ltz.lfp.application.service.ListingQueryService;
import com.ltz.lfp.application.service.ListingSearch;
import com.ltz.lfp.application.service.ListingService;
import com.ltz.lfp.application.service.ListingView;
import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.enums.Platform;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Oyun arkadaşı ilanları (LTZ-UMT-LFP-01, -02, -03). Yazma işlemlerinde sahip her zaman token sahibidir; sahiplik ve
 * engel kuralları servis katmanında denetlenir.
 */
@RestController
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final ListingQueryService listingQueries;

    /** LFP-01..09: ilan açar; 201 + {@code Location}. */
    @PostMapping(LfpApiPaths.LISTINGS)
    public ResponseEntity<ListingResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateListingRequest request) {
        ListingView listing = listingService.create(AuthenticatedUser.id(jwt), request.toCommand());
        return ResponseEntity.created(UriComponentsBuilder.fromPath(LfpApiPaths.LISTING).build(listing.id()))
                .body(ListingResponse.from(listing));
    }

    /** LFP-10/11: aktif ilanlar; tüm süzgeçler isteğe bağlıdır ve birlikte uygulanır. */
    @GetMapping(LfpApiPaths.LISTINGS)
    public ListingPageResponse search(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) UUID gameId,
                                      @RequestParam(required = false) Platform platform,
                                      @RequestParam(required = false) String skillLevel,
                                      @RequestParam(required = false) String role,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant playFrom,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant playTo,
                                      @RequestParam(required = false) Boolean micRequired,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) String cursor) {
        ListingSearch search = new ListingSearch(gameId, platform, skillLevel, role, playFrom, playTo, micRequired, q);
        return ListingPageResponse.from(listingQueries.search(AuthenticatedUser.id(jwt), search, limit, cursor));
    }

    /** Sahibin kendi ilanları (tüm durumlar); {@code status} okuma anındaki duruma göre süzer. */
    @GetMapping(LfpApiPaths.MY_LISTINGS)
    public ListingPageResponse mine(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) ListingStatus status,
                                    @RequestParam(required = false) Integer limit,
                                    @RequestParam(required = false) String cursor) {
        return ListingPageResponse.from(listingQueries.mine(AuthenticatedUser.id(jwt), status, limit, cursor));
    }

    /** İlan detayı (her durumda); sahibiyle aralarında engel varsa 404. */
    @GetMapping(LfpApiPaths.LISTING)
    public ListingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        return ListingResponse.from(listingQueries.get(AuthenticatedUser.id(jwt), listingId));
    }

    /**
     * LFP-16: ilan sahibiyle iletişim izni. Yanıttaki {@code recipientId} ile messaging-service'te birebir sohbet
     * açılır; LFP ikinci bir mesajlaşma altyapısı kurmaz.
     */
    @PostMapping(LfpApiPaths.LISTING_CONTACT)
    public ContactResponse contact(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        return ContactResponse.from(listingQueries.contact(AuthenticatedUser.id(jwt), listingId));
    }

    /** LFP-12: sahip günceller (tam değiştirme). */
    @PutMapping(LfpApiPaths.LISTING)
    public ListingResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId,
                                  @Valid @RequestBody UpdateListingRequest request) {
        return ListingResponse.from(listingService.update(AuthenticatedUser.id(jwt), listingId, request.toCommand()));
    }

    /** LFP-13: sahip kapatır; idempotent. */
    @PostMapping(LfpApiPaths.LISTING_CLOSE)
    public ListingResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        return ListingResponse.from(listingService.close(AuthenticatedUser.id(jwt), listingId));
    }
}
