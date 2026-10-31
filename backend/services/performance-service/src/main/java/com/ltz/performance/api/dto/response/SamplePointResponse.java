package com.ltz.performance.api.dto.response;

import java.time.Instant;

public record SamplePointResponse(String metric, String unit, Instant capturedAt, double value) { }
