package com.ltz.community.api.dto.response;

import java.util.List;

/** Keyset sayfalı forum listesi (eskiden yeniye); {@code nextCursor} son sayfada null'dır. */
public record ForumPageResponse(List<ForumResponse> items, String nextCursor) {
}
