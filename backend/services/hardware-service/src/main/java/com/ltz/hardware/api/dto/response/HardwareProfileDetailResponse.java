package com.ltz.hardware.api.dto.response;

import java.util.List;

public record HardwareProfileDetailResponse(HardwareProfileResponse profile,
        List<HardwareComponentResponse> components, int page, int size, long totalComponents, int totalPages) { }
