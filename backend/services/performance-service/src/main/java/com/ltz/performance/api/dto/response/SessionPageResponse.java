package com.ltz.performance.api.dto.response;

import java.util.List;

public record SessionPageResponse(List<SessionResponse> items, int page, int size, boolean hasNext,
        long totalElements) { }
