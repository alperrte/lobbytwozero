package com.ltz.hardware.domain.exception;

public class HardwarePerformanceUnavailableException extends RuntimeException {
    public HardwarePerformanceUnavailableException() { super("Performance summary unavailable"); }
}
