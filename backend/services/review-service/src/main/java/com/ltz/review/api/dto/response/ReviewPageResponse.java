package com.ltz.review.api.dto.response;

import java.util.List;

/** Sayfalı inceleme listesi (kullanıcının kendi incelemeleri). Toplam, filtrelenmiş kümenin gerçek toplamıdır. */
public record ReviewPageResponse(List<ReviewResponse> items, int page, int size, long totalElements, long totalPages) {
    public ReviewPageResponse { items = List.copyOf(items); }
}
