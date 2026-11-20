package com.ltz.recommendation.domain.entity;

import com.ltz.recommendation.domain.enums.RecommendationOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommendation_observations")
public class RecommendationObservation {
    @Id
    private UUID id;
    @Column(name = "algorithm_version", nullable = false, length = 32)
    private String algorithmVersion;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecommendationOutcome outcome;
    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;
    @Column(name = "result_count", nullable = false)
    private int resultCount;
    @Column(name = "total_score", nullable = false)
    private int totalScore;
    @Version
    private long version;

    protected RecommendationObservation() { }

    public String getAlgorithmVersion() { return algorithmVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public RecommendationOutcome getOutcome() { return outcome; }
    public int getCandidateCount() { return candidateCount; }
    public int getResultCount() { return resultCount; }
    public int getTotalScore() { return totalScore; }
}
