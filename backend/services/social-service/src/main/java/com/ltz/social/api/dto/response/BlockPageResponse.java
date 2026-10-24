package com.ltz.social.api.dto.response;

import java.util.List;

import com.ltz.social.application.service.BlockPageView;

/** {@code nextCursor} null ise son sayfadır; değilse sonraki istekte {@code cursor} olarak aynen gönderilir. */
public record BlockPageResponse(List<BlockResponse> items, String nextCursor) {

    public static BlockPageResponse from(BlockPageView view) {
        return new BlockPageResponse(view.items().stream().map(BlockResponse::from).toList(), view.nextCursor());
    }
}
