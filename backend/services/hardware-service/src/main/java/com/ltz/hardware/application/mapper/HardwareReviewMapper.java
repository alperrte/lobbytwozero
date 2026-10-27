package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.response.HardwareReviewResponse;
import com.ltz.hardware.domain.entity.HardwareReview;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HardwareReviewMapper {
    public HardwareReviewResponse toResponse(HardwareReview review, UUID viewer) {
        return new HardwareReviewResponse(review.getId(), review.getProductId(), review.getBody(), review.getRating(),
                review.getStatus(), review.getVersion(), viewer.equals(review.getUserId()), review.getCreatedAt(), review.getUpdatedAt());
    }
}
