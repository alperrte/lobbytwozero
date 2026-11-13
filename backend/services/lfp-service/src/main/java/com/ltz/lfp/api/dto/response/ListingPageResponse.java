package com.ltz.lfp.api.dto.response;

import java.util.List;

import com.ltz.lfp.application.service.ListingPageView;

/** İlan sayfası; {@code nextCursor} son sayfada {@code null}. Sonraki sayfa aynı süzgeçlerle istenir. */
public record ListingPageResponse(List<ListingResponse> items, String nextCursor) {

    public static ListingPageResponse from(ListingPageView page) {
        return new ListingPageResponse(page.items().stream().map(ListingResponse::from).toList(), page.nextCursor());
    }
}
