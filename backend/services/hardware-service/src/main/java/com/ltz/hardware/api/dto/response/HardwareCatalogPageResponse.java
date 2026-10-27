package com.ltz.hardware.api.dto.response;

import java.util.List;

public record HardwareCatalogPageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) { }
