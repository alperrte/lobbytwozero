package com.ltz.hardware.api.dto.response;

/** Values are recorded facts; no performance score, winner or compatibility inference. */
public record HardwareComparisonResponse<T>(T left, T right) { }
