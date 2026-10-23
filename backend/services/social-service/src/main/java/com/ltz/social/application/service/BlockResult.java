package com.ltz.social.application.service;

/** {@code created}: bu çağrıda yeni engel kondu mu (HTTP 201) yoksa zaten vardı mı (HTTP 200). */
public record BlockResult(BlockView block, boolean created) {
}
