package com.ltz.content.api.dto.response;

import java.util.List;

/** Keyset sayfası: {@code nextCursor} doluysa sonraki sayfa vardır (opak imleç; aynı filtrelerle istenmelidir). */
public record ContentPageResponse(List<ContentCardResponse> items, String nextCursor) {
}
