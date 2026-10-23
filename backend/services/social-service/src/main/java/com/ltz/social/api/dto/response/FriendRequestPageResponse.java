package com.ltz.social.api.dto.response;

import java.util.List;

import com.ltz.social.application.service.FriendRequestPageView;

/** {@code nextCursor} null ise son sayfadır; değilse sonraki istekte {@code cursor} olarak aynen gönderilir. */
public record FriendRequestPageResponse(List<FriendRequestResponse> items, String nextCursor) {

    public static FriendRequestPageResponse from(FriendRequestPageView view) {
        return new FriendRequestPageResponse(
                view.items().stream().map(FriendRequestResponse::from).toList(), view.nextCursor());
    }
}
