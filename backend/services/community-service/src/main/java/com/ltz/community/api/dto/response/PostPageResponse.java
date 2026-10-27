package com.ltz.community.api.dto.response;

import java.util.List;

/** Keyset sayfalı sonuç; {@code nextCursor} son sayfada null'dır. */
public record PostPageResponse(List<PostViewResponse> items, String nextCursor) {
}
