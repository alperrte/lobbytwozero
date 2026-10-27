package com.ltz.hardware.application.port;

import java.util.UUID;

public interface HardwareReviewModerationAccess {
    String PERMISSION = "hardware:reviews:moderate";
    UUID requireModeratorId();
}
