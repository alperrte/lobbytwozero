-- V192 explicitly allocated to Hardware by the user (2026-11-08); V1-V10 remain unchanged.
-- Review ratings may now use half-star steps: 1.0, 1.5, ... 5.0. Existing integer ratings convert losslessly.
ALTER TABLE hardware_reviews DROP CONSTRAINT ck_hardware_reviews_rating;
ALTER TABLE hardware_reviews ALTER COLUMN rating TYPE numeric(2,1);
ALTER TABLE hardware_reviews ADD CONSTRAINT ck_hardware_reviews_rating
    CHECK (rating BETWEEN 1 AND 5 AND rating * 2 = trunc(rating * 2));
