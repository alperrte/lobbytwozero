package com.ltz.performance.api.dto.response;

import java.util.List;

public record SamplePageResponse(List<SamplePointResponse> items, int page, int size, boolean hasNext) { }
