package com.ltz.social.application.service;

import java.util.List;

/** Keyset sayfa sonucu; {@code nextCursor} null ise sonraki sayfa yoktur. */
public record FriendRequestPageView(List<FriendRequestView> items, String nextCursor) {
}
