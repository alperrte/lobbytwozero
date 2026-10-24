package com.ltz.hardware.api.dto.response;

import java.util.UUID;

public record HardwareReviewModerationResponse(HardwareReviewResponse review, UUID noticeEventId,
        String notificationDeliveryStatus) { }
