package com.ltz.hardware.api.dto.response;

import java.util.List;

public record HardwareProfilePageResponse(List<HardwareProfileResponse> items, int page, int size,
                                          long totalElements, int totalPages) { }
