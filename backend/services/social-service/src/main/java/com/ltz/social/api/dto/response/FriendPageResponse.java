package com.ltz.social.api.dto.response;

import java.util.List;

import com.ltz.social.application.service.FriendPageView;

/** {@code nextCursor} null ise son sayfadır; değilse sonraki istekte {@code cursor} olarak aynen gönderilir. */
public record FriendPageResponse(List<FriendResponse> items, String nextCursor) {

    public static FriendPageResponse from(FriendPageView view) {
        return new FriendPageResponse(view.items().stream().map(FriendResponse::from).toList(), view.nextCursor());
    }
}
