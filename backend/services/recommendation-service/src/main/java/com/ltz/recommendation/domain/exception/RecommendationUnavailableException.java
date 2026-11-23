package com.ltz.recommendation.domain.exception;

public class RecommendationUnavailableException extends RuntimeException {
    public RecommendationUnavailableException() {
        super("Recommendation algorithm is unavailable");
    }
}
