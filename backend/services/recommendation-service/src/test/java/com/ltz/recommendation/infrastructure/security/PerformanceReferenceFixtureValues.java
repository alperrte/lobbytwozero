package com.ltz.recommendation.infrastructure.security;

import java.util.HashMap;
import java.util.Map;

final class PerformanceReferenceFixtureValues {
    private PerformanceReferenceFixtureValues() { }

    static Map<String, Object> numbers(String mode, long samples) {
        Map<String, Object> result = new HashMap<>();
        result.put("avgFps", samples < 60 ? null : mode.equals("outlier-fps") ? 9999 : 60);
        result.put("minFps", samples < 60 ? null : mode.equals("invalid-fps") ? 100 : 30);
        result.put("maxFps", samples < 60 ? null : mode.equals("outlier-fps") ? 12000 : 90);
        return result;
    }
}
