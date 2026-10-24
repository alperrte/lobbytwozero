package com.ltz.community.api.dto.response;

import java.util.List;

/** Keyset sayfalı yorum listesi (eskiden yeniye); {@code nextCursor} son sayfada null'dır. */
public record CommentPageResponse(List<CommentResponse> items, String nextCursor) {
}
