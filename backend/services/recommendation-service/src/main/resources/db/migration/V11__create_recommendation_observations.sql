CREATE TABLE recommendation_observations (
    id uuid CONSTRAINT pk_recommendation_observations PRIMARY KEY,
    algorithm_version varchar(32) NOT NULL,
    occurred_at timestamptz NOT NULL,
    outcome varchar(16) NOT NULL,
    candidate_count integer NOT NULL,
    result_count integer NOT NULL,
    total_score integer NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_recommendation_observations_algorithm CHECK (algorithm_version ~ '^[a-z][a-z0-9-]{0,31}$'),
    CONSTRAINT ck_recommendation_observations_outcome CHECK (outcome IN ('GENERATED', 'EMPTY', 'UNAVAILABLE')),
    CONSTRAINT ck_recommendation_observations_counts CHECK (
        candidate_count BETWEEN 0 AND 500 AND result_count BETWEEN 0 AND LEAST(50, candidate_count)
        AND total_score BETWEEN result_count AND result_count * 10000
        AND ((outcome = 'GENERATED') = (result_count > 0)))
);
CREATE INDEX ix_recommendation_observations_occurred_at ON recommendation_observations (occurred_at);
