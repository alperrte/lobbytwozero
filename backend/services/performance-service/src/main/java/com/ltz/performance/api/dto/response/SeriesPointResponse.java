package com.ltz.performance.api.dto.response;

import java.time.Instant;

/** Ham noktada {@code value=min=max} ve {@code count=1}; kovada {@code value} ortalama, {@code at} kova başlangıcıdır. */
public record SeriesPointResponse(Instant at, double value, double min, double max, long count) { }
